package com.pixelpals.app.database

import org.junit.Assert.assertEquals
import org.junit.Test

class EntityDefaultsTest {

    @Test
    fun petStatus_defaultsStayWithinPlayableRange() {
        val entity = PetStatusEntity("angel")
        assertEquals(92, entity.health)
        assertEquals(78, entity.energy)
        assertEquals(72, entity.satiety)
        assertEquals(84, entity.hygiene)
        assertEquals("HAPPY", entity.mood)
    }

    @Test
    fun ownershipDefaultsRemainExplicit() {
        val entity = OwnedProductEntity("pet_angel_premium", "pet", "purchase", 1L)
        assertEquals(true, entity.acknowledged)
    }

    @Test
    fun treasureAndGiftDefaultsPreserveCollectionHistory(): Unit {
        val treasure = TreasureItem("🌙", 2, 1L, 2L)
        val bond = PetBondEntity("bloop")
        assertEquals(2, treasure.totalFound)
        assertEquals("", bond.lastTreasureGiftDay)
        assertEquals(0, bond.treasuresGifted)
        assertEquals(0, bond.favoriteTreasuresGifted)
    }
}
