package com.pixelpals.app.feature.overlay.behavior

import com.pixelpals.app.core.motion.PetRandom
import com.pixelpals.app.core.runtime.pets.TaroRuntimeBrain
import com.pixelpals.app.core.runtime.pets.TaroRuntimeDefinition
import com.pixelpals.app.core.runtime.pets.TaroRuntimeState

/** Production behavior for Taro's approved quadruped Runtime V2. */
class TaroRuntimeBehavior(
    bridge: PetViewBridge,
    random: PetRandom,
) : RuntimePetBehavior<TaroRuntimeState>(
    bridge = bridge,
    random = random,
    definition = TaroRuntimeDefinition.value,
    brain = TaroRuntimeBrain(random),
    simulationSpeedMultiplier = TaroRuntimeDefinition.MOTION_SPEED_MULTIPLIER,
)
