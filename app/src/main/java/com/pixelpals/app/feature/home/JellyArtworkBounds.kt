package com.pixelpals.app.feature.home

import android.graphics.Rect

/** Care atlas cameras measured before the outline/shadow cleanup (alpha >= 16). */
internal object JellyArtworkBounds {
    private val careBounds = listOf(
        intArrayOf(24, 92, 232, 238), intArrayOf(34, 86, 221, 238),
        intArrayOf(34, 78, 221, 238), intArrayOf(33, 85, 223, 238),
        intArrayOf(27, 96, 228, 238), intArrayOf(19, 109, 237, 238),
        intArrayOf(30, 62, 225, 238), intArrayOf(40, 78, 216, 238),
        intArrayOf(33, 71, 222, 238), intArrayOf(35, 77, 221, 238),
        intArrayOf(18, 92, 238, 238), intArrayOf(41, 90, 215, 238),
        intArrayOf(31, 90, 224, 238), intArrayOf(34, 91, 222, 238),
        intArrayOf(23, 80, 232, 238), intArrayOf(37, 82, 219, 238),
        intArrayOf(36, 105, 220, 238), intArrayOf(29, 129, 227, 238),
        intArrayOf(29, 97, 227, 238), intArrayOf(28, 106, 228, 238),
        intArrayOf(40, 102, 215, 238), intArrayOf(41, 91, 215, 238),
        intArrayOf(43, 92, 213, 238), intArrayOf(40, 96, 216, 238),
    )

    fun care(cellSize: Int): List<Rect> = careBounds.map { b ->
        Rect(
            b[0] * cellSize / 256,
            b[1] * cellSize / 256,
            (b[2] * cellSize + 255) / 256,
            (b[3] * cellSize + 255) / 256,
        )
    }
}
