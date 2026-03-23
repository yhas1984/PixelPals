package com.pixelpals.app.behavior
import com.pixelpals.app.PetState

import android.graphics.Canvas
import kotlin.math.sin
import kotlin.math.abs
import kotlin.random.Random

/**
 * CorgiBehavior — Playful dog with sitting/standing states.
 */
class CorgiBehavior(
    private val petView: PetViewBridge
) : PetBehavior {

    private var time = 0f
    var corgiPose = CorgiPose.SITTING
    var corgiIdleTimer = 0f
    var corgiWalkTimer = 0f

    enum class CorgiPose { SITTING, STANDING, WALKING, BARKING, JUMPING }

    override fun updateIdle(dt: Float) {
        time += dt
        corgiIdleTimer += dt

        when (corgiPose) {
            CorgiPose.SITTING -> {
                petView.currentFrame = 0
                petView.animOffsetY = sin(time * 1.5f) * 2f
                if (corgiIdleTimer > 4f) {
                    corgiPose = CorgiPose.STANDING
                    corgiIdleTimer = 0f
                }
            }
            CorgiPose.STANDING -> {
                petView.currentFrame = 1
                petView.animOffsetX = sin(time * 2f) * 2f
                if (corgiIdleTimer > 2f) {
                    corgiPose = CorgiPose.SITTING
                    corgiIdleTimer = 0f
                }
            }
            else -> { petView.currentFrame = 0 }
        }

        if (Random.nextFloat() < 0.003f) {
            petView.showBubble(listOf("¡Guau!", "❤️", "🦴", "🐾").random())
        }
    }

    override fun updateDrag(dt: Float) {
        petView.animRotation = sin(time * 15f) * 10f
    }

    override fun updateFalling(dt: Float) {
        petView.currentFrame = 5
    }

    override fun updateJumping(dt: Float) {
        petView.currentFrame = 5
    }

    override fun updateAutonomous(dt: Float) {}

    override fun onInteract() {
        petView.showBubble("❤️")
        petView.playHaptic(40)
    }

    override fun updateInteracting(dt: Float) {
        when {
            dt < 0.3f -> {
                petView.currentFrame = 7
                petView.animScaleY = 0.95f
                petView.playHaptic(30)
            }
            dt < 0.6f -> { petView.currentFrame = 8; petView.animOffsetY = -5f }
            dt < 0.9f -> {
                petView.currentFrame = 9
                petView.animScaleY = 1.1f
                petView.animScaleX = 1.1f
            }
            dt < 1.2f -> {
                petView.currentFrame = 10
                petView.animScaleY = 0.9f
                petView.animScaleX = 1.15f
            }
            dt < 2.0f -> {
                petView.currentFrame = 7
                petView.animScaleY = 1f
                petView.animScaleX = 1f
            }
            else -> {
                petView.currentFrame = 0
                corgiPose = CorgiPose.SITTING
                petView.state = PetState.IDLE
                reset()
            }
        }
    }

    override fun onDraw(canvas: Canvas, cx: Float, cy: Float) {}

    override fun reset() {
        petView.animScaleX = 1f
        petView.animScaleY = 1f
        petView.animRotation = 0f
    }
}
