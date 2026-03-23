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
 * ImpBehavior — Diablillo Alado (Winged Imp) v3 - Utility AI
 *
 * Sistema de Necesidades:
 * - chaosLevel (0-100): Sube con el tiempo. Al 100, hace travesura pesada.
 * - boredom (0-100): Sube si lo ignoras. Al 100, jump scare.
 * - heat (0-100): Sube rápido si lo agarras. Al 100, se quema y escapa.
 *
 * Frames (11):
 * 0: Idle, 1: Wings_Up, 2: Wings_Down, 3: Glide
 * 4: Turn_1/4, 5: Turn_Back, 6: Turn_3/4, 7: Turn_Reset
 * 8: Fire_Prep, 9: Fire_Release, 10: Fire_Cooldown
 */
class ImpBehavior(
    private val petView: PetViewBridge
) : PetBehavior {

    // ══════════════════════════════════════════════════════════
    // ▌ NECESIDADES DEL DIABLILLO
    // ══════════════════════════════════════════════════════════

    private var chaosLevel = 0f      // Sube con el tiempo
    private var boredom = 0f         // Sube si lo ignoras
    private var heat = 0f            // Sube si lo agarras

    // ══════════════════════════════════════════════════════════
    // ▌ ESTADOS
    // ══════════════════════════════════════════════════════════

    enum class State {
        LURKING,        // Acechando (frames 0-1)
        RUNNING,        // Sprint (frames 2-3)
        PLOTTING,       // Planeando travesura
        JUMP_SCARE,     // Salto para asustar (frame 4)
        FIRE_MISCHIEF,  // Fuego (frames 8-9-10)
        TELEPORTING,    // Desaparece y reaparece
        BURNED_OUT,     // Quemado (filtro rojo)
        DRAGGED         // Siendo arrastrado
    }

    private var state = State.LURKING
    private var stateTimer = 0f
    private var timer = 0f
    private var globalTime = 0f

    // Teleport
    private var isTeleportingOut = false

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

        // Temblor que aumenta con el calor
        val shakeIntensity = (heat / 100f) * 10f
        petView.animOffsetX = sin(stateTimer * 30f) * shakeIntensity
        petView.currentFrame = 4

        // Micro-vibraciones de advertencia
        if (heat > 70f && (stateTimer * 10f).toInt() % 3 == 0) {
            petView.playHaptic(10)
        }
    }

    override fun updateFalling(dt: Float) {
        // Ciclo de aleteo
        val flapPos = timer % 0.4f
        petView.currentFrame = when {
            flapPos < 0.13f -> 0
            flapPos < 0.26f -> 1
            else -> 2
        }
        petView.animOffsetY = sin(timer * 2f) * 4f
        if (petView.velocityY > 2f) petView.velocityY = -3f
    }

    override fun updateJumping(dt: Float) {
        val flapPos = timer % 0.35f
        petView.currentFrame = when {
            flapPos < 0.12f -> 0
            flapPos < 0.23f -> 1
            else -> 2
        }
        if (petView.velocityY > 2f) petView.velocityY = -4f
    }

    override fun updateAutonomous(dt: Float) {}

    override fun onInteract() {
        if (state == State.BURNED_OUT) return // No hace caso si está quemado

        boredom = 0f
        chaosLevel += 15f // Tocarlo alimenta su caos

        if (chaosLevel > 80f) {
            changeState(State.FIRE_MISCHIEF)
        } else {
            // Susto rápido
            petView.currentFrame = 4
            petView.showBubble("😈")
            petView.playHaptic(20)
        }
    }

    override fun updateInteracting(dt: Float) {
        // Animación de fuego
        if (state == State.FIRE_MISCHIEF) {
            petView.currentFrame = when {
                stateTimer < 0.25f -> 8
                stateTimer < 0.50f -> 9
                stateTimer < 0.80f -> 10
                else -> {
                    changeState(State.LURKING)
                    0
                }
            }
        }
    }

    override fun onDraw(canvas: Canvas, cx: Float, cy: Float) {}

    override fun reset() {
        state = State.LURKING
        timer = 0f
        stateTimer = 0f
        globalTime = 0f
        chaosLevel = 0f
        boredom = 0f
        heat = 0f
        isTeleportingOut = false
        petView.animAlpha = 1f
        petView.animScaleX = 1f
        petView.animScaleY = 1f
        petView.animRotation = 0f
    }

    // ══════════════════════════════════════════════════════════
    // ▌ SISTEMA DE NECESIDADES
    // ══════════════════════════════════════════════════════════

    private fun updateNeeds(dt: Float) {
        if (state == State.DRAGGED) {
            heat = min(100f, heat + dt * 35f) // Se calienta rápido (~3s)
            boredom = 0f
        } else {
            heat = max(0f, heat - dt * 10f)   // Se enfría al soltar
            boredom = min(100f, boredom + dt * 1.5f) // Se aburre
        }

        if (state == State.LURKING || state == State.PLOTTING) {
            val multiplier = if (state == State.PLOTTING) 3f else 1f
            chaosLevel = min(100f, chaosLevel + dt * 2f * multiplier)
        }
    }

    // ══════════════════════════════════════════════════════════
    // ▌ ÁRBOL DE DECISIONES
    // ══════════════════════════════════════════════════════════

    private fun evaluateState() {
        // Prioridad 1: ¡Quemarse!
        if (heat >= 100f && state != State.BURNED_OUT) {
            changeState(State.BURNED_OUT)
            return
        }

        // Si está arrastrado o quemado, no tomar otras decisiones
        if (state == State.DRAGGED || state == State.BURNED_OUT) {
            if (state == State.BURNED_OUT && stateTimer > 2f) {
                heat = 0f
                changeState(State.LURKING)
            }
            return
        }

        when (state) {
            State.LURKING -> {
                if (boredom > 90f) {
                    // Ignorado mucho tiempo → Susto
                    changeState(State.JUMP_SCARE)
                } else if (chaosLevel > 95f) {
                    // Caos máximo → Travesura pesada
                    if (Random.nextBoolean()) changeState(State.TELEPORTING)
                    else changeState(State.FIRE_MISCHIEF)
                } else if (stateTimer > 3f) {
                    // Decisiones normales
                    val roll = Random.nextFloat()
                    when {
                        roll < 0.30f -> changeState(State.RUNNING)
                        roll < 0.50f -> changeState(State.PLOTTING)
                        else -> stateTimer = 0f // Seguir lurking
                    }
                }
            }
            State.PLOTTING -> {
                if (stateTimer > 2f) changeState(State.LURKING)
            }
            State.RUNNING -> {
                if (stateTimer > 1.5f || abs(petView.velocityX) < 0.1f) {
                    changeState(State.LURKING)
                }
            }
            State.JUMP_SCARE -> {
                if (stateTimer > 1.5f) changeState(State.LURKING)
            }
            State.FIRE_MISCHIEF -> {
                if (stateTimer > 1f) changeState(State.LURKING)
            }
            State.TELEPORTING -> {
                // Manejado en applyAnimation
            }
            else -> {}
        }
    }

    // ══════════════════════════════════════════════════════════
    // ▌ ANIMACIONES
    // ══════════════════════════════════════════════════════════

    private fun applyAnimation(dt: Float) {
        // Flotación base siempre activa
        val baseFloatY = sin(globalTime * 1.5f) * 4f
        val baseFloatX = sin(globalTime * 0.8f) * 2f

        // Respiración siempre activa
        val breathe = sin(globalTime * 2f) * 0.015f
        petView.animScaleY = 1f + breathe
        petView.animScaleX = 1f - breathe * 0.5f

        when (state) {
            State.LURKING -> {
                // Alternar frames 0-1 lentamente
                petView.currentFrame = if ((stateTimer * 0.5f).toInt() % 2 == 0) 0 else 1
                petView.animOffsetY = baseFloatY
                petView.animOffsetX = baseFloatX
                petView.animAlpha = 1f
            }

            State.PLOTTING -> {
                petView.currentFrame = 0
                petView.animOffsetX = sin(stateTimer * 30f) * 2f // Vibra por emoción
                petView.animOffsetY = baseFloatY
            }

            State.RUNNING -> {
                // Sprint Naruto (frames 2-3 muy rápido)
                petView.currentFrame = if ((stateTimer * 15f).toInt() % 2 == 0) 2 else 3
                petView.animOffsetY = abs(sin(stateTimer * 20f)) * 5f
            }

            State.JUMP_SCARE -> {
                petView.currentFrame = 4 // Cara de susto
                petView.animScaleX = 1.2f
                petView.animScaleY = 1.2f // Se hace más grande
            }

            State.FIRE_MISCHIEF -> {
                petView.currentFrame = when {
                    stateTimer < 0.25f -> 8  // Carga
                    stateTimer < 0.50f -> 9  // Dispara
                    stateTimer < 0.80f -> 10 // Humo
                    else -> 0
                }
                petView.animRotation = sin(stateTimer * 10f) * 15f // Gira en el aire
            }

            State.TELEPORTING -> {
                if (isTeleportingOut) {
                    // Desaparecer
                    petView.animAlpha = max(0f, 1f - stateTimer * 5f)
                    if (petView.animAlpha <= 0f) {
                        isTeleportingOut = false
                        stateTimer = 0f
                        petView.teleportToRandomEdge()
                    }
                } else {
                    // Reaparecer
                    petView.animAlpha = min(1f, stateTimer * 5f)
                    petView.currentFrame = 4 // Cara de "¡Ajá!"
                    if (petView.animAlpha >= 1f && stateTimer > 0.3f) {
                        petView.showBubble("😈")
                        petView.playHaptic(30)
                        changeState(State.LURKING)
                    }
                }
            }

            State.BURNED_OUT -> {
                petView.currentFrame = 5 // Modo fuego
                petView.animOffsetX = sin(stateTimer * 40f) * 4f // Tiembla
            }

            State.DRAGGED -> {
                petView.currentFrame = 4
                // Ya manejado en updateDrag
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
            State.LURKING -> {
                petView.velocityX = 0f
                petView.animAlpha = 1f
            }
            State.PLOTTING -> {
                petView.velocityX = 0f
                petView.showBubble("😈")
            }
            State.RUNNING -> {
                petView.velocityX = if (Random.nextBoolean()) 15f else -15f
            }
            State.JUMP_SCARE -> {
                boredom = 0f
                petView.currentFrame = 4
                petView.velocityY = -18f
                petView.showBubble("👹")
                petView.playHaptic(50)
            }
            State.FIRE_MISCHIEF -> {
                chaosLevel = 0f
                petView.currentFrame = 8
                petView.velocityY = -22f
                petView.velocityX = (Random.nextFloat() - 0.5f) * 15f
                petView.showBubble("🔥")
                petView.playHaptic(60)
            }
            State.TELEPORTING -> {
                chaosLevel = 0f
                isTeleportingOut = true
                petView.velocityX = 0f
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

    /** Doble-tap en posición vacía → el diablillo vuela ahí */
    fun onUserTouch(x: Float, y: Float) {
        if (state == State.BURNED_OUT) return

        boredom = 0f
        chaosLevel += 10f

        // Volar hacia donde tocó
        changeState(State.RUNNING)
        petView.velocityX = if (x > petView.screenWidth / 2) 10f else -10f
    }

    fun onDragStarted() {
        changeState(State.DRAGGED)
    }

    fun onDragEnded() {
        if (state == State.DRAGGED) {
            changeState(State.LURKING)
            petView.velocityY = 2f
        }
    }
}
