package com.pixelpals.app.feature.store

import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.data.catalog.CatalogItemState
import com.pixelpals.app.data.catalog.PetCatalogItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreCatalogPolicyTest {
    @Test
    fun lockedPremiumExcludesBaseAndOwnedPets() {
        val items = listOf(
            item("base", false, CatalogItemState.OWNED),
            item("owned", true, CatalogItemState.OWNED),
            item("locked", true, CatalogItemState.LOCKED),
        )
        val result = StoreCatalogPolicy.lockedPremium(items)
        assertEquals(listOf("locked"), result.map { it.id })
    }

    @Test
    fun cosmeticActionPrioritizesEquippedState() {
        assertEquals(CosmeticAction.BUY, StoreCatalogPolicy.cosmeticAction(false, false))
        assertEquals(CosmeticAction.EQUIP, StoreCatalogPolicy.cosmeticAction(true, false))
        assertEquals(CosmeticAction.EQUIPPED, StoreCatalogPolicy.cosmeticAction(true, true))
        assertTrue(StoreCatalogPolicy.cosmeticAction(false, true) == CosmeticAction.EQUIPPED)
    }

    private fun item(id: String, isPremium: Boolean, state: CatalogItemState): PetCatalogItem =
        PetCatalogItem(id, id, id, 0, PetType.ANGEL, if (isPremium) id else null, isPremium, state)
}
