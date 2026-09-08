package com.pixelpals.app.feature.overlay.behavior

import com.pixelpals.app.core.domain.PetState
import com.pixelpals.app.R
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.roundToInt
import com.pixelpals.app.core.motion.PetRandom

/**
 * ImpBehavior — Diablillo Travieso.
 * IA: Vuelo (0-1, 2-3), Escalada vertical (4-5-6), Interacción (7-8-9).
 */
class ImpBehavior(bridge: PetViewBridge, override val random: PetRandom) : BaseBehavior(bridge, random) {

    private var restRequested: Boolean = false
    private val restFade = com.pixelpals.app.core.rest.RestFadeTransition()
    override fun onScheduledRestRequested(requested: Boolean) {
        restRequested = requested
        if (!requested) cancelRestFade()
    }
    override fun canStartScheduledSleep(reducedMotion: Boolean): Boolean =
        !restFade.active && impState == ImpState.FLYING &&
            kotlin.math.hypot(velX, velY) <= 1f && abs(bridge.animRotation) <= .1f

    private fun cancelRestFade() {
        if (!restFade.active) return
        restFade.cancel()
        (bridge as? android.view.View)?.alpha = 1f
    }
    override fun advanceScheduledRestTransition(delta: Float, reducedMotion: Boolean) {
        if (!restRequested || !reducedMotion) {
            cancelRestFade()
            return
        }
        if (!restFade.active && canStartScheduledSleep(true)) return
        if (!restFade.active) restFade.start()
        val resetPose: Boolean = restFade.advance(delta)
        (bridge as? android.view.View)?.alpha = restFade.opacity
        if (resetPose) {
            impState = ImpState.FLYING
            velX = 0f
            velY = 0f
            bridge.animRotation = 0f
            bridge.animOffsetX = 0f
            bridge.animOffsetY = 0f
            bridge.animScaleY = 1f
            bridge.currentFrame = 0
        }
    }
    override fun destroy() {
        cancelRestFade()
        super.destroy()
    }

    override val resourceIds = listOf(R.drawable.diablillo_0, R.drawable.diablillo_1, R.drawable.diablillo_2, R.drawable.diablillo_3, R.drawable.diablillo_4, R.drawable.diablillo_5, R.drawable.diablillo_6, R.drawable.diablillo_7, R.drawable.diablillo_8, R.drawable.diablillo_9)

    init {
        loadFramesAsync()
    }

    private enum class ImpState { FLYING, CLIMBING, DASHING }
    private var impState = ImpState.FLYING
    private var climbTimer = 0f
    private var climbDuration = 1.8f
    private var isClimbingRight = false 

    override fun getBaseSpeed(): Float = if (impState == ImpState.CLIMBING) 120f else 250f

    override fun updateIdle(dt: Float) {
        if (isLoading) return
        when (impState) {
            ImpState.FLYING -> updateFlying(dt)
            ImpState.CLIMBING -> updateClimbing(dt)
            ImpState.DASHING -> updateDashing(dt)
        }
    }

    private fun updateDashing(dt: Float) {
        time += dt
        val params = bridge.getWindowParams() ?: return
        val dashSpeed = 560f
        params.x = (params.x + ((if (isClimbingRight) dashSpeed else -dashSpeed) * dt).toInt())
            .coerceIn(0, safeMaxX())
        params.y = params.y.coerceIn(safeMinY(), safeMaxY())
        bridge.currentFrame = if ((time * 14f).toInt() % 2 == 0) 2 else 3
        bridge.animScaleX = if (isClimbingRight) 1f else -1f
        bridge.animOffsetX = if (isClimbingRight) 4f else -4f
        bridge.updateWindowLayout(params)
        if (time > 0.55f) {
            impState = ImpState.FLYING
            velX = if (isClimbingRight) -210f else 210f
            bridge.animOffsetX = 0f
        }
    }

    private fun updateFlying(dt: Float) {
        if (restRequested) {
            settleForRest(dt)
            return
        }
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

    private fun settleForRest(dt: Float) {
        val step: Float = dt.coerceIn(0f, 1f / 30f)
        val factor: Float = kotlin.math.exp(-6f * step)
        velX *= factor
        velY *= factor
        val params = bridge.getWindowParams() ?: return
        params.x = (params.x + velX * step).roundToInt().coerceIn(0, safeMaxX())
        params.y = (params.y + velY * step).roundToInt().coerceIn(safeMinY(), safeMaxY())
        bridge.updateWindowLayout(params)
        time += step
        bridge.currentFrame = if ((time * 5f).toInt() % 2 == 0) 0 else 1
        bridge.animOffsetX *= factor
        bridge.animOffsetY *= factor
        bridge.animRotation *= factor
    }

    private fun startClimbing(isRight: Boolean) {
        impState = ImpState.CLIMBING
        climbTimer = 0f
        climbDuration = 1.2f + random.nextFloat() * 1.6f
        isClimbingRight = isRight
        velX = 0f
        velY = -getBaseSpeed()
        bridge.animRotation = if (isRight) -90f else 90f
        bridge.animScaleX = if (isRight) 1f else -1f 
        bridge.showBubble("😈")
    }

    private fun updateClimbing(dt: Float) {
        climbTimer += dt
        time += dt
        val params = bridge.getWindowParams() ?: return
        params.y += (velY * dt).toInt()
        params.x = if (isClimbingRight) bridge.screenWidth - bridge.petSpriteSize else 0
        
        val cycle = (time * 6f).toInt() % 3
        bridge.currentFrame = when(cycle) { 0 -> 4; 1 -> 5; else -> 6 }

        if (params.y < safeMinY()) params.y = safeMinY()
        if (params.y > safeMaxY()) {
            params.y = safeMaxY()
        }
        if (climbTimer > climbDuration) {
            impState = ImpState.FLYING
            velX = if (isClimbingRight) -200f else 200f
            velY = if (params.y < bridge.screenHeight * 0.4f) 120f else -120f
            bridge.animRotation = 0f
            bridge.animOffsetX = 0f
            bridge.animOffsetY = 0f
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
        bridge.state = PetState.IDLE
        reset()
    }

    override fun onInteract() {
        super.onInteract()
        impState = ImpState.FLYING
        bridge.animRotation = 0f
    }

    override fun onFling(velocityX: Float, velocityY: Float) {
        super.onInteract()
        bridge.state = PetState.IDLE
        isClimbingRight = velocityX >= 0f
        impState = ImpState.DASHING
        time = 0f
        bridge.showBubble("💨")
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
