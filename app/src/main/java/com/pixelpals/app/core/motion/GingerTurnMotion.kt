package com.pixelpals.app.core.motion

/** Shared planted turn timeline for Ginger's optional turn supplement. */
object GingerTurnMotion {
    const val DURATION_SECONDS: Float = .50f
    const val COMMIT_SECONDS: Float = .30f

    data class Pose(val frame: Int, val mirrored: Boolean, val finished: Boolean)

    fun poseAt(elapsedSeconds: Float): Pose {
        val elapsed = elapsedSeconds.coerceAtLeast(0f)
        return when {
            elapsed < .10f -> Pose(18, false, false)
            elapsed < .20f -> Pose(22, false, false)
            elapsed < .30f -> Pose(23, false, false)
            elapsed < .40f -> Pose(22, true, false)
            elapsed < DURATION_SECONDS -> Pose(18, true, false)
            else -> Pose(18, true, true)
        }
    }
}
