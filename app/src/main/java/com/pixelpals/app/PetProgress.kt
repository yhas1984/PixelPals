package com.pixelpals.app

import android.content.Context
import android.content.SharedPreferences
import com.pixelpals.app.database.AppDatabase
import com.pixelpals.app.database.TreasureItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * PetProgress — El Tamagotchi interior.
 *
 * Gestiona la evolución, felicidad y tesoros de la mascota via SharedPreferences y Room DB.
 *
 * Niveles de evolución:
 *   Lv1 "Bebé"  (0-99 XP)    → 65% tamaño, pocas animaciones
 *   Lv2 "Niño"  (100-499 XP) → 80% tamaño, más animaciones
 *   Lv3 "Joven" (500-1999 XP)→ 92% tamaño, casi todas las animaciones
 *   Lv4 "Adulto"(2000+ XP)   → 100% tamaño, todas las animaciones
 *
 * XP se gana con:
 *   - 1 XP por minuto activo en pantalla
 *   - 5 XP por interacción (tap/drag)
 *   - 10 XP por tesoro encontrado
 *   - 3 XP por evento secreto presenciado
 */
class PetProgress(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("pixelpals_progress", Context.MODE_PRIVATE)

    private val db = AppDatabase.getDatabase(context)
    private val scope = CoroutineScope(Dispatchers.IO)

    // ── Happiness / XP ─────────────────────────────────────────

    var happinessPoints: Int
        get() = prefs.getInt("happiness_points", 0)
        private set(value) = prefs.edit().putInt("happiness_points", value).apply()

    var totalActiveMinutes: Int
        get() = prefs.getInt("total_active_minutes", 0)
        private set(value) = prefs.edit().putInt("total_active_minutes", value).apply()

    var totalInteractions: Int
        get() = prefs.getInt("total_interactions", 0)
        private set(value) = prefs.edit().putInt("total_interactions", value).apply()

    fun addXP(amount: Int) {
        happinessPoints += amount
    }

    fun trackMinute() {
        totalActiveMinutes++
        addXP(1)
    }

    fun trackInteraction() {
        totalInteractions++
        addXP(5)
    }

    // ── Evolution Level ────────────────────────────────────────

    val petLevel: Int
        get() = when {
            happinessPoints < 100 -> 1   // Bebé
            happinessPoints < 500 -> 2   // Niño
            happinessPoints < 2000 -> 3  // Joven
            else -> 4                     // Adulto
        }

    val levelName: String
        get() = when (petLevel) {
            1 -> "Bebé"
            2 -> "Niño"
            3 -> "Joven"
            else -> "Adulto"
        }

    /** Pets grow physically as they level up */
    val sizeMultiplier: Float
        get() = when (petLevel) {
            1 -> 0.65f
            2 -> 0.82f
            3 -> 0.93f
            else -> 1.0f
        }

    /** Higher levels unlock more behaviors */
    val unlockedBehaviors: Int
        get() = petLevel  // 1=basic, 2=secrets, 3=treasures, 4=all

    // ── Treasures ──────────────────────────────────────────────

    private val allTreasures = listOf(
        "🪙", "🌸", "🦴", "⭐", "💎", "🍀", "🐚", "🎀",
        "🍄", "🔑", "🧩", "🎵", "🪶", "🍬", "🌙", "💍", "👑", "🔮", "🍕"
    )

    fun addTreasure(emoji: String) {
        // 1. Legado: mantener map para XP rápido en PetService
        val map = getTreasureMap().toMutableMap()
        map[emoji] = (map[emoji] ?: 0) + 1
        saveTreasureMap(map)
        addXP(10)

        // 2. Room Database: Persistir detalles de colección
        scope.launch {
            val dao = db.treasureDao()
            val existing = dao.getTreasure(emoji)
            val now = System.currentTimeMillis()
            if (existing != null) {
                dao.updateTreasure(existing.copy(count = existing.count + 1, lastFoundAt = now))
            } else {
                dao.insertTreasure(TreasureItem(emoji, 1, now, now))
            }
        }
    }

    /** Get a random treasure for finding */
    fun rollTreasure(): String = allTreasures.random()

    fun getTreasureMap(): Map<String, Int> {
        val raw = prefs.getString("treasures", "") ?: ""
        if (raw.isEmpty()) return emptyMap()
        return try {
            raw.split(",").filter { it.contains(":") }.associate {
                val parts = it.split(":")
                parts[0] to (parts.getOrNull(1)?.toIntOrNull() ?: 0)
            }
        } catch (_: Exception) { emptyMap() }
    }

    private fun saveTreasureMap(map: Map<String, Int>) {
        val encoded = map.entries.joinToString(",") { "${it.key}:${it.value}" }
        prefs.edit().putString("treasures", encoded).apply()
    }

    val treasureCount: Int
        get() = getTreasureMap().values.sum()

    val uniqueTreasureCount: Int
        get() = getTreasureMap().size

    // ── Rare Events ────────────────────────────────────────────

    var rareEventsWitnessed: Int
        get() = prefs.getInt("rare_events", 0)
        private set(value) = prefs.edit().putInt("rare_events", value).apply()

    fun trackRareEvent() {
        rareEventsWitnessed++
        addXP(3)
    }

    // ── Stats Summary ──────────────────────────────────────────

    fun getStatsSummary(): String {
        return buildString {
            appendLine("🏆 Nivel: $petLevel ($levelName)")
            appendLine("💛 Felicidad: $happinessPoints XP")
            appendLine("⏱️ Tiempo activo: ${totalActiveMinutes} min")
            appendLine("👆 Interacciones: $totalInteractions")
            appendLine("💎 Tesoros: $treasureCount ($uniqueTreasureCount únicos)")
            appendLine("✨ Eventos raros: $rareEventsWitnessed")
        }
    }
}
