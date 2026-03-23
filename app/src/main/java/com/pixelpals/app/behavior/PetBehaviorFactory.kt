package com.pixelpals.app.behavior

import com.pixelpals.app.PetType

/**
 * PetBehaviorFactory — Creates the correct behavior for each pet type.
 */
object PetBehaviorFactory {

    fun create(petType: PetType, bridge: PetViewBridge): PetBehavior {
        return when (petType) {
            PetType.BLOOP -> BloopBehavior(bridge)
            PetType.NUBE_MICHI -> NubeMichiBehavior(bridge)
            PetType.JELLY -> JellyBehavior(bridge)
            PetType.CORGI -> CorgiBehavior(bridge)
            PetType.GINGER -> GingerBehavior(bridge)
            PetType.PATITO -> DuckBehavior(bridge)
            PetType.DIABLILLO -> ImpBehavior(bridge)
        }
    }
}
