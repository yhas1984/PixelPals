package com.pixelpals.app.behavior

import com.pixelpals.app.PetState
import kotlin.random.Random

class CorgiBehavior(
    bridge: PetViewBridge
) : BaseBehavior(bridge) {

    override val resourceIds = (0..20).mapNotNull { i ->
        val id = (bridge as android.view.View).context.resources.getIdentifier(
            "corgi_$i", "drawable", (bridge as android.view.View).context.packageName
        )
        if (id != 0) id else null
    }

    private var internalState = CorgiState.WALKING
    private var stateTimer = 0f
    private var hasFoundBone = false

    enum class CorgiState { WALKING, SNIFFING, DIGGING, IDLE, SPINNING }

    override fun getBaseSpeed(): Float = 120f 

    override fun updateIdle(dt: Float) {
        if (isLoading || frames.isEmpty()) return
        time += dt
        stateTimer += dt

        when (internalState) {
            CorgiState.WALKING -> {
                val cycle = (time * 6f).toInt() % 3
                bridge.currentFrame = min(cycle, frames.size - 1)
                
                updateDecision(dt) 
                applyMovement(dt)  

                if (velX > 2f) bridge.animScaleX = 1f 
                else if (velX < -2f) bridge.animScaleX = -1f

                if (stateTimer > 4f && Random.nextFloat() < 0.02f) {
                    changeState(CorgiState.SNIFFING)
                } else if (stateTimer > 8f) {
                    changeState(CorgiState.IDLE)
                }
            }
            CorgiState.SNIFFING -> {
                bridge.currentFrame = min(3, frames.size - 1) 
                velX = 0f
                velY = 0f
                if (stateTimer > 2f) {
                    if (Random.nextFloat() < 0.6f) changeState(CorgiState.DIGGING)
                    else changeState(CorgiState.IDLE)
                }
            }
            CorgiState.DIGGING -> {
                velX = 0f
                velY = 0f
                if (stateTimer < 2f) {
                    bridge.currentFrame = min(4, frames.size - 1)
                    if ((time * 15f).toInt() % 2 == 0) bridge.animOffsetX = (Random.nextFloat() - 0.5f) * 6f 
                } else {
                    bridge.currentFrame = min(5, frames.size - 1)
                    bridge.animOffsetX = 0f
                    if (!hasFoundBone) {
                        bridge.showBubble("🦴!")
                        hasFoundBone = true
                    }
                }
                if (stateTimer > 4f) changeState(CorgiState.IDLE)
            }
            CorgiState.IDLE -> {
                velX = 0f
                velY = 0f
                bridge.currentFrame = if ((time * 2f).toInt() % 2 == 0) min(6, frames.size - 1) else min(7, frames.size - 1)
                if (stateTimer > 3f) changeState(CorgiState.WALKING)
            }
            else -> {}
        }
    }

    private fun changeState(newState: CorgiState) {
        internalState = newState
        stateTimer = 0f
        hasFoundBone = false
        bridge.animOffsetX = 0f
        if (newState == CorgiState.WALKING) decisionTimer = 0f 
    }

    override fun onInteract() {
        super.onInteract() 
        changeState(CorgiState.SPINNING) 
        velX = 0f 
        velY = 0f
        bridge.showBubble("✨🐾")
        bridge.playHaptic(30)
    }

    override fun updateInteracting(dt: Float) {
        if (frames.size < 10) {
            bridge.currentFrame = frames.size - 1
            interactionTimer += dt
            if (interactionTimer >= 3.0f) {
                bridge.state = PetState.IDLE
                changeState(CorgiState.WALKING)
            }
            return
        }

        interactionTimer += dt 

        if (interactionTimer < 3.0f) {
            // REDUCIDO UN 50%: de 8f pasa a 4f.
            val cycle = (interactionTimer * 4f).toInt() % 2
            bridge.currentFrame = if (cycle == 0) 8 else 9
        } else {
            bridge.state = PetState.IDLE
            changeState(CorgiState.WALKING)
        }
    }

    private fun min(a: Int, b: Int): Int = if (a < b) a else b
}
