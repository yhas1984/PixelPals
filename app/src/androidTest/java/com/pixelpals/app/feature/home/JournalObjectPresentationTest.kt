package com.pixelpals.app.feature.home

import android.content.res.Configuration
import android.graphics.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.R
import com.pixelpals.app.database.CompanionJournalEntity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import kotlinx.coroutines.flow.first

@RunWith(AndroidJUnit4::class)
class JournalObjectPresentationTest {
    @Test fun journalRecoversSavedObjectEventsOncePerDayWithoutChangingLearning(): Unit = kotlinx.coroutines.runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = androidx.room.Room.inMemoryDatabaseBuilder(context, com.pixelpals.app.database.AppDatabase::class.java).build()
        try {
            val dao = db.companionDao()
            val base = 1_700_000_000_000L
            val events = listOf(
                CompanionJournalEntity("adopt", "corgi", "adopt", "", base),
                CompanionJournalEntity("first", "corgi", "object", "ball", base + 1),
                CompanionJournalEntity("latest", "corgi", "object", "ball", base + 2),
                CompanionJournalEntity("bed", "corgi", "object", "linen_bed", base + 3),
                CompanionJournalEntity("tomorrow", "corgi", "object", "ball", base + 86_400_000L),
                CompanionJournalEntity("other", "yuki", "object", "ball", base + 4))
            events.forEach { dao.remember(it) }
            val journal = dao.observeJournal("corgi").first()
            assertEquals(listOf("tomorrow", "bed", "latest", "adopt"), journal.map { it.id })
            assertEquals("ball", dao.learnedFavorite("corgi"))
        } finally { db.close() }
    }

    @Test fun objectMemoriesUseLocalizedNamesAndSpeciesArtwork() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val event = CompanionJournalEntity("test", "yuki", "object", "ball", 1L)
            for (language in listOf("en", "es")) {
                val localized = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) })
                val expectedName = DecorationPresentation.title(localized, requireNotNull(DecorationCatalog.find("ball")), JournalPresentation.pet(event))
                assertEquals(localized.getString(R.string.journal_object, expectedName), JournalPresentation.objectTitle(localized, event))
                assertEquals(localized.getString(R.string.journal_object_unknown), JournalPresentation.objectTitle(localized, event.copy(detail = "retired_item")))
            }
            fun render(record: CompanionJournalEntity): IntArray {
                val bitmap = Bitmap.createBitmap(400, 304, Bitmap.Config.ARGB_8888)
                try {
                    val view = MemoryIllustration(context, record.kind, record.detail, JournalPresentation.pet(record))
                    view.layout(0, 0, 400, 304)
                    view.draw(Canvas(bitmap))
                    return IntArray(400 * 304).also { bitmap.getPixels(it, 0, 400, 0, 0, 400, 304) }
                } finally { bitmap.recycle() }
            }
            val snowball = render(event)
            assertTrue("Use the event pet, not always Corgi", !snowball.contentEquals(render(event.copy(petId = "corgi"))))
            assertTrue("Use the actual object, not the generic heart", !snowball.contentEquals(render(event.copy(kind = "care"))))
        }
    }
}
