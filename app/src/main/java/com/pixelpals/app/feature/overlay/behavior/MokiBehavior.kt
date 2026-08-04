package com.pixelpals.app.feature.overlay.behavior

import android.view.View
import android.view.WindowManager
import com.pixelpals.app.core.domain.PetState
import com.pixelpals.app.core.motion.MokiMode
import com.pixelpals.app.core.motion.MokiMotionController
import com.pixelpals.app.core.motion.MokiPose
import com.pixelpals.app.core.motion.PetRandom

class MokiBehavior(
    bridge: PetViewBridge,
    override val random: PetRandom,
) : BaseBehavior(bridge, random) {
    override val resourceIds: List<Int> = emptyList()
    private val controller: MokiMotionController = MokiMotionController(density = (bridge as View).resources.displayMetrics.density, topClearanceDp = 88f)
    private var pose: MokiPose = controller.getPose()

    init {
        loadSpriteSheetAssetAsync("pets/moki/moki_sheet_v1.json")
    }

    override fun updateIdle(dt: Float) {
        if (isLoading || spriteSheetBitmap == null || spriteFrameRects.isEmpty()) return
        controller.updateViewport(bridge.screenWidth, bridge.screenHeight, bridge.petSpriteSize.toFloat(), topSystemInset = 0, bottomSystemInset = 0)
        pose = controller.update(dt)
        syncPoseToBridge()
        bridge.state = if (pose.mode == com.pixelpals.app.core.motion.MokiMode.TONGUE) PetState.INTERACTING else PetState.IDLE
    }

    override fun updateInteracting(dt: Float) {
        updateIdle(dt)
    }

    override fun onInteract() {
        controller.startTongueStrike()
        bridge.state = PetState.INTERACTING
        bridge.trackInteraction()
        bridge.playHaptic(24)
        bridge.showBubble("👅")
    }

    override fun onFling(velocityX: Float, velocityY: Float) {
        syncControllerToWindow()
        controller.releaseDrag(velocityX, velocityY)
        bridge.state = PetState.IDLE
    }

    override fun updateDrag(dt: Float) {
        syncControllerToWindow()
        bridge.currentFrame = 17
        bridge.animRotation = 0f
    }

    override fun updateFalling(dt: Float) {
        updateIdle(dt)
    }

    override fun updateJumping(dt: Float) {
        updateIdle(dt)
    }

    override fun reset() {
        if (controller.mode == MokiMode.DRAGGING) {
            syncControllerToWindow()
            controller.releaseDrag(0f, 0f)
        }
        super.reset()
    }

    private fun syncControllerToWindow() {
        val params: WindowManager.LayoutParams = bridge.getWindowParams() ?: return
        controller.updateViewport(
            bridge.screenWidth,
            bridge.screenHeight,
            bridge.petSpriteSize.toFloat(),
            topSystemInset = 0,
            bottomSystemInset = 0
        )
        val halfViewSize: Float = bridge.petSpriteSize * 0.7f
        val centerX: Float = params.x + halfViewSize
        val centerY: Float = params.y + halfViewSize
        if (controller.mode != MokiMode.DRAGGING) {
            controller.startDrag(centerX, centerY)
        } else {
            controller.moveDrag(centerX, centerY)
        }
        pose = controller.getPose()
    }

    private fun syncPoseToBridge() {
        val params: WindowManager.LayoutParams = bridge.getWindowParams() ?: return
        bridge.currentFrame = pose.frameIndex
        bridge.animRotation = pose.rotationDegrees
        bridge.animScaleX = 1f
        bridge.animScaleY = 1f
        bridge.animOffsetX = 0f
        bridge.animOffsetY = 0f
        val halfViewSize: Int = (bridge.petSpriteSize * 0.7f).toInt()
        params.x = (pose.x - halfViewSize).toInt().coerceIn(0, (bridge.screenWidth - params.width).coerceAtLeast(0))
        params.y = (pose.y - halfViewSize).toInt().coerceIn(0, (bridge.screenHeight - params.height).coerceAtLeast(0))
        bridge.updateWindowLayout(params)
    }
}
