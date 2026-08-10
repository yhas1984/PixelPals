package com.pixelpals.app.feature.overlay.behavior

import com.pixelpals.app.core.domain.PetState
import com.pixelpals.app.core.motion.PetRandom
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * MentaBehavior — Serpientita de menta.
 * Se desliza por TODA la pantalla con ondulación real: cruza el suelo,
 * sube por los bordes laterales, hace slalom en diagonal y se enrosca.
 * IA atlas: idle (0-3), slither (4-5), stretch (6-7), happy (8-11), touch (12-14), sleep (15).
 */
class MentaBehavior(
    bridge: PetViewBridge,
    override val random: PetRandom,
) : BaseBehavior(bridge, random) {

    override val resourceIds: List<Int> = emptyList()

    private enum class Mode { SLITHER, COIL, HAPPY, TOUCH, SLEEP }

    private var mode = Mode.SLITHER
    private var modeTimer = 0f
    private var modeDuration = 3f
    private var animClock = 0f
    private var startX = 0f
    private var startY = 0f
    private var cruiseTargetX = 0f
    private var cruiseTargetY = 0f
    private var facingRight = true

    init {
        loadSpriteSheetAssetAsync("pets/menta/menta_sheet_v1.json")
    }

    override fun getBaseSpeed(): Float = 130f

    override fun updateIdle(dt: Float) {
        if (isLoading || spriteSheetBitmap == null || spriteFrameRects.isEmpty()) return
        val step = dt.coerceIn(0f, 1f / 30f)
        time += step
        modeTimer += step
        animClock += step

        when (mode) {
            Mode.SLITHER -> updateSlither(step)
            Mode.COIL -> updateCoil(step)
            Mode.HAPPY -> updateHappy(step)
            Mode.TOUCH -> updateTouch(step)
            Mode.SLEEP -> updateSleep(step)
        }
        syncWindowPosition()
    }

    /** Crucero por toda la pantalla: elige un punto lejano (incluso en altura). */
    private fun pickTarget() {
        val params = bridge.getWindowParams() ?: return
        startX = params.x.toFloat()
        startY = params.y.toFloat()
        val minX = 0f
        val maxX = (bridge.screenWidth - bridge.petSpriteSize).coerceAtLeast(0).toFloat()
        val minY = 60f
        val maxY = (bridge.screenHeight - bridge.petSpriteSize - 80).coerceAtLeast(60).toFloat()
        // Tendencia a cruzar: 60% objetivo lejano horizontal, 40% vertical.
        if (random.nextFloat() < 0.6f) {
            val far = if (startX < maxX / 2f) maxX else minX
            cruiseTargetX = far
            cruiseTargetY = random.nextFloat() * (maxY - minY) + minY
        } else {
            cruiseTargetX = random.nextFloat() * (maxX - minX) + minX
            cruiseTargetY = if (startY < maxY / 2f) maxY else minY
        }
        val dx = cruiseTargetX - startX
        val dy = cruiseTargetY - startY
        val dist = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        modeDuration = (dist / getBaseSpeed()).coerceIn(1.6f, 6f)
        facingRight = dx >= 0f
    }

    private fun updateSlither(dt: Float) {
        val params = bridge.getWindowParams() ?: return
        val t = (modeTimer / modeDuration).coerceIn(0f, 1f)
        if (t >= 1f) {
            mode = if (random.nextFloat() < 0.22f) Mode.COIL else Mode.SLITHER
            modeTimer = 0f
            pickTarget()
            return
        }
        val eased = sin((t * 3.14159f) / 2f)
        val x = startX + (cruiseTargetX - startX) * eased
        val y = startY + (cruiseTargetY - startY) * eased
        params.x = x.roundToInt()
        params.y = y.roundToInt()
        bridge.updateWindowLayout(params)

        // Ondulación: la serpiente serpentea mientras avanza
        bridge.animOffsetX = sin(time * 9f) * 3f
        bridge.animOffsetY = sin(time * 6f) * 2f
        bridge.animRotation = sin(time * 7f) * 6f
        bridge.animScaleX = if (facingRight) 1f else -1f
        bridge.animScaleY = 1f + sin(time * 8f) * 0.03f

        // Alterna frames de slither con velocidad proporcional al movimiento
        val speedFactor = (abs(cruiseTargetX - startX) / modeDuration) / 130f
        val frameRate = (4f + speedFactor * 10f)
        bridge.currentFrame = if (((time * frameRate).toInt() % 2) == 0) 4 else 5

        if (random.nextFloat() < 0.00035f) {
            mode = Mode.HAPPY
            modeTimer = 0f
            modeDuration = 1.2f + random.nextFloat() * 1.0f
        }
    }

    private fun updateCoil(dt: Float) {
        val spec = spriteSheetSpec ?: return
        val clip = spec.clip("stretch") ?: return
        val idx = ((animClock / 0.30f).toInt() % clip.frames.size)
        bridge.currentFrame = clip.frames[idx]
        bridge.animOffsetX = 0f
        bridge.animOffsetY = sin(time * 2.2f) * 2f
        bridge.animRotation = 0f
        bridge.animScaleX = 1f
        bridge.animScaleY = 1f + sin(time * 2.8f) * 0.03f
        if (modeTimer >= modeDuration) {
            mode = Mode.SLITHER
            modeTimer = 0f
            pickTarget()
        }
    }

    private fun updateHappy(dt: Float) {
        val spec = spriteSheetSpec ?: return
        val clip = spec.clip("happy") ?: return
        val idx = ((animClock / 0.24f).toInt() % clip.frames.size)
        bridge.currentFrame = clip.frames[idx]
        bridge.animScaleY = 1f + sin(time * 6f) * 0.05f
        bridge.animScaleX = 1f - sin(time * 6f) * 0.04f
        bridge.animOffsetY = sin(time * 4f) * 3f
        if (modeTimer >= modeDuration) {
            mode = Mode.SLITHER
            modeTimer = 0f
            pickTarget()
        }
    }

    private fun updateTouch(dt: Float) {
        val spec = spriteSheetSpec ?: return
        val clip = spec.clip("touch") ?: return
        val idx = ((animClock / 0.26f).toInt() % clip.frames.size)
        bridge.currentFrame = clip.frames[idx]
        if (modeTimer >= modeDuration) {
            bridge.state = PetState.IDLE
            mode = Mode.SLITHER
            modeTimer = 0f
            animClock = 0f
            pickTarget()
            reset()
        }
    }

    private fun updateSleep(dt: Float) {
        val spec = spriteSheetSpec ?: return
        val clip = spec.clip("sleep") ?: return
        bridge.currentFrame = clip.frames[0]
        bridge.animOffsetY = sin(time * 1.2f) * 1.5f
        if (modeTimer >= modeDuration) {
            mode = Mode.SLITHER
            modeTimer = 0f
            pickTarget()
        }
    }

    override fun onInteract() {
        super.onInteract()
        mode = Mode.TOUCH
        modeDuration = 0.9f + random.nextFloat() * 0.6f
        modeTimer = 0f
        animClock = 0f
    }

    override fun updateInteracting(dt: Float) {
        // TOUCH gestiona su propia salida
    }

    private fun syncWindowPosition() {
        val params = bridge.getWindowParams() ?: return
        val minX = 0
        val maxX = (bridge.screenWidth - bridge.petSpriteSize).coerceAtLeast(0)
        val minY = 60
        val maxY = (bridge.screenHeight - bridge.petSpriteSize - 80).coerceAtLeast(minY)
        params.x = params.x.coerceIn(minX, maxX)
        params.y = params.y.coerceIn(minY, maxY)
        bridge.updateWindowLayout(params)
    }
}
