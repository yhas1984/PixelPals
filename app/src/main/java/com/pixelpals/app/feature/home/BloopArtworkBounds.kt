package com.pixelpals.app.feature.home

import android.graphics.Rect

/** Original source cameras: erasing a shadow or wire must not refit the remaining body. */
internal object BloopArtworkBounds {
    private val legacyBounds = listOf(
        intArrayOf(109, 107, 658, 661),
        intArrayOf(88, 112, 679, 656),
        intArrayOf(188, 153, 579, 615),
    )
    fun legacy(cellSize: Int): List<Rect> = legacyBounds.map { b ->
        Rect(b[0] * cellSize / 768, b[1] * cellSize / 768,
            (b[2] * cellSize + 767) / 768, (b[3] * cellSize + 767) / 768)
    }
    private val careBounds = listOf(
        intArrayOf(26, 53, 230, 238),
        intArrayOf(34, 52, 221, 238),
        intArrayOf(39, 55, 216, 238),
        intArrayOf(26, 52, 229, 238),
        intArrayOf(28, 87, 228, 238),
        intArrayOf(18, 73, 238, 238),
        intArrayOf(34, 57, 221, 238),
        intArrayOf(42, 54, 214, 238),
        intArrayOf(43, 60, 213, 238),
        intArrayOf(41, 58, 215, 238),
        intArrayOf(37, 62, 218, 238),
        intArrayOf(40, 57, 216, 238),
        intArrayOf(37, 65, 218, 238),
        intArrayOf(39, 71, 216, 238),
        intArrayOf(40, 69, 215, 238),
        intArrayOf(44, 62, 212, 238),
        intArrayOf(38, 85, 218, 238),
        intArrayOf(18, 101, 238, 238),
        intArrayOf(36, 102, 220, 238),
        intArrayOf(28, 89, 228, 238),
        intArrayOf(38, 58, 218, 238),
        intArrayOf(36, 63, 220, 238),
        intArrayOf(40, 65, 216, 238),
        intArrayOf(40, 59, 215, 238),
    )
    fun care(cellSize: Int): List<Rect> = careBounds.map { b ->
        Rect(b[0] * cellSize / 256, b[1] * cellSize / 256,
            (b[2] * cellSize + 255) / 256, (b[3] * cellSize + 255) / 256)
    }
}
