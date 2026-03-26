package com.pixelpals.app.behavior

import com.pixelpals.app.PetState
import kotlin.math.*
import kotlin.random.Random

/**
 * DuckBehavior — Curious and adventurous duck.
 */
class DuckBehavior(
    bridge: PetViewBridge
) : BaseBehavior(bridge) {

    override val resourceIds = (0..20).map { i ->
        (bridge as android.view.View).context.resources.getIdentifier(
            "patito_$i", "drawable", (bridge as android.view.View).context.packageName
        )
    }

    private var walkTimer = 0f
    private var isWaddling = false

    override fun updateIdle(dt: Float) {
        if (isLoading || frames.isEmpty()) return
        super.updateIdle(dt)
        
        if (Random.nextFloat() < 0.01f) isWaddling = !isWaddling

        if (isWaddling) {
            walkTimer += dt
            bridge.currentFrame = if ((walkTimer * 8f).toInt() % 2 == 0) 1 else 2
            bridge.animOffsetY = abs(sin(walkTimer * 15f)) * 5f
            bridge.animRotation = sin(walkTimer * 10f) * 3f
        } else {
            val cycle = time % 3f
            bridge.currentFrame = if (cycle < 2f) 0 else 3
            bridge.animScaleY = 1f + sin(time * 2f) * 0.02f
        }

        if (Random.nextFloat() < 0.004f) {
            bridge.showBubble("Quack!")
            bridge.playHaptic(30)
        }
    }

    override fun onInteract() {
        super.onInteract()
        bridge.showBubble("QUACK!!")
        bridge.playHaptic(100)
    }

    override fun updateInteracting(dt: Float) {
        if (frames.isEmpty()) return
        if (dt > 1.2f) {
            bridge.state = PetState.IDLE
            reset()
        } else {
            bridge.currentFrame = 14
            bridge.animScaleY = 1.2f
            bridge.animOffsetY = -15f
        }
    }
}
