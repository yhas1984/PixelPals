package com.pixelpals.app.feature.store

import com.pixelpals.app.data.catalog.AccessoryPurchaseResult
import com.pixelpals.app.data.catalog.CoinProduct
import com.pixelpals.app.data.catalog.PremiumPack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests del catálogo de tienda sin dependencias de Android.
 * Verifican shape, conteo y ordenamiento de productos monetizables.
 */
class StorePurchaseFlowTest {

    @Test
    fun `CoinProduct catalog has 4 packs`() {
        assertEquals(4, CoinProduct.CATALOG.size)
        assertTrue(CoinProduct.CATALOG.all { it.coinAmount > 0 })
    }

    @Test
    fun `PremiumPack catalog has 3 packs with accessories`() {
        assertEquals(3, PremiumPack.CATALOG.size)
        PremiumPack.CATALOG.forEach { pack ->
            assertTrue("Pack ${pack.productId} should have at least 1 accessory", pack.accessoryIds.isNotEmpty())
            assertTrue("Pack ${pack.productId} should have bonus coins > 0", pack.bonusCoins > 0)
        }
    }

    @Test
    fun `coin amounts are ascending`() {
        val amounts = CoinProduct.CATALOG.map { it.coinAmount }
        assertEquals(amounts.sorted(), amounts)
    }

    @Test
    fun `best value flag is on Mega Pack`() {
        val mega = CoinProduct.CATALOG.first { it.productId == "coins_large" }
        assertTrue("Mega Pack should be best value", mega.bestValueFlag)
    }

    @Test
    fun `AccessoryPurchaseResult has 5 outcomes`() {
        assertEquals(5, AccessoryPurchaseResult.entries.size)
        assertTrue(AccessoryPurchaseResult.entries.contains(AccessoryPurchaseResult.PURCHASED))
        assertTrue(AccessoryPurchaseResult.entries.contains(AccessoryPurchaseResult.NOT_ENOUGH_COINS))
    }

    @Test
    fun `celestial pack contains halo and wings`() {
        val pack = PremiumPack.CATALOG.first { it.productId == "pack_celestial" }
        assertEquals(2, pack.accessoryIds.size)
        assertTrue(pack.accessoryIds.contains("halo_glow"))
        assertTrue(pack.accessoryIds.contains("celestial_wings"))
    }

    @Test
    fun `demonic pack contains crown and demonic wings`() {
        val pack = PremiumPack.CATALOG.first { it.productId == "pack_demonic" }
        assertEquals(2, pack.accessoryIds.size)
        assertTrue(pack.accessoryIds.contains("royal_crown"))
        assertTrue(pack.accessoryIds.contains("demonic_wings"))
    }

    @Test
    fun `adventure pack contains jetpack and pilot glasses`() {
        val pack = PremiumPack.CATALOG.first { it.productId == "pack_adventure" }
        assertEquals(2, pack.accessoryIds.size)
        assertTrue(pack.accessoryIds.contains("duck_jetpack"))
        assertTrue(pack.accessoryIds.contains("pilot_glasses"))
    }
}
