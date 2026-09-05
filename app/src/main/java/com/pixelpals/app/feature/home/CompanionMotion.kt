package com.pixelpals.app.feature.home

import kotlin.math.abs
import kotlin.math.sign

enum class CompanionActivity { GREET, APPROACH_TOY, PLAY, APPROACH_BED, REST }

/** Small continuous scene brain. Changing targets never teleports the actor. */
class CompanionMotion {
    var x: Float = 500f
        private set
    var activity: CompanionActivity = CompanionActivity.GREET
        private set
    var elapsed: Float = 0f
        private set
    var isFacingLeft: Boolean = false
        private set
    private var speed: Float = 0f
    fun advance(delta: Float, toyX: Float, bedX: Float, tempo: Float, bond: Int): Unit {
        val dt: Float = delta.coerceIn(0f, .1f)
        elapsed += dt
        when (activity) {
            CompanionActivity.GREET -> if (elapsed >= 4f - bond.coerceIn(0, 100) * .02f) transition(CompanionActivity.APPROACH_TOY)
            CompanionActivity.APPROACH_TOY -> if (approach(toyX, dt, tempo)) transition(CompanionActivity.PLAY)
            CompanionActivity.PLAY -> if (elapsed >= 5f) transition(CompanionActivity.APPROACH_BED)
            CompanionActivity.APPROACH_BED -> if (approach(bedX, dt, tempo)) transition(CompanionActivity.REST)
            CompanionActivity.REST -> if (elapsed >= 8f / tempo.coerceAtLeast(.45f)) transition(CompanionActivity.APPROACH_TOY)
        }
    }
    private fun transition(next: CompanionActivity): Unit { activity = next; elapsed = 0f; speed = 0f }
    private fun approach(target: Float, dt: Float, tempo: Float): Boolean {
        val distance: Float = target.coerceIn(180f, 820f) - x
        if (abs(distance) < 1f) return true
        isFacingLeft = distance < 0
        speed = (speed + dt * 130f * tempo).coerceAtMost(85f * tempo)
        val easingSpeed: Float = minOf(speed, abs(distance) * 2f).coerceAtLeast(2f)
        x += sign(distance) * minOf(abs(distance), easingSpeed * dt)
        return false
    }
}
