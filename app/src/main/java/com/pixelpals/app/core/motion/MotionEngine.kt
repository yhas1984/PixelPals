package com.pixelpals.app.core.motion

data class MotionStepResult(val steps: Int, val stepDt: Float)

/**
 * Timestep fijo con acumulador: la simulación avanza siempre en pasos de
 * [fixedStepSeconds] (1/60 s) y el resto de tiempo se conserva en el
 * acumulador en lugar de descartarse. Así la física no depende del FPS real
 * (30/60/120 Hz producen la misma simulación) y un spike no pierde tiempo:
 * solo se limita el catch-up por frame a [maxStepsPerFrame].
 */
class MotionEngine(
    private val fixedStepSeconds: Float = DEFAULT_FIXED_STEP_SECONDS,
    private val maxStepsPerFrame: Int = DEFAULT_MAX_STEPS_PER_FRAME
) {
    private var accumulator = 0f

    fun sanitizeDeltaSeconds(dt: Float): Float =
        if (!dt.isFinite() || dt <= 0f) 0f else dt.coerceAtMost(maxStepsPerFrame * fixedStepSeconds)

    fun splitDelta(dt: Float): MotionStepResult {
        val sanitized = sanitizeDeltaSeconds(dt)
        if (sanitized == 0f) return MotionStepResult(0, 0f)
        accumulator += sanitized
        var steps = 0
        while (steps < maxStepsPerFrame && accumulator >= fixedStepSeconds - EPSILON) {
            accumulator -= fixedStepSeconds
            steps++
        }
        return MotionStepResult(steps, fixedStepSeconds)
    }

    /** Descarta el tiempo acumulado (pausa/resume del bucle). */
    fun resetAccumulator() {
        accumulator = 0f
    }

    private companion object {
        const val DEFAULT_FIXED_STEP_SECONDS = 1f / 60f
        const val DEFAULT_MAX_STEPS_PER_FRAME = 15
        const val EPSILON = 1e-6f
    }
}
