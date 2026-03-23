package com.pixelpals.app.behavior
import com.pixelpals.app.PetState

import android.graphics.Canvas
import android.view.animation.OvershootInterpolator
import kotlin.math.sin
import kotlin.random.Random

/**
 * JellyBehavior — Bouncy slime that wobbles and jumps.
 */
class JellyBehavior(
    private val petView: PetViewBridge
) : PetBehavior {

    private var time = 0f

    override fun updateIdle(dt: Float) {
        time += dt
        val sine = sin(time * Math.PI / 0.6f).toFloat()
        petView.animScaleY = 1.0f + sine * 0.06f
        petView.animScaleX = 1.0f - sine * 0.04f

        val wobbleCycle = (time * 1.2f) % 8f
        petView.currentFrame = when {
            wobbleCycle < 1.0f -> 0
            wobbleCycle < 2.0f -> 1
            wobbleCycle < 3.0f -> 2
            wobbleCycle < 4.0f -> 3
            wobbleCycle < 5.0f -> 4
            wobbleCycle < 6.0f -> 5
            wobbleCycle < 7.0f -> 6
            else -> 7
        }

        if (Random.nextFloat() < 0.006f) {
            val pop = listOf("✨", "💖", "🫧", "🍬", "🌈", "💫").random()
            petView.showBubble(pop)
        }
    }

    override fun updateDrag(dt: Float) {
        petView.animRotation = sin(time * 15f) * 10f
    }

    override fun updateFalling(dt: Float) {
        petView.currentFrame = 3
        petView.animAlpha = 0.8f
    }

    override fun updateJumping(dt: Float) {
        petView.currentFrame = 4
    }

    override fun updateAutonomous(dt: Float) {
        // Jelly bounces autonomously - handled in PetView
    }

    override fun onInteract() {
        petView.showBubble("🟢")
        petView.playHaptic(100)
    }

    override fun updateInteracting(dt: Float) {
        when {
            dt < 0.15f -> {
                petView.currentFrame = 8
                petView.animScaleX = 1.6f
                petView.animScaleY = 0.4f
                petView.playHaptic(80)
            }
            dt < 0.4f -> {
                petView.currentFrame = 9
                petView.animScaleX = 0.8f
                petView.animScaleY = 1.3f
            }
            dt < 0.8f -> {
                val t = (dt - 0.4f) / 0.4f
                val overshoot = OvershootInterpolator(2.5f).getInterpolation(t)
                petView.animScaleX = 0.8f + (1f - 0.8f) * overshoot
                petView.animScaleY = 1.3f + (1f - 1.3f) * overshoot
            }
            dt < 2.0f -> {
                petView.currentFrame = if ((dt * 4f).toInt() % 2 == 0) 5 else 6
                petView.animScaleY = 1f + sin(dt * 6f) * 0.04f
                petView.animScaleX = 1f - sin(dt * 6f) * 0.03f
            }
            else -> {
                petView.state = PetState.IDLE
                reset()
            }
        }
    }

    override fun onDraw(canvas: Canvas, cx: Float, cy: Float) {}

    override fun reset() {
        petView.animAlpha = 0.95f
        petView.animScaleX = 1f
        petView.animScaleY = 1f
        petView.animRotation = 0f
    }
}
