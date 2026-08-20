package com.pixelpals.app.core.motion

import kotlin.math.hypot

data class PetGestureConfig(
    val touchSlopPx: Float,
    val minimumFlingVelocityPxPerSecond: Float,
    val longPressTimeoutMillis: Long = 500L,
) {
    init {
        require(touchSlopPx >= 0f && touchSlopPx.isFinite()) { "Touch slop must be finite and non-negative" }
        require(minimumFlingVelocityPxPerSecond >= 0f && minimumFlingVelocityPxPerSecond.isFinite()) {
            "Minimum fling velocity must be finite and non-negative"
        }
        require(longPressTimeoutMillis > 0L) { "Long press timeout must be positive" }
    }
}

enum class PetGestureType {
    NONE,
    DRAG_STARTED,
    DRAGGED,
    TAP,
    HOLD_STARTED,
    HOLD_RELEASED,
    RELEASE,
    FLING,
    CANCEL,
}

data class PetGestureResult(
    val type: PetGestureType,
    val velocityX: Float = 0f,
    val velocityY: Float = 0f,
)

/** Pure gesture classifier shared by every overlay pet. */
class PetGestureRecognizer(
    private val config: PetGestureConfig,
) {
    private enum class State {
        IDLE,
        PENDING,
        HOLDING,
        DRAGGING,
    }

    private var downX = 0f
    private var downY = 0f
    private var downTimeMillis = 0L
    private var state = State.IDLE

    fun onDown(x: Float, y: Float, eventTimeMillis: Long = 0L): PetGestureResult {
        downX = x
        downY = y
        downTimeMillis = eventTimeMillis
        state = State.PENDING
        return PetGestureResult(PetGestureType.NONE)
    }

    fun onMove(x: Float, y: Float, eventTimeMillis: Long = 0L): PetGestureResult {
        if (state == State.IDLE) return PetGestureResult(PetGestureType.NONE)
        if (state == State.PENDING && hasReachedLongPress(eventTimeMillis)) {
            state = State.HOLDING
        }
        if (state != State.DRAGGING && hypot(x - downX, y - downY) < config.touchSlopPx) {
            return PetGestureResult(PetGestureType.NONE)
        }
        if (state == State.DRAGGING) return PetGestureResult(PetGestureType.DRAGGED)
        state = State.DRAGGING
        return PetGestureResult(PetGestureType.DRAG_STARTED)
    }

    fun onTime(eventTimeMillis: Long): PetGestureResult {
        if (state != State.PENDING || !hasReachedLongPress(eventTimeMillis)) {
            return PetGestureResult(PetGestureType.NONE)
        }
        state = State.HOLDING
        return PetGestureResult(PetGestureType.HOLD_STARTED)
    }

    fun onUp(velocityX: Float, velocityY: Float, eventTimeMillis: Long = 0L): PetGestureResult {
        val result: PetGestureResult = when (state) {
            State.PENDING -> if (hasReachedLongPress(eventTimeMillis)) {
                PetGestureResult(PetGestureType.HOLD_RELEASED)
            } else {
                PetGestureResult(PetGestureType.TAP)
            }
            State.HOLDING -> PetGestureResult(PetGestureType.HOLD_RELEASED)
            State.DRAGGING -> if (hypot(velocityX, velocityY) >= config.minimumFlingVelocityPxPerSecond) {
                PetGestureResult(PetGestureType.FLING, velocityX, velocityY)
            } else {
                PetGestureResult(PetGestureType.RELEASE, velocityX, velocityY)
            }
            State.IDLE -> PetGestureResult(PetGestureType.NONE)
        }
        reset()
        return result
    }

    fun onCancel(): PetGestureResult {
        reset()
        return PetGestureResult(PetGestureType.CANCEL)
    }

    private fun reset(): Unit {
        downX = 0f
        downY = 0f
        downTimeMillis = 0L
        state = State.IDLE
    }

    private fun hasReachedLongPress(eventTimeMillis: Long): Boolean =
        eventTimeMillis - downTimeMillis >= config.longPressTimeoutMillis
}
