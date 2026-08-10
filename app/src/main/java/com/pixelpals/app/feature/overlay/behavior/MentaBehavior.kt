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

    override fun getBaseSpeed(): Float = 55f

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

    /** Crucero por toda la pantalla: va de un lado al otro, subiendo/bajando un poco. */
    private fun pickTarget() {
        val params = bridge.getWindowParams() ?: return
        startX = params.x.toFloat()
        startY = params.y.toFloat()
        val minX = 0f
        val maxX = (bridge.screenWidth - bridge.petSpriteSize).coerceAtLeast(0).toFloat()
        val minY = 80f
        val maxY = (bridge.screenHeight - bridge.petSpriteSize - 80).coerceAtLeast(80).toFloat()
        // La serpiente CRUZA de un lado al otro de la pantalla (nunca hacia el
        // centro sin sentido): si está a la izquierda va a la derecha y viceversa.
        val far = if (startX < (minX + maxX) / 2f) maxX else minX
        cruiseTargetX = far
        // Sube o baja un poco entre cruces para recorrer todo el alto con el tiempo
        if (random.nextFloat() < 0.6f) {
            cruiseTargetY = random.nextFloat() * (maxY - minY) + minY
        } else {
            cruiseTargetY = if (startY < (minY + maxY) / 2f) maxY else minY
        }
        val dx = cruiseTargetX - startX
        val dy = cruiseTargetY - startY
        val dist = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        modeDuration = (dist / getBaseSpeed()).coerceIn(3.0f, 12.0f)
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
        bridge.animOffsetX = sin(time * 5f) * 3f
        bridge.animOffsetY = sin(time * 3.5f) * 2f
        bridge.animRotation = sin(time * 4f) * 6f
        bridge.animScaleX = if (facingRight) 1f else -1f
        bridge.animScaleY = 1f + sin(time * 4.5f) * 0.03f

        // Alterna frames de slither con velocidad proporcional al movimiento
        val speedFactor = (abs(cruiseTargetX - startX) / modeDuration) / 55f
        val frameRate = (2.5f + speedFactor * 6f)
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
        time += dt
        modeTimer += dt
        animClock += dt
        if (mode == Mode.TOUCH) updateTouch(dt)
    }

    override fun updateDrag(dt: Float) {
        // Al arrastrar, la serpiente se queda en pose de slither (no se congela).
        time += dt
        bridge.currentFrame = if (((time * 4f).toInt() % 2) == 0) 4 else 5
        bridge.animOffsetX = sin(time * 5f) * 2f
        bridge.animScaleX = if (facingRight) 1f else -1f
        bridge.animScaleY = 1f
        bridge.animRotation = 0f
    }

    override fun reset() {
        super.reset()
        // Al soltar, reanuda el cruce de pantalla al momento.
        modeTimer = 0f
        animClock = 0f
        mode = Mode.SLITHER
        pickTarget()
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
