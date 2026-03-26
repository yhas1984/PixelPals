package com.pixelpals.app.behavior

import com.pixelpals.app.PetState
import kotlin.math.*
import kotlin.random.Random

class JellyBehavior(
    bridge: PetViewBridge
) : BaseBehavior(bridge) {

    override val resourceIds = (0..20).map { i ->
        (bridge as android.view.View).context.resources.getIdentifier(
            "jelly_$i", "drawable", (bridge as android.view.View).context.packageName
        )
    }

    private var moveTimer = 0f
    private var nextJumpTime = 3f + Random.nextFloat() * 2f

    override fun updateIdle(dt: Float) {
        if (isLoading || frames.isEmpty()) return
        super.updateIdle(dt)
        
        val sine = sin(time * 5f)
        bridge.animScaleY = 1.0f + sine * 0.08f
        bridge.animScaleX = 1.0f - sine * 0.05f
        
        bridge.currentFrame = (time * 8f).toInt() % frames.size

        moveTimer += dt
        if (moveTimer > nextJumpTime) {
            bridge.state = PetState.JUMPING
            bridge.velocityY = -20f
            moveTimer = 0f
            nextJumpTime = 2f + Random.nextFloat() * 4f
        }
    }

    override fun updateJumping(dt: Float) {
        if (frames.isEmpty()) return
        bridge.currentFrame = 4
        val stretch = abs(bridge.velocityY) / 25f
        bridge.animScaleY = 1.1f + stretch * 0.2f
        bridge.animScaleX = 0.9f - stretch * 0.1f
    }

    override fun onInteract() {
        super.onInteract()
        bridge.showBubble("🟢 Boing!")
        bridge.playHaptic(60)
    }

    override fun updateInteracting(dt: Float) {
        if (dt > 1.0f) {
            bridge.state = PetState.IDLE
            reset()
        } else {
            bridge.animScaleY = 0.5f
            bridge.animScaleX = 1.5f
        }
    }
}
