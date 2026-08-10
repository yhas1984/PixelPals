package com.pixelpals.app.feature.overlay.behavior

import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.DefaultPetRandom
import com.pixelpals.app.core.motion.MokiMotionController
import com.pixelpals.app.core.motion.PetRandom

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
            PetType.YUKI -> AtlasPetBehavior(bridge, random, "pets/yuki/yuki_sheet_v1.json", AtlasPetBehavior.Personality(
                baseSpeed = 70f, idleFrameMs = 240, walkFrameMs = 260, wanderChance = 0.30f,
                bobAmplitude = 2.0f, bobSpeed = 1.3f, touchFrameMs = 300, happyChance = 0.20f
            ))
            PetType.PIRU -> AtlasPetBehavior(bridge, random, "pets/piru/piru_sheet_v1.json", AtlasPetBehavior.Personality(
                baseSpeed = 120f, idleFrameMs = 220, walkFrameMs = 190, wanderChance = 0.42f,
                bobAmplitude = 2.8f, bobSpeed = 1.9f, touchFrameMs = 240, happyChance = 0.22f
            ))
            PetType.TARO -> AtlasPetBehavior(bridge, random, "pets/taro/taro_sheet_v1.json", AtlasPetBehavior.Personality(
                baseSpeed = 38f, idleFrameMs = 400, walkFrameMs = 440, wanderChance = 0.18f,
                bobAmplitude = 1.2f, bobSpeed = 0.9f, touchFrameMs = 320, happyChance = 0.15f
            ))
            PetType.MENTA -> AtlasPetBehavior(bridge, random, "pets/menta/menta_sheet_v1.json", AtlasPetBehavior.Personality(
                baseSpeed = 95f, idleFrameMs = 230, walkFrameMs = 210, wanderChance = 0.36f,
                bobAmplitude = 2.4f, bobSpeed = 1.6f, touchFrameMs = 250, happyChance = 0.20f
            ))
        }
    }
}
