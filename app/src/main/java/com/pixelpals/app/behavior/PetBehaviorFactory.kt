package com.pixelpals.app.behavior

import com.pixelpals.app.PetType
import com.pixelpals.app.motion.DefaultPetRandom
import com.pixelpals.app.motion.MokiMotionController
import com.pixelpals.app.motion.PetRandom

/**
 * PetBehaviorFactory — Creates the correct behavior for each pet type.
 */
object PetBehaviorFactory {

    fun create(petType: PetType, bridge: PetViewBridge, random: PetRandom = DefaultPetRandom()): PetBehavior {
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
        }
    }
}
