package com.pixelpals.app.feature.overlay.behavior

/** Keeps Corgi facing inward at an edge without alternating on zero-pixel steps. */
internal object CorgiEdgeMotion {
    fun shouldReverse(
        positionX: Int,
        proposedX: Int,
        maxX: Int,
        direction: Float,
    ): Boolean {
        if (proposedX < 0 || proposedX > maxX) return true
        if (positionX <= 0 && direction < 0f) return true
        return positionX >= maxX && direction > 0f
    }
}
