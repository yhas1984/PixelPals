package com.pixelpals.app.behavior

import com.pixelpals.app.PetState
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * ImpBehavior — Diablillo Travieso.
 * IA: Vuelo (0-1, 2-3), Escalada vertical (4-5-6), Interacción (7-8-9).
 */
class ImpBehavior(bridge: PetViewBridge) : BaseBehavior(bridge) {

    override val resourceIds = (0..20).map { i ->
        (bridge as android.view.View).context.resources.getIdentifier(
            "diablillo_$i", "drawable", (bridge as android.view.View).context.packageName
        )
    }

    private enum class ImpState { FLYING, CLIMBING }
    private var impState = ImpState.FLYING
    private var climbTimer = 0f
    private var isClimbingRight = false 

    override fun getBaseSpeed(): Float = if (impState == ImpState.CLIMBING) 120f else 250f

    override fun updateIdle(dt: Float) {
        if (isLoading) return
        when (impState) {
            ImpState.FLYING -> updateFlying(dt)
            ImpState.CLIMBING -> updateClimbing(dt)
        }
    }

    private fun updateFlying(dt: Float) {
        updateDecision(dt)
        applyMovement(dt)
        time += dt

        if (abs(velX) > 40f) {
            bridge.currentFrame = if ((time * 5f).toInt() % 2 == 0) 2 else 3
            bridge.animScaleX = if (velX > 0) 1f else -1f
        } else {
            bridge.currentFrame = if ((time * 5f).toInt() % 2 == 0) 0 else 1
            bridge.animScaleX = 1f
        }
        bridge.animOffsetY = sin(time * 3f) * 10f

        val params = bridge.getWindowParams() ?: return
        if (params.x <= 0 || params.x >= bridge.screenWidth - bridge.petSpriteSize) {
            startClimbing(params.x >= bridge.screenWidth - bridge.petSpriteSize)
        }
    }

    private fun startClimbing(isRight: Boolean) {
        impState = ImpState.CLIMBING
        climbTimer = 0f
        isClimbingRight = isRight
        velX = 0f
        velY = -getBaseSpeed()
        bridge.animRotation = 0f 
        bridge.animScaleX = if (isRight) 1f else -1f 
        bridge.showBubble("😈攀")
    }

    private fun updateClimbing(dt: Float) {
        climbTimer += dt
        time += dt
        val params = bridge.getWindowParams() ?: return
        params.y += (velY * dt).toInt()
        params.x = if (isClimbingRight) bridge.screenWidth - bridge.petSpriteSize else 0
        
        val cycle = (time * 6f).toInt() % 3
        bridge.currentFrame = when(cycle) { 0 -> 4; 1 -> 5; else -> 6 }

        if (params.y < 50 || params.y > bridge.screenHeight - bridge.petSpriteSize - 100) velY *= -1
        if (climbTimer > 4f && Random.nextFloat() < 0.05f) {
            impState = ImpState.FLYING
            velX = if (isClimbingRight) -200f else 200f
        }
        bridge.updateWindowLayout(params)
    }

    override fun updateDrag(dt: Float) {
        // Bloqueo total de rotación y volteretas durante el arrastre
        bridge.animRotation = 0f
        bridge.animScaleX = 1f
        bridge.animScaleY = 1f
        bridge.currentFrame = 0
        bridge.animOffsetX = sin(time * 50f) * 5f
    }

    override fun updateFalling(dt: Float) {
        // Sin volteretas al soltar
        time += dt
        bridge.currentFrame = 0
        bridge.animRotation = 0f
    }

    override fun onInteract() {
        super.onInteract()
        impState = ImpState.FLYING
        bridge.animRotation = 0f
    }

    override fun updateInteracting(dt: Float) {
        interactionTimer += dt
        // Ataque: Inflar(7), Escupir(8), Humo(9)
        when {
            interactionTimer < 0.5f -> bridge.currentFrame = 7
            interactionTimer < 1.2f -> bridge.currentFrame = 8
            interactionTimer < 1.8f -> bridge.currentFrame = 9
            else -> { bridge.state = PetState.IDLE; reset() }
        }
    }

    override fun reset() {
        super.reset()
        impState = ImpState.FLYING
        bridge.animAlpha = 1f
        bridge.animOffsetX = 0f
        bridge.animRotation = 0f
        bridge.animScaleX = 1f
    }
}
