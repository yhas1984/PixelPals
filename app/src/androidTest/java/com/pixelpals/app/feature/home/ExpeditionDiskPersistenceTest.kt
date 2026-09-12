package com.pixelpals.app.feature.home

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pixelpals.app.core.care.TimeProvider
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.data.repository.PixelPalsRepository
import com.pixelpals.app.database.AppDatabase
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies an expedition remains single-claim after closing and reopening Room. */
@RunWith(AndroidJUnit4::class)
class ExpeditionDiskPersistenceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "expedition-disk-${System.nanoTime()}.db"
    private var database: AppDatabase? = null
    private var wall = 2_000_000_000_000L
    private var uptime = 50_000L
    private var boot = 1

    @Before fun prepare(): Unit {
        context.deleteDatabase(databaseName)
    }

    @After fun cleanup(): Unit {
        database?.close()
        database = null
        context.deleteDatabase(databaseName)
    }

    private fun open(): Pair<CompanionRepository, PixelPalsRepository> {
        val db = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).build()
        database = db
        val economy = PixelPalsRepository(context, db, object : TimeProvider {
            override fun getCurrentTimeMillis(): Long = wall
        })
        return CompanionRepository(context, db, economy, { wall }, { uptime }, { boot }) to economy
    }

    @Test fun completionSurvivesDiskReopenAndSecondClaimIsIdempotent(): Unit = runBlocking {
        val (firstRepository, _) = open()
        firstRepository.ensureHome(PetType.CORGI)
        assertTrue(firstRepository.startExpedition(PetType.CORGI, ExpeditionDestination.MEADOW))
        val requestId = requireNotNull(firstRepository.dao.getExpedition()).requestId
        database?.close()
        database = null

        wall += ExpeditionDestination.MEADOW.durationMs
        uptime += ExpeditionDestination.MEADOW.durationMs
        val (claimRepository, claimEconomy) = open()
        assertNotNull(claimRepository.dao.getExpedition())
        assertTrue(claimRepository.finishExpedition(requestId))
        assertNull(claimRepository.dao.getExpedition())
        val treasureCount = requireNotNull(database).treasureDao().getAllTreasuresSnapshot().sumOf { it.count }
        val inventoryCount = claimRepository.dao.getOwned("wildflowers")?.let { 1 } ?: 0
        val coinBalance = claimEconomy.getCoinBalance(null)
        val journalCount = claimRepository.dao.observeJournal("corgi").first()
            .count { it.id == "expedition:$requestId" }
        assertEquals(1, treasureCount)
        assertEquals(1, inventoryCount)
        assertEquals(1, journalCount)
        database?.close()
        database = null

        val (reopenedRepository, reopenedEconomy) = open()
        assertFalse(reopenedRepository.finishExpedition(requestId))
        assertEquals(coinBalance, reopenedEconomy.getCoinBalance(null))
        assertEquals(treasureCount, requireNotNull(database).treasureDao().getAllTreasuresSnapshot().sumOf { it.count })
        assertEquals(1, reopenedRepository.dao.getOwned("wildflowers")?.let { 1 } ?: 0)
        assertEquals(1, reopenedRepository.dao.observeJournal("corgi").first()
            .count { it.id == "expedition:$requestId" })
        assertEquals(4, reopenedRepository.dao.getPlacements("corgi").size)
    }
    @Test fun rebootWithClockRollbackKeepsProgressAndCancellationCannotAffectTheNextTrip(): Unit = runBlocking {
        val (first, firstEconomy) = open()
        first.ensureHome(PetType.CORGI)
        val balance: Int = firstEconomy.getCoinBalance(null)
        assertTrue(first.startExpedition(PetType.CORGI, ExpeditionDestination.MEADOW))
        val firstId: String = requireNotNull(first.dao.getExpedition()).requestId
        val progress: Long = ExpeditionDestination.MEADOW.durationMs / 2
        wall += progress
        uptime += progress
        first.refreshExpedition()
        assertEquals(progress, requireNotNull(first.dao.getExpedition()).elapsedMs)
        database?.close()
        database = null

        boot += 1
        uptime = 1_000L
        wall -= 86_400_000L
        val (afterBoot, _) = open()
        afterBoot.refreshExpedition()
        val resumed = requireNotNull(afterBoot.dao.getExpedition())
        assertEquals(progress, resumed.elapsedMs)
        assertEquals(boot, resumed.bootCount)
        assertFalse("A backward clock must not make the reward ready", afterBoot.finishExpedition(firstId))
        uptime += 5_000L
        wall += 5_000L
        afterBoot.refreshExpedition()
        assertEquals(progress + 5_000L, requireNotNull(afterBoot.dao.getExpedition()).elapsedMs)
        assertTrue(afterBoot.finishExpedition(firstId, cancel = true))
        database?.close()
        database = null

        val (reopened, economy) = open()
        assertNull(reopened.dao.getExpedition())
        assertEquals(balance, economy.getCoinBalance(null))
        assertTrue(requireNotNull(database).treasureDao().getAllTreasuresSnapshot().isEmpty())
        assertNull(reopened.dao.getOwned("wildflowers"))
        assertFalse(reopened.dao.observeJournal("corgi").first().any { it.id == "expedition:$firstId" })
        assertTrue(reopened.startExpedition(PetType.CORGI, ExpeditionDestination.FOREST))
        val nextId: String = requireNotNull(reopened.dao.getExpedition()).requestId
        assertFalse(reopened.finishExpedition(firstId))
        assertFalse(reopened.finishExpedition(firstId, cancel = true))
        assertEquals(nextId, requireNotNull(reopened.dao.getExpedition()).requestId)
        assertTrue(requireNotNull(database).treasureDao().getAllTreasuresSnapshot().isEmpty())
    }

}
