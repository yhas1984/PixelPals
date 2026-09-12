package com.pixelpals.app.core.motion

import com.pixelpals.app.core.care.scene.SpeciesCarePose

/** Render-independent three-second medicine choreography for Jelly. */
data class JellyMedicinePose(
    val body: SpeciesCarePose,
    val spoonReach: Float,
    val medicineAmount: Float,
    val spoonAlpha: Float,
)

object JellyMedicineMotion {
    /** The animation is sampled in normalized time, where one is three seconds. */
    const val DURATION_SECONDS: Float = 3f

    /** Spoon reaches the mouth, empties, then returns before the eyes close. */
    const val CONTACT_START: Float = .22f
    const val CONTACT_END: Float = .43f
    const val SPOON_RETURN_END: Float = .50f

    /** The swallow starts after the spoon is fully hidden and ends at .8 progress. */
    const val SWALLOW_START: Float = .55f
    const val SWALLOW_SQUASH_END: Float = .64f
    const val SWALLOW_STRETCH_END: Float = .72f
    const val SWALLOW_END: Float = .80f

    /** Samples a deterministic pose; progress outside the clip is held at its endpoint. */
    fun sample(progress: Float, reduced: Boolean): JellyMedicinePose {
        val p: Float = progress.coerceIn(0f, 1f)
        if (reduced) {
            return JellyMedicinePose(
                body = SpeciesCarePose(),
                spoonReach = 1f,
                medicineAmount = 1f - ease((p - CONTACT_START) / (CONTACT_END - CONTACT_START)),
                spoonAlpha = if (p < SPOON_RETURN_END) 1f else 0f,
            )
        }

        val reach: Float = when {
            p < CONTACT_START -> ease(p / CONTACT_START)
            p < CONTACT_END -> 1f
            p < SPOON_RETURN_END -> 1f - ease((p - CONTACT_END) / (SPOON_RETURN_END - CONTACT_END))
            else -> 0f
        }
        val medicine: Float = when {
            p < CONTACT_START -> 1f
            p < CONTACT_END -> 1f - ease((p - CONTACT_START) / (CONTACT_END - CONTACT_START))
            else -> 0f
        }
        val body: SpeciesCarePose = if (p < SWALLOW_START || p >= SWALLOW_END) {
            SpeciesCarePose()
        } else {
            val scaleY: Float = when {
                p < SWALLOW_SQUASH_END -> lerp(1f, .98f,
                    ease((p - SWALLOW_START) / (SWALLOW_SQUASH_END - SWALLOW_START)))
                p < SWALLOW_STRETCH_END -> lerp(.98f, 1.02f,
                    ease((p - SWALLOW_SQUASH_END) / (SWALLOW_STRETCH_END - SWALLOW_SQUASH_END)))
                else -> lerp(1.02f, 1f,
                    ease((p - SWALLOW_STRETCH_END) / (SWALLOW_END - SWALLOW_STRETCH_END)))
            }
            SpeciesCarePose(scaleX = 1f / scaleY, scaleY = scaleY)
        }
        return JellyMedicinePose(
            body = body,
            spoonReach = reach,
            medicineAmount = medicine,
            spoonAlpha = if (p < CONTACT_END) 1f else reach,
        )
    }

    private fun ease(value: Float): Float {
        val t: Float = value.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun lerp(start: Float, end: Float, amount: Float): Float = start + (end - start) * amount
}
