package com.pixelpals.app.behavior

import android.graphics.Canvas
import com.pixelpals.app.PetState
import kotlin.math.sin
import kotlin.math.abs
import kotlin.math.sign
import kotlin.random.Random

/**
 * ImpBehavior v7 — Diablillo con comportamiento coherente
 *
 * Frames (10):
 * 0-1: Frente (alas arriba/abajo) - IDLE/VOLANDO VERTICAL
 * 2-3: Perfil (alas arriba/abajo) - VOLANDO HORIZONTAL
 * 4: Infla cachetes - FUEGO (solo al tocar)
 * 5: Llamarada - FUEGO (solo al tocar)
 * 6: Humo - FUEGO (solo al tocar)
 * 7-8: Brazos escalada - TREPAR
 * 9: Mirar atrás - TREPAR
 */
class ImpBehavior(
    private val petView: PetViewBridge
) : PetBehavior {

    // ══════════════════════════════════════════════════════════
    // ▌ ESTADOS
    // ══════════════════════════════════════════════════════════

    private enum class State {
        IDLE_FRONT,       // Quieto de frente flotando
        FLY_VERTICAL,     // Sube/baja de frente
        FLY_HORIZONTAL,   // Se desplaza de perfil con tilt
        CLIMBING,         // Trepando por borde
        FIRE_ATTACK       // Solo al tocar: cachetes→llamarada→humo
    }

    private var state = State.IDLE_FRONT
    private var stateTimer = 0f
    private var globalTime = 0f

    // Frame animation
    private var frameTimer = 0f
    private var currentAnimFrame = 0

    // Movement targets
    private var targetX = 0f
    private var targetY = 0f

    // Climbing
    private var climbEdge = 0f // 0 = left, screenWidth = right
    private var climbDir = 1f  // 1 = up, -1 = down

    // ══════════════════════════════════════════════════════════
    // ▌ PET BEHAVIOR
    // ══════════════════════════════════════════════════════════

    override fun updateIdle(dt: Float) {
        globalTime += dt
        stateTimer += dt
        frameTimer += dt

        // Update frame animation
        updateFrames()

        // State logic
        when (state) {
            State.IDLE_FRONT -> {
                // Gentle floating
                petView.animOffsetY = sin(globalTime * 1.5f) * 5f
                petView.animOffsetX = sin(globalTime * 0.8f) * 3f

                // Decide movement every 3-5 seconds
                if (stateTimer > 3f + Random.nextFloat() * 2f) {
                    val roll = Random.nextFloat()
                    when {
                        roll < 0.40f -> startHorizontalFlight()
                        roll < 0.70f -> startClimbToEdge()
                        else -> {
                            // 30% stay idle or vertical float
                            if (Random.nextBoolean()) {
                                startVerticalFlight()
                            } else {
                                stateTimer = 0f
                            }
                        }
                    }
                }
            }
            State.FLY_VERTICAL -> {
                // Move up/down with bounds
                petView.animOffsetY += 40f * petView.velocityY * dt

                // Keep within screen bounds (relative to center)
                val maxY = (petView.screenHeight / 3).toFloat()
                if (petView.animOffsetY < -maxY || petView.animOffsetY > maxY) {
                    petView.velocityY *= -1f
                }
                petView.animOffsetY = petView.animOffsetY.coerceIn(-maxY, maxY)

                // After 2-4 seconds, go back to idle
                if (stateTimer > 2f + Random.nextFloat() * 2f) {
                    changeState(State.IDLE_FRONT)
                }
            }
            State.FLY_HORIZONTAL -> {
                // Move toward target with bounds
                val dx = targetX - petView.animOffsetX
                val dy = targetY - petView.animOffsetY
                val dist = abs(dx) + abs(dy)

                if (dist > 30f) {
                    val speedX = 100f * dt
                    val speedY = 50f * dt
                    petView.animOffsetX += sign(dx) * speedX.coerceAtMost(abs(dx))
                    petView.animOffsetY += sign(dy) * speedY.coerceAtMost(abs(dy))

                    // Clamp to screen bounds
                    val maxX = (petView.screenWidth / 2 - 30).toFloat()
                    val maxY = (petView.screenHeight / 3).toFloat()
                    petView.animOffsetX = petView.animOffsetX.coerceIn(-maxX, maxX)
                    petView.animOffsetY = petView.animOffsetY.coerceIn(-maxY, maxY)
                } else {
                    // Arrived - go back to idle
                    changeState(State.IDLE_FRONT)
                }

                // Timeout
                if (stateTimer > 3f) {
                    changeState(State.IDLE_FRONT)
                }
            }
            State.CLIMBING -> {
                // Climb up/down along edge
                petView.animOffsetY += 35f * climbDir * dt

                // Bounds
                val minY = 50f
                val maxY = (petView.screenHeight - 200).toFloat()
                if (petView.animOffsetY < minY || petView.animOffsetY > maxY) {
                    climbDir *= -1f // Reverse
                }

                // Random chance to look back
                if (stateTimer > 2f && Random.nextFloat() < 0.01f) {
                    petView.showBubble("👀")
                }

                // After 4-8 seconds, stop climbing
                if (stateTimer > 4f + Random.nextFloat() * 4f) {
                    petView.animRotation = 0f
                    petView.animScaleX = 1f
                    petView.animScaleY = 1f
                    changeState(State.IDLE_FRONT)
                }
            }
            State.FIRE_ATTACK -> {
                // Fire sequence handled in updateInteracting
            }
        }

        // Breathing animation
        val breathe = sin(globalTime * 2f) * 0.015f
        petView.animScaleY = 1f + breathe
    }

    override fun updateDrag(dt: Float) {
        petView.currentFrame = 4 // Surprised face
        petView.animRotation = 0f
        petView.animScaleX = 1f
        petView.animScaleY = 1f
    }

    override fun updateFalling(dt: Float) {
        // Flap wings while falling
        if (frameTimer >= 0.15f) {
            frameTimer = 0f
            currentAnimFrame = (currentAnimFrame + 1) % 2
            petView.currentFrame = currentAnimFrame // 0-1 frente
        }
        petView.animScaleX = 1f
        if (petView.velocityY > 2f) petView.velocityY = -3f
    }

    override fun updateJumping(dt: Float) {
        if (frameTimer >= 0.08f) {
            frameTimer = 0f
            currentAnimFrame = (currentAnimFrame + 1) % 2
            petView.currentFrame = 2 + currentAnimFrame // 2-3 perfil
        }
        if (petView.velocityY > 2f) petView.velocityY = -4f
    }

    override fun updateAutonomous(dt: Float) {}

    override fun onInteract() {
        // Start fire attack ONLY when user taps
        if (state != State.FIRE_ATTACK) {
            changeState(State.FIRE_ATTACK)
        }
    }

    override fun updateInteracting(dt: Float) {
        // Fire sequence: 4 (inflate cheeks) → 5 (flame) → 6 (smoke)
        when {
            stateTimer < 0.25f -> {
                // Inflate cheeks
                petView.currentFrame = 4
                petView.animRotation = 15f
                petView.animScaleX = 1.1f
                petView.animScaleY = 1.1f
                if (stateTimer < 0.05f) {
                    petView.showBubble("😤")
                    petView.playHaptic(30)
                }
            }
            stateTimer < 0.75f -> {
                // Flame burst
                petView.currentFrame = 5
                petView.animRotation = 15f
                petView.animScaleX = 1.15f
                petView.animScaleY = 1.05f
                // Burst haptic during flame
                if ((stateTimer * 10f).toInt() % 2 == 0) {
                    petView.playHaptic(40)
                }
                if (stateTimer < 0.30f) {
                    petView.showBubble("🔥")
                }
            }
            stateTimer < 1.00f -> {
                // Smoke
                petView.currentFrame = 6
                petView.animRotation = 15f
                petView.animScaleX = 1.05f
                petView.animScaleY = 1f
            }
            else -> {
                // Return to idle
                petView.animRotation = 0f
                petView.animScaleX = 1f
                petView.animScaleY = 1f
                changeState(State.IDLE_FRONT)
            }
        }
    }

    override fun onDraw(canvas: Canvas, cx: Float, cy: Float) {}

    override fun reset() {
        state = State.IDLE_FRONT
        stateTimer = 0f
        globalTime = 0f
        frameTimer = 0f
        currentAnimFrame = 0
        petView.animAlpha = 1f
        petView.animScaleX = 1f
        petView.animScaleY = 1f
        petView.animRotation = 0f
        petView.animOffsetX = 0f
        petView.animOffsetY = 0f
    }

    // ══════════════════════════════════════════════════════════
    // ▌ FRAME ANIMATION
    // ══════════════════════════════════════════════════════════

    private fun updateFrames() {
        val flapDuration = when (state) {
            State.IDLE_FRONT -> 0.15f       // Slow idle flap
            State.FLY_VERTICAL -> 0.15f     // Slow vertical flap
            State.FLY_HORIZONTAL -> 0.08f   // Fast horizontal flap
            State.CLIMBING -> 0.40f         // Slow arm movement
            State.FIRE_ATTACK -> 0.25f      // Fire duration
        }

        if (frameTimer >= flapDuration) {
            frameTimer = 0f
            currentAnimFrame = (currentAnimFrame + 1) % 2

            when (state) {
                State.IDLE_FRONT, State.FLY_VERTICAL -> {
                    petView.currentFrame = currentAnimFrame // 0-1 frente
                    petView.animScaleX = 1f // Face user
                    petView.animRotation = 0f
                }
                State.FLY_HORIZONTAL -> {
                    petView.currentFrame = 2 + currentAnimFrame // 2-3 perfil
                    // Flip based on direction
                    petView.animScaleX = if (targetX > petView.animOffsetX) 1f else -1f
                    // Slight tilt
                    petView.animRotation = if (targetX > petView.animOffsetX) 5f else -5f
                }
                State.CLIMBING -> {
                    // Alternate 7-8, occasionally 9
                    if (Random.nextFloat() < 0.05f) {
                        petView.currentFrame = 9 // Look back
                    } else {
                        petView.currentFrame = 7 + currentAnimFrame // 7-8 arms
                    }
                    // Rotate for wall
                    petView.animRotation = if (climbEdge < petView.screenWidth / 2) 90f else -90f
                    petView.animScaleX = 0.9f
                    petView.animScaleY = 0.9f
                }
                State.FIRE_ATTACK -> {
                    // Handled in updateInteracting
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════
    // ▌ STATE CHANGES
    // ══════════════════════════════════════════════════════════

    private fun changeState(newState: State) {
        if (state == newState) return
        state = newState
        stateTimer = 0f
        frameTimer = 0f
        currentAnimFrame = 0

        when (newState) {
            State.IDLE_FRONT -> {
                petView.velocityX = 0f
                petView.velocityY = 0f
                petView.animRotation = 0f
                petView.animScaleX = 1f
                petView.animScaleY = 1f
                petView.currentFrame = 0
            }
            State.FLY_VERTICAL -> {
                petView.velocityY = if (Random.nextBoolean()) 1f else -1f
                petView.animRotation = 0f
                petView.animScaleX = 1f
            }
            State.FLY_HORIZONTAL -> {
                // Target already set
            }
            State.CLIMBING -> {
                petView.velocityX = 0f
                petView.velocityY = 0f
                climbDir = if (Random.nextBoolean()) 1f else -1f
            }
            State.FIRE_ATTACK -> {
                petView.velocityX = 0f
                petView.velocityY = 0f
                petView.currentFrame = 4
            }
        }
    }

    // ══════════════════════════════════════════════════════════
    // ▌ MOVEMENT HELPERS
    // ══════════════════════════════════════════════════════════

    private fun startVerticalFlight() {
        targetX = petView.animOffsetX // Stay at same X
        targetY = petView.animOffsetY + (Random.nextFloat() - 0.5f) * 400f
        changeState(State.FLY_VERTICAL)
    }

    private fun startHorizontalFlight() {
        val sw = petView.screenWidth.toFloat()
        val sh = petView.screenHeight.toFloat()
        targetX = Random.nextFloat() * (sw - 100f) + 50f
        targetY = Random.nextFloat() * (sh - 300f) + 100f
        changeState(State.FLY_HORIZONTAL)
    }

    private fun startClimbToEdge() {
        val sw = petView.screenWidth.toFloat()
        // Fly to nearest edge
        val currentX = petView.animOffsetX
        if (currentX < sw / 2) {
            targetX = 0f // Left edge
        } else {
            targetX = sw - 60f // Right edge
        }
        targetY = petView.animOffsetY
        climbEdge = targetX
        changeState(State.FLY_HORIZONTAL)
    }

    // ══════════════════════════════════════════════════════════
    // ▌ EXTERNAL INTERACTION
    // ══════════════════════════════════════════════════════════

    fun onUserTouch(x: Float, y: Float) {
        // Double tap: fly toward that position
        targetX = x
        targetY = y
        changeState(State.FLY_HORIZONTAL)
    }

    fun onDragStarted() {
        petView.currentFrame = 4
    }

    fun onDragEnded() {
        changeState(State.IDLE_FRONT)
        petView.velocityY = 2f
    }
}
