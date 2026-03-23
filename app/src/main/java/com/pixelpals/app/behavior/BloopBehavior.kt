package com.pixelpals.app.behavior
import com.pixelpals.app.PetState

import android.graphics.Canvas
import kotlin.math.sin
import kotlin.math.cos
import kotlin.random.Random

/**
 * BloopBehavior — Ethereal ghost that floats.
 * Simple behavior: floats with sine wave, fades in/out.
 */
class BloopBehavior(
    private val petView: PetViewBridge
) : PetBehavior {

    private var time = 0f

    override fun updateIdle(dt: Float) {
        time += dt
        // Ethereal floating
        petView.animOffsetY = sin(time * 2.0f) * 20f
        petView.animOffsetX = cos(time * 1.5f) * 10f
        petView.animAlpha = 0.75f + sin(time * 3f) * 0.15f

        // Cycle through frames
        val floatCycle = (time * 0.6f) % 8f
        petView.currentFrame = when {
            floatCycle < 1.5f -> 0
            floatCycle < 2.5f -> 1
            floatCycle < 3.5f -> 2
            floatCycle < 4.5f -> 3
            floatCycle < 5.5f -> 4
            floatCycle < 6.5f -> 5
            floatCycle < 7.5f -> 6
            else -> 7
        }

        // Random cute reactions
        if (Random.nextFloat() < 0.004f) {
            val pop = listOf("👻", "🫧", "✨", "💫", "🌙", "⭐").random()
            petView.showBubble(pop)
        }
    }

    override fun updateDrag(dt: Float) {
        petView.animRotation = 0f
    }

    override fun updateFalling(dt: Float) {
        // Bloop doesn't fall - floats back to idle
        petView.state = PetState.IDLE
        petView.velocityY = 0f
        petView.animRotation = 0f
    }

    override fun updateJumping(dt: Float) {
        // Bloop doesn't really jump, just floats
        petView.state = PetState.IDLE
    }

    override fun updateAutonomous(dt: Float) {
        // Drift slowly
        petView.animOffsetX = sin(time * 0.5f) * 15f
    }

    override fun onInteract() {
        petView.showBubble("👻")
        petView.playHaptic(30)
    }

    override fun updateInteracting(dt: Float) {
        // Shrink and fade - shy ghost
        if (dt > 1.5f) {
            petView.animAlpha = 0.3f
            petView.animScaleX = 0.8f
            petView.animScaleY = 0.8f
        } else {
            petView.animScaleX = 0.9f
            petView.animScaleY = 0.9f
        }
    }

    override fun onDraw(canvas: Canvas, cx: Float, cy: Float) {
        // No special drawing for Bloop
    }

    override fun reset() {
        petView.animAlpha = 0.8f
        petView.animScaleX = 1f
        petView.animScaleY = 1f
        petView.animOffsetX = 0f
        petView.animOffsetY = 0f
        petView.animRotation = 0f
    }
}
