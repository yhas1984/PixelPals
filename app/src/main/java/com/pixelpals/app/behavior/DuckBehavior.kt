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

    private enum class DuckMode { WADDLE, PEEK, SPRINT }
    private var mode = DuckMode.PEEK
    private var modeTimer = 0f

    override fun updateIdle(dt: Float) {
        if (isLoading || frames.isEmpty()) return
        time += dt
        modeTimer += dt

        when (mode) {
            DuckMode.WADDLE -> {
                updateDecision(dt)
                applyMovement(dt)
                bridge.currentFrame = if ((time * 9f).toInt() % 2 == 0) 1 else 2
                bridge.animOffsetY = abs(sin(time * 16f)) * 5f
                bridge.animRotation = sin(time * 9f) * 4f
                if (velX > 3f) bridge.animScaleX = -1f
                else if (velX < -3f) bridge.animScaleX = 1f
                if (modeTimer > 4.5f && Random.nextFloat() < 0.40f) {
                    mode = DuckMode.PEEK
                    modeTimer = 0f
                    velX = 0f
                    velY = 0f
                }
            }

            DuckMode.PEEK -> {
                velX = 0f
                velY = 0f
                bridge.currentFrame = if ((time * 3f).toInt() % 2 == 0) 0 else 3.coerceAtMost(frames.size - 1)
                bridge.animScaleY = 1f + sin(time * 2f) * 0.02f
                bridge.animRotation = sin(time * 1.2f) * 1.5f
                if (modeTimer > 2.8f) {
                    mode = if (Random.nextFloat() < 0.25f) DuckMode.SPRINT else DuckMode.WADDLE
                    modeTimer = 0f
                    decisionTimer = 0f
                }
            }

            DuckMode.SPRINT -> {
                updateDecision(dt)
                velX *= 1.6f
                velY *= 1.2f
                applyMovement(dt)
                bridge.currentFrame = 4.coerceAtMost(frames.size - 1)
                bridge.animRotation = sin(time * 18f) * 7f
                bridge.animOffsetY = abs(sin(time * 20f)) * 7f
                if (modeTimer > 1.2f) {
                    mode = DuckMode.PEEK
                    modeTimer = 0f
                }
            }
        }

        if (Random.nextFloat() < 0.003f) {
            bridge.showBubble("Quack!")
            bridge.playHaptic(30)
        }
    }

    override fun onInteract() {
        bridge.state = PetState.INTERACTING
        interactionTimer = 0f
        bridge.showBubble("QUACK!!")
        bridge.playHaptic(100)
    }

    override fun updateInteracting(dt: Float) {
        if (frames.isEmpty()) return
        interactionTimer += dt
        if (interactionTimer > 1.2f) {
            bridge.state = PetState.IDLE
            reset()
        } else {
            bridge.currentFrame = 14
            bridge.animScaleY = 1.2f + sin(interactionTimer * 14f) * 0.08f
            bridge.animScaleX = 0.9f
            bridge.animOffsetY = -15f - abs(sin(interactionTimer * 20f)) * 6f
        }
    }

    override fun reset() {
        super.reset()
        mode = DuckMode.PEEK
        modeTimer = 0f
    }
}
