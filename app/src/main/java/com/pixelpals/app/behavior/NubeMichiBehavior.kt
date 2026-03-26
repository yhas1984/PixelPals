package com.pixelpals.app.behavior

import com.pixelpals.app.PetState
import com.pixelpals.app.R
import kotlin.math.*
import kotlin.random.Random

/**
 * NubeMichiBehavior — Gatito nube con movimientos suaves.
 */
class NubeMichiBehavior(
    bridge: PetViewBridge
) : BaseBehavior(bridge) {

    override val resourceIds = listOf(
        R.drawable.gato_0, R.drawable.gato_1, R.drawable.gato_2, R.drawable.gato_3
    )

    override fun updateIdle(dt: Float) {
        if (isLoading) return
        super.updateIdle(dt)
        
        // Flotación muy suave y lenta
        bridge.animOffsetY = sin(time * 1.2f) * 12f
        bridge.animOffsetX = cos(time * 0.5f) * 5f
        
        // Respiración (escala)
        val breathe = sin(time * 1.5f) * 0.04f
        bridge.animScaleY = 1f + breathe
        bridge.animScaleX = 1f - breathe * 0.2f

        // Frames (animación lenta)
        bridge.currentFrame = (time * 4f).toInt() % frames.size
    }

    override fun onInteract() {
        super.onInteract()
        bridge.showBubble("☁️ Purrr...")
        bridge.playHaptic(20)
        bridge.animScaleY = 1.25f // Se infla
    }

    override fun updateInteracting(dt: Float) {
        if (dt > 1.2f) {
            bridge.state = PetState.IDLE
            reset()
        }
    }
}
