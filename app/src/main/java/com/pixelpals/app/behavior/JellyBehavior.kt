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

    private enum class JellyMode { BOUNCING, CHARGING, SPLAT }
    private var mode = JellyMode.BOUNCING
    private var modeTimer = 0f
    private var nextJumpTime = 1.8f + Random.nextFloat() * 1.4f

    override fun updateIdle(dt: Float) {
        if (isLoading || frames.isEmpty()) return
        time += dt
        modeTimer += dt

        when (mode) {
            JellyMode.BOUNCING -> {
                updateDecision(dt)
                applyMovement(dt)
                bridge.currentFrame = ((time * 10f).toInt() % 4).coerceAtMost(frames.size - 1)
                val wobble = sin(time * 7f)
                bridge.animScaleY = 1f + wobble * 0.10f
                bridge.animScaleX = 1f - wobble * 0.08f
                bridge.animOffsetY = abs(sin(time * 12f)) * 3f
                if (modeTimer > nextJumpTime) {
                    mode = JellyMode.CHARGING
                    modeTimer = 0f
                    velX = 0f
                    velY = 0f
                }
            }

            JellyMode.CHARGING -> {
                bridge.currentFrame = 4.coerceAtMost(frames.size - 1)
                val compress = (modeTimer / 0.45f).coerceIn(0f, 1f)
                bridge.animScaleY = 1f - compress * 0.45f
                bridge.animScaleX = 1f + compress * 0.35f
                if (modeTimer > 0.45f) {
                    mode = JellyMode.SPLAT
                    modeTimer = 0f
                }
            }

            JellyMode.SPLAT -> {
                bridge.currentFrame = 5.coerceAtMost(frames.size - 1)
                bridge.animScaleY = 0.7f + sin(modeTimer * 20f) * 0.12f
                bridge.animScaleX = 1.25f - sin(modeTimer * 20f) * 0.10f
                if (modeTimer > 0.55f) {
                    mode = JellyMode.BOUNCING
                    modeTimer = 0f
                    nextJumpTime = 1.6f + Random.nextFloat() * 1.6f
                    decisionTimer = 0f
                }
            }
        }
    }

    override fun updateJumping(dt: Float) {
        updateIdle(dt)
    }

    override fun onInteract() {
        bridge.state = PetState.INTERACTING
        interactionTimer = 0f
        bridge.showBubble("🟢 Boing!")
        bridge.playHaptic(60)
        mode = JellyMode.CHARGING
        modeTimer = 0f
    }

    override fun updateInteracting(dt: Float) {
        interactionTimer += dt
        bridge.currentFrame = 6.coerceAtMost(frames.size - 1)
        bridge.animScaleY = 0.55f + abs(sin(interactionTimer * 14f)) * 0.20f
        bridge.animScaleX = 1.35f - abs(sin(interactionTimer * 14f)) * 0.15f
        if (interactionTimer > 1.0f) {
            bridge.state = PetState.IDLE
            reset()
        }
    }

    override fun reset() {
        super.reset()
        mode = JellyMode.BOUNCING
        modeTimer = 0f
    }
}
