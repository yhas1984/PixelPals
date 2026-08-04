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

/**
 * Catálogo de un accesorio. Carga desde JSON (ver [AccessoryCatalog]).
 *
 * El campo [visual] define cómo se dibuja (emoji o sprite con frames animados).
 * El campo [modifiers] define efectos en el comportamiento (velocidad, partículas, etc.).
 * El campo [slot] define la capa de render (HEAD, FACE, BACK, GADGET, BODY).
 */
data class AccessoryCatalogItem(
    val id: String,
    val productId: String,
    val displayName: String,
    val description: String,
    val emoji: String,
    val slot: AccessorySlot,
    val visual: AccessoryVisual,
    val modifiers: List<PetModifier>,
    val isPremium: Boolean,
    val packLabel: String,
    val supportedPetIds: Set<String>,
    val coinPrice: Int? = null,
    val bondRequired: Int = 0,
    val tags: Set<String> = emptySet(),
    val sprite: AccessorySpriteSpec? = null,
) {
    /** Compat: ratios para renderizado simple (legacy emojis). */
    val offsetXRatio: Float
        get() = (visual as? AccessoryVisual.EmojiOverlay)?.offsetXRatio ?: 0f
    val offsetYRatio: Float
        get() = (visual as? AccessoryVisual.EmojiOverlay)?.offsetYRatio ?: 0f
    val scale: Float
        get() = (visual as? AccessoryVisual.EmojiOverlay)?.scale ?: 0.24f
}

enum class AccessoryPurchaseResult {
    PURCHASED,
    ALREADY_OWNED,
    NOT_ENOUGH_COINS,
    BOND_REQUIRED,
    NOT_AVAILABLE,
}
