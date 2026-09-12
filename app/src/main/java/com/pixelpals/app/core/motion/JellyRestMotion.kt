package com.pixelpals.app.core.motion

import com.pixelpals.app.core.care.scene.SpeciesCarePose

/** Renderer-independent sleep and wake choreography for Jelly's care artwork. */
data class JellyRestPose(
    val body: SpeciesCarePose,
    val frame: Int,
    val sleepAmount: Float,
)

object JellyRestMotion {
    /** Care REST lasts five seconds; scheduled transitions use Companion's 1.2 second wake window. */
    const val DURATION_SECONDS: Float = 5f
    const val SCHEDULED_TRANSITION_SECONDS: Float = 1.2f

    const val SLEEP_START: Float = .30f
    const val SLEEP_END: Float = .70f
    const val AWAKE_FRAME: Int = 20
    const val FIRST_SLEEP_FRAME: Int = 24
    const val LAST_SLEEP_FRAME: Int = 29
    const val SLEEP_SCALE_Y: Float = .86f

    /** Samples the five-second care sequence in normalized progress. */
    fun sampleCare(progress: Float, reduced: Boolean): JellyRestPose {
        val p: Float = progress.coerceIn(0f, 1f)
        if (reduced) {
            return if (p >= 1f) awake() else sleeping()
        }
        return when {
            p <= 0f -> awake()
            p < SLEEP_START -> entering(p / SLEEP_START)
            p <= SLEEP_END -> sleeping()
            p < 1f -> waking((p - SLEEP_END) / (1f - SLEEP_END))
            else -> awake()
        }
    }

    /** Samples a scheduled bed entry or wake over the full 1.2 second transition. */
    fun sampleScheduled(seconds: Float, waking: Boolean, reduced: Boolean): JellyRestPose {
        if (reduced) return if (waking) awake() else sleeping()
        val t: Float = (seconds / SCHEDULED_TRANSITION_SECONDS).coerceIn(0f, 1f)
        return if (waking) sampleCare(SLEEP_END + (1f - SLEEP_END) * t, false)
        else sampleCare(SLEEP_START * t, false)
    }

    private fun entering(linearPhase: Float): JellyRestPose {
        val t: Float = linearPhase.coerceIn(0f, 1f)
        val slot: Int = entryFrameSlot(t)
        val frame: Int = if (slot == 0) AWAKE_FRAME else FIRST_SLEEP_FRAME + slot - 1
        return pose(scaleY = lerp(1f, SLEEP_SCALE_Y, ease(t)), frame = frame, sleepAmount = ease(t))
    }

    private fun waking(linearPhase: Float): JellyRestPose {
        val t: Float = linearPhase.coerceIn(0f, 1f)
        val frame: Int = LAST_SLEEP_FRAME - wakeFrameSlot(t)
        return pose(scaleY = lerp(SLEEP_SCALE_Y, 1f, ease(t)), frame = frame, sleepAmount = 1f - ease(t))
    }

    /** Six linear artwork slots; epsilon keeps exact care boundaries deterministic. */
    private fun entryFrameSlot(linearPhase: Float): Int {
        val phase: Float = linearPhase.coerceIn(0f, 1f)
        val slot: Int = (phase * 6f + FRAME_BOUNDARY_EPSILON).toInt().coerceIn(0, 6)
        return slot
    }

    private fun wakeFrameSlot(linearPhase: Float): Int {
        val phase: Float = linearPhase.coerceIn(0f, 1f)
        val slot: Int = (phase * 6f + FRAME_BOUNDARY_EPSILON).toInt().coerceIn(0, 5)
        return slot
    }

    private fun sleeping(): JellyRestPose = pose(SLEEP_SCALE_Y, LAST_SLEEP_FRAME, 1f)

    private fun awake(): JellyRestPose = pose(1f, AWAKE_FRAME, 0f)

    private fun pose(scaleY: Float, frame: Int, sleepAmount: Float): JellyRestPose = JellyRestPose(
        body = SpeciesCarePose(scaleX = 1f / scaleY, scaleY = scaleY),
        frame = frame,
        sleepAmount = sleepAmount,
    )

    private fun ease(value: Float): Float {
        val t: Float = value.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun lerp(start: Float, end: Float, amount: Float): Float = start + (end - start) * amount

    private const val FRAME_BOUNDARY_EPSILON: Float = .00001f
}
