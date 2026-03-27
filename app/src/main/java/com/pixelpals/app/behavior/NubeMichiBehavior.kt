package com.pixelpals.app.behavior

import com.pixelpals.app.PetState
import com.pixelpals.app.R
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * NubeMichiBehavior — Gatito nube somnoliento que despierta, camina
 * y al tocarlo se vuelve pluma antes de subir como nube esponjosa.
 */
class NubeMichiBehavior(
    bridge: PetViewBridge
) : BaseBehavior(bridge) {

    override val resourceIds = listOf(
        R.drawable.gato_0, // dormido flotando
        R.drawable.gato_1, // despierta
        R.drawable.gato_2, // nube esponjosa subiendo
        R.drawable.gato_3, // se levanta
        R.drawable.gato_4, // camina
        R.drawable.gato_5, // pluma
        R.drawable.gato_6, // camina 2
        R.drawable.gato_7  // camina 3
    )

    private enum class Mode {
        SLEEP_FLOAT,
        WAKE_UP,
        STAND_UP,
        WALK,
        FEATHER_FALL,
        CLOUD_RETURN
    }

    private var mode = Mode.SLEEP_FLOAT
    private var stateTimer = 0f
    private var walkTargetX = 0f
    private var walkDirection = 1f
    private var returnTargetY = 0f

    override fun getBaseSpeed(): Float = 52f

    private fun setFacing(directionX: Float, stretch: Float = 1f) {
        val absStretch = abs(stretch)
        bridge.animScaleX = if (directionX >= 0f) absStretch else -absStretch
    }

    private fun startSleepFloat(resetTimer: Boolean = true) {
        mode = Mode.SLEEP_FLOAT
        if (resetTimer) stateTimer = 0f
        velX = 0f
        velY = 0f
        bridge.animRotation = 0f
        bridge.animOffsetX = 0f
        bridge.animOffsetY = 0f
    }

    private fun startWalk() {
        mode = Mode.WALK
        stateTimer = 0f
        val params = bridge.getWindowParams() ?: return
        val minX = 20f
        val maxX = (bridge.screenWidth - bridge.petSpriteSize - 20).coerceAtLeast(20).toFloat()
        val goRight = params.x < (bridge.screenWidth - bridge.petSpriteSize) / 2f
        walkTargetX = if (goRight) maxX else minX
        walkDirection = if (goRight) 1f else -1f
        velX = walkDirection * getBaseSpeed()
        velY = 0f
    }

    override fun updateIdle(dt: Float) {
        if (isLoading || frames.isEmpty()) return
        time += dt
        stateTimer += dt

        when (mode) {
            Mode.SLEEP_FLOAT -> {
                bridge.currentFrame = 0
                bridge.animOffsetY = sin(time * 1.1f) * 12f
                bridge.animOffsetX = cos(time * 0.55f) * 5f
                bridge.animRotation = sin(time * 0.45f) * 2f
                bridge.animScaleY = 1f + sin(time * 0.9f) * 0.025f
                bridge.animScaleX = 1f - sin(time * 0.9f) * 0.01f
                velX = 0f
                velY = 0f

                if (stateTimer >= 4.5f) {
                    mode = Mode.WAKE_UP
                    stateTimer = 0f
                }
            }

            Mode.WAKE_UP -> {
                bridge.currentFrame = 1
                bridge.animOffsetY = sin(time * 1.2f) * 8f
                bridge.animOffsetX = 0f
                bridge.animRotation = 0f
                bridge.animScaleY = 1f + sin(time * 2.5f) * 0.02f
                bridge.animScaleX = 1f
                velX = 0f
                velY = 0f

                if (stateTimer >= 0.8f) {
                    mode = Mode.STAND_UP
                    stateTimer = 0f
                }
            }

            Mode.STAND_UP -> {
                bridge.currentFrame = 3
                bridge.animOffsetY = sin(time * 1.6f) * 4f
                bridge.animOffsetX = 0f
                bridge.animRotation = 0f
                bridge.animScaleY = 1f + sin(time * 2.2f) * 0.015f
                bridge.animScaleX = 1f
                velX = 0f
                velY = 0f

                if (stateTimer >= 0.7f) {
                    startWalk()
                }
            }

            Mode.WALK -> {
                val walkFrame = if (stateTimer < 0.42f) {
                    4
                } else {
                    if (((time * 7f).toInt() % 2) == 0) 6 else 7
                }
                bridge.currentFrame = walkFrame
                setFacing(walkDirection)
                bridge.animOffsetY = sin(time * 6f) * 2f
                bridge.animOffsetX = 0f
                bridge.animRotation = 0f
                bridge.animScaleY = 1f + sin(time * 8f) * 0.01f
                velY = 0f
                applyMovement(dt)

                val params = bridge.getWindowParams()
                val reached = params != null && abs(params.x - walkTargetX) <= 8f
                if (reached || stateTimer >= 3.8f) {
                    startSleepFloat()
                }
            }

            Mode.FEATHER_FALL,
            Mode.CLOUD_RETURN -> {
                // Se manejan dentro de updateInteracting.
            }
        }
    }

    override fun onInteract() {
        super.onInteract()
        mode = Mode.FEATHER_FALL
        stateTimer = 0f
        val params = bridge.getWindowParams()
        val currentX = params?.x ?: bridge.windowX
        walkDirection = if (currentX < bridge.screenWidth / 2) 1f else -1f
        returnTargetY = Random.nextInt(
            bridge.screenHeight / 6,
            bridge.screenHeight / 3
        ).toFloat()
        bridge.showBubble("☁")
        bridge.playHaptic(20)
        velX = 0f
        velY = 0f
    }

    override fun updateInteracting(dt: Float) {
        time += dt
        interactionTimer += dt
        stateTimer += dt

        val params = bridge.getWindowParams() ?: return
        val minX = 0
        val maxX = (bridge.screenWidth - bridge.petSpriteSize).coerceAtLeast(0)
        val minY = 50
        val maxY = (bridge.screenHeight - bridge.petSpriteSize - 100).coerceAtLeast(minY)

        when (mode) {
            Mode.FEATHER_FALL -> {
                bridge.currentFrame = 5
                bridge.animScaleX = 1f
                bridge.animScaleY = 1f
                bridge.animRotation = sin(time * 3.2f) * 18f
                bridge.animOffsetX = 0f
                bridge.animOffsetY = 0f

                val driftX = (sin(stateTimer * 2.4f) * 55f + walkDirection * 18f) * dt
                val fallY = (55f + abs(sin(stateTimer * 1.8f)) * 28f) * dt

                params.x = (params.x + driftX.roundToInt()).coerceIn(minX, maxX)
                params.y = (params.y + fallY.roundToInt().coerceAtLeast(1)).coerceIn(minY, maxY)
                bridge.updateWindowLayout(params)

                if (params.y >= maxY) {
                    mode = Mode.CLOUD_RETURN
                    stateTimer = 0f
                }
            }

            Mode.CLOUD_RETURN -> {
                bridge.currentFrame = 2
                bridge.animRotation = 0f
                bridge.animOffsetX = sin(stateTimer * 1.5f) * 6f
                bridge.animOffsetY = sin(stateTimer * 2.4f) * 4f
                bridge.animScaleX = 1f + sin(stateTimer * 2.1f) * 0.02f
                bridge.animScaleY = 1f + sin(stateTimer * 2.1f) * 0.04f

                val riseY = (72f * dt).roundToInt().coerceAtLeast(1)
                params.y = (params.y - riseY).coerceIn(minY, maxY)
                params.x = (params.x + (sin(stateTimer * 2f) * 4f).roundToInt()).coerceIn(minX, maxX)
                bridge.updateWindowLayout(params)

                if (params.y <= returnTargetY) {
                    bridge.state = PetState.IDLE
                    startSleepFloat()
                    reset()
                }
            }

            else -> {
                bridge.state = PetState.IDLE
                startSleepFloat()
                reset()
            }
        }
    }

    override fun updateDrag(dt: Float) {
        bridge.currentFrame = 0
        bridge.animRotation = 0f
        bridge.animScaleX = 1f
        bridge.animScaleY = 1f
        bridge.animOffsetX = 0f
        bridge.animOffsetY = 0f
    }

    override fun updateFalling(dt: Float) {
        bridge.state = PetState.IDLE
        startSleepFloat()
        reset()
    }

    override fun reset() {
        super.reset()
        if (bridge.state == PetState.IDLE) {
            bridge.animAlpha = 1f
        }
    }
}
