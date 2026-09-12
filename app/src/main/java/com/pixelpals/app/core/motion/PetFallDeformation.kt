package com.pixelpals.app.core.motion

import com.pixelpals.app.core.domain.PetType
import kotlin.math.abs
import kotlin.math.exp

/** Secondary shape motion for soft bodies only; translation belongs to PetPhysics. */
object PetFallDeformation {
    fun heightScale(pet: PetType, verticalSpeed: Float, spriteSize: Int,
        currentScale: Float, deltaSeconds: Float, reducedMotion: Boolean): Float {
        val elasticity: Float = when (pet) {
            PetType.JELLY -> .15f
            PetType.BLOOP, PetType.NUBE_MICHI -> .04f
            else -> 0f
        }
        if (elasticity == 0f || reducedMotion) return 1f
        val speed: Float = if (verticalSpeed.isFinite()) abs(verticalSpeed) else 0f
        val target: Float = 1f + elasticity * (speed / (spriteSize.coerceAtLeast(1) * 5f)).coerceIn(0f, 1f)
        val current: Float = if (currentScale.isFinite()) currentScale.coerceIn(1f, 1f + elasticity) else 1f
        val delta: Float = if (deltaSeconds.isFinite()) deltaSeconds.coerceAtLeast(0f) else 0f
        val blend: Float = 1f - exp(-delta / .08f)
        return current + (target - current) * blend
    }
}
