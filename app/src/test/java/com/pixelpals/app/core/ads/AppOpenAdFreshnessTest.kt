package com.pixelpals.app.core.ads

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppOpenAdFreshnessTest {
    @Test
    fun acceptsAdsYoungerThanFourHours() {
        val loadedAt = 1_000L
        assertTrue(AppOpenAdFreshness.isFresh(loadedAt, loadedAt + 3 * 60 * 60 * 1_000L))
    }

    @Test
    fun rejectsMissingFutureAndExpiredLoadTimes() {
        val fourHours = 4 * 60 * 60 * 1_000L
        assertFalse(AppOpenAdFreshness.isFresh(0L, 1_000L))
        assertFalse(AppOpenAdFreshness.isFresh(2_000L, 1_000L))
        assertFalse(AppOpenAdFreshness.isFresh(1_000L, 1_000L + fourHours))
    }
}
