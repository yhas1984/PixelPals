package com.pixelpals.app.core.motion

/** Fixed camera constants for Ginger's optional posture supplement. */
object GingerArtworkScale {
    const val GROUND: Float = 368f / 384f
    const val CELL: Float = .875f
    const val CARE_SEATED: Float = .65f
    /** Care REST cells carry the same authored camera scale as native Ginger. */
    fun careRest(frame: Int): Float = if (frame in 16..19) CELL / (15f / 16f) else CARE_SEATED

    fun frame(index: Int): Float = when (index) {
        0, 1 -> .68f
        2 -> .70f
        else -> 1f
    }
}
