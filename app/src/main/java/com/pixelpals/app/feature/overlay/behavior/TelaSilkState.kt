package com.pixelpals.app.feature.overlay.behavior

/** Screen-space geometry for Tela's temporary web overlay. */
data class TelaSilkState(
    val anchorX: Float,
    val anchorY: Float,
    val targetX: Float,
    val targetY: Float,
    val sway: Float,
    val alpha: Float = 1f,
)
