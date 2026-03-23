package com.pixelpals.app.behavior
import com.pixelpals.app.PetState

import android.graphics.Canvas
import kotlin.math.sin
import kotlin.math.abs
import kotlin.random.Random

/**
 * NubeMichiBehavior — Fluffy cloud cat that floats like a feather.
 * Transforms to pluma when falling.
 */
class NubeMichiBehavior(
    private val petView: PetViewBridge
) : PetBehavior {

    private var time = 0f
    var showPluma = false

    override fun updateIdle(dt: Float) {
        time += dt
        // Cloud-like floating
        val floatY = sin(time * 1.2f) * 15f
        val floatX = sin(time * 0.8f) * 8f
        petView.animOffsetY = floatY
        petView.animOffsetX = floatX

        // Breathing
        val breathe = sin(time * 1.5f) * 0.04f
        petView.animScaleY = 1f + breathe
        petView.animScaleX = 1f - breathe * 0.3f

        // Cycle frames
        val frameCycle = (time * 0.5f) % 4f
        petView.currentFrame = when {
            frameCycle < 1.5f -> 0
            frameCycle < 2.5f -> 1
            frameCycle < 3.5f -> 2
            else -> 3
        }

        showPluma = false
    }

    override fun updateDrag(dt: Float) {
        petView.animRotation = sin(time * 3f) * 15f
        showPluma = false
        petView.animAlpha = 1f
    }

    override fun updateFalling(dt: Float) {
        // Transform to pluma
        petView.animAlpha = 0f // Cat invisible
        showPluma = true
        petView.currentFrame = 0
    }

    override fun updateJumping(dt: Float) {
        petView.currentFrame = 2
    }

    override fun updateAutonomous(dt: Float) {
        // Rarely moves
    }

    override fun onInteract() {
        val reaction = listOf("☁️", "💕", "✨", "😻").random()
        petView.showBubble(reaction)
        petView.playHaptic(25)
        showPluma = false
        petView.animAlpha = 1f
    }

    override fun updateInteracting(dt: Float) {
        // Coqueta interaction
        petView.animAlpha = 1f
        when {
            dt < 0.5f -> {
                petView.currentFrame = 3
                petView.animScaleX = 1.08f
                petView.animScaleY = 1.12f
            }
            dt < 1.5f -> {
                petView.currentFrame = if ((dt * 3f).toInt() % 2 == 0) 0 else 1
                petView.animScaleY = 1f + sin(dt * 8f) * 0.03f
            }
            dt < 2.5f -> {
                petView.currentFrame = 2
                val breathe = sin(dt * 3f) * 0.02f
                petView.animScaleY = 1f + breathe
            }
            else -> {
                petView.state = PetState.IDLE
                reset()
            }
        }
    }

    override fun onDraw(canvas: Canvas, cx: Float, cy: Float) {
        // Pluma drawing handled by PetView
    }

    override fun reset() {
        petView.animAlpha = 1f
        petView.animScaleX = 1f
        petView.animScaleY = 1f
        petView.animRotation = 0f
    }
}
