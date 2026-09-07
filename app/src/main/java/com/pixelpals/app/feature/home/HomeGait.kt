package com.pixelpals.app.feature.home

import com.pixelpals.app.core.domain.PetType

/** Restrained secondary motion; the source frames provide the actual anatomy and steps. */
internal data class HomeGait(val bounce: Float, val sway: Float, val isFloating: Boolean = false) {
    companion object {
        private val FLOAT: HomeGait = HomeGait(0f, .5f, true)
        private val CRAWL: HomeGait = HomeGait(.6f, .4f)
        private val WALK: HomeGait = HomeGait(1.6f, .7f)
        private val BOUNCE: HomeGait = HomeGait(4f, .6f)
        private val WADDLE: HomeGait = HomeGait(2.4f, 2f)
        fun forPet(pet: PetType): HomeGait = when (pet) {
            PetType.BLOOP, PetType.NUBE_MICHI, PetType.ANGEL, PetType.DIABLILLO, PetType.PATITO -> FLOAT
            PetType.MOKI, PetType.TARO, PetType.MENTA, PetType.TELA -> CRAWL
            PetType.PIRU, PetType.YUKI -> WADDLE
            PetType.JELLY -> BOUNCE
            else -> WALK
        }
    }
}
