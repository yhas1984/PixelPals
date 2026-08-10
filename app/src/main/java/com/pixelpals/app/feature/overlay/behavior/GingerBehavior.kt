package com.pixelpals.app.feature.overlay.behavior

import com.pixelpals.app.core.domain.PetState
import com.pixelpals.app.core.motion.PetRandom
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/** Grounded feline movement for the redesigned Ginger atlas. */
class GingerBehavior(
    bridge: PetViewBridge,
    override val random: PetRandom,
) : BaseBehavior(bridge, random) {
    override val resourceIds: List<Int> = emptyList()

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
    }

    private var mode: Mode = Mode.SIT
    private var modeTimer: Float = 0f
    private var modeDuration: Float = 1.6f
    private var facingDirection: Float = -1f
    private var moveStartX: Float = 0f
    private var moveTargetX: Float = 0f
    private var airX: Float = 0f
    private var airY: Float = 0f
    private var airVelocityX: Float = 0f
    private var airVelocityY: Float = 0f

    init {
        loadSpriteSheetAssetAsync(ATLAS_SPEC_PATH)
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
        }
    }

    private fun updateSit() {
        placeOnGround()
        bridge.currentFrame = FRAME_SIT
        applyFacing()
        bridge.animScaleY = 1f + sin(time * 1.8f) * 0.018f
        bridge.animOffsetY = sin(time * 1.3f) * 1.5f
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
        bridge.currentFrame = FRAME_SLEEP
        applyFacing()
        val breath: Float = sin(time * 1.45f)
        bridge.animScaleY = 1f + breath * 0.025f
        bridge.animScaleX = facingScale(1f - breath * 0.012f)
        bridge.animOffsetY = 0f
        bridge.animRotation = 0f
        if (modeTimer >= modeDuration) changeMode(Mode.WAKE, 0.84f)
    }

    private fun updateWake() {
        placeOnGround()
        bridge.currentFrame = when {
            modeTimer < 0.24f -> FRAME_SLEEP
            modeTimer < 0.66f -> FRAME_STRETCH
            else -> FRAME_SIT
        }
        applyFacing()
        bridge.animScaleY = 1f
        bridge.animOffsetY = 0f
        bridge.animRotation = 0f
        if (modeTimer >= modeDuration) startWalk()
    }

    private fun updateWalk() {
        val params = bridge.getWindowParams() ?: return
        val progress: Float = (modeTimer / modeDuration).coerceIn(0f, 1f)
        val eased: Float = progress * progress * (3f - 2f * progress)
        params.x = (moveStartX + (moveTargetX - moveStartX) * eased).roundToInt()
            .coerceIn(0, maxWindowX())
        params.y = groundY().roundToInt()
        bridge.updateWindowLayout(params)

        bridge.currentFrame = FRAME_WALK_START + ((modeTimer / WALK_FRAME_SECONDS).toInt() % 4)
        applyFacing()
        bridge.animScaleY = 1f
        bridge.animOffsetY = -abs(sin(modeTimer * PI.toFloat() / WALK_FRAME_SECONDS)) * 1.8f
        bridge.animRotation = facingDirection * sin(modeTimer * 5.2f) * 1.2f
        if (progress >= 1f) startSit(0.8f + random.nextFloat() * 0.8f)
    }

    private fun updateStalk() {
        val params = bridge.getWindowParams() ?: return
        val progress: Float = (modeTimer / modeDuration).coerceIn(0f, 1f)
        params.x = (moveStartX + (moveTargetX - moveStartX) * progress).roundToInt()
            .coerceIn(0, maxWindowX())
        params.y = groundY().roundToInt()
        bridge.updateWindowLayout(params)

        bridge.currentFrame = FRAME_STALK_START + ((modeTimer / STALK_FRAME_SECONDS).toInt() % 3)
        applyFacing()
        bridge.animScaleY = 1f
        bridge.animOffsetY = 0f
        bridge.animRotation = 0f
        if (progress >= 1f) changeMode(Mode.POUNCE_COIL, POUNCE_COIL_SECONDS)
    }

    private fun updatePounceCoil() {
        placeOnGround()
        bridge.currentFrame = FRAME_POUNCE_COIL
        applyFacing()
        val compression: Float = (modeTimer / modeDuration).coerceIn(0f, 1f)
        bridge.animScaleY = 1f - compression * 0.07f
        bridge.animScaleX = facingScale(1f + compression * 0.05f)
        bridge.animOffsetY = compression * 3f
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
        val impact: Float = sin((modeTimer / LAND_SECONDS).coerceIn(0f, 1f) * PI).toFloat()
        bridge.animScaleY = 0.92f + impact * 0.08f
        bridge.animScaleX = facingScale(1.07f - impact * 0.07f)
        bridge.animOffsetY = (1f - impact) * 3f
        bridge.animRotation = 0f
        if (modeTimer >= modeDuration) startSit(1.0f)
    }

    private fun updateTouch() {
        bridge.currentFrame = FRAME_TOUCH
        applyFacing()
        bridge.animScaleY = 1f + sin(modeTimer * 10f) * 0.025f
        bridge.animOffsetY = -abs(sin(modeTimer * 9f)) * 2f
        bridge.animRotation = sin(modeTimer * 8f) * 2f
    }

    private fun chooseGroundAction() {
        when (val roll: Float = random.nextFloat()) {
            in 0f..<0.48f -> startWalk()
            in 0.48f..<0.68f -> changeMode(Mode.GROOM, 1.35f)
            in 0.68f..<0.84f -> changeMode(Mode.SLEEP, 3.8f + random.nextFloat() * 2.8f)
            else -> startStalk()
        }
    }

    private fun startWalk() {
        val params = bridge.getWindowParams() ?: return
        val maxX: Float = maxWindowX().toFloat()
        moveStartX = params.x.toFloat().coerceIn(0f, maxX)
        val preferredDirection: Float = if (moveStartX < maxX * 0.5f) 1f else -1f
        facingDirection = if (random.nextFloat() < 0.78f) preferredDirection else -preferredDirection
        val distance: Float = bridge.petSpriteSize * (1.1f + random.nextFloat() * 1.9f)
        moveTargetX = (moveStartX + facingDirection * distance).coerceIn(0f, maxX)
        if (abs(moveTargetX - moveStartX) < bridge.petSpriteSize * 0.45f) {
            facingDirection *= -1f
            moveTargetX = (moveStartX + facingDirection * distance).coerceIn(0f, maxX)
        }
        val duration: Float = (abs(moveTargetX - moveStartX) / (bridge.petSpriteSize * 0.56f))
            .coerceIn(1.4f, 4.8f) / moodSpeedMultiplier()
        changeMode(Mode.WALK, duration)
    }

    private fun startStalk() {
        val params = bridge.getWindowParams() ?: return
        val maxX: Float = maxWindowX().toFloat()
        moveStartX = params.x.toFloat().coerceIn(0f, maxX)
        facingDirection = if (moveStartX < maxX * 0.5f) 1f else -1f
        val distance: Float = bridge.petSpriteSize * (0.8f + random.nextFloat() * 0.75f)
        moveTargetX = (moveStartX + facingDirection * distance).coerceIn(0f, maxX)
        val duration: Float = (abs(moveTargetX - moveStartX) / (bridge.petSpriteSize * 0.28f))
            .coerceIn(1.4f, 3.2f)
        changeMode(Mode.STALK, duration)
    }

    private fun startAirborne(velocityX: Float, velocityY: Float) {
        val params = bridge.getWindowParams() ?: return
        mode = Mode.AIRBORNE
        modeTimer = 0f
        modeDuration = Float.POSITIVE_INFINITY
        airX = params.x.toFloat()
        airY = params.y.toFloat()
        airVelocityX = velocityX.coerceIn(-bridge.petSpriteSize * 4f, bridge.petSpriteSize * 4f)
        airVelocityY = velocityY.coerceIn(-bridge.petSpriteSize * 4f, bridge.petSpriteSize * 2f)
        if (abs(airVelocityX) > 1f) facingDirection = if (airVelocityX >= 0f) 1f else -1f
    }

    private fun startSit(duration: Float = 1.6f + random.nextFloat() * 1.4f) {
        changeMode(Mode.SIT, duration)
        placeOnGround()
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
        val magnitude: Float = abs(stretch)
        return if (facingDirection < 0f) magnitude else -magnitude
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
        super.onInteract()
        val params = bridge.getWindowParams()
        facingDirection = if ((params?.x ?: bridge.windowX) < maxWindowX() / 2f) -1f else 1f
        changeMode(Mode.TOUCH, TOUCH_SECONDS)
        bridge.showBubble("prrr")
        bridge.playHaptic(24)
    }

    override fun updateInteracting(dt: Float) {
        if (isLoading || spriteSheetBitmap == null || spriteFrameRects.isEmpty()) return
        time += dt
        modeTimer += dt
        updateTouch()
        if (modeTimer >= TOUCH_SECONDS) {
            bridge.state = PetState.IDLE
            changeMode(Mode.POUNCE_COIL, POUNCE_COIL_SECONDS)
        }
    }

    override fun updateDrag(dt: Float) {
        bridge.currentFrame = FRAME_TOUCH
        bridge.animScaleX = facingScale()
        bridge.animScaleY = 1f
        bridge.animRotation = 0f
        bridge.animOffsetX = 0f
        bridge.animOffsetY = 0f
    }

    override fun onFling(velocityX: Float, velocityY: Float) {
        bridge.state = PetState.IDLE
        startAirborne(velocityX * 0.34f, velocityY * 0.34f)
        bridge.showBubble("mrrp")
    }

    override fun reset() {
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
        const val WALK_FRAME_SECONDS: Float = 0.135f
        const val STALK_FRAME_SECONDS: Float = 0.19f
        const val POUNCE_COIL_SECONDS: Float = 0.22f
        const val LAND_SECONDS: Float = 0.32f
        const val TOUCH_SECONDS: Float = 0.55f
        const val TOP_LIMIT_PX: Float = 50f
    }
}
