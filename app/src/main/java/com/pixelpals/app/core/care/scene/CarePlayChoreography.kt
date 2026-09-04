package com.pixelpals.app.core.care.scene

import com.pixelpals.app.core.domain.PetType

enum class CarePlayVariation { DIRECT, FEINT, DOUBLE_PASS }

data class CarePlayBeat(val travel: Float, val lift: Float, val poseProgress: Float)

/** Session-scoped variation; never choose randomness inside a rendered frame. */
class CarePlayVariations {
    private val next: MutableMap<PetType, Int> = mutableMapOf()

    @Synchronized
    fun nextFor(pet: PetType): CarePlayVariation {
        val index: Int = next[pet] ?: 0
        next[pet] = (index + 1) % CarePlayVariation.entries.size
        return CarePlayVariation.entries[index]
    }

    companion object {
        val shared: CarePlayVariations = CarePlayVariations()
    }
}

/** Anticipation, one attempt (or a feint/second pass), then an unhurried finish. */
object CarePlayChoreography {
    private data class Key(val time: Float, val travel: Float, val lift: Float, val pose: Float)
    private val direct: List<Key> = listOf(Key(0f, -.8f, 0f, 0f), Key(.18f, -.8f, 0f, .08f),
        Key(.53f, .85f, 1f, .6f), Key(.82f, .2f, .05f, .85f), Key(1f, 0f, 0f, 1f))
    private val feint: List<Key> = listOf(Key(0f, -.8f, 0f, 0f), Key(.16f, -.8f, 0f, .08f),
        Key(.35f, -.2f, .4f, .3f), Key(.48f, -.7f, .1f, .15f),
        Key(.73f, .8f, 1f, .7f), Key(.91f, .1f, 0f, .9f), Key(1f, 0f, 0f, 1f))
    private val doublePass: List<Key> = listOf(Key(0f, -.8f, 0f, 0f), Key(.16f, -.8f, 0f, .08f),
        Key(.39f, .55f, .85f, .5f), Key(.55f, -.35f, .08f, .22f),
        Key(.78f, .85f, 1f, .72f), Key(.95f, 0f, 0f, .95f), Key(1f, 0f, 0f, 1f))

    fun sample(progress: Float, variation: CarePlayVariation): CarePlayBeat {
        val time: Float = progress.coerceIn(0f, 1f)
        val keys: List<Key> = when (variation) {
            CarePlayVariation.DIRECT -> direct
            CarePlayVariation.FEINT -> feint
            CarePlayVariation.DOUBLE_PASS -> doublePass
        }
        val index: Int = keys.indexOfFirst { it.time >= time }.coerceAtLeast(1)
        val start: Key = keys[index - 1]
        val end: Key = keys[index]
        val fraction: Float = ((time - start.time) / (end.time - start.time)).coerceIn(0f, 1f)
        val eased: Float = fraction * fraction * (3f - 2f * fraction)
        return CarePlayBeat(start.travel + (end.travel - start.travel) * eased,
            start.lift + (end.lift - start.lift) * eased, start.pose + (end.pose - start.pose) * eased)
    }
}
