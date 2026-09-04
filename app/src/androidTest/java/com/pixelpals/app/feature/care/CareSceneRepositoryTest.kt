package com.pixelpals.app.feature.care

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.care.scene.CareSceneAction
import com.pixelpals.app.core.care.scene.CareSceneResult
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.data.repository.PixelPalsRepository
import com.pixelpals.app.database.AppDatabase
import com.pixelpals.app.database.PetStatusEntity
import com.pixelpals.app.database.PetBondEntity
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Dedicated in-memory DB + namespaced preferences: never clears installed user data. */
@RunWith(AndroidJUnit4::class)
class CareSceneRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: PixelPalsRepository
    private val preferenceNames: MutableSet<String> = mutableSetOf()
    private val target: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun setUp(): Unit = runBlocking {
        val prefix: String = "care-test-${UUID.randomUUID()}-"
        val isolated: Context = object : ContextWrapper(target) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                preferenceNames.add(prefix + name)
                return target.getSharedPreferences(prefix + name, mode)
            }
        }
        database = Room.inMemoryDatabaseBuilder(isolated, AppDatabase::class.java).build()
        repository = PixelPalsRepository(isolated, database)
        database.petStatusDao().upsert(PetStatusEntity("corgi", satiety = 40, energy = 40, hygiene = 40,
            lastInteractionAt = System.currentTimeMillis() - 120_000L))
        database.petBondDao().upsert(PetBondEntity("corgi", bondPoints = 20))
    }

    @After fun tearDown(): Unit {
        database.close()
        preferenceNames.forEach { target.deleteSharedPreferences(it) }
    }

    @Test fun feedReportsActualStateAndDoesNotRepeatDailyReward(): Unit = runBlocking {
        val first: CareSceneResult.Completed = repository.completeCareScene(PetType.CORGI, CareSceneAction.FEED) as CareSceneResult.Completed
        assertEquals(40, first.before.hunger)
        assertEquals(70, first.after.hunger)
        assertTrue(first.bondGain > 0)
        val second: CareSceneResult.Completed = repository.completeCareScene(PetType.CORGI, CareSceneAction.FEED) as CareSceneResult.Completed
        assertEquals(100, second.after.hunger)
        assertEquals(0, second.bondGain)
        assertEquals(0, second.coinGain)
    }

    @Test fun pettingUsesInteractionCooldownNotDailyCheckIn(): Unit = runBlocking {
        val first: CareSceneResult.Completed = repository.completeCareScene(PetType.CORGI, CareSceneAction.PET) as CareSceneResult.Completed
        assertEquals(3, first.bondGain)
        assertEquals(0, first.coinGain)
        val second: CareSceneResult.Completed = repository.completeCareScene(PetType.CORGI, CareSceneAction.PET) as CareSceneResult.Completed
        assertEquals(0, second.bondGain)
        assertTrue(repository.getDailyTasks(PetType.CORGI).none { it.completed })
    }

    @Test fun medicineRevalidatesAtTheCommitBoundary(): Unit = runBlocking {
        assertEquals(CareSceneResult.Unavailable, repository.completeCareScene(PetType.CORGI, CareSceneAction.MEDICINE))
        database.petStatusDao().upsert(PetStatusEntity("corgi", condition = "SICK", lastMedicineAt = System.currentTimeMillis()))
        assertEquals(CareSceneResult.Unavailable, repository.completeCareScene(PetType.CORGI, CareSceneAction.MEDICINE))
    }

    @Test fun maximumBondStillAllowsCareWithoutInventedPoints(): Unit = runBlocking {
        database.petBondDao().upsert(PetBondEntity("corgi", bondPoints = 100))
        val result: CareSceneResult.Completed = repository.completeCareScene(PetType.CORGI, CareSceneAction.FEED) as CareSceneResult.Completed
        assertEquals(0, result.bondGain)
        assertEquals(100, result.after.bond)
        assertEquals(70, result.after.hunger)
    }

    @Test fun stoppedPetCanBeCaredForWithoutStartingAService(): Unit = runBlocking {
        val result: CareSceneResult.Completed = repository.completeCareScene(PetType.CORGI, CareSceneAction.CLEAN) as CareSceneResult.Completed
        assertTrue(result.after.hygiene > result.before.hygiene)
    }

    @Test fun hibernationReportsWakingRatherThanPretendingToFeed(): Unit = runBlocking {
        database.petStatusDao().upsert(PetStatusEntity("corgi", condition = "HIBERNATING", satiety = 20))
        val result: CareSceneResult.Completed = repository.completeCareScene(PetType.CORGI, CareSceneAction.FEED) as CareSceneResult.Completed
        assertTrue(result.didWake)
    }
}
