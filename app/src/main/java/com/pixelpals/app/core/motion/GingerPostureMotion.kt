package com.pixelpals.app.core.motion

/** Pure sitting/standing timeline for Ginger's optional frames 16..18. */
object GingerPostureMotion {
    const val DURATION_SECONDS: Float = .66f

    enum class Transition {
        STAND_UP,
        SIT_DOWN,
    }

    data class Pose(val frame: Int, val finished: Boolean)

    fun poseAt(transition: Transition, elapsedSeconds: Float): Pose {
        val elapsed: Float = elapsedSeconds.coerceAtLeast(0f)
        return when (transition) {
            Transition.STAND_UP -> when {
                elapsed < .10f -> Pose(0, false)
                elapsed < .28f -> Pose(16, false)
                elapsed < .48f -> Pose(17, false)
                elapsed < DURATION_SECONDS -> Pose(18, false)
                else -> Pose(18, true)
            }
            Transition.SIT_DOWN -> when {
                elapsed < .10f -> Pose(18, false)
                elapsed < .28f -> Pose(17, false)
                elapsed < .48f -> Pose(16, false)
                elapsed < DURATION_SECONDS -> Pose(0, false)
                else -> Pose(0, true)
            }
        }
    }
}
