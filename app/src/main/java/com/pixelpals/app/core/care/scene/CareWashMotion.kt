package com.pixelpals.app.core.care.scene

data class CareWashState(val foam: Float, val rinse: Float, val drift: Float)

/** Wet, lather, rinse, clean. Reduced motion keeps readable lather but no drifting bubbles. */
object CareWashMotion {
    fun sample(progress: Float, reduced: Boolean): CareWashState {
        val time: Float = progress.coerceIn(0f, 1f)
        val lather: Float = ((time - .08f) / .25f).coerceIn(0f, 1f)
        val rinse: Float = ((time - .65f) / .29f).coerceIn(0f, 1f)
        return CareWashState(lather * (1f - rinse), rinse, if (reduced) 0f else rinse * .12f)
    }
}
