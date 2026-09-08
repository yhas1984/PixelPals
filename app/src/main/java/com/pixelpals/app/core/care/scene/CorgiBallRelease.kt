package com.pixelpals.app.core.care.scene

/** Fixed-time gravity and one damped bounce; sampling is independent of render frame rate. */
object CorgiBallRelease {
    const val HOLD_MS: Long = 450L
    private const val FALL_SECONDS: Float = .34f
    private const val BOUNCE_HEIGHT: Float = .14f
    // The bounce uses the same gravity as the fall; only impact energy decreases.
    private val bounceSeconds: Float = 2f * FALL_SECONDS * kotlin.math.sqrt(BOUNCE_HEIGHT)
    fun height(mouthY: Float, floorY: Float, seconds: Float): Float {
        val distance: Float = (floorY - mouthY).coerceAtLeast(0f)
        val fall: Float = (seconds / FALL_SECONDS).coerceIn(0f, 1f)
        if (seconds <= FALL_SECONDS) return mouthY + distance * fall * fall
        val bounce: Float = ((seconds - FALL_SECONDS) / bounceSeconds).coerceIn(0f, 1f)
        return floorY - distance * BOUNCE_HEIGHT * 4f * bounce * (1f - bounce)
    }
}
