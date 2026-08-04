package com.pixelpals.app.feature.overlay.behavior
import com.pixelpals.app.core.domain.PetState

import android.graphics.Canvas
import android.view.MotionEvent

/**
 * PetBehavior — Interface for pet-specific behaviors.
 *
 * Each pet type implements this interface to define its unique
 * animation, movement, and interaction logic.
 */
interface PetBehavior {

    /** Update idle animation */
    fun updateIdle(dt: Float)

    /** Update drag animation */
    fun updateDrag(dt: Float)

    /** Update falling animation */
    fun updateFalling(dt: Float)

    /** Update jumping animation */
    fun updateJumping(dt: Float)

    /** Update autonomous movement */
    fun updateAutonomous(dt: Float)

    /** Trigger interaction on tap */
    fun onInteract()

    /** Update interaction animation */
    fun updateInteracting(dt: Float)

    /** Draw pet-specific elements */
    fun onDraw(canvas: Canvas, cx: Float, cy: Float)

    /** Trigger action on fling/swipe gesture */
    fun onFling(velocityX: Float, velocityY: Float) {}

    /** Direct touch intercepts (return true if handled, preventing default drag) */
    fun onTouchDown(x: Float, y: Float): Boolean = false
    fun onTouchUp(): Boolean = false

    /** Reset state */
    fun reset()

    // --- Lifecycle and Events ---
    fun resume() {}
    fun pause() {}
    fun destroy() {}
    fun onTreasureConsumed(emoji: String) {}
    fun onBatteryStatusChanged(percent: Int, isCharging: Boolean) {}
    fun onKeyboardVisibilityChanged(visible: Boolean, height: Int) {}
    fun onAirplaneModeChanged(isAirplane: Boolean) {}
}
