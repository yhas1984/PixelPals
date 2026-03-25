package com.pixelpals.app.behavior
import com.pixelpals.app.PetState

import android.graphics.Canvas
import kotlin.math.sin
import kotlin.math.abs
import kotlin.random.Random

/**
 * GingerBehavior — Elegant cat with grooming, belly rubs, and affection system.
 */
class GingerBehavior(
    private val petView: PetViewBridge
) : PetBehavior {

    private var time = 0f
    var gingerPose = GingerPose.SITTING
    var gingerIdleTimer = 0f
    var gingerGroomingPhase = 0
    var gingerGroomingTimer = 0f
    var gingerAffectionLevel = 0
    var gingerWinkTimer = 0f
    var gingerIsWinking = false
    var gingerDoubleXPActive = false
    var gingerPurrIntensity = 0f

    enum class GingerPose { SITTING, STANDING, WALKING, BELLY_RUB }

    private val groomingFrames = listOf(4, 6, 5, 3, 4)
    private val groomingDurations = listOf(2.0f, 2.5f, 2.5f, 1.5f, 1.0f)

    override fun updateIdle(dt: Float) {
        time += dt

        when (gingerPose) {
            GingerPose.SITTING -> {
                // Organic grooming
                gingerGroomingTimer += dt
                if (gingerGroomingTimer >= groomingDurations[gingerGroomingPhase]) {
                    gingerGroomingTimer = 0f
                    gingerGroomingPhase = (gingerGroomingPhase + 1) % groomingFrames.size
                }
                petView.currentFrame = groomingFrames[gingerGroomingPhase]

                val breathe = sin(time * 1.5f) * 0.015f
                petView.animScaleY = 1f + breathe

                // Wink timer
                gingerWinkTimer += dt
                if (gingerWinkTimer >= 120f && !gingerIsWinking) {
                    gingerIsWinking = true
                    gingerDoubleXPActive = true
                    gingerWinkTimer = 0f
                    petView.currentFrame = 3
                    petView.showBubble("😉")
                }
            }
            GingerPose.STANDING -> {
                petView.currentFrame = 2
                petView.animOffsetX = sin(time * 1.8f) * 1.5f
                gingerIdleTimer += dt
                if (gingerIdleTimer > 6f) {
                    gingerPose = GingerPose.SITTING
                    gingerIdleTimer = 0f
                }
            }
            else -> { petView.currentFrame = 0 }
        }
    }

    override fun updateDrag(dt: Float) {
        petView.currentFrame = if (gingerPose == GingerPose.STANDING) 2 else 4
        petView.animRotation = 0f
        gingerPurrIntensity = (dt / 3f).coerceIn(0f, 1f)
        petView.animScaleY = 1f - gingerPurrIntensity * 0.03f
    }

    override fun updateFalling(dt: Float) {
        petView.currentFrame = 1
        petView.animRotation *= 0.7f
    }

    override fun updateJumping(dt: Float) {
        petView.currentFrame = 1
    }

    override fun updateAutonomous(dt: Float) {}

    override fun onInteract() {
        gingerAffectionLevel = (gingerAffectionLevel + 10).coerceAtMost(100)
        if (gingerDoubleXPActive) {
            petView.showBubble("😉💕")
        } else {
            petView.showBubble(listOf("💕", "✨", "😻", "🐾").random())
        }
        petView.playHaptic(40)
    }

    override fun updateInteracting(dt: Float) {
        when {
            dt < 0.5f -> {
                petView.currentFrame = 5
                petView.animScaleY = 0.95f
                petView.animScaleX = 1.05f
                petView.playHaptic(20)
            }
            dt < 1.8f -> {
                petView.currentFrame = 5
                petView.animScaleY = 1f + sin(dt * 5f) * 0.015f
            }
            dt < 3.0f -> {
                petView.currentFrame = 0
            }
            else -> {
                petView.currentFrame = if (gingerPose == GingerPose.SITTING) 4 else 2
                petView.state = PetState.IDLE
                reset()
            }
        }
    }

    override fun onDraw(canvas: Canvas, cx: Float, cy: Float) {}

    override fun reset() {
        gingerPurrIntensity = 0f
        petView.animAlpha = 1f
        petView.animScaleX = 1f
        petView.animScaleY = 1f
        petView.animRotation = 0f
    }

    override fun onFling(velocityX: Float, velocityY: Float) {
        gingerPose = GingerPose.BELLY_RUB
        petView.showBubble("😻")
        petView.playHaptic(120)
        petView.currentFrame = 5 // Belly rub pose
        petView.state = com.pixelpals.app.PetState.INTERACTING
    }
}
