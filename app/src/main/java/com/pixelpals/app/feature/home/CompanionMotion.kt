package com.pixelpals.app.feature.home

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

enum class CompanionActivity { GREET, APPROACH_TOY, PLAY, APPROACH_BED, REST }

/** Distance-driven gait with anticipation, braking and planted direction changes. */
class CompanionMotion {
    var x: Float = 500f
        private set
    var y: Float = 650f
        private set
    var activity: CompanionActivity = CompanionActivity.GREET
        private set
    var elapsed: Float = 0f
        private set
    var isFacingLeft: Boolean = false
        private set
    var distanceTravelled: Float = 0f
        private set
    var speed: Float = 0f
        private set
    var turnElapsed: Float = 0f
        private set
    var turnFromFacingLeft: Boolean = false
        private set
    var isTurning: Boolean = false
        private set
    private var targetFacesLeft: Boolean = false
    private var headingX: Float = 0f
    private var headingY: Float = 0f
    val isApproaching: Boolean get() = activity == CompanionActivity.APPROACH_TOY || activity == CompanionActivity.APPROACH_BED
    val isPreparing: Boolean get() = isApproaching && elapsed < ANTICIPATION_SECONDS
    fun advance(delta: Float, toyX: Float, bedX: Float, tempo: Float, bond: Int, toyY: Float = 650f, bedY: Float = 650f, toyFacesLeft: Boolean? = null): Unit {
        val dt: Float = delta.coerceIn(0f, .1f)
        elapsed += dt
        when (activity) {
            CompanionActivity.GREET -> if (elapsed >= 4f - bond.coerceIn(0, 100) * .02f) transition(CompanionActivity.APPROACH_TOY)
            CompanionActivity.APPROACH_TOY -> if (approach(toyX, toyY, dt, tempo)) transition(CompanionActivity.PLAY)
            CompanionActivity.PLAY -> {
                if (toyFacesLeft != null) updateTurn(if (toyFacesLeft) -100f else 100f, dt, tempo)
                if (elapsed >= 5f && !isTurning) transition(CompanionActivity.APPROACH_BED)
            }
            CompanionActivity.APPROACH_BED -> if (approach(bedX, bedY, dt, tempo)) transition(CompanionActivity.REST)
            CompanionActivity.REST -> if (elapsed >= 8f / tempo.coerceAtLeast(.45f)) transition(CompanionActivity.APPROACH_TOY)
        }
    }
    private fun transition(next: CompanionActivity): Unit {
        activity = next
        elapsed = 0f
        speed = 0f
        isTurning = false
    }
    private fun updateTurn(dx: Float, dt: Float, tempo: Float): Boolean {
        if (!isTurning && abs(dx) > 3f && (dx < 0) != isFacingLeft) {
            isTurning = true
            turnFromFacingLeft = isFacingLeft
            turnElapsed = 0f
            targetFacesLeft = dx < 0
        }
        if (!isTurning) return false
        if (speed > 0f) {
            speed = (speed - 180f * tempo * dt).coerceAtLeast(0f)
            val oldX: Float = x
            val oldY: Float = y
            x = (x + headingX * speed * dt).coerceIn(140f, 860f)
            y = (y + headingY * speed * dt).coerceIn(430f, 680f)
            distanceTravelled += hypot(x - oldX, y - oldY)
            return true
        }
        turnElapsed += dt
        if (turnElapsed >= TURN_SECONDS / 2) isFacingLeft = targetFacesLeft
        if (turnElapsed >= TURN_SECONDS) isTurning = false
        return true
    }
    private fun approach(target: Float, targetY: Float, dt: Float, tempo: Float): Boolean {
        val dx: Float = target.coerceIn(140f, 860f) - x
        val dy: Float = targetY.coerceIn(430f, 680f) - y
        val distance: Float = hypot(dx, dy)
        if (distance <= .35f) { speed = 0f; return true }
        if (updateTurn(dx, dt, tempo) || isPreparing) return false
        val acceleration: Float = 130f * tempo.coerceIn(.3f, 2f)
        val desiredSpeed: Float = minOf(85f * tempo, sqrt(2f * acceleration * distance))
        speed += (desiredSpeed - speed).coerceIn(-acceleration * dt, acceleration * dt)
        val step: Float = minOf(distance, speed.coerceAtLeast(0f) * dt)
        headingX = dx / distance
        headingY = dy / distance
        x += headingX * step
        y += headingY * step
        distanceTravelled += step
        return false
    }
    companion object {
        const val ANTICIPATION_SECONDS: Float = .22f
        const val TURN_SECONDS: Float = .36f
    }
}
