package com.pixelpals.app.core.care.scene

import kotlin.math.PI
import kotlin.math.sin

data class ImpPettingPose(val handOffset: Float = 0f, val leanDegrees: Float = 0f)
data class ImpFirePose(val strength: Float = 0f, val reach: Float = 0f)

/** Presentation only: these clocks never award care or extend a persistence transaction. */
object ImpCareMotion {
    const val EATING_DURATION_MS: Long = 2_400L
    const val FIRE_START_MS: Long = 2_160L
    const val FIRE_END_MS: Long = 2_910L

    fun samplePetting(progress: Float, reduced: Boolean): ImpPettingPose {
        if (reduced) return ImpPettingPose()
        val time: Float = progress.coerceIn(0f, 1f)
        val envelope: Float = sin(time * PI).toFloat()
        val stroke: Float = sin(time * PI * 4).toFloat() * envelope
        return ImpPettingPose(handOffset = stroke * .045f, leanDegrees = stroke * 2.2f)
    }

    fun getWingFold(progress: Float, reduced: Boolean): Float =
        if (reduced) 1f else smoothStep((progress - .10f) / .30f)

    fun getFoodAmount(elapsedMs: Long): Float =
        (1f - ((elapsedMs.toFloat() / EATING_DURATION_MS - .2f) / .65f)).coerceIn(0f, 1f)

    fun getReducedFrame(action: CareSceneAction, elapsedMs: Long): Int? = when (action) {
        CareSceneAction.REST -> 9
        CareSceneAction.FEED -> if (sampleFire(elapsedMs, true).strength > 0f) 6 else 3
        else -> null
    }

    fun sampleFire(elapsedMs: Long, reduced: Boolean): ImpFirePose {
        if (elapsedMs <= FIRE_START_MS || elapsedMs >= FIRE_END_MS) return ImpFirePose()
        // Reduced motion shows one small steady puff, with no flicker or moving sparks.
        if (reduced) return ImpFirePose(strength = .6f, reach = .22f)
        val grow: Float = smoothStep((elapsedMs - FIRE_START_MS) / 210f)
        val fade: Float = 1f - smoothStep((elapsedMs - 2_660L) / 250f)
        return ImpFirePose(strength = grow * fade, reach = .16f + .24f * grow)
    }

    private fun smoothStep(value: Float): Float {
        val bounded: Float = value.coerceIn(0f, 1f)
        return bounded * bounded * (3f - 2f * bounded)
    }
}
