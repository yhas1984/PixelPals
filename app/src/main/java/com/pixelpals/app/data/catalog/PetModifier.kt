package com.pixelpals.app.data.catalog

import androidx.annotation.RawRes

/**
 * Modificadores de comportamiento aplicados por un accesorio equipado.
 * Se evalúan en [com.pixelpals.app.feature.overlay.behavior.BaseBehavior]
 * y en [com.pixelpals.app.PetView] para alterar render y física.
 */
sealed class PetModifier {

    /** Multiplica la velocidad base del pet (1.0 = neutral, 1.1 = +10%). */
    data class SpeedBoost(val multiplier: Float) : PetModifier()

    /** Alas/gadgets que elevan: cae más lento y salta más alto. */
    data class WingLift(
        val liftMultiplier: Float = 0.15f,      // +15% altura de salto
        val airTimeMultiplier: Float = 0.25f,   // 25% más lento al caer
        val flapClip: String = "flap",
    ) : PetModifier()

    /** Tipo de trail de partículas a dibujar detrás del sprite. */
    data class TrailParticles(val type: ParticleType, val density: Int = 4) : PetModifier()

    /** Sonido opcional al activar un gesto o al moverse. */
    data class SoundEffect(@RawRes val soundResId: Int) : PetModifier()

    /** Override de animación en un modo concreto del behavior (ej. jetpack_hop). */
    data class AnimationOverride(val modeName: String) : PetModifier()
}

enum class ParticleType {
    STARDUST,
    FIRE,
    BUBBLES,
    SPARKLES,
}
