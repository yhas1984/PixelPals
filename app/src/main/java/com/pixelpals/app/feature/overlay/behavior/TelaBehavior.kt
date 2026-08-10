package com.pixelpals.app.feature.overlay.behavior

import com.pixelpals.app.core.domain.PetState
import com.pixelpals.app.core.motion.PetRandom
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * TelaBehavior — Arañita de menta-lavanda.
 * Se comporta como una araña de verdad:
 *  - TREPA por los bordes laterales (sube y baja por las paredes),
 *  - CAMINA por el techo (parte superior de la pantalla, patas arriba),
 *  - SE CUELGA de un hilo y se balancea,
 *  - Recorre el perímetro de la pantalla entera.
 * IA atlas: idle colgando (0-3), walk (4-5), climb (6-7), happy (8-11), touch (12-14), sleep (15).
 */
class TelaBehavior(
    bridge: PetViewBridge,
    override val random: PetRandom,
) : BaseBehavior(bridge, random) {

    override val resourceIds: List<Int> = emptyList()

    private enum class Mode { HANG, WALK, CLIMB, CEILING, HAPPY, TOUCH, SLEEP }

    private var mode = Mode.HANG
    private var modeTimer = 0f
    private var modeDuration = 2.5f
    private var animClock = 0f
    private var fromX = 0f
    private var fromY = 0f
    private var toX = 0f
    private var toY = 0f
    private var facingRight = true

    init {
        loadSpriteSheetAssetAsync("pets/tela/tela_sheet_v1.json")
    }

    override fun getBaseSpeed(): Float = 0f

    private fun minX() = 0f
    private fun maxX() = (bridge.screenWidth - bridge.petSpriteSize).coerceAtLeast(0).toFloat()
    private fun minY() = 60f
    private fun maxY() = (bridge.screenHeight - bridge.petSpriteSize - 60).coerceAtLeast(60).toFloat()

    private fun startMode(m: Mode, duration: Float, fromX: Float, fromY: Float, toX: Float, toY: Float) {
        mode = m
        modeTimer = 0f
        modeDuration = duration
        this.fromX = fromX
        this.fromY = fromY
        this.toX = toX
        this.toY = toY
        facingRight = toX >= fromX
    }

    /** Decide la siguiente acción según la posición actual (comportamiento de araña). */
    private fun decideNext() {
        val params = bridge.getWindowParams() ?: return
        val x = params.x.toFloat()
        val y = params.y.toFloat()
        val roll = random.nextFloat()

        when {
            // En el techo: baja por un borde
            y <= minY() + 40f -> {
                val sideX = if (roll < 0.5f) minX() else maxX()
                startMode(Mode.CLIMB, 1.6f + random.nextFloat() * 1.4f, x, y, sideX, maxY() * 0.5f)
            }
            // En un borde lateral: sube al techo o baja al suelo
            x <= minX() + 20f || x >= maxX() - 20f -> {
                if (roll < 0.5f) {
                    startMode(Mode.CLIMB, 1.4f + random.nextFloat() * 1.6f, x, y, x, minY() + 20f)
                } else {
                    startMode(Mode.CLIMB, 1.2f + random.nextFloat() * 1.4f, x, y, x, maxY() - 20f)
                }
            }
            // En el suelo: trepa por la pared más cercana
            y >= maxY() - 40f -> {
                val sideX = if (x < bridge.screenWidth / 2f) minX() else maxX()
                startMode(Mode.CLIMB, 1.5f + random.nextFloat() * 1.5f, x, y, sideX, y * 0.5f)
            }
            // En el aire (colgando): se balancea, camina por el techo o baja
            else -> {
                when {
                    roll < 0.30f -> {
                        // Caminar por el techo (patas arriba)
                        val ceilingY = minY() + 10f
                        startMode(Mode.CEILING, 1.8f + random.nextFloat() * 1.6f, x, y, random.nextFloat() * (maxX() - minX()) + minX(), ceilingY)
                    }
                    roll < 0.55f -> {
                        // Colgarse y balancearse en el sitio
                        startMode(Mode.HANG, 1.6f + random.nextFloat() * 1.4f, x, y, x, y)
                    }
                    roll < 0.80f -> {
                        // Cruzar en diagonal (lanzarse con hilo)
                        val targetX = random.nextFloat() * (maxX() - minX()) + minX()
                        val targetY = random.nextFloat() * (maxY() - minY()) + minY()
                        startMode(Mode.WALK, 1.8f + random.nextFloat() * 1.6f, x, y, targetX, targetY)
                    }
                    else -> {
                        // Bajar al suelo
                        startMode(Mode.CLIMB, 1.3f + random.nextFloat() * 1.3f, x, y, x, maxY() - 20f)
                    }
                }
            }
        }
    }

    override fun updateIdle(dt: Float) {
        if (isLoading || spriteSheetBitmap == null || spriteFrameRects.isEmpty()) return
        val step = dt.coerceIn(0f, 1f / 30f)
        time += step
        modeTimer += step
        animClock += step

        when (mode) {
            Mode.HANG -> updateHang(step)
            Mode.WALK -> updateWalk(step)
            Mode.CLIMB -> updateClimb(step)
            Mode.CEILING -> updateCeiling(step)
            Mode.HAPPY -> updateHappy(step)
            Mode.TOUCH -> updateTouch(step)
            Mode.SLEEP -> updateSleep(step)
        }
        syncWindowPosition()
    }

    private fun updateHang(dt: Float) {
        val params = bridge.getWindowParams() ?: return
        val spec = spriteSheetSpec ?: return
        val clip = spec.clip("idle") ?: return
        val idx = ((animClock / 0.26f).toInt() % clip.frames.size)
        bridge.currentFrame = clip.frames[idx]
        // Balanceo de araña colgada
        bridge.animRotation = sin(time * 2.4f) * 5f
        bridge.animOffsetX = sin(time * 2.4f) * 3f
        bridge.animOffsetY = abs(sin(time * 1.8f)) * 2f
        bridge.animScaleX = if (facingRight) 1f else -1f
        bridge.animScaleY = 1f + sin(time * 3f) * 0.02f

        if (modeTimer >= modeDuration) {
            modeTimer = 0f
            decideNext()
        }
    }

    private fun updateWalk(dt: Float) {
        val params = bridge.getWindowParams() ?: return
        val t = (modeTimer / modeDuration).coerceIn(0f, 1f)
        val eased = sin((t * PI).toFloat() / 2f)
        val x = fromX + (toX - fromX) * eased
        val y = fromY + (toY - fromY) * eased
        params.x = x.roundToInt()
        params.y = y.roundToInt()
        bridge.updateWindowLayout(params)

        val spec = spriteSheetSpec ?: return
        val clip = spec.clip("walk") ?: return
        val idx = ((animClock / 0.16f).toInt() % clip.frames.size)
        bridge.currentFrame = clip.frames[idx]
        bridge.animScaleX = if (facingRight) 1f else -1f
        bridge.animScaleY = 1f + sin(time * 8f) * 0.03f
        bridge.animRotation = sin(time * 6f) * 2f
        bridge.animOffsetY = 0f

        if (t >= 1f) {
            modeTimer = 0f
            decideNext()
        }
    }

    private fun updateClimb(dt: Float) {
        val params = bridge.getWindowParams() ?: return
        val t = (modeTimer / modeDuration).coerceIn(0f, 1f)
        val eased = sin((t * PI).toFloat() / 2f)
        val x = fromX + (toX - fromX) * eased
        val y = fromY + (toY - fromY) * eased
        params.x = x.roundToInt()
        params.y = y.roundToInt()
        bridge.updateWindowLayout(params)

        val spec = spriteSheetSpec ?: return
        val clip = spec.clip("climb") ?: return
        val idx = ((animClock / 0.19f).toInt() % clip.frames.size)
        bridge.currentFrame = clip.frames[idx]
        // En paredes, la araña se orienta según la dirección vertical
        val climbingUp = toY <= fromY
        bridge.animScaleX = if (facingRight) 1f else -1f
        bridge.animScaleY = 1f
        bridge.animRotation = 0f
        bridge.animOffsetY = sin(time * 10f) * 1.5f

        if (t >= 1f) {
            modeTimer = 0f
            decideNext()
        }
    }

    private fun updateCeiling(dt: Float) {
        val params = bridge.getWindowParams() ?: return
        val t = (modeTimer / modeDuration).coerceIn(0f, 1f)
        val eased = sin((t * PI).toFloat() / 2f)
        val x = fromX + (toX - fromX) * eased
        params.x = x.roundToInt()
        params.y = (minY() + 10f).roundToInt() // pegado al techo
        bridge.updateWindowLayout(params)

        val spec = spriteSheetSpec ?: return
        val clip = spec.clip("walk") ?: return
        val idx = ((animClock / 0.16f).toInt() % clip.frames.size)
        bridge.currentFrame = clip.frames[idx]
        // Patas arriba en el techo
        bridge.animScaleX = if (facingRight) 1f else -1f
        bridge.animScaleY = -1f
        bridge.animRotation = 0f
        bridge.animOffsetY = sin(time * 6f) * 1f

        if (t >= 1f) {
            modeTimer = 0f
            decideNext()
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
            modeTimer = 0f
            decideNext()
        }
    }

    private fun updateTouch(dt: Float) {
        val spec = spriteSheetSpec ?: return
        val clip = spec.clip("touch") ?: return
        val idx = ((animClock / 0.24f).toInt() % clip.frames.size)
        bridge.currentFrame = clip.frames[idx]
        if (modeTimer >= modeDuration) {
            bridge.state = PetState.IDLE
            animClock = 0f
            modeTimer = 0f
            decideNext()
            reset()
        }
    }

    private fun updateSleep(dt: Float) {
        val spec = spriteSheetSpec ?: return
        val clip = spec.clip("sleep") ?: return
        bridge.currentFrame = clip.frames[0]
        bridge.animOffsetY = sin(time * 1.2f) * 1.5f
        if (modeTimer >= modeDuration) {
            modeTimer = 0f
            decideNext()
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
        params.x = params.x.coerceIn(minX().roundToInt(), maxX().roundToInt())
        params.y = params.y.coerceIn(minY().roundToInt(), maxY().roundToInt())
        bridge.updateWindowLayout(params)
    }
}
