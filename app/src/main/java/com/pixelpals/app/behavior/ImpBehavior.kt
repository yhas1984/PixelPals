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
        IDLE_FRONT,       // Quieto de frente flotando (0)
        FLY_VERTICAL,     // Sube/baja de frente (1,2)
        FLY_HORIZONTAL,   // Se desplaza de perfil con tilt y seno (3)
        CLIMBING,         // Trepando por borde (7,8)
        SPINNING,         // Maniobra de giro tras fling (4,5,6)
        FIRE_ATTACK       // Solo al tocar: llamarada épica (9)
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
    private var baseFlightY = 0f

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
                // Gentle floating using animOffset for visual only
                petView.animOffsetY = sin(globalTime * 1.8f) * 8f
                petView.animOffsetX = sin(globalTime * 0.9f) * 4f

                // Decide movement every 3-5 seconds
                if (stateTimer > 3f + Random.nextFloat() * 2f) {
                    val roll = Random.nextFloat()
                    when {
                        roll < 0.45f -> startHorizontalFlight()
                        roll < 0.75f -> startClimbToEdge()
                        else -> {
                            if (Random.nextBoolean()) startVerticalFlight() else stateTimer = 0f
                        }
                    }
                }
            }
            State.FLY_VERTICAL -> {
                // Move up/down using windowY (real position)
                val moveY = (45f * petView.velocityY * dt).toInt()
                val newY = petView.windowY + moveY

                // Bounds
                if (newY < 80 || newY > petView.screenHeight - 250) {
                    petView.velocityY *= -1f
                } else {
                    petView.windowY = newY
                }

                if (stateTimer > 2.5f) changeState(State.IDLE_FRONT)
            }
            State.FLY_HORIZONTAL -> {
                // Sinusoidal movement toward targetX
                val currentX = petView.windowX.toFloat()
                val dx = targetX - currentX
                
                if (abs(dx) > 15f) {
                    val speedX = 140f * dt
                    val moveX = (sign(dx) * speedX.coerceAtMost(abs(dx))).toInt()
                    petView.windowX += moveX
                    
                    // Sine flight: combine targetY with globalTime wave
                    val sineWave = sin(globalTime * 6f) * 35f
                    petView.windowY = (baseFlightY + sineWave).toInt()

                    // Flip based on direction
                    petView.animScaleX = if (dx > 0) 1f else -1f
                    // Agile tilt
                    petView.animRotation = if (dx > 0) 12f else -12f
                } else {
                    changeState(State.IDLE_FRONT)
                }

                if (stateTimer > 5f) changeState(State.IDLE_FRONT)
            }
            State.SPINNING -> {
                // Quick spin 360 maneuver
                if (stateTimer > 0.6f) changeState(State.IDLE_FRONT)
            }
            State.CLIMBING -> {
                // Climb up/down along edge
                val moveY = (40f * climbDir * dt).toInt()
                val newY = petView.windowY + moveY

                if (newY < 80 || newY > petView.screenHeight - 250) {
                    climbDir *= -1f
                } else {
                    petView.windowY = newY
                }

                if (stateTimer > 5f) changeState(State.IDLE_FRONT)
            }
            State.FIRE_ATTACK -> {
                // Handled in updateInteracting
            }
        }

        // Breathing animation (subtle)
        val breathe = sin(globalTime * 2f) * 0.02f
        petView.animScaleY = 1f + breathe
    }

    override fun updateDrag(dt: Float) {
        petView.currentFrame = 4 // Turn profile (looks like struggle)
        petView.animRotation = sin(globalTime * 20f) * 10f
        petView.animScaleX = 1f
    }

    override fun updateFalling(dt: Float) {
        // Fast panic flapping
        if (frameTimer >= 0.10f) {
            frameTimer = 0f
            currentAnimFrame = if (currentAnimFrame == 0) 1 else 0 // 0, 1 frente
            petView.currentFrame = currentAnimFrame 
        }
        petView.animScaleY = 1.1f
    }

    override fun updateJumping(dt: Float) {
        petView.currentFrame = 1 // Wings spread for jump
        petView.animScaleY = 0.9f
    }

    override fun updateAutonomous(dt: Float) {}

    override fun onInteract() {
        if (state != State.FIRE_ATTACK) {
            changeState(State.FIRE_ATTACK)
            petView.playHaptic(80)
        }
    }

    override fun onTouchDown(x: Float, y: Float): Boolean {
        // Intercept touch to launch the fire attack natively instead of letting PetView drag the imp
        onInteract()
        return true
    }

    override fun updateInteracting(dt: Float) {
        // Epic Fire Sequence (Frame 9 - epic_blast)
        when {
            stateTimer < 0.25f -> {
                // Frame 4 (Turn Profile) acts as charging
                petView.currentFrame = 4
                petView.animRotation = 15f
                if (stateTimer < 0.05f) petView.showBubble("🔥")
            }
            stateTimer < 0.80f -> {
                // BLAST EPIC (Frame 9)
                petView.currentFrame = 9
                petView.animScaleX = 1.2f
                petView.animScaleY = 1.1f
                if ((stateTimer * 15f).toInt() % 2 == 0) petView.playHaptic(40)
            }
            stateTimer < 1.10f -> {
                // Frame 5 (Turn 3/4) recoil
                petView.currentFrame = 5
                petView.animRotation = -8f
            }
            else -> changeState(State.IDLE_FRONT)
        }
    }

    override fun onDraw(canvas: Canvas, cx: Float, cy: Float) {}

    override fun onFling(velocityX: Float, velocityY: Float) {
        // Trigger Spin Maneuver!
        if (state != State.SPINNING && state != State.FIRE_ATTACK) {
            changeState(State.SPINNING)
            petView.playHaptic(100)
            petView.showBubble("💫")
            
            // Impulse in fling direction
            targetX = petView.windowX + velocityX * 0.05f
            targetY = petView.windowY + velocityY * 0.05f
            // Bound targets to screen
            targetX = targetX.coerceIn(50f, petView.screenWidth - 100f)
            targetY = targetY.coerceIn(100f, petView.screenHeight - 300f)
        }
    }

    override fun reset() {
        state = State.IDLE_FRONT
        stateTimer = 0f
        globalTime = 0f
        frameTimer = 0f
        petView.animRotation = 0f
        petView.animScaleX = 1f
        petView.animScaleY = 1f
    }

    // ══════════════════════════════════════════════════════════
    // ▌ FRAME ANIMATION
    // ══════════════════════════════════════════════════════════

    private fun updateFrames() {
        val duration = when (state) {
            State.IDLE_FRONT -> 0.20f
            State.FLY_VERTICAL -> 0.12f
            State.FLY_HORIZONTAL -> 0.10f
            State.CLIMBING -> 0.25f
            State.SPINNING -> 0.06f
            State.FIRE_ATTACK -> 0.15f
        }

        if (frameTimer >= duration) {
            frameTimer = 0f
            
            when (state) {
                State.IDLE_FRONT, State.FLY_VERTICAL -> {
                    currentAnimFrame = if (currentAnimFrame == 0) 1 else 0
                    petView.currentFrame = currentAnimFrame
                }
                State.FLY_HORIZONTAL -> {
                    currentAnimFrame = if (currentAnimFrame == 2) 3 else 2
                    petView.currentFrame = currentAnimFrame
                }
                State.SPINNING -> {
                    // Turn sequence: 4 (1/4) -> 5 (3/4) -> 6 (back) -> 5 -> 4
                    currentAnimFrame = (currentAnimFrame + 1) % 4
                    petView.currentFrame = when(currentAnimFrame) {
                        0 -> 4; 1 -> 5; 2 -> 6; else -> 5
                    }
                }
                State.CLIMBING -> {
                    currentAnimFrame = if (currentAnimFrame == 7) 8 else 7 // Climb Up/Down
                    petView.currentFrame = currentAnimFrame
                    petView.animRotation = if (climbEdge < petView.screenWidth / 2) 90f else -90f
                }
                State.FIRE_ATTACK -> {}
            }
        }
    }

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
            }
            State.FLY_HORIZONTAL -> {
                baseFlightY = targetY
            }
            State.SPINNING -> {
                // Fast impulse
                petView.velocityX = (targetX - petView.windowX) * 0.15f
                petView.velocityY = (targetY - petView.windowY) * 0.15f
            }
            else -> {}
        }
    }

    private fun startVerticalFlight() {
        petView.velocityY = if (Random.nextBoolean()) 1.2f else -1.2f
        changeState(State.FLY_VERTICAL)
    }

    private fun startHorizontalFlight() {
        targetX = Random.nextFloat() * (petView.screenWidth - 150f) + 75f
        targetY = Random.nextFloat() * (petView.screenHeight - 350f) + 150f
        changeState(State.FLY_HORIZONTAL)
    }

    private fun startClimbToEdge() {
        val currentX = petView.windowX.toFloat()
        targetX = if (currentX < petView.screenWidth / 2) 35f else (petView.screenWidth - 95).toFloat()
        targetY = petView.windowY.toFloat()
        climbEdge = targetX
        changeState(State.FLY_HORIZONTAL)
    }

    fun onUserTouch(x: Float, y: Float) {
        targetX = x
        targetY = y
        changeState(State.FLY_HORIZONTAL)
    }

    fun onDragStarted() { petView.currentFrame = 4 }
    fun onDragEnded() { 
        changeState(State.IDLE_FRONT)
        petView.velocityY = 2.5f 
    }
}
