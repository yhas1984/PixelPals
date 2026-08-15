package com.pixelpals.app.feature.overlay.behavior

import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.PetRandom

/** Debug-only substitutions for pets that are not yet promoted to production. */
internal object BuildVariantPetBehaviorFactory {
    fun create(petType: PetType, bridge: PetViewBridge, random: PetRandom): PetBehavior? = when (petType) {
        PetType.TELA -> TelaBehavior(bridge, random)
        else -> null
    }
}
