package com.pixelpals.app.feature.overlay.behavior

import com.pixelpals.app.core.domain.PetState
import com.pixelpals.app.core.motion.LumiMotionClip
import com.pixelpals.app.core.motion.LumiMotionController
import com.pixelpals.app.core.motion.LumiMotionSpec
import com.pixelpals.app.core.motion.PetRandom
import com.pixelpals.app.status.PetMood
import kotlin.math.roundToInt

/** Production adapter for Lumi's JSON-driven motion controller. */
class LumiBehavior(
    bridge: PetViewBridge,
    override val random: PetRandom,
) : BaseBehavior(bridge, random) {
    override val resourceIds: List<Int> = emptyList()

    private val controller = LumiMotionController(random)
    private var configured = false
    private var grounded = false

    init {
        loadSpriteSheetAssetAsync("pets/lumi/lumi_motion_v2.json") { spec ->
            controller.setSpec(
                LumiMotionSpec(
                    spec.clips.associate { clip ->
                        clip.id to LumiMotionClip(
                            id = clip.id,
                            frames = clip.frames,
                            loop = clip.loop,
                            frameDurationSeconds = clip.frameDurationMs / 1000f,
                        )
                    }
                )
            )
            configured = true
            syncControllerToWindow()
        }
    }

    override fun updateIdle(dt: Float) {
        updateMotion(dt)
    }

    override fun updateInteracting(dt: Float) {
        updateMotion(dt)
    }

    override fun updateAutonomous(dt: Float) {
        updateMotion(dt)
    }

    override fun updateJumping(dt: Float) {
        updateMotion(dt)
    }

    override fun updateFalling(dt: Float) {
        // PetView owns shared fling physics. The controller is resynchronised
        // when that physics settles and calls reset().
        syncControllerToWindow()
    }

    override fun updateDrag(dt: Float) {
        syncControllerToWindow()
        bridge.currentFrame = 4
        bridge.animScaleX = 1f
        bridge.animScaleY = 1f
        bridge.animRotation = 0f
        bridge.animOffsetX = 0f
        bridge.animOffsetY = 0f
    }

    override fun onInteract() {
        val clipId = controller.startInteraction()
        bridge.state = PetState.INTERACTING
        bridge.trackInteraction()
        bridge.playHaptic(32)
        bridge.showBubble(if (clipId == "magic") "✨" else "💛")
    }

    override fun reset() {
        syncControllerToWindow()
        controller.reset()
        super.reset()
    }

    private fun updateMotion(dt: Float) {
        if (!configured || isLoading || spriteSheetBitmap == null || spriteFrameRects.isEmpty()) return

        syncControllerToWindow()
        val pose = controller.update(
            deltaSeconds = dt,
            shouldSleep = bridge.petStatus.mood == PetMood.SLEEPY || bridge.petStatus.energy <= 28,
        )
        syncPoseToBridge(pose)
        bridge.state = when (pose.mode) {
            com.pixelpals.app.core.motion.LumiMode.HOP_UP,
            com.pixelpals.app.core.motion.LumiMode.HOP_DOWN,
            com.pixelpals.app.core.motion.LumiMode.POUNCE,
            com.pixelpals.app.core.motion.LumiMode.SOCIAL,
            com.pixelpals.app.core.motion.LumiMode.MAGIC -> PetState.INTERACTING
            else -> PetState.IDLE
        }
    }

    private fun syncControllerToWindow() {
        controller.updateViewport(
            width = bridge.screenWidth,
            height = bridge.screenHeight,
            drawSize = bridge.petSpriteSize.toFloat(),
            topSystemInset = bridge.topSystemInsetPx,
            bottomSystemInset = bridge.bottomSystemInsetPx,
        )
        val params = bridge.getWindowParams() ?: return
        if (configured && !grounded) {
            params.y = bridge.groundY
            bridge.updateWindowLayout(params)
            grounded = true
        }
        val halfSize = bridge.petSpriteSize * 0.5f
        controller.setPosition(params.x + halfSize, params.y + halfSize)
    }

    private fun syncPoseToBridge(pose: com.pixelpals.app.core.motion.LumiPose) {
        val params = bridge.getWindowParams() ?: return
        val halfSize = bridge.petSpriteSize * 0.5f
        params.x = (pose.x - halfSize).roundToInt()
            .coerceIn(0, (bridge.screenWidth - bridge.petSpriteSize).coerceAtLeast(0))
        params.y = (pose.y - halfSize).roundToInt()
            .coerceIn(bridge.topSystemInsetPx, (bridge.screenHeight - bridge.bottomSystemInsetPx - bridge.petSpriteSize).coerceAtLeast(bridge.topSystemInsetPx))
        bridge.updateWindowLayout(params)
        bridge.currentFrame = pose.frameIndex
        bridge.animScaleX = if (pose.facingRight) 1f else -1f
        bridge.animScaleY = 1f
        bridge.animRotation = 0f
        bridge.animOffsetX = 0f
        bridge.animOffsetY = 0f
    }
}
