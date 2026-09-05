package com.pixelpals.app.feature.home

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pixelpals.app.core.care.TimeProvider
import com.pixelpals.app.core.care.scene.CareSceneAction
import com.pixelpals.app.core.care.scene.CareSceneResult
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.data.repository.CoinSpendResult
import com.pixelpals.app.data.repository.PixelPalsRepository
import com.pixelpals.app.database.AppDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompanionRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var economy: PixelPalsRepository
    private lateinit var repository: CompanionRepository
    private var wall: Long = 2_000_000_000_000
    private var uptime: Long = 50_000
    private var boot: Int = 1
    @Before fun prepare(): Unit {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        economy = PixelPalsRepository(context, db, object : TimeProvider { override fun getCurrentTimeMillis(): Long = wall })
        repository = createRepository()
    }
    @After fun close(): Unit { db.close() }
    private fun createRepository(): CompanionRepository = CompanionRepository(context, db, economy, { wall }, { uptime }, { boot })

    @Test fun homesAreIndependentAndAdoptionDoesNotResetProgress(): Unit = runBlocking {
        economy.recordInteraction(PetType.CORGI)
        val bond: Int = economy.getStatusSnapshot(PetType.CORGI).bond
        for (pet: PetType in PetType.entries) {
            repository.ensureHome(pet); repository.adopt(pet, pet.name)
            assertEquals(pet.name, repository.dao.getHome(pet.name.lowercase())?.nickname)
            assertEquals(4, repository.dao.getPlacements(pet.name.lowercase()).size)
        }
        repository.setEnvironment(PetType.CORGI, HomeEnvironment.GARDEN)
        repository.adopt(PetType.CORGI, "Maple")
        assertEquals("COZY", repository.dao.getHome("taro")?.environment)
        assertEquals(bond, economy.getStatusSnapshot(PetType.CORGI).bond)
        assertEquals(1, repository.dao.observeJournal("corgi").first().count { it.kind == "adopt" })
    }
    @Test fun simultaneousPurchasesChargeOnlyOnceAndDoNotAllowRewardPurchases(): Unit = runBlocking {
        repository.ensureHome(PetType.CORGI)
        economy.grantCoins(null, 100)
        val results: List<CoinSpendResult> = coroutineScope { List(8) { async(Dispatchers.IO) { repository.purchase("fern") } }.awaitAll() }
        assertEquals(1, results.count { it == CoinSpendResult.Purchased })
        assertEquals(70, economy.getCoinBalance(null))
        assertTrue(repository.purchase("mushrooms") is CoinSpendResult.Failure)
        assertEquals(CoinSpendResult.InsufficientFunds, repository.purchase("moon_lamp"))
    }
    @Test fun placementRequiresOwnershipFreeCellAndSurvivesRepositoryRecreation(): Unit = runBlocking {
        repository.ensureHome(PetType.CORGI)
        assertFalse(repository.place(PetType.CORGI, "fern", 4, 0))
        assertFalse(repository.place(PetType.CORGI, "ball", -1, 0))
        assertFalse(repository.place(PetType.CORGI, "ball", 0, 2))
        assertTrue(repository.place(PetType.CORGI, "ball", 4, 0))
        repository = createRepository()
        assertEquals(4, repository.dao.getPlacements("corgi").first { it.decorationId == "ball" }.column)
        repository.store(PetType.CORGI, "ball")
        assertTrue(repository.dao.getPlacements("corgi").none { it.decorationId == "ball" })
        assertNotNull(repository.dao.getOwned("ball"))
    }
    @Test fun travelFreezesCareAndCanOnlyBeClaimedOnceAfterRecreation(): Unit = runBlocking {
        repository.ensureHome(PetType.CORGI)
        assertTrue(repository.startExpedition(PetType.CORGI, ExpeditionDestination.MEADOW))
        val request: String = repository.dao.getExpedition()!!.requestId
        val before = economy.getStatusSnapshot(PetType.CORGI)
        assertFalse(repository.startExpedition(PetType.BLOOP, ExpeditionDestination.FOREST))
        assertFalse(repository.finishExpedition(request))
        assertEquals(CareSceneResult.Unavailable, economy.completeCareScene(PetType.CORGI, CareSceneAction.FEED))
        wall += 5 * 60_000; uptime += 5 * 60_000
        assertEquals(before, economy.getStatusSnapshot(PetType.CORGI))
        repository = createRepository()
        assertTrue(repository.finishExpedition(request))
        val coins: Int = economy.getCoinBalance(null)
        assertFalse(repository.finishExpedition(request))
        assertEquals(coins, economy.getCoinBalance(null))
        assertEquals(1, db.treasureDao().getAllTreasuresSnapshot().sumOf { it.count })
        assertNotNull(repository.dao.getOwned("wildflowers"))
        assertEquals(1, repository.dao.observeJournal("corgi").first().count { it.kind == "expedition" })
    }
    @Test fun clockChangesAndRebootRemainBounded(): Unit = runBlocking {
        repository.ensureHome(PetType.CORGI)
        repository.startExpedition(PetType.CORGI, ExpeditionDestination.MEADOW)
        val request: String = repository.dao.getExpedition()!!.requestId
        wall += 86_400_000; uptime += 100
        repository.refreshExpedition()
        assertEquals(100L, repository.dao.getExpedition()!!.elapsedMs)
        assertFalse(repository.finishExpedition(request))
        boot += 1; uptime = 0; wall += 5 * 60_000
        repository.refreshExpedition()
        assertEquals(5 * 60_000L, repository.dao.getExpedition()!!.elapsedMs)
    }
    @Test fun cancellationNeverGrantsDiscoveriesAndCannotClaimAnOldRequest(): Unit = runBlocking {
        repository.ensureHome(PetType.CORGI)
        repository.startExpedition(PetType.CORGI, ExpeditionDestination.MEADOW)
        val request: String = repository.dao.getExpedition()!!.requestId
        assertTrue(repository.finishExpedition(request, cancel = true))
        assertEquals(0, economy.getCoinBalance(null))
        assertTrue(db.treasureDao().getAllTreasuresSnapshot().isEmpty())
        repository.startExpedition(PetType.CORGI, ExpeditionDestination.FOREST)
        assertFalse(repository.finishExpedition(request, cancel = true))
        assertNotNull(repository.dao.getExpedition())
    }
    @Test fun interactionLearnsWithoutDuplicateJournalEntries(): Unit = runBlocking {
        repository.ensureHome(PetType.CORGI)
        assertTrue(economy.completeCareScene(PetType.CORGI, CareSceneAction.PLAY) is CareSceneResult.Completed)
        assertEquals(1, repository.dao.getHome("corgi")?.playCount)
        economy.completeCareScene(PetType.CORGI, CareSceneAction.PLAY)
        assertEquals(1, repository.dao.getHome("corgi")?.playCount)
        assertEquals(1, repository.dao.observeJournal("corgi").first().size)
    }
}
