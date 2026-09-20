package com.pixelpals.app.feature.overlay.behavior

import com.pixelpals.app.core.domain.PetState
import com.pixelpals.app.R
import com.pixelpals.app.core.motion.PetRandom
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import com.pixelpals.app.core.motion.GingerArtworkScale
import com.pixelpals.app.core.motion.GingerPostureMotion
import com.pixelpals.app.core.motion.GingerRestMotion
import com.pixelpals.app.core.motion.GingerTurnMotion

/** Grounded feline movement for the redesigned Ginger atlas. */
class GingerBehavior(
    bridge: PetViewBridge,
    override val random: PetRandom,
) : BaseBehavior(bridge, random) {
    override val isSleeping: Boolean get() = mode == Mode.SLEEP

    override val resourceIds: List<Int> = emptyList()

    private val hasPostureArtwork: Boolean get() = spriteFrameRects.size == POSTURE_FRAME_COUNT || spriteFrameRects.size == REST_FRAME_COUNT || spriteFrameRects.size == TURN_FRAME_COUNT
    private val hasRestArtwork: Boolean get() = spriteFrameRects.size == REST_FRAME_COUNT || spriteFrameRects.size == TURN_FRAME_COUNT
    private val hasTurnArtwork: Boolean get() = spriteFrameRects.size == TURN_FRAME_COUNT
    override fun getFrameCameraScale(index: Int): Float = if (hasPostureArtwork) GingerArtworkScale.frame(index) else 1f
    override val frameGround: Float get() = if (hasPostureArtwork) GingerArtworkScale.GROUND else .5f

    override fun canStartScheduledSleep(reducedMotion: Boolean): Boolean =
        mode == Mode.SIT || mode == Mode.SLEEP || mode == Mode.STANDING || (reducedMotion && mode in listOf(Mode.WALK, Mode.STALK))

    private enum class Mode {
        SIT,
        GROOM,
        SLEEP,
        WAKE,
        WALK,
        STALK,
        POUNCE_COIL,
        AIRBORNE,
        LAND,
        TOUCH,
        STAND_UP,
        SIT_DOWN,
        TURN,
        STANDING,
    }

    private var mode: Mode = Mode.SIT
    private var modeTimer: Float = 0f
    private var modeDuration: Float = 1.6f
    private var facingDirection: Float = -1f
    override val facingLeft: Boolean get() = bridge.animScaleX >= 0f
    private var moveStartX: Float = 0f
    private var moveTargetX: Float = 0f
    private var airX: Float = 0f
    private var airY: Float = 0f
    private var airVelocityX: Float = 0f
    private var airVelocityY: Float = 0f
    private var postureTimer: Float = 0f
    private var postureInitialFacing: Float = -1f
    private var postureCompletion: (() -> Unit)? = null
    private var turnTimer: Float = 0f
    private var turnTargetFacing: Float = -1f
    private var turnCompletion: (() -> Unit)? = null
    private var reducedMotionEnabled: Boolean = false
    private var restStartsSeated: Boolean = false
    private var wakeFromFrame: Int = FRAME_SLEEP
    private var pendingTouchAfterWake: Boolean = false

    init {
        val context = (bridge as? android.view.View)?.context
        val restAtlasAvailable: Boolean = try {
            context?.assets?.open(REST_ATLAS_SPEC_PATH)?.use { true } ?: false
        } catch (_: Exception) {
            false
        }
        val turnAtlasAvailable: Boolean = try {
            context?.assets?.open(TURN_ATLAS_SPEC_PATH)?.use { true } ?: false
        } catch (_: Exception) { false }
        val motionAtlasAvailable: Boolean = try {
            context?.assets?.open(MOTION_ATLAS_SPEC_PATH)?.use { true } ?: false
        } catch (_: Exception) { false }
        loadSpriteSheetAssetAsync(when { turnAtlasAvailable -> TURN_ATLAS_SPEC_PATH; restAtlasAvailable -> REST_ATLAS_SPEC_PATH; motionAtlasAvailable -> MOTION_ATLAS_SPEC_PATH; else -> ATLAS_SPEC_PATH })
    }

    override val isSeatedForScheduledRest: Boolean get() = hasPostureArtwork &&
        (mode == Mode.SIT || (mode == Mode.SLEEP && (!hasRestArtwork || restStartsSeated)))
    override val scheduledRestFrame: Int? get() = if (hasRestArtwork && isSleeping) bridge.currentFrame else null

    override fun onScheduledSleepInterrupted(frame: Int) {
        if (!hasRestArtwork || frame !in setOf(0, 16, 17, 18, 19, 20, 21)) return
        pendingTouchAfterWake = false
        if (frame == 17) {
            startWake(frame)
            bridge.currentFrame = frame
            return
        }
        restStartsSeated = frame == FRAME_SIT || frame == 16
        bridge.currentFrame = frame
        clearTransforms()
        mode = Mode.SLEEP
        modeTimer = (if (restStartsSeated) GingerRestMotion.seatedRestFrames else GingerRestMotion.restFrames)
            .indexOf(frame).coerceAtLeast(0) * GingerRestMotion.STEP_SECONDS
        modeDuration = 3600f
    }

    override fun onScheduledWakeCompleted() {
        if (!hasPostureArtwork) return
        pendingTouchAfterWake = false
        cancelPosture()
        changeMode(Mode.STANDING, .35f)
        clearTransforms()
        placeOnGround()
        bridge.currentFrame = 18
    }

    override fun advanceScheduledRestTransition(delta: Float, reducedMotion: Boolean) {
        reducedMotionEnabled = reducedMotion
        if (reducedMotion && (mode == Mode.STAND_UP || mode == Mode.SIT_DOWN)) {
            postureTimer = GingerPostureMotion.DURATION_SECONDS
            applyPostureFrame()
        }
        if (reducedMotion && mode == Mode.TURN) {
            facingDirection = turnTargetFacing
            turnCompletion = null
            turnTimer = GingerTurnMotion.DURATION_SECONDS
            mode = Mode.STANDING
            modeTimer = 0f
            modeDuration = .35f
            clearTransforms()
            bridge.currentFrame = FRAME_STAND
        }
        if (reducedMotion && mode == Mode.WAKE) startSit()
    }

    override fun updateIdle(dt: Float) {
        if (isLoading || spriteSheetBitmap == null || spriteFrameRects.isEmpty()) return
        time += dt
        modeTimer += dt

        when (mode) {
            Mode.SIT -> updateSit()
            Mode.GROOM -> updateGroom()
            Mode.SLEEP -> updateSleep()
            Mode.WAKE -> updateWake()
            Mode.WALK -> updateWalk()
            Mode.STALK -> updateStalk()
            Mode.POUNCE_COIL -> updatePounceCoil()
            Mode.AIRBORNE -> updateAirborne(dt)
            Mode.LAND -> updateLand()
            Mode.TOUCH -> updateTouch()
            Mode.STAND_UP, Mode.SIT_DOWN -> {
                postureTimer += dt
                applyPostureFrame()
            }
            Mode.TURN -> {
                turnTimer += dt
                applyTurnFrame()
            }
            Mode.STANDING -> {
                placeOnGround()
                clearTransforms()
                bridge.currentFrame = 18
                if (modeTimer >= modeDuration) startWalk()
            }
        }
    }

    private fun updateSit() {
        placeOnGround()
        bridge.currentFrame = FRAME_SIT
        applyFacing()
        bridge.animScaleY = 1f
        bridge.animOffsetY = 0f
        bridge.animRotation = 0f
        if (modeTimer >= modeDuration) chooseGroundAction()
    }

    private fun updateGroom() {
        placeOnGround()
        bridge.currentFrame = if ((modeTimer / 0.34f).toInt() % 2 == 0) FRAME_SIT else FRAME_GROOM
        applyFacing()
        bridge.animOffsetY = 0f
        bridge.animRotation = sin(modeTimer * 5f) * 1.5f
        if (modeTimer >= modeDuration) startSit()
    }

    private fun updateSleep() {
        placeOnGround()
        if (hasRestArtwork) bridge.currentFrame = GingerRestMotion.restFrame(modeTimer, restStartsSeated)
        else bridge.currentFrame = FRAME_SLEEP
        applyFacing()
        bridge.animScaleY = 1f
        bridge.animOffsetY = 0f
        bridge.animRotation = 0f
        if (modeTimer >= modeDuration) startWake(bridge.currentFrame)
    }

    private fun updateWake() {
        placeOnGround()
        bridge.currentFrame = if (hasRestArtwork) GingerRestMotion.wakeFrame(modeTimer, wakeFromFrame) else when {
            modeTimer < 0.24f -> FRAME_SLEEP
            modeTimer < 0.66f -> FRAME_STRETCH
            else -> FRAME_SIT
        }
        applyFacing()
        bridge.animScaleY = 1f
        bridge.animOffsetY = 0f
        bridge.animRotation = 0f
        if (modeTimer >= modeDuration) {
            if (!hasRestArtwork) {
                startWalk()
                return
            }
            mode = Mode.STANDING
            modeTimer = 0f
            bridge.currentFrame = 18
            if (pendingTouchAfterWake) {
                pendingTouchAfterWake = false
                startSitDownForTouch()
            } else startWalk()
        }
    }

    private fun startWake(frame: Int): Unit {
        wakeFromFrame = frame
        changeMode(Mode.WAKE, if (hasRestArtwork) GingerRestMotion.wakeDuration(frame) else .84f)
    }

    private fun startSitDownForTouch(): Unit {
        startSitDown(TOUCH_SECONDS)
        postureCompletion = {
            changeMode(Mode.TOUCH, TOUCH_SECONDS)
            bridge.showBubble(localizedString(R.string.bubble_ginger_purr, "prrr"))
            bridge.playHaptic(24)
        }
    }

    private fun updateWalk() {
        val params = bridge.getWindowParams() ?: return
        val progress: Float = (modeTimer / modeDuration).coerceIn(0f, 1f)
        val eased: Float = progress * progress * (3f - 2f * progress)
        params.x = (moveStartX + (moveTargetX - moveStartX) * eased).roundToInt()
            .coerceIn(0, maxWindowX())
        params.y = groundY().roundToInt()
        bridge.updateWindowLayout(params)

        val gait: Float = abs(params.x - moveStartX) / (bridge.petSpriteSize * .22f)
        val moving: Float = 4f * progress * (1f - progress)
        bridge.currentFrame = FRAME_WALK_START + (gait.toInt() % 4)
        applyFacing()
        bridge.animScaleY = 1f
        bridge.animOffsetY = -abs(sin(gait * PI.toFloat())) * 1.8f * moving
        bridge.animRotation = facingDirection * sin(gait * PI.toFloat()) * 1.2f * moving
        if (progress >= 1f) enterSit(0.8f + random.nextFloat() * 0.8f)
    }

    private fun updateStalk() {
        val params = bridge.getWindowParams() ?: return
        val progress: Float = (modeTimer / modeDuration).coerceIn(0f, 1f)
        val eased = com.pixelpals.app.core.motion.GroundGait.progress(modeTimer, modeDuration)
        params.x = (moveStartX + (moveTargetX - moveStartX) * eased).roundToInt()
            .coerceIn(0, maxWindowX())
        params.y = groundY().roundToInt()
        bridge.updateWindowLayout(params)

        val travelled = abs(params.x - moveStartX)
        bridge.currentFrame = FRAME_STALK_START + (travelled / (bridge.petSpriteSize * .18f)).toInt() % 3
        applyFacing()
        bridge.animScaleY = 1f
        bridge.animOffsetY = 0f
        bridge.animRotation = 0f
        if (progress >= 1f) {
            changeMode(Mode.POUNCE_COIL, POUNCE_COIL_SECONDS)
            updatePounceCoil()
        }
    }

    private fun updatePounceCoil() {
        placeOnGround()
        bridge.currentFrame = FRAME_POUNCE_COIL
        applyFacing()
        // The crouch is drawn into the pose; scaling it also shrinks the head and paws.
        bridge.animScaleY = 1f
        bridge.animOffsetY = 0f
        bridge.animRotation = 0f
        if (modeTimer >= modeDuration) {
            startAirborne(
                velocityX = facingDirection * bridge.petSpriteSize * 1.9f,
                velocityY = -bridge.petSpriteSize * 2.45f,
            )
        }
    }

    private fun updateAirborne(dt: Float) {
        val params = bridge.getWindowParams() ?: return
        val step: Float = dt.coerceIn(0f, 1f / 30f)
        airVelocityY += bridge.petSpriteSize * 5.8f * step
        airX += airVelocityX * step
        airY += airVelocityY * step

        val maxX: Float = maxWindowX().toFloat()
        if (airX < 0f || airX > maxX) {
            airX = airX.coerceIn(0f, maxX)
            airVelocityX *= -0.32f
            facingDirection = if (airVelocityX >= 0f) 1f else -1f
        }
        if (airY < topLimitPx()) {
            airY = topLimitPx()
            airVelocityY = abs(airVelocityY) * 0.2f
        }

        val floor: Float = groundY()
        if (airY >= floor && airVelocityY >= 0f) {
            airY = floor
            params.x = airX.roundToInt()
            params.y = floor.roundToInt()
            bridge.updateWindowLayout(params)
            changeMode(Mode.LAND, LAND_SECONDS)
            // The first draw on contact already needs the planted impact pose;
            // otherwise the airborne pose and rotation linger on the floor.
            updateLand()
            return
        }

        params.x = airX.roundToInt()
        params.y = airY.roundToInt()
        bridge.updateWindowLayout(params)
        bridge.currentFrame = FRAME_POUNCE_AIR
        applyFacing()
        bridge.animScaleY = 1f
        bridge.animOffsetY = 0f
        bridge.animRotation = (airVelocityY / bridge.petSpriteSize * 4f).coerceIn(-8f, 10f) * facingDirection
    }

    private fun updateLand() {
        placeOnGround()
        bridge.currentFrame = if (modeTimer < LAND_SECONDS * 0.48f) FRAME_LAND_IMPACT else FRAME_LAND_RECOVER
        applyFacing()
        // Impact and recovery frames carry the weight without stretching the whole animal.
        bridge.animScaleY = 1f
        bridge.animOffsetY = 0f
        bridge.animRotation = 0f
        if (modeTimer >= modeDuration) enterSit(1.0f)
    }

    private fun updateTouch() {
        bridge.currentFrame = FRAME_TOUCH
        applyFacing()
        bridge.animScaleY = 1f
        bridge.animOffsetY = -abs(sin(modeTimer * 9f)) * 2f
        bridge.animRotation = sin(modeTimer * 8f) * 2f
    }

    private fun chooseGroundAction() {
        when (val roll: Float = random.nextFloat()) {
            in 0f..<0.48f -> startWalk()
            in 0.48f..<0.68f -> changeMode(Mode.GROOM, 1.35f)
            in 0.68f..<0.84f -> {
                restStartsSeated = mode == Mode.SIT || mode == Mode.SLEEP
                changeMode(Mode.SLEEP, 3.8f + random.nextFloat() * 2.8f)
            }
            else -> startStalk()
        }
    }

    private fun startWalk() {
        val params = bridge.getWindowParams() ?: return
        val previousFacing: Float = facingDirection
        val maxX: Float = maxWindowX().toFloat()
        moveStartX = params.x.toFloat().coerceIn(0f, maxX)
        val preferredDirection: Float = if (moveStartX < maxX * 0.5f) 1f else -1f
        var targetFacing: Float = if (random.nextFloat() < 0.78f) preferredDirection else -preferredDirection
        val distance: Float = bridge.petSpriteSize * (1.1f + random.nextFloat() * 1.9f)
        moveTargetX = (moveStartX + targetFacing * distance).coerceIn(0f, maxX)
        if (abs(moveTargetX - moveStartX) < bridge.petSpriteSize * 0.45f) {
            targetFacing *= -1f
            moveTargetX = (moveStartX + targetFacing * distance).coerceIn(0f, maxX)
        }
        val duration: Float = (abs(moveTargetX - moveStartX) / (bridge.petSpriteSize * 0.56f))
            .coerceIn(1.4f, 4.8f) / moodSpeedMultiplier()
        beginHeadingChange(previousFacing, targetFacing) { activateWalk(duration) }
    }

    private fun activateWalk(duration: Float): Unit = changeMode(Mode.WALK, duration)

    private fun startStalk() {
        val params = bridge.getWindowParams() ?: return
        val previousFacing: Float = facingDirection
        val maxX: Float = maxWindowX().toFloat()
        moveStartX = params.x.toFloat().coerceIn(0f, maxX)
        val targetFacing: Float = if (moveStartX < maxX * 0.5f) 1f else -1f
        val distance: Float = bridge.petSpriteSize * (0.8f + random.nextFloat() * 0.75f)
        moveTargetX = (moveStartX + targetFacing * distance).coerceIn(0f, maxX)
        val duration = com.pixelpals.app.core.motion.GroundGait.duration(
            moveTargetX - moveStartX, bridge.petSpriteSize * .45f, 1.4f,
        )
        beginHeadingChange(previousFacing, targetFacing) { activateStalk(duration) }
    }

    private fun activateStalk(duration: Float): Unit = changeMode(Mode.STALK, duration)

    private fun beginHeadingChange(previousFacing: Float, targetFacing: Float, completion: () -> Unit) {
        if (hasPostureArtwork && mode != Mode.STAND_UP && mode != Mode.STANDING) {
            startStandUp(previousFacing) {
                if (previousFacing == targetFacing) completion() else startTurn(targetFacing, completion)
            }
        } else if (previousFacing == targetFacing) {
            facingDirection = targetFacing
            completion()
        } else if (hasPostureArtwork) {
            startTurn(targetFacing, completion)
        } else {
            facingDirection = targetFacing
            completion()
        }
    }

    private fun startTurn(targetFacing: Float, completion: () -> Unit) {
        if (reducedMotionEnabled) {
            facingDirection = targetFacing
            turnCompletion = null
            turnTimer = GingerTurnMotion.DURATION_SECONDS
            mode = Mode.STANDING
            modeTimer = 0f
            modeDuration = .35f
            clearTransforms()
            bridge.currentFrame = FRAME_STAND
            completion()
            return
        }
        turnTargetFacing = targetFacing
        postureInitialFacing = facingDirection
        turnTimer = 0f
        turnCompletion = completion
        mode = Mode.TURN
        modeTimer = 0f
        modeDuration = GingerTurnMotion.DURATION_SECONDS
        placeOnGround()
        applyTurnFrame()
    }

    private fun applyTurnFrame() {
        val pose = GingerTurnMotion.poseAt(turnTimer)
        val frame = if (hasTurnArtwork) pose.frame else FRAME_STAND
        val effectiveFacing = if (pose.mirrored) -postureInitialFacing else postureInitialFacing
        bridge.currentFrame = frame
        bridge.animScaleX = facingScaleFor(effectiveFacing)
        bridge.animScaleY = 1f
        bridge.animOffsetX = 0f
        bridge.animOffsetY = 0f
        bridge.animRotation = 0f
        if (!pose.finished) return
        facingDirection = turnTargetFacing
        val completion = turnCompletion
        turnCompletion = null
        turnTimer = 0f
        mode = Mode.STANDING
        modeTimer = 0f
        modeDuration = .35f
        clearTransforms()
        completion?.invoke()
    }

    private fun startAirborne(velocityX: Float, velocityY: Float) {
        pendingTouchAfterWake = false
        cancelPosture()
        val params = bridge.getWindowParams() ?: return
        mode = Mode.AIRBORNE
        modeTimer = 0f
        modeDuration = Float.POSITIVE_INFINITY
        airX = params.x.toFloat()
        airY = params.y.toFloat()
        airVelocityX = velocityX.coerceIn(-bridge.petSpriteSize * 4f, bridge.petSpriteSize * 4f)
        airVelocityY = velocityY.coerceIn(-bridge.petSpriteSize * 4f, bridge.petSpriteSize * 2f)
        if (abs(airVelocityX) > 1f) facingDirection = if (airVelocityX >= 0f) 1f else -1f
        bridge.currentFrame = FRAME_POUNCE_AIR
        applyFacing()
    }

    private fun startSit(duration: Float = 1.6f + random.nextFloat() * 1.4f) {
        changeMode(Mode.SIT, duration)
        placeOnGround()
        clearTransforms()
        bridge.currentFrame = FRAME_SIT
    }

    private fun enterSit(duration: Float): Unit {
        if (hasPostureArtwork) startSitDown(duration) else startSit(duration)
    }

    private fun startStandUp(initialFacing: Float, completion: () -> Unit): Unit {
        mode = Mode.STAND_UP
        modeTimer = 0f
        postureTimer = 0f
        postureInitialFacing = initialFacing
        postureCompletion = completion
        clearTransforms()
        placeOnGround()
        applyPostureFrame()
    }

    private fun startSitDown(duration: Float): Unit {
        mode = Mode.SIT_DOWN
        modeTimer = 0f
        postureTimer = 0f
        modeDuration = duration
        postureCompletion = { startSit(duration) }
        clearTransforms()
        placeOnGround()
        applyPostureFrame()
    }

    private fun applyPostureFrame(): Unit {
        val transition: GingerPostureMotion.Transition = if (mode == Mode.STAND_UP)
            GingerPostureMotion.Transition.STAND_UP else GingerPostureMotion.Transition.SIT_DOWN
        val pose: GingerPostureMotion.Pose = GingerPostureMotion.poseAt(transition, postureTimer)
        placeOnGround()
        bridge.currentFrame = pose.frame
        bridge.animScaleX = if (mode == Mode.STAND_UP && postureTimer < .10f)
            facingScaleFor(postureInitialFacing) else facingScale()
        bridge.animScaleY = 1f
        bridge.animOffsetX = 0f
        bridge.animOffsetY = 0f
        bridge.animRotation = 0f
        if (!pose.finished) return
        val completion: (() -> Unit)? = postureCompletion
        postureCompletion = null
        postureTimer = 0f
        completion?.invoke()
    }

    private fun cancelPosture(): Unit {
        if (mode == Mode.TURN) {
            if (turnTimer >= GingerTurnMotion.COMMIT_SECONDS) facingDirection = turnTargetFacing
            turnCompletion = null
            turnTimer = 0f
            mode = Mode.SIT
            modeTimer = 0f
            modeDuration = 1.6f
            clearTransforms()
            return
        }
        if (mode != Mode.STAND_UP && mode != Mode.SIT_DOWN) return
        if (mode == Mode.STAND_UP && postureTimer < .10f) facingDirection = postureInitialFacing
        postureCompletion = null
        postureTimer = 0f
        // Input may already have dragged the window into the air. Discard only
        // the pending action; resetting through startSit would teleport it down.
        changeMode(Mode.SIT, 1.6f)
        clearTransforms()
    }

    private fun changeMode(nextMode: Mode, duration: Float) {
        mode = nextMode
        modeTimer = 0f
        modeDuration = duration
    }

    private fun placeOnGround() {
        val params = bridge.getWindowParams() ?: return
        params.x = params.x.coerceIn(0, maxWindowX())
        params.y = groundY().roundToInt()
        bridge.updateWindowLayout(params)
    }

    private fun groundY(): Float = bridge.groundY.toFloat().coerceAtLeast(topLimitPx())

    private fun maxWindowX(): Int = (bridge.screenWidth - bridge.petSpriteSize).coerceAtLeast(0)

    private fun topLimitPx(): Float = (bridge.topSystemInsetPx + TOP_LIMIT_PX.toInt()).toFloat()

    private fun facingScale(stretch: Float = 1f): Float {
        return facingScaleFor(facingDirection, stretch)
    }

    private fun facingScaleFor(direction: Float, stretch: Float = 1f): Float {
        val magnitude: Float = abs(stretch)
        return if (direction < 0f) magnitude else -magnitude
    }

    private fun applyFacing() {
        bridge.animScaleX = facingScale()
    }

    private fun clearTransforms() {
        bridge.animScaleX = facingScale()
        bridge.animScaleY = 1f
        bridge.animRotation = 0f
        bridge.animOffsetX = 0f
        bridge.animOffsetY = 0f
        bridge.animAlpha = 1f
    }

    override fun onInteract() {
        if (hasRestArtwork && mode == Mode.WAKE) {
            super.onInteract()
            pendingTouchAfterWake = true
            return
        }
        if (mode == Mode.SLEEP && hasRestArtwork) {
            super.onInteract()
            pendingTouchAfterWake = true
            startWake(bridge.currentFrame)
            return
        }
        if (mode == Mode.SIT_DOWN && bridge.state == PetState.INTERACTING) return
        super.onInteract()
        cancelPosture()
        val params = bridge.getWindowParams()
        if (params != null && params.y < groundY() - 2f) {
            // A tap is affection, not a new landing: keep the jump's momentum.
            bridge.state = PetState.IDLE
            if (mode != Mode.AIRBORNE) startAirborne(0f, 0f)
        } else {
            facingDirection = if ((params?.x ?: bridge.windowX) < maxWindowX() / 2f) -1f else 1f
            changeMode(Mode.TOUCH, TOUCH_SECONDS)
        }
        bridge.showBubble(localizedString(R.string.bubble_ginger_purr, "prrr"))
        bridge.playHaptic(24)
    }

    override fun updateInteracting(dt: Float) {
        if (isLoading || spriteSheetBitmap == null || spriteFrameRects.isEmpty()) return
        time += dt
        modeTimer += dt
        if (mode == Mode.WAKE) {
            updateWake()
            return
        }
        if (mode == Mode.STAND_UP || mode == Mode.SIT_DOWN) {
            postureTimer += dt
            applyPostureFrame()
            return
        }
        updateTouch()
        if (modeTimer >= TOUCH_SECONDS) {
            bridge.state = PetState.IDLE
            changeMode(Mode.POUNCE_COIL, POUNCE_COIL_SECONDS)
        }
    }

    override fun updateDrag(dt: Float) {
        cancelPosture()
        pendingTouchAfterWake = false
        bridge.currentFrame = FRAME_TOUCH
        bridge.animScaleX = facingScale()
        bridge.animScaleY = 1f
        bridge.animRotation = 0f
        bridge.animOffsetX = 0f
        bridge.animOffsetY = 0f
    }

    override fun onFling(velocityX: Float, velocityY: Float) {
        cancelPosture()
        bridge.state = PetState.IDLE
        startAirborne(velocityX * 0.34f, velocityY * 0.34f)
        bridge.showBubble(localizedString(R.string.bubble_ginger_mrrp, "mrrp"))
    }

    override fun reset() {
        cancelPosture()
        pendingTouchAfterWake = false
        super.reset()
        val params = bridge.getWindowParams()
        if (params != null && params.y < groundY() - 2f) {
            startAirborne(0f, 0f)
        } else {
            startSit()
        }
    }

    private companion object {
        const val ATLAS_SPEC_PATH: String = "pets/ginger/ginger_sheet_v2.json"
        const val MOTION_ATLAS_SPEC_PATH: String = "pets/ginger/ginger_motion_v2.json"
        const val TURN_ATLAS_SPEC_PATH: String = "pets/ginger/ginger_turn_v2.json"
        const val POSTURE_FRAME_COUNT: Int = 19
        const val REST_FRAME_COUNT: Int = 22
        const val TURN_FRAME_COUNT: Int = 24
        const val REST_ATLAS_SPEC_PATH: String = "pets/ginger/ginger_rest_v2.json"
        const val FRAME_SIT: Int = 0
        const val FRAME_GROOM: Int = 1
        const val FRAME_SLEEP: Int = 2
        const val FRAME_STRETCH: Int = 3
        const val FRAME_WALK_START: Int = 4
        const val FRAME_STALK_START: Int = 8
        const val FRAME_POUNCE_COIL: Int = 11
        const val FRAME_POUNCE_AIR: Int = 12
        const val FRAME_LAND_IMPACT: Int = 13
        const val FRAME_LAND_RECOVER: Int = 14
        const val FRAME_TOUCH: Int = 15
        const val FRAME_STAND: Int = 18
        const val POUNCE_COIL_SECONDS: Float = 0.22f
        const val LAND_SECONDS: Float = 0.32f
        const val TOUCH_SECONDS: Float = 0.55f
        const val TOP_LIMIT_PX: Float = 50f
    }
}
