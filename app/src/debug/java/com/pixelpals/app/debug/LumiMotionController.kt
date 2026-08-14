package com.pixelpals.app.debug

import kotlin.math.PI
import kotlin.math.sin

internal data class LumiMotionPoint(
    val x: Float,
    val y: Float,
)

/** Pure root-motion curves for the Lumi V2 clips. */
internal class LumiMotionController(
    private val density: Float,
) {
    fun walkX(
        cycleStartX: Float,
        targetX: Float,
        facing: Float,
        cycleDistance: Float,
        phase: Float,
    ): Float {
        val desiredX = cycleStartX + facing * cycleDistance * phase.coerceIn(0f, 1f)
        return if (facing > 0f) minOf(desiredX, targetX) else maxOf(desiredX, targetX)
    }

    fun hopPoint(
        progress: Float,
        startX: Float,
        targetX: Float,
        startY: Float,
        targetY: Float,
    ): LumiMotionPoint {
        val normalized = progress.coerceIn(0f, 1f)
        val launch = smoothStep(((normalized - 0.16f) / 0.18f).coerceIn(0f, 1f))
        val flight = smoothStep(((normalized - 0.34f) / 0.46f).coerceIn(0f, 1f))
        val landing = smoothStep(((normalized - 0.80f) / 0.20f).coerceIn(0f, 1f))
        val xProgress = when {
            normalized < 0.16f -> 0f
            normalized < 0.34f -> launch * 0.22f
            normalized < 0.80f -> 0.22f + flight * 0.78f
            else -> 1f
        }
        val groundY = startY + (targetY - startY) * xProgress
        val arcProgress = ((normalized - 0.24f) / 0.56f).coerceIn(0f, 1f)
        val arc = sin(arcProgress * PI).toFloat() * 54f * density
        val y = when {
            normalized < 0.16f -> startY + 4f * density
            normalized < 0.80f -> groundY - arc
            else -> targetY + (1f - landing) * 6f * density
        }
        return LumiMotionPoint(
            x = startX + (targetX - startX) * xProgress,
            y = y,
        )
    }

    private fun smoothStep(value: Float): Float = value * value * (3f - 2f * value)

    companion object {
        const val TURN_DURATION_SECONDS: Float = 1.70f
        const val HOP_DURATION_SECONDS: Float = 2.76f
    }
}
