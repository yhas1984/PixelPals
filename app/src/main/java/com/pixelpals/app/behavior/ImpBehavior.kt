package com.pixelpals.app.behavior
import com.pixelpals.app.PetState

import android.graphics.Canvas
import kotlin.math.sin
import kotlin.random.Random

/**
 * ImpBehavior — Mischievous winged imp with fire attacks.
 */
class ImpBehavior(
    private val petView: PetViewBridge
) : PetBehavior {

    private var time = 0f
    var impState = ImpState.LURKING
    var impRunTimer = 0f
    var impLurkTimer = 0f

    enum class ImpState { LURKING, RUNNING, FIRE_JUMP, SURPRISED }

    override fun updateIdle(dt: Float) {
        time += dt
        when (impState) {
            ImpState.LURKING -> {
                petView.currentFrame = 0
                impLurkTimer += dt
                petView.animOffsetY = sin(time * 1.5f) * 3f
                petView.animOffsetX = sin(time * 0.8f) * 2f
            }
            ImpState.RUNNING -> {
                impRunTimer += dt
                val flapCycle = impRunTimer % 0.5f
                petView.currentFrame = when {
                    flapCycle < 0.17f -> 0
                    flapCycle < 0.33f -> 1
                    else -> 2
                }
                if (impRunTimer > 2f) {
                    petView.velocityX = 0f
                    impState = ImpState.LURKING
                    impRunTimer = 0f
                }
            }
            ImpState.FIRE_JUMP -> {
                impRunTimer += dt
                petView.currentFrame = when {
                    impRunTimer < 0.25f -> 8
                    impRunTimer < 0.50f -> 9
                    impRunTimer < 0.80f -> 10
                    else -> {
                        impState = ImpState.LURKING
                        impRunTimer = 0f
                        0
                    }
                }
            }
            ImpState.SURPRISED -> {
                impRunTimer += dt
                petView.currentFrame = when {
                    impRunTimer < 0.12f -> 4
                    impRunTimer < 0.24f -> 5
                    impRunTimer < 0.36f -> 6
                    impRunTimer < 0.48f -> 7
                    else -> {
                        impState = ImpState.LURKING
                        impRunTimer = 0f
                        0
                    }
                }
            }
        }
    }

    override fun updateDrag(dt: Float) {
        petView.currentFrame = 4
    }

    override fun updateFalling(dt: Float) {
        petView.currentFrame = 0
        petView.animOffsetY = sin(time * 2f) * 3f
        if (petView.velocityY > 2f) petView.velocityY = -3f
    }

    override fun updateJumping(dt: Float) {
        petView.currentFrame = 0
    }

    override fun updateAutonomous(dt: Float) {}

    override fun onInteract() {
        impState = ImpState.FIRE_JUMP
        impRunTimer = 0f
        petView.currentFrame = 8
        petView.showBubble("🔥")
        petView.playHaptic(80)
    }

    override fun updateInteracting(dt: Float) {
        // Handled in updateIdle with FIRE_JUMP state
    }

    override fun onDraw(canvas: Canvas, cx: Float, cy: Float) {}

    override fun reset() {
        impState = ImpState.LURKING
        petView.animAlpha = 1f
        petView.animScaleX = 1f
        petView.animScaleY = 1f
    }
}
