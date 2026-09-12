package com.pixelpals.app.feature.overlay.behavior

import com.pixelpals.app.R
import com.pixelpals.app.core.domain.PetState
import com.pixelpals.app.core.motion.JellyElasticMotion
import com.pixelpals.app.core.motion.JellyFlightMotion
import com.pixelpals.app.core.motion.PetRandom
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Elastic material keeps one camera while momentum and contact drive its shape. */
class JellyBehavior(
    bridge: PetViewBridge,
    override val random: PetRandom,
) : BaseBehavior(bridge, random) {
    override val resourceIds: List<Int> = listOf(R.drawable.jelly_0, R.drawable.jelly_1,
        R.drawable.jelly_2, R.drawable.jelly_3, R.drawable.jelly_4, R.drawable.jelly_5,
        R.drawable.jelly_6, R.drawable.jelly_7)

    private enum class JellyMode { IDLE, PREPARE_HOP, HOPPING, LANDING, TOUCH }
    private var mode: JellyMode = JellyMode.IDLE
    private var modeTimer: Float = 0f
    private var nextHopDelay: Float = randomIdleDelay()
    private var flight: JellyFlightMotion? = null
    private var touchOnLanding: Boolean = false
    private var reduced: Boolean = false
    private var initialScaleY: Float = 1f
    private var flightBlendSeconds: Float = 0f
    private var handoff: Shape = Shape()
    private var preparationStartY: Float = 1f

    private data class Shape(val x: Float = 1f, val y: Float = 1f,
        val offsetX: Float = 0f, val offsetY: Float = 0f, val rotation: Float = 0f)

    init { loadFramesAsync() }

    override fun getBaseSpeed(): Float = 0f

    override fun canStartScheduledSleep(reducedMotion: Boolean): Boolean =
        mode == JellyMode.IDLE && bridge.windowY >= floorY().roundToInt()

    override fun advanceScheduledRestTransition(delta: Float, reducedMotion: Boolean) {
        reduced = reducedMotion
        // PetView pauses idle animation in reduced motion; finish only the
        // pending contact, so Jelly cannot freeze in a compressed takeoff pose.
        if (reduced && bridge.state == PetState.IDLE) {
            if (mode == JellyMode.PREPARE_HOP) startIdle()
            else if (mode == JellyMode.LANDING || mode == JellyMode.TOUCH) updateIdle(delta)
            else if (mode == JellyMode.IDLE) applyShape(1f)
        }
    }

    private fun randomIdleDelay(): Float = 1f + random.nextFloat() * .9f
    private fun floorY(): Float = bridge.groundY.toFloat()
    private fun gravity(): Float = (bridge.petSpriteSize * 24f).coerceAtLeast(900f)

    private fun startIdle() {
        mode = JellyMode.IDLE
        modeTimer = 0f
        flight = null
        touchOnLanding = false
        nextHopDelay = randomIdleDelay()
        bridge.state = PetState.IDLE
        applyShape(1f)
    }

    private fun startHopPreparation() {
        mode = JellyMode.PREPARE_HOP
        modeTimer = 0f
        preparationStartY = bridge.animScaleY
    }

    private fun startAutonomousHop() {
        val params = bridge.getWindowParams() ?: return
        val distance: Float = bridge.petSpriteSize * (.8f + random.nextFloat() * 1.2f)
        val direction: Float = if (params.x < bridge.screenWidth / 2) {
            if (random.nextFloat() > .25f) 1f else -1f
        } else if (random.nextFloat() > .75f) 1f else -1f
        val height: Float = bridge.petSpriteSize * (.55f + random.nextFloat() * .35f)
        val upwardSpeed: Float = sqrt(2f * gravity() * height)
        val duration: Float = 2f * upwardSpeed / gravity()
        launch(distance * direction / duration, -upwardSpeed)
    }

    private fun launch(velocityX: Float, velocityY: Float) {
        val params = bridge.getWindowParams() ?: return
        handoff = captureShape()
        flightBlendSeconds = 0f
        flight = JellyFlightMotion(params.x.toFloat(), params.y.toFloat(), velocityX, velocityY,
            gravity(), bridge.bounds.left.toFloat(), bridge.bounds.right.toFloat(),
            bridge.bounds.top.toFloat(), floorY())
        mode = JellyMode.HOPPING
        modeTimer = 0f
        touchOnLanding = false
        bridge.state = PetState.JUMPING
        // No pose or window mutation here: the first flight sample is exactly
        // the pose the user released, including any interrupted compression.
    }

    override fun updateIdle(dt: Float) {
        if (isLoading || frames.isEmpty()) return
        val delta: Float = dt.coerceAtLeast(0f)
        time += delta
        modeTimer += delta
        when (mode) {
            JellyMode.IDLE -> {
                if (bridge.windowY < floorY().roundToInt()) {
                    launch(0f, 0f)
                    return
                }
                val settled: Float = JellyElasticMotion.ease(modeTimer / .25f)
                applyShape(if (reduced) 1f else 1f + sin(time * 3.1f) * settled * .018f)
                if (!reduced && modeTimer >= nextHopDelay) startHopPreparation()
            }
            JellyMode.PREPARE_HOP -> {
                val progress: Float = JellyElasticMotion.ease(modeTimer / JellyElasticMotion.PREPARE_SECONDS)
                applyShape(preparationStartY + (.82f - preparationStartY) * progress)
                if (modeTimer >= JellyElasticMotion.PREPARE_SECONDS) startAutonomousHop()
            }
            JellyMode.HOPPING -> advanceFlight(delta)
            JellyMode.LANDING -> {
                applyShape(if (reduced) 1f else JellyElasticMotion.landingScaleY(modeTimer, initialScaleY))
                if (modeTimer >= JellyElasticMotion.LAND_SECONDS) startIdle()
            }
            JellyMode.TOUCH -> advanceTouch()
        }
    }

    override fun updateJumping(dt: Float) { updateIdle(dt) }

    private fun advanceFlight(delta: Float) {
        val airborne: JellyFlightMotion = flight ?: return
        airborne.advance(delta)
        val params = bridge.getWindowParams() ?: return
        params.x = airborne.x.roundToInt()
        params.y = airborne.y.roundToInt()
        bridge.updateWindowLayout(params)
        flightBlendSeconds += delta
        if (airborne.landed) {
            flight = null
            initialScaleY = bridge.animScaleY
            modeTimer = 0f
            mode = if (touchOnLanding) JellyMode.TOUCH else JellyMode.LANDING
            bridge.state = if (touchOnLanding) PetState.INTERACTING else PetState.IDLE
            // Keep the contact sample, then compress on subsequent ticks.
        } else {
            val scaleY: Float = if (reduced) 1f else JellyElasticMotion.airScaleY(airborne.velocityY, bridge.petSpriteSize.toFloat())
            val blend: Float = JellyElasticMotion.ease(flightBlendSeconds / .18f)
            applyShape(scaleY, handoff, blend)
        }
    }

    override fun onInteract() {
        super.onInteract()
        touchOnLanding = true
        val existing: JellyFlightMotion? = flight
        val params = bridge.getWindowParams()
        if (mode == JellyMode.HOPPING && existing != null && params != null &&
            abs(existing.x - params.x) <= 1f && abs(existing.y - params.y) <= 1f) {
            // A tap does not cancel velocity. Affection is expressed on contact.
        } else if (bridge.windowY < floorY().roundToInt()) {
            launch(0f, 0f)
            touchOnLanding = true
            bridge.state = PetState.INTERACTING
        } else {
            flight = null
            initialScaleY = bridge.animScaleY
            mode = JellyMode.TOUCH
            modeTimer = 0f
        }
        bridge.showBubble(localizedString(R.string.bubble_jelly_splash, "splash"))
    }

    override fun onFling(velocityX: Float, velocityY: Float) {
        super.onInteract()
        launch((velocityX * .4f).coerceIn(-bridge.petSpriteSize * 5f, bridge.petSpriteSize * 5f),
            (velocityY * .4f).coerceIn(-bridge.petSpriteSize * 8f, bridge.petSpriteSize * 8f))
        bridge.showBubble(localizedString(R.string.bubble_jelly_boing, "boing"))
    }

    override fun onRelease(velocityX: Float, velocityY: Float) { launch(0f, 0f) }

    override fun onKeyboardVisibilityChanged(visible: Boolean, height: Int) {
        super.onKeyboardVisibilityChanged(visible, height)
        // The flight captured the old floor. Rebase from the viewport-adjusted
        // position so its next sample cannot carry Jelly behind the keyboard.
        reset()
    }

    override fun updateInteracting(dt: Float) {
        if (isLoading || frames.isEmpty()) return
        val delta: Float = dt.coerceAtLeast(0f)
        interactionTimer += delta
        modeTimer += delta
        if (flight != null) advanceFlight(delta) else advanceTouch()
    }

    private fun advanceTouch() {
        applyShape(if (reduced) 1f else JellyElasticMotion.touchScaleY(modeTimer, initialScaleY))
        if (modeTimer >= if (reduced) .2f else JellyElasticMotion.TOUCH_SECONDS) startIdle()
    }

    private fun captureShape(): Shape = Shape(bridge.animScaleX, bridge.animScaleY,
        bridge.animOffsetX, bridge.animOffsetY, bridge.animRotation)

    private fun applyShape(scaleY: Float, from: Shape? = null, blend: Float = 1f) {
        val contact: Float = (JellyElasticMotion.GROUND - .5f) * bridge.petSpriteSize * bridge.spriteScale * spriteAtlasDrawScale
        fun value(start: Float?, end: Float): Float = if (start == null) end else start + (end - start) * blend
        // Legacy action drawings change material and camera. The original glossy
        // body provides continuous elastic motion until replacement art is reviewed.
        bridge.currentFrame = 0
        bridge.animScaleY = value(from?.y, scaleY)
        bridge.animScaleX = value(from?.x, 1f / scaleY)
        bridge.animOffsetX = value(from?.offsetX, 0f)
        bridge.animOffsetY = value(from?.offsetY, contact * (1f - scaleY))
        bridge.animRotation = value(from?.rotation, 0f)
    }

    override fun reset() {
        val previous: Shape = captureShape()
        val previousFlight: JellyFlightMotion? = flight?.takeIf {
            abs(it.x - bridge.windowX) <= 1f && abs(it.y - bridge.windowY) <= 1f
        }
        super.reset()
        flight = null
        touchOnLanding = false
        if (bridge.windowY < floorY().roundToInt()) {
            launch(previousFlight?.velocityX ?: 0f, previousFlight?.velocityY ?: 0f)
            handoff = previous
            applyShape(1f, previous, 0f)
        } else {
            initialScaleY = previous.y
            mode = JellyMode.LANDING
            modeTimer = 0f
            bridge.state = PetState.IDLE
            applyShape(previous.y)
        }
    }
}
