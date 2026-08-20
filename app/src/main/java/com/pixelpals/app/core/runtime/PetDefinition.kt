package com.pixelpals.app.core.runtime

import com.pixelpals.app.core.motion.PhysicsProfile

data class PetTemperament(
    val curiosity: Float,
    val affection: Float,
    val energy: Float,
    val caution: Float,
    val independence: Float,
    val playfulness: Float,
) {
    init {
        val values: List<Float> = listOf(curiosity, affection, energy, caution, independence, playfulness)
        require(values.all { value -> value in 0f..1f }) { "Temperament values must be between zero and one" }
    }
}

data class PetLocomotionProfile(
    val physicsProfile: PhysicsProfile,
    val primarySurface: PetSurface,
    val speedPixelsPerSecond: Float,
    val maximumFlingSpeedPixelsPerSecond: Float,
    val cycleDisplacementPixelsByClip: Map<String, Float> = emptyMap(),
) {
    init {
        require(speedPixelsPerSecond > 0f && speedPixelsPerSecond.isFinite()) {
            "Locomotion speed must be finite and positive"
        }
        require(maximumFlingSpeedPixelsPerSecond > 0f && maximumFlingSpeedPixelsPerSecond.isFinite()) {
            "Maximum fling speed must be finite and positive"
        }
        require(cycleDisplacementPixelsByClip.keys.none(String::isBlank)) {
            "Locomotion clip ids cannot be blank"
        }
        require(cycleDisplacementPixelsByClip.values.all { displacement ->
            displacement > 0f && displacement.isFinite()
        }) { "Locomotion displacement values must be finite and positive" }
    }
}

data class PetInteractionProfile(
    val holdHapticDurationMs: Long,
    val dragVisualLagPixels: Float,
    val recoveryIntent: PetIntent = PetIntent.RECOVER,
) {
    init {
        require(holdHapticDurationMs >= 0L) { "Hold haptic duration cannot be negative" }
        require(dragVisualLagPixels >= 0f && dragVisualLagPixels.isFinite()) {
            "Drag visual lag must be finite and non-negative"
        }
    }
}

data class PetBondBehavior(
    val stage: PetBondStage,
    val actionId: String,
) {
    init {
        require(actionId.isNotBlank()) { "Bond behavior action id cannot be blank" }
    }
}

data class PetDefinition(
    val petId: String,
    val atlasSpecPath: String,
    val requiredClips: Set<String>,
    val locomotion: PetLocomotionProfile,
    val interaction: PetInteractionProfile,
    val temperament: PetTemperament,
    val bondBehaviors: List<PetBondBehavior>,
) {
    init {
        require(petId.isNotBlank()) { "Pet id cannot be blank" }
        require(atlasSpecPath.isNotBlank()) { "Atlas spec path cannot be blank" }
        require(requiredClips.isNotEmpty()) { "A pet definition must require at least one clip" }
        require(requiredClips.none(String::isBlank)) { "Required clip ids cannot be blank" }
        require(bondBehaviors.map(PetBondBehavior::stage).distinct().size == bondBehaviors.size) {
            "A pet definition can declare only one behavior per bond stage"
        }
    }
}
