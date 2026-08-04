package com.pixelpals.app.catalog

import androidx.annotation.DrawableRes
import com.pixelpals.app.PetType

enum class CatalogItemState {
    LOCKED,
    OWNED,
    SELECTED
}

data class PetCatalogItem(
    val id: String,
    val displayName: String,
    val description: String,
    @param:DrawableRes val previewResId: Int,
    val petType: PetType?,
    val productId: String?,
    val isPremium: Boolean,
    val state: CatalogItemState,
    val badge: String? = null
)

data class AccessoryCatalogItem(
    val id: String,
    val productId: String,
    val displayName: String,
    val description: String,
    val emoji: String,
    val isPremium: Boolean,
    val packLabel: String,
    val offsetXRatio: Float,
    val offsetYRatio: Float,
    val scale: Float,
    val supportedPetIds: Set<String>,
    val coinPrice: Int? = null,
    val bondRequired: Int = 0,
)

enum class AccessoryPurchaseResult {
    PURCHASED,
    ALREADY_OWNED,
    NOT_ENOUGH_COINS,
    BOND_REQUIRED,
    NOT_AVAILABLE,
}
