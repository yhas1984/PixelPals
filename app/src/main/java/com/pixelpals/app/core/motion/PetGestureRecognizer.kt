package com.pixelpals.app.core.motion

import kotlin.math.hypot

data class PetGestureConfig(
    val touchSlopPx: Float,
    val minimumFlingVelocityPxPerSecond: Float,
) {
    init {
        require(touchSlopPx >= 0f && touchSlopPx.isFinite()) { "Touch slop must be finite and non-negative" }
        require(minimumFlingVelocityPxPerSecond >= 0f && minimumFlingVelocityPxPerSecond.isFinite()) {
            "Minimum fling velocity must be finite and non-negative"
        }
    }
}

enum class PetGestureType {
    NONE,
    DRAG_STARTED,
    DRAGGED,
    TAP,
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
    private var downX = 0f
    private var downY = 0f
    private var hasMoved = false

    fun onDown(x: Float, y: Float): PetGestureResult {
        downX = x
        downY = y
        hasMoved = false
        return PetGestureResult(PetGestureType.NONE)
    }

    fun onMove(x: Float, y: Float): PetGestureResult {
        if (!hasMoved && hypot(x - downX, y - downY) < config.touchSlopPx) {
            return PetGestureResult(PetGestureType.NONE)
        }
        val type = if (hasMoved) PetGestureType.DRAGGED else PetGestureType.DRAG_STARTED
        hasMoved = true
        return PetGestureResult(type)
    }

    fun onUp(velocityX: Float, velocityY: Float): PetGestureResult {
        val result = when {
            !hasMoved -> PetGestureResult(PetGestureType.TAP)
            hypot(velocityX, velocityY) >= config.minimumFlingVelocityPxPerSecond -> {
                PetGestureResult(PetGestureType.FLING, velocityX, velocityY)
            }
            else -> PetGestureResult(PetGestureType.RELEASE, velocityX, velocityY)
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
        hasMoved = false
    }
}
