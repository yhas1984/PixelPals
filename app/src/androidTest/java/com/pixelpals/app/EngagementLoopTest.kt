package com.pixelpals.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.data.repository.PixelPalsRepository
import com.pixelpals.app.database.AppDatabase
import com.pixelpals.app.status.CareAction
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EngagementLoopTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var repository: PixelPalsRepository

    @Before
    fun resetDatabase() {
        AppDatabase.getDatabase(context).clearAllTables()
        repository = PixelPalsRepository(context)
    }

    @Test
    fun dailyCareEarnsCoinsWithoutRepeatRewards() = runBlocking {
        repository.applyCareAction(PetType.CORGI, CareAction.FEED)
        repository.applyCareAction(PetType.CORGI, CareAction.CLEAN)
        repository.applyCareAction(PetType.CORGI, CareAction.PLAY)
        val beforeRepeatedCare = repository.getStatusSnapshot(PetType.CORGI)
        // Las monedas van al MONEDERO GLOBAL (v1.6+), no a la fila del pet.
        val walletBefore = repository.getCoinBalance(PetType.CORGI)
        repository.applyCareAction(PetType.CORGI, CareAction.FEED)
        val afterRepeatedCare = repository.getStatusSnapshot(PetType.CORGI)
        val walletAfter = repository.getCoinBalance(PetType.CORGI)

        assertEquals(40, walletBefore)
        assertEquals(24, beforeRepeatedCare.bond)
        assertEquals(walletBefore, walletAfter)
        assertEquals(beforeRepeatedCare.bond, afterRepeatedCare.bond)
    }

    @Test
    fun repeatedTapDoesNotFarmBondDuringCooldown() = runBlocking {
        val first = repository.recordInteraction(PetType.MOKI)
        val second = repository.recordInteraction(PetType.MOKI)
        assertEquals(3, first.bond)
        assertEquals(first.bond, second.bond)
    }
}
