package com.pixelpals.app.behavior

import com.pixelpals.app.PetState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * DuckBehavior — Patito nadador que, al tocarlo, sale del agua,
 * vuela un momento y vuelve a aterrizar para seguir nadando.
 */
class DuckBehavior(
    bridge: PetViewBridge
) : BaseBehavior(bridge) {

    override val resourceIds = listOf(0, 1, 2, 3, 4, 5, 6, 7, 9).map { i ->
        (bridge as android.view.View).context.resources.getIdentifier(
            "patito_$i", "drawable", (bridge as android.view.View).context.packageName
        )
    }

    private enum class DuckMode {
        SWIM,
        TAKEOFF,
        FLY,
        LANDING,
        LAND_END
    }

    private var mode = DuckMode.SWIM
    private var modeTimer = 0f

    private var facingDir = 1f
    private var swimStartX = 0f
    private var swimStartY = 0f
    private var swimTargetX = 0f
    private var swimTargetY = 0f
    private var swimDuration = 0f

    private var flyStartX = 0f
    private var flyStartY = 0f
    private var flyTargetX = 0f
    private var flyTargetY = 0f
    private var landingTargetY = 0f
    private var wingFlapCycles = 4

    override fun getBaseSpeed(): Float = 0f

    private fun facingScale(directionX: Float, stretch: Float = 1f): Float {
        val magnitude = abs(stretch)
        return if (directionX >= 0f) magnitude else -magnitude
    }

    private fun startSwim(resetTimer: Boolean = true) {
        val params = bridge.getWindowParams() ?: return
        val minX = 0f
        val maxX = (bridge.screenWidth - bridge.petSpriteSize).coerceAtLeast(0).toFloat()
        val minY = (bridge.screenHeight * 0.18f).roundToInt().toFloat()
        val maxY = (bridge.screenHeight - bridge.petSpriteSize - 120).coerceAtLeast(minY.roundToInt()).toFloat()

        mode = DuckMode.SWIM
        if (resetTimer) modeTimer = 0f

        swimStartX = params.x.toFloat().coerceIn(minX, maxX)
        swimStartY = params.y.toFloat().coerceIn(minY, maxY)

        swimTargetX = Random.nextInt(minX.roundToInt(), maxX.roundToInt() + 1).toFloat()
        swimTargetY = Random.nextInt(minY.roundToInt(), maxY.roundToInt() + 1).toFloat()

        val dx = swimTargetX - swimStartX
        if (abs(dx) > 10f) facingDir = if (dx >= 0f) 1f else -1f

        val distance = kotlin.math.sqrt(dx * dx + (swimTargetY - swimStartY) * (swimTargetY - swimStartY))
        swimDuration = (distance / 95f).coerceIn(2.2f, 5.6f)
    }

    override fun updateIdle(dt: Float) {
        if (isLoading || frames.isEmpty()) return
        time += dt
        modeTimer += dt

        when (mode) {
            DuckMode.SWIM -> {
                val params = bridge.getWindowParams() ?: return
                if (swimDuration <= 0f) startSwim(resetTimer = false)

                val t = (modeTimer / swimDuration).coerceIn(0f, 1f)
                val easedT = sin((t * PI).toFloat() / 2f)
                val x = swimStartX + (swimTargetX - swimStartX) * easedT
                val y = swimStartY + (swimTargetY - swimStartY) * easedT

                params.x = x.roundToInt()
                params.y = y.roundToInt()
                bridge.updateWindowLayout(params)

                bridge.currentFrame = when (((time * 5.5f).toInt() % 4)) {
                    0 -> 0
                    1 -> 1
                    2 -> 2
                    else -> 3
                }
                bridge.animScaleX = facingScale(facingDir)
                bridge.animScaleY = 1f + sin(time * 4.4f) * 0.02f
                bridge.animOffsetX = 0f
                bridge.animOffsetY = sin(time * 8.5f) * 3f
                bridge.animRotation = sin(time * 3.4f) * 2f

                if (t >= 1f) startSwim()
            }

            DuckMode.TAKEOFF -> {
                val params = bridge.getWindowParams() ?: return
                val t = (modeTimer / 0.38f).coerceIn(0f, 1f)
                params.y = (flyStartY - bridge.petSpriteSize * 0.35f * t).roundToInt()
                bridge.updateWindowLayout(params)

                bridge.currentFrame = if (t < 0.55f) {
                    4
                } else {
                    if ((((modeTimer - 0.20f) / 0.10f).toInt() % 2) == 0) 5 else 8
                }
                bridge.animScaleX = facingScale(facingDir)
                bridge.animScaleY = 1f + t * 0.05f
                bridge.animOffsetX = 0f
                bridge.animOffsetY = -sin((t * PI).toFloat()) * 5f
                bridge.animRotation = facingDir * (6f * t)

                if (t >= 1f) {
                    mode = DuckMode.FLY
                    modeTimer = 0f
                }
            }

            DuckMode.FLY -> {
                val params = bridge.getWindowParams() ?: return
                val flyDuration = wingFlapCycles * 0.24f
                val t = (modeTimer / flyDuration).coerceIn(0f, 1f)
                val x = flyStartX + (flyTargetX - flyStartX) * t
                val y = flyStartY + (flyTargetY - flyStartY) * t - sin((t * PI).toFloat()) * bridge.petSpriteSize * 0.28f

                params.x = x.roundToInt()
                params.y = y.roundToInt()
                bridge.updateWindowLayout(params)

                bridge.currentFrame = if (((modeTimer / 0.12f).toInt() % 2) == 0) 5 else 8
                bridge.animScaleX = facingScale(facingDir)
                bridge.animScaleY = 1f + sin(time * 10f) * 0.03f
                bridge.animOffsetX = 0f
                bridge.animOffsetY = sin(time * 12f) * 2f
                bridge.animRotation = facingDir * sin(time * 6f) * 4f

                if (t >= 1f) {
                    mode = DuckMode.LANDING
                    modeTimer = 0f
                }
            }

            DuckMode.LANDING -> {
                val params = bridge.getWindowParams() ?: return
                val t = (modeTimer / 0.42f).coerceIn(0f, 1f)
                params.y = (flyTargetY + (landingTargetY - flyTargetY) * t).roundToInt()
                bridge.updateWindowLayout(params)

                bridge.currentFrame = 6
                bridge.animScaleX = facingScale(facingDir)
                bridge.animScaleY = 1f - t * 0.06f
                bridge.animOffsetX = 0f
                bridge.animOffsetY = 0f
                bridge.animRotation = facingDir * (4f * (1f - t))

                if (t >= 1f) {
                    mode = DuckMode.LAND_END
                    modeTimer = 0f
                }
            }

            DuckMode.LAND_END -> {
                bridge.currentFrame = 7
                bridge.animScaleX = facingScale(facingDir)
                bridge.animScaleY = 1f + sin(time * 6f) * 0.015f
                bridge.animOffsetX = 0f
                bridge.animOffsetY = sin(time * 5f) * 1.5f
                bridge.animRotation = 0f

                if (modeTimer >= 0.48f) {
                    startSwim()
                }
            }
        }

        if (Random.nextFloat() < 0.0018f) {
            bridge.showBubble("cuac")
        }
    }

    override fun onInteract() {
        super.onInteract()
        val params = bridge.getWindowParams() ?: return
        val minX = 0f
        val maxX = (bridge.screenWidth - bridge.petSpriteSize).coerceAtLeast(0).toFloat()
        val minY = 40f
        val maxY = (bridge.screenHeight - bridge.petSpriteSize - 100).coerceAtLeast(minY.roundToInt()).toFloat()

        flyStartX = params.x.toFloat()
        flyStartY = params.y.toFloat()

        val flyDistanceX = bridge.petSpriteSize * (1.4f + Random.nextFloat() * 1.4f)
        val flyDistanceY = bridge.petSpriteSize * (0.9f + Random.nextFloat() * 0.7f)
        val goRight = if (flyStartX < bridge.screenWidth * 0.5f) Random.nextFloat() > 0.2f else Random.nextFloat() > 0.8f
        facingDir = if (goRight) 1f else -1f

        flyTargetX = (flyStartX + if (goRight) flyDistanceX else -flyDistanceX).coerceIn(minX, maxX)
        flyTargetY = (flyStartY - flyDistanceY).coerceIn(minY, maxY)
        landingTargetY = (flyStartY + bridge.petSpriteSize * 0.18f).coerceIn(minY, maxY)
        wingFlapCycles = Random.nextInt(4, 7)

        mode = DuckMode.TAKEOFF
        modeTimer = 0f
        bridge.showBubble("cuac!")
    }

    override fun updateInteracting(dt: Float) {
        updateIdle(dt)
        if (mode == DuckMode.SWIM) {
            bridge.state = PetState.IDLE
        }
    }

    override fun reset() {
        super.reset()
        startSwim()
    }
}
