package com.pixelpals.app.behavior

import com.pixelpals.app.PetState
import com.pixelpals.app.R
import kotlin.math.sin
import kotlin.math.cos
import kotlin.random.Random

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

    private enum class BloopState { IDLE, ESCAPING, ALERT }
    private var bloopState = BloopState.IDLE
    private var stateTimer = 0f

    override fun updateIdle(dt: Float) {
        if (isLoading) return
        time += dt
        stateTimer += dt

        when (bloopState) {
            BloopState.IDLE -> {
                // Vuelo/Flotación: 0 y 1. Se mueve por toda la pantalla.
                bridge.currentFrame = if ((time * 2f).toInt() % 2 == 0) 0 else 1
                
                // Moverse por toda la pantalla
                updateDecision(dt)
                applyMovement(dt)
                
                // Efecto flotante
                bridge.animOffsetY = sin(time * 1.5f) * 15f
            }
            BloopState.ALERT -> {
                // Notificación: Frames 2, 3, 4
                val cycle = (time * 4f).toInt() % 3
                bridge.currentFrame = when(cycle) { 0 -> 2; 1 -> 3; else -> 4 }
                if (stateTimer > 3f) bloopState = BloopState.IDLE
            }
            BloopState.ESCAPING -> {
                // Huida: Frames 5, 6, 7, 8
                val cycle = (time * 8f).toInt() % 4
                bridge.currentFrame = when(cycle) { 0 -> 5; 1 -> 6; 2 -> 7; else -> 8 }
                
                // Se aleja rápido
                applyMovement(dt)
                if (stateTimer > 2f) {
                    bloopState = BloopState.IDLE
                }
            }
        }
    }

    override fun onInteract() {
        super.onInteract()
        bloopState = BloopState.ESCAPING
        stateTimer = 0f
        bridge.animRotation = 0f // Forzado a 0 para que no gire
        bridge.animScaleX = 1f
        bridge.animScaleY = 1f
        bridge.showBubble("💨!")
    }

    override fun updateDrag(dt: Float) {
        // Bloqueo total de rotación durante el arrastre
        bridge.animRotation = 0f
        bridge.animScaleX = 1f
        bridge.animScaleY = 1f
        bridge.currentFrame = 0
    }

    override fun updateFalling(dt: Float) {
        // Al soltarlo, se estabiliza
        bridge.animRotation = 0f
        bloopState = BloopState.IDLE
    }

    override fun updateInteracting(dt: Float) {
        // La lógica está en updateIdle con el estado ESCAPING
    }

    override fun reset() {
        super.reset()
        bloopState = BloopState.IDLE
        bridge.animAlpha = 1f
        bridge.animRotation = 0f
    }
}
