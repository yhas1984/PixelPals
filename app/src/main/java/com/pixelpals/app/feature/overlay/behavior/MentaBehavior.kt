package com.pixelpals.app.feature.overlay.behavior

import com.pixelpals.app.core.domain.PetState
import com.pixelpals.app.core.motion.PetRandom
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * MentaBehavior — Serpientita de menta.
 * Se desliza por TODA la pantalla como una serpiente real:
 *  - Cruza horizontalmente de lado a lado (frames de slither 4-7),
 *  - Sube y baja verticalmente estirándose (frames de stretch 8-11),
 *  - SIEMPRE de frente a su dirección (nunca mira hacia un lado raro),
 *  - Deslizamiento lento y continuo: sin golpeteos de cabeza ni tirones.
 * IA atlas: idle (0-3), slither (4-7), stretch (8-11), touch (12-14), sleep (15).
 */
class MentaBehavior(
    bridge: PetViewBridge,
    override val random: PetRandom,
) : BaseBehavior(bridge, random) {

    override val resourceIds: List<Int> = emptyList()

    private enum class Mode { SLITHER, CLIMB, COIL, HAPPY, TOUCH, SLEEP }

    private var mode = Mode.SLITHER
    private var modeTimer = 0f
    private var modeDuration = 3f
    private var animClock = 0f
    private var startX = 0f
    private var startY = 0f
    private var cruiseTargetX = 0f
    private var cruiseTargetY = 0f
    private var facingRight = true
    private var climbingUp = true

    init {
        loadSpriteSheetAssetAsync("pets/menta/menta_sheet_v1.json")
    }

    override fun getBaseSpeed(): Float = 38f

    override fun updateIdle(dt: Float) {
        if (isLoading || spriteSheetBitmap == null || spriteFrameRects.isEmpty()) return
        val step = dt.coerceIn(0f, 1f / 30f)
        time += step
        modeTimer += step
        animClock += step

        when (mode) {
            Mode.SLITHER -> updateSlither(step)
            Mode.CLIMB -> updateClimb(step)
            Mode.COIL -> updateCoil(step)
            Mode.HAPPY -> updateHappy(step)
            Mode.TOUCH -> updateTouch(step)
            Mode.SLEEP -> updateSleep(step)
        }
        syncWindowPosition()
    }

    /**
     * Elige el siguiente desplazamiento: cruza horizontal (65%) o sube/baja
     * vertical (35%). Siempre recorre TODA la pantalla.
     */
    private fun pickTarget() {
        val params = bridge.getWindowParams() ?: return
        startX = params.x.toFloat()
        startY = params.y.toFloat()
        val minX = 0f
        val maxX = (bridge.screenWidth - bridge.petSpriteSize).coerceAtLeast(0).toFloat()
        val minY = 80f
        val maxY = (bridge.screenHeight - bridge.petSpriteSize - 80).coerceAtLeast(80).toFloat()

        if (random.nextFloat() < 0.65f) {
            // CRUCE HORIZONTAL: de un lado al otro, subiendo/bajando un poco
            mode = Mode.SLITHER
            val far = if (startX < (minX + maxX) / 2f) maxX else minX
            cruiseTargetX = far
            cruiseTargetY = if (random.nextFloat() < 0.6f) {
                random.nextFloat() * (maxY - minY) + minY
            } else {
                if (startY < (minY + maxY) / 2f) maxY else minY
            }
            facingRight = cruiseTargetX >= startX
        } else {
            // MOVIMIENTO VERTICAL: sube o baja estirándose en el mismo sitio
            mode = Mode.CLIMB
            cruiseTargetX = startX
            climbingUp = random.nextFloat() < 0.5f
            cruiseTargetY = if (climbingUp) minY else maxY
        }

        val dx = cruiseTargetX - startX
        val dy = cruiseTargetY - startY
        val dist = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        modeDuration = (dist / getBaseSpeed()).coerceIn(2.5f, 14.0f)
    }

    /** Deslizamiento horizontal: lento, ondulando el cuerpo, de frente. */
    private fun updateSlither(dt: Float) {
        val params = bridge.getWindowParams() ?: return
        val t = (modeTimer / modeDuration).coerceIn(0f, 1f)
        if (t >= 1f) {
            modeTimer = 0f
            if (random.nextFloat() < 0.20f) {
                mode = Mode.COIL
                modeDuration = 2.5f + random.nextFloat() * 2.5f
            } else {
                pickTarget()
            }
            return
        }
        val eased = t
        val x = startX + (cruiseTargetX - startX) * eased
        val y = startY + (cruiseTargetY - startY) * eased
        params.x = x.roundToInt()
        params.y = y.roundToInt()
        bridge.updateWindowLayout(params)

        // Deslizamiento suave: el cuerpo ondula lentamente, sin tirones
        bridge.animOffsetX = sin(time * 2.2f) * 4f
        bridge.animOffsetY = sin(time * 1.6f) * 2f
        bridge.animRotation = 0f   // siempre de frente, sin golpeteos de cabeza
        // Cada dirección tiene sus propias poses dibujadas: no espejar la
        // serpiente, porque el atlas ya coloca la cabeza delante del cuerpo.
        bridge.animScaleX = 1f
        bridge.animScaleY = 1f + sin(time * 2.0f) * 0.02f

        // La onda viaja sincronizada con el avance: cada pose corresponde a
        // una fracción real del trayecto, no a un reloj independiente.
        // La onda avanza por distancia real, no por un reloj independiente:
        // cabeza y contoneo tienen la misma velocidad física.
        val distance = kotlin.math.sqrt(
            (cruiseTargetX - startX) * (cruiseTargetX - startX) +
                (cruiseTargetY - startY) * (cruiseTargetY - startY)
        )
        val travelled = distance * t
        // Dos fases por dirección, repetidas según los píxeles recorridos.
        // right = 4-5; left = 6-7.
        val wave = (travelled / 42f).toInt() % 2
        val poseBase = if (facingRight) 4 else 6
        bridge.currentFrame = poseBase + wave

        if (random.nextFloat() < 0.00025f) {
            mode = Mode.HAPPY
            modeTimer = 0f
            modeDuration = 1.2f + random.nextFloat() * 1.0f
        }
    }

    /** Movimiento vertical: se estira para subir/bajar, siempre de frente. */
    private fun updateClimb(dt: Float) {
        val params = bridge.getWindowParams() ?: return
        val t = (modeTimer / modeDuration).coerceIn(0f, 1f)
        if (t >= 1f) {
            modeTimer = 0f
            if (random.nextFloat() < 0.15f) {
                mode = Mode.COIL
                modeDuration = 2.5f + random.nextFloat() * 2.5f
            } else {
                pickTarget()
            }
            return
        }
        val eased = t
        val x = startX + (cruiseTargetX - startX) * eased
        val y = startY + (cruiseTargetY - startY) * eased
        params.x = x.roundToInt()
        params.y = y.roundToInt()
        bridge.updateWindowLayout(params)

        // También en vertical: la cabeza avanza y la onda la sigue por el
        // cuerpo mientras recorre la pantalla.
        // La dirección se obtiene del desplazamiento real, no de un estado
        // auxiliar que pueda quedar desfasado después de otro modo.
        val movingDown = cruiseTargetY > startY
        val distance = kotlin.math.abs(cruiseTargetY - startY)
        val travelled = distance * t
        val wave = (travelled / 42f).toInt() and 1
        // El atlas tiene dos grupos dibujados: up = 8-9, down = 10-11.
        // No se voltea la imagen: cada pose conserva la cara y la cabeza líder.
        bridge.currentFrame = (if (movingDown) 10 else 8) + wave
        bridge.animRotation = 0f
        bridge.animScaleX = 1f
        bridge.animScaleY = 1f
        bridge.animOffsetX = sin(time * 1.8f) * 2f
        bridge.animOffsetY = sin(time * 1.4f) * 1.5f
    }

    private fun updateCoil(dt: Float) {
        val spec = spriteSheetSpec ?: return
        val clip = spec.clip("idle") ?: return
        val idx = ((animClock / 0.30f).toInt() % clip.frames.size)
        bridge.currentFrame = clip.frames[idx]
        bridge.animOffsetX = 0f
        bridge.animOffsetY = sin(time * 2.2f) * 2f
        bridge.animRotation = 0f
        bridge.animScaleX = 1f
        bridge.animScaleY = 1f + sin(time * 2.8f) * 0.03f
        if (modeTimer >= modeDuration) {
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
        // Al arrastrar, la serpiente se desliza suave (no se congela).
        time += dt
        // Durante el arrastre, el cuerpo sigue el desplazamiento del dedo.
        bridge.currentFrame = 4 + ((time * 5f).toInt() % 2)
        bridge.animOffsetX = sin(time * 2.2f) * 3f
        bridge.animScaleX = 1f
        bridge.animScaleY = 1f
        bridge.animRotation = 0f
    }

    override fun reset() {
        super.reset()
        // Al soltar, reanuda el desplazamiento al momento.
        modeTimer = 0f
        animClock = 0f
        pickTarget()
    }

    private fun syncWindowPosition() {
        val params = bridge.getWindowParams() ?: return
        val minX = 0
        val maxX = (bridge.screenWidth - bridge.petSpriteSize).coerceAtLeast(0)
        val minY = 80
        val maxY = (bridge.screenHeight - bridge.petSpriteSize - 80).coerceAtLeast(minY)
        params.x = params.x.coerceIn(minX, maxX)
        params.y = params.y.coerceIn(minY, maxY)
        bridge.updateWindowLayout(params)
    }
}
