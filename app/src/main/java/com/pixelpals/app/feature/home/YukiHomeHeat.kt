package com.pixelpals.app.feature.home

import com.pixelpals.app.core.runtime.pets.YukiRuntimeDefinition

/** Pure thermal state for the Yuki home presentation. */
class YukiHomeHeat {
    private var latched: Boolean = false

    var elapsedSeconds: Float = 0f
        private set

    val active: Boolean get() = latched || elapsedSeconds > 0f
    val isMelted: Boolean get() = active

    fun updateTemperature(sampleCelsius: Float?, sharedLatched: Boolean? = null) {
        if (sharedLatched != null) { latched = sharedLatched; return }
        val temperature = sampleCelsius?.takeIf(Float::isFinite) ?: return
        when {
            temperature >= YukiRuntimeDefinition.MELT_ENTER_CELSIUS -> latched = true
            temperature <= YukiRuntimeDefinition.MELT_EXIT_CELSIUS -> latched = false
        }
    }

    /** Visible-time playback, reversed when the battery cools. */
    fun advance(deltaSeconds: Float, reducedMotion: Boolean = false): Unit {
        val delta: Float = deltaSeconds.coerceIn(0f, .1f)
        elapsedSeconds = if (reducedMotion) {
            if (latched) YukiRuntimeDefinition.MELT_SECONDS else 0f
        } else (elapsedSeconds + if (latched) delta else -delta)
            .coerceIn(0f, YukiRuntimeDefinition.MELT_SECONDS)
    }
}
