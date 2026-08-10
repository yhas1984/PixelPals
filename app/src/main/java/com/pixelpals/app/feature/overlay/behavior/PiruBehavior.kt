package com.pixelpals.app.feature.overlay.behavior

import com.pixelpals.app.core.domain.PetState
import com.pixelpals.app.core.motion.PetRandom
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * PiruBehavior — Pingüinito con bufanda.
 * Waddle por el suelo como el Patito, con deslizamientos sobre la panza
 * (frames 4-5) y saltitos alegres. Es un pet de suelo: nunca vuela.
 * IA atlas: idle (0-3), slide (4-5), jump (6-7), happy (8-11), touch (12-14), sleep (15).
 */
class PiruBehavior(
    bridge: PetViewBridge,
    override val random: PetRandom,
) : BaseBehavior(bridge, random) {

    override val resourceIds: List<Int> = emptyList()

    private enum class Mode { WADDLE, SLIDE, JUMP, HAPPY, TOUCH, SLEEP }

    private var mode = Mode.WADDLE
    private var modeTimer = 0f
    private var modeDuration = 2.5f
    private var animClock = 0f
    private var swimStartX = 0f
    private var swimTargetX = 0f
    private var swimDuration = 0f
    private var facingDir = 1f
    private var jumpStartY = 0f
    private var jumpTargetY = 0f

    init {
        loadSpriteSheetAssetAsync("pets/piru/piru_sheet_v1.json")
    }

    override fun getBaseSpeed(): Float = 0f

    private fun groundY(): Float = bridge.groundY.coerceAtLeast(60).toFloat()

    private fun facingScale(directionX: Float): Float = if (directionX >= 0f) 1f else -1f

    private fun startWaddle(resetTimer: Boolean = true) {
        val params = bridge.getWindowParams() ?: return
        val minX = 0f
        val maxX = (bridge.screenWidth - bridge.petSpriteSize).coerceAtLeast(0).toFloat()
        val baseY = groundY()

        mode = Mode.WADDLE
        if (resetTimer) modeTimer = 0f

        swimStartX = params.x.toFloat().coerceIn(minX, maxX)
        params.y = baseY.roundToInt()
        bridge.updateWindowLayout(params)

        swimTargetX = random.nextFloat() * (maxX - minX) + minX
        val dx = swimTargetX - swimStartX
        if (abs(dx) > 10f) facingDir = if (dx >= 0f) 1f else -1f
        swimDuration = (abs(dx) / 110f).coerceIn(2.0f, 5.0f)
    }

    private fun startSlide() {
        val params = bridge.getWindowParams() ?: return
        val minX = 0f
        val maxX = (bridge.screenWidth - bridge.petSpriteSize).coerceAtLeast(0).toFloat()
        mode = Mode.SLIDE
        modeTimer = 0f
        swimStartX = params.x.toFloat().coerceIn(minX, maxX)
        val dir = if (random.nextFloat() < 0.5f) -1f else 1f
        swimTargetX = (swimStartX + dir * bridge.petSpriteSize * (3f + random.nextFloat() * 5f))
            .coerceIn(minX, maxX)
        facingDir = dir
        swimDuration = 1.1f + random.nextFloat() * 0.9f
        params.y = groundY().roundToInt()
        bridge.updateWindowLayout(params)
    }

    private fun startJump() {
        val params = bridge.getWindowParams() ?: return
        jumpStartY = params.y.toFloat()
        jumpTargetY = groundY() - bridge.petSpriteSize * 0.9f
        mode = Mode.JUMP
        modeTimer = 0f
        // El salto mantiene la X
        val minX = 0f
        val maxX = (bridge.screenWidth - bridge.petSpriteSize).coerceAtLeast(0).toFloat()
        swimTargetX = (params.x.toFloat() + facingDir * bridge.petSpriteSize * 0.6f).coerceIn(minX, maxX)
    }

    override fun updateIdle(dt: Float) {
        if (isLoading || spriteSheetBitmap == null || spriteFrameRects.isEmpty()) return
        val step = dt.coerceIn(0f, 1f / 30f)
        time += step
        modeTimer += step
        animClock += step

        when (mode) {
            Mode.WADDLE -> updateWaddle(step)
            Mode.SLIDE -> updateSlide(step)
            Mode.JUMP -> updateJump(step)
            Mode.HAPPY -> updateHappy(step)
            Mode.TOUCH -> updateTouch(step)
            Mode.SLEEP -> updateSleep(step)
        }
        syncWindowPosition()
    }

    private fun updateWaddle(dt: Float) {
        val params = bridge.getWindowParams() ?: return
        if (swimDuration <= 0f) startWaddle(resetTimer = false)

        val t = (modeTimer / swimDuration).coerceIn(0f, 1f)
        val easedT = sin((t * PI).toFloat() / 2f)
        val x = swimStartX + (swimTargetX - swimStartX) * easedT
        val y = groundY()

        params.x = x.roundToInt()
        params.y = y.roundToInt()
        bridge.updateWindowLayout(params)

        val spec = spriteSheetSpec ?: return
        val clip = spec.clip("idle") ?: return
        val idx = ((animClock / 0.22f).toInt() % clip.frames.size)
        bridge.currentFrame = clip.frames[idx]
        bridge.animScaleX = facingScale(facingDir)
        bridge.animScaleY = 1f + sin(time * 4.4f) * 0.02f
        bridge.animOffsetX = 0f
        bridge.animOffsetY = abs(sin(time * 8.5f)) * 2f
        bridge.animRotation = facingDir * sin(time * 6f) * 2f

        val roll = random.nextFloat()
        if (roll < 0.0004f) {
            mode = Mode.HAPPY
            modeTimer = 0f
            modeDuration = 1.2f + random.nextFloat() * 1.0f
        } else if (roll < 0.0008f) {
            startSlide()
        } else if (roll < 0.001f) {
            startJump()
        } else if (t >= 1f) startWaddle()
    }

    private fun updateSlide(dt: Float) {
        val params = bridge.getWindowParams() ?: return
        val t = (modeTimer / swimDuration).coerceIn(0f, 1f)
        val x = swimStartX + (swimTargetX - swimStartX) * t
        params.x = x.roundToInt()
        params.y = groundY().roundToInt()
        bridge.updateWindowLayout(params)

        val spec = spriteSheetSpec ?: return
        val clip = spec.clip("slide") ?: return
        val idx = ((animClock / 0.17f).toInt() % clip.frames.size)
        bridge.currentFrame = clip.frames[idx]
        bridge.animScaleX = facingScale(facingDir)
        bridge.animScaleY = 0.96f + sin(time * 10f) * 0.02f
        bridge.animOffsetX = 0f
        bridge.animOffsetY = 0f
        bridge.animRotation = 0f

        if (t >= 1f) startWaddle()
    }

    private fun updateJump(dt: Float) {
        val params = bridge.getWindowParams() ?: return
        val t = (modeTimer / 0.55f).coerceIn(0f, 1f)
        val x = swimStartX + (swimTargetX - swimStartX) * t
        val y = jumpStartY + (jumpTargetY - jumpStartY) * sin((t * PI).toFloat())
        params.x = x.roundToInt()
        params.y = y.roundToInt()
        bridge.updateWindowLayout(params)

        val spec = spriteSheetSpec ?: return
        val clip = spec.clip("jump") ?: return
        val idx = ((animClock / 0.16f).toInt() % clip.frames.size)
        bridge.currentFrame = clip.frames[idx]
        bridge.animScaleX = facingScale(facingDir)
        bridge.animScaleY = 1f + sin(time * 8f) * 0.05f
        bridge.animRotation = facingDir * sin(time * 6f) * 3f

        if (t >= 1f) startWaddle()
    }

    private fun updateHappy(dt: Float) {
        val spec = spriteSheetSpec ?: return
        val clip = spec.clip("happy") ?: return
        val idx = ((animClock / 0.24f).toInt() % clip.frames.size)
        bridge.currentFrame = clip.frames[idx]
        bridge.animScaleX = facingScale(facingDir)
        bridge.animScaleY = 1f + sin(time * 6f) * 0.05f
        bridge.animOffsetY = sin(time * 4f) * 3f
        if (modeTimer >= modeDuration) startWaddle()
    }

    private fun updateTouch(dt: Float) {
        val spec = spriteSheetSpec ?: return
        val clip = spec.clip("touch") ?: return
        val idx = ((animClock / 0.26f).toInt() % clip.frames.size)
        bridge.currentFrame = clip.frames[idx]
        if (modeTimer >= modeDuration) {
            bridge.state = PetState.IDLE
            animClock = 0f
            startWaddle()
            reset()
        }
    }

    private fun updateSleep(dt: Float) {
        val spec = spriteSheetSpec ?: return
        val clip = spec.clip("sleep") ?: return
        bridge.currentFrame = clip.frames[0]
        bridge.animScaleX = facingScale(facingDir)
        bridge.animOffsetY = sin(time * 1.2f) * 1.5f
        if (modeTimer >= modeDuration) startWaddle()
    }

    override fun onInteract() {
        super.onInteract()
        mode = Mode.TOUCH
        modeDuration = 0.9f + random.nextFloat() * 0.6f
        modeTimer = 0f
        animClock = 0f
    }

    override fun updateInteracting(dt: Float) {
        time += dt
        modeTimer += dt
        animClock += dt
        if (mode == Mode.TOUCH) updateTouch(dt)
    }

    private fun syncWindowPosition() {
        val params = bridge.getWindowParams() ?: return
        val minX = 0
        val maxX = (bridge.screenWidth - bridge.petSpriteSize).coerceAtLeast(0)
        val minY = 50
        val maxY = (bridge.screenHeight - bridge.petSpriteSize - 100).coerceAtLeast(minY)
        params.x = params.x.coerceIn(minX, maxX)
        params.y = params.y.coerceIn(minY, maxY)
        bridge.updateWindowLayout(params)
    }
}
