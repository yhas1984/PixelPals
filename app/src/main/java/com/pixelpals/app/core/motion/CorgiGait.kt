package com.pixelpals.app.core.motion

/** One distance-based foot cycle shared by free locomotion and desktop fetch. */
object CorgiGait {
    private const val FIRST_FRAME: Int = 10
    private const val FRAME_COUNT: Int = 4
    private const val WALK_STRIDE: Float = .44f
    private const val RUN_STRIDE: Float = .34f

    fun frameAt(distance: Float, spriteSize: Float, running: Boolean = false): Int =
        FIRST_FRAME + cycleIndexAt(distance, spriteSize, running)

    fun cycleIndexAt(distance: Float, spriteSize: Float, running: Boolean = false): Int {
        val phase: Float = phaseAt(distance, spriteSize, running) % 1f
        return (phase * FRAME_COUNT).toInt().coerceIn(0, FRAME_COUNT - 1)
    }

    fun phaseAt(distance: Float, spriteSize: Float, running: Boolean = false): Float {
        val stride: Float = spriteSize * if (running) RUN_STRIDE else WALK_STRIDE
        return GroundGait.phase(distance, stride)
    }
}
