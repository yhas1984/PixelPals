package com.pixelpals.app.core.motion

import com.pixelpals.app.core.care.scene.CarePlayVariation
import com.pixelpals.app.core.care.scene.SpeciesCarePose
/** Pure choreography for Jelly's spring toy; the renderer owns tool geometry. */
object JellySpringPlayMotion {
    const val TOOL_SCALE: Float = .28f
    const val SPRING_X: Float = .22f
    const val SPRING_BOTTOM: Float = .44f
    const val SPRING_HEIGHT: Float = .83f * TOOL_SCALE

    private const val ANTICIPATION_END: Float = .10f
    private const val SPRING_REACHED: Float = .30f
    private const val COMPRESSION_END: Float = .42f
    private const val RELEASE_END: Float = .50f
    private const val FLIGHT_END: Float = .70f
    private const val LANDING_END: Float = .78f
    private const val SECOND_RELEASE_END: Float = .84f
    private const val RETURN_END: Float = .94f

    data class Pose(val body: SpeciesCarePose, val springCompression: Float)

    fun sample(
        progress: Float,
        reduced: Boolean,
        variation: CarePlayVariation = CarePlayVariation.DIRECT,
    ): Pose {
        if (reduced) return Pose(SpeciesCarePose(), 1f)
        val p = progress.coerceIn(0f, 1f)
        val heightFactor = when (variation) {
            CarePlayVariation.DIRECT -> 1f
            CarePlayVariation.FEINT -> .92f
            CarePlayVariation.DOUBLE_PASS -> 1.06f
        }
        return when {
            p < ANTICIPATION_END -> pose(scaleY = lerp(1f, .96f, ease(p / ANTICIPATION_END)))
            p < SPRING_REACHED -> {
                val t = (p - ANTICIPATION_END) / (SPRING_REACHED - ANTICIPATION_END)
                val u = ease(t)
                val arc = .08f * heightFactor * 4f * t * (1f - t)
                pose(x = SPRING_X * u, y = -SPRING_HEIGHT * t - arc, scaleY = lerp(.96f, 1f, u))
            }
            p < COMPRESSION_END -> {
                val t = (p - SPRING_REACHED) / (COMPRESSION_END - SPRING_REACHED)
                val y = hermite(-SPRING_HEIGHT, -SPRING_HEIGHT * .45f,
                    (-SPRING_HEIGHT + 4f * .08f * heightFactor) / (SPRING_REACHED - ANTICIPATION_END),
                    0f, t, COMPRESSION_END - SPRING_REACHED)
                pose(x = SPRING_X, y = y, scaleY = lerp(1f, .78f, ease(t)), compression = -y / SPRING_HEIGHT)
            }
            p < RELEASE_END -> {
                val t = (p - COMPRESSION_END) / (RELEASE_END - COMPRESSION_END)
                val y = hermite(-SPRING_HEIGHT * .45f, -SPRING_HEIGHT, 0f,
                    -4f * .12f * heightFactor / .20f, t, RELEASE_END - COMPRESSION_END)
                val compression = ((-y / SPRING_HEIGHT).coerceIn(.45f, 1f))
                val eased = ease(t)
                pose(x = SPRING_X, y = y, scaleY = lerp(.78f, 1.08f, eased), compression = compression)
            }
            p < FLIGHT_END -> {
                val t = (p - RELEASE_END) / (FLIGHT_END - RELEASE_END)
                val extra = .12f * heightFactor * 4f * t * (1f - t)
                pose(x = SPRING_X, y = -SPRING_HEIGHT - extra,
                    scaleY = lerp(1.08f, 1f, ease(t)), compression = 1f)
            }
            p < LANDING_END -> {
                val t = (p - FLIGHT_END) / (LANDING_END - FLIGHT_END)
                val y = hermite(-SPRING_HEIGHT, -SPRING_HEIGHT * .60f,
                    4f * .12f * heightFactor / (FLIGHT_END - RELEASE_END), 0f, t, LANDING_END - FLIGHT_END)
                pose(x = SPRING_X, y = y, scaleY = lerp(1f, .87f, ease(t)), compression = -y / SPRING_HEIGHT)
            }
            p < SECOND_RELEASE_END -> {
                val t = (p - LANDING_END) / (SECOND_RELEASE_END - LANDING_END)
                val y = hermite(-SPRING_HEIGHT * .60f, -SPRING_HEIGHT, 0f,
                    (SPRING_HEIGHT - 4f * .08f * heightFactor) / (RETURN_END - SECOND_RELEASE_END),
                    t, SECOND_RELEASE_END - LANDING_END)
                pose(x = SPRING_X, y = y, scaleY = lerp(.87f, 1f, ease(t)), compression = -y / SPRING_HEIGHT)
            }
            p < RETURN_END -> {
                val t = (p - SECOND_RELEASE_END) / (RETURN_END - SECOND_RELEASE_END)
                val u = ease(t)
                val arc = .08f * heightFactor * 4f * t * (1f - t)
                val y = -SPRING_HEIGHT * (1f - t) - arc
                pose(x = SPRING_X * (1f - u), y = y, scaleY = lerp(1f, 1.04f, u), compression = 1f)
            }
            else -> pose(scaleY = JellyElasticMotion.landingScaleY(
                (p - RETURN_END) / (1f - RETURN_END) * JellyElasticMotion.LAND_SECONDS, 1.04f))
        }
    }

    private fun pose(
        x: Float = 0f,
        y: Float = 0f,
        scaleY: Float = 1f,
        compression: Float = 1f,
    ): Pose {
        return Pose(
            body = SpeciesCarePose(x = x, y = y, scaleX = 1f / scaleY, scaleY = scaleY),
            springCompression = compression,
        )
    }

    private fun ease(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun lerp(start: Float, end: Float, amount: Float): Float = start + (end - start) * amount

    private fun hermite(start: Float, end: Float, startSlope: Float, endSlope: Float,
        t: Float, duration: Float): Float {
        val u = t.coerceIn(0f, 1f)
        val h00 = 2f * u * u * u - 3f * u * u + 1f
        val h10 = u * u * u - 2f * u * u + u
        val h01 = -2f * u * u * u + 3f * u * u
        val h11 = u * u * u - u * u
        return h00 * start + h10 * duration * startSlope + h01 * end + h11 * duration * endSlope
    }
}
