package com.pixelpals.app.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pixelpals.app.data.repository.PixelPalsRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProcessedPurchaseRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun samePurchaseTokenGrantsCoinsOnlyOnce() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repository = PixelPalsRepository(context, db)

            assertTrue(
                repository.grantPlayPurchaseOnce(
                    purchaseToken = "token-1",
                    productId = "coins_small",
                    quantity = 1,
                    purchaseTime = 1L,
                    source = "test",
                )
            )
            assertFalse(
                repository.grantPlayPurchaseOnce(
                    purchaseToken = "token-1",
                    productId = "coins_small",
                    quantity = 1,
                    purchaseTime = 1L,
                    source = "test-retry",
                )
            )
            assertEquals(100, repository.getCoinBalance(null))

            assertTrue(
                repository.grantPlayPurchaseOnce(
                    purchaseToken = "token-2",
                    productId = "coins_small",
                    quantity = 1,
                    purchaseTime = 2L,
                    source = "test",
                )
            )
            assertEquals(200, repository.getCoinBalance(null))
        } finally {
            db.close()
        }
    }
}
