package com.pixelpals.app

import android.content.Context
import android.content.SharedPreferences
import com.pixelpals.app.database.AppDatabase
import com.pixelpals.app.database.TreasureItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    companion object {
        private const val KEY_LAST_TREASURE_INTERACTION_MILESTONE = "last_treasure_interaction_milestone"
        private const val KEY_LAST_TREASURE_ACTIVE_MILESTONE = "last_treasure_active_milestone"
        private const val KEY_LAST_TREASURE_EMOJI = "last_treasure_emoji"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences("pixelpals_progress", Context.MODE_PRIVATE)

    private val db = AppDatabase.getDatabase(context)
    private val scope = CoroutineScope(Dispatchers.IO)
    private val treasureMutex = Mutex()

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

    private suspend fun addTreasureInternal(emoji: String) {
        val dao = db.treasureDao()
        val map = getTreasureMap().toMutableMap()
        val newCount = (map[emoji] ?: 0) + 1
        map[emoji] = newCount
        saveTreasureMap(map)
        addXP(10)

        val now = System.currentTimeMillis()
        val existing = dao.getTreasure(emoji)
        if (existing != null) {
            dao.updateTreasure(existing.copy(count = newCount, lastFoundAt = now))
        } else {
            dao.insertTreasure(TreasureItem(emoji, newCount, now, now))
        }
    }

    fun addTreasure(emoji: String) {
        scope.launch {
            treasureMutex.withLock {
                addTreasureInternal(emoji)
            }
        }
    }

    /** Get a treasure trying to avoid repetition and favoring missing/rarer ones. */
    fun rollTreasure(): String {
        val treasureMap = getTreasureMap()
        val lastTreasure = prefs.getString(KEY_LAST_TREASURE_EMOJI, null)

        val unseenTreasures = allTreasures.filter { (treasureMap[it] ?: 0) == 0 }
        val basePool = if (unseenTreasures.isNotEmpty()) {
            unseenTreasures
        } else {
            val minCount = allTreasures.minOf { treasureMap[it] ?: 0 }
            allTreasures.filter { (treasureMap[it] ?: 0) == minCount }
        }

        val filteredPool = if (lastTreasure != null && basePool.size > 1) {
            basePool.filter { it != lastTreasure }.ifEmpty { basePool }
        } else {
            basePool
        }

        return filteredPool.random().also { selected ->
            prefs.edit().putString(KEY_LAST_TREASURE_EMOJI, selected).apply()
        }
    }

    suspend fun maybeAwardTreasureFromInteraction(): String? = treasureMutex.withLock {
        val milestone = when {
            treasureCount == 0 && totalInteractions >= 3 -> 1
            else -> totalInteractions / 12
        }
        val lastMilestone = prefs.getInt(KEY_LAST_TREASURE_INTERACTION_MILESTONE, 0)
        if (milestone <= lastMilestone || milestone <= 0) return@withLock null

        val treasure = rollTreasure()
        addTreasureInternal(treasure)
        prefs.edit().putInt(KEY_LAST_TREASURE_INTERACTION_MILESTONE, milestone).apply()
        treasure
    }

    suspend fun maybeAwardTreasureFromActiveMinute(): String? = treasureMutex.withLock {
        val milestone = when {
            treasureCount == 0 && totalActiveMinutes >= 1 -> 1
            else -> totalActiveMinutes / 4
        }
        val lastMilestone = prefs.getInt(KEY_LAST_TREASURE_ACTIVE_MILESTONE, 0)
        if (milestone <= lastMilestone || milestone <= 0) return@withLock null

        val treasure = rollTreasure()
        addTreasureInternal(treasure)
        prefs.edit().putInt(KEY_LAST_TREASURE_ACTIVE_MILESTONE, milestone).apply()
        treasure
    }

    suspend fun syncRoomWithLegacyMap() {
        treasureMutex.withLock {
            val dao = db.treasureDao()
            val now = System.currentTimeMillis()
            val legacyMap = getTreasureMap()

            legacyMap.forEach { (emoji, count) ->
                if (count <= 0) return@forEach
                val existing = dao.getTreasure(emoji)
                if (existing == null) {
                    dao.insertTreasure(TreasureItem(emoji, count, now, now))
                } else if (existing.count != count) {
                    dao.updateTreasure(existing.copy(count = count, lastFoundAt = now))
                }
            }

            dao.getAllTreasuresSnapshot().forEach { roomItem ->
                val legacyCount = legacyMap[roomItem.emoji] ?: 0
                if (legacyCount <= 0) {
                    dao.deleteTreasure(roomItem)
                }
            }
        }
    }

    suspend fun consumeTreasure(emoji: String): Int = treasureMutex.withLock {
        val dao = db.treasureDao()
        val map = getTreasureMap().toMutableMap()
        val currentCount = map[emoji] ?: 0
        if (currentCount <= 0) return@withLock 0

        val newCount = currentCount - 1
        if (newCount <= 0) {
            map.remove(emoji)
        } else {
            map[emoji] = newCount
        }
        saveTreasureMap(map)

        val existing = dao.getTreasure(emoji)
        if (existing != null) {
            if (newCount <= 0) {
                dao.deleteTreasure(existing)
            } else {
                dao.updateTreasure(existing.copy(count = newCount, lastFoundAt = System.currentTimeMillis()))
            }
        }
        newCount
    }

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
