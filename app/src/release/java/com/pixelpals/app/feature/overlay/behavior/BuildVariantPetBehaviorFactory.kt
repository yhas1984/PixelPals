package com.pixelpals.app.feature.overlay.behavior

import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.PetRandom

/**
 * Release has no experimental substitutions. Promoted candidates are selected
 * by the main factory only after their atlas and behavior gates pass.
 */
internal object BuildVariantPetBehaviorFactory {
    fun create(petType: PetType, bridge: PetViewBridge, random: PetRandom): PetBehavior? = null
}
