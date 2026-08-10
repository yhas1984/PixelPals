package com.pixelpals.app.feature.overlay.behavior

import com.pixelpals.app.core.domain.PetState
import com.pixelpals.app.R
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import com.pixelpals.app.core.motion.PetRandom

/**
 * NubeMichiBehavior — Gatito nube. ES una nube con forma de gato:
 * siempre flota, nunca toca el suelo. Duerme flotando, despierta,
 * navega con el viento y deriva por una banda celeste; al tocarlo
 * se vuelve pluma antes de condensarse de nuevo y subir como nube.
 *
 * Frames:
 *   0 dormido flotando, 1 despierta, 2 nube esponjosa con rizos,
 *   3 se estira (micro-transición), 4/6/7 "viento" (micro-transiciones
 *   de deriva), 5 pluma, 8 puff alargado (flotación libre),
 *   9 cometa subiendo (estela), 10 ráfaga de viento.
 */
class NubeMichiBehavior(
    bridge: PetViewBridge,
    override val random: PetRandom
) : BaseBehavior(bridge, random) {

    override val resourceIds = listOf(
        R.drawable.gato_0,  // dormido flotando
        R.drawable.gato_1,  // despierta
        R.drawable.gato_2,  // nube esponjosa subiendo
        R.drawable.gato_3,  // se estira (transición)
        R.drawable.gato_4,  // deriva con viento (transición)
        R.drawable.gato_5,  // pluma
        R.drawable.gato_6,  // deriva con viento (transición)
        R.drawable.gato_7,  // deriva con viento (transición)
        R.drawable.gato_8,  // puff alargado — flotación libre
        R.drawable.gato_9,  // cometa subiendo (estela)
        R.drawable.gato_10, // ráfaga de viento
    )

    init {
        loadFramesAsync()
    }

    private enum class Mode {
        SLEEP_FLOAT,
        WAKE_UP,
        WIND_GUST,
        PUFF_FLOAT,
        FEATHER_FALL,
        CLOUD_RETURN,
    }

    private var mode = Mode.SLEEP_FLOAT
    private var stateTimer = 0f
    private var walkDirection = 1f
    private var driftSpeedX = 0f
    private var returnTargetY = 0f
    private var bandMinY = 0
    private var bandMaxY = 0
    private var gustTimer = 0f

    override fun getBaseSpeed(): Float = 52f

    /** Banda celeste donde flota la nube: 18%–42% de la pantalla. */
    private fun refreshCloudBand() {
        bandMinY = (bridge.screenHeight * 0.18f).toInt()
        bandMaxY = (bridge.screenHeight * 0.42f).toInt().coerceAtLeast(bandMinY)
    }

    private fun clampToBand(y: Int): Int = y.coerceIn(bandMinY, bandMaxY)

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
        refreshCloudBand()
        val params = bridge.getWindowParams() ?: return
        params.y = clampToBand(params.y)
        bridge.updateWindowLayout(params)
    }

    override fun updateIdle(dt: Float) {
        if (isLoading || frames.isEmpty()) return
        time += dt
        stateTimer += dt
        refreshCloudBand()

        when (mode) {
            Mode.SLEEP_FLOAT -> {
                // La nube duerme flotando con vaivén suave y etéreo.
                bridge.currentFrame = 0
                bridge.animOffsetY = sin(time * 1.1f) * 12f
                bridge.animOffsetX = cos(time * 0.55f) * 5f
                bridge.animRotation = sin(time * 0.45f) * 2f
                bridge.animScaleY = 1f + sin(time * 0.9f) * 0.025f
                bridge.animScaleX = 1f - sin(time * 0.9f) * 0.01f
                velX = 0f
                velY = 0f

                when {
                    // A veces una ráfaga la arrastra suavemente.
                    stateTimer >= 6.5f -> {
                        mode = Mode.WIND_GUST
                        stateTimer = 0f
                        gustTimer = 1.8f + random.nextFloat() * 1.4f
                        driftSpeedX = getBaseSpeed() *
                            if (random.nextBoolean()) 1f else -1f
                    }
                    // Y a veces despierta y se pone a derivar.
                    stateTimer >= 4.5f -> {
                        mode = Mode.WAKE_UP
                        stateTimer = 0f
                    }
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
                    mode = Mode.PUFF_FLOAT
                    stateTimer = 0f
                    // Deriva en la banda celeste.
                    walkDirection = if (random.nextBoolean()) 1f else -1f
                    driftSpeedX = walkDirection * getBaseSpeed() * 0.62f
                }
            }

            Mode.WIND_GUST -> {
                // La ráfaga la arrastra de lado, con micro-poses de viento.
                val gustFrame = when ((time * 8f).toInt() % 3) {
                    0 -> 4
                    1 -> 6
                    else -> 7
                }
                bridge.currentFrame = gustFrame
                setFacing(driftSpeedX)
                bridge.animOffsetY = sin(time * 3.2f) * 7f
                bridge.animOffsetX = sin(time * 1.3f) * 4f
                bridge.animRotation = sin(time * 1.8f) * 5f * (if (driftSpeedX >= 0f) 1f else -1f)
                bridge.animScaleY = 1f + sin(time * 4f) * 0.02f

                val params = bridge.getWindowParams() ?: return
                params.x = (params.x + (driftSpeedX * dt).roundToInt())
                    .coerceIn(0, (bridge.screenWidth - bridge.petSpriteSize).coerceAtLeast(0))
                params.y = clampToBand(params.y)
                bridge.updateWindowLayout(params)

                gustTimer -= dt
                if (gustTimer <= 0f) {
                    startSleepFloat()
                }
            }

            Mode.PUFF_FLOAT -> {
                // Flotación libre: nube con rizos (frame 2) o puff alargado (8),
                // con micro-transiciones de viento (4/6/7) cada ~0.9s.
                bridge.currentFrame = if (((time / 0.9f).toInt() % 4) == 0) 8 else 2
                setFacing(driftSpeedX)
                bridge.animOffsetY = sin(time * 1.6f) * 9f
                bridge.animOffsetX = sin(time * 0.9f) * 6f
                bridge.animRotation = sin(time * 0.7f) * 3.5f
                bridge.animScaleY = 1f + sin(time * 1.1f) * 0.03f
                bridge.animScaleX = 1f - sin(time * 1.1f) * 0.015f

                val params = bridge.getWindowParams() ?: return
                val minX = 0
                val maxX = (bridge.screenWidth - bridge.petSpriteSize).coerceAtLeast(0)
                var nextX = params.x + (driftSpeedX * dt).roundToInt()
                if (nextX <= minX || nextX >= maxX) {
                    driftSpeedX *= -1f
                    setFacing(driftSpeedX)
                    nextX = nextX.coerceIn(minX, maxX)
                }
                params.x = nextX
                params.y = clampToBand(params.y)
                bridge.updateWindowLayout(params)

                if (stateTimer >= 4.5f + random.nextFloat() * 2.5f) {
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
        refreshCloudBand()
        returnTargetY = random.nextInt(bandMinY, bandMaxY + 1).toFloat()
        bridge.showBubble("☁")
        bridge.playHaptic(20)
        velX = 0f
        velY = 0f
    }

    override fun onFling(velocityX: Float, velocityY: Float) {
        super.onInteract()
        mode = Mode.CLOUD_RETURN
        stateTimer = 0f
        refreshCloudBand()
        returnTargetY = (bandMinY + (bandMaxY - bandMinY) * 0.3f)
        walkDirection = if (velocityX >= 0f) 1f else -1f
        bridge.animScaleX = 1.08f
        bridge.animScaleY = 1.08f
        bridge.animRotation = velocityX * 0.01f
        bridge.animOffsetY = -abs(velocityY) * 0.01f
    }

    override fun updateInteracting(dt: Float) {
        time += dt
        interactionTimer += dt
        stateTimer += dt

        val params = bridge.getWindowParams() ?: return
        val minX = 0
        val maxX = safeMaxX()
        val minY = safeMinY()
        val maxY = safeMaxY()

        when (mode) {
            Mode.FEATHER_FALL -> {
                // La pluma-nube cae con balanceo y drift; nunca en línea recta.
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
                // Se condensa y sube como cometa de nube (frame 9) hasta la banda celeste.
                bridge.currentFrame = 9
                bridge.animRotation = sin(stateTimer * 1.4f) * 3f
                bridge.animOffsetX = sin(stateTimer * 1.5f) * 6f
                bridge.animOffsetY = sin(stateTimer * 2.4f) * 4f
                bridge.animScaleX = 1f + sin(stateTimer * 2.1f) * 0.02f
                bridge.animScaleY = 1f + sin(stateTimer * 2.1f) * 0.04f

                val riseY = (130f * dt).roundToInt().coerceAtLeast(1)
                params.y = (params.y - riseY).coerceIn(minY, maxY)
                params.x = (params.x + (sin(stateTimer * 2f) * 4f).roundToInt()).coerceIn(minX, maxX)
                bridge.updateWindowLayout(params)

                refreshCloudBand()
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
            startSleepFloat()
        }
    }
}
