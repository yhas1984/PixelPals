package com.pixelpals.app.core.thermal

import android.content.Context
import android.content.SharedPreferences
import com.pixelpals.app.core.runtime.pets.YukiRuntimeDefinition

/** One local hysteresis decision shared by home and overlay instances. */
class YukiThermalMemory(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(context.applicationContext.getSharedPreferences("yuki_thermal", Context.MODE_PRIVATE))
    fun update(sampleCelsius: Float?): Boolean = synchronized(lock) {
        val previous: Boolean = preferences.getBoolean("melted", false)
        val temperature: Float? = sampleCelsius?.takeIf(Float::isFinite)
        val next: Boolean = when {
            temperature == null -> previous
            temperature >= YukiRuntimeDefinition.MELT_ENTER_CELSIUS -> true
            temperature <= YukiRuntimeDefinition.MELT_EXIT_CELSIUS -> false
            else -> previous
        }
        if (next != previous) preferences.edit().putBoolean("melted", next).apply()
        next
    }
    private companion object { val lock: Any = Any() }
}
