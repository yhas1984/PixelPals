package com.pixelpals.app.feature.home

import com.pixelpals.app.database.HomeDecorationEntity

/** Mirrors the bed used by the home actor, captured once per care session. */
object CareDecorationSelection {
    fun careToy(pet: com.pixelpals.app.core.domain.PetType, placements: List<HomeDecorationEntity>, favorite: String?): String? {
        val compatible: Set<String> = when (pet) {
            com.pixelpals.app.core.domain.PetType.CORGI, com.pixelpals.app.core.domain.PetType.GINGER -> setOf("ball", "yarn", "star_toy")
            else -> setOf("ball") // The starter object resolves to the species' own care toy.
        }
        return toy(placements.filter { it.decorationId in compatible }, favorite)
    }

    fun toy(placements: List<HomeDecorationEntity>, favorite: String?): String? =
        placements.firstOrNull { it.decorationId == favorite && DecorationCatalog.find(it.decorationId)?.kind == DecorationKind.TOY }?.decorationId
            ?: placements.firstOrNull { DecorationCatalog.find(it.decorationId)?.kind == DecorationKind.TOY }?.decorationId

    fun bed(placements: List<HomeDecorationEntity>): String? = placements.firstOrNull {
        DecorationCatalog.find(it.decorationId)?.kind == DecorationKind.BED
    }?.decorationId
}
