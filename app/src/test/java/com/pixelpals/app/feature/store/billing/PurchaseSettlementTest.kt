package com.pixelpals.app.feature.store.billing

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PurchaseSettlementTest {
    @Test fun interruptionAfterGrantCanRetryWithoutLosingOrDuplicatingCoins() = runBlocking {
        var persisted = false
        var balance = 0
        val grant: suspend () -> Int = {
            if (persisted) 0 else { persisted = true; balance += 100; 1 }
        }
        try {
            fulfillBeforeSettlement(grant) { assertEquals(100, balance); error("process interrupted") }
            fail("Expected interruption")
        } catch (_: IllegalStateException) { }
        val retry = fulfillBeforeSettlement(grant) { true }
        assertTrue(retry.settled)
        assertEquals(0, retry.newlyGranted)
        assertEquals(100, balance)
    }
    @Test fun failedLocalGrantNeverConsumesThePurchase() = runBlocking {
        var consumed = false
        try {
            fulfillBeforeSettlement({ error("storage unavailable") }, { consumed = true; true })
            fail("Expected storage error")
        } catch (_: IllegalStateException) { }
        assertFalse(consumed)
    }
}
