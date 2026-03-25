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
    private var stateTimer = 0f
    private var frameTimer = 0f
    private var currentAnimFrame = 0

    private enum class State { IDLE, DRIFTING, SHY, GLOWING }
    private var flyState = State.IDLE

    // Escaping targets
    private var escapeTargetX = 0f
    private var escapeTargetY = 0f

    override fun updateIdle(dt: Float) {
        time += dt
        stateTimer += dt
        frameTimer += dt

        updateFrames()

        when (flyState) {
            State.IDLE -> {
                // Anti-gravity: Cancel out generic falling and float with sine wave
                val sineMove = sin(System.currentTimeMillis() / 400.0).toFloat() * 2f
                petView.windowY = (petView.windowY + sineMove).toInt().coerceIn(100, petView.screenHeight - 200)

                petView.animOffsetX = cos(time * 1.5f) * 10f

                // High base opacity
                if (petView.animAlpha < 0.8f) petView.animAlpha += dt

                // Randomly start drifting or glowing
                if (stateTimer > 4f + Random.nextFloat() * 3f) {
                    if (Random.nextFloat() < 0.2f) {
                        changeState(State.GLOWING)
                    } else {
                        startDrifting()
                    }
                }
            }
            State.DRIFTING -> {
                // Ethereal drift towards target
                val dx = escapeTargetX - petView.windowX
                val dy = escapeTargetY - petView.windowY
                val dist = Math.abs(dx) + Math.abs(dy)

                if (dist > 20f) {
                    val speed = 60f * dt
                    petView.windowX += (Math.signum(dx) * speed).toInt()
                    petView.windowY += (Math.signum(dy) * speed).toInt()
                    
                    // Facing direction
                    petView.animScaleX = if (dx < 0) -1f else 1f
                } else {
                    changeState(State.IDLE)
                }

                if (stateTimer > 6f) changeState(State.IDLE)
            }
            State.SHY -> {
                // Quickly flee to the corner
                val dx = escapeTargetX - petView.windowX
                val dy = escapeTargetY - petView.windowY
                val dist = Math.abs(dx) + Math.abs(dy)

                if (dist > 20f) {
                    val speed = 400f * dt // High speed for fleeing
                    petView.windowX += (Math.signum(dx) * speed).toInt()
                    petView.windowY += (Math.signum(dy) * speed).toInt()
                }

                // Shivering
                petView.animOffsetX = sin(time * 30f) * 5f

                if (stateTimer > 2.0f) {
                    changeState(State.IDLE)
                }
            }
            State.GLOWING -> {
                // Happy floating in place
                val sineMove = sin(time * 3f) * 3f
                petView.windowY = (petView.windowY + sineMove).toInt().coerceIn(100, petView.screenHeight - 200)

                if (stateTimer > 3f) {
                    changeState(State.IDLE)
                }
            }
        }
    }

    private fun updateFrames() {
        val duration = when (flyState) {
            State.IDLE -> 0.40f
            State.DRIFTING -> 0.20f
            State.SHY -> 0.10f
            State.GLOWING -> 0.15f
        }

        if (frameTimer >= duration) {
            frameTimer = 0f
            
            when (flyState) {
                State.IDLE -> {
                    currentAnimFrame = if (currentAnimFrame == 0) 1 else 0
                    petView.currentFrame = currentAnimFrame
                }
                State.DRIFTING -> {
                    currentAnimFrame = if (currentAnimFrame == 2) 3 else 2
                    petView.currentFrame = currentAnimFrame
                }
                State.SHY -> {
                    // Sequence: 4 -> 5 -> 6 (and stays in 6)
                    if (currentAnimFrame < 4) currentAnimFrame = 4
                    else if (currentAnimFrame < 6) currentAnimFrame++
                    petView.currentFrame = currentAnimFrame
                }
                State.GLOWING -> {
                    currentAnimFrame = if (currentAnimFrame == 7) 8 else 7
                    petView.currentFrame = currentAnimFrame
                }
            }
        }
    }

    private fun changeState(newState: State) {
        if (flyState == newState) return
        flyState = newState
        stateTimer = 0f
        frameTimer = durationForNewFrame(newState) // Force immediate frame update
        
        // Reset effects
        petView.animOffsetX = 0f
        petView.animRotation = 0f
        if (newState != State.SHY) {
            petView.animAlpha = 0.8f
        }
    }

    private fun durationForNewFrame(s: State): Float = 100f

    private fun startDrifting() {
        escapeTargetX = Random.nextFloat() * (petView.screenWidth - 100f) + 50f
        escapeTargetY = Random.nextFloat() * (petView.screenHeight - 200f) + 50f
        changeState(State.DRIFTING)
    }

    override fun updateDrag(dt: Float) {
        petView.currentFrame = 4 // Surprised eyes while dragging
        petView.animAlpha = 0.5f
        petView.animRotation = 0f
    }

    override fun updateFalling(dt: Float) {}
    override fun updateJumping(dt: Float) {}
    override fun updateAutonomous(dt: Float) {}

    override fun onInteract() {
        if (flyState != State.SHY) {
            // Scare the ghost!
            changeState(State.SHY)
            petView.animAlpha = 0.3f
            petView.playHaptic(80)
            
            // Flee to opposite corner
            val isLeft = petView.windowX < petView.screenWidth / 2
            val isTop = petView.windowY < petView.screenHeight / 2
            
            escapeTargetX = if (isLeft) (petView.screenWidth - 100).toFloat() else 50f
            escapeTargetY = if (isTop) (petView.screenHeight - 200).toFloat() else 50f
        }
    }

    override fun onTouchDown(x: Float, y: Float): Boolean {
        // Intercept all touches to prevent dragging!
        onInteract()
        return true
    }

    override fun updateInteracting(dt: Float) {
        // Handled in IDLE state machine so it operates while floating away
        updateIdle(dt)
    }

    override fun onDraw(canvas: Canvas, cx: Float, cy: Float) {}

    override fun reset() {
        flyState = State.IDLE
        petView.animAlpha = 0.8f
        petView.animScaleX = 1f
        petView.animScaleY = 1f
        petView.animOffsetX = 0f
        petView.animOffsetY = 0f
        petView.animRotation = 0f
    }
}
