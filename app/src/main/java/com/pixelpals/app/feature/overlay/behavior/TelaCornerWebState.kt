package com.pixelpals.app.feature.overlay.behavior

enum class TelaWebCorner {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_RIGHT,
    BOTTOM_LEFT,
}

/** A short-lived decorative web left at a screen corner by Tela. */
data class TelaCornerWebState(
    val corner: TelaWebCorner,
    val centerX: Float,
    val centerY: Float,
    val radius: Float,
    val alpha: Float = 1f,
)
