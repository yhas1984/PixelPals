package com.pixelpals.app.feature.overlay.behavior

import com.pixelpals.app.core.domain.PetState
import com.pixelpals.app.R
import com.pixelpals.app.core.motion.PetRandom
import com.pixelpals.app.core.motion.CorgiGait
import kotlin.math.abs
import kotlin.math.sin
import com.pixelpals.app.core.motion.CorgiPostureMotion
import com.pixelpals.app.core.motion.CorgiTurnMotion

/** Grounded dog behavior: walking, sniffing, digging, play bows and zoomies. */
class CorgiBehavior(
    bridge: PetViewBridge,
    override val random: PetRandom,
) : BaseBehavior(bridge, random) {
    override fun getFrameCameraScale(index: Int): Float = com.pixelpals.app.core.motion.CorgiArtworkScale.originalFrame(index)
    override val frameGround: Float = com.pixelpals.app.core.motion.CorgiArtworkScale.ORIGINAL_GROUND
    override val preloadAllFrames: Boolean = true
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

    override fun canStartScheduledSleep(reducedMotion: Boolean): Boolean =
        mode == Mode.REST || mode == Mode.ALERT || (reducedMotion && mode == Mode.WALK)

    private enum class Mode {
        WALK,
        ALERT,
        SNIFF,
        DIG,
        PLAY_BOW,
        REST,
        SIT_DOWN,
        STAND_UP,
        TURN,
    }

    private var mode = Mode.WALK
    private var modeTimer = 0f
    private var modeDuration = 4.5f
    private var walkDirection = 1f
    private var currentSpeed = 0f
    private var gaitDistance = 0f
    private val walkTravel: com.pixelpals.app.core.motion.SubpixelTravel = com.pixelpals.app.core.motion.SubpixelTravel()
    private var edgeTurnRemaining: Float = 0f
    private var turnTimer: Float = 0f
    private var turnStartDirection: Float = 1f
    private var hasFoundBone = false
    private var isZooming = false
    private var zoomStartX = 0
    private var zoomEndX = 0
    private var zoomTurnSeconds = 0f
    private var zoomStartDirection = 1f
    private var zoomDirection = 1f
    private var pendingAction: Mode? = null
    private var pendingActionDuration: Float = 0f
    private var postureTimer: Float = 0f
    private var restDurationAfterSit: Float = 2.2f
    private var postureTransition: CorgiPostureMotion.Transition? = null
    private val hasPostureArtwork: Boolean = hasOptionalPostureArtwork()
    private val hasTurnArtwork: Boolean get() = spriteFrameRects.size == 23

    init {
        if (hasPostureArtwork) loadSpriteSheetAssetAsync(POSTURE_ATLAS_PATH) else loadFramesAsync()
    }

    override val isSeatedForScheduledRest: Boolean
        get() = hasPostureArtwork && mode == Mode.REST

    override fun advanceScheduledRestTransition(delta: Float, reducedMotion: Boolean) {
        if (reducedMotion && isZooming) {
            cancelZoomie()
            bridge.state = PetState.IDLE
            changeMode(Mode.ALERT, ACTION_PLANT_SECONDS)
            bridge.currentFrame = FRAME_ALERT
        }
        if (reducedMotion && mode == Mode.TURN) {
            turnTimer = CorgiTurnMotion.DURATION_SECONDS
            applyTurnFrame()
        }
        if (!hasPostureArtwork || postureTransition == null) return
        if (reducedMotion) {
            postureTimer = CorgiPostureMotion.DURATION_SECONDS
            applyPostureFrame()
        }
    }

    override fun onScheduledWakeCompleted() {
        pendingAction = null
        pendingActionDuration = 0f
        postureTransition = null
        postureTimer = 0f
        mode = Mode.ALERT
        modeTimer = 0f
        modeDuration = ACTION_PLANT_SECONDS
        clearTransforms()
        stopOnGround()
        bridge.currentFrame = FRAME_ALERT
    }

    override fun updateIdle(dt: Float) {
        if (isLoading || (!hasPostureArtwork && frames.isEmpty()) ||
            (hasPostureArtwork && spriteFrameRects.isEmpty())) return
        time += dt
        modeTimer += dt
        when (mode) {
            Mode.WALK -> updateWalk(dt)
            Mode.ALERT -> updateAlert()
            Mode.SNIFF -> updateSniff()
            Mode.DIG -> updateDig()
            Mode.PLAY_BOW -> updatePlayBow()
            Mode.REST -> updateRest()
            Mode.SIT_DOWN, Mode.STAND_UP -> {
                postureTimer += dt
                applyPostureFrame()
            }
            Mode.TURN -> {
                turnTimer += dt
                applyTurnFrame()
            }
        }
    }

    private fun updateWalk(dt: Float) {
        val params = bridge.getWindowParams() ?: return
        val maxX = maxWindowX()
        if (edgeTurnRemaining > 0f) {
            edgeTurnRemaining = (edgeTurnRemaining - dt).coerceAtLeast(0f)
            bridge.currentFrame = FRAME_ALERT
            return
        }
        val edgeDistance: Float = if (walkDirection > 0) (maxX - params.x).toFloat() else params.x.toFloat()
        val brakingSpeed: Float = kotlin.math.sqrt(2f * bridge.petSpriteSize * 1.9f * (edgeDistance + 1f))
        val actionBrakingSpeed: Float = (modeDuration - modeTimer).coerceAtLeast(0f) * bridge.petSpriteSize * 1.9f
        val targetSpeed = minOf(bridge.petSpriteSize * 0.58f * moodSpeedMultiplier(), brakingSpeed, actionBrakingSpeed)
        currentSpeed = approach(currentSpeed, targetSpeed, bridge.petSpriteSize * 1.9f * dt)
        val previousX = params.x
        val proposedX = params.x + walkTravel.advance(walkDirection * currentSpeed * dt)
        val shouldReverse = CorgiEdgeMotion.shouldReverse(
            positionX = params.x,
            proposedX = proposedX,
            maxX = maxX,
            direction = walkDirection,
        )
        params.x = proposedX.coerceIn(0, maxX)
        if (shouldReverse && !hasTurnArtwork) {
            walkDirection *= -1f
            currentSpeed = 0f
            edgeTurnRemaining = .28f
            walkTravel.reset()
        }
        params.y = groundY()
        bridge.updateWindowLayout(params)

        gaitDistance += abs(params.x - previousX)
        if (shouldReverse && hasTurnArtwork) {
            startEdgeTurn()
            return
        }
        bridge.currentFrame = if (edgeTurnRemaining > 0f) FRAME_ALERT
            else CorgiGait.frameAt(gaitDistance, bridge.petSpriteSize.toFloat())
        bridge.animScaleX = if (walkDirection >= 0f) 1f else -1f
        bridge.animScaleY = 1f
        bridge.animOffsetX = 0f
        bridge.animOffsetY = 0f
        bridge.animRotation = 0f

        if (modeTimer >= modeDuration) chooseDogAction()
    }

    private fun startEdgeTurn() {
        mode = Mode.TURN
        modeTimer = 0f
        turnTimer = 0f
        turnStartDirection = walkDirection
        edgeTurnRemaining = 0f
        walkTravel.reset()
        applyTurnFrame()
    }

    private fun applyTurnFrame() {
        stopOnGround()
        val pose: CorgiTurnMotion.Pose = CorgiTurnMotion.poseAt(turnTimer)
        clearTransforms()
        bridge.currentFrame = pose.frame
        bridge.animScaleX = turnStartDirection * if (pose.mirrored) -1f else 1f
        if (pose.finished) {
            walkDirection = -turnStartDirection
            startWalk()
        }
    }

    private fun cancelTurn() {
        if (mode != Mode.TURN) return
        // Keep the visible side when an input interrupts a partly completed
        // turn. Never resume an obsolete pivot after dragging or affection.
        walkDirection = turnStartDirection * if (CorgiTurnMotion.poseAt(turnTimer).mirrored) -1f else 1f
        startWalk()
        bridge.currentFrame = FRAME_ALERT
    }

    private fun updateAlert() {
        stopOnGround()
        bridge.currentFrame = FRAME_ALERT
        bridge.animScaleX = if (walkDirection >= 0f) 1f else -1f
        bridge.animScaleY = 1f
        bridge.animRotation = 0f
        if (modeTimer >= modeDuration) {
            val action: Mode? = pendingAction
            if (action == null) {
                startWalk()
            } else {
                pendingAction = null
                val duration = pendingActionDuration
                pendingActionDuration = 0f
                changeMode(action, duration)
            }
        }
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
        if (modeTimer >= modeDuration) startWalk()
    }

    private fun updateRest() {
        stopOnGround()
        bridge.currentFrame = if (hasPostureArtwork) CorgiPostureMotion.restingFrame(modeTimer)
            else if ((modeTimer / 0.72f).toInt() % 2 == 0) FRAME_REST else FRAME_REST_BLINK
        bridge.animScaleX = if (walkDirection >= 0f) 1f else -1f
        bridge.animScaleY = 1f
        bridge.animOffsetY = 0f
        bridge.animRotation = 0f
        if (modeTimer >= modeDuration) startStandUpIfNeeded()
    }

    private fun chooseDogAction() {
        when (random.nextInt(0, 4)) {
            0 -> changeMode(Mode.ALERT, 0.8f)
            1 -> beginActionAfterPlant(Mode.SNIFF, 1.55f)
            2 -> beginActionAfterPlant(Mode.PLAY_BOW, 1.05f)
            else -> beginActionAfterPlant(Mode.REST, 2.2f)
        }
    }

    private fun beginActionAfterPlant(action: Mode, duration: Float) {
        pendingAction = action
        pendingActionDuration = duration
        changeMode(Mode.ALERT, ACTION_PLANT_SECONDS)
        // Replace the last gait frame immediately; the first planted draw must
        // not briefly show a lifted paw while the transition timer starts.
        bridge.currentFrame = FRAME_ALERT
    }

    private fun startSitDown(duration: Float) {
        mode = Mode.SIT_DOWN
        modeTimer = 0f
        postureTimer = 0f
        restDurationAfterSit = duration
        postureTransition = CorgiPostureMotion.Transition.SIT_DOWN
        clearTransforms()
        stopOnGround()
        applyPostureFrame()
    }

    private fun startStandUp() {
        mode = Mode.STAND_UP
        modeTimer = 0f
        postureTimer = 0f
        postureTransition = CorgiPostureMotion.Transition.STAND_UP
        clearTransforms()
        stopOnGround()
        applyPostureFrame()
    }

    private fun startStandUpIfNeeded() {
        if (hasPostureArtwork) startStandUp() else startWalk()
    }

    private fun applyPostureFrame() {
        val transition: CorgiPostureMotion.Transition = postureTransition ?: return
        stopOnGround()
        val pose: CorgiPostureMotion.Pose = CorgiPostureMotion.poseAt(transition, postureTimer)
        bridge.currentFrame = pose.frame
        bridge.animScaleX = if (walkDirection >= 0f) 1f else -1f
        bridge.animScaleY = 1f
        bridge.animOffsetX = 0f
        bridge.animOffsetY = 0f
        bridge.animRotation = 0f
        if (!pose.finished) return
        postureTransition = null
        postureTimer = 0f
        modeTimer = 0f
        if (transition == CorgiPostureMotion.Transition.SIT_DOWN) {
            mode = Mode.REST
            modeDuration = restDurationAfterSit
            bridge.currentFrame = CorgiPostureMotion.restingFrame(0f)
        } else {
            startWalk()
        }
    }

    private fun cancelPostureTransition() {
        val wasTransitioning: Boolean = postureTransition != null
        postureTransition = null
        postureTimer = 0f
        if (wasTransitioning && (mode == Mode.SIT_DOWN || mode == Mode.STAND_UP)) startWalk()
    }

    private fun hasOptionalPostureArtwork(): Boolean {
        val context = (bridge as? android.view.View)?.context ?: return false
        return try {
            context.assets.open(POSTURE_ATLAS_PATH).use { true }
        } catch (_: Exception) {
            false
        }
    }

    private fun startWalk() {
        pendingAction = null
        pendingActionDuration = 0f
        changeMode(Mode.WALK, 3.8f + random.nextFloat() * 2.2f)
        currentSpeed = 0f
        gaitDistance = 0f
        walkTravel.reset()
        edgeTurnRemaining = 0f
        turnTimer = 0f
        clearTransforms()
    }

    private fun changeMode(nextMode: Mode, duration: Float) {
        if (nextMode == Mode.REST && hasPostureArtwork) {
            startSitDown(duration)
            return
        }
        mode = nextMode
        modeTimer = 0f
        modeDuration = duration
        hasFoundBone = false
        clearTransforms()
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
        cancelTurn()
        cancelZoomie()
        super.onInteract()
        pendingAction = null
        pendingActionDuration = 0f
        cancelPostureTransition()
        interactionTimer = 0f
        currentSpeed = 0f
        clearTransforms()
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
        bridge.animScaleY = 1f
        bridge.animRotation = sin(interactionTimer * 8f) * 7f
        bridge.animOffsetY = -abs(sin(interactionTimer * 8f)) * 2f
        if (interactionTimer >= 1.35f) {
            bridge.state = PetState.IDLE
            startWalk()
        }
    }

    private fun updateZoomie(dt: Float) {
        interactionTimer += dt.coerceAtLeast(0f)
        if (interactionTimer < zoomTurnSeconds) {
            val pose = CorgiTurnMotion.poseAt(interactionTimer)
            stopOnGround()
            clearTransforms()
            bridge.currentFrame = pose.frame
            bridge.animScaleX = zoomStartDirection * if (pose.mirrored) -1f else 1f
            return
        }
        walkDirection = zoomDirection
        // Keep excess time when a tick crosses the turn endpoint: anticipation
        // and travel share one timeline and cannot restart after a slow frame.
        val burstSeconds = interactionTimer - zoomTurnSeconds
        // Anticipate on planted feet, at the same camera as the standing pose.
        // The old frame 9 is a separately framed lying dog, not a run contact.
        if (burstSeconds < .16f) {
            stopOnGround()
            clearTransforms()
            bridge.currentFrame = FRAME_DRAG
            return
        }
        val params = bridge.getWindowParams() ?: return
        val previousX = params.x
        // Plan the short burst within the available floor. This curve reaches
        // both endpoints at rest, including when a nearby edge shortens the run.
        val progress = com.pixelpals.app.core.motion.GroundGait.progress(burstSeconds - .16f, .74f)
        params.x = (zoomStartX + (zoomEndX - zoomStartX) * progress).toInt().coerceIn(0, maxWindowX())
        params.y = groundY()
        bridge.updateWindowLayout(params)
        gaitDistance += abs(params.x - previousX)

        bridge.currentFrame = if (zoomStartX == zoomEndX) FRAME_ALERT
            else CorgiGait.frameAt(gaitDistance, bridge.petSpriteSize.toFloat(), running = true)
        bridge.animScaleX = if (walkDirection >= 0f) 1f else -1f
        bridge.animScaleY = 1f
        bridge.animOffsetY = 0f
        bridge.animRotation = 0f

        if (burstSeconds >= 0.9f) {
            isZooming = false
            zoomTurnSeconds = 0f
            bridge.state = PetState.IDLE
            beginActionAfterPlant(Mode.SNIFF, 1.1f)
        }
    }

    override fun onFling(velocityX: Float, velocityY: Float) {
        cancelTurn()
        cancelZoomie()
        pendingAction = null
        pendingActionDuration = 0f
        cancelPostureTransition()
        clearTransforms()
        bridge.currentFrame = FRAME_DRAG
        // PetView launches shared physics if a released gesture remains DRAGGING.
        // Taking ownership in midair used to snap Corgi directly to the floor.
        val airborne = (bridge.getWindowParams()?.y ?: groundY()) < groundY() - 1
        if (bridge.state == PetState.FALLING || bridge.state == PetState.JUMPING ||
            (bridge.state == PetState.DRAGGING && (airborne || abs(velocityY) > abs(velocityX)))) return

        super.onInteract()
        isZooming = true
        interactionTimer = 0f
        zoomStartDirection = walkDirection
        zoomDirection = if (velocityX >= 0f) 1f else -1f
        zoomTurnSeconds = if (hasTurnArtwork && zoomDirection != walkDirection) CorgiTurnMotion.DURATION_SECONDS else 0f
        if (zoomTurnSeconds == 0f) walkDirection = zoomDirection
        zoomStartX = (bridge.getWindowParams()?.x ?: 0).coerceIn(0, maxWindowX())
        zoomEndX = (zoomStartX + zoomDirection * bridge.petSpriteSize * .28f).toInt().coerceIn(0, maxWindowX())
        currentSpeed = 0f
        gaitDistance = 0f
        walkTravel.reset()
        clearTransforms()
        bridge.currentFrame = FRAME_DRAG
        bridge.showBubble("💨🐾")
    }

    private fun cancelZoomie() {
        if (!isZooming) return
        if (interactionTimer < zoomTurnSeconds) {
            walkDirection = zoomStartDirection * if (CorgiTurnMotion.poseAt(interactionTimer).mirrored) -1f else 1f
        }
        isZooming = false
        zoomTurnSeconds = 0f
        interactionTimer = 0f
        clearTransforms()
    }

    override fun updateDrag(dt: Float) {
        super.updateDrag(dt)
        cancelTurn()
        cancelZoomie()
        cancelPostureTransition()
        bridge.currentFrame = FRAME_DRAG
        bridge.animScaleX = if (walkDirection >= 0f) 1f else -1f
    }

    override fun updateFalling(dt: Float) {
        super.updateFalling(dt)
        clearTransforms()
    }

    override fun updateJumping(dt: Float) {
        super.updateJumping(dt)
        clearTransforms()
    }

    override fun reset() {
        cancelZoomie()
        cancelTurn()
        cancelPostureTransition()
        pendingAction = null
        pendingActionDuration = 0f
        super.reset()
        startWalk()
    }

    fun resumeAfterFetch(facingLeft: Boolean): Unit = resumeAfterCare(facingLeft, false)

    fun resumeAfterCare(facingLeft: Boolean, wasResting: Boolean): Unit {
        pendingAction = null
        pendingActionDuration = 0f
        reset()
        walkDirection = if (facingLeft) -1f else 1f
        // Match the upright final care pose before beginning another stride.
        // Starting WALK immediately replaced it with a stretched running contact.
        if (wasResting && hasPostureArtwork) {
            postureTransition = null
            mode = Mode.REST
            modeTimer = 0f
            modeDuration = .65f
            clearTransforms()
            stopOnGround()
            bridge.currentFrame = CorgiPostureMotion.restingFrame(0f)
        } else {
            changeMode(if (wasResting) Mode.REST else Mode.ALERT, .65f)
            bridge.currentFrame = if (wasResting) FRAME_REST else FRAME_DRAG
        }
        bridge.animScaleX = walkDirection
    }

    private fun approach(value: Float, target: Float, delta: Float): Float {
        return when {
            value < target -> (value + delta).coerceAtMost(target)
            value > target -> (value - delta).coerceAtLeast(target)
            else -> target
        }
    }

    private companion object {
        const val POSTURE_ATLAS_PATH = "pets/corgi/corgi_motion_v2.json"
        const val FRAME_DRAG = 0
        // Frame 1 has lifted paws: holding it at rest makes Corgi look lame.
        const val FRAME_ALERT = FRAME_DRAG
        const val FRAME_PLAY_BOW = 2
        const val FRAME_SNIFF = 3
        const val FRAME_DIG = 4
        const val FRAME_BONE = 5
        const val FRAME_REST = 6
        const val FRAME_REST_BLINK = 7
        const val FRAME_BELLY_RUB = 8
        const val ACTION_PLANT_SECONDS = .18f
    }
}
