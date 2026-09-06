package com.pixelpals.app.feature.home

import kotlin.math.abs
import kotlin.math.sign

enum class CompanionActivity { GREET, APPROACH_TOY, PLAY, APPROACH_BED, REST }

/** Small continuous scene brain. Changing targets never teleports the actor. */
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
    private var speed: Float = 0f
    fun advance(delta: Float, toyX: Float, bedX: Float, tempo: Float, bond: Int, toyY: Float = 650f, bedY: Float = 650f): Unit {
        val dt: Float = delta.coerceIn(0f, .1f)
        elapsed += dt
        when (activity) {
            CompanionActivity.GREET -> if (elapsed >= 4f - bond.coerceIn(0, 100) * .02f) transition(CompanionActivity.APPROACH_TOY)
            CompanionActivity.APPROACH_TOY -> if (approach(toyX, toyY, dt, tempo)) transition(CompanionActivity.PLAY)
            CompanionActivity.PLAY -> if (elapsed >= 5f) transition(CompanionActivity.APPROACH_BED)
            CompanionActivity.APPROACH_BED -> if (approach(bedX, bedY, dt, tempo)) transition(CompanionActivity.REST)
            CompanionActivity.REST -> if (elapsed >= 8f / tempo.coerceAtLeast(.45f)) transition(CompanionActivity.APPROACH_TOY)
        }
    }
    private fun transition(next: CompanionActivity): Unit { activity = next; elapsed = 0f; speed = 0f }
    private fun approach(target: Float, targetY: Float, dt: Float, tempo: Float): Boolean {
        val dx = target.coerceIn(140f, 860f) - x
        val dy = targetY.coerceIn(430f, 680f) - y
        val distance = kotlin.math.hypot(dx, dy)
        if (distance < 1f) return true
        if (abs(dx) > 1f) isFacingLeft = dx < 0
        speed = (speed + dt * 130f * tempo).coerceAtMost(85f * tempo)
        val easingSpeed: Float = minOf(speed, abs(distance) * 2f).coerceAtLeast(2f)
        val step = minOf(distance, easingSpeed * dt)
        x += dx / distance * step
        y += dy / distance * step
        return false
    }
}
