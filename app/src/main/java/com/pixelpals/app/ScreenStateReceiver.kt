package com.pixelpals.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * ScreenStateReceiver — BroadcastReceiver para optimización de batería.
 *
 * Escucha ACTION_SCREEN_OFF y ACTION_SCREEN_ON para pausar/reanudar
 * completamente el bucle de animación de la mascota.
 *
 * CRÍTICO: El bucle de dibujo DEBE detenerse cuando la pantalla está apagada.
 * Esto es la diferencia entre usar 0% y 5% de batería en standby.
 */
class ScreenStateReceiver(
    private val onScreenStateChanged: (isScreenOn: Boolean) -> Unit
) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SCREEN_OFF -> {
                onScreenStateChanged(false)
            }
            Intent.ACTION_SCREEN_ON -> {
                onScreenStateChanged(true)
            }
        }
    }
}
