package com.pixelpals.app.behavior

import com.pixelpals.app.PetState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

class JellyBehavior(
    bridge: PetViewBridge
) : BaseBehavior(bridge) {

    override val resourceIds = (0..7).map { i ->
        (bridge as android.view.View).context.resources.getIdentifier(
            "jelly_$i", "drawable", (bridge as android.view.View).context.packageName
        )
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

    private fun randomIdleDelay(): Float = 1.0f + Random.nextFloat() * 0.9f

    private fun startIdle(resetTimer: Boolean = true) {
        mode = JellyMode.IDLE
        if (resetTimer) modeTimer = 0f
        nextHopDelay = randomIdleDelay()
        bridge.currentFrame = 0
    }

    private fun startHopPreparation() {
        val params = bridge.getWindowParams() ?: return
        val minX = 0f
        val maxX = (bridge.screenWidth - bridge.petSpriteSize).coerceAtLeast(0).toFloat()
        val minY = 70f
        val maxY = (bridge.screenHeight - bridge.petSpriteSize - 110).coerceAtLeast(70).toFloat()

        hopStartX = params.x.toFloat()
        hopStartY = params.y.toFloat()

        val horizontalDistance = Random.nextInt(
            (bridge.petSpriteSize * 0.8f).roundToInt(),
            (bridge.petSpriteSize * 2.0f).roundToInt()
        ).toFloat()
        val verticalDistance = Random.nextInt(
            (bridge.petSpriteSize * 0.35f).roundToInt(),
            (bridge.petSpriteSize * 1.0f).roundToInt()
        ).toFloat()

        val moveRight = if (hopStartX < bridge.screenWidth * 0.5f) {
            Random.nextFloat() > 0.25f
        } else {
            Random.nextFloat() > 0.75f
        }
        val moveDown = Random.nextBoolean()

        hopTargetX = (hopStartX + if (moveRight) horizontalDistance else -horizontalDistance)
            .coerceIn(minX, maxX)
        hopTargetY = (hopStartY + if (moveDown) verticalDistance else -verticalDistance)
            .coerceIn(minY, maxY)
        hopHeight = bridge.petSpriteSize * (0.55f + Random.nextFloat() * 0.35f)

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

    override fun updateInteracting(dt: Float) {
        if (frames.isEmpty()) return
        interactionTimer += dt
        val params = bridge.getWindowParams() ?: return
        val minX = 0f
        val maxX = (bridge.screenWidth - bridge.petSpriteSize).coerceAtLeast(0).toFloat()
        val floorY = (bridge.screenHeight - bridge.petSpriteSize - 100).coerceAtLeast(50).toFloat()

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
