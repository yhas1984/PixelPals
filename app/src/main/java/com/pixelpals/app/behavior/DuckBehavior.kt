package com.pixelpals.app.behavior
import com.pixelpals.app.PetState

import android.graphics.Canvas
import kotlin.math.sin
import kotlin.math.abs
import kotlin.random.Random

/**
 * DuckBehavior — Curious duck that waddles, swims, and quacks.
 */
class DuckBehavior(
    private val petView: PetViewBridge
) : PetBehavior {

    private var time = 0f
    var duckState = DuckState.IDLE_SIDE
    var duckIdleTime = 0f
    var duckWalkTimer = 0f
    var duckWalkDirection = 1f

    enum class DuckState { IDLE_SIDE, WALKING, CURIOSITY, QUACK_SUPREMO }

    override fun updateIdle(dt: Float) {
        time += dt
        duckIdleTime += dt

        when (duckState) {
            DuckState.IDLE_SIDE -> {
                val cycle = duckIdleTime % 3f
                petView.currentFrame = if (cycle < 1.5f) 0 else 3
            }
            DuckState.WALKING -> {
                duckWalkTimer += dt
                petView.currentFrame = if ((duckWalkTimer * 5f).toInt() % 2 == 0) 1 else 2
                petView.animOffsetY = abs(sin(duckWalkTimer * 10f)) * 3f
                if (duckWalkTimer > 2f) {
                    petView.velocityX = 0f
                    duckState = DuckState.IDLE_SIDE
                    duckWalkTimer = 0f
                }
            }
            DuckState.CURIOSITY -> {
                petView.currentFrame = if (duckIdleTime % 2f < 1f) 3 else 4
            }
            DuckState.QUACK_SUPREMO -> {
                duckWalkTimer += dt
                petView.currentFrame = when {
                    duckWalkTimer < 0.2f -> 3
                    duckWalkTimer < 0.5f -> 13
                    duckWalkTimer < 1.0f -> 14
                    else -> {
                        duckState = DuckState.IDLE_SIDE
                        duckWalkTimer = 0f
                        0
                    }
                }
            }
        }
    }

    override fun updateDrag(dt: Float) {
        petView.currentFrame = 0
    }

    override fun updateFalling(dt: Float) {
        petView.currentFrame = 1
        petView.animOffsetY = sin(time * 5f) * 3f
    }

    override fun updateJumping(dt: Float) {
        petView.currentFrame = 5
    }

    override fun updateAutonomous(dt: Float) {}

    override fun onInteract() {
        duckState = DuckState.QUACK_SUPREMO
        duckWalkTimer = 0f
        petView.showBubble("Quack!")
        petView.playHaptic(80)
    }

    override fun updateInteracting(dt: Float) {
        // Handled in updateIdle with QUACK_SUPREMO state
    }

    override fun onDraw(canvas: Canvas, cx: Float, cy: Float) {}

    override fun reset() {
        duckState = DuckState.IDLE_SIDE
        petView.animScaleX = 1f
        petView.animScaleY = 1f
    }
}
