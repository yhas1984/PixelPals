package com.pixelpals.app.core.motion

/** Shared frame timeline for Ginger's optional curled rest artwork. */
object GingerRestMotion {
    const val STEP_SECONDS: Float = .18f
    val restFrames: List<Int> = listOf(18, 19, 20, 21)
    val seatedRestFrames: List<Int> = listOf(0, 16, 19, 20, 21)

    fun restFrame(elapsedSeconds: Float, startsSeated: Boolean): Int {
        val frames = if (startsSeated) seatedRestFrames else restFrames
        return frames[(elapsedSeconds / STEP_SECONDS).toInt().coerceIn(0, frames.lastIndex)]
    }

    fun wakeFrames(fromFrame: Int): List<Int> = when (fromFrame) {
        21 -> listOf(21, 20, 19, 18)
        20 -> listOf(20, 19, 18)
        19 -> listOf(19, 18)
        18 -> listOf(18)
        17 -> listOf(17, 18)
        16 -> listOf(16, 17, 18)
        else -> listOf(0, 16, 17, 18)
    }

    fun wakeFrame(elapsedSeconds: Float, fromFrame: Int): Int {
        val frames = wakeFrames(fromFrame)
        return frames[(elapsedSeconds / STEP_SECONDS).toInt().coerceIn(0, frames.lastIndex)]
    }

    fun wakeDuration(fromFrame: Int): Float = wakeFrames(fromFrame).size * STEP_SECONDS
}
