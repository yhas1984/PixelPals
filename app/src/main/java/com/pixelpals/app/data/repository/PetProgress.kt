package com.pixelpals.app.data.repository

import android.content.Context
import android.content.SharedPreferences

/**
 * PetProgress — XP, level y rare events de la mascota.
 *
 * Solo persiste felicidad / evolución / rare-events. El sistema de tesoros
 * vive en [PixelPalsRepository] + Room (sin doble fuente de verdad).
 *
 * Las escrituras se acumulan en memoria y se vuelcan con [flush] (al minuto
 * activo, al pausar o al destruir) para minimizar I/O de SharedPreferences.
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

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("pixelpals_progress", Context.MODE_PRIVATE)

    private var pendingHappiness = 0
    private var pendingActiveMinutes = 0
    private var pendingInteractions = 0
    private var pendingRareEvents = 0

    val happinessPoints: Int
        get() = prefs.getInt(KEY_HAPPINESS_POINTS, 0) + pendingHappiness

    val totalActiveMinutes: Int
        get() = prefs.getInt(KEY_TOTAL_ACTIVE_MINUTES, 0) + pendingActiveMinutes

    val totalInteractions: Int
        get() = prefs.getInt(KEY_TOTAL_INTERACTIONS, 0) + pendingInteractions

    fun addXP(amount: Int) {
        pendingHappiness += amount
    }

    fun trackMinute() {
        pendingActiveMinutes++
        addXP(1)
    }

    fun trackInteraction() {
        pendingInteractions++
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
            1 -> appContext.getString(com.pixelpals.app.R.string.progress_level_baby)
            2 -> appContext.getString(com.pixelpals.app.R.string.progress_level_child)
            3 -> appContext.getString(com.pixelpals.app.R.string.progress_level_young)
            else -> appContext.getString(com.pixelpals.app.R.string.progress_level_adult)
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

    val rareEventsWitnessed: Int
        get() = prefs.getInt(KEY_RARE_EVENTS, 0) + pendingRareEvents

    fun trackRareEvent() {
        pendingRareEvents++
        addXP(3)
    }

    /** Vuelca los contadores pendientes a disco en una sola escritura. */
    fun flush() {
        val editor = prefs.edit()
        if (pendingHappiness != 0) editor.putInt(KEY_HAPPINESS_POINTS, happinessPoints)
        if (pendingActiveMinutes != 0) editor.putInt(KEY_TOTAL_ACTIVE_MINUTES, totalActiveMinutes)
        if (pendingInteractions != 0) editor.putInt(KEY_TOTAL_INTERACTIONS, totalInteractions)
        if (pendingRareEvents != 0) editor.putInt(KEY_RARE_EVENTS, rareEventsWitnessed)
        editor.apply()
        pendingHappiness = 0
        pendingActiveMinutes = 0
        pendingInteractions = 0
        pendingRareEvents = 0
    }

    fun getStatsSummary(): String {
        return buildString {
            appendLine(appContext.getString(com.pixelpals.app.R.string.progress_summary_level, petLevel, levelName))
            appendLine(appContext.getString(com.pixelpals.app.R.string.progress_summary_happiness, happinessPoints))
            appendLine(appContext.getString(com.pixelpals.app.R.string.progress_summary_active_time, totalActiveMinutes))
            appendLine(appContext.getString(com.pixelpals.app.R.string.progress_summary_interactions, totalInteractions))
            appendLine(appContext.getString(com.pixelpals.app.R.string.progress_summary_rare_events, rareEventsWitnessed))
        }
    }

    private companion object {
        const val KEY_HAPPINESS_POINTS = "happiness_points"
        const val KEY_TOTAL_ACTIVE_MINUTES = "total_active_minutes"
        const val KEY_TOTAL_INTERACTIONS = "total_interactions"
        const val KEY_RARE_EVENTS = "rare_events"
    }
}
