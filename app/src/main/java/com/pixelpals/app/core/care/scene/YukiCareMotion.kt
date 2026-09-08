package com.pixelpals.app.core.care.scene

import kotlin.math.sin
import kotlin.math.PI

/** Two snow throws and a partial melt that always recovers before care ends. */
object YukiCareMotion {
    private fun cycle(progress: Float): Float = ((progress.coerceIn(0f, .9999f) * 2f) % 1f)
    fun flightAt(progress: Float): Float = ((cycle(progress) - .3f) / .55f).coerceIn(0f, 1f)
    fun throwLean(progress: Float): Float = -sin(cycle(progress) * PI.toFloat() * 2f) * 4f
    fun playFrame(progress: Float): Int = when {
        progress >= .92f -> 7
        cycle(progress) < .3f -> 5
        cycle(progress) < .85f -> 6
        else -> 7
    }
    fun meltAt(progress: Float): Float {
        val p: Float = progress.coerceIn(0f, 1f)
        val rise: Float = ((p - .12f) / .4f).coerceIn(0f, 1f)
        val recovery: Float = ((.94f - p) / .32f).coerceIn(0f, 1f)
        val amount: Float = minOf(rise, recovery)
        return amount * amount * (3f - 2f * amount)
    }
}
