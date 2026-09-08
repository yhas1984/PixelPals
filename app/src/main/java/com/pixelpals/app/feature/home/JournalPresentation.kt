package com.pixelpals.app.feature.home

import android.content.Context
import com.pixelpals.app.R
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.database.CompanionJournalEntity

object JournalPresentation {
    fun pet(event: CompanionJournalEntity): PetType = PetType.entries.firstOrNull {
        it.name.equals(event.petId, ignoreCase = true)
    } ?: PetType.CORGI

    fun objectTitle(context: Context, event: CompanionJournalEntity): String {
        val decoration: Decoration = DecorationCatalog.find(event.detail)
            ?: return context.getString(R.string.journal_object_unknown)
        return context.getString(R.string.journal_object, DecorationPresentation.title(context, decoration, pet(event)))
    }
}
