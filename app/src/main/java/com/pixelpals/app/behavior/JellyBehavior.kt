package com.pixelpals.app.behavior

import com.pixelpals.app.PetState
import com.pixelpals.app.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import com.pixelpals.app.motion.PetRandom

class JellyBehavior(
    bridge: PetViewBridge,
    override val random: PetRandom
) : BaseBehavior(bridge, random) {

    override val resourceIds = listOf(R.drawable.jelly_0, R.drawable.jelly_1, R.drawable.jelly_2, R.drawable.jelly_3, R.drawable.jelly_4, R.drawable.jelly_5, R.drawable.jelly_6, R.drawable.jelly_7)

    init {
        loadFramesAsync()
    }

    private enum class JellyMode {
        IDLE,
        PREPARE_HOP,
        HOPPING,
        LANDING
    }

    private var mode = JellyMode.IDLE
    private var modeTimer = 0f
    private var nextHopDelay = randomIdleDelay()

    private var hopStartX = 0f
    private var hopStartY = 0f
    private var hopTargetX = 0f
    private var hopTargetY = 0f
    private var hopHeight = 0f

    private var meltStartX = 0f
    private var meltStartY = 0f

    override fun getBaseSpeed(): Float = 0f

    private fun randomIdleDelay(): Float = 1.0f + random.nextFloat() * 0.9f

    private fun startIdle(resetTimer: Boolean = true) {
        mode = JellyMode.IDLE
        if (resetTimer) modeTimer = 0f
        nextHopDelay = randomIdleDelay()
        bridge.currentFrame = 0
        val params = bridge.getWindowParams() ?: return
        params.y = floorY().roundToInt()
        bridge.updateWindowLayout(params)
    }

    private fun floorY(): Float = bridge.groundY.coerceAtLeast(50).toFloat()

    private fun startHopPreparation() {
        val params = bridge.getWindowParams() ?: return
        val minX = 0f
        val maxX = (bridge.screenWidth - bridge.petSpriteSize).coerceAtLeast(0).toFloat()
        val floorY = floorY()

        hopStartX = params.x.toFloat()
        hopStartY = floorY
        params.y = floorY.roundToInt()
        bridge.updateWindowLayout(params)

        val horizontalDistance = random.nextInt(
            (bridge.petSpriteSize * 0.8f).roundToInt(),
            (bridge.petSpriteSize * 2.0f).roundToInt()
        ).toFloat()
        val moveRight = if (hopStartX < bridge.screenWidth * 0.5f) {
            random.nextFloat() > 0.25f
        } else {
            random.nextFloat() > 0.75f
        }

        hopTargetX = (hopStartX + if (moveRight) horizontalDistance else -horizontalDistance)
            .coerceIn(minX, maxX)
        hopTargetY = floorY
        hopHeight = bridge.petSpriteSize * (0.55f + random.nextFloat() * 0.35f)

        mode = JellyMode.PREPARE_HOP
        modeTimer = 0f
    }

    override fun updateIdle(dt: Float) {
        if (isLoading || frames.isEmpty()) return
        time += dt
        modeTimer += dt

        when (mode) {
            JellyMode.IDLE -> {
                bridge.currentFrame = 0
                val wobble = sin(time * 3.1f)
                bridge.animScaleY = 1f + wobble * 0.05f
                bridge.animScaleX = 1f - wobble * 0.04f
                bridge.animOffsetY = abs(sin(time * 4.2f)) * 2f
                bridge.animOffsetX = 0f
                bridge.animRotation = 0f

                if (modeTimer >= nextHopDelay) {
                    startHopPreparation()
                }
            }

            JellyMode.PREPARE_HOP -> {
                bridge.currentFrame = 1
                val squash = (modeTimer / 0.28f).coerceIn(0f, 1f)
                bridge.animScaleY = 1f - squash * 0.24f
                bridge.animScaleX = 1f + squash * 0.20f
                bridge.animOffsetY = squash * 6f
                bridge.animOffsetX = 0f
                bridge.animRotation = 0f

                if (modeTimer >= 0.28f) {
                    mode = JellyMode.HOPPING
                    modeTimer = 0f
                }
            }

            JellyMode.HOPPING -> {
                val params = bridge.getWindowParams() ?: return
                val t = (modeTimer / 0.82f).coerceIn(0f, 1f)
                val x = hopStartX + (hopTargetX - hopStartX) * t
                val groundY = hopStartY + (hopTargetY - hopStartY) * t
                val y = groundY - sin((t * PI).toFloat()) * hopHeight

                params.x = x.roundToInt()
                params.y = y.roundToInt()
                bridge.updateWindowLayout(params)

                bridge.currentFrame = when {
                    t < 0.33f -> 2
                    t < 0.68f -> 3
                    else -> 4
                }
                bridge.animScaleY = 1f + sin((t * PI).toFloat()) * 0.06f
                bridge.animScaleX = 1f - sin((t * PI).toFloat()) * 0.05f
                bridge.animOffsetX = 0f
                bridge.animOffsetY = 0f
                bridge.animRotation = 0f

                if (t >= 1f) {
                    mode = JellyMode.LANDING
                    modeTimer = 0f
                }
            }

            JellyMode.LANDING -> {
                bridge.currentFrame = 5
                val rebound = sin((modeTimer / 0.34f).coerceIn(0f, 1f) * PI).toFloat()
                bridge.animScaleY = 0.82f + rebound * 0.10f
                bridge.animScaleX = 1.18f - rebound * 0.12f
                bridge.animOffsetY = (1f - rebound) * 4f
                bridge.animOffsetX = 0f
                bridge.animRotation = 0f

                if (modeTimer >= 0.34f) {
                    startIdle()
                }
            }
        }
    }

    override fun updateJumping(dt: Float) {
        updateIdle(dt)
    }

    override fun onInteract() {
        super.onInteract()
        val params = bridge.getWindowParams()
        meltStartX = params?.x?.toFloat() ?: bridge.windowX.toFloat()
        meltStartY = params?.y?.toFloat() ?: bridge.windowY.toFloat()
        bridge.showBubble("splash")
    }

    override fun onFling(velocityX: Float, velocityY: Float) {
        super.onInteract()
        val params = bridge.getWindowParams() ?: return
        val maxX = (bridge.screenWidth - bridge.petSpriteSize).coerceAtLeast(0).toFloat()
        hopStartX = params.x.toFloat()
        hopStartY = floorY()
        params.y = hopStartY.roundToInt()
        bridge.updateWindowLayout(params)
        hopTargetX = (hopStartX + velocityX * 0.09f).coerceIn(0f, maxX)
        hopTargetY = floorY()
        hopHeight = (bridge.petSpriteSize * 0.7f + abs(velocityY) * 0.025f)
            .coerceAtMost(bridge.petSpriteSize * 1.8f)
        mode = JellyMode.HOPPING
        modeTimer = 0f
        bridge.state = PetState.IDLE
        bridge.showBubble("boing")
        bridge.animRotation = (velocityX * 0.02f).coerceIn(-20f, 20f)
        bridge.animScaleX = 1.25f
        bridge.animScaleY = 0.75f
    }

    override fun updateInteracting(dt: Float) {
        if (frames.isEmpty()) return
        interactionTimer += dt
        val params = bridge.getWindowParams() ?: return
        val minX = 0f
        val maxX = (bridge.screenWidth - bridge.petSpriteSize).coerceAtLeast(0).toFloat()
        val floorY = floorY()

        val fallT = (interactionTimer / 1.45f).coerceIn(0f, 1f)
        val meltProgress = fallT * fallT
        val sway = sin(interactionTimer * 3.2f) * (bridge.petSpriteSize * 0.18f)
        val meltY = meltStartY + (floorY - meltStartY) * meltProgress

        params.x = (meltStartX + sway).coerceIn(minX, maxX).roundToInt()
        params.y = meltY.roundToInt()
        bridge.updateWindowLayout(params)

        bridge.currentFrame = if (((interactionTimer / 0.22f).toInt() % 2) == 0) 6 else 7
        val wobble = abs(sin(interactionTimer * 4.1f))
        bridge.animScaleY = 0.52f + wobble * 0.18f
        bridge.animScaleX = 1.34f - wobble * 0.18f
        bridge.animOffsetX = sin(interactionTimer * 6.4f) * 6f
        bridge.animOffsetY = sin(interactionTimer * 4.8f) * 4f
        bridge.animRotation = sin(interactionTimer * 2.8f) * 9f

        if (interactionTimer > 1.75f) {
            bridge.state = PetState.IDLE
            reset()
        }
    }

    override fun reset() {
        super.reset()
        startIdle()
    }
}
