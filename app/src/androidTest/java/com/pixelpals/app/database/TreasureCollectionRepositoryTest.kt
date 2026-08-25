package com.pixelpals.app.database

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.care.TimeProvider
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.data.prefs.SelectedPetStore
import com.pixelpals.app.data.repository.PixelPalsRepository
import com.pixelpals.app.feature.treasure.TreasureBadge
import com.pixelpals.app.feature.treasure.TreasureCatalog
import com.pixelpals.app.feature.treasure.TreasureGiftResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TreasureCollectionRepositoryTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val database: AppDatabase = AppDatabase.getDatabase(context)
    private var currentTime: Long = FIXED_TIME
    private lateinit var repository: PixelPalsRepository
    private lateinit var selectedPetStore: SelectedPetStore

    @Before
    fun setUp(): Unit = runBlocking {
        database.clearAllTables()
        context.getSharedPreferences("pixelpals_coins", Context.MODE_PRIVATE).edit().clear().commit()
        selectedPetStore = SelectedPetStore(context)
        selectedPetStore.save(PetType.CORGI)
        selectedPetStore.setPetEnabled(true)
        repository = PixelPalsRepository(
            context = context,
            database = database,
            timeProvider = TimeProvider { currentTime },
        )
    }

    @Test
    fun newDiscoveryIsPermanentAndRewardsCoinsAndOneBondPoint(): Unit = runBlocking {
        database.petBondDao().upsert(PetBondEntity(petId = "corgi", bondPoints = 9))
        val result = repository.maybeAwardTreasureFromInteraction(PetType.CORGI)
        val collection = repository.getTreasureCollection(PetType.CORGI)
        assertEquals(true, result?.isNewDiscovery)
        assertEquals(10, result?.coinsGained)
        assertEquals(1, result?.bondGained)
        assertNull(result?.milestone)
        assertEquals(1, collection.summary.discoveredCount)
        assertEquals(19, collection.items.size)
        assertEquals(10, repository.getCoinBalance(PetType.CORGI))
        assertEquals(10, repository.getStatusSnapshot(PetType.CORGI).bond)
    }

    @Test
    fun duplicateAddsInventoryWithoutAddingBond(): Unit = runBlocking {
        seedAllTreasures(count = 1)
        database.treasureCollectionStateDao().upsert(
            TreasureCollectionStateEntity(lastRewardedMilestone = 19, completedAt = FIXED_TIME),
        )
        database.petBondDao().upsert(PetBondEntity(petId = "corgi", bondPoints = 20, activeMinutes = 4))
        val result = repository.maybeAwardTreasureFromActiveMinute(PetType.CORGI)
        assertFalse(requireNotNull(result).isNewDiscovery)
        assertEquals(0, result.bondGained)
        assertEquals(10, result.coinsGained)
        assertEquals(20, repository.getStatusSnapshot(PetType.CORGI).bond)
        assertEquals(20, database.treasureDao().getAllTreasuresSnapshot().sumOf { item -> item.totalFound })
    }

    @Test
    fun fifthDiscoveryAwardsBronzeExactlyOnce(): Unit = runBlocking {
        seedTreasures(TreasureCatalog.all.take(4).map { definition -> definition.emoji })
        database.petBondDao().upsert(PetBondEntity(petId = "corgi", bondPoints = 36))
        val result = requireNotNull(repository.maybeAwardTreasureFromInteraction(PetType.CORGI))
        assertEquals(TreasureBadge.BRONZE, result.milestone)
        assertEquals(35, result.coinsGained)
        assertEquals(35, repository.getCoinBalance(PetType.CORGI))
        repository.getTreasureCollection(PetType.CORGI)
        assertEquals(35, repository.getCoinBalance(PetType.CORGI))
    }

    @Test
    fun retroactiveMilestonesAreReconciledOnlyOnce(): Unit = runBlocking {
        seedTreasures(TreasureCatalog.all.take(10).map { definition -> definition.emoji })
        repository.getTreasureCollection(PetType.CORGI)
        val firstBalance: Int = repository.getCoinBalance(PetType.CORGI)
        repository.getTreasureCollection(PetType.CORGI)
        val secondBalance: Int = repository.getCoinBalance(PetType.CORGI)
        assertEquals(75, firstBalance)
        assertEquals(firstBalance, secondBalance)
        assertEquals(10, database.treasureCollectionStateDao().getState()?.lastRewardedMilestone)
    }

    @Test
    fun finalDiscoveryRecordsCollectorAndCreatesSpecialMemory(): Unit = runBlocking {
        seedTreasures(TreasureCatalog.all.take(18).map { definition -> definition.emoji })
        database.treasureCollectionStateDao().upsert(
            TreasureCollectionStateEntity(lastRewardedMilestone = 15),
        )
        database.petBondDao().upsert(PetBondEntity(petId = "corgi", bondPoints = 36))
        val result = requireNotNull(repository.maybeAwardTreasureFromInteraction(PetType.CORGI))
        val state = requireNotNull(database.treasureCollectionStateDao().getState())
        val memories = repository.getMemories(PetType.CORGI)
        assertEquals(TreasureBadge.LEGENDARY, result.milestone)
        assertEquals("corgi", state.finalCollectorPetId)
        assertEquals(FIXED_TIME, state.completedAt)
        assertTrue(memories.any { memory -> memory.id == "treasure_collection_complete" })
    }

    @Test
    fun favoriteGiftAddsFiveBondAndSecondGiftDoesNotConsume(): Unit = runBlocking {
        selectedPetStore.save(PetType.BLOOP)
        database.treasureDao().insertTreasure(TreasureItem("🌙", 2, FIXED_TIME, FIXED_TIME, 2))
        database.petBondDao().upsert(PetBondEntity(petId = "bloop", bondPoints = 10))
        val first = repository.giftTreasure(PetType.BLOOP, "nap_moon")
        val second = repository.giftTreasure(PetType.BLOOP, "nap_moon")
        val success = first as TreasureGiftResult.Success
        assertTrue(success.isFavorite)
        assertEquals(5, success.bondGained)
        assertEquals(1, success.remainingCount)
        assertEquals(TreasureGiftResult.AlreadyGiftedToday, second)
        assertEquals(1, database.treasureDao().getTreasure("🌙")?.count)
        assertEquals(15, database.petBondDao().getByPetId("bloop")?.bondPoints)
    }

    @Test
    fun maximumBondNeedsConfirmationAndKeepsZeroInventoryRow(): Unit = runBlocking {
        selectedPetStore.save(PetType.BLOOP)
        database.treasureDao().insertTreasure(TreasureItem("🌙", 1, FIXED_TIME, FIXED_TIME, 1))
        database.petBondDao().upsert(PetBondEntity(petId = "bloop", bondPoints = 100))
        val first = repository.giftTreasure(PetType.BLOOP, "nap_moon")
        assertEquals(TreasureGiftResult.MaximumBondConfirmationRequired, first)
        assertEquals(1, database.treasureDao().getTreasure("🌙")?.count)
        val confirmed = repository.giftTreasure(PetType.BLOOP, "nap_moon", acceptsNoBondReward = true)
        val success = confirmed as TreasureGiftResult.Success
        assertEquals(0, success.bondGained)
        assertEquals(0, database.treasureDao().getTreasure("🌙")?.count)
        assertEquals(1, database.treasureDao().getTreasure("🌙")?.totalFound)
        val collection = repository.getTreasureCollection(PetType.BLOOP)
        assertEquals(1, collection.summary.discoveredCount)
        assertFalse(requireNotNull(collection.items.firstOrNull { item -> item.id == "nap_moon" }).canGift)
    }

    @Test
    fun stoppedPetCannotReceiveOrConsumeGift(): Unit = runBlocking {
        database.treasureDao().insertTreasure(TreasureItem("💎", 1, FIXED_TIME, FIXED_TIME, 1))
        selectedPetStore.setPetEnabled(false)
        val result = repository.giftTreasure(PetType.CORGI, "bright_gem")
        assertEquals(TreasureGiftResult.PetNotActive, result)
        assertEquals(1, database.treasureDao().getTreasure("💎")?.count)
    }

    @Test
    fun giftLimitResetsOnNextLocalDay(): Unit = runBlocking {
        database.treasureDao().insertTreasure(TreasureItem("💎", 2, FIXED_TIME, FIXED_TIME, 2))
        repository.giftTreasure(PetType.CORGI, "bright_gem")
        currentTime += ONE_DAY_MILLIS
        val nextDay = repository.giftTreasure(PetType.CORGI, "bright_gem")
        assertTrue(nextDay is TreasureGiftResult.Success)
        assertEquals(0, database.treasureDao().getTreasure("💎")?.count)
        assertEquals(2, database.petBondDao().getByPetId("corgi")?.treasuresGifted)
    }

    private suspend fun seedAllTreasures(count: Int): Unit {
        TreasureCatalog.all.forEach { definition ->
            database.treasureDao().insertTreasure(
                TreasureItem(definition.emoji, count, FIXED_TIME, FIXED_TIME, count),
            )
        }
    }

    private suspend fun seedTreasures(emoji: List<String>): Unit {
        emoji.forEach { value ->
            database.treasureDao().insertTreasure(TreasureItem(value, 1, FIXED_TIME, FIXED_TIME, 1))
        }
    }

    private companion object {
        const val FIXED_TIME: Long = 1_777_118_400_000L
        const val ONE_DAY_MILLIS: Long = 86_400_000L
    }
}
