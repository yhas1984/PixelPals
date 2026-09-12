package com.pixelpals.app.feature.home

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pixelpals.app.core.care.TimeProvider
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.data.repository.CoinSpendResult
import com.pixelpals.app.data.repository.PixelPalsRepository
import com.pixelpals.app.database.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DecorationPurchaseDiskTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName: String = "decoration-purchase-${System.nanoTime()}.db"
    private var database: AppDatabase? = null
    private val wall: Long = 2_000_000_000_000L

    @Before fun prepare(): Unit { context.deleteDatabase(databaseName) }

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
        return CompanionRepository(context, db, economy) to economy
    }

    @Test fun purchaseSurvivesDiskReopenAndCannotChargeTwice(): Unit = runBlocking {
        val (first, firstEconomy) = open()
        first.ensureHome(PetType.CORGI)
        firstEconomy.grantCoins(null, 100)
        assertEquals(CoinSpendResult.Purchased, first.purchase("fern"))
        assertEquals(70, firstEconomy.getCoinBalance(null))
        assertNotNull(first.dao.getOwned("fern"))
        database?.close(); database = null

        val (reopened, reopenedEconomy) = open()
        assertEquals(70, reopenedEconomy.getCoinBalance(null))
        assertEquals(CoinSpendResult.AlreadyOwned, reopened.purchase("fern"))
        assertEquals(70, reopenedEconomy.getCoinBalance(null))
        assertTrue(reopened.place(PetType.CORGI, "fern", 4, 0))
        database?.close(); database = null

        val (finalRepository, finalEconomy) = open()
        assertEquals(70, finalEconomy.getCoinBalance(null))
        assertEquals(1, finalRepository.dao.getOwned("fern")?.let { 1 } ?: 0)
        assertEquals(1, finalRepository.dao.getPlacements("corgi").count { it.decorationId == "fern" })
        val placement = finalRepository.dao.getPlacements("corgi").single { it.decorationId == "fern" }
        assertEquals(4, placement.column)
        assertEquals(0, placement.row)
        assertEquals(5, finalRepository.dao.getPlacements("corgi").size)
    }
}
