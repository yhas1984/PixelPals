package com.pixelpals.app.feature.overlay.behavior

import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.DefaultPetRandom
import com.pixelpals.app.core.motion.PetRandom

/**
 * PetBehaviorFactory — Creates the correct behavior for each pet type.
 */
object PetBehaviorFactory {

    fun create(petType: PetType, bridge: PetViewBridge, random: PetRandom = DefaultPetRandom()): PetBehavior {
        BuildVariantPetBehaviorFactory.create(petType, bridge, random)?.let { return it }
        return when (petType) {
            PetType.BLOOP -> BloopBehavior(bridge, random)
            PetType.NUBE_MICHI -> NubeMichiBehavior(bridge, random)
            PetType.JELLY -> JellyBehavior(bridge, random)
            PetType.CORGI -> CorgiBehavior(bridge, random)
            PetType.GINGER -> GingerBehavior(bridge, random)
            PetType.ANGEL -> AngelBehavior(bridge, random)
            PetType.PATITO -> DuckBehavior(bridge, random)
            PetType.DIABLILLO -> ImpBehavior(bridge, random)
            PetType.MOKI -> MokiBehavior(bridge, random)
            PetType.YUKI -> YukiRuntimeBehavior(bridge, random)
            PetType.PIRU -> PiruBehavior(bridge, random)
            PetType.TARO -> TaroRuntimeBehavior(bridge, random)
            PetType.MENTA -> MentaBehavior(bridge, random)
            PetType.TELA -> TelaBehavior(bridge, random)
            PetType.LUMI -> LumiBehavior(bridge, random)
        }
    }
}
