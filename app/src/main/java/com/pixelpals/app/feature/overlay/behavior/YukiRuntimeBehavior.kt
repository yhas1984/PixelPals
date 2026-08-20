package com.pixelpals.app.feature.overlay.behavior

import com.pixelpals.app.core.motion.PetRandom
import com.pixelpals.app.core.runtime.pets.YukiRuntimeBrain
import com.pixelpals.app.core.runtime.pets.YukiRuntimeDefinition
import com.pixelpals.app.core.runtime.pets.YukiRuntimeState

/** Production Runtime V2 behavior for Yuki. */
class YukiRuntimeBehavior(
    bridge: PetViewBridge,
    random: PetRandom,
) : RuntimePetBehavior<YukiRuntimeState>(
    bridge = bridge,
    random = random,
    definition = YukiRuntimeDefinition.value,
    brain = YukiRuntimeBrain(random),
)
