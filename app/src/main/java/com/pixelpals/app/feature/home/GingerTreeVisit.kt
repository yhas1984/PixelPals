package com.pixelpals.app.feature.home

import com.pixelpals.app.core.motion.GroundGait
import kotlin.math.hypot

/** A return trip using existing feline pounce/landing poses, never a rotated walk. */
internal class GingerTreeVisit(val startX: Float, val startY: Float) {
    enum class Phase { APPROACH, COIL, ASCEND, LAND_BRANCH, PERCH, DESCEND, LAND_FLOOR, RETURN, DONE }
    var phase: Phase = Phase.APPROACH
        private set
    var elapsed: Float = 0f
        private set
    var x: Float = startX
        private set
    var y: Float = startY
        private set
    private val approachDuration = GroundGait.duration(hypot(startX - TAKEOFF_X, startY - FLOOR_Y), 85f, .8f)
    val isAirborne: Boolean get() = phase == Phase.ASCEND || phase == Phase.DESCEND
    val facingLeft: Boolean get() = when (phase) {
        Phase.APPROACH -> TAKEOFF_X < startX
        Phase.DESCEND, Phase.LAND_FLOOR -> false
        Phase.RETURN -> startX < TAKEOFF_X
        else -> true
    }
    val clip: String get() = when (phase) {
        Phase.APPROACH, Phase.RETURN -> "walk"
        Phase.COIL -> "stalk"
        Phase.ASCEND, Phase.DESCEND -> "pounce"
        Phase.LAND_BRANCH, Phase.LAND_FLOOR -> "land"
        else -> "idle"
    }
    val clipSeconds: Float get() = when (phase) {
        Phase.APPROACH -> hypot(x - startX, y - startY) / 105f * .54f
        Phase.RETURN -> hypot(x - TAKEOFF_X, y - FLOOR_Y) / 105f * .54f
        Phase.ASCEND, Phase.DESCEND -> .13f
        Phase.COIL -> 0f
        else -> elapsed
    }
    fun advance(delta: Float) {
        if (phase == Phase.DONE) return
        elapsed += delta.coerceIn(0f, .1f)
        val duration = when (phase) {
            Phase.APPROACH, Phase.RETURN -> approachDuration
            Phase.COIL -> .35f
            Phase.ASCEND -> 1.2f
            Phase.DESCEND -> 1.1f
            Phase.LAND_BRANCH, Phase.LAND_FLOOR -> .35f
            Phase.PERCH -> 3f
            Phase.DONE -> return
        }
        val t = (elapsed / duration).coerceIn(0f, 1f)
        val smooth = GroundGait.progress(elapsed, duration)
        when (phase) {
            Phase.APPROACH -> { x = startX + (TAKEOFF_X - startX) * smooth; y = startY + (FLOOR_Y - startY) * smooth }
            Phase.RETURN -> { x = TAKEOFF_X + (startX - TAKEOFF_X) * smooth; y = FLOOR_Y + (startY - FLOOR_Y) * smooth }
            Phase.ASCEND -> { x = TAKEOFF_X + (BRANCH_X - TAKEOFF_X) * t; y = FLOOR_Y + (BRANCH_Y - FLOOR_Y) * t - 4f * 120f * t * (1f - t) }
            Phase.DESCEND -> { x = BRANCH_X + (TAKEOFF_X - BRANCH_X) * t; y = BRANCH_Y + (FLOOR_Y - BRANCH_Y) * t - 4f * 110f * t * (1f - t) }
            else -> Unit
        }
        if (t >= 1f) { phase = Phase.entries[phase.ordinal + 1]; elapsed = 0f }
    }
    companion object {
        const val BRANCH_X = 375f
        const val BRANCH_Y = 305f
        private const val TAKEOFF_X = 525f
        private const val FLOOR_Y = 650f
    }
}
