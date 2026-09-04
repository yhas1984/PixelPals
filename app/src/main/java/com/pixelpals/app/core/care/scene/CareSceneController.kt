package com.pixelpals.app.core.care.scene

import kotlin.math.hypot

data class CarePoint(val x: Float, val y: Float)

data class CareSceneTiming(val durationMs: Long, val completionMs: Long, val contactMs: Long = 1_500L) {
    init {
        require(durationMs > 0 && completionMs in 1..durationMs && contactMs > 0)
    }
}

/** Pure scene clock/input. Coordinates are normalized to the rendered stage. */
class CareSceneController(
    val action: CareSceneAction,
    val mode: CareSceneMode,
    val timing: CareSceneTiming,
    val playVariation: CarePlayVariation = CarePlayVariation.DIRECT,
) {
    var elapsedMs: Long = 0L
        private set
    var animationMs: Long = 0L
        private set
    var prop: CarePoint? = null
        private set
    var hasContact: Boolean = mode == CareSceneMode.AUTOMATIC
        private set
    var isComplete: Boolean = false
        private set
    var isCancelled: Boolean = false
        private set
    private var contactMs: Long = 0L
    private var isStroking: Boolean = false
    private var lastPointer: CarePoint? = null
    private var pendingStroke: Boolean = false
    private var didEmitCompletion: Boolean = false
    val progress: Float get() = (animationMs.toFloat() / timing.durationMs).coerceIn(0f, 1f)

    fun movePointer(point: CarePoint, target: CarePoint, isDown: Boolean): Unit {
        // Once accepted, finish the interaction at its original target. A second
        // pointer must not retarget a thrown ball or flip the actor mid-chase.
        if (mode != CareSceneMode.MANUAL || hasContact || isComplete || isCancelled) return
        prop = point
        val near: Boolean = hypot(point.x - target.x, point.y - target.y) <= CONTACT_RADIUS
        val strokeAction: Boolean = action == CareSceneAction.PET || action == CareSceneAction.CLEAN
        val previous: CarePoint? = lastPointer
        if (strokeAction) {
            isStroking = near && isDown
            pendingStroke = pendingStroke || (isStroking && previous != null &&
                hypot(point.x - previous.x, point.y - previous.y) > MIN_STROKE_DISTANCE)
        } else if (near && (action != CareSceneAction.PLAY || !isDown)) {
            hasContact = true
        } else if (action == CareSceneAction.PLAY && !isDown && point.x in 0f..1f && point.y in 0f..1f) {
            // The pet follows the released ball; catch is at the scene's completion marker.
            hasContact = true
        }
        lastPointer = if (isDown) point else null
    }

    /** Returns true exactly once, when persistence should be requested. */
    fun advance(deltaMs: Long): Boolean {
        if (isCancelled || isComplete || deltaMs <= 0L) return false
        elapsedMs += deltaMs
        if (pendingStroke && isStroking) contactMs += deltaMs.coerceAtMost(100L)
        pendingStroke = false
        if (contactMs >= timing.contactMs) hasContact = true
        if (hasContact) animationMs = (animationMs + deltaMs).coerceAtMost(timing.durationMs)
        if (!didEmitCompletion && animationMs >= timing.completionMs) {
            didEmitCompletion = true
            return true
        }
        if (mode == CareSceneMode.MANUAL && elapsedMs >= MANUAL_TIMEOUT_MS && !didEmitCompletion) {
            isCancelled = true
        }
        isComplete = animationMs >= timing.durationMs
        return false
    }

    fun cancel(): Unit { isCancelled = true }

    companion object {
        const val MANUAL_TIMEOUT_MS: Long = 15_000L
        private const val CONTACT_RADIUS: Float = 0.18f
        private const val MIN_STROKE_DISTANCE: Float = 0.002f
    }
}
