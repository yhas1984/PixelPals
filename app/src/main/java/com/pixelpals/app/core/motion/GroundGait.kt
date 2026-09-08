package com.pixelpals.app.core.motion

import kotlin.math.abs
import kotlin.math.max

/** Fixed camera locomotion: feet follow distance and travel starts/ends at rest. */
object GroundGait {
    fun progress(elapsed: Float, duration: Float): Float {
        val t: Float = (elapsed / duration.coerceAtLeast(.001f)).coerceIn(0f, 1f)
        return t * t * t * (t * (t * 6f - 15f) + 10f)
    }

    fun duration(distance: Float, maximumSpeed: Float, minimumDuration: Float): Float =
        max(minimumDuration, abs(distance) * 1.875f / maximumSpeed.coerceAtLeast(1f))

    fun phase(distance: Float, strideLength: Float): Float =
        abs(distance) / strideLength.coerceAtLeast(1f)
}
