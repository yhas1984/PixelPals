package com.pixelpals.app.behavior

import com.pixelpals.app.PetState
import com.pixelpals.app.R
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import com.pixelpals.app.motion.PetRandom

/**
 * BloopBehavior — Fantasma juguetón.
 * IA: flotación, alerta, invisibilidad y huida diagonal.
 */
class BloopBehavior(bridge: PetViewBridge, override val random: PetRandom) : BaseBehavior(bridge, random) {

    // The old neutral duplicate and fully invisible frame were removed.
    override val resourceIds = listOf(
        R.drawable.fantasma_1, R.drawable.fantasma_2, R.drawable.fantasma_3,
        R.drawable.fantasma_4, R.drawable.fantasma_5, R.drawable.fantasma_7,
        R.drawable.fantasma_8
    )

    init {
        loadFramesAsync()
    }

    private enum class Mode { FLOAT_VISIBLE, DISAPPEAR, ALERT, ESCAPING }
    private var mode = Mode.FLOAT_VISIBLE

    private var disappearCountdown = 5f
    private var disappearRemaining = 0f

    private var alertRemaining = 0f
    private var alertCooldown = 6f

    private var escapeRemaining = 0f

    private var escapeTargetX = 0f
    private var escapeTargetY = 0f
    private var facingRight = true

    override fun getBaseSpeed(): Float = 85f

    private fun facingScale(stretch: Float = 1f): Float {
        val magnitude = kotlin.math.abs(stretch)
        return if (facingRight) magnitude else -magnitude
    }

    private fun updateFacing(horizontal: Float) {
        if (horizontal > 1f) facingRight = true
        else if (horizontal < -1f) facingRight = false
    }

    private fun teleportWithinWalls() {
        val params = bridge.getWindowParams() ?: return
        val minX = 0
        val maxX = (bridge.screenWidth - bridge.petSpriteSize).coerceAtLeast(minX)
        val minY = 50
        val maxY = (bridge.screenHeight - bridge.petSpriteSize - 100).coerceAtLeast(minY)

        params.x = if (maxX == minX) minX else random.nextInt(minX, maxX + 1)
        params.y = if (maxY == minY) minY else random.nextInt(minY, maxY + 1)
        bridge.updateWindowLayout(params)
    }

    private fun applyGhostFlight(dt: Float) {
        val params = bridge.getWindowParams() ?: return
        val minX = 0
        val maxX = (bridge.screenWidth - bridge.petSpriteSize).coerceAtLeast(0)
        val minY = 50
        val maxY = (bridge.screenHeight - bridge.petSpriteSize - 100).coerceAtLeast(minY)
        val softMargin = bridge.petSpriteSize * 0.9f
        if (params.x < minX + softMargin) velX = maxOf(abs(velX), 70f)
        if (params.x > maxX - softMargin) velX = -maxOf(abs(velX), 70f)
        if (params.y < minY + softMargin) velY = maxOf(abs(velY), 58f)
        if (params.y > maxY - softMargin) velY = -maxOf(abs(velY), 58f)
        params.x = (params.x + (velX * dt).roundToInt()).coerceIn(minX, maxX)
        params.y = (params.y + (velY * dt).roundToInt()).coerceIn(minY, maxY)
        bridge.updateWindowLayout(params)
    }

