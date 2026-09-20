package com.pixelpals.app.feature.home

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pixelpals.app.core.care.TimeProvider
import com.pixelpals.app.core.care.scene.CareSceneAction
import com.pixelpals.app.core.care.scene.CareSceneResult
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.data.catalog.CosmeticCatalog
import com.pixelpals.app.data.repository.CoinSpendResult
import com.pixelpals.app.data.repository.PixelPalsRepository
import com.pixelpals.app.database.AppDatabase
import com.pixelpals.app.database.TreasureItem
import com.pixelpals.app.feature.treasure.TreasureCatalog
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

    @Test fun desktopObjectsAreOptionalAndRemovalPreservesInventory(): Unit = runBlocking {
        assertEquals("", repository.ensureHome(PetType.CORGI).desktopObject)
        assertTrue(repository.chooseObject(PetType.CORGI, "ball", true))
        assertEquals("ball", createRepository().ensureHome(PetType.CORGI).desktopObject)
        repository.removeDesktopObject(PetType.CORGI)
        assertEquals("", createRepository().ensureHome(PetType.CORGI).desktopObject)
        assertNotNull(repository.dao.getOwned("ball"))
        assertTrue(repository.dao.getPlacements("corgi").any { it.decorationId == "ball" })
    }

    @Test fun treasureSelectionIsPerPetAndOnlyAllowsDiscoveredCatalogItems(): Unit = runBlocking {
        val first = TreasureCatalog.all.first()
        val second = TreasureCatalog.all[1]
        db.treasureDao().insertTreasure(TreasureItem(first.emoji, 0, wall, wall, 1))

        assertTrue(repository.selectTreasure(PetType.CORGI, first.id))
        assertTrue(repository.selectTreasure(PetType.BLOOP, ""))
        assertTrue(repository.selectTreasure(PetType.CORGI, null))
        assertFalse(repository.selectTreasure(PetType.BLOOP, second.id))
        assertFalse(repository.selectTreasure(PetType.BLOOP, "unknown_treasure"))
        assertEquals(null, db.companionDao().getHome("corgi")?.selectedTreasureId)
        assertEquals("", db.companionDao().getHome("bloop")?.selectedTreasureId)
    }

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
    @Test fun cosmeticEquippingRequiresOwnershipAndNullUnequips(): Unit = runBlocking {
        val cosmetic = CosmeticCatalog.all(context).first()
        economy.grantCoins(null, requireNotNull(cosmetic.coinPrice))
        assertEquals(CoinSpendResult.Purchased,
            economy.purchaseCosmeticWithCoins("corgi", cosmetic.id))
        economy.setEquippedCosmetic("corgi", cosmetic.id)
        assertEquals(cosmetic.id, economy.getEquippedCosmetic("corgi"))
        val unowned = CosmeticCatalog.all(context).first { it.id != cosmetic.id }
        var rejected = false
        try {
            economy.setEquippedCosmetic("corgi", unowned.id)
        } catch (_: IllegalStateException) {
            rejected = true
        }
        assertTrue("An unowned cosmetic must be rejected", rejected)
        assertEquals("Rejecting an unowned cosmetic must preserve the current one",
            cosmetic.id, economy.getEquippedCosmetic("corgi"))
        economy.setEquippedCosmetic("corgi", null)
        assertNull(economy.getEquippedCosmetic("corgi"))
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
    @Test fun unknownDestinationCanBeCancelledAfterReopeningWithoutNeglectOrRewards(): Unit = runBlocking {
        repository.ensureHome(PetType.CORGI)
        val before = economy.getStatusSnapshot(PetType.CORGI)
        assertTrue(repository.startExpedition(PetType.CORGI, ExpeditionDestination.MEADOW))
        val trip = requireNotNull(repository.dao.getExpedition())
        repository.dao.saveExpedition(trip.copy(destination = "future_destination"))
        wall += 8 * 86_400_000L; uptime += 8 * 86_400_000L
        repository = createRepository()
        assertFalse(repository.finishExpedition(trip.requestId))
        assertTrue(repository.finishExpedition(trip.requestId, cancel = true))
        assertNull(repository.dao.getExpedition())
        assertFalse(repository.finishExpedition(trip.requestId, cancel = true))
        val after = economy.getStatusSnapshot(PetType.CORGI)
        assertEquals(before.hunger, after.hunger)
        assertEquals(before.energy, after.energy)
        assertEquals(before.hygiene, after.hygiene)
        assertEquals(before.condition, after.condition)
        assertEquals(0, economy.getCoinBalance(null))
        assertTrue(db.treasureDao().getAllTreasuresSnapshot().isEmpty())
        assertNull(repository.dao.getOwned("wildflowers"))
        assertTrue(repository.startExpedition(PetType.CORGI, ExpeditionDestination.FOREST))
    }

    @Test fun unknownPetCannotClaimButDoesNotLockFutureTravel(): Unit = runBlocking {
        repository.ensureHome(PetType.CORGI)
        assertTrue(repository.startExpedition(PetType.CORGI, ExpeditionDestination.MEADOW))
        val trip = requireNotNull(repository.dao.getExpedition())
        repository.dao.saveExpedition(trip.copy(petId = "future_pet", elapsedMs = ExpeditionDestination.MEADOW.durationMs))
        assertFalse(repository.finishExpedition(trip.requestId))
        assertTrue(repository.finishExpedition(trip.requestId, cancel = true))
        assertNull(repository.dao.getExpedition())
        assertTrue(db.treasureDao().getAllTreasuresSnapshot().isEmpty())
        assertTrue(repository.startExpedition(PetType.CORGI, ExpeditionDestination.FOREST))
    }
    @Test fun interactionLearnsWithoutDuplicateJournalEntries(): Unit = runBlocking {
        repository.ensureHome(PetType.CORGI)
        assertTrue(economy.completeCareScene(PetType.CORGI, CareSceneAction.PLAY) is CareSceneResult.Completed)
        assertEquals(1, repository.dao.getHome("corgi")?.playCount)
        economy.completeCareScene(PetType.CORGI, CareSceneAction.PLAY)
        assertEquals(1, repository.dao.getHome("corgi")?.playCount)
        assertEquals(1, repository.dao.observeJournal("corgi").first().size)
    }

    @Test fun delayedReturnDoesNotCountTravelAsAbsence(): Unit = runBlocking {
        val selection = com.pixelpals.app.data.prefs.SelectedPetStore(context)
        val originalPet = selection.load()
        val originalEnabled = selection.isPetEnabled()
        try {
            selection.save(PetType.CORGI)
            selection.setPetEnabled(true)
            repository.ensureHome(PetType.CORGI)
            economy.applyCareAction(PetType.CORGI, com.pixelpals.app.status.CareAction.FEED)
            val before = economy.getStatusSnapshot(PetType.CORGI)
            assertTrue(repository.startExpedition(PetType.CORGI, ExpeditionDestination.MEADOW))
            val request = repository.dao.getExpedition()!!.requestId
            wall += 8L * 86_400_000L
            uptime += 8L * 86_400_000L
            assertTrue(repository.finishExpedition(request))
            val after = economy.getStatusSnapshot(PetType.CORGI)
            assertEquals("Travel must not trigger absence hibernation", before.condition, after.condition)
        } finally {
            selection.save(originalPet)
            selection.setPetEnabled(originalEnabled)
        }
    }

    @Test fun concurrentStatusRefreshDoesNotOverwriteCompletedFeeding(): Unit = runBlocking {
        val selection = com.pixelpals.app.data.prefs.SelectedPetStore(context)
        val originalPet = selection.load()
        val originalEnabled = selection.isPetEnabled()
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        try {
            selection.save(PetType.CORGI)
            selection.setPetEnabled(true)
            economy.getStatusSnapshot(PetType.CORGI)
            wall += 30L * 60_000L
            val reads = java.util.concurrent.atomic.AtomicInteger()
            val refreshRepository = PixelPalsRepository(context, db, object : TimeProvider {
                override fun getCurrentTimeMillis(): Long {
                    if (reads.incrementAndGet() == 2) {
                        entered.countDown()
                        check(release.await(10, java.util.concurrent.TimeUnit.SECONDS))
                    }
                    return wall
                }
            })
            val refresh = async(Dispatchers.IO) { refreshRepository.getStatusSnapshot(PetType.CORGI) }
            assertTrue(withContext(Dispatchers.IO) { entered.await(5, java.util.concurrent.TimeUnit.SECONDS) })
            val feeding = async(Dispatchers.IO) { economy.applyCareAction(PetType.CORGI, com.pixelpals.app.status.CareAction.FEED) }
            delay(100)
            release.countDown()
            refresh.await()
            val fed = feeding.await()
            assertEquals("A stale refresh must not replace completed care", fed.hunger,
                economy.getStatusSnapshot(PetType.CORGI).hunger)
        } finally {
            release.countDown()
            selection.save(originalPet)
            selection.setPetEnabled(originalEnabled)
        }
    }
    @Test fun favoriteIsLearnedFromCompletedObjectUse(): Unit = runBlocking {
        repository.ensureHome(PetType.CORGI)
        economy.grantCoins(null, 100)
        repository.purchase("yarn")
        repository.recordObjectUse(PetType.CORGI, "ball")
        wall += 31_000
        repository.recordObjectUse(PetType.CORGI, "ball")
        wall += 31_000
        repository.recordObjectUse(PetType.CORGI, "yarn")
        assertEquals("ball", repository.dao.getHome("corgi")?.favoriteObject)
        wall += 31_000
        repository.recordObjectUse(PetType.CORGI, "yarn")
        wall += 31_000
        repository.recordObjectUse(PetType.CORGI, "yarn")
        assertEquals("yarn", repository.dao.getHome("corgi")?.favoriteObject)
    }
}
