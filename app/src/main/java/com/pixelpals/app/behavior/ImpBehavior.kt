package com.pixelpals.app.behavior

import android.graphics.Canvas
import com.pixelpals.app.PetState
import kotlin.math.sin
import kotlin.random.Random

/**
 * ImpBehavior — Diablillo Alado (Winged Imp)
 *
 * Frames (11 total):
 * 0: Idle_Base (alas neutras, frente)
 * 1: Wings_Up (alas arriba)
 * 2: Wings_Down (alas abajo)
 * 3: Glide (alas extendidas lateralmente)
 * 4: Turn_1/4 (perfil derecho)
 * 5: Turn_Back (espalda)
 * 6: Turn_3/4 (perfil izquierdo)
 * 7: Turn_Reset (frente con brillo)
 * 8: Fire_Prep (carga fuego)
 * 9: Fire_Release (llamarada)
 * 10: Fire_Cooldown (humo)
 */
class ImpBehavior(
    private val petView: PetViewBridge
) : PetBehavior {

    // ══════════════════════════════════════════════════════════
    // ▌ STATE
    // ══════════════════════════════════════════════════════════

    enum class ImpState {
        IDLE,           // Frame 0: Mirando al frente
        FLYING,         // Frames 0→1→2→1→0: Ciclo de aleteo
        TURNING,        // Frames 4→5→6→7→0: Giro completo
        GLIDING,        // Frame 3: Desplazamiento rápido
        FIRE_ATTACK,    // Frames 8→9→10: Ataque de fuego
        SURPRISED,      // Frames 4→5→6→7: Giro de susto
        TELEPORTING,    // Invisible
        BURNED_OUT      // Quemado por arrastre
    }

    private var state = ImpState.IDLE
    private var timer = 0f
    private var decisionTimer = 0f
    private var nextDecision = 2f + Random.nextFloat() * 3f
    private var burnTimer = 0f
    private var isBurned = false

    // ══════════════════════════════════════════════════════════
    // ▌ PET BEHAVIOR INTERFACE
    // ══════════════════════════════════════════════════════════

    override fun updateIdle(dt: Float) {
        timer += dt
        decisionTimer += dt

        when (state) {
            ImpState.IDLE -> {
                // Mirando al frente con flotación suave
                petView.currentFrame = 0
                petView.animOffsetY = sin(timer * 1.5f) * 3f
                petView.animOffsetX = sin(timer * 0.8f) * 2f

                // Decisión autónoma
                if (decisionTimer > nextDecision) {
                    makeDecision()
                    decisionTimer = 0f
                    nextDecision = 2f + Random.nextFloat() * 3f
                }
            }
            ImpState.FLYING -> {
                // Ciclo de aleteo: 0→1→2→1→0
                val flapCycle = timer % 0.6f
                petView.currentFrame = when {
                    flapCycle < 0.15f -> 0  // Base
                    flapCycle < 0.30f -> 1  // Wings up
                    flapCycle < 0.45f -> 2  // Wings down
                    else -> 1              // Wings back up
                }

                // Bob sincronizado con alas
                petView.animOffsetY = when {
                    flapCycle < 0.15f -> 0f
                    flapCycle < 0.30f -> -3f  // Baja cuando alas suben
                    flapCycle < 0.45f -> 3f   // Sube cuando alas bajan
                    else -> -3f
                }

                // Terminar vuelo después de 2-4 segundos
                if (timer > 2f + Random.nextFloat() * 2f) {
                    state = ImpState.IDLE
                    timer = 0f
                }
            }
            ImpState.TURNING -> {
                // Secuencia de giro: 4→5→6→7→0
                petView.currentFrame = when {
                    timer < 0.12f -> 4  // Turn 1/4
                    timer < 0.24f -> 5  // Turn back
                    timer < 0.36f -> 6  // Turn 3/4
                    timer < 0.48f -> 7  // Turn reset
                    else -> {
                        state = ImpState.GLIDING
                        timer = 0f
                        0
                    }
                }
            }
            ImpState.GLIDING -> {
                // Desplazamiento con frame 3 (glide)
                petView.currentFrame = 3
                petView.animOffsetY = sin(timer * 2f) * 2f

                // Terminar después de 1-2 segundos
                if (timer > 1f + Random.nextFloat() * 1f) {
                    petView.velocityX = 0f
                    state = ImpState.IDLE
                    timer = 0f
                }
            }
            ImpState.FIRE_ATTACK -> {
                // Secuencia de fuego: 8→9→10
                petView.currentFrame = when {
                    timer < 0.25f -> 8  // Fire prep
                    timer < 0.50f -> 9  // Fire release
                    timer < 0.80f -> 10 // Fire cooldown
                    else -> {
                        state = ImpState.IDLE
                        timer = 0f
                        0
                    }
                }
            }
            ImpState.SURPRISED -> {
                // Giro rápido de susto: 4→5→6→7
                petView.currentFrame = when {
                    timer < 0.10f -> 4
                    timer < 0.20f -> 5
                    timer < 0.30f -> 6
                    timer < 0.40f -> 7
                    else -> {
                        state = ImpState.IDLE
                        timer = 0f
                        0
                    }
                }
            }
            ImpState.TELEPORTING -> {
                // Invisible durante teletransporte
                petView.animAlpha = 0f
            }
            ImpState.BURNED_OUT -> {
                // Quemado - shake y filtro rojo
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
        // Sorprendido mientras es arrastrado
        petView.currentFrame = 4
        petView.animRotation = 0f
    }

    override fun updateFalling(dt: Float) {
        // Vuela mirando al frente mientras cae
        petView.currentFrame = 0
        petView.animOffsetY = sin(timer * 2f) * 3f

        // Anti-gravedad
        if (petView.velocityY > 2f) {
            petView.velocityY = -3f
        }

        // Límite superior
        if (petView.animOffsetY < -50f) {
            petView.animOffsetY = 50f
            petView.velocityY = 2f
        }
    }

    override fun updateJumping(dt: Float) {
        // Ciclo de aleteo mientras salta
        val flapCycle = timer % 0.4f
        petView.currentFrame = when {
            flapCycle < 0.13f -> 0
            flapCycle < 0.26f -> 1
            else -> 2
        }

        // Anti-gravedad suave
        if (petView.velocityY > 2f) {
            petView.velocityY = -4f
        }
    }

    override fun updateAutonomous(dt: Float) {
        // Movimiento horizontal si está gliding
        if (state == ImpState.GLIDING) {
            // El movimiento se maneja en PetView
        }
    }

    override fun onInteract() {
        // Fire Attack al tocar
        state = ImpState.FIRE_ATTACK
        timer = 0f
        petView.currentFrame = 8
        petView.velocityX = 0f
        petView.velocityY = 0f
        petView.showBubble("🔥")
        petView.playHaptic(80)
    }

    override fun updateInteracting(dt: Float) {
        // Handled in updateIdle with FIRE_ATTACK state
    }

    override fun onDraw(canvas: Canvas, cx: Float, cy: Float) {
        // No special drawing needed
    }

    override fun reset() {
        state = ImpState.IDLE
        timer = 0f
        isBurned = false
        burnTimer = 0f
        petView.animAlpha = 1f
        petView.animScaleX = 1f
        petView.animScaleY = 1f
        petView.animRotation = 0f
    }

    // ══════════════════════════════════════════════════════════
    // ▌ PRIVATE HELPERS
    // ══════════════════════════════════════════════════════════

    private fun makeDecision() {
        if (isBurned) return

        val roll = Random.nextFloat()
        when {
            roll < 0.40f -> {
                // 40%: Volar (aleteo)
                state = ImpState.FLYING
                timer = 0f
            }
            roll < 0.60f -> {
                // 20%: Girar y desplazarse
                state = ImpState.TURNING
                timer = 0f
                // Velocidad reducida
                val direction = if (Random.nextBoolean()) 1f else -1f
                petView.velocityX = direction * (1.6f + Random.nextFloat() * 1.2f)
            }
            roll < 0.80f -> {
                // 20%: Quedarse quieto
                state = ImpState.IDLE
                timer = 0f
            }
            roll < 0.90f -> {
                // 10%: Teletransporte
                teleport()
            }
            else -> {
                // 10%: Giro de susto
                state = ImpState.SURPRISED
                timer = 0f
                petView.showBubble("😈")
            }
        }
    }

    private fun teleport() {
        state = ImpState.TELEPORTING
        petView.animAlpha = 0f

        // Reaparecer después de 200ms
        // (La animación se completará en el siguiente frame)
        petView.animAlpha = 1f
        state = ImpState.SURPRISED
        timer = 0f
        petView.showBubble("😈")
        petView.playHaptic(30)
    }
}