    override fun updateIdle(dt: Float) {
        if (isLoading || frames.isEmpty()) return
        time += dt

        when (mode) {
            Mode.FLOAT_VISIBLE -> {
                // Flotación normal (usando SOLO fantasma_1)
                bridge.animAlpha = 1f
                bridge.currentFrame = 0
                bridge.animOffsetY = sin(time * 1.6f) * 14f
                bridge.animOffsetX = cos(time * 0.9f) * 7f
                bridge.animRotation = sin(time * 0.7f) * 2.5f
                updateFacing(velX)
                bridge.animScaleX = facingScale()
                bridge.animScaleY = 1f + sin(time * 1.2f) * 0.02f

                // Movimiento por pantalla, siempre con clamp de paredes (BaseBehavior.applyMovement)
                updateDecision(dt)
                updateFacing(velX)
                applyGhostFlight(dt)

                    // Fully transparent for one second; no dedicated invisible frame is needed.
                disappearCountdown -= dt
                if (disappearCountdown <= 0f) {
                    mode = Mode.DISAPPEAR
                    disappearRemaining = 1f

                    bridge.animAlpha = 0f
                    bridge.currentFrame = 0
                    velX = 0f
                    velY = 0f
                    teleportWithinWalls()
                    bridge.showBubble("👻")
                    // Evita que al reaparecer dispare alertas encadenadas
                    alertCooldown = maxOf(alertCooldown, 4.5f)
                }

                // Avisos (usando SOLO fantasma_2/3/4)
                alertCooldown -= dt
                if (alertCooldown <= 0f && random.nextFloat() < 0.10f) {
                    mode = Mode.ALERT
                    alertRemaining = 1.8f
                    bridge.showBubble("!")
                }
            }

            Mode.DISAPPEAR -> {
                bridge.animAlpha = 0f
                bridge.currentFrame = 0
                velX = 0f
                velY = 0f
                disappearRemaining -= dt
                if (disappearRemaining <= 0f) {
                    mode = Mode.FLOAT_VISIBLE
                    bridge.animAlpha = 1f
                    bridge.currentFrame = 0
                    decisionTimer = 0f
                    // Ciclo: transparente 1s y vuelve a flotar 4s => cada 5s inicia otra desaparición
                    disappearCountdown = 4f
                    // Lockout tras reaparecer para que no salte alerta inmediatamente
                    alertCooldown = maxOf(alertCooldown, 4.5f)
                    bridge.showBubble("...")
                }
            }

            Mode.ALERT -> {
                // Notificación alternando entre fantasma_2/3/4
                val cycle = (time * 4f).toInt() % 3
                bridge.currentFrame = when (cycle) {
                    0 -> 1
                    1 -> 2
                    else -> 3
                }
                bridge.animAlpha = 1f
                bridge.animOffsetY = sin(time * 3f) * 6f
                updateFacing(velX)
                bridge.animScaleX = facingScale(1f + sin(time * 10f) * 0.03f)
                bridge.animScaleY = 1f + cos(time * 10f) * 0.03f
                bridge.animRotation = sin(time * 6f) * 5f

                // Mientras avisa, mantiene el “vagabundeo” sin salir de paredes
                updateDecision(dt)
                updateFacing(velX)
                applyGhostFlight(dt)

                alertRemaining -= dt
                if (alertRemaining <= 0f) {
                    mode = Mode.FLOAT_VISIBLE
                    alertCooldown = 7f
                }
            }

            Mode.ESCAPING -> {
                // Normalmente el escape ocurre en updateInteracting; si caemos aquí, volvemos a flotación.
                mode = Mode.FLOAT_VISIBLE
                bridge.animAlpha = 1f
                bridge.currentFrame = 0
                decisionTimer = 0f
            }
        }
    }

    override fun onInteract() {
        super.onInteract()
        mode = Mode.ESCAPING
        escapeRemaining = 2.2f
        decisionTimer = 0f
        bridge.animAlpha = 1f
        bridge.animRotation = 0f
        bridge.animScaleX = 1f
        bridge.animScaleY = 1f
        bridge.animOffsetX = 0f
        bridge.animOffsetY = 0f
        bridge.showBubble("💨")

        // Objetivo diagonal alejado, pero no pegado a una esquina extrema:
        // se siente mas natural y evita una huida demasiado vertical/horizontal.
        val minX = 0f
        val maxX = (bridge.screenWidth - bridge.petSpriteSize).coerceAtLeast(0).toFloat()
        val minY = 50f
        val maxY = (bridge.screenHeight - bridge.petSpriteSize - 100).coerceAtLeast(minY.toInt()).toFloat()

        val params = bridge.getWindowParams()
        val currX = (params?.x ?: bridge.windowX).toFloat()
        val currY = (params?.y ?: bridge.windowY).toFloat()

        val horizontalPadding = (bridge.screenWidth * 0.126f).coerceAtLeast(60f)
        val verticalPadding = (bridge.screenHeight * 0.098f).coerceAtLeast(60f)
        val midX = (minX + maxX) / 2f
        val midY = (minY + maxY) / 2f

        escapeTargetX = if (currX < midX) {
            (maxX - horizontalPadding).coerceAtLeast(minX)
        } else {
            (minX + horizontalPadding).coerceAtMost(maxX)
        }

        escapeTargetY = if (currY < midY) {
            (maxY - verticalPadding).coerceAtLeast(minY)
        } else {
            (minY + verticalPadding).coerceAtMost(maxY)
        }

        // Si por geometria quedara demasiado cerca en un eje, abre mas la diagonal.
        if (kotlin.math.abs(escapeTargetX - currX) < bridge.petSpriteSize * 0.6f) {
            escapeTargetX = if (currX < midX) maxX else minX
        }
        if (kotlin.math.abs(escapeTargetY - currY) < bridge.petSpriteSize * 0.6f) {
            escapeTargetY = if (currY < midY) maxY else minY
        }
    }

