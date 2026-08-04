package com.pixelpals.app.data.repository

import android.content.Context
import android.content.SharedPreferences

/**
 * PetProgress — XP, level y rare events de la mascota.
 *
 * Solo persiste felicidad / evolución / rare-events. El sistema de tesoros
 * vive en [PixelPalsRepository] + Room (sin doble fuente de verdad).
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
 *   - 3 XP por evento secreto presenciado
 */
class PetProgress(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("pixelpals_progress", Context.MODE_PRIVATE)

    var happinessPoints: Int
        get() = prefs.getInt(KEY_HAPPINESS_POINTS, 0)
        private set(value) = prefs.edit().putInt(KEY_HAPPINESS_POINTS, value).apply()

    var totalActiveMinutes: Int
        get() = prefs.getInt(KEY_TOTAL_ACTIVE_MINUTES, 0)
        private set(value) = prefs.edit().putInt(KEY_TOTAL_ACTIVE_MINUTES, value).apply()

    var totalInteractions: Int
        get() = prefs.getInt(KEY_TOTAL_INTERACTIONS, 0)
        private set(value) = prefs.edit().putInt(KEY_TOTAL_INTERACTIONS, value).apply()

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

    val petLevel: Int
        get() = when {
            happinessPoints < 100 -> 1
            happinessPoints < 500 -> 2
            happinessPoints < 2000 -> 3
            else -> 4
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

    val unlockedBehaviors: Int
        get() = petLevel

    var rareEventsWitnessed: Int
        get() = prefs.getInt(KEY_RARE_EVENTS, 0)
        private set(value) = prefs.edit().putInt(KEY_RARE_EVENTS, value).apply()

    fun trackRareEvent() {
        rareEventsWitnessed++
        addXP(3)
    }

    fun getStatsSummary(): String {
        return buildString {
            appendLine("🏆 Nivel: $petLevel ($levelName)")
            appendLine("💛 Felicidad: $happinessPoints XP")
            appendLine("⏱️ Tiempo activo: $totalActiveMinutes min")
            appendLine("👆 Interacciones: $totalInteractions")
            appendLine("✨ Eventos raros: $rareEventsWitnessed")
        }
    }

    private companion object {
        const val KEY_HAPPINESS_POINTS = "happiness_points"
        const val KEY_TOTAL_ACTIVE_MINUTES = "total_active_minutes"
        const val KEY_TOTAL_INTERACTIONS = "total_interactions"
        const val KEY_RARE_EVENTS = "rare_events"
    }
}
