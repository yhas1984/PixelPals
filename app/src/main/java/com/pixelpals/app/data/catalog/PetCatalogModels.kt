package com.pixelpals.app.data.catalog

import androidx.annotation.DrawableRes
import com.pixelpals.app.core.domain.PetType

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
