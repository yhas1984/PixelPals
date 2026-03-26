package com.pixelpals.app.behavior

import com.pixelpals.app.PetState
import com.pixelpals.app.R
import kotlin.math.*
import kotlin.random.Random

/**
 * JellyBehavior — Bouncy slime that wobbles and jumps.
 * Inherits from BaseBehavior for optimized rendering.
 */
class JellyBehavior(
    bridge: PetViewBridge
) : BaseBehavior(bridge) {

    override val resourceIds = listOf(
        R.drawable.jelly_0, R.drawable.jelly_1, R.drawable.jelly_2,
        R.drawable.jelly_3, R.drawable.jelly_4, R.drawable.jelly_5,
        R.drawable.jelly_6, R.drawable.jelly_7, R.drawable.jelly_8,
        R.drawable.jelly_9, R.drawable.jelly_10, R.drawable.jelly_11
    )

    private var moveTimer = 0f
    private var nextJumpTime = 3f + Random.nextFloat() * 2f

    override fun updateIdle(dt: Float) {
        if (isLoading) return
        super.updateIdle(dt)
        
        // Rhythmic breathing wobble
        val sine = sin(time * 5f)
        bridge.animScaleY = 1.0f + sine * 0.08f
        bridge.animScaleX = 1.0f - sine * 0.05f
        
        // Animation frames
        bridge.currentFrame = (time * 8f).toInt() % frames.size

        // Decide to jump
        moveTimer += dt
        if (moveTimer > nextJumpTime) {
            bridge.state = PetState.JUMPING
            bridge.velocityY = -20f
            moveTimer = 0f
            nextJumpTime = 2f + Random.nextFloat() * 4f
        }
    }

    override fun updateJumping(dt: Float) {
        // Stretch while jumping
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
            // Squish reaction
            bridge.animScaleY = 0.5f
            bridge.animScaleX = 1.5f
        }
    }
}
