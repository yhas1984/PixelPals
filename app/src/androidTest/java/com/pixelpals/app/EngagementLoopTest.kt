package com.pixelpals.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.data.catalog.AccessoryPurchaseResult
import com.pixelpals.app.data.repository.PixelPalsRepository
import com.pixelpals.app.database.AppDatabase
import com.pixelpals.app.status.CareAction
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun dailyCareEarnsAccessoryAndEquipsItWithoutRepeatRewards() = runBlocking {
        repository.applyCareAction(PetType.CORGI, CareAction.FEED)
        repository.applyCareAction(PetType.CORGI, CareAction.CLEAN)
        repository.applyCareAction(PetType.CORGI, CareAction.PLAY)
        val beforeRepeatedCare = repository.getStatusSnapshot(PetType.CORGI)
        repository.applyCareAction(PetType.CORGI, CareAction.FEED)
        val afterRepeatedCare = repository.getStatusSnapshot(PetType.CORGI)

        assertEquals(40, beforeRepeatedCare.softCurrency)
        assertEquals(24, beforeRepeatedCare.bond)
        assertEquals(beforeRepeatedCare.softCurrency, afterRepeatedCare.softCurrency)
        assertEquals(beforeRepeatedCare.bond, afterRepeatedCare.bond)

        repository.applyCareAction(PetType.CORGI, CareAction.REST)
        assertEquals(
            AccessoryPurchaseResult.PURCHASED,
            repository.purchaseAccessoryWithCoins(PetType.CORGI, "star_trail"),
        )
        assertEquals(5, repository.getStatusSnapshot(PetType.CORGI).softCurrency)
        assertTrue(repository.equipAccessory(PetType.CORGI, "star_trail"))
        assertEquals("star_trail", repository.getEquippedAccessory(PetType.CORGI)?.id)
        assertEquals(
            AccessoryPurchaseResult.ALREADY_OWNED,
            repository.purchaseAccessoryWithCoins(PetType.CORGI, "star_trail"),
        )
    }

    @Test
    fun repeatedTapDoesNotFarmBondDuringCooldown() = runBlocking {
        val first = repository.recordInteraction(PetType.MOKI)
        val second = repository.recordInteraction(PetType.MOKI)
        assertEquals(3, first.bond)
        assertEquals(first.bond, second.bond)
    }
}
