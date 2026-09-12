package com.pixelpals.app.core.motion

import kotlin.math.abs

/** Shared, renderer-independent deformation curves for Jelly. */
object JellyElasticMotion {
    // Painted gel support after removing the exterior sticker border.
    const val GROUND: Float = 718f / 768f
    const val PREPARE_SECONDS: Float = .28f
    const val LAND_SECONDS: Float = .44f
    const val TOUCH_SECONDS: Float = .94f
    const val STRIDE: Float = 105f

    data class HomePose(val scaleY: Float, val lift: Float)

    fun ease(t: Float): Float {
        val clamped = t.coerceIn(0f, 1f)
        return clamped * clamped * (3f - 2f * clamped)
    }

    fun prepareScaleY(seconds: Float): Float =
        lerp(1f, .82f, ease(seconds / PREPARE_SECONDS))

    fun landingScaleY(seconds: Float, initialScaleY: Float = 1f): Float {
        val elapsed = seconds.coerceAtLeast(0f)
        return when {
            elapsed < .10f -> lerp(initialScaleY, .82f, ease(elapsed / .10f))
            elapsed < .29f -> lerp(.82f, 1.045f, ease((elapsed - .10f) / .19f))
            elapsed < LAND_SECONDS -> lerp(1.045f, 1f, ease((elapsed - .29f) / (LAND_SECONDS - .29f)))
            else -> 1f
        }
    }

    fun touchScaleY(seconds: Float, initialScaleY: Float = 1f): Float {
        val elapsed = seconds.coerceAtLeast(0f)
        return when {
            elapsed < .18f -> lerp(initialScaleY, .74f, ease(elapsed / .18f))
            elapsed < .40f -> lerp(.74f, .78f, ease((elapsed - .18f) / .22f))
            elapsed < .72f -> lerp(.78f, 1.035f, ease((elapsed - .40f) / .32f))
            elapsed < TOUCH_SECONDS -> lerp(1.035f, 1f, ease((elapsed - .72f) / (TOUCH_SECONDS - .72f)))
            else -> 1f
        }
    }

    fun airScaleY(velocityY: Float, spriteSize: Float): Float {
        val denominator = spriteSize.coerceAtLeast(.001f) * 8f
        return 1f + (abs(velocityY) / denominator).coerceAtMost(.12f)
    }

    fun homePose(distance: Float): HomePose {
        val phase = (abs(distance) % STRIDE) / STRIDE
        return when {
            phase < .14f -> HomePose(prepareScaleY(phase / .14f * PREPARE_SECONDS), 0f)
            phase < .72f -> {
                val flight = (phase - .14f) / .58f
                val scale = if (flight < .5f) {
                    lerp(.82f, 1.12f, ease(flight / .5f))
                } else {
                    lerp(1.12f, 1f, ease((flight - .5f) / .5f))
                }
                HomePose(scale, -18f * 4f * flight * (1f - flight))
            }
            else -> {
                val landingElapsed = (phase - .72f) / .28f * LAND_SECONDS
                HomePose(landingScaleY(landingElapsed), 0f)
            }
        }
    }

    private fun lerp(start: Float, end: Float, amount: Float): Float =
        start + (end - start) * amount
}
