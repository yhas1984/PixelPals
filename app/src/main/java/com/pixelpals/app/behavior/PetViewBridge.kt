package com.pixelpals.app.behavior
import com.pixelpals.app.PetState


/**
 * PetViewBridge — Interface for behaviors to access PetView state.
 * Avoids circular dependency between PetView and behaviors.
 */
interface PetViewBridge {
    // Animation state
    var currentFrame: Int
    var animScaleX: Float
    var animScaleY: Float
    var animOffsetX: Float
    var animOffsetY: Float
    var animRotation: Float
    var animAlpha: Float

    // Physics
    var velocityX: Float
    var velocityY: Float

    // State
    var state: PetState

    // Actions
    fun showBubble(text: String)
    fun playHaptic(durationMs: Long)
    fun invalidate()
}
