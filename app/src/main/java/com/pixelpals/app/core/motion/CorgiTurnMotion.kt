package com.pixelpals.app.core.motion

/** Pure seven-pose turn timeline for the optional Corgi standing artwork. */
object CorgiTurnMotion {
    const val DURATION_SECONDS: Float = .56f
    const val SEGMENT_SECONDS: Float = .08f

    data class Pose(val frame: Int, val mirrored: Boolean, val finished: Boolean)

    private val SEGMENTS: List<Pair<Int, Boolean>> = listOf(
        0 to false,
        20 to false,
        21 to false,
        22 to false,
        21 to true,
        20 to true,
        0 to true,
    )

    fun poseAt(elapsedSeconds: Float): Pose {
        val elapsed: Float = elapsedSeconds.coerceAtLeast(0f)
        if (elapsed >= DURATION_SECONDS) return Pose(0, true, true)
        val segment: Int = (elapsed / SEGMENT_SECONDS).toInt().coerceIn(0, SEGMENTS.lastIndex)
        val pose: Pair<Int, Boolean> = SEGMENTS[segment]
        return Pose(pose.first, pose.second, false)
    }
}
