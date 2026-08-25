package com.pixelpals.app.feature.overlay.behavior

import com.pixelpals.app.core.domain.PetState
import com.pixelpals.app.R
import com.pixelpals.app.core.motion.PetRandom
import kotlin.math.abs
import kotlin.math.sin

/** Grounded dog behavior: walking, sniffing, digging, play bows and zoomies. */
class CorgiBehavior(
    bridge: PetViewBridge,
    override val random: PetRandom,
) : BaseBehavior(bridge, random) {
    override val resourceIds = listOf(
        R.drawable.corgi_0,
        R.drawable.corgi_1,
        R.drawable.corgi_2,
        R.drawable.corgi_3,
        R.drawable.corgi_4,
        R.drawable.corgi_5,
        R.drawable.corgi_6,
        R.drawable.corgi_7,
        R.drawable.corgi_8,
        R.drawable.corgi_9,
        R.drawable.corgi_10,
        R.drawable.corgi_11,
        R.drawable.corgi_12,
        R.drawable.corgi_13,
    )

    private enum class Mode {
        WALK,
        ALERT,
        SNIFF,
        DIG,
        PLAY_BOW,
        REST,
    }

    private var mode = Mode.WALK
    private var modeTimer = 0f
    private var modeDuration = 4.5f
    private var walkDirection = 1f
    private var currentSpeed = 0f
    private var gaitDistance = 0f
    private var hasFoundBone = false
    private var isZooming = false

    init {
        loadFramesAsync()
    }

    override fun updateIdle(dt: Float) {
        if (isLoading || frames.isEmpty()) return
        time += dt
        modeTimer += dt
        when (mode) {
            Mode.WALK -> updateWalk(dt)
            Mode.ALERT -> updateAlert()
            Mode.SNIFF -> updateSniff()
            Mode.DIG -> updateDig()
            Mode.PLAY_BOW -> updatePlayBow()
            Mode.REST -> updateRest()
        }
    }

    private fun updateWalk(dt: Float) {
        val params = bridge.getWindowParams() ?: return
        val maxX = maxWindowX()
        val targetSpeed = bridge.petSpriteSize * 0.58f * moodSpeedMultiplier()
        currentSpeed = approach(currentSpeed, targetSpeed, bridge.petSpriteSize * 1.9f * dt)
        val previousX = params.x
        val proposedX = params.x + (walkDirection * currentSpeed * dt).toInt()
        val shouldReverse = CorgiEdgeMotion.shouldReverse(
            positionX = params.x,
            proposedX = proposedX,
            maxX = maxX,
            direction = walkDirection,
        )
        params.x = proposedX.coerceIn(0, maxX)
        if (shouldReverse) {
            walkDirection *= -1f
            currentSpeed *= 0.72f
        }
        params.y = groundY()
        bridge.updateWindowLayout(params)

        gaitDistance += abs(params.x - previousX)
        bridge.currentFrame = WALK_FRAME_START +
            ((gaitDistance / (bridge.petSpriteSize * WALK_FRAME_TRAVEL_RATIO)).toInt() % WALK_FRAME_COUNT)
        bridge.animScaleX = if (walkDirection >= 0f) 1f else -1f
        bridge.animScaleY = 1f
        bridge.animOffsetX = 0f
        bridge.animOffsetY = 0f
        bridge.animRotation = 0f

        if (modeTimer >= modeDuration) chooseDogAction()
    }

    private fun updateAlert() {
        stopOnGround()
        bridge.currentFrame = FRAME_ALERT
        bridge.animScaleX = if (walkDirection >= 0f) 1f else -1f
        bridge.animScaleY = 1f + sin(modeTimer * 7f) * 0.012f
        bridge.animRotation = sin(modeTimer * 5f) * 1.5f
        if (modeTimer >= modeDuration) startWalk()
    }

    private fun updateSniff() {
        stopOnGround()
        bridge.currentFrame = FRAME_SNIFF
        bridge.animScaleX = if (walkDirection >= 0f) 1f else -1f
        bridge.animScaleY = 1f
        bridge.animOffsetY = abs(sin(modeTimer * 8f)) * 1.5f
        bridge.animRotation = walkDirection * sin(modeTimer * 7f) * 2f
        if (modeTimer >= modeDuration) {
            if (random.nextFloat() < 0.58f) changeMode(Mode.DIG, 2.7f) else startWalk()
        }
    }

    private fun updateDig() {
        stopOnGround()
        if (modeTimer < 1.65f) {
            bridge.currentFrame = FRAME_DIG
            bridge.animOffsetX = walkDirection * sin(modeTimer * 22f) * 3f
        } else {
            bridge.currentFrame = FRAME_BONE
            bridge.animOffsetX = 0f
            if (!hasFoundBone) {
                hasFoundBone = true
                bridge.showBubble("🦴!")
            }
        }
        bridge.animScaleX = if (walkDirection >= 0f) 1f else -1f
        bridge.animScaleY = 1f
        bridge.animRotation = 0f
        if (modeTimer >= modeDuration) changeMode(Mode.REST, 1.4f)
    }

    private fun updatePlayBow() {
        stopOnGround()
        bridge.currentFrame = FRAME_PLAY_BOW
        bridge.animScaleX = if (walkDirection >= 0f) 1f else -1f
        bridge.animScaleY = 1f
        bridge.animOffsetY = -abs(sin(modeTimer * 8f)) * 2f
        bridge.animRotation = 0f
        if (modeTimer >= modeDuration) startWalk(initialSpeedRatio = 0.82f)
    }

    private fun updateRest() {
        stopOnGround()
        bridge.currentFrame = if ((modeTimer / 0.72f).toInt() % 2 == 0) FRAME_REST else FRAME_REST_BLINK
        bridge.animScaleX = if (walkDirection >= 0f) 1f else -1f
        bridge.animScaleY = 1f + sin(time * 1.8f) * 0.015f
        bridge.animOffsetY = 0f
        bridge.animRotation = 0f
        if (modeTimer >= modeDuration) startWalk()
    }

    private fun chooseDogAction() {
        when (random.nextInt(0, 4)) {
            0 -> changeMode(Mode.ALERT, 0.8f)
            1 -> changeMode(Mode.SNIFF, 1.55f)
            2 -> changeMode(Mode.PLAY_BOW, 1.05f)
            else -> changeMode(Mode.REST, 2.2f)
        }
    }

    private fun startWalk(initialSpeedRatio: Float = 0f) {
        changeMode(Mode.WALK, 3.8f + random.nextFloat() * 2.2f)
        currentSpeed = bridge.petSpriteSize * 0.58f * initialSpeedRatio
        gaitDistance = 0f
        clearTransforms()
    }

    private fun changeMode(nextMode: Mode, duration: Float) {
        mode = nextMode
        modeTimer = 0f
        modeDuration = duration
        hasFoundBone = false
        bridge.animOffsetX = 0f
        bridge.animOffsetY = 0f
    }

    private fun stopOnGround() {
        currentSpeed = 0f
        val params = bridge.getWindowParams() ?: return
        params.x = params.x.coerceIn(0, maxWindowX())
        params.y = groundY()
        bridge.updateWindowLayout(params)
    }

    private fun groundY(): Int = bridge.groundY.coerceAtLeast(50)

    private fun maxWindowX(): Int = (bridge.screenWidth - bridge.petSpriteSize).coerceAtLeast(0)

    private fun clearTransforms() {
        bridge.animScaleX = if (walkDirection >= 0f) 1f else -1f
        bridge.animScaleY = 1f
        bridge.animRotation = 0f
        bridge.animOffsetX = 0f
        bridge.animOffsetY = 0f
    }

    override fun onInteract() {
        super.onInteract()
        isZooming = false
        interactionTimer = 0f
        currentSpeed = 0f
        bridge.showBubble("✨🐾")
        bridge.playHaptic(30)
    }

    override fun updateInteracting(dt: Float) {
        if (isZooming) {
            updateZoomie(dt)
            return
        }
        interactionTimer += dt
        stopOnGround()
        bridge.currentFrame = FRAME_BELLY_RUB
        bridge.animScaleX = if (walkDirection >= 0f) 1f else -1f
        bridge.animScaleY = 1f + sin(interactionTimer * 9f) * 0.025f
        bridge.animRotation = sin(interactionTimer * 8f) * 7f
        bridge.animOffsetY = -abs(sin(interactionTimer * 8f)) * 2f
        if (interactionTimer >= 1.35f) {
            bridge.state = PetState.IDLE
            startWalk(initialSpeedRatio = 0.35f)
        }
    }

    private fun updateZoomie(dt: Float) {
        interactionTimer += dt
        val params = bridge.getWindowParams() ?: return
        val previousX = params.x
        val proposedX = params.x + (walkDirection * currentSpeed * dt).toInt()
        params.x = proposedX.coerceIn(0, maxWindowX())
        params.y = groundY()
        bridge.updateWindowLayout(params)
        gaitDistance += abs(params.x - previousX)
        currentSpeed = approach(currentSpeed, bridge.petSpriteSize * 0.72f, bridge.petSpriteSize * 1.25f * dt)

        bridge.currentFrame = if (interactionTimer < 0.16f) {
            FRAME_ZOOM_START
        } else {
            WALK_FRAME_START +
                ((gaitDistance / (bridge.petSpriteSize * ZOOM_FRAME_TRAVEL_RATIO)).toInt() % WALK_FRAME_COUNT)
        }
        bridge.animScaleX = if (walkDirection >= 0f) 1f else -1f
        bridge.animScaleY = 1f
        bridge.animOffsetY = 0f
        bridge.animRotation = 0f

        if (proposedX != params.x || interactionTimer >= 0.9f) {
            isZooming = false
            bridge.state = PetState.IDLE
            changeMode(Mode.SNIFF, 1.1f)
        }
    }

    override fun onFling(velocityX: Float, velocityY: Float) {
        super.onInteract()
        isZooming = true
        interactionTimer = 0f
        walkDirection = if (velocityX >= 0f) 1f else -1f
        currentSpeed = (abs(velocityX) * 0.3f)
            .coerceIn(bridge.petSpriteSize * 0.9f, bridge.petSpriteSize * 1.8f)
        gaitDistance = 0f
        bridge.showBubble("💨🐾")
    }

    override fun updateDrag(dt: Float) {
        super.updateDrag(dt)
        bridge.currentFrame = FRAME_DRAG
        bridge.animScaleX = if (walkDirection >= 0f) 1f else -1f
    }

    override fun reset() {
        isZooming = false
        super.reset()
        startWalk()
    }

    private fun approach(value: Float, target: Float, delta: Float): Float {
        return when {
            value < target -> (value + delta).coerceAtMost(target)
            value > target -> (value - delta).coerceAtLeast(target)
            else -> target
        }
    }

    private companion object {
        const val FRAME_DRAG = 0
        const val FRAME_ALERT = 1
        const val FRAME_PLAY_BOW = 2
        const val FRAME_SNIFF = 3
        const val FRAME_DIG = 4
        const val FRAME_BONE = 5
        const val FRAME_REST = 6
        const val FRAME_REST_BLINK = 7
        const val FRAME_BELLY_RUB = 8
        const val FRAME_ZOOM_START = 9
        const val WALK_FRAME_START = 10
        const val WALK_FRAME_COUNT = 4
        const val WALK_FRAME_TRAVEL_RATIO = 0.11f
        const val ZOOM_FRAME_TRAVEL_RATIO = 0.085f
    }
}
