package com.pixelpals.app.core.motion

/** Pure frame timeline for the optional Corgi posture artwork. */
object CorgiPostureMotion {
    const val DURATION_SECONDS: Float = .52f
    enum class Transition {
        SIT_DOWN,
        STAND_UP,
    }

    data class Pose(val frame: Int, val finished: Boolean)

    fun poseAt(transition: Transition, elapsedSeconds: Float): Pose {
        val elapsed: Float = elapsedSeconds.coerceAtLeast(0f)
        return when (transition) {
            Transition.SIT_DOWN -> when {
                elapsed < 0.10f -> Pose(0, false)
                elapsed < 0.25f -> Pose(15, false)
                elapsed < 0.42f -> Pose(16, false)
                elapsed < 0.52f -> Pose(17, false)
                else -> Pose(17, true)
            }
            Transition.STAND_UP -> when {
                elapsed < 0.10f -> Pose(17, false)
                elapsed < 0.25f -> Pose(16, false)
                elapsed < 0.42f -> Pose(15, false)
                elapsed < 0.52f -> Pose(0, false)
                else -> Pose(0, true)
            }
        }
    }

    /** Rest pose with a short blink that leaves the body silhouette unchanged. */
    fun restingFrame(elapsedSeconds: Float): Int {
        val cycle: Float = elapsedSeconds.coerceAtLeast(0f) % 2.8f
        return when {
            cycle < 2.20f -> 17
            cycle < 2.28f -> 18
            cycle < 2.36f -> 19
            cycle < 2.44f -> 18
            else -> 17
        }
    }
}
