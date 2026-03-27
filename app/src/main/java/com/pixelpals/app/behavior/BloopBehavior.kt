package com.pixelpals.app.behavior

import com.pixelpals.app.PetState
import com.pixelpals.app.R
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import android.util.Log
import org.json.JSONObject

/**
 * BloopBehavior — Fantasma juguetón.
 * IA: Vuelo (0-1), Alerta (2-3-4), Huida (5-6-7-8).
 */
class BloopBehavior(bridge: PetViewBridge) : BaseBehavior(bridge) {

    // Bloop tiene exactamente frames del 0 al 8
    override val resourceIds = (0..8).map { i ->
        (bridge as android.view.View).context.resources.getIdentifier(
            "fantasma_$i", "drawable", (bridge as android.view.View).context.packageName
        )
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

    private fun debugLog(hypothesisId: String, message: String) {
        try {
            val params = bridge.getWindowParams()
            val payload = JSONObject().apply {
                put("sessionId", "a40953")
                put("runId", "bloop")
                put("hypothesisId", hypothesisId)
                put("location", "BloopBehavior.kt")
                put("message", message)
                put("timestamp", System.currentTimeMillis())
                put("data", JSONObject().apply {
                    put("x", params?.x ?: -1)
                    put("y", params?.y ?: -1)
                    put("currentFrame", bridge.currentFrame)
                    put("animAlpha", bridge.animAlpha)
                    put("mode", mode.name)
                })
            }
            Log.i("AGENT_DEBUG", payload.toString())
        } catch (_: Exception) {}
    }

    private fun teleportWithinWalls() {
        val params = bridge.getWindowParams() ?: return
        val minX = 0
        val maxX = (bridge.screenWidth - bridge.petSpriteSize).coerceAtLeast(minX)
        val minY = 50
        val maxY = (bridge.screenHeight - bridge.petSpriteSize - 100).coerceAtLeast(minY)

        params.x = if (maxX == minX) minX else Random.nextInt(minX, maxX + 1)
        params.y = if (maxY == minY) minY else Random.nextInt(minY, maxY + 1)
        bridge.updateWindowLayout(params)
    }

    override fun updateIdle(dt: Float) {
        if (isLoading || frames.isEmpty()) return
        time += dt

        when (mode) {
            Mode.FLOAT_VISIBLE -> {
                // Flotación normal (usando SOLO fantasma_1)
                bridge.animAlpha = 1f
                bridge.currentFrame = 1
                bridge.animOffsetY = sin(time * 1.6f) * 14f
                bridge.animOffsetX = cos(time * 0.9f) * 7f
                bridge.animRotation = sin(time * 0.7f) * 2.5f
                updateFacing(velX)
                bridge.animScaleX = facingScale()
                bridge.animScaleY = 1f + sin(time * 1.2f) * 0.02f

                // Movimiento por pantalla, siempre con clamp de paredes (BaseBehavior.applyMovement)
                updateDecision(dt)
                updateFacing(velX)
                applyMovement(dt)

                    // Transparencia cada 5s durante 1s (fantasma_6 + alpha=0)
                disappearCountdown -= dt
                if (disappearCountdown <= 0f) {
                    mode = Mode.DISAPPEAR
                    disappearRemaining = 1f

                    bridge.animAlpha = 0f
                    bridge.currentFrame = 6
                    velX = 0f
                    velY = 0f
                    teleportWithinWalls()
                    bridge.showBubble("👻")
                    // Evita que al reaparecer dispare alertas encadenadas
                    alertCooldown = maxOf(alertCooldown, 4.5f)
                    debugLog(hypothesisId = "H5", message = "DISAPPEAR_START")
                }

                // Avisos (usando SOLO fantasma_2/3/4)
                alertCooldown -= dt
                if (alertCooldown <= 0f && Random.nextFloat() < 0.10f) {
                    mode = Mode.ALERT
                    alertRemaining = 1.8f
                    bridge.showBubble("!")
                    debugLog(hypothesisId = "H6", message = "ALERT_START")
                }
            }

            Mode.DISAPPEAR -> {
                bridge.animAlpha = 0f
                bridge.currentFrame = 6
                velX = 0f
                velY = 0f
                disappearRemaining -= dt
                if (disappearRemaining <= 0f) {
                    mode = Mode.FLOAT_VISIBLE
                    bridge.animAlpha = 1f
                    bridge.currentFrame = 1
                    decisionTimer = 0f
                    // Ciclo: transparente 1s y vuelve a flotar 4s => cada 5s inicia otra desaparición
                    disappearCountdown = 4f
                    // Lockout tras reaparecer para que no salte alerta inmediatamente
                    alertCooldown = maxOf(alertCooldown, 4.5f)
                    bridge.showBubble("...")
                    debugLog(hypothesisId = "H5", message = "DISAPPEAR_END")
                }
            }

            Mode.ALERT -> {
                // Notificación alternando entre fantasma_2/3/4
                val cycle = (time * 4f).toInt() % 3
                bridge.currentFrame = when (cycle) {
                    0 -> 2
                    1 -> 3
                    else -> 4
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
                applyMovement(dt)

                alertRemaining -= dt
                if (alertRemaining <= 0f) {
                    mode = Mode.FLOAT_VISIBLE
                    alertCooldown = 7f
                    debugLog(hypothesisId = "H6", message = "ALERT_END")
                }
            }

            Mode.ESCAPING -> {
                // Normalmente el escape ocurre en updateInteracting; si caemos aquí, volvemos a flotación.
                mode = Mode.FLOAT_VISIBLE
                bridge.animAlpha = 1f
                bridge.currentFrame = 1
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
        debugLog(hypothesisId = "H7", message = "ESCAPE_START")
    }

    override fun updateDrag(dt: Float) {
        // Durante el drag mantenemos frame estable (sin “desaparecer”)
        bridge.animRotation = 0f
        bridge.animScaleX = 1f
        bridge.animScaleY = 1f
        bridge.animAlpha = 1f
        bridge.currentFrame = 1
    }

    override fun updateFalling(dt: Float) {
        // Al soltarlo, se estabiliza y vuelve a flotación
        bridge.animRotation = 0f
        mode = Mode.FLOAT_VISIBLE
        bridge.animAlpha = 1f
        bridge.currentFrame = 1
    }

    override fun updateInteracting(dt: Float) {
        if (frames.isEmpty()) return
        time += dt
        interactionTimer += dt

        // Huida (usando SOLO fantasma_5/7/8)
        val cycle = (time * 10f).toInt() % 3
        bridge.currentFrame = when (cycle) {
            0 -> 5
            1 -> 7
            else -> 8
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
            debugLog(hypothesisId = "H7", message = "ESCAPE_END")
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
