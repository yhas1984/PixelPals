package com.pixelpals.app.feature.overlay.behavior
import com.pixelpals.app.core.domain.PetState

import android.graphics.Canvas
import android.view.MotionEvent
import com.pixelpals.app.status.PetStatusSnapshot

/**
 * PetBehavior — Interface for pet-specific behaviors.
 *
 * Each pet type implements this interface to define its unique
 * animation, movement, and interaction logic.
 */
interface PetBehavior {

    /** Actual autonomous sleep, distinct from a sleepy mood or an idle blink. */
    val isSleeping: Boolean get() = false

    /** Request a natural stopping point without interrupting committed movement. */
    fun onScheduledRestRequested(requested: Boolean) {}

    /** Optional accessibility transition while ordinary autonomous motion is disabled. */
    fun advanceScheduledRestTransition(delta: Float, reducedMotion: Boolean) {}

    /** Safe handoff to scheduled rest, after committed jumps and care reactions finish. */
    fun canStartScheduledSleep(reducedMotion: Boolean): Boolean = true

    /** Carry an already seated pose into sleep instead of repeating the sit-down. */
    val isSeatedForScheduledRest: Boolean get() = false

    /** Optional shared rest atlas pose already reached before the schedule takes over. */
    val scheduledRestFrame: Int? get() = null

    /** The shared wake renderer has reached its upright endpoint. */
    fun onScheduledWakeCompleted() {}

    /** Preserve a shared atlas pose when direct input takes over scheduled sleep. */
    fun onScheduledSleepInterrupted(frame: Int) {}

    /** Semantic facing, independent of whether the source artwork was drawn mirrored. */
    val facingLeft: Boolean? get() = null
    /** Current rendered ground contact relative to the pet window's vertical center. */
    val careBaselineOffsetY: Float? get() = null

    /** True when this behavior, rather than PetView, owns drag and release physics. */
    val usesRuntimeInput: Boolean get() = false

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

    /** Trigger the pet-specific response to a deliberate press and hold. */
    fun onHold() { onInteract() }
    fun onHoldReleased() {}

    fun onDragStart(pointerX: Float, pointerY: Float, grabOffsetX: Float, grabOffsetY: Float) {}
    fun onDragMove(pointerX: Float, pointerY: Float) {}
    fun onRelease(velocityX: Float, velocityY: Float) {}
    fun onGestureCancelled() {}

    /** Returns null while no alpha mask is available, allowing the legacy fallback. */
    fun hitTest(localX: Float, localY: Float, viewWidth: Int, viewHeight: Int): Boolean? = null

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

    /** Rebuild any cached trajectory after the window has been clamped to new bounds. */
    fun onViewportChanged() { reset() }

    // --- Lifecycle and Events ---
    fun resume() {}
    fun pause() {}
    fun destroy() {}
    fun onTreasureConsumed(emoji: String) {}
    fun onBatteryStatusChanged(percent: Int, isCharging: Boolean) {}
    fun onBatteryTemperatureChanged(temperatureCelsius: Float?) {}
    fun onKeyboardVisibilityChanged(visible: Boolean, height: Int) {}
    fun onAirplaneModeChanged(isAirplane: Boolean) {}
    fun onStatusChanged(previous: PetStatusSnapshot, current: PetStatusSnapshot) {}
}
