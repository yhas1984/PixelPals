package com.pixelpals.app.behavior

import android.graphics.Canvas
import com.pixelpals.app.PetState
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.abs
import kotlin.random.Random

/**
 * ImpBehavior — Diablillo Alado (Winged Imp) v2
 *
 * Comportamiento mejorado:
 * - Movimiento suave y continuo
 * - Usa TODOS los frames con carisma
 * - Reacciones variadas constantes
 * - Flotación y respiración constante
 * - Timeout de seguridad anti-congelamiento
 *
 * Frames (11 total):
 * 0: Idle_Base, 1: Wings_Up, 2: Wings_Down, 3: Glide
 * 4-7: Turn sequence, 8-10: Fire attack
 */
class ImpBehavior(
    private val petView: PetViewBridge
) : PetBehavior {

    enum class ImpState {
        IDLE,
        EXPLORING,        // Ciclo completo usando todos los frames
        OBSERVING,
        FLYING_TO_TARGET,
        LANDING,
        FIRE_ATTACK,
        BURNED_OUT
    }

    private var state = ImpState.IDLE
    private var timer = 0f
    private var stateTimer = 0f
    private var burnTimer = 0f
    private var isBurned = false
    private var globalTime = 0f

    // Exploración
    private var targetX = 0f
    private var targetY = 0f
    private var currentX = 0f
    private var currentY = 0f

    // Puntos de interés
    data class PointOfInterest(val x: Float, val y: Float, val name: String)
    private val pointsOfInterest = mutableListOf<PointOfInterest>()

    // Carisma
    private val reactions = listOf("😈", "🔥", "✨", "💫", "👀", "🦇", "⭐", "🌙", "💨", "🫧")
    private var reactionTimer = 0f
    private var nextReaction = 2f + Random.nextFloat() * 3f

    // Timeout de seguridad
    private val maxStateTime = 4f

    init {
        generatePointsOfInterest()
    }

    private fun generatePointsOfInterest() {
        pointsOfInterest.clear()
        pointsOfInterest.add(PointOfInterest(540f, 60f, "Status"))
        pointsOfInterest.add(PointOfInterest(180f, 1780f, "Dock1"))
        pointsOfInterest.add(PointOfInterest(360f, 1780f, "Dock2"))
        pointsOfInterest.add(PointOfInterest(540f, 1780f, "Dock3"))
        pointsOfInterest.add(PointOfInterest(720f, 1780f, "Dock4"))
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
        globalTime += dt
        reactionTimer += dt

        // Flotación base SIEMPRE activa
        val baseFloatY = sin(globalTime * 1.5f) * 4f
        val baseFloatX = sin(globalTime * 0.8f) * 2f

        // Respiración sutil SIEMPRE activa
        val breathe = sin(globalTime * 2f) * 0.015f
        petView.animScaleY = 1f + breathe
        petView.animScaleX = 1f - breathe * 0.5f

        // Reacciones variadas
        if (reactionTimer > nextReaction) {
            val reaction = reactions.random()
            petView.showBubble(reaction)
            petView.playHaptic(20)
            reactionTimer = 0f
            nextReaction = 1.5f + Random.nextFloat() * 3f

            // Squish aleatorio para dar vida
            petView.animScaleX = 1.15f
            petView.animScaleY = 0.9f
        }

        // Timeout de seguridad
        if (stateTimer > maxStateTime && state != ImpState.EXPLORING) {
            forceExploring()
        }

        when (state) {
            ImpState.IDLE -> {
                // Flotación suave con frame frontal
                petView.currentFrame = 0
                petView.animOffsetY = baseFloatY
                petView.animOffsetX = baseFloatX

                if (stateTimer > 1.5f + Random.nextFloat() * 0.5f) {
                    startExploring()
                }
            }

            ImpState.EXPLORING -> {
                // Ciclo completo de exploración usando TODOS los frames
                val cycleDuration = 6f  // 6 segundos por ciclo completo
                val cyclePos = timer % cycleDuration

                when {
                    // Fase 1: Aleteo (frames 0-2)
                    cyclePos < 1.5f -> {
                        val flapPos = cyclePos % 0.4f
                        petView.currentFrame = when {
                            flapPos < 0.13f -> 0
                            flapPos < 0.26f -> 1
                            else -> 2
                        }
                        petView.animOffsetY = baseFloatY + when {
                            flapPos < 0.13f -> 0f
                            flapPos < 0.26f -> -3f
                            else -> 3f
                        }
                    }
                    // Fase 2: Glide (frame 3)
                    cyclePos < 2.5f -> {
                        petView.currentFrame = 3
                        petView.animOffsetY = baseFloatY
                    }
                    // Fase 3: Giro (frames 4-7)
                    cyclePos < 3.5f -> {
                        val turnPos = (cyclePos - 2.5f) % 1f
                        petView.currentFrame = when {
                            turnPos < 0.25f -> 4
                            turnPos < 0.50f -> 5
                            turnPos < 0.75f -> 6
                            else -> 7
                        }
                        petView.animOffsetY = baseFloatY
                    }
                    // Fase 4: Aleteo rápido (frames 0-2)
                    cyclePos < 4.5f -> {
                        val flapPos = cyclePos % 0.3f
                        petView.currentFrame = when {
                            flapPos < 0.10f -> 0
                            flapPos < 0.20f -> 1
                            else -> 2
                        }
                        petView.animOffsetY = baseFloatY + when {
                            flapPos < 0.10f -> 0f
                            flapPos < 0.20f -> -4f
                            else -> 4f
                        }
                    }
                    // Fase 5: Glide suave (frame 3)
                    cyclePos < 5.5f -> {
                        petView.currentFrame = 3
                        petView.animOffsetY = baseFloatY
                    }
                    // Fase 6: Idle frontal (frame 0)
                    else -> {
                        petView.currentFrame = 0
                        petView.animOffsetY = baseFloatY
                    }
                }

                // Movimiento sinusoidal SUAVE por la pantalla
                val moveX = sin(globalTime * 0.3f) * 80f  // 80px máximo, no 300px
                val moveY = cos(globalTime * 0.25f) * 60f  // 60px máximo
                petView.animOffsetX = baseFloatX + moveX

                // Cada 4-6s, decidir si observar o volar a POI
                if (stateTimer > 4f + Random.nextFloat() * 2f) {
                    val roll = Random.nextFloat()
                    when {
                        roll < 0.40f -> startObserving()
                        roll < 0.70f -> flyToRandomPOI()
                        else -> stateTimer = 0f  // Continuar explorando
                    }
                }
            }

            ImpState.OBSERVING -> {
                // Mirando "hacia abajo" con frame 5 (espalda)
                petView.currentFrame = 5
                petView.animOffsetY = baseFloatY
                petView.animOffsetX = baseFloatX * 0.5f

                // Máximo 2 segundos observando
                if (stateTimer > 1.5f + Random.nextFloat() * 0.5f) {
                    petView.showBubble(listOf("👁️", "👀", "🤔").random())
                    forceExploring()
                }
            }

            ImpState.FLYING_TO_TARGET -> {
                // Aleteo mientras vuela al objetivo
                val flapPos = timer % 0.35f
                petView.currentFrame = when {
                    flapPos < 0.12f -> 0
                    flapPos < 0.23f -> 1
                    else -> 2
                }

                // Mover hacia objetivo con interpolación suave
                val dx = targetX - currentX
                val dy = targetY - currentY
                val dist = kotlin.math.sqrt(dx * dx + dy * dy)

                if (dist > 15f) {
                    currentX += dx * 0.04f
                    currentY += dy * 0.04f
                    petView.animOffsetX = currentX + sin(timer * 6f) * 3f
                    petView.animOffsetY = currentY + sin(timer * 8f) * 2f + baseFloatY
                } else {
                    state = ImpState.LANDING
                    stateTimer = 0f
                    petView.showBubble(listOf("🐾", "✨", "💫").random())
                    petView.playHaptic(25)
                }

                // Timeout para volar
                if (stateTimer > 5f) {
                    forceExploring()
                }
            }

            ImpState.LANDING -> {
                // Posándose momentáneamente
                petView.currentFrame = 0
                petView.animOffsetY = baseFloatY
                petView.animOffsetX = baseFloatX

                if (stateTimer > 1f + Random.nextFloat() * 0.5f) {
                    petView.showBubble(listOf("😈", "🔥", "✨").random())
                    forceExploring()
                }
            }

            ImpState.FIRE_ATTACK -> {
                petView.currentFrame = when {
                    timer < 0.25f -> 8
                    timer < 0.50f -> 9
                    timer < 0.80f -> 10
                    else -> {
                        forceExploring()
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
                    forceExploring()
                    burnTimer = 0f
                }
            }
        }
    }

    override fun updateDrag(dt: Float) {
        petView.currentFrame = 4
        petView.animRotation = 0f
        petView.animScaleY = 0.95f + sin(timer * 5f) * 0.03f
    }

    override fun updateFalling(dt: Float) {
        // Ciclo de aleteo mientras cae
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
        globalTime = 0f
        reactionTimer = 0f
        isBurned = false
        burnTimer = 0f
        currentX = 0f
        currentY = 0f
        petView.animAlpha = 1f
        petView.animScaleX = 1f
        petView.animScaleY = 1f
        petView.animRotation = 0f
    }

    // ══════════════════════════════════════════════════════════
    // ▌ HELPERS
    // ══════════════════════════════════════════════════════════

    private fun startExploring() {
        state = ImpState.EXPLORING
        stateTimer = 0f
        timer = 0f
    }

    private fun forceExploring() {
        state = ImpState.EXPLORING
        stateTimer = 0f
        timer = 0f
    }

    private fun startObserving() {
        state = ImpState.OBSERVING
        stateTimer = 0f
    }

    private fun flyToRandomPOI() {
        if (pointsOfInterest.isEmpty()) return
        val poi = pointsOfInterest.random()
        targetX = poi.x
        targetY = poi.y
        currentX = petView.animOffsetX
        currentY = petView.animOffsetY
        state = ImpState.FLYING_TO_TARGET
        stateTimer = 0f
        timer = 0f
    }

    fun onUserTouch(x: Float, y: Float) {
        targetX = x
        targetY = y
        currentX = petView.animOffsetX
        currentY = petView.animOffsetY
        state = ImpState.FLYING_TO_TARGET
        stateTimer = 0f
        timer = 0f
    }
}