    override fun onFling(velocityX: Float, velocityY: Float) {
        super.onInteract()
        mode = Mode.ESCAPING
        escapeRemaining = 1.8f
        val params = bridge.getWindowParams()
        val currX = (params?.x ?: bridge.windowX).toFloat()
        val currY = (params?.y ?: bridge.windowY).toFloat()
        escapeTargetX = if (velocityX >= 0f) {
            (bridge.screenWidth - bridge.petSpriteSize).coerceAtLeast(0).toFloat()
        } else {
            0f
        }
        escapeTargetY = if (velocityY >= 0f) {
            (bridge.screenHeight - bridge.petSpriteSize - 100).coerceAtLeast(50).toFloat()
        } else {
            50f
        }
        if (kotlin.math.abs(escapeTargetX - currX) < 20f) escapeTargetX = (bridge.screenWidth / 2f)
    }

    override fun updateDrag(dt: Float) {
        // Durante el drag mantenemos frame estable (sin “desaparecer”)
        bridge.animRotation = 0f
        bridge.animScaleX = 1f
        bridge.animScaleY = 1f
        bridge.animAlpha = 1f
        bridge.currentFrame = 0
    }

    override fun updateFalling(dt: Float) {
        // Al soltarlo, se estabiliza y vuelve a flotación
        bridge.animRotation = 0f
        bridge.state = PetState.IDLE
        mode = Mode.FLOAT_VISIBLE
        reset()
    }

    override fun updateInteracting(dt: Float) {
        if (frames.isEmpty()) return
        time += dt
        interactionTimer += dt

        // Huida (usando SOLO fantasma_5/7/8)
        val cycle = (time * 10f).toInt() % 3
        bridge.currentFrame = when (cycle) {
            0 -> 4
            1 -> 5
            else -> 6
        }

        // Huida: objetivo fijo para evitar “anclajes” por dx/dy ~ 0.
        val params = bridge.getWindowParams() ?: return
        val currX = params.x.toFloat()
        val currY = params.y.toFloat()

        val dx = escapeTargetX - currX
        val dy = escapeTargetY - currY
        val dist = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        val headingX = (dx / dist).coerceIn(-1f, 1f)
        updateFacing(dx)

        bridge.animAlpha = 1f
        bridge.animRotation = headingX * 8f
        bridge.animOffsetY = sin(time * 7f) * 3f
        bridge.animOffsetX = headingX * 5f
        bridge.animScaleX = facingScale()
        bridge.animScaleY = 1f + sin(time * 4f) * 0.015f

        // Movimiento rapido y muy visible: huye de verdad hacia otra zona de la pantalla.
        val progress = (escapeRemaining / 2.2f).coerceIn(0f, 1f)
        val speed = getBaseSpeed() * 3.055f * (0.9f + 0.4f * progress)

        var moveX = ((dx / dist) * speed * dt).roundToInt()
        var moveY = ((dy / dist) * speed * dt).roundToInt()

        // Fuerza un desplazamiento minimo para que no se quede "vibrando" en el sitio.
        if (moveX == 0 && abs(dx) > 1f) moveX = if (dx > 0f) 4 else -4
        if (moveY == 0 && abs(dy) > 1f) moveY = if (dy > 0f) 4 else -4

        val minX = 0
        val maxX = (bridge.screenWidth - bridge.petSpriteSize).coerceAtLeast(0)
        val minY = 50
        val maxY = (bridge.screenHeight - bridge.petSpriteSize - 100).coerceAtLeast(minY)

        params.x = (params.x + moveX).coerceIn(minX, maxX)
        params.y = (params.y + moveY).coerceIn(minY, maxY)
        bridge.updateWindowLayout(params)

        escapeRemaining -= dt
        if (escapeRemaining <= 0f) {
            bridge.state = PetState.IDLE
            mode = Mode.FLOAT_VISIBLE
            disappearCountdown = 5f
            reset()
        }
    }

    override fun reset() {
        super.reset()
        mode = Mode.FLOAT_VISIBLE
        bridge.animAlpha = 1f
        bridge.animRotation = 0f
        bridge.animOffsetX = 0f
        bridge.animOffsetY = 0f
    }
}
