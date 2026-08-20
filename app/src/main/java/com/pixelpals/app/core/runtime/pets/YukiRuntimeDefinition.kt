package com.pixelpals.app.core.runtime.pets

import com.pixelpals.app.core.motion.PhysicsProfile
import com.pixelpals.app.core.runtime.PetBondBehavior
import com.pixelpals.app.core.runtime.PetBondStage
import com.pixelpals.app.core.runtime.PetDefinition
import com.pixelpals.app.core.runtime.PetInteractionProfile
import com.pixelpals.app.core.runtime.PetLocomotionProfile
import com.pixelpals.app.core.runtime.PetSurface
import com.pixelpals.app.core.runtime.PetTemperament

object YukiRuntimeDefinition {
    const val WALK_SPEED_PIXELS_PER_SECOND: Float = 70f
    const val MELT_ENTER_CELSIUS: Float = 40f
    const val MELT_EXIT_CELSIUS: Float = 38f
    private const val WALK_CYCLE_SECONDS: Float = 2f * 0.22f

    val value: PetDefinition = PetDefinition(
        petId = "yuki",
        atlasSpecPath = "pets/yuki/yuki_sheet_v1.json",
        requiredClips = setOf(
            "idle",
            "blink",
            "walk",
            "jump",
            "happy",
            "melt",
            "touch",
            "sleep",
        ),
        locomotion = PetLocomotionProfile(
            physicsProfile = PhysicsProfile.GROUND,
            primarySurface = PetSurface.FLOOR,
            speedPixelsPerSecond = WALK_SPEED_PIXELS_PER_SECOND,
            maximumFlingSpeedPixelsPerSecond = 1_800f,
            cycleDisplacementPixelsByClip = mapOf(
                "walk" to WALK_SPEED_PIXELS_PER_SECOND * WALK_CYCLE_SECONDS,
            ),
        ),
        interaction = PetInteractionProfile(
            holdHapticDurationMs = 22L,
            dragVisualLagPixels = 6f,
        ),
        temperament = PetTemperament(
            curiosity = 0.82f,
            affection = 0.68f,
            energy = 0.46f,
            caution = 0.28f,
            independence = 0.32f,
            playfulness = 0.74f,
        ),
        bondBehaviors = listOf(
            PetBondBehavior(PetBondStage.CLOSE, "happy"),
            PetBondBehavior(PetBondStage.TRUSTED, "touch"),
            PetBondBehavior(PetBondStage.SOULMATE, "snow_dream"),
        ),
    )
}
