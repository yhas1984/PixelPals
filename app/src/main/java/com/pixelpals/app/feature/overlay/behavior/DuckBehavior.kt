package com.pixelpals.app.feature.overlay.behavior

import com.pixelpals.app.core.domain.PetState
import com.pixelpals.app.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import com.pixelpals.app.core.motion.PetRandom
import com.pixelpals.app.core.motion.GroundGait
import com.pixelpals.app.core.motion.DuckGait
import android.view.View

/**
 * Short duck flights with a planted landing; reviewed walk art is shared with home.
 */
class DuckBehavior(
    bridge: PetViewBridge,
    override val random: PetRandom
) : BaseBehavior(bridge, random) {

    override val resourceIds = listOf(R.drawable.patito_0, R.drawable.patito_1, R.drawable.patito_2, R.drawable.patito_3, R.drawable.patito_4, R.drawable.patito_5, R.drawable.patito_6, R.drawable.patito_7, R.drawable.patito_8, R.drawable.patito_9)

    private val hasWalkingArtwork = "patito_motion_v2.json" in
        (bridge as View).context.assets.list("pets/patito").orEmpty()
    override val preloadAllFrames: Boolean = true
    init {
        if (hasWalkingArtwork) loadSpriteSheetAssetAsync("pets/patito/patito_motion_v2.json")
        else loadFramesAsync()
        bridge.currentFrame = 8
    }

    override fun canStartScheduledSleep(reducedMotion: Boolean): Boolean =
        mode == DuckMode.QUACK || (reducedMotion && mode == DuckMode.WADDLE)

    private enum class DuckMode {
        WADDLE,
        TAKEOFF,
        FLUTTER,
        LANDING,
        LAND_END,
        QUACK
    }

    private var mode = DuckMode.WADDLE
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
    private var landingStartY = 0f
    private var landingDuration = .42f
    private var wingFlapCycles = 4
    private var groundedTakeoff = false

    private companion object {
        const val TAKEOFF_ANTICIPATION_SECONDS = .10f
        const val TAKEOFF_FLIGHT_SECONDS = .28f
    }

    private fun applyTakeoffEntryPose() {
        bridge.currentFrame = if (groundedTakeoff) 8 else 4
        bridge.animScaleX = facingScale(facingDir)
        bridge.animScaleY = 1f
        bridge.animOffsetX = 0f
        bridge.animOffsetY = 0f
        bridge.animRotation = 0f
    }

    override fun getBaseSpeed(): Float = 0f

    private fun facingScale(directionX: Float, stretch: Float = 1f): Float {
        val magnitude = abs(stretch)
        return if (directionX >= 0f) magnitude else -magnitude
    }

    private fun groundY(): Float = bridge.groundY.coerceAtLeast(bridge.bounds.top).toFloat()

    private fun startSwim(resetTimer: Boolean = true) {
        val params = bridge.getWindowParams() ?: return
        val minX = 0f
        val maxX = (bridge.screenWidth - bridge.petSpriteSize).coerceAtLeast(0).toFloat()
        val baseY = groundY()

        mode = DuckMode.WADDLE
        if (resetTimer) modeTimer = 0f

        swimStartX = params.x.toFloat().coerceIn(minX, maxX)
        swimStartY = baseY
        params.y = baseY.roundToInt()
        bridge.updateWindowLayout(params)

        swimTargetX = random.nextInt(minX.roundToInt(), maxX.roundToInt() + 1).toFloat()
        swimTargetY = baseY

        val dx = swimTargetX - swimStartX
        if (abs(dx) > 10f) facingDir = if (dx >= 0f) 1f else -1f

        val distance = abs(dx)
        swimDuration = GroundGait.duration(distance, 95f, 2.2f)
    }

    override fun updateIdle(dt: Float) {
        if (isLoading || (frames.isEmpty() && spriteFrameRects.isEmpty())) return
        time += dt
        modeTimer += dt

        when (mode) {
            DuckMode.WADDLE -> {
                val params = bridge.getWindowParams() ?: return
                if (swimDuration <= 0f) startSwim(resetTimer = false)

                val t = (modeTimer / swimDuration).coerceIn(0f, 1f)
                val easedT = GroundGait.progress(modeTimer, swimDuration)
                val x = swimStartX + (swimTargetX - swimStartX) * easedT
                val y = swimStartY

                params.x = x.roundToInt().coerceIn(bridge.bounds.left, bridge.bounds.right)
                params.y = y.roundToInt().coerceIn(bridge.bounds.top, bridge.bounds.floor)
                bridge.updateWindowLayout(params)

                val distance = abs(x - swimStartX)
                val gait = if (hasWalkingArtwork) DuckGait.phaseAt(distance, bridge.petSpriteSize.toFloat())
                    else distance / (bridge.petSpriteSize * .22f)
                val movement = sin(t * PI.toFloat())
                bridge.currentFrame = if (hasWalkingArtwork) {
                    if (t <= .025f || t >= .975f) 8 else 10 + DuckGait.cycleIndexAt(distance, bridge.petSpriteSize.toFloat())
                } else when (gait.toInt() % 4) {
                    0 -> 0
                    1 -> 1
                    2 -> 2
                    else -> 3
                }
                bridge.animScaleX = facingScale(facingDir)
                bridge.animScaleY = 1f
                bridge.animOffsetX = 0f
                val stepPhase = gait * PI.toFloat() * if (hasWalkingArtwork) 2f else 1f
                bridge.animOffsetY = -abs(sin(stepPhase)) * 2f * movement
                bridge.animRotation = facingDir * sin(stepPhase) * 1.5f * movement

                if (t >= 1f) {
                    mode = DuckMode.QUACK
                    modeTimer = 0f
                }
            }

            DuckMode.TAKEOFF -> {
                val params = bridge.getWindowParams() ?: return
                val anticipating = groundedTakeoff && modeTimer < TAKEOFF_ANTICIPATION_SECONDS
                val t = if (groundedTakeoff)
                    ((modeTimer - TAKEOFF_ANTICIPATION_SECONDS) / TAKEOFF_FLIGHT_SECONDS).coerceIn(0f, 1f)
                else (modeTimer / 0.38f).coerceIn(0f, 1f)
                val headroom = (flyStartY - bridge.bounds.top).coerceAtLeast(0f)
                val lift = minOf(bridge.petSpriteSize * .35f, headroom)
                params.y = (flyStartY - lift * t).roundToInt()
                    .coerceIn(bridge.bounds.top, bridge.bounds.floor)
                bridge.updateWindowLayout(params)

                bridge.currentFrame = if (anticipating) {
                    8
                } else if (t < 0.55f) {
                    4
                } else {
                    if ((((modeTimer - 0.20f) / 0.10f).toInt() % 2) == 0) 4 else 9
                }
                bridge.animScaleX = facingScale(facingDir)
                bridge.animScaleY = 1f
                bridge.animOffsetX = 0f
                bridge.animOffsetY = if (anticipating) 0f else (-sin((t * PI).toFloat()) * 5f)
                    .coerceAtLeast((bridge.bounds.top - params.y).toFloat())
                bridge.animRotation = if (anticipating) 0f else facingDir * (6f * t)

                if (t >= 1f) {
                    // Continue from the actual end of takeoff, not the ground origin.
                    flyStartY = params.y.toFloat()
                    mode = DuckMode.FLUTTER
                    modeTimer = 0f
                }
            }

            DuckMode.FLUTTER -> {
                val params = bridge.getWindowParams() ?: return
                val flyDuration = GroundGait.duration(
                    kotlin.math.hypot(flyTargetX - flyStartX, flyTargetY - flyStartY),
                    bridge.petSpriteSize * 2.5f, wingFlapCycles * 0.28f)
                val t = (modeTimer / flyDuration).coerceIn(0f, 1f)
                val progress = GroundGait.progress(modeTimer, flyDuration)
                val x = flyStartX + (flyTargetX - flyStartX) * progress
                val headroom = (minOf(flyStartY, flyTargetY) - bridge.bounds.top).coerceAtLeast(0f)
                val arcHeight = minOf(bridge.petSpriteSize * .18f, headroom)
                val y = flyStartY + (flyTargetY - flyStartY) * progress - sin((t * PI).toFloat()) * arcHeight

                params.x = x.roundToInt().coerceIn(bridge.bounds.left, bridge.bounds.right)
                params.y = y.roundToInt().coerceIn(bridge.bounds.top, bridge.bounds.floor)
                bridge.updateWindowLayout(params)

                // 8 is standing on two feet; 4/9 are the matching up/down wing poses.
                bridge.currentFrame = if (((modeTimer / 0.12f).toInt() % 2) == 0) 4 else 9
                bridge.animScaleX = facingScale(facingDir)
                bridge.animScaleY = 1f
                bridge.animOffsetX = 0f
                bridge.animOffsetY = (sin(modeTimer * 12f) * 2f * sin(t * PI.toFloat()))
                    .coerceIn((bridge.bounds.top - params.y).toFloat(), (bridge.bounds.floor - params.y).toFloat())
                // Carry takeoff pitch into flight, then settle before landing.
                val settle = (t / .2f).coerceIn(0f, 1f)
                val pitch = 6f * (1f - settle * settle * (3f - 2f * settle))
                bridge.animRotation = facingDir * (pitch + sin(modeTimer * 6f) * 4f * sin(t * PI.toFloat()))

                if (t >= 1f) {
                    startLanding()
                }
            }

            DuckMode.LANDING -> {
                val params = bridge.getWindowParams() ?: return
                val t = (modeTimer / landingDuration).coerceIn(0f, 1f)
                val progress = GroundGait.progress(modeTimer, landingDuration)
                params.y = (landingStartY + (landingTargetY - landingStartY) * progress).roundToInt()
                    .coerceIn(bridge.bounds.top, bridge.bounds.floor)
                bridge.updateWindowLayout(params)

                bridge.currentFrame = if (t < .70f) {
                    if ((modeTimer / .16f).toInt() % 2 == 0) 4 else 9
                } else 6
                bridge.animScaleX = facingScale(facingDir)
                bridge.animScaleY = 1f
                bridge.animOffsetX = 0f
                bridge.animOffsetY = 0f
                bridge.animRotation = facingDir * 4f * sin(t * PI.toFloat())

                if (t >= 1f) {
                    mode = DuckMode.LAND_END
                    modeTimer = 0f
                }
            }

            DuckMode.LAND_END -> {
                bridge.currentFrame = if (modeTimer < .12f) 7 else 8
                bridge.animScaleX = facingScale(facingDir)
                bridge.animScaleY = 1f
                bridge.animOffsetX = 0f
                bridge.animOffsetY = 0f
                bridge.animRotation = 0f

                if (modeTimer >= 0.48f) {
                    mode = DuckMode.QUACK
                    modeTimer = 0f
                }
            }

            DuckMode.QUACK -> {
                bridge.currentFrame = 8
                bridge.animScaleX = facingScale(facingDir)
                bridge.animScaleY = 1f
                bridge.animOffsetX = 0f
                bridge.animOffsetY = 0f
                bridge.animRotation = 0f
                if (modeTimer >= 0.45f) startSwim()
            }
        }

        if (random.nextFloat() < 0.0004f) {
            bridge.showBubble(localizedString(R.string.bubble_duck_quack, "quack"))
        }
    }

    override fun onInteract() {
        super.onInteract()
        val params = bridge.getWindowParams() ?: return
        val minX = 0f
        val maxX = (bridge.screenWidth - bridge.petSpriteSize).coerceAtLeast(0).toFloat()
        val minY = (groundY() - bridge.petSpriteSize * 0.45f).coerceAtLeast(bridge.bounds.top.toFloat())
        val maxY = groundY()

        flyStartX = params.x.toFloat()
        flyStartY = params.y.toFloat()
        groundedTakeoff = flyStartY >= groundY() - 1f

        val flyDistanceX = bridge.petSpriteSize * (1.4f + random.nextFloat() * 1.4f)
        // Con jetpack/alas, el patito vuela más alto en cada batida.
        val flyDistanceY = bridge.petSpriteSize * (0.35f + random.nextFloat() * 0.18f)
        val goRight = if (flyStartX < bridge.screenWidth * 0.5f) random.nextFloat() > 0.2f else random.nextFloat() > 0.8f
        facingDir = if (goRight) 1f else -1f

        flyTargetX = (flyStartX + if (goRight) flyDistanceX else -flyDistanceX).coerceIn(minX, maxX)
        flyTargetY = (flyStartY - flyDistanceY).coerceIn(minY, maxY)
        landingTargetY = groundY()
        wingFlapCycles = random.nextInt(2, 4)

        mode = DuckMode.TAKEOFF
        modeTimer = 0f
        applyTakeoffEntryPose()
        bridge.showBubble(localizedString(R.string.bubble_duck_quack_excited, "quack!"))
    }

    override fun onFling(velocityX: Float, velocityY: Float) {
        super.onInteract()
        facingDir = if (velocityX >= 0f) 1f else -1f
        mode = DuckMode.TAKEOFF
        modeTimer = 0f
        flyStartX = bridge.windowX.toFloat()
        flyStartY = bridge.windowY.toFloat()
        groundedTakeoff = flyStartY >= groundY() - 1f
        applyTakeoffEntryPose()
        flyTargetX = (flyStartX + velocityX * 0.08f).coerceIn(0f, (bridge.screenWidth - bridge.petSpriteSize).coerceAtLeast(0).toFloat())
        flyTargetY = (flyStartY - abs(velocityY) * 0.02f).coerceIn((groundY() - bridge.petSpriteSize * 0.5f).coerceAtLeast(bridge.bounds.top.toFloat()), groundY())
        landingTargetY = groundY()
    }

    private fun startLanding() {
        landingStartY = bridge.windowY.toFloat()
        landingTargetY = groundY()
        landingDuration = GroundGait.duration(landingTargetY - landingStartY,
            bridge.petSpriteSize * 2.5f, .42f)
        mode = DuckMode.LANDING
        modeTimer = 0f
    }

    override fun updateDrag(dt: Float) {
        super.updateDrag(dt)
        facingDir = if (bridge.animScaleX < 0f) -1f else 1f
        // A duck being held spreads its wings; do not retain water or a seated pose.
        bridge.currentFrame = 9
    }

    override fun onRelease(velocityX: Float, velocityY: Float) {
        super.onInteract()
        // PetView retains ownership of the drag; this bird owns the controlled descent.
        startLanding()
    }

    override fun updateInteracting(dt: Float) {
        updateIdle(dt)
        if (mode == DuckMode.WADDLE || mode == DuckMode.QUACK) {
            bridge.state = PetState.IDLE
        }
    }

    override fun reset() {
        super.reset()
        bridge.animScaleX = facingScale(facingDir)
        if (bridge.windowY < groundY() - 1f) {
            startLanding()
            // Finish a cancelled drag even when autonomous motion is reduced.
            bridge.state = PetState.INTERACTING
        }
        else {
            mode = DuckMode.QUACK
            modeTimer = 0f
            bridge.currentFrame = 8
        }
    }
}
