package com.pixelpals.app.core.motion

/** Fixed camera constants for Ginger's optional posture supplement. */
object GingerArtworkScale {
    const val GROUND: Float = 368f / 384f
    const val CELL: Float = .875f

    fun frame(index: Int): Float = when (index) {
        0, 1 -> .68f
        2 -> .70f
        else -> 1f
    }
}
