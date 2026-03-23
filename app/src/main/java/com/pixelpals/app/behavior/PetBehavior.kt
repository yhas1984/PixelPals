package com.pixelpals.app.behavior

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

    /** Reset state */
    fun reset()
}
