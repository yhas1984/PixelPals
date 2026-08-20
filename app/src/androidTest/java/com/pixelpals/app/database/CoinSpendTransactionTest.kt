package com.pixelpals.app.database

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.data.repository.CoinSpendResult
import com.pixelpals.app.data.repository.PixelPalsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CoinSpendTransactionTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var repository: PixelPalsRepository

    @Before
    fun setUp() {
        AppDatabase.getDatabase(context).clearAllTables()
        context.getSharedPreferences("pixelpals_coins", Context.MODE_PRIVATE).edit().clear().commit()
        repository = PixelPalsRepository(context)
        runBlocking { repository.grantCoins(null, 1_000) }
    }

    @Test
    fun simultaneousPetPurchasesDeductBalanceOnce() = runBlocking {
        val results: List<CoinSpendResult> = listOf(
            async(Dispatchers.IO) { repository.purchasePetWithCoins(PetType.TARO) },
            async(Dispatchers.IO) { repository.purchasePetWithCoins(PetType.TARO) },
        ).awaitAll()

        assertEquals(1, results.count { it == CoinSpendResult.Purchased })
        assertEquals(1, results.count { it == CoinSpendResult.AlreadyOwned })
        assertEquals(550, repository.getCoinBalance(null))
        assertTrue(repository.isProductOwned("pet_taro_premium"))
    }
}
