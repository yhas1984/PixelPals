package com.pixelpals.app.behavior

import android.graphics.Canvas
import android.graphics.LightingColorFilter
import com.pixelpals.app.PetState
import kotlin.math.sin
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * ImpBehavior v5 — Diablillo con Motor Simplificado
 *
 * Conceptos del código de referencia aplicados:
 * - Estados simples: IDLE_FRONT, FLY_PROFILE, CLIMBING, FIRE_ATTACK
 * - Física directa con targetX/targetY
 * - Flip horizontal según dirección de vuelo
 * - Secuencias de frames claras
 * - Velocidades de frame por estado
 *
 * Frames (10):
 * 0-1: Vuelo IDLE frente (alas arriba/abajo)
 * 2-3: Vuelo MOV perfil (alas arriba/abajo)
 * 4-6: Fuego (carga, llamarada, humo)
 * 7-9: Escalada (brazo der, brazo izq, mirar atrás)
 */
class ImpBehavior(
    private val petView: PetViewBridge
) : PetBehavior {

    // ══════════════════════════════════════════════════════════
    // ▌ ESTADOS
    // ══════════════════════════════════════════════════════════

    enum class State {
        IDLE_FRONT,     // Vuelo quieto mirando al usuario
        FLY_PROFILE,    // Vuelo horizontal
        CLIMBING,       // Escalando pared
        FIRE_ATTACK,    // Lanzallamas
        DRAGGED         // Siendo arrastrado
    }

    private var state = State.IDLE_FRONT
    private var stateTimer = 0f
    private var timer = 0f
    private var globalTime = 0f

    // Física simplificada
    private var posX = 0f
    private var posY = 0f
    private var targetX = 0f
    private var targetY = 0f

    // Secuencias de frames
    private var currentSequence = listOf(0, 1)
    private var frameIndex = 0
    private var frameTimer = 0f
    private var frameDuration = 0.15f // 150ms por defecto

    // Escalada
    private var isOnLeftWall = false
    private var isOnRightWall = false
    private var climbTargetY = 0f

    // Decisiones
    private var brainTick = 0

    // ══════════════════════════════════════════════════════════
    // ▌ PET BEHAVIOR
    // ══════════════════════════════════════════════════════════

    override fun updateIdle(dt: Float) {
        timer += dt
        stateTimer += dt
        globalTime += dt
        frameTimer += dt
        brainTick++

        // Actualizar frame según duración
        if (frameTimer >= frameDuration) {
            frameIndex = (frameIndex + 1) % currentSequence.size
            frameTimer = 0f
            petView.currentFrame = currentSequence[frameIndex]
        }

        // Cerebro: decisiones cada ~3 segundos
        if (brainTick % 90 == 0) {
            updateBrain()
        }

        // Física y animación
        updatePhysics(dt)
        applyVisuals()
    }

    override fun updateDrag(dt: Float) {
        if (state != State.DRAGGED) {
            changeState(State.DRAGGED)
        }
        petView.currentFrame = 4 // Frame de carga mientras es arrastrado
    }

    override fun updateFalling(dt: Float) {
        // Aleteo frente
        frameTimer += dt
        if (frameTimer >= 0.15f) {
            frameIndex = (frameIndex + 1) % 2
            frameTimer = 0f
            petView.currentFrame = frameIndex // Frames 0-1 frente
        }
        petView.animScaleX = 1f
        if (petView.velocityY > 2f) petView.velocityY = -3f
    }

    override fun updateJumping(dt: Float) {
        // Aleteo rápido perfil
        frameTimer += dt
        if (frameTimer >= 0.08f) {
            frameIndex = (frameIndex + 1) % 2
            frameTimer = 0f
            petView.currentFrame = 2 + frameIndex // Frames 2-3 perfil
        }
        if (petView.velocityY > 2f) petView.velocityY = -4f
    }

    override fun updateAutonomous(dt: Float) {}

    override fun onInteract() {
        changeState(State.FIRE_ATTACK)
        petView.showBubble("🔥")
        petView.playHaptic(60)
    }

    override fun updateInteracting(dt: Float) {
        if (state == State.FIRE_ATTACK) {
            // Secuencia: 4→5→5→6 (carga, llamarada x2, humo)
            petView.currentFrame = when {
                stateTimer < 0.20f -> 4
                stateTimer < 0.50f -> 5
                stateTimer < 0.70f -> 5
                stateTimer < 1.00f -> 6
                else -> {
                    changeState(State.IDLE_FRONT)
                    0
                }
            }
            // Burst haptic durante llamarada
            if (stateTimer in 0.20f..0.70f && (stateTimer * 10f).toInt() % 2 == 0) {
                petView.playHaptic(40)
            }
        }
    }

    override fun onDraw(canvas: Canvas, cx: Float, cy: Float) {}

    override fun reset() {
        state = State.IDLE_FRONT
        timer = 0f
        stateTimer = 0f
        globalTime = 0f
        frameTimer = 0f
        frameIndex = 0
        brainTick = 0
        posX = 0f
        posY = 0f
        petView.animAlpha = 1f
        petView.animScaleX = 1f
        petView.animScaleY = 1f
        petView.animRotation = 0f
    }

    // ══════════════════════════════════════════════════════════
    // ▌ CEREBRO (Decisiones Caóticas)
    // ══════════════════════════════════════════════════════════

    private fun updateBrain() {
        if (state == State.DRAGGED || state == State.FIRE_ATTACK) return

        val rand = Random.nextFloat()
        val sw = petView.screenWidth.toFloat()
        val sh = petView.screenHeight.toFloat()

        when {
            // 15%: Ataque de fuego
            rand < 0.15f -> {
                changeState(State.FIRE_ATTACK)
            }
            // 30%: Volar a otro punto
            rand < 0.45f -> {
                targetX = Random.nextFloat() * (sw - 100f) + 50f
                targetY = Random.nextFloat() * (sh - 300f) + 100f
                changeState(State.FLY_PROFILE)
            }
            // 25%: Trepar si está cerca de pared
            rand < 0.70f && (isOnLeftWall || isOnRightWall) -> {
                climbTargetY = Random.nextFloat() * (sh - 300f) + 100f
                changeState(State.CLIMBING)
            }
            // 30%: Flotar quieto
            else -> {
                changeState(State.IDLE_FRONT)
            }
        }
    }

    // ══════════════════════════════════════════════════════════
    // ▌ FÍSICA
    // ══════════════════════════════════════════════════════════

    private fun updatePhysics(dt: Float) {
        if (state == State.DRAGGED || state == State.FIRE_ATTACK) return

        val sw = petView.screenWidth.toFloat()

        when (state) {
            State.FLY_PROFILE -> {
                // Moverse hacia el objetivo
                val distX = targetX - posX
                val distY = targetY - posY

                val velX = if (abs(distX) > 10f) sign(distX) * 6f else 0f
                val velY = if (abs(distY) > 10f) sign(distY) * 3f else 0f

                posX += velX * dt * 60f
                posY += velY * dt * 60f

                // Flip según dirección
                petView.animScaleX = if (distX > 0) 1f else -1f

                // Llegó al destino
                if (abs(distX) <= 10f && abs(distY) <= 10f) {
                    changeState(State.IDLE_FRONT)
                }
            }
            State.CLIMBING -> {
                // Moverse verticalmente por la pared
                val distY = climbTargetY - posY
                val velY = if (abs(distY) > 10f) sign(distY) * 4f else 0f
                posY += velY * dt * 60f

                // Rotación según lado
                petView.animRotation = if (isOnLeftWall) 90f else -90f
                petView.animScaleX = 0.9f
                petView.animScaleY = 0.9f

                // Llegó al destino
                if (abs(distY) <= 10f) {
                    petView.animRotation = 0f
                    petView.animScaleX = 1f
                    petView.animScaleY = 1f
                    changeState(State.IDLE_FRONT)
                }
            }
            State.IDLE_FRONT -> {
                // Flotación suave
                petView.animOffsetY = sin(globalTime * 1.5f) * 5f
                petView.animOffsetX = sin(globalTime * 0.8f) * 3f
                petView.animRotation = 0f
                petView.animScaleX = 1f
                petView.animScaleY = 1f
            }
            else -> {}
        }

        // Actualizar posición visual
        if (state == State.FLY_PROFILE || state == State.CLIMBING) {
            petView.animOffsetX = posX
            petView.animOffsetY = posY
        }

        // Detección de paredes
        isOnLeftWall = posX <= 10f
        isOnRightWall = posX >= sw - 60f
    }

    // ══════════════════════════════════════════════════════════
    // ▌ ANIMACIÓN
    // ══════════════════════════════════════════════════════════

    private fun applyVisuals() {
        // Respiración sutil siempre activa
        val breathe = sin(globalTime * 2f) * 0.015f
        petView.animScaleY = 1f + breathe
        if (state == State.IDLE_FRONT || state == State.FLY_PROFILE) {
            petView.animScaleX = if (petView.animScaleX < 0) -(1f - breathe * 0.5f) else (1f - breathe * 0.5f)
        }
    }

    // ══════════════════════════════════════════════════════════
    // ▌ TRANSICIONES
    // ══════════════════════════════════════════════════════════

    private fun changeState(newState: State) {
        if (state == newState) return
        state = newState
        stateTimer = 0f
        timer = 0f
        frameIndex = 0
        frameTimer = 0f

        currentSequence = when (newState) {
            State.IDLE_FRONT -> {
                frameDuration = 0.15f // 150ms - aleteo lento
                petView.animRotation = 0f
                petView.animScaleX = 1f
                petView.animScaleY = 1f
                petView.velocityX = 0f
                petView.velocityY = 0f
                listOf(0, 1) // Frente
            }
            State.FLY_PROFILE -> {
                frameDuration = 0.10f // 100ms - aleteo rápido
                listOf(2, 3) // Perfil
            }
            State.CLIMBING -> {
                frameDuration = 0.40f // 400ms - brazos lentos
                petView.velocityX = 0f
                petView.velocityY = 0f
                // Posicionar en borde más cercano
                val sw = petView.screenWidth.toFloat()
                if (petView.animOffsetX < sw / 2) {
                    posX = 0f
                    isOnLeftWall = true
                } else {
                    posX = sw - 60f
                    isOnRightWall = true
                }
                if (Random.nextBoolean()) listOf(7, 8, 7, 9) // Con mirar atrás
                else listOf(7, 8)
            }
            State.FIRE_ATTACK -> {
                frameDuration = 0.25f // 250ms - dramático
                petView.velocityX = 0f
                petView.velocityY = 0f
                petView.animRotation = 15f // Diagonal
                listOf(4, 5, 5, 6) // Carga, Llamarada x2, Humo
            }
            State.DRAGGED -> {
                frameDuration = 0.15f
                petView.animColorFilter = null
                listOf(4)
            }
        }

        petView.currentFrame = currentSequence[0]
    }

    // ══════════════════════════════════════════════════════════
    // ▌ INTERACCIÓN EXTERNA
    // ══════════════════════════════════════════════════════════

    fun onUserTouch(x: Float, y: Float) {
        targetX = x
        targetY = y
        changeState(State.FLY_PROFILE)
    }

    fun onDragStarted() {
        changeState(State.DRAGGED)
    }

    fun onDragEnded() {
        if (state == State.DRAGGED) {
            changeState(State.IDLE_FRONT)
            petView.velocityY = 2f
        }
    }
}
