package com.pixelpals.app.feature.overlay.behavior

import com.pixelpals.app.core.domain.PetState
import com.pixelpals.app.core.motion.PetRandom
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin

/** Upright angelic flight driven by wing beats, glide damping, prayer and recovery. */
class AngelBehavior(
    bridge: PetViewBridge,
    override val random: PetRandom,
) : BaseBehavior(bridge, random) {
    override val resourceIds: List<Int> = emptyList()

    private enum class Mode {
        HOVER,
        CRUISE,
        GLIDE,
        GRACE,
        PRAYER,
        RECOVER,
        TOUCH,
    }

    private var mode = Mode.HOVER
    private var modeTimer = 0f
    private var modeDuration = 2.4f
    private var positionX = 0f
    private var positionY = 0f
    private var velocityX = 0f
    private var velocityY = 0f
    private var flightTargetX = 0f
    private var flightTargetY = 0f
    private var facingRight = true
    private var positionInitialized = false
    private var flapClock = 0f

    init {
        loadSpriteSheetAssetAsync(ATLAS_SPEC_PATH)
    }

    override fun updateIdle(dt: Float) {
        if (isLoading || spriteSheetBitmap == null || spriteFrameRects.isEmpty()) return
        initializePosition()
        val step = dt.coerceIn(0f, 1f / 30f)
        time += step
        modeTimer += step
        flapClock += step

        when (mode) {
            Mode.HOVER -> updateHover(step)
            Mode.CRUISE -> updateCruise(step)
            Mode.GLIDE -> updateGlide(step)
            Mode.GRACE -> updateGrace(step)
            Mode.PRAYER -> updatePrayer(step)
            Mode.RECOVER -> updateRecover(step)
            Mode.TOUCH -> updateTouch(step)
        }
        syncWindowPosition()
    }

    private fun updateHover(dt: Float) {
        flightTargetY = flightTargetY.coerceIn(preferredTop(), preferredBottom())
        steer(dt, accelerationRatio = 0.34f, damping = 3.6f, maxSpeedRatio = 0.22f)
        bridge.currentFrame = FRAME_HOVER_START + ((time / HOVER_FRAME_SECONDS).toInt() % 4)
        applyUprightAttitude(maxTilt = 3.5f)
        bridge.animOffsetY = sin(time * 1.7f) * 4f
        if (modeTimer >= modeDuration) startCruise()
    }

    private fun updateCruise(dt: Float) {
        if (flapClock >= FLAP_INTERVAL_SECONDS) {
            flapClock %= FLAP_INTERVAL_SECONDS
            if (positionY > flightTargetY - bridge.petSpriteSize * 0.08f) {
                velocityY -= bridge.petSpriteSize * 0.12f
            }
        }
        steer(dt, accelerationRatio = 0.78f, damping = 1.55f, maxSpeedRatio = 0.52f)
        bridge.currentFrame = FRAME_FLIGHT_START + ((time / FLIGHT_FRAME_SECONDS).toInt() % 2)
        applyUprightAttitude(maxTilt = 8f)
        bridge.animOffsetY = sin(time * 4.5f) * 1.5f
        if (distanceToTarget() <= bridge.petSpriteSize * 0.34f || modeTimer >= modeDuration) {
            changeMode(Mode.GLIDE, 1.0f + random.nextFloat() * 0.8f)
        }
    }

    private fun updateGlide(dt: Float) {
        velocityY += bridge.petSpriteSize * 0.035f * dt
        steer(dt, accelerationRatio = 0.24f, damping = 1.15f, maxSpeedRatio = 0.40f)
        bridge.currentFrame = FRAME_GLIDE_START + ((time / GLIDE_FRAME_SECONDS).toInt() % 2)
        applyUprightAttitude(maxTilt = 6f)
        bridge.animOffsetY = sin(time * 2.2f) * 2f
        if (modeTimer >= modeDuration) {
            when (random.nextInt(0, 4)) {
                0 -> changeMode(Mode.GRACE, 1.05f)
                1 -> changeMode(Mode.PRAYER, 2.1f + random.nextFloat() * 1.6f)
                else -> startHover()
            }
        }
    }

    private fun updateGrace(dt: Float) {
        dampInPlace(dt, 4.2f)
        val frame = (modeTimer / GRACE_FRAME_SECONDS).toInt().coerceIn(0, 3)
        bridge.currentFrame = FRAME_GRACE_START + frame
        applyUprightAttitude(maxTilt = 2f)
        bridge.animScaleY = 1f + sin(modeTimer * 5f) * 0.025f
        if (modeTimer >= modeDuration) startHover()
    }

    private fun updatePrayer(dt: Float) {
        dampInPlace(dt, 4.8f)
        bridge.currentFrame = FRAME_PRAYER_START + ((modeTimer / PRAYER_FRAME_SECONDS).toInt() % 2)
        applyUprightAttitude(maxTilt = 1.5f)
        bridge.animOffsetY = sin(time * 1.25f) * 3f
        if (modeTimer >= modeDuration) startHover()
    }

    private fun updateRecover(dt: Float) {
        steer(dt, accelerationRatio = 1.18f, damping = 2.5f, maxSpeedRatio = 0.68f)
        bridge.currentFrame = if (modeTimer < 0.22f) FRAME_FLING_TUCK else FRAME_RECOVER
        applyUprightAttitude(maxTilt = 9f)
        bridge.animOffsetY = 0f
        if (modeTimer >= 0.72f && distanceToTarget() <= bridge.petSpriteSize * 0.42f) {
            startHover()
        } else if (modeTimer >= modeDuration) {
            startHover()
        }
    }

    private fun updateTouch(dt: Float) {
        dampInPlace(dt, 5.2f)
        bridge.currentFrame = FRAME_TOUCH
        applyUprightAttitude(maxTilt = 2f)
        bridge.animScaleY = 1f + sin(modeTimer * 8f) * 0.035f
        bridge.animOffsetY = -abs(sin(modeTimer * 8f)) * 3f
    }

    private fun steer(dt: Float, accelerationRatio: Float, damping: Float, maxSpeedRatio: Float) {
        val dx = flightTargetX - positionX
        val dy = flightTargetY - positionY
        val distance = hypot(dx, dy).coerceAtLeast(1f)
        val acceleration = bridge.petSpriteSize * accelerationRatio
        velocityX += dx / distance * acceleration * dt
        velocityY += dy / distance * acceleration * dt
        val dampingFactor = (1f - damping * dt).coerceIn(0f, 1f)
        velocityX *= dampingFactor
        velocityY *= dampingFactor

        val maxSpeed = bridge.petSpriteSize * maxSpeedRatio * moodSpeedMultiplier()
        val speed = hypot(velocityX, velocityY)
        if (speed > maxSpeed) {
            velocityX = velocityX / speed * maxSpeed
            velocityY = velocityY / speed * maxSpeed
        }
        integrate(dt)
    }

    private fun dampInPlace(dt: Float, damping: Float) {
        val factor = (1f - damping * dt).coerceIn(0f, 1f)
        velocityX *= factor
        velocityY *= factor
        integrate(dt)
    }

    private fun integrate(dt: Float) {
        positionX += velocityX * dt
        positionY += velocityY * dt

        val maxX = maxWindowX().toFloat()
        val minY = hardTop()
        val maxY = hardBottom()
        val softMargin = bridge.petSpriteSize * 0.35f
        val spring = bridge.petSpriteSize * 1.1f * dt

        if (positionX < softMargin) velocityX += spring
        if (positionX > maxX - softMargin) velocityX -= spring
        if (positionY < minY + softMargin) velocityY += spring
        if (positionY > maxY - softMargin) velocityY -= spring

        if (positionX < 0f || positionX > maxX) {
            positionX = positionX.coerceIn(0f, maxX)
            velocityX *= -0.24f
        }
        if (positionY < minY || positionY > maxY) {
            positionY = positionY.coerceIn(minY, maxY)
            velocityY *= -0.20f
        }
    }

    private fun startCruise() {
        chooseTarget()
        changeMode(Mode.CRUISE, 2.6f + random.nextFloat() * 2.0f)
    }

    private fun startHover() {
        flightTargetX = positionX
        flightTargetY = positionY.coerceIn(preferredTop(), preferredBottom())
        changeMode(Mode.HOVER, 1.8f + random.nextFloat() * 1.8f)
        bridge.animScaleY = 1f
        bridge.animOffsetY = 0f
    }

    private fun startRecovery() {
        flightTargetX = maxWindowX() * 0.5f
        flightTargetY = (preferredTop() + preferredBottom()) * 0.5f
        changeMode(Mode.RECOVER, 2.2f)
    }

    private fun chooseTarget() {
        val maxX = maxWindowX()
        val margin = (bridge.petSpriteSize * 0.25f).toInt()
        val minTargetX = margin.coerceAtMost(maxX)
        val maxTargetX = (maxX - margin).coerceAtLeast(minTargetX)
        flightTargetX = if (minTargetX == maxTargetX) {
            minTargetX.toFloat()
        } else {
            random.nextInt(minTargetX, maxTargetX + 1).toFloat()
        }
        val minTargetY = preferredTop().toInt()
        val maxTargetY = preferredBottom().toInt().coerceAtLeast(minTargetY)
        flightTargetY = if (minTargetY == maxTargetY) {
            minTargetY.toFloat()
        } else {
            random.nextInt(minTargetY, maxTargetY + 1).toFloat()
        }
    }

    private fun applyUprightAttitude(maxTilt: Float) {
        if (velocityX > 2f) facingRight = true
        if (velocityX < -2f) facingRight = false
        val horizontalTilt = velocityX / (bridge.petSpriteSize * 0.52f) * maxTilt
        val verticalTilt = velocityY / (bridge.petSpriteSize * 0.52f) * 2f
        bridge.animRotation = (horizontalTilt + verticalTilt).coerceIn(-maxTilt, maxTilt)
        bridge.animScaleX = if (facingRight) 1f else -1f
        bridge.animScaleY = 1f
        bridge.animOffsetX = 0f
        bridge.animAlpha = 1f
    }

    private fun initializePosition() {
        if (positionInitialized) return
        val params = bridge.getWindowParams() ?: return
        positionX = params.x.toFloat().coerceIn(0f, maxWindowX().toFloat())
        positionY = params.y.toFloat().coerceIn(hardTop(), hardBottom())
        flightTargetX = positionX
        flightTargetY = positionY.coerceIn(preferredTop(), preferredBottom())
        positionInitialized = true
    }

    private fun syncWindowPosition() {
        val params = bridge.getWindowParams() ?: return
        params.x = positionX.toInt().coerceIn(0, maxWindowX())
        params.y = positionY.toInt().coerceIn(hardTop().toInt(), hardBottom().toInt())
        bridge.updateWindowLayout(params)
    }

    private fun distanceToTarget(): Float = hypot(flightTargetX - positionX, flightTargetY - positionY)

    private fun maxWindowX(): Int {
        val width = bridge.getWindowParams()?.width ?: bridge.petSpriteSize
        return (bridge.screenWidth - width).coerceAtLeast(0)
    }

    private fun preferredTop(): Float = bridge.screenHeight * 0.10f
    private fun preferredBottom(): Float = bridge.screenHeight * 0.30f
    private fun hardTop(): Float = bridge.screenHeight * 0.06f
    private fun hardBottom(): Float = bridge.screenHeight * 0.42f

    private fun changeMode(nextMode: Mode, duration: Float) {
        mode = nextMode
        modeTimer = 0f
        modeDuration = duration
    }

    override fun onInteract() {
        super.onInteract()
        changeMode(Mode.TOUCH, TOUCH_SECONDS)
        bridge.showBubble("✨🤍")
        bridge.playHaptic(20)
    }

    override fun updateInteracting(dt: Float) {
        if (isLoading || spriteSheetBitmap == null || spriteFrameRects.isEmpty()) return
        initializePosition()
        val step = dt.coerceIn(0f, 1f / 30f)
        time += step
        modeTimer += step
        updateTouch(step)
        syncWindowPosition()
        if (modeTimer >= TOUCH_SECONDS) {
            bridge.state = PetState.IDLE
            changeMode(Mode.GRACE, 1.05f)
        }
    }

    override fun updateDrag(dt: Float) {
        val params = bridge.getWindowParams()
        if (params != null) {
            positionX = params.x.toFloat()
            positionY = params.y.toFloat()
            positionInitialized = true
        }
        velocityX = 0f
        velocityY = 0f
        bridge.currentFrame = FRAME_DRAG
        bridge.animRotation = 0f
        bridge.animScaleX = if (facingRight) 1f else -1f
        bridge.animScaleY = 1f
        bridge.animOffsetX = 0f
        bridge.animOffsetY = 0f
    }

    override fun onFling(velocityX: Float, velocityY: Float) {
        initializePosition()
        this.velocityX = (velocityX * 0.14f).coerceIn(-bridge.petSpriteSize * 0.9f, bridge.petSpriteSize * 0.9f)
        this.velocityY = (velocityY * 0.14f).coerceIn(-bridge.petSpriteSize * 0.75f, bridge.petSpriteSize * 0.75f)
        bridge.state = PetState.IDLE
        startRecovery()
    }

    override fun updateFalling(dt: Float) {
        updateIdle(dt)
    }

    override fun updateJumping(dt: Float) {
        updateIdle(dt)
    }

    override fun reset() {
        super.reset()
        initializePosition()
        startRecovery()
    }

    private companion object {
        const val ATLAS_SPEC_PATH = "pets/angel/angel_sheet_v4.json"
        const val FRAME_HOVER_START = 0
        const val FRAME_FLIGHT_START = 4
        const val FRAME_GLIDE_START = 6
        const val FRAME_GRACE_START = 8
        const val FRAME_PRAYER_START = 10
        const val FRAME_TOUCH = 12
        const val FRAME_DRAG = 13
        const val FRAME_FLING_TUCK = 14
        const val FRAME_RECOVER = 15
        const val HOVER_FRAME_SECONDS = 0.18f
        const val FLIGHT_FRAME_SECONDS = 0.145f
        const val GLIDE_FRAME_SECONDS = 0.24f
        const val GRACE_FRAME_SECONDS = 0.23f
        const val PRAYER_FRAME_SECONDS = 0.42f
        const val FLAP_INTERVAL_SECONDS = 0.48f
        const val TOUCH_SECONDS = 0.58f
    }
}
