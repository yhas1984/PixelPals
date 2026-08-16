package com.pixelpals.app.feature.store

import com.pixelpals.app.data.catalog.CatalogItemState
import com.pixelpals.app.data.catalog.PetCatalogItem

enum class CosmeticAction {
    BUY,
    EQUIP,
    EQUIPPED,
}

object StoreCatalogPolicy {
    fun lockedPremium(items: List<PetCatalogItem>): List<PetCatalogItem> =
        items.filter { it.isPremium && it.state == CatalogItemState.LOCKED }

    fun cosmeticAction(isOwned: Boolean, isEquipped: Boolean): CosmeticAction = when {
        isEquipped -> CosmeticAction.EQUIPPED
        isOwned -> CosmeticAction.EQUIP
        else -> CosmeticAction.BUY
    }
}
