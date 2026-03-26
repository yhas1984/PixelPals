package com.pixelpals.app.behavior

import com.pixelpals.app.PetState
import com.pixelpals.app.R
import kotlin.math.sin
import kotlin.math.cos
import kotlin.random.Random

/**
 * BloopBehavior — Ethereal ghost that floats.
 */
class BloopBehavior(
    bridge: PetViewBridge
) : BaseBehavior(bridge) {

    override val resourceIds = listOf(
        R.drawable.fantasma_0, R.drawable.fantasma_1, R.drawable.fantasma_2,
        R.drawable.fantasma_3, R.drawable.fantasma_4, R.drawable.fantasma_5,
        R.drawable.fantasma_6, R.drawable.fantasma_7, R.drawable.fantasma_8,
        R.drawable.fantasma_9, R.drawable.fantasma_10, R.drawable.fantasma_11
    )

    override fun updateIdle(dt: Float) {
        if (isLoading) return
        super.updateIdle(dt)
        
        bridge.animOffsetY = sin(time * 1.5f) * 25f
        bridge.animOffsetX = cos(time * 1.0f) * 12f
        bridge.animAlpha = 0.7f + sin(time * 2f) * 0.2f
        bridge.currentFrame = (time * 6f).toInt() % frames.size

        if (Random.nextFloat() < 0.003f) {
            bridge.showBubble(listOf("👻", "🫧", "✨", "🌙").random())
        }
    }

    override fun onInteract() {
        super.onInteract()
        bridge.showBubble("!")
        bridge.playHaptic(20)
        bridge.animScaleX = 0.8f
        bridge.animScaleY = 0.8f
    }

    override fun updateInteracting(dt: Float) {
        if (dt > 1.0f) {
            bridge.state = PetState.IDLE
            reset()
        } else {
            bridge.animAlpha = 0.4f
        }
    }
}
