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

    /** Decide la siguiente acción según la posición actual (perímetro, como araña real). */
    private fun decideNext() {
        val params = bridge.getWindowParams() ?: return
        val x = params.x.toFloat()
        val y = params.y.toFloat()
        val edge = 30f
        val nearLeft = x <= minX() + edge
        val nearRight = x >= maxX() - edge
        val nearTop = y <= minY() + edge
        val nearBottom = y >= maxY() - edge
        val roll = random.nextFloat()

        // La araña recorre el PERÍMETRO: borde izq → techo → borde der → suelo → ...
        when {
            nearTop && !nearLeft && !nearRight -> {
                // En el techo: cruza hacia el lado opuesto o baja por el borde más cercano
                if (roll < 0.35f && (nearLeft || nearRight)) {
                    // Ya en esquina: baja
                    startMode(Mode.CLIMB, 1.4f + random.nextFloat() * 1.2f, x, y, x, maxY() - 20f)
                } else {
                    val sideX = if (x < bridge.screenWidth / 2f) minX() else maxX()
                    startMode(Mode.CEILING, 1.8f + random.nextFloat() * 1.6f, x, y, sideX + (if (sideX == minX()) 20f else -20f), minY() + 10f)
                }
            }
            nearLeft || nearRight -> {
                // En un borde lateral: sube al techo o baja al suelo por el mismo borde
                if (roll < 0.5f) {
                    startMode(Mode.CLIMB, 1.3f + random.nextFloat() * 1.4f, x, y, x, minY() + 10f)
                } else {
                    startMode(Mode.CLIMB, 1.2f + random.nextFloat() * 1.3f, x, y, x, maxY() - 20f)
                }
            }
            nearBottom && !nearLeft && !nearRight -> {
                // En el suelo: sube por la pared más cercana
                val sideX = if (x < bridge.screenWidth / 2f) minX() else maxX()
                startMode(Mode.CLIMB, 1.5f + random.nextFloat() * 1.3f, x, y, sideX + (if (sideX == minX()) 20f else -20f), maxY() - 20f)
            }
            else -> {
                // En el aire (colgando de un hilo en el centro): sube al techo
                // o se deja caer al suelo; NUNCA cruza el centro sin motivo.
                if (roll < 0.55f) {
                    startMode(Mode.CLIMB, 1.2f + random.nextFloat() * 1.2f, x, y, x, minY() + 10f)
                } else {
                    startMode(Mode.CLIMB, 1.2f + random.nextFloat() * 1.2f, x, y, x, maxY() - 20f)
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
        // El PetView llama a esto mientras state == INTERACTING; si no avanzamos
        // el reloj, la araña se queda congelada en el frame de touch para siempre.
        time += dt
        modeTimer += dt
        animClock += dt
        if (mode == Mode.TOUCH) updateTouch(dt)
    }

    private fun syncWindowPosition() {
        val params = bridge.getWindowParams() ?: return
        params.x = params.x.coerceIn(minX().roundToInt(), maxX().roundToInt())
        params.y = params.y.coerceIn(minY().roundToInt(), maxY().roundToInt())
        bridge.updateWindowLayout(params)
    }
}
