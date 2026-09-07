package com.pixelpals.app.core.motion

/** Keeps fractional window movement instead of discarding it at high refresh rates. */
class SubpixelTravel {
    private var remainder: Float = 0f
    fun advance(distance: Float): Int {
        remainder += distance
        val pixels: Int = remainder.toInt()
        remainder -= pixels
        return pixels
    }
    fun reset(): Unit { remainder = 0f }
}
