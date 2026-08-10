package com.pixelpals.app.feature.overlay.behavior

import com.pixelpals.app.core.domain.PetState
import com.pixelpals.app.core.motion.PetRandom
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * TaroBehavior — Tortuguita de jardín.
 * La más pausada de todas: pasea por el suelo muy lentamente, se queda
 * quieta mucho rato (respirando), y al tocarla se ESCONDE en el caparazón
 * con el clip "hide" antes de asomar la cabeza con curiosidad.
 * IA atlas: idle (0-3), walk (4-5), hide (6-7), happy (8-11), touch (12-14), sleep (15).
 */
class TaroBehavior(
    bridge: PetViewBridge,
    override val random: PetRandom,
) : BaseBehavior(bridge, random) {

    override val resourceIds: List<Int> = emptyList()

    private enum class Mode { IDLE, WALK, HIDE, HAPPY, TOUCH, SLEEP }

    private var mode = Mode.IDLE
    private var modeTimer = 0f
    private var modeDuration = 4f
    private var animClock = 0f
    private var walkStartX = 0f
    private var walkTargetX = 0f
    private var walkDuration = 0f
    private var facingDir = 1f

    init {
        loadSpriteSheetAssetAsync("pets/taro/taro_sheet_v1.json")
    }

    override fun getBaseSpeed(): Float = 0f

    private fun groundY(): Float = bridge.groundY.coerceAtLeast(60).toFloat()

    private fun facingScale(directionX: Float): Float = if (directionX >= 0f) 1f else -1f

    private fun startWalk(resetTimer: Boolean = true) {
        val params = bridge.getWindowParams() ?: return
        val minX = 0f
        val maxX = (bridge.screenWidth - bridge.petSpriteSize).coerceAtLeast(0).toFloat()
        val baseY = groundY()

        mode = Mode.WALK
        if (resetTimer) modeTimer = 0f
        walkStartX = params.x.toFloat().coerceIn(minX, maxX)
        params.y = baseY.roundToInt()
        bridge.updateWindowLayout(params)

        walkTargetX = random.nextFloat() * (maxX - minX) + minX
        val dx = walkTargetX - walkStartX
        if (abs(dx) > 10f) facingDir = if (dx >= 0f) 1f else -1f
        // MUY lenta: velocidad ~40px/s
        walkDuration = (abs(dx) / 40f).coerceIn(3.0f, 9.0f)
    }

    override fun updateIdle(dt: Float) {
        if (isLoading || spriteSheetBitmap == null || spriteFrameRects.isEmpty()) return
        val step = dt.coerceIn(0f, 1f / 30f)
        time += step
        modeTimer += step
        animClock += step

        when (mode) {
            Mode.IDLE -> updateIdleMode(step)
            Mode.WALK -> updateWalk(step)
            Mode.HIDE -> updateHide(step)
            Mode.HAPPY -> updateHappy(step)
            Mode.TOUCH -> updateTouch(step)
            Mode.SLEEP -> updateSleep(step)
        }
        syncWindowPosition()
    }

    private fun updateIdleMode(dt: Float) {
        val spec = spriteSheetSpec ?: return
        val clip = spec.clip("idle") ?: return
        val idx = ((animClock / 0.38f).toInt() % clip.frames.size)
        bridge.currentFrame = clip.frames[idx]
        bridge.animScaleX = facingScale(facingDir)
        bridge.animScaleY = 1f + sin(time * 1.6f) * 0.015f
        bridge.animOffsetY = sin(time * 1.2f) * 1.5f
        bridge.animRotation = 0f

        if (modeTimer >= modeDuration) {
            mode = if (random.nextFloat() < 0.30f) Mode.WALK else Mode.IDLE
            modeTimer = 0f
            if (mode == Mode.WALK) startWalk(resetTimer = false)
            else modeDuration = 4f + random.nextFloat() * 4f
        }
    }

    private fun updateWalk(dt: Float) {
        val params = bridge.getWindowParams() ?: return
        if (walkDuration <= 0f) startWalk(resetTimer = false)

        val t = (modeTimer / walkDuration).coerceIn(0f, 1f)
        val easedT = sin((t * PI).toFloat() / 2f)
        val x = walkStartX + (walkTargetX - walkStartX) * easedT
        val y = groundY()

        params.x = x.roundToInt()
        params.y = y.roundToInt()
        bridge.updateWindowLayout(params)

        val spec = spriteSheetSpec ?: return
        val clip = spec.clip("walk") ?: return
        val idx = ((animClock / 0.42f).toInt() % clip.frames.size)
        bridge.currentFrame = clip.frames[idx]
        bridge.animScaleX = facingScale(facingDir)
        bridge.animScaleY = 1f + sin(time * 2.2f) * 0.015f
        bridge.animOffsetY = abs(sin(time * 3.5f)) * 1f
        bridge.animRotation = 0f

        if (random.nextFloat() < 0.0002f) {
            mode = Mode.HAPPY
            modeTimer = 0f
            modeDuration = 1.4f + random.nextFloat() * 1.2f
        } else if (t >= 1f) {
            mode = Mode.IDLE
            modeTimer = 0f
            modeDuration = 5f + random.nextFloat() * 5f
        }
    }

    private fun updateHide(dt: Float) {
        val spec = spriteSheetSpec ?: return
        val clip = spec.clip("hide") ?: return
        val idx = ((animClock / 0.32f).toInt() % clip.frames.size)
        bridge.currentFrame = clip.frames[idx]
        bridge.animScaleX = facingScale(facingDir)
        bridge.animScaleY = 1f + sin(time * 3f) * 0.02f
        bridge.animOffsetY = 0f
        if (modeTimer >= modeDuration) {
            mode = Mode.IDLE
            modeTimer = 0f
            modeDuration = 3f + random.nextFloat() * 3f
        }
    }

    private fun updateHappy(dt: Float) {
        if (modeTimer >= modeDuration) {
            mode = Mode.IDLE
            modeTimer = 0f
            modeDuration = 4f + random.nextFloat() * 4f
            return
        }
        val spec = spriteSheetSpec ?: return
        val clip = spec.clip("happy") ?: return
        val idx = ((animClock / 0.25f).toInt() % clip.frames.size)
        bridge.currentFrame = clip.frames[idx]
        bridge.animScaleX = facingScale(facingDir)
        bridge.animScaleY = 1f + sin(time * 5f) * 0.04f
        bridge.animOffsetY = sin(time * 3.5f) * 2.5f
    }

    private fun updateTouch(dt: Float) {
        if (modeTimer >= modeDuration) {
            bridge.state = PetState.IDLE
            animClock = 0f
            mode = Mode.HIDE
            modeTimer = 0f
            modeDuration = 2.2f + random.nextFloat() * 1.5f
            return
        }
        val spec = spriteSheetSpec ?: return
        val clip = spec.clip("touch") ?: return
        val idx = ((animClock / 0.30f).toInt() % clip.frames.size)
        bridge.currentFrame = clip.frames[idx]
        // Se encoge visiblemente al esconderse
        bridge.animScaleY = 0.94f
        bridge.animScaleX = 1.05f
    }

    private fun updateSleep(dt: Float) {
        val spec = spriteSheetSpec ?: return
        val clip = spec.clip("sleep") ?: return
        bridge.currentFrame = clip.frames[0]
        bridge.animOffsetY = sin(time * 1.0f) * 1.2f
        if (modeTimer >= modeDuration) {
            mode = Mode.IDLE
            modeTimer = 0f
            modeDuration = 4f + random.nextFloat() * 4f
        }
    }

    override fun onInteract() {
        super.onInteract()
        mode = Mode.TOUCH
        modeDuration = 1.0f + random.nextFloat() * 0.7f
        modeTimer = 0f
        animClock = 0f
    }

    override fun updateInteracting(dt: Float) {
        time += dt
        modeTimer += dt
        animClock += dt
        if (mode == Mode.TOUCH) updateTouch(dt)
    }

    override fun updateDrag(dt: Float) {
        // Al arrastrar, la tortuga mantiene pose idle (no se congela).
        time += dt
        val spec = spriteSheetSpec ?: return
        val clip = spec.clip("idle") ?: return
        val idx = ((time / 0.38f).toInt() % clip.frames.size)
        bridge.currentFrame = clip.frames[idx]
        bridge.animScaleX = facingScale(facingDir)
        bridge.animScaleY = 1f
        bridge.animRotation = 0f
    }

    override fun reset() {
        super.reset()
        // Al soltar, reanuda su paseo lentísimo al momento.
        modeTimer = 0f
        animClock = 0f
        mode = Mode.WALK
        startWalk(resetTimer = false)
    }

    private fun syncWindowPosition() {
        val params = bridge.getWindowParams() ?: return
        params.x = params.x.coerceIn(0, safeMaxX())
        params.y = params.y.coerceIn(safeMinY(), safeMaxY())
        bridge.updateWindowLayout(params)
    }
}
