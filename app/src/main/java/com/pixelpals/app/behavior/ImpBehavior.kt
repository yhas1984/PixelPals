package com.pixelpals.app.behavior

import com.pixelpals.app.PetState
import com.pixelpals.app.R
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * ImpBehavior — Diablillo Travieso.
 * IA de vuelo, escalada de paredes y ataque de fuego.
 */
class ImpBehavior(
    bridge: PetViewBridge
) : BaseBehavior(bridge) {

    override val resourceIds = listOf(
        R.drawable.diablillo_0, R.drawable.diablillo_1, R.drawable.diablillo_2,
        R.drawable.diablillo_3, R.drawable.diablillo_4, R.drawable.diablillo_5,
        R.drawable.diablillo_6, R.drawable.diablillo_7, R.drawable.diablillo_8,
        R.drawable.diablillo_9
    )

    private enum class ImpState { FLYING, CLIMBING, ATTACKING }
    private var impState = ImpState.FLYING
    private var climbDirection = 1f // 1 = arriba, -1 = abajo
    private var climbTimer = 0f

    override fun getBaseSpeed(): Float = if (impState == ImpState.CLIMBING) 80f else 220f 

    override fun updateIdle(dt: Float) {
        if (isLoading) return
        
        when (impState) {
            ImpState.FLYING -> updateFlying(dt)
            ImpState.CLIMBING -> updateClimbing(dt)
            else -> {}
        }
    }

    private fun updateFlying(dt: Float) {
        updateDecision(dt)
        applyMovement(dt)
        time += dt

        // Orientación de vuelo
        if (velX > 5) bridge.animScaleX = 1f
        else if (velX < -5) bridge.animScaleX = -1f

        // Animación de alas
        if (abs(velX) > 40f) {
            bridge.currentFrame = if ((time * 5.6f).toInt() % 2 == 0) 2 else 3
        } else {
            bridge.currentFrame = if ((time * 5.6f).toInt() % 2 == 0) 0 else 1
        }
        bridge.animOffsetY = sin(time * 3f) * 10f

        // Detectar si choca con paredes laterales para empezar a escalar
        val params = bridge.getWindowParams() ?: return
        if (params.x <= 0 || params.x >= bridge.screenWidth - bridge.petSpriteSize) {
            if (Random.nextFloat() < 0.3f) { // 30% de probabilidad de escalar al chocar
                startClimbing()
            }
        }
    }

    private fun startClimbing() {
        impState = ImpState.CLIMBING
        climbTimer = 0f
        velX = 0f
        velY = if (Random.nextBoolean()) -getBaseSpeed() else getBaseSpeed()
        bridge.animRotation = if (bridge.windowX < 100) 90f else -90f // Rotar hacia la pared
        bridge.showBubble("😈攀")
    }

    private fun updateClimbing(dt: Float) {
        climbTimer += dt
        time += dt
        
        val params = bridge.getWindowParams() ?: return
        params.y += (velY * dt).toInt()
        
        // Mantener pegado a la pared
        if (bridge.windowX < 100) params.x = 0 
        else params.x = bridge.screenWidth - bridge.petSpriteSize
        
        // Animación de escalada (frames 7-8)
        if ((time * 4f).toInt() % 5 == 0 && Random.nextFloat() < 0.05f) {
            bridge.currentFrame = 9 // Mirar atrás
        } else {
            bridge.currentFrame = if ((time * 6f).toInt() % 2 == 0) 7 else 8
        }

        // Cambiar dirección si llega arriba/abajo
        if (params.y < 50 || params.y > bridge.screenHeight - bridge.petSpriteSize - 100) {
            velY *= -1
        }

        // Salir de la pared después de un tiempo
        if (climbTimer > 5f && Random.nextFloat() < 0.02f) {
            stopClimbing()
        }
        
        bridge.updateWindowLayout(params)
    }

    private fun stopClimbing() {
        impState = ImpState.FLYING
        bridge.animRotation = 0f
        velX = if (bridge.windowX < 100) 150f else -150f // Impulso hacia afuera
        decisionTimer = 2f
    }

    override fun onInteract() {
        if (impState == ImpState.CLIMBING) stopClimbing()
        super.onInteract()
        bridge.animRotation = 0f 
        bridge.showBubble("😈🔥")
    }

    override fun updateInteracting(dt: Float) {
        // Secuencia de fuego (se mantiene igual)
        when {
            dt < 0.5f -> { bridge.currentFrame = 4; bridge.animScaleX = 1.15f; bridge.animScaleY = 1.15f }
            dt < 1.2f -> {
                bridge.currentFrame = 5
                if ((time * 15f).toInt() % 2 == 0) {
                    bridge.playHaptic(25)
                    bridge.animOffsetX = (Random.nextFloat() - 0.5f) * 5f
                }
                bridge.animScaleX = 1.25f; bridge.animScaleY = 1.25f
            }
            dt < 1.8f -> { bridge.currentFrame = 6; bridge.animAlpha = 0.7f; bridge.animScaleX = 1.0f; bridge.animScaleY = 1.0f }
            else -> { bridge.state = PetState.IDLE; reset(); impState = ImpState.FLYING }
        }
    }

    override fun reset() {
        super.reset()
        impState = ImpState.FLYING
        bridge.animAlpha = 1f
        bridge.animOffsetX = 0f
    }
}
