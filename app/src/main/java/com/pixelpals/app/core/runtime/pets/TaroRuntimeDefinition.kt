package com.pixelpals.app.core.runtime.pets

import com.pixelpals.app.core.motion.PhysicsProfile
import com.pixelpals.app.core.runtime.PetBondBehavior
import com.pixelpals.app.core.runtime.PetBondStage
import com.pixelpals.app.core.runtime.PetDefinition
import com.pixelpals.app.core.runtime.PetInteractionProfile
import com.pixelpals.app.core.runtime.PetLocomotionProfile
import com.pixelpals.app.core.runtime.PetSurface
import com.pixelpals.app.core.runtime.PetTemperament

object TaroRuntimeDefinition {
    const val WALK_SPEED_PIXELS_PER_SECOND: Float = 42f
    const val MOTION_SPEED_MULTIPLIER: Float = 2f
    private const val WALK_CYCLE_SECONDS: Float = 8f * 0.42f

    val value: PetDefinition = PetDefinition(
        petId = "taro",
        atlasSpecPath = "pets/taro/taro_motion_v2.json",
        requiredClips = setOf(
            "idle",
            "idle_front",
            "walk",
            "turn",
            "hide",
            "peek",
            "front_social",
            "playful_wave",
            "playful_delight",
            "playful_surprise",
            "touch",
            "sleep",
            "curiosity",
        ),
        locomotion = PetLocomotionProfile(
            physicsProfile = PhysicsProfile.GROUND,
            primarySurface = PetSurface.FLOOR,
            speedPixelsPerSecond = WALK_SPEED_PIXELS_PER_SECOND,
            maximumFlingSpeedPixelsPerSecond = 2_200f,
            cycleDisplacementPixelsByClip = mapOf(
                "walk" to WALK_SPEED_PIXELS_PER_SECOND * WALK_CYCLE_SECONDS,
            ),
        ),
        interaction = PetInteractionProfile(
            holdHapticDurationMs = 28L,
            dragVisualLagPixels = 8f,
        ),
        temperament = PetTemperament(
            curiosity = 0.62f,
            affection = 0.72f,
            energy = 0.38f,
            caution = 0.58f,
            independence = 0.44f,
            playfulness = 0.42f,
        ),
        bondBehaviors = listOf(
            PetBondBehavior(PetBondStage.CLOSE, "playful_wave"),
            PetBondBehavior(PetBondStage.TRUSTED, "trusted_hold"),
            PetBondBehavior(PetBondStage.SOULMATE, "soulmate_greeting"),
        ),
    )
}
