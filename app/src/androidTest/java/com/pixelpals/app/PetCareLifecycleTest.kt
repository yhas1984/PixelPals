package com.pixelpals.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.care.PetCondition
import com.pixelpals.app.core.care.TimeProvider
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.data.prefs.SelectedPetStore
import com.pixelpals.app.data.repository.PixelPalsRepository
import com.pixelpals.app.database.AppDatabase
import com.pixelpals.app.database.PetStatusEntity
import com.pixelpals.app.status.CareAction
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PetCareLifecycleTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val database: AppDatabase = AppDatabase.getDatabase(context)
    private val selectedPetStore = SelectedPetStore(context)
    private lateinit var clock: MutableTimeProvider
    private lateinit var repository: PixelPalsRepository

    @Before
    fun setUp() = runBlocking {
        database.clearAllTables()
        selectedPetStore.save(PetType.CORGI)
        selectedPetStore.setPetEnabled(true)
        clock = MutableTimeProvider(System.currentTimeMillis())
        repository = PixelPalsRepository(context, database, clock)
    }

    @Test
    fun activeTimeUsesBalancedHalfHourDecay() = runBlocking {
        repository.getStatusSnapshot(PetType.CORGI)
        clock.advanceMinutes(30)

        val snapshot = repository.getStatusSnapshot(PetType.CORGI)

        assertEquals(69, snapshot.hunger)
        assertEquals(76, snapshot.energy)
        assertEquals(83, snapshot.hygiene)
    }

    @Test
    fun nonSelectedPetDoesNotDecay() = runBlocking {
        val before = repository.getStatusSnapshot(PetType.CORGI)
        selectedPetStore.save(PetType.MOKI)
        clock.advanceHours(48)

        val after = repository.getStatusSnapshot(PetType.CORGI)

        assertEquals(before.hunger, after.hunger)
        assertEquals(before.energy, after.energy)
        assertEquals(before.hygiene, after.hygiene)
    }

    @Test
    fun medicineIsFreeAndCannotBeRepeatedDuringCooldown() = runBlocking {
        val now = clock.getCurrentTimeMillis()
        database.petStatusDao().upsert(
            PetStatusEntity(
                petId = "corgi",
                energy = 30,
                satiety = 30,
                hygiene = 40,
                condition = PetCondition.SICK.name,
                conditionStartedAt = now,
                criticalNeedsStartedAt = now - 25L * HOUR_MILLIS,
                lastUpdatedAt = now,
                lastInteractionAt = now,
            )
        )

        val first = repository.applyCareAction(PetType.CORGI, CareAction.MEDICINE)
        val repeated = repository.applyCareAction(PetType.CORGI, CareAction.MEDICINE)

        assertEquals(30, first.recoveryProgress)
        assertEquals(first.recoveryProgress, repeated.recoveryProgress)
        assertEquals(0, repository.getCoinBalance(PetType.CORGI))
    }

    private companion object {
        const val HOUR_MILLIS: Long = 60L * 60L * 1_000L
    }
}

private class MutableTimeProvider(private var currentTimeMillis: Long) : TimeProvider {
    override fun getCurrentTimeMillis(): Long = currentTimeMillis

    fun advanceMinutes(minutes: Int) {
        currentTimeMillis += minutes * 60L * 1_000L
    }

    fun advanceHours(hours: Int) {
        currentTimeMillis += hours * 60L * 60L * 1_000L
    }
}
