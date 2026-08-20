package com.pixelpals.app.core.runtime

import com.pixelpals.app.core.motion.PetRandom

data class PetActionCandidate(
    val intent: PetIntent,
    val baseWeight: Float,
    val traitMultiplier: Float = 1f,
    val contextMultiplier: Float = 1f,
    val cooldownMultiplier: Float = 1f,
) {
    init {
        require(baseWeight >= 0f && baseWeight.isFinite()) { "Action base weight must be finite and non-negative" }
        require(traitMultiplier >= 0f && traitMultiplier.isFinite()) { "Trait multiplier must be finite and non-negative" }
        require(contextMultiplier >= 0f && contextMultiplier.isFinite()) { "Context multiplier must be finite and non-negative" }
        require(cooldownMultiplier >= 0f && cooldownMultiplier.isFinite()) { "Cooldown multiplier must be finite and non-negative" }
    }

    val weight: Float = baseWeight * traitMultiplier * contextMultiplier * cooldownMultiplier
}

class PetActionScheduler(private val random: PetRandom) {
    fun select(candidates: List<PetActionCandidate>, recentActions: List<PetIntent>): PetIntent {
        val eligible: List<PetActionCandidate> = candidates.filter { candidate -> candidate.weight > 0f }
        require(eligible.isNotEmpty()) { "At least one positive action candidate is required" }
        val lastAction: PetIntent? = recentActions.lastOrNull()
        val adjusted: List<Pair<PetActionCandidate, Float>> = eligible.map { candidate ->
            val repeatPenalty: Float = if (candidate.intent == lastAction && eligible.size > 1) REPEAT_PENALTY else 1f
            candidate to candidate.weight * repeatPenalty
        }
        val totalWeight: Float = adjusted.sumOf { entry -> entry.second.toDouble() }.toFloat()
        var cursor: Float = random.nextFloat().coerceIn(0f, 0.999999f) * totalWeight
        adjusted.forEach { (candidate, weight) ->
            cursor -= weight
            if (cursor <= 0f) return candidate.intent
        }
        return adjusted.last().first.intent
    }

    private companion object {
        const val REPEAT_PENALTY: Float = 0.08f
    }
}
