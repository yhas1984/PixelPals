package com.pixelpals.app.feature.home

import com.pixelpals.app.database.HomeDecorationEntity

/** Mirrors the bed used by the home actor, captured once per care session. */
object CareDecorationSelection {
    fun toy(placements: List<HomeDecorationEntity>, favorite: String?): String? =
        placements.firstOrNull { it.decorationId == favorite && DecorationCatalog.find(it.decorationId)?.kind == DecorationKind.TOY }?.decorationId
            ?: placements.firstOrNull { DecorationCatalog.find(it.decorationId)?.kind == DecorationKind.TOY }?.decorationId

    fun bed(placements: List<HomeDecorationEntity>): String? = placements.firstOrNull {
        DecorationCatalog.find(it.decorationId)?.kind == DecorationKind.BED
    }?.decorationId
}
