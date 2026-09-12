package com.pixelpals.app

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.care.TimeProvider
import com.pixelpals.app.data.repository.PixelPalsRepository
import com.pixelpals.app.database.AppDatabase
import com.pixelpals.app.feature.treasure.TreasureCatalog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryLocalePresentationTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: AppDatabase
    private lateinit var repository: PixelPalsRepository
    private var originalLocales: LocaleListCompat = LocaleListCompat.getEmptyLocaleList()

    @Before
    fun setUp() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            originalLocales = AppCompatDelegate.getApplicationLocales()
        }
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = PixelPalsRepository(context, database, object : TimeProvider {
            override fun getCurrentTimeMillis(): Long = 1_700_000_000_000L
        })
    }

    @After
    fun tearDown() {
        database.close()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AppCompatDelegate.setApplicationLocales(originalLocales)
        }
    }

    @Test
    fun oneRepositoryFollowsAppCompatLocaleForCatalogMemoriesAndTasks(): Unit = runBlocking {
        val english = readPresentation("en")
        val spanish = readPresentation("es")
        val englishAgain = readPresentation("en")
        val system = readPresentation("")
        assertEquals("Cherub", english.catalogName)
        assertEquals("Querubín", spanish.catalogName)
        assertEquals(english, englishAgain)
        assertEquals(context.getString(PetType.ANGEL.displayNameResId), system.catalogName)
        assertEquals(context.getString(TreasureCatalog.all.first().nameResourceId), system.treasureName)
        assertNotEquals(english.memoryTitle, spanish.memoryTitle)
        assertNotEquals(english.taskTitle, spanish.taskTitle)
        assertNotEquals(english.treasureName, spanish.treasureName)
    }

    private fun setLocale(language: String) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language))
        }
    }

    private suspend fun readPresentation(language: String): PresentationSnapshot {
        setLocale(language)
        val expected = if (language.isEmpty()) context.applicationContext else context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocales(LocaleList.forLanguageTags(language)) }
        )
        val catalogName = repository.getCatalog(PetType.CORGI).first { it.petType == PetType.ANGEL }.displayName
        val memories = repository.getMemories(PetType.CORGI).first()
        val tasks = repository.getDailyTasks(PetType.CORGI).first()
        val treasure = repository.getTreasureCollection(PetType.CORGI).items.first().name
        assertEquals(expected.getString(PetType.ANGEL.displayNameResId), catalogName)
        assertEquals(expected.getString(R.string.memory_first_day_title), memories.title)
        assertEquals(expected.getString(R.string.daily_task_check_in_title), tasks.title)
        assertEquals(expected.getString(R.string.daily_task_check_in_description), tasks.description)
        assertEquals(expected.getString(TreasureCatalog.all.first().nameResourceId), treasure)
        return PresentationSnapshot(catalogName, memories.title, tasks.title, treasure)
    }

    private data class PresentationSnapshot(
        val catalogName: String,
        val memoryTitle: String,
        val taskTitle: String,
        val treasureName: String,
    )
}
