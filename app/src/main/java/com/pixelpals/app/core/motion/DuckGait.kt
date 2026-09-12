package com.pixelpals.app.core.motion

/** Six drawings cover two waddling steps, with cadence tied to body travel. */
object DuckGait {
    fun phaseAt(distance: Float, spriteSize: Float): Float =
        GroundGait.phase(distance, spriteSize * .5f)

    fun cycleIndexAt(distance: Float, spriteSize: Float): Int =
        ((phaseAt(distance, spriteSize) % 1f) * 6f).toInt().coerceIn(0, 5)
}
