package com.pixelpals.app.feature.overlay.behavior

import com.pixelpals.app.core.domain.PetState
import com.pixelpals.app.core.motion.PetRandom
import kotlin.math.sin

/**
 * AtlasPetBehavior — Behavior genérico para pets premium con spritesheet 4x4.
 *
 * Cada pet define su personalidad con [Personality]: velocidad de paseo,
 * probabilidad de explorar, duración de clips y si es "pesado" (waddle lento)
 * o "ágil" (se mueve rápido). Los clips del atlas JSON (`idle`, `walk`,
 * `jump`, `happy`, `touch`, `sleep`) se usan según el estado.
 */
class AtlasPetBehavior(
    bridge: PetViewBridge,
    override val random: PetRandom,
    private val specAssetPath: String,
    private val personality: Personality,
) : BaseBehavior(bridge, random) {

    override val resourceIds: List<Int> = emptyList()

    /** Parámetros de personalidad por pet (movimiento y animación). */
    data class Personality(
        val baseSpeed: Float = 90f,
        val idleFrameMs: Int = 220,
        val walkFrameMs: Int = 220,
        val wanderChance: Float = 0.35f,   // prob de explorar en cada decisión
        val bobAmplitude: Float = 2.5f,    // vaivén vertical sutil (idle)
        val bobSpeed: Float = 1.6f,
        val touchFrameMs: Int = 260,
        val happyChance: Float = 0.18f,    // prob de hacer "happy" en idle
    )

    private enum class Mode { IDLE, WANDER, HAPPY, TOUCH, MELT }

    private var mode = Mode.IDLE
    private var modeTimer = 0f
    private var modeDuration = 2.5f
    private var animClock = 0f
    private var lastFrameBase = 0

    /** ¿Este pet se derrite con el calor? (solo Yuki por ahora). */
    private val meltsInHeat: Boolean = specAssetPath.contains("yuki")

    /** Temperatura de batería (°C) leída la última vez. -1 = desconocida. */
    private var batteryTempC: Float = -1f
    private var meltCooldown = 0f

    init {
        loadSpriteSheetAssetAsync(specAssetPath)
    }

    /** Lee la temperatura de la batería (°C) vía Intent sticky (API 21+, universal). */
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

    override fun getBaseSpeed(): Float = personality.baseSpeed

    override fun updateIdle(dt: Float) {
        if (isLoading || spriteSheetBitmap == null || spriteFrameRects.isEmpty()) return
        val step = dt.coerceIn(0f, 1f / 30f)
        time += step
        modeTimer += step
        animClock += step

        // Yuki se derrite con calor: lee la temperatura cada ~5s y entra/sale de MELT.
        meltCooldown -= step
        if (meltsInHeat && meltCooldown <= 0f) {
            meltCooldown = 5f
            batteryTempC = readBatteryTempC()
        }
        if (meltsInHeat && batteryTempC >= MELT_TEMP_C && mode != Mode.TOUCH) {
            if (mode != Mode.MELT) {
                mode = Mode.MELT
                modeTimer = 0f
                animClock = 0f
                velX = 0f
                velY = 0f
            }
        }

        when (mode) {
            Mode.IDLE -> updateIdleMode(step)
            Mode.WANDER -> updateWanderMode(step)
            Mode.HAPPY -> updateHappyMode(step)
            Mode.TOUCH -> updateTouchMode(step)
            Mode.MELT -> updateMeltMode(step)
        }
        syncWindowPosition()
    }

    private fun updateMeltMode(dt: Float) {
        val spec = spriteSheetSpec ?: return
        val clip = spec.clip("melt") ?: return
        val dur = 0.34f
        val idx = ((animClock / dur).toInt() % clip.frames.size)
        bridge.currentFrame = clip.frames[idx]
        bridge.animScaleY = 0.86f + sin(time * 2.4f) * 0.04f   // se hunde un poco
        bridge.animScaleX = 1.12f - sin(time * 2.4f) * 0.03f   // se ensancha
        bridge.animOffsetY = sin(time * 1.8f) * 2f
        bridge.animRotation = 0f
        // Si vuelve el frío, se recompone
        if (batteryTempC < MELT_TEMP_C) {
            mode = Mode.IDLE
            modeDuration = 2.0f + random.nextFloat() * 2.5f
            modeTimer = 0f
        }
    }

    private fun updateIdleMode(dt: Float) {
        // Respiración suave (squash/estirar muy sutil)
        bridge.animScaleY = 1f + sin(time * 2.2f) * 0.02f
        bridge.animScaleX = 1f - sin(time * 2.2f) * 0.015f
        bridge.animOffsetY = sin(time * personality.bobSpeed) * personality.bobAmplitude
        bridge.animRotation = sin(time * 1.3f) * 1.2f

        val spec = spriteSheetSpec ?: return
        val clip = spec.clip("idle") ?: return
        val dur = (personality.idleFrameMs / 1000f)
        val idx = ((animClock / dur).toInt() % clip.frames.size)
        bridge.currentFrame = clip.frames[idx]

        if (modeTimer >= modeDuration) {
            val roll = random.nextFloat()
            when {
                roll < personality.happyChance -> {
                    mode = Mode.HAPPY
                    modeDuration = 1.4f + random.nextFloat() * 1.2f
                    modeTimer = 0f
                }
                roll < personality.happyChance + personality.wanderChance -> {
                    mode = Mode.WANDER
                    modeDuration = 2.0f + random.nextFloat() * 2.5f
                    modeTimer = 0f
                    // elegir un destino lateral
                    val dir = if (random.nextFloat() < 0.5f) -1f else 1f
                    targetX = (bridge.windowX + dir * bridge.petSpriteSize * (2f + random.nextFloat() * 4f))
                        .coerceIn(0f, (bridge.screenWidth - bridge.petSpriteSize).coerceAtLeast(0).toFloat())
                    targetY = bridge.windowY.toFloat()
                    val dist = kotlin.math.abs(targetX - bridge.windowX).coerceAtLeast(1f)
                    val speed = getBaseSpeed()
                    velX = (targetX - bridge.windowX) / dist * speed
                    velY = 0f
                }
                else -> {
                    modeDuration = 2.0f + random.nextFloat() * 2.5f
                    modeTimer = 0f
                }
            }
        }
    }

    private fun updateWanderMode(dt: Float) {
        val spec = spriteSheetSpec ?: return
        val clip = spec.clip("walk") ?: return
        val dur = (personality.walkFrameMs / 1000f)
        val idx = ((animClock / dur).toInt() % clip.frames.size)
        bridge.currentFrame = clip.frames[idx]
        bridge.animRotation = 0f
        bridge.animScaleY = 1f
        bridge.animScaleX = 1f
        bridge.animOffsetY = 0f

        // avanzar hacia el destino
        val dx = targetX - bridge.windowX
        val stepMove = velX * dt
        if (kotlin.math.abs(dx) > 4f) {
            val params = bridge.getWindowParams() ?: return
            params.x += stepMove.toInt()
            params.x = params.x.coerceIn(0, (bridge.screenWidth - bridge.petSpriteSize).coerceAtLeast(0))
            bridge.updateWindowLayout(params)
        }
        if (modeTimer >= modeDuration || kotlin.math.abs(dx) <= 4f) {
            velX = 0f
            velY = 0f
            mode = Mode.IDLE
            modeDuration = 2.0f + random.nextFloat() * 2.5f
            modeTimer = 0f
        }
    }

    private fun updateHappyMode(dt: Float) {
        val spec = spriteSheetSpec ?: return
        val clip = spec.clip("happy") ?: return
        val dur = 0.24f
        val idx = ((animClock / dur).toInt() % clip.frames.size)
        bridge.currentFrame = clip.frames[idx]
        bridge.animScaleY = 1f + sin(time * 6f) * 0.05f
        bridge.animScaleX = 1f - sin(time * 6f) * 0.04f
        bridge.animOffsetY = sin(time * 4f) * 3f
        if (modeTimer >= modeDuration) {
            mode = Mode.IDLE
            modeDuration = 2.0f + random.nextFloat() * 2.5f
            modeTimer = 0f
        }
    }

    private fun updateTouchMode(dt: Float) {
        val spec = spriteSheetSpec ?: return
        val clip = spec.clip("touch") ?: return
        val dur = (personality.touchFrameMs / 1000f)
        val idx = ((animClock / dur).toInt() % clip.frames.size)
        bridge.currentFrame = clip.frames[idx]
        if (modeTimer >= modeDuration) {
            bridge.state = PetState.IDLE
            mode = Mode.IDLE
            modeDuration = 2.0f + random.nextFloat() * 2.5f
            modeTimer = 0f
            reset()
        }
    }

    override fun onInteract() {
        super.onInteract()
        mode = Mode.TOUCH
        modeDuration = 0.9f + random.nextFloat() * 0.6f
        modeTimer = 0f
        animClock = 0f
        velX = 0f
        velY = 0f
    }

    companion object {
        /** A partir de esta temperatura (°C) Yuki empieza a derretirse. */
        private const val MELT_TEMP_C = 36f
    }

    override fun updateInteracting(dt: Float) {
        // El modo TOUCH gestiona la duración; no forzar la salida de BaseBehavior.
    }

    private fun syncWindowPosition() {
        // No-op: el movimiento lo aplican updateWanderMode / BaseBehavior.applyMovement
        // según el modo. Mantener la ventana dentro de límites por seguridad.
        val params = bridge.getWindowParams() ?: return
        val minX = 0
        val maxX = (bridge.screenWidth - bridge.petSpriteSize).coerceAtLeast(0)
        val minY = 50
        val maxY = (bridge.screenHeight - bridge.petSpriteSize - 100).coerceAtLeast(minY)
        params.x = params.x.coerceIn(minX, maxX)
        params.y = params.y.coerceIn(minY, maxY)
        bridge.updateWindowLayout(params)
    }
}
