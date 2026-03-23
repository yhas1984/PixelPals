package com.pixelpals.app.behavior

import android.graphics.Canvas
import com.pixelpals.app.PetState
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.abs
import kotlin.random.Random

/**
 * ImpBehavior — Diablillo Alado (Winged Imp)
 *
 * Comportamiento de exploración:
 * 1. Aletea por toda la pantalla con movimientos sutiles
 * 2. Se detiene sobre "puntos de interés" (simulando apps)
 * 3. Vuela hacia donde el usuario toca
 *
 * Frames (11 total):
 * 0: Idle_Base, 1: Wings_Up, 2: Wings_Down, 3: Glide
 * 4-7: Turn sequence, 8-10: Fire attack
 */
class ImpBehavior(
    private val petView: PetViewBridge
) : PetBehavior {

    enum class ImpState {
        IDLE,               // Flotando suave
        FLYING_AROUND,      // Explorando con aleteo
        OBSERVING,          // Detenido mirando abajo (frame 5)
        FLYING_TO_TARGET,   // Volando hacia posición
        LANDING,            // Posándose momentáneamente
        TURNING,            // Giro
        FIRE_ATTACK,        // Fuego al tocar
        BURNED_OUT          // Quemado
    }

    private var state = ImpState.IDLE
    private var timer = 0f
    private var stateTimer = 0f
    private var burnTimer = 0f
    private var isBurned = false

    // Exploración
    private var targetX = 0f
    private var targetY = 0f
    private var velocityX = 0f
    private var velocityY = 0f
    private val flySpeed = 2f

    // Puntos de interés (simulación de apps)
    data class PointOfInterest(val x: Float, val y: Float, val name: String)
    private val pointsOfInterest = mutableListOf<PointOfInterest>()
    private var currentPOI: PointOfInterest? = null

    init {
        generatePointsOfInterest()
    }

    /** Generar puntos de interés simulando posiciones de apps */
    private fun generatePointsOfInterest() {
        pointsOfInterest.clear()
        // Status bar
        pointsOfInterest.add(PointOfInterest(540f, 60f, "Status"))
        // Dock inferior
        pointsOfInterest.add(PointOfInterest(180f, 1780f, "Dock1"))
        pointsOfInterest.add(PointOfInterest(360f, 1780f, "Dock2"))
        pointsOfInterest.add(PointOfInterest(540f, 1780f, "Dock3"))
        pointsOfInterest.add(PointOfInterest(720f, 1780f, "Dock4"))
        // Grid apps (filas simuladas)
        for (row in 0 until 4) {
            for (col in 0 until 4) {
                val x = 130f + col * 180f
                val y = 400f + row * 280f
                pointsOfInterest.add(PointOfInterest(x, y, "App_${row}_${col}"))
            }
        }
    }

    // ══════════════════════════════════════════════════════════
    // ▌ PET BEHAVIOR
    // ══════════════════════════════════════════════════════════

    override fun updateIdle(dt: Float) {
        timer += dt
        stateTimer += dt

        when (state) {
            ImpState.IDLE -> {
                // Flotación suave
                petView.currentFrame = 0
                petView.animOffsetY = sin(timer * 1.2f) * 5f
                petView.animOffsetX = sin(timer * 0.6f) * 3f

                // Después de 2-3s, empezar a explorar
                if (stateTimer > 2f + Random.nextFloat()) {
                    startExploring()
                }
            }

            ImpState.FLYING_AROUND -> {
                // Aleteo constante mientras explora
                val flapCycle = timer % 0.5f
                petView.currentFrame = when {
                    flapCycle < 0.17f -> 0
                    flapCycle < 0.33f -> 1
                    else -> 2
                }

                // Bob sincronizado
                petView.animOffsetY = when {
                    flapCycle < 0.17f -> 0f
                    flapCycle < 0.33f -> -3f
                    else -> 3f
                }

                // Movimiento sinusoidal por toda la pantalla
                val moveX = sin(timer * 0.15f) * 300f
                val moveY = cos(timer * 0.12f) * 200f
                petView.animOffsetX = moveX

                // Cada 3-5s, decidir si detenerse
                if (stateTimer > 3f + Random.nextFloat() * 2f) {
                    val roll = Random.nextFloat()
                    when {
                        roll < 0.50f -> {
                            // 50%: Detenerse a observar
                            startObserving()
                        }
                        roll < 0.80f -> {
                            // 30%: Volar a un punto de interés
                            flyToRandomPOI()
                        }
                        else -> {
                            // 20%: Continuar explorando
                            stateTimer = 0f
                        }
                    }
                }
            }

            ImpState.OBSERVING -> {
                // Frame 5 (espalda) - mirando "hacia abajo" como observando apps
                petView.currentFrame = 5
                petView.animOffsetY = sin(timer * 0.8f) * 2f
                petView.animOffsetX = 0f

                // Observar por 2-4s
                stateTimer += dt
                if (stateTimer > 2f + Random.nextFloat() * 2f) {
                    petView.showBubble("👁️")
                    state = ImpState.FLYING_AROUND
                    stateTimer = 0f
                }
            }

            ImpState.FLYING_TO_TARGET -> {
                // Aleteo rápido mientras vuela al objetivo
                val flapCycle = timer % 0.3f
                petView.currentFrame = when {
                    flapCycle < 0.10f -> 0
                    flapCycle < 0.20f -> 1
                    else -> 2
                }

                // Mover hacia el objetivo con suavizado
                val dx = targetX - petView.animOffsetX
                val dy = targetY - petView.animOffsetY
                val dist = kotlin.math.sqrt(dx * dx + dy * dy)

                if (dist > 20f) {
                    // Interpolación suave
                    petView.animOffsetX += dx * 0.03f
                    petView.animOffsetY += dy * 0.03f
                    // Bob mientras vuela
                    petView.animOffsetY += sin(timer * 8f) * 2f
                } else {
                    // Llegó al objetivo
                    state = ImpState.LANDING
                    stateTimer = 0f
                    petView.showBubble("🐾")
                    petView.playHaptic(30)
                }
            }

            ImpState.LANDING -> {
                // Posándose sobre la "app"
                petView.currentFrame = 0
                petView.animOffsetY = sin(timer * 1.5f) * 3f

                // Posarse por 1-2s
                stateTimer += dt
                if (stateTimer > 1.5f + Random.nextFloat()) {
                    // Volver a explorar
                    state = ImpState.FLYING_AROUND
                    stateTimer = 0f
                }
            }

            ImpState.FIRE_ATTACK -> {
                petView.currentFrame = when {
                    timer < 0.25f -> 8
                    timer < 0.50f -> 9
                    timer < 0.80f -> 10
                    else -> {
                        state = ImpState.IDLE
                        timer = 0f
                        0
                    }
                }
            }

            ImpState.TURNING -> {
                petView.currentFrame = when {
                    timer < 0.12f -> 4
                    timer < 0.24f -> 5
                    timer < 0.36f -> 6
                    timer < 0.48f -> 7
                    else -> {
                        state = ImpState.FLYING_AROUND
                        timer = 0f
                        0
                    }
                }
            }

            ImpState.BURNED_OUT -> {
                petView.currentFrame = 0
                petView.animOffsetY = sin(timer * 20f) * 3f
                burnTimer += dt
                if (burnTimer > 1.5f) {
                    isBurned = false
                    state = ImpState.IDLE
                    burnTimer = 0f
                    timer = 0f
                }
            }
        }
    }

    override fun updateDrag(dt: Float) {
        petView.currentFrame = 4
        petView.animRotation = 0f
    }

    override fun updateFalling(dt: Float) {
        petView.currentFrame = 0
        petView.animOffsetY = sin(timer * 2f) * 3f
        if (petView.velocityY > 2f) petView.velocityY = -3f
    }

    override fun updateJumping(dt: Float) {
        val flapCycle = timer % 0.4f
        petView.currentFrame = when {
            flapCycle < 0.13f -> 0
            flapCycle < 0.26f -> 1
            else -> 2
        }
        if (petView.velocityY > 2f) petView.velocityY = -4f
    }

    override fun updateAutonomous(dt: Float) {}

    override fun onInteract() {
        state = ImpState.FIRE_ATTACK
        timer = 0f
        petView.currentFrame = 8
        petView.velocityX = 0f
        petView.velocityY = 0f
        petView.showBubble("🔥")
        petView.playHaptic(80)
    }

    override fun updateInteracting(dt: Float) {}

    override fun onDraw(canvas: Canvas, cx: Float, cy: Float) {}

    override fun reset() {
        state = ImpState.IDLE
        timer = 0f
        stateTimer = 0f
        isBurned = false
        burnTimer = 0f
        petView.animAlpha = 1f
        petView.animScaleX = 1f
        petView.animScaleY = 1f
        petView.animRotation = 0f
    }

    // ══════════════════════════════════════════════════════════
    // ▌ EXPLORATION HELPERS
    // ══════════════════════════════════════════════════════════

    private fun startExploring() {
        state = ImpState.FLYING_AROUND
        stateTimer = 0f
        timer = 0f
    }

    private fun startObserving() {
        state = ImpState.OBSERVING
        stateTimer = 0f
        timer = 0f
    }

    private fun flyToRandomPOI() {
        if (pointsOfInterest.isEmpty()) return
        currentPOI = pointsOfInterest.random()
        targetX = currentPOI!!.x
        targetY = currentPOI!!.y
        state = ImpState.FLYING_TO_TARGET
        stateTimer = 0f
        timer = 0f
    }

    /** Llamar cuando el usuario toca la pantalla para que el diablillo vuele ahí */
    fun onUserTouch(x: Float, y: Float) {
        targetX = x
        targetY = y
        state = ImpState.FLYING_TO_TARGET
        stateTimer = 0f
        timer = 0f
    }
}
