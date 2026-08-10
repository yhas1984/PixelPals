package com.pixelpals.app.feature.overlay.behavior

import com.pixelpals.app.core.domain.PetState
import com.pixelpals.app.core.motion.PetRandom
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * YukiBehavior — Muñeco de nieve.
 * Camina por el suelo con andares pausados de nieve (resbalando un poco),
 * y se DERRITE cuando el dispositivo supera [MELT_TEMP_C]: se hunde,
 * se ensancha y tiembla con el clip "melt". Al enfriarse se recompone.
 * IA atlas: idle (0-3), walk (4-5), jump (6-7), happy (8-11), melt (12-14), sleep (15).
 */
class YukiBehavior(
    bridge: PetViewBridge,
    override val random: PetRandom,
) : BaseBehavior(bridge, random) {

    override val resourceIds: List<Int> = emptyList()

    private enum class Mode { WALK, IDLE, HAPPY, MELT, TOUCH, SLEEP }

    private var mode = Mode.IDLE
    private var modeTimer = 0f
    private var modeDuration = 2.5f
    private var animClock = 0f
    private var walkStartX = 0f
    private var walkTargetX = 0f
    private var walkDuration = 0f
    private var facingDir = 1f

    private var batteryTempC: Float = -1f
    private var tempReadCooldown = 0f

    init {
        loadSpriteSheetAssetAsync("pets/yuki/yuki_sheet_v1.json")
    }

    override fun getBaseSpeed(): Float = 0f

    private fun groundY(): Float = bridge.groundY.coerceAtLeast(60).toFloat()

    private fun facingScale(directionX: Float): Float = if (directionX >= 0f) 1f else -1f

    private fun readBatteryTempC(): Float {
        return try {
            val intent = (bridge as android.view.View).context
                .registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            val raw = intent?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
            if (raw == Int.MIN_VALUE) -1f else raw / 10f
        } catch (_: Exception) {
            -1f
        }
    }

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
        walkDuration = (abs(dx) / 70f).coerceIn(2.4f, 6.5f) // lento: nieve
    }

    override fun updateIdle(dt: Float) {
        if (isLoading || spriteSheetBitmap == null || spriteFrameRects.isEmpty()) return
        val step = dt.coerceIn(0f, 1f / 30f)
        time += step
        modeTimer += step
        animClock += step

        // Temperatura: leída cada 4s; si hace calor, Yuki se derrite.
        tempReadCooldown -= step
        if (tempReadCooldown <= 0f) {
            tempReadCooldown = 4f
            batteryTempC = readBatteryTempC()
        }
        if (batteryTempC >= MELT_TEMP_C && mode != Mode.TOUCH && mode != Mode.MELT) {
            mode = Mode.MELT
            modeTimer = 0f
            animClock = 0f
            return
        }

        when (mode) {
            Mode.WALK -> updateWalk(step)
            Mode.IDLE -> updateIdleMode(step)
            Mode.HAPPY -> updateHappy(step)
            Mode.MELT -> updateMelt(step)
            Mode.TOUCH -> updateTouch(step)
            Mode.SLEEP -> updateSleep(step)
        }
        syncWindowPosition()
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
        val idx = ((animClock / 0.28f).toInt() % clip.frames.size)
        bridge.currentFrame = clip.frames[idx]
        bridge.animScaleX = facingScale(facingDir)
        bridge.animScaleY = 1f + sin(time * 3.4f) * 0.02f
        bridge.animOffsetY = abs(sin(time * 6.5f)) * 1.5f
        bridge.animRotation = facingDir * sin(time * 5f) * 1.2f

        if (random.nextFloat() < 0.0003f) {
            mode = Mode.HAPPY
            modeTimer = 0f
            modeDuration = 1.3f + random.nextFloat() * 1.0f
        } else if (t >= 1f) {
            mode = Mode.IDLE
            modeTimer = 0f
            modeDuration = 2.0f + random.nextFloat() * 2.5f
        }
    }

    private fun updateIdleMode(dt: Float) {
        val spec = spriteSheetSpec ?: return
        val clip = spec.clip("idle") ?: return
        val idx = ((animClock / 0.24f).toInt() % clip.frames.size)
        bridge.currentFrame = clip.frames[idx]
        bridge.animScaleX = facingScale(facingDir)
        bridge.animScaleY = 1f + sin(time * 2.2f) * 0.02f
        bridge.animOffsetY = sin(time * 1.6f) * 2f
        bridge.animRotation = sin(time * 1.3f) * 1.2f

        // Yuki no permanece inmóvil: de vez en cuando se anima solo, saluda
        // y muestra una reacción breve como un personaje vivo.
        if (modeTimer > 0.9f && random.nextFloat() < 0.0045f) {
            mode = Mode.HAPPY
            modeTimer = 0f
            modeDuration = 1.4f + random.nextFloat() * 1.0f
            bridge.showBubble(listOf("❄️", "brrr!", "☃️", "✨").random())
            bridge.playHaptic(18)
            return
        }

        if (modeTimer >= modeDuration) {
            mode = if (random.nextFloat() < 0.72f) Mode.WALK else Mode.IDLE
            modeTimer = 0f
            if (mode == Mode.WALK) startWalk(resetTimer = false)
            else modeDuration = 2.0f + random.nextFloat() * 2.5f
        }
    }

    private fun updateHappy(dt: Float) {
        val spec = spriteSheetSpec ?: return
        val clip = spec.clip("happy") ?: return
        val idx = ((animClock / 0.24f).toInt() % clip.frames.size)
        bridge.currentFrame = clip.frames[idx]
        bridge.animScaleX = facingScale(facingDir)
        bridge.animScaleY = 1f + sin(time * 6f) * 0.05f
        bridge.animOffsetY = sin(time * 4f) * 3f
        if (modeTimer >= modeDuration) {
            mode = Mode.IDLE
            modeTimer = 0f
            modeDuration = 2.0f + random.nextFloat() * 2.5f
        }
    }

    private fun updateMelt(dt: Float) {
        val params = bridge.getWindowParams() ?: return
        // Se hunde: baja lentamente hacia el suelo y se encoge
        val spec = spriteSheetSpec ?: return
        val clip = spec.clip("melt") ?: return
        val idx = ((animClock / 0.34f).toInt() % clip.frames.size)
        bridge.currentFrame = clip.frames[idx]
        bridge.animScaleY = 0.82f + sin(time * 2.6f) * 0.05f
        bridge.animScaleX = 1.16f - sin(time * 2.6f) * 0.04f
        bridge.animOffsetY = sin(time * 1.8f) * 3f
        bridge.animRotation = sin(time * 2.2f) * 2f

        // Derretido: no camina; se queda en el sitio temblando
        if (batteryTempC < MELT_TEMP_C) {
            mode = Mode.IDLE
            modeTimer = 0f
            modeDuration = 2.0f + random.nextFloat() * 2.5f
            bridge.animScaleX = 1f
            bridge.animScaleY = 1f
        }
    }

    private fun updateTouch(dt: Float) {
        val spec = spriteSheetSpec ?: return
        val clip = spec.clip("touch") ?: return
        val idx = ((animClock / 0.30f).toInt() % clip.frames.size)
        bridge.currentFrame = clip.frames[idx]
        if (modeTimer >= modeDuration) {
            bridge.state = PetState.IDLE
            animClock = 0f
            mode = Mode.IDLE
            modeTimer = 0f
            modeDuration = 2.0f + random.nextFloat() * 2.5f
            reset()
        }
    }

    private fun updateSleep(dt: Float) {
        val spec = spriteSheetSpec ?: return
        val clip = spec.clip("sleep") ?: return
        bridge.currentFrame = clip.frames[0]
        bridge.animOffsetY = sin(time * 1.2f) * 1.5f
        if (modeTimer >= modeDuration) {
            mode = Mode.IDLE
            modeTimer = 0f
            modeDuration = 2.0f + random.nextFloat() * 2.5f
        }
    }

    override fun onInteract() {
        super.onInteract()
        bridge.showBubble(listOf("brrr!", "☃️", "¡frío!", "✨").random())
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
        // Al arrastrar, el muñeco de nieve mantiene pose idle (no se congela).
        time += dt
        val spec = spriteSheetSpec ?: return
        val clip = spec.clip("idle") ?: return
        val idx = ((time / 0.24f).toInt() % clip.frames.size)
        bridge.currentFrame = clip.frames[idx]
        bridge.animScaleX = facingScale(facingDir)
        bridge.animScaleY = 1f
        bridge.animRotation = 0f
    }

    override fun reset() {
        super.reset()
        // Al soltar, reanuda su paseo al momento.
        modeTimer = 0f
        animClock = 0f
        mode = Mode.WALK
        startWalk(resetTimer = false)
    }

    private fun syncWindowPosition() {
        val params = bridge.getWindowParams() ?: return
        val minX = 0
        val maxX = (bridge.screenWidth - bridge.petSpriteSize).coerceAtLeast(0)
        val minY = 50
        val maxY = (bridge.screenHeight - bridge.petSpriteSize - 100).coerceAtLeast(minY)
        params.x = params.x.coerceIn(minX, maxX)
        params.y = params.y.coerceIn(minY, maxY)
        bridge.updateWindowLayout(params)
    }

    private companion object {
        /** A partir de 40 °C de batería el dispositivo está realmente caliente. */
        const val MELT_TEMP_C = 40f
    }
}
