package com.pixelpals.app.behavior

import android.graphics.Canvas
import com.pixelpals.app.PetState
import kotlin.math.sin
import kotlin.math.abs
import kotlin.random.Random

/**
 * CorgiBehavior v2 — Corgi Explorador con 13 frames
 *
 * Frames:
 * 0: quieto, 1: respiracion, 2: parpadea
 * 3: caminata, 4: trote, 5: corre
 * 6: olfatea, 7: olfatea cola arriba
 * 8: cavando, 9: hueso
 * 10: ladra, 11: panza arriba, 12: rodando feliz
 */
class CorgiBehavior(
    private val petView: PetViewBridge
) : PetBehavior {

    // ══════════════════════════════════════════════════════════
    // ▌ ESTADOS
    // ══════════════════════════════════════════════════════════

    private enum class State {
        IDLE,       // Quieto con respiración y parpadeo (0,1,2)
        WALKING,    // Caminando (3,4,5)
        SNIFFING,   // Olfateando (6,7)
        DIGGING,    // Cavando (8,9)
        BARKING,    // Ladrando (10)
        ROLLING     // Mimos panza arriba (11,12)
    }

    private var state = State.IDLE
    private var stateTimer = 0f
    private var globalTime = 0f

    // Frame animation
    private var frameTimer = 0f
    private var frameIndex = 0
    private var currentSequence = listOf(0, 1, 0, 2)
    private var frameDuration = 0.30f // 300ms por defecto

    // Movement
    private var walkDirection = 1f
    private var walkSpeed = 80f

    // ══════════════════════════════════════════════════════════
    // ▌ PET BEHAVIOR
    // ══════════════════════════════════════════════════════════

    override fun updateIdle(dt: Float) {
        globalTime += dt
        stateTimer += dt
        frameTimer += dt

        // Update frame
        if (frameTimer >= frameDuration) {
            frameTimer = 0f
            frameIndex = (frameIndex + 1) % currentSequence.size
            petView.currentFrame = currentSequence[frameIndex]
        }

        // State logic
        when (state) {
            State.IDLE -> updateIdleState(dt)
            State.WALKING -> updateWalkingState(dt)
            State.SNIFFING -> updateSniffingState(dt)
            State.DIGGING -> updateDiggingState(dt)
            State.BARKING -> updateBarkingState(dt)
            State.ROLLING -> updateRollingState(dt)
        }

        // Breathing animation
        val breathe = sin(globalTime * 2f) * 0.015f
        petView.animScaleY = 1f + breathe
    }

    override fun updateDrag(dt: Float) {
        petView.currentFrame = 0 // Quieto mientras es arrastrado
        petView.animRotation = sin(globalTime * 15f) * 5f
    }

    override fun updateFalling(dt: Float) {
        petView.currentFrame = 4 // Trote mientras cae
        petView.animScaleX = 1f
    }

    override fun updateJumping(dt: Float) {
        petView.currentFrame = 5 // Corre mientras salta
    }

    override fun updateAutonomous(dt: Float) {}

    override fun onInteract() {
        // Tap: bark with jump
        changeState(State.BARKING)
    }

    override fun updateInteracting(dt: Float) {
        // BARKING state handles this
    }

    override fun onDraw(canvas: Canvas, cx: Float, cy: Float) {}

    override fun reset() {
        state = State.IDLE
        stateTimer = 0f
        globalTime = 0f
        frameTimer = 0f
        frameIndex = 0
        petView.animAlpha = 1f
        petView.animScaleX = 1f
        petView.animScaleY = 1f
        petView.animRotation = 0f
    }

    // ══════════════════════════════════════════════════════════
    // ▌ ESTADOS
    // ══════════════════════════════════════════════════════════

    private fun updateIdleState(dt: Float) {
        // Gentle sitting pose
        petView.animOffsetY = sin(globalTime * 1.5f) * 2f
        petView.animOffsetX = 0f
        petView.animScaleX = 1f // Face user

        // Every 4-6 seconds, decide what to do
        if (stateTimer > 4f + Random.nextFloat() * 2f) {
            val roll = Random.nextFloat()
            when {
                roll < 0.40f -> changeState(State.WALKING)
                roll < 0.55f -> changeState(State.SNIFFING)
                roll < 0.60f -> changeState(State.DIGGING) // 5% chance
                else -> stateTimer = 0f // Stay idle
            }
        }
    }

    private fun updateWalkingState(dt: Float) {
        // Walk from edge to edge
        petView.animOffsetX += walkSpeed * walkDirection * dt

        // Keep in bounds
        val maxX = (petView.screenWidth / 2 - 30).toFloat()
        if (petView.animOffsetX < -maxX || petView.animOffsetX > maxX) {
            walkDirection *= -1f // Turn around
        }
        petView.animOffsetX = petView.animOffsetX.coerceIn(-maxX, maxX)

        // Flip based on direction
        petView.animScaleX = if (walkDirection > 0) 1f else -1f

        // Walking bob
        petView.animOffsetY = abs(sin(globalTime * 10f)) * 3f

        // After 3-5 seconds, stop
        if (stateTimer > 3f + Random.nextFloat() * 2f) {
            changeState(State.IDLE)
        }
    }

    private fun updateSniffingState(dt: Float) {
        // Sniff sequence: 6 -> 7 -> 6
        petView.animScaleX = 1f // Face user

        // Slight lean forward while sniffing
        petView.animOffsetY = sin(globalTime * 3f) * 2f

        // After 2-3 seconds, stop sniffing
        if (stateTimer > 2f + Random.nextFloat() * 1f) {
            petView.showBubble("🐕")
            changeState(State.IDLE)
        }
    }

    private fun updateDiggingState(dt: Float) {
        // Dig sequence: 8 -> 8 -> 8 -> 9 (bone)
        // Shake while digging
        petView.animOffsetX = sin(globalTime * 20f) * 3f

        // After finding bone, show happiness
        if (stateTimer > 2f) {
            petView.showBubble("🦴")
            petView.playHaptic(50)
            changeState(State.IDLE)
        }
    }

    private fun updateBarkingState(dt: Float) {
        // Bark with jump
        petView.animOffsetY = -abs(sin(globalTime * 8f)) * 5f

        // After 1-1.5 seconds, stop barking
        if (stateTimer > 1f + Random.nextFloat() * 0.5f) {
            changeState(State.IDLE)
        }
    }

    private fun updateRollingState(dt: Float) {
        // Roll on back - alternate 11 and 12
        petView.animScaleX = if ((globalTime * 2f).toInt() % 2 == 0) 1f else -1f

        // After 2-3 seconds, stop rolling
        if (stateTimer > 2f + Random.nextFloat() * 1f) {
            petView.showBubble("❤️")
            changeState(State.IDLE)
        }
    }

    // ══════════════════════════════════════════════════════════
    // ▌ TRANSICIONES
    // ══════════════════════════════════════════════════════════

    private fun changeState(newState: State) {
        if (state == newState) return
        state = newState
        stateTimer = 0f
        frameTimer = 0f
        frameIndex = 0

        currentSequence = when (newState) {
            State.IDLE -> {
                frameDuration = 0.30f // 300ms - slow breathing
                petView.velocityX = 0f
                petView.animRotation = 0f
                petView.animScaleX = 1f
                petView.animScaleY = 1f
                listOf(0, 1, 0, 2) // Quieto, respira, quieto, parpadea
            }
            State.WALKING -> {
                frameDuration = 0.08f // 80ms - fast paws
                walkDirection = if (Random.nextBoolean()) 1f else -1f
                listOf(3, 4, 5) // Caminata, trote, corre
            }
            State.SNIFFING -> {
                frameDuration = 0.25f // 250ms - slow curious
                petView.velocityX = 0f
                listOf(6, 7, 6, 7) // Olfateo curioso
            }
            State.DIGGING -> {
                frameDuration = 0.12f // 120ms - fast digging
                petView.velocityX = 0f
                petView.showBubble("🐾")
                listOf(8, 8, 8, 9) // Cava 3 veces, saca hueso
            }
            State.BARKING -> {
                frameDuration = 0.15f // 150ms
                petView.velocityX = 0f
                petView.velocityY = -10f // Jump
                petView.showBubble("Woof!")
                petView.playHaptic(50)
                listOf(10) // Ladrido
            }
            State.ROLLING -> {
                frameDuration = 0.18f // 180ms - medium mimos
                petView.velocityX = 0f
                petView.showBubble("💕")
                listOf(11, 12, 11, 12) // Panza arriba y rodando
            }
        }

        petView.currentFrame = currentSequence[0]
    }

    // ══════════════════════════════════════════════════════════
    // ▌ INTERACCIÓN EXTERNA
    // ══════════════════════════════════════════════════════════

    /** Swipe over corgi → rolling (belly rub) */
    fun onSwipe() {
        changeState(State.ROLLING)
    }

    fun onDragStarted() {
        petView.currentFrame = 0
    }

    fun onDragEnded() {
        changeState(State.IDLE)
        petView.velocityY = 2f
    }
}
