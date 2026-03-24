package com.pixelpals.app.behavior

import android.graphics.Canvas
import com.pixelpals.app.PetState
import kotlin.math.sin
import kotlin.math.abs
import kotlin.math.sign
import kotlin.random.Random

/**
 * ImpBehavior v6 — Diablillo funcional con movimiento real
 *
 * Frames (10):
 * 0-1: Frente (alas arriba/abajo) - IDLE
 * 2-3: Perfil (alas arriba/abajo) - VOLANDO
 * 4: Carga diagonal - FUEGO
 * 5: Llamarada diagonal - FUEGO
 * 6: Humo diagonal - FUEGO
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
        IDLE,           // Quieto de frente (frames 0-1)
        FLYING,         // Volando perfil (frames 2-3)
        FIRE_START,     // Cargando fuego (frame 4)
        FIRE_ACTIVE,    // Lanzando fuego (frame 5)
        FIRE_END,       // Humo (frame 6)
        CLIMBING        // Trepando (frames 7-8-9)
    }

    private var state = State.IDLE
    private var stateTimer = 0f
    private var globalTime = 0f

    // Frame animation
    private var frameTimer = 0f
    private var currentAnimFrame = 0

    // Movement
    private var moveTargetX = 0f
    private var moveTargetY = 0f
    private var moveSpeedX = 0f
    private var moveSpeedY = 0f
    private var facingRight = true

    // Climbing
    private var isNearLeftEdge = false
    private var isNearRightEdge = false

    // ══════════════════════════════════════════════════════════
    // ▌ PET BEHAVIOR
    // ══════════════════════════════════════════════════════════

    override fun updateIdle(dt: Float) {
        globalTime += dt
        stateTimer += dt
        frameTimer += dt

        // Update animation frames
        updateFrameAnimation(dt)

        // State machine
        when (state) {
            State.IDLE -> updateIdleState(dt)
            State.FLYING -> updateFlyingState(dt)
            State.FIRE_START, State.FIRE_ACTIVE, State.FIRE_END -> updateFireState(dt)
            State.CLIMBING -> updateClimbingState(dt)
            else -> {}
        }

        // Always apply breathing
        val breathe = sin(globalTime * 2f) * 0.02f
        petView.animScaleY = 1f + breathe
    }

    override fun updateDrag(dt: Float) {
        petView.currentFrame = 4 // Surprised face while dragged
        petView.animRotation = 0f
        petView.animScaleX = 1f
        petView.animScaleY = 1f
    }

    override fun updateFalling(dt: Float) {
        // Flap wings while falling
        val flapSpeed = 0.12f
        if (frameTimer >= flapSpeed) {
            frameTimer = 0f
            currentAnimFrame = (currentAnimFrame + 1) % 2
            petView.currentFrame = currentAnimFrame // 0-1 frente
        }
        petView.animScaleX = 1f // Face user
        if (petView.velocityY > 2f) petView.velocityY = -3f
    }

    override fun updateJumping(dt: Float) {
        // Fast flap while jumping
        val flapSpeed = 0.08f
        if (frameTimer >= flapSpeed) {
            frameTimer = 0f
            currentAnimFrame = (currentAnimFrame + 1) % 2
            petView.currentFrame = 2 + currentAnimFrame // 2-3 perfil
        }
        if (petView.velocityY > 2f) petView.velocityY = -4f
    }

    override fun updateAutonomous(dt: Float) {}

    override fun onInteract() {
        // Start fire attack
        if (state == State.FIRE_START || state == State.FIRE_ACTIVE || state == State.FIRE_END) return
        changeState(State.FIRE_START)
    }

    override fun updateInteracting(dt: Float) {
        updateFireState(dt)
    }

    override fun onDraw(canvas: Canvas, cx: Float, cy: Float) {}

    override fun reset() {
        state = State.IDLE
        stateTimer = 0f
        globalTime = 0f
        frameTimer = 0f
        currentAnimFrame = 0
        facingRight = true
        petView.animAlpha = 1f
        petView.animScaleX = 1f
        petView.animScaleY = 1f
        petView.animRotation = 0f
    }

    // ══════════════════════════════════════════════════════════
    // ▌ FRAME ANIMATION
    // ══════════════════════════════════════════════════════════

    private fun updateFrameAnimation(dt: Float) {
        val flapDuration = when (state) {
            State.IDLE -> 0.15f       // 150ms - slow idle flap
            State.FLYING -> 0.08f     // 80ms - fast when moving
            State.CLIMBING -> 0.40f   // 400ms - slow arm movement
            else -> 0.25f             // 250ms - fire
        }

        if (frameTimer >= flapDuration) {
            frameTimer = 0f
            currentAnimFrame = (currentAnimFrame + 1) % 2

            when (state) {
                State.IDLE -> {
                    petView.currentFrame = currentAnimFrame // 0-1 frente
                }
                State.FLYING -> {
                    petView.currentFrame = 2 + currentAnimFrame // 2-3 perfil
                }
                State.CLIMBING -> {
                    // Alternate 7-8, occasionally show 9
                    if (stateTimer > 3f && Random.nextFloat() < 0.1f) {
                        petView.currentFrame = 9 // Look back
                        petView.showBubble("👀")
                        stateTimer = 0f
                    } else {
                        petView.currentFrame = 7 + currentAnimFrame // 7-8 arms
                    }
                }
                State.FIRE_START -> {
                    petView.currentFrame = 4 // Charging
                }
                State.FIRE_ACTIVE -> {
                    petView.currentFrame = 5 // Flame
                }
                State.FIRE_END -> {
                    petView.currentFrame = 6 // Smoke
                }
                else -> {}
            }
        }
    }

    // ══════════════════════════════════════════════════════════
    // ▌ STATE UPDATES
    // ══════════════════════════════════════════════════════════

    private fun updateIdleState(dt: Float) {
        // Gentle floating
        petView.animOffsetY = sin(globalTime * 1.5f) * 5f
        petView.animOffsetX = sin(globalTime * 0.8f) * 3f
        petView.animRotation = 0f
        petView.animScaleX = 1f // Face user

        // Every 3-5 seconds, decide what to do
        if (stateTimer > 3f + Random.nextFloat() * 2f) {
            val roll = Random.nextFloat()
            when {
                roll < 0.50f -> startFlying()
                roll < 0.70f -> startClimbing()
                roll < 0.85f -> changeState(State.FIRE_START)
                else -> stateTimer = 0f // Stay idle
            }
        }
    }

    private fun updateFlyingState(dt: Float) {
        // Move toward target
        val dx = moveTargetX - petView.animOffsetX
        val dy = moveTargetY - petView.animOffsetY
        val dist = abs(dx) + abs(dy)

        if (dist > 20f) {
            val speed = 150f * dt // pixels per second
            petView.animOffsetX += sign(dx) * speed.coerceAtMost(abs(dx))
            petView.animOffsetY += sign(dy) * (speed * 0.5f).coerceAtMost(abs(dy))

            // Face direction of movement
            petView.animScaleX = if (dx > 0) 1f else -1f
            facingRight = dx > 0

            // Slight tilt while flying
            petView.animRotation = if (dx > 0) 5f else -5f

            // Float animation while flying
            petView.animOffsetY += sin(globalTime * 3f) * 2f
        } else {
            // Arrived at target - go back to idle
            changeState(State.IDLE)
        }

        // Timeout - go back to idle after 4 seconds
        if (stateTimer > 4f) {
            changeState(State.IDLE)
        }
    }

    private fun updateFireState(dt: Float) {
        when (state) {
            State.FIRE_START -> {
                petView.currentFrame = 4
                petView.animRotation = 15f // Diagonal
                petView.showBubble("🔥")
                petView.playHaptic(40)
                if (stateTimer > 0.3f) {
                    changeState(State.FIRE_ACTIVE)
                }
            }
            State.FIRE_ACTIVE -> {
                petView.currentFrame = 5
                petView.animRotation = 15f
                // Burst haptic during flame
                if ((stateTimer * 8f).toInt() % 2 == 0) {
                    petView.playHaptic(30)
                }
                if (stateTimer > 0.6f) {
                    changeState(State.FIRE_END)
                }
            }
            State.FIRE_END -> {
                petView.currentFrame = 6
                petView.animRotation = 15f
                if (stateTimer > 0.4f) {
                    changeState(State.IDLE)
                }
            }
            else -> {}
        }
    }

    private fun updateClimbingState(dt: Float) {
        // Move up/down along edge
        val climbSpeed = 40f * dt
        petView.animOffsetY += climbSpeed * moveSpeedY

        // Keep on edge
        if (isNearLeftEdge) {
            petView.animOffsetX = 0f
            petView.animRotation = 90f // Rotated for left wall
        } else if (isNearRightEdge) {
            petView.animOffsetX = (petView.screenWidth - 60).toFloat()
            petView.animRotation = -90f // Rotated for right wall
        }

        // Check bounds
        val minY = 50f
        val maxY = (petView.screenHeight - 200).toFloat()

        if (petView.animOffsetY < minY || petView.animOffsetY > maxY) {
            moveSpeedY *= -1f // Reverse direction
        }

        // Randomly stop climbing and go back to idle
        if (stateTimer > 3f + Random.nextFloat() * 3f) {
            petView.animRotation = 0f
            changeState(State.IDLE)
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
            State.IDLE -> {
                petView.velocityX = 0f
                petView.velocityY = 0f
                petView.animRotation = 0f
                petView.animScaleX = 1f
                petView.animScaleY = 1f
                petView.currentFrame = 0
            }
            State.FIRE_START -> {
                petView.velocityX = 0f
                petView.velocityY = 0f
                petView.currentFrame = 4
            }
            State.FIRE_ACTIVE -> {
                petView.currentFrame = 5
            }
            State.FIRE_END -> {
                petView.currentFrame = 6
            }
            State.CLIMBING -> {
                petView.velocityX = 0f
                petView.velocityY = 0f
                petView.animScaleX = 0.9f
                petView.animScaleY = 0.9f
                petView.currentFrame = 7
                moveSpeedY = if (Random.nextBoolean()) 1f else -1f
            }
            else -> {}
        }
    }

    // ══════════════════════════════════════════════════════════
    // ▌ HELPERS
    // ══════════════════════════════════════════════════════════

    private fun startFlying() {
        // Pick random target position
        val sw = petView.screenWidth.toFloat()
        val sh = petView.screenHeight.toFloat()
        moveTargetX = Random.nextFloat() * (sw - 100f) + 50f
        moveTargetY = Random.nextFloat() * (sh - 300f) + 100f
        changeState(State.FLYING)
    }

    private fun startClimbing() {
        // Decide which edge to climb
        isNearLeftEdge = Random.nextBoolean()
        isNearRightEdge = !isNearLeftEdge
        changeState(State.CLIMBING)
    }

    // ══════════════════════════════════════════════════════════
    // ▌ EXTERNAL INTERACTION
    // ══════════════════════════════════════════════════════════

    fun onUserTouch(x: Float, y: Float) {
        // Fly toward where user touched
        moveTargetX = x
        moveTargetY = y
        changeState(State.FLYING)
    }

    fun onDragStarted() {
        petView.currentFrame = 4
    }

    fun onDragEnded() {
        changeState(State.IDLE)
        petView.velocityY = 2f
    }
}
