package com.pixelpals.app.core.rest

/** Reduced-motion repositioning occurs only in a fully invisible rendered frame. */
class RestFadeTransition {
    private var elapsed: Float = 0f
    private var hidden: Boolean = false
    private var moved: Boolean = false
    var active: Boolean = false
        private set
    var opacity: Float = 1f
        private set

    fun start() {
        elapsed = 0f
        moved = false
        hidden = false
        opacity = 1f
        active = true
    }

    /** Returns true exactly once, at zero opacity, to relocate the actor. */
    fun advance(delta: Float): Boolean {
        if (!active) return false
        elapsed += if (delta.isFinite()) delta.coerceIn(0f, .05f) else 0f
        if (!moved && hidden) {
            moved = true
            elapsed = 0f
            return true
        }
        if (!moved) {
            opacity = (1f - elapsed / HALF_DURATION).coerceIn(0f, 1f)
            if (elapsed >= HALF_DURATION) {
                elapsed = 0f
                hidden = true
                opacity = 0f
            }
        } else {
            opacity = (elapsed / HALF_DURATION).coerceIn(0f, 1f)
            if (elapsed >= HALF_DURATION) active = false
        }
        return false
    }

    fun cancel() {
        active = false
        opacity = 1f
    }

    private companion object { const val HALF_DURATION: Float = .15f }
}
