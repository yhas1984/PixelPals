package com.pixelpals.app.behavior

import android.graphics.Canvas
import android.graphics.LightingColorFilter
import com.pixelpals.app.PetState
import kotlin.math.sin
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * ImpBehavior — Diablillo Travieso v4 - Perspectiva Dinámica
 *
 * Frames (10):
 * 0: Alas arriba frente (Vuelo IDLE)
 * 1: Alas abajo frente (Vuelo IDLE)
 * 2: Alas arriba perfil (Vuelo MOV)
 * 3: Alas abajo perfil (Vuelo MOV)
 * 4: Carga diagonal (Fuego)
 * 5: Llamarada diagonal (Fuego)
 * 6: Humo diagonal (Fuego)
 * 7: Escalar brazo derecho (Escalada)
 * 8: Escalar brazo izquierdo (Escalada)
 * 9: Mirar atrás (Escalada)
 *
 * Sistema de Necesidades:
 * - chaosLevel: Sube con el tiempo → travesuras
 * - boredom: Sube si lo ignoras → llamar atención
 * - heat: Sube si lo agarras → se quema
 */
class ImpBehavior(
    private val petView: PetViewBridge
) : PetBehavior {

    // ══════════════════════════════════════════════════════════
    // ▌ NECESIDADES
    // ══════════════════════════════════════════════════════════

    private var chaosLevel = 0f
    private var boredom = 0f
    private var heat = 0f

    // ══════════════════════════════════════════════════════════
    // ▌ ESTADOS
    // ══════════════════════════════════════════════════════════

    enum class State {
        IDLE_FLY,       // Vuelo quieto mirando al usuario (frames 0-1)
        MOVING_FLY,     // Vuelo horizontal (frames 2-3)
        FIRE_ATTACK,    // Lanzallamas (frames 4-5-6)
        CLIMBING,       // Escalando pared (frames 7-8-9)
        DRAGGED,        // Siendo arrastrado
        BURNED_OUT      // Quemado
    }

    private var state = State.IDLE_FLY
    private var stateTimer = 0f
    private var timer = 0f
    private var globalTime = 0f

    // Escalada
    private var climbDirection = 1f  // 1 = subiendo, -1 = bajando
    private var climbY = 0f
    private var climbPauseTimer = 0f

    // ══════════════════════════════════════════════════════════
    // ▌ PET BEHAVIOR INTERFACE
    // ══════════════════════════════════════════════════════════

    override fun updateIdle(dt: Float) {
        timer += dt
        stateTimer += dt
        globalTime += dt

        updateNeeds(dt)
        evaluateState()
        applyAnimation(dt)
    }

    override fun updateDrag(dt: Float) {
        if (state != State.DRAGGED) {
            changeState(State.DRAGGED)
        }

        val shakeIntensity = (heat / 100f) * 10f
        petView.animOffsetX = sin(stateTimer * 30f) * shakeIntensity

        if (heat > 70f && (stateTimer * 10f).toInt() % 3 == 0) {
            petView.playHaptic(10)
        }
    }

    override fun updateFalling(dt: Float) {
        // Aleteo frente mientras cae
        val flapSpeed = 0.15f // 150ms
        val flapPos = timer % (flapSpeed * 2)
        petView.currentFrame = if (flapPos < flapSpeed) 0 else 1
        petView.animScaleX = 1f // Sin flip
        petView.animOffsetY = sin(timer * 2f) * 4f
        if (petView.velocityY > 2f) petView.velocityY = -3f
    }

    override fun updateJumping(dt: Float) {
        // Aleteo rápido perfil mientras salta
        val flapSpeed = 0.08f // 80ms
        val flapPos = timer % (flapSpeed * 2)
        petView.currentFrame = if (flapPos < flapSpeed) 2 else 3
        if (petView.velocityY > 2f) petView.velocityY = -4f
    }

    override fun updateAutonomous(dt: Float) {}

    override fun onInteract() {
        if (state == State.BURNED_OUT) return

        boredom = 0f
        chaosLevel += 15f

        if (chaosLevel > 80f) {
            changeState(State.FIRE_ATTACK)
        } else {
            petView.showBubble("😈")
            petView.playHaptic(20)
        }
    }

    override fun updateInteracting(dt: Float) {
        if (state == State.FIRE_ATTACK) {
            // Secuencia de fuego: 4→5→6
            petView.currentFrame = when {
                stateTimer < 0.20f -> 4  // Carga
                stateTimer < 0.70f -> 5  // Llamarada
                stateTimer < 1.00f -> 6  // Humo
                else -> {
                    changeState(State.IDLE_FLY)
                    0
                }
            }
            // Burst haptic durante llamarada
            if (stateTimer in 0.20f..0.70f && (stateTimer * 10f).toInt() % 2 == 0) {
                petView.playHaptic(40)
            }
        }
    }

    override fun onDraw(canvas: Canvas, cx: Float, cy: Float) {
        // Rotación para escalada
        if (state == State.CLIMBING) {
            val edge = if (petView.animOffsetX < 0) -1f else 1f
            canvas.save()
            canvas.rotate(90f * edge, cx, cy)
        }
    }

    override fun reset() {
        state = State.IDLE_FLY
        timer = 0f
        stateTimer = 0f
        globalTime = 0f
        chaosLevel = 0f
        boredom = 0f
        heat = 0f
        climbY = 0f
        climbDirection = 1f
        petView.animAlpha = 1f
        petView.animScaleX = 1f
        petView.animScaleY = 1f
        petView.animRotation = 0f
    }

    // ══════════════════════════════════════════════════════════
    // ▌ NECESIDADES
    // ══════════════════════════════════════════════════════════

    private fun updateNeeds(dt: Float) {
        if (state == State.DRAGGED) {
            heat = min(100f, heat + dt * 35f)
            boredom = 0f
        } else {
            heat = max(0f, heat - dt * 10f)
            boredom = min(100f, boredom + dt * 1.5f)
        }

        if (state == State.IDLE_FLY || state == State.CLIMBING) {
            chaosLevel = min(100f, chaosLevel + dt * 2f)
        }
    }

    // ══════════════════════════════════════════════════════════
    // ▌ ÁRBOL DE DECISIONES
    // ══════════════════════════════════════════════════════════

    private fun evaluateState() {
        if (heat >= 100f && state != State.BURNED_OUT) {
            changeState(State.BURNED_OUT)
            return
        }

        if (state == State.DRAGGED || state == State.BURNED_OUT) {
            if (state == State.BURNED_OUT && stateTimer > 2f) {
                heat = 0f
                changeState(State.IDLE_FLY)
            }
            return
        }

        when (state) {
            State.IDLE_FLY -> {
                if (boredom > 90f) {
                    // Mucho tiempo quieto → lanzallamas
                    changeState(State.FIRE_ATTACK)
                } else if (chaosLevel > 95f) {
                    // Mucho caos → escalar pared
                    changeState(State.CLIMBING)
                } else if (stateTimer > 3f) {
                    val roll = Random.nextFloat()
                    when {
                        roll < 0.50f -> changeState(State.MOVING_FLY)
                        roll < 0.70f -> changeState(State.CLIMBING)
                        else -> stateTimer = 0f
                    }
                }
            }
            State.MOVING_FLY -> {
                // Verificar si llegó a un borde
                val atLeftEdge = petView.animOffsetX <= -petView.screenWidth / 2 + 50
                val atRightEdge = petView.animOffsetX >= petView.screenWidth / 2 - 50
                if (atLeftEdge || atRightEdge) {
                    changeState(State.CLIMBING)
                } else if (stateTimer > 2f) {
                    changeState(State.IDLE_FLY)
                }
            }
            State.FIRE_ATTACK -> {
                if (stateTimer > 1.2f) changeState(State.IDLE_FLY)
            }
            State.CLIMBING -> {
                // Escalada manejada en applyAnimation
            }
            else -> {}
        }
    }

    // ══════════════════════════════════════════════════════════
    // ▌ ANIMACIONES
    // ══════════════════════════════════════════════════════════

    private fun applyAnimation(dt: Float) {
        val baseFloatY = sin(globalTime * 1.5f) * 4f
        val breathe = sin(globalTime * 2f) * 0.015f
        petView.animScaleY = 1f + breathe
        petView.animScaleX = 1f - breathe * 0.5f

        when (state) {
            State.IDLE_FLY -> {
                // Vuelo quieto: frames 0-1 frente, 150ms
                val flapSpeed = 0.15f
                val flapPos = timer % (flapSpeed * 2)
                petView.currentFrame = if (flapPos < flapSpeed) 0 else 1
                petView.animOffsetY = baseFloatY
                petView.animOffsetX = sin(globalTime * 0.8f) * 3f
                petView.animScaleX = 1f // Sin flip, mira al usuario
            }

            State.MOVING_FLY -> {
                // Vuelo horizontal: frames 2-3 perfil, 80ms
                val flapSpeed = 0.08f
                val flapPos = timer % (flapSpeed * 2)
                petView.currentFrame = if (flapPos < flapSpeed) 2 else 3
                petView.animOffsetY = baseFloatY

                // Flip según dirección
                val movingRight = petView.velocityX > 0
                petView.animScaleX = if (movingRight) 1f else -1f

                // Mover
                val speed = 3f
                petView.animOffsetX += speed * petView.animScaleX * dt
                petView.animOffsetX = petView.animOffsetX.coerceIn(
                    -petView.screenWidth / 2f + 30f,
                    petView.screenWidth / 2f - 30f
                )
            }

            State.FIRE_ATTACK -> {
                // Lanzallamas: frames 4→5→6
                petView.currentFrame = when {
                    stateTimer < 0.20f -> 4  // Carga
                    stateTimer < 0.70f -> 5  // Llamarada
                    stateTimer < 1.00f -> 6  // Humo
                    else -> 0
                }
                // Rotación diagonal
                petView.animRotation = 15f
            }

            State.CLIMBING -> {
                // Escalada: frames 7-8 alternando, ocasionalmente 9
                climbPauseTimer += dt

                if (climbPauseTimer > 3f && Random.nextFloat() < 0.02f) {
                    // Mirar atrás para vigilar
                    petView.currentFrame = 9
                    if (climbPauseTimer > 4f) {
                        climbPauseTimer = 0f
                        petView.showBubble("👀")
                    }
                } else {
                    // Alternar brazos
                    val armSpeed = 0.4f
                    val armPos = timer % (armSpeed * 2)
                    petView.currentFrame = if (armPos < armSpeed) 7 else 8
                }

                // Subir/bajar por el borde
                climbY += climbDirection * 30f * dt
                if (climbY > petView.screenHeight / 3f) climbDirection = -1f
                if (climbY < -petView.screenHeight / 3f) climbDirection = 1f
                petView.animOffsetY = climbY

                // Rotación según lado
                val atLeft = petView.animOffsetX < 0
                petView.animRotation = if (atLeft) 90f else -90f

                // Escalar más pequeño mientras trepa
                petView.animScaleX = 0.9f
                petView.animScaleY = 0.9f
            }

            State.BURNED_OUT -> {
                petView.currentFrame = 5
                petView.animOffsetX = sin(stateTimer * 40f) * 4f
            }

            State.DRAGGED -> {
                petView.currentFrame = 4
            }
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

        when (newState) {
            State.IDLE_FLY -> {
                petView.velocityX = 0f
                petView.animAlpha = 1f
                petView.animRotation = 0f
                petView.animScaleX = 1f
                petView.animScaleY = 1f
            }
            State.MOVING_FLY -> {
                petView.velocityX = if (Random.nextBoolean()) 8f else -8f
            }
            State.FIRE_ATTACK -> {
                chaosLevel = 0f
                boredom = 0f
                petView.currentFrame = 4
                petView.showBubble("🔥")
                petView.playHaptic(60)
            }
            State.CLIMBING -> {
                chaosLevel = 0f
                climbY = petView.animOffsetY
                climbDirection = if (Random.nextBoolean()) 1f else -1f
                climbPauseTimer = 0f
                // Posicionar en el borde más cercano
                if (petView.animOffsetX < 0) {
                    petView.animOffsetX = -petView.screenWidth / 2f + 40f
                } else {
                    petView.animOffsetX = petView.screenWidth / 2f - 40f
                }
                petView.showBubble("😈")
            }
            State.BURNED_OUT -> {
                petView.velocityY = 5f
                petView.animColorFilter = LightingColorFilter(0xFFFF4444.toInt(), 0x00000000)
                petView.showBubble("🔥😤")
                petView.playHaptic(80)
            }
            State.DRAGGED -> {
                petView.currentFrame = 4
                petView.animColorFilter = null
            }
        }
    }

    // ══════════════════════════════════════════════════════════
    // ▌ INTERACCIÓN EXTERNA
    // ══════════════════════════════════════════════════════════

    fun onUserTouch(x: Float, y: Float) {
        if (state == State.BURNED_OUT) return
        boredom = 0f
        changeState(State.MOVING_FLY)
        petView.velocityX = if (x > petView.screenWidth / 2) 8f else -8f
    }

    fun onDragStarted() {
        changeState(State.DRAGGED)
    }

    fun onDragEnded() {
        if (state == State.DRAGGED) {
            changeState(State.IDLE_FLY)
            petView.velocityY = 2f
        }
    }
}
