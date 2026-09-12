package com.pixelpals.app.core.motion

/** Camera correction for the current 40-frame Tela motion atlas, not species size. */
object TelaArtworkScale {
    // Open-eye center spacing: idle ~75px, descent ~60px, first hanging pose ~83px.
    // Leg spread is deliberately excluded: folding the legs must not inflate the head.
    // Other clips remain unchanged until their anatomical references are reviewed.
    fun frame(index: Int): Float = when (index) {
        8 -> 1.14f
        9 -> 1.21f
        10 -> 1.10f
        11 -> 1.25f
        12 -> 1.17f
        13 -> 1.20f
        14 -> 1.10f
        15 -> 1.11f
        16 -> .82f
        17 -> .77f
        18 -> .82f
        19 -> .91f
        in 20..23 -> 1.25f
        24 -> .90f
        else -> 1f
    }

    fun mirrored(index: Int): Boolean = index in 8..11
    fun ground(index: Int): Float = if (index in 4..11) 367f / 384f else .5f
}
