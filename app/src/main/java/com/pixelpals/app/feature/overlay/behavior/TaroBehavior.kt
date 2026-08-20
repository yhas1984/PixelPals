package com.pixelpals.app.feature.overlay.behavior

import com.pixelpals.app.core.domain.PetState
import com.pixelpals.app.core.motion.PetAnimationClip
import com.pixelpals.app.core.motion.PetAnimationPlayer
import com.pixelpals.app.core.motion.PetRandom

/** Legacy rollback behavior retained until the Runtime V2 release is established. */
internal class TaroBehavior(
    bridge: PetViewBridge,
    override val random: PetRandom,
) : BaseBehavior(bridge, random) {
    override val resourceIds: List<Int> = emptyList()
    private val controller = TaroV2Controller(random)
    private var player = PetAnimationPlayer()
    private var wasDragged = false

    init {
        loadSpriteSheetAssetAsync("pets/taro/taro_motion_v2.json") { spec ->
            player = PetAnimationPlayer(spec.clips.map { clip ->
                PetAnimationClip(clip.id, clip.frames, clip.loop, clip.frameDurationMs / 1000f)
            })
            applyOutput(controller.reset())
        }
    }

    override fun getBaseSpeed(): Float = 42f

    override fun updateIdle(dt: Float) {
        if (isLoading || spriteSheetBitmap == null) return
        val params = bridge.getWindowParams() ?: return
        val output = controller.update(dt, params.x.toFloat(), bridge.bounds.left.toFloat(), bridge.bounds.right.toFloat(), player.isFinished)
        applyOutput(output)
        output.positionX?.let {
            params.x = it.toInt().coerceIn(bridge.bounds.left, bridge.bounds.right)
            params.y = bridge.bounds.floor
            bridge.updateWindowLayout(params)
        }
        tickClip(dt)
        clampWindowParams(bridge.getWindowParams() ?: return, minY = bridge.bounds.top, maxY = bridge.bounds.floor)
    }

    override fun onInteract() {
        super.onInteract()
        applyOutput(controller.onTap())
    }

    override fun updateInteracting(dt: Float) {
        time += dt
        val output = controller.update(dt, bridge.windowX.toFloat(), bridge.bounds.left.toFloat(), bridge.bounds.right.toFloat(), player.isFinished)
        applyOutput(output)
        tickClip(dt)
        if (output.mode == TaroV2Mode.IDLE) bridge.state = PetState.IDLE
    }

    override fun updateDrag(dt: Float) {
        time += dt
        wasDragged = true
        applyOutput(controller.onDrag())
        tickClip(dt)
        bridge.animRotation = 0f
    }

    override fun onFling(velocityX: Float, velocityY: Float) {
        applyOutput(controller.onFling(velocityX))
    }

    override fun reset() {
        super.reset()
        val output = if (wasDragged) controller.resetAfterDrop() else controller.reset()
        wasDragged = false
        applyOutput(output)
        bridge.animRotation = 0f
    }

    private fun applyOutput(output: TaroV2Output) {
        if (player.clipId != output.clipId) setClip(output.clipId)
        bridge.animScaleX = output.facing
        bridge.animScaleY = 1f
        bridge.animOffsetX = 0f
        bridge.animOffsetY = 0f
        bridge.animRotation = 0f
    }

    private fun setClip(id: String) {
        if (player.setClip(id)) bridge.currentFrame = player.currentFrame()
    }

    private fun tickClip(dt: Float) {
        bridge.currentFrame = player.update(dt)
    }
}
