package com.pixelpals.app.behavior

import com.pixelpals.app.PetState
import kotlin.math.*
import kotlin.random.Random

/**
 * GingerBehavior — Refactored Feline Elegance.
 */
class GingerBehavior(
    bridge: PetViewBridge
) : BaseBehavior(bridge) {

    override val resourceIds = (0..20).map { i ->
        (bridge as android.view.View).context.resources.getIdentifier(
            "ginger_$i", "drawable", (bridge as android.view.View).context.packageName
        )
    }

    private var walkTimer = 0f
    private var isWalking = false

    override fun updateIdle(dt: Float) {
        if (isLoading || frames.isEmpty()) return
        super.updateIdle(dt)
        
        if (Random.nextFloat() < 0.01f) isWalking = !isWalking

        if (isWalking) {
            walkTimer += dt
            bridge.currentFrame = if ((walkTimer * 5.6f).toInt() % 2 == 0) 0 else 1
            bridge.animOffsetY = abs(sin(walkTimer * 10f)) * 4f
            
            updateDecision(dt)
            applyMovement(dt)
            
            if (velX > 5) bridge.animScaleX = -1f
            else if (velX < -5) bridge.animScaleX = 1f
            
        } else {
            val cycle = time % 4f
            bridge.currentFrame = if (cycle < 2f) 4 else 5
            bridge.animScaleY = 1f + sin(time * 1.5f) * 0.015f
            velX = 0f
            velY = 0f
        }

        if (Random.nextFloat() < 0.002f) {
            bridge.showBubble("Meow~")
            bridge.playHaptic(10)
        }
    }

    override fun onInteract() {
        super.onInteract()
        bridge.showBubble("😻")
        bridge.playHaptic(40)
    }

    override fun updateInteracting(dt: Float) {
        if (frames.isEmpty()) return
        if (dt > 1.5f) {
            bridge.state = PetState.IDLE
            reset()
        } else {
            bridge.currentFrame = 7
            bridge.animScaleY = 1f + sin(dt * 10f) * 0.03f
        }
    }
}
