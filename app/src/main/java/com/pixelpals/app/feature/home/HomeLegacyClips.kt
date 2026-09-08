package com.pixelpals.app.feature.home

import com.pixelpals.app.R
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.feature.overlay.behavior.PetClipSpec

internal data class HomeLegacyClips(val resources: List<Int>, val clips: Map<String, PetClipSpec>) {
    companion object {
        fun forPet(pet: PetType): HomeLegacyClips = when (pet) {
            PetType.CORGI -> create(listOf(R.drawable.corgi_1, R.drawable.corgi_2, R.drawable.corgi_6, R.drawable.corgi_7,
                R.drawable.corgi_10, R.drawable.corgi_11, R.drawable.corgi_12, R.drawable.corgi_13), listOf(0), listOf(4, 5, 6, 7), listOf(1, 0), listOf(2, 3))
            PetType.PATITO -> create(listOf(R.drawable.patito_4, R.drawable.patito_5, R.drawable.patito_8, R.drawable.patito_9), listOf(2), listOf(0, 1), listOf(3, 2), listOf(2))
            PetType.DIABLILLO -> create(listOf(R.drawable.diablillo_0, R.drawable.diablillo_1, R.drawable.diablillo_2, R.drawable.diablillo_3), listOf(0, 1), listOf(2, 3), listOf(0, 1), listOf(0))
            PetType.BLOOP -> create(listOf(R.drawable.fantasma_1, R.drawable.fantasma_2, R.drawable.fantasma_3), listOf(0), listOf(0, 1), listOf(2, 0), listOf(0))
            PetType.NUBE_MICHI -> create(listOf(R.drawable.gato_0, R.drawable.gato_1, R.drawable.gato_3, R.drawable.gato_4), listOf(1), listOf(2, 3), listOf(1, 2), listOf(0))
            PetType.JELLY -> create(listOf(R.drawable.jelly_0, R.drawable.jelly_1, R.drawable.jelly_2, R.drawable.jelly_3, R.drawable.jelly_6, R.drawable.jelly_7), listOf(0), listOf(1, 2, 3, 0), listOf(4, 5), listOf(0))
            else -> error("Missing home clips for $pet")
        }
        private fun create(resources: List<Int>, idle: List<Int>, walk: List<Int>, play: List<Int>, sleep: List<Int>): HomeLegacyClips = HomeLegacyClips(resources,
            mapOf("idle" to PetClipSpec("idle", idle, true, 650), "walk" to PetClipSpec("walk", walk, true, 150),
                "play" to PetClipSpec("play", play, true, 450), "sleep" to PetClipSpec("sleep", sleep, false, 950),
                "wake" to PetClipSpec("wake", sleep.reversed(), false, 300)))
    }
}
