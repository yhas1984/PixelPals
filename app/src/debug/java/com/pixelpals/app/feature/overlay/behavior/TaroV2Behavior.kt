package com.pixelpals.app.feature.overlay.behavior

import com.pixelpals.app.core.domain.PetState
import com.pixelpals.app.core.motion.PetAnimationClip
import com.pixelpals.app.core.motion.PetAnimationPlayer
import com.pixelpals.app.core.motion.PetRandom
import kotlin.math.abs
import kotlin.math.roundToInt

/** Deterministic Taro V2 state machine. It is compiled only into debug builds. */
internal class TaroV2Behavior(
    bridge: PetViewBridge,
    override val random: PetRandom,
) : BaseBehavior(bridge, random) {
    override val resourceIds: List<Int> = emptyList()

    private enum class Mode { IDLE, WALK, TURN, HIDE, PEEK, FRONT_SOCIAL, TOUCH, SLEEP, CURIOSITY }

    private var mode = Mode.IDLE
    private var modeTimer = 0f
    private var modeDuration = 4f
    private var facing = 1f
    private var walkStart = 0f
    private var walkTarget = 0f
    private var walkDuration = 1f
    private var player = PetAnimationPlayer()

    init {
        loadSpriteSheetAssetAsync("pets/taro/taro_motion_v2.json") { spec ->
            player = PetAnimationPlayer(spec.clips.map { clip ->
                PetAnimationClip(clip.id, clip.frames, clip.loop, clip.frameDurationMs / 1000f)
            })
            setClip("idle")
        }
    }

    override fun getBaseSpeed(): Float = 42f

    private fun setClip(id: String) {
        if (player.setClip(id)) bridge.currentFrame = player.currentFrame()
    }

    private fun tickClip(dt: Float) {
        bridge.currentFrame = player.update(dt)
        bridge.animScaleX = facing
        bridge.animScaleY = 1f
        bridge.animOffsetY = 0f
        bridge.animRotation = 0f
    }

    private fun beginWalk() {
        val params = bridge.getWindowParams() ?: return
        val minX = bridge.bounds.left.toFloat()
        val maxX = bridge.bounds.right.toFloat()
        walkStart = params.x.toFloat().coerceIn(minX, maxX)
        walkTarget = random.nextFloat() * (maxX - minX).coerceAtLeast(1f) + minX
        if (abs(walkTarget - walkStart) < 12f) walkTarget = if (walkStart < (minX + maxX) / 2f) maxX else minX
        facing = if (walkTarget >= walkStart) 1f else -1f
        walkDuration = (abs(walkTarget - walkStart) / getBaseSpeed()).coerceIn(1.2f, 8f)
        mode = Mode.WALK
        modeTimer = 0f
        setClip("walk")
        params.y = bridge.bounds.floor
        bridge.updateWindowLayout(params)
    }

    override fun updateIdle(dt: Float) {
        if (isLoading || spriteSheetBitmap == null) return
        val step = dt.coerceIn(0f, 1f / 30f)
        time += step
        modeTimer += step
        when (mode) {
            Mode.IDLE -> { tickClip(step); if (modeTimer >= modeDuration) beginWalk() }
            Mode.WALK -> {
                val params = bridge.getWindowParams() ?: return
                val progress = (modeTimer / walkDuration).coerceIn(0f, 1f)
                params.x = (walkStart + (walkTarget - walkStart) * progress).roundToInt()
                params.y = bridge.bounds.floor
                bridge.updateWindowLayout(params)
                tickClip(step)
                if (progress >= 1f) { mode = Mode.TURN; modeTimer = 0f; modeDuration = 0.45f; setClip("turn") }
            }
            Mode.TURN -> { tickClip(step); if (modeTimer >= modeDuration) { facing *= -1f; mode = Mode.IDLE; modeTimer = 0f; modeDuration = 3f + random.nextFloat() * 4f; setClip("idle") } }
            Mode.HIDE -> { tickClip(step); if (player.isFinished) { mode = Mode.PEEK; modeTimer = 0f; setClip("peek") } }
            Mode.PEEK -> { tickClip(step); if (player.isFinished) { mode = Mode.IDLE; modeTimer = 0f; modeDuration = 2f; setClip("idle") } }
            Mode.FRONT_SOCIAL -> { tickClip(step); if (player.isFinished) { mode = Mode.IDLE; modeTimer = 0f; modeDuration = 3f; setClip("idle") } }
            Mode.TOUCH -> { tickClip(step); if (player.isFinished) { mode = Mode.HIDE; modeTimer = 0f; setClip("hide") } }
            Mode.SLEEP -> { tickClip(step); if (modeTimer >= modeDuration) { mode = Mode.IDLE; modeTimer = 0f; setClip("idle") } }
            Mode.CURIOSITY -> { tickClip(step); if (player.isFinished) { mode = Mode.IDLE; modeTimer = 0f; setClip("idle") } }
        }
        clampWindowParams(bridge.getWindowParams() ?: return, minY = bridge.bounds.top, maxY = bridge.bounds.floor)
    }

    override fun onInteract() {
        super.onInteract()
        mode = Mode.TOUCH
        modeTimer = 0f
        setClip("touch")
    }

    override fun updateInteracting(dt: Float) {
        time += dt
        modeTimer += dt
        if (mode == Mode.TOUCH) {
            tickClip(dt)
            if (player.isFinished) { bridge.state = PetState.IDLE; mode = Mode.HIDE; modeTimer = 0f; setClip("hide") }
        }
    }

    override fun updateDrag(dt: Float) {
        time += dt
        if (player.clipId != "idle") setClip("idle")
        tickClip(dt)
        bridge.animRotation = 0f
    }

    override fun onFling(velocityX: Float, velocityY: Float) {
        facing = if (velocityX >= 0f) 1f else -1f
        mode = Mode.CURIOSITY
        modeTimer = 0f
        setClip("curiosity")
    }

    override fun reset() {
        super.reset()
        mode = Mode.IDLE
        modeTimer = 0f
        modeDuration = 2f
        setClip("idle")
    }
}
