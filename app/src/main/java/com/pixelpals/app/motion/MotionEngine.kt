package com.pixelpals.app.motion

data class MotionStepResult(val steps: Int, val stepDt: Float)

class MotionEngine(
    private val maxDeltaSeconds: Float = 0.12f,
    private val maxSubSteps: Int = 4
) {
    fun sanitizeDeltaSeconds(dt: Float): Float = if (!dt.isFinite() || dt <= 0f) 0f else dt.coerceAtMost(maxDeltaSeconds)

    fun splitDelta(dt: Float): MotionStepResult {
        val sanitized = sanitizeDeltaSeconds(dt)
        if (sanitized == 0f) return MotionStepResult(0, 0f)
        val steps = if (sanitized > maxDeltaSeconds / 2f) maxSubSteps else 1
        return MotionStepResult(steps, sanitized / steps)
    }
}
