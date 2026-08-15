package com.pixelpals.app.feature.overlay.behavior

import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.PetRandom

/** Debug-only V2 substitutions. Release is deliberately backed by the V1 factory. */
internal object BuildVariantPetBehaviorFactory {
    fun create(petType: PetType, bridge: PetViewBridge, random: PetRandom): PetBehavior? = when (petType) {
        PetType.TELA -> TelaBehavior(bridge, random)
        PetType.TARO -> TaroV2Behavior(bridge, random)
        else -> null
    }
}
