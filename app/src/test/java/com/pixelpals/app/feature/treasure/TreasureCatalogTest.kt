package com.pixelpals.app.feature.treasure

import com.pixelpals.app.status.PetPersonality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TreasureCatalogTest {
    @Test
    fun catalogHasNineteenStableUniqueTreasures(): Unit {
        assertEquals(19, TreasureCatalog.all.size)
        assertEquals(19, TreasureCatalog.all.map { treasure -> treasure.id }.toSet().size)
        assertEquals(19, TreasureCatalog.all.map { treasure -> treasure.emoji }.toSet().size)
    }

    @Test
    fun everyPersonalityHasThreeKnownFavorites(): Unit {
        val catalogEmoji: Set<String> = TreasureCatalog.all.map { treasure -> treasure.emoji }.toSet()
        PetPersonality.entries.forEach { personality ->
            val favorites: Set<String> = TreasureCatalog.getFavorites(personality)
            assertEquals(3, favorites.size)
            assertTrue(catalogEmoji.containsAll(favorites))
        }
    }

    @Test
    fun collectionBadgesExposeApprovedMilestonesAndRewards(): Unit {
        val milestones: List<Pair<Int, Int>> = TreasureBadge.entries
            .filterNot { badge -> badge == TreasureBadge.NONE }
            .map { badge -> badge.milestone to badge.rewardCoins }
        assertEquals(listOf(5 to 25, 10 to 50, 15 to 75, 19 to 150), milestones)
        assertEquals(TreasureBadge.BRONZE, TreasureBadge.getForProgress(9))
        assertEquals(TreasureBadge.SILVER, TreasureBadge.getNext(9))
    }
}
