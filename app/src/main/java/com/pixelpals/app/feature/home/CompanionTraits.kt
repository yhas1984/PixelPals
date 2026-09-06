package com.pixelpals.app.feature.home

import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.database.CompanionHomeEntity

/** One learned temperament shared by the room and desktop. Effects remain bounded. */
data class CompanionTraits(val tempo: Float, val initiative: Float) {
    companion object {
        fun derive(pet: PetType, home: CompanionHomeEntity?, bond: Int): CompanionTraits {
            val profile = CompanionProfiles.forPet(pet)
            val play = (home?.playCount ?: 0).coerceAtMost(100)
            val touch = (home?.touchCount ?: 0).coerceAtMost(100)
            val feed = (home?.feedCount ?: 0).coerceAtMost(100)
            return CompanionTraits(
                (1f + (play - touch) * .01f).coerceIn(.85f, 1.15f),
                (.7f + profile.curiosity * .3f + bond.coerceIn(0, 100) * .002f + feed * .001f).coerceIn(.7f, 1.3f),
            )
        }
    }
}
