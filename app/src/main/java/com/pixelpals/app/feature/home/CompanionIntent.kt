package com.pixelpals.app.feature.home

import kotlin.random.Random

/** Presentation inputs only. Autonomous actions never change needs or award care. */
data class CompanionIntentContext(
    val energy: Int = 75,
    val isUnwell: Boolean = false,
    val hasToy: Boolean = true,
    val hasBed: Boolean = true,
    val curiosity: Float = .5f,
    val playPreference: Float = .5f,
    val confidence: Float = .5f,
)

enum class CompanionIntent { PLAY, REST, EXPLORE, OBSERVE }

/** Weighted choices with a repetition penalty, rather than a fixed toy/bed loop. */
class CompanionIntentSelector(private val random: Random = Random.Default) {
    private var previous: CompanionIntent? = null
    fun choose(context: CompanionIntentContext): CompanionIntent {
        if (context.energy <= 25 || context.isUnwell) return CompanionIntent.REST.also { previous = it }
        val weights: Map<CompanionIntent, Float> = mapOf(
            CompanionIntent.PLAY to if (context.hasToy) .5f + context.playPreference.coerceIn(0f, 1f) * 2f else 0f,
            CompanionIntent.REST to .3f + (100 - context.energy.coerceIn(0, 100)) * .025f,
            CompanionIntent.EXPLORE to .4f + context.curiosity.coerceIn(0f, 1f) * context.confidence.coerceIn(0f, 1f) * 2f,
            CompanionIntent.OBSERVE to .6f + (1f - context.confidence.coerceIn(0f, 1f)),
        ).mapValues { (intent, weight) -> if (intent == previous) weight * .12f else weight }
        var ticket: Float = random.nextFloat() * weights.values.sum()
        val selected: CompanionIntent = weights.entries.firstOrNull { ticket -= it.value; ticket < 0f }?.key ?: CompanionIntent.OBSERVE
        previous = selected
        return selected
    }
    fun nextPause(): Float = 2.5f + random.nextFloat() * 3f
    fun nextX(): Float = 230f + random.nextFloat() * 540f
    fun nextY(): Float = 520f + random.nextFloat() * 140f
}
