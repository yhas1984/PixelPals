package com.pixelpals.app.feature.store

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.pixelpals.app.R
import com.pixelpals.app.core.services.AppServices
import com.pixelpals.app.data.catalog.Cosmetic
import com.pixelpals.app.data.catalog.CosmeticCatalog
import com.pixelpals.app.data.catalog.CosmeticEffect
import com.pixelpals.app.data.prefs.SelectedPetStore
import com.pixelpals.app.data.repository.PixelPalsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Tab de cosméticos: efectos que envuelven al pet sin alineación
 * (tint, aura, float). Se compran con monedas y se equipan por pet.
 */
class CosmeticsTabFragment : Fragment() {

    private val repository: PixelPalsRepository by lazy { AppServices.repository(requireContext()) }
    private val analytics by lazy { AppServices.analytics(requireContext()) }
    private val selectedPetStore by lazy { SelectedPetStore(requireContext()) }

    private lateinit var root: LinearLayout
    private var renderJob: Job? = null
    private lateinit var storeActivity: StoreActivity

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val scroll = inflater.inflate(R.layout.fragment_store_scroll, container, false) as android.widget.ScrollView
        root = scroll.findViewById(R.id.scrollContent)
        storeActivity = requireActivity() as StoreActivity
        renderCosmetics()
        return scroll
    }

    override fun onResume() {
        super.onResume()
        // Al volver a la pestaña (p.ej. tras cambiar de mascota en PetsTab),
        // re-renderiza con el pet seleccionado actual.
        renderCosmetics()
    }

    private fun renderCosmetics() {
        renderJob?.cancel()
        renderJob = lifecycleScope.launch {
            val selected = selectedPetStore.load()
            val petId = selected.name.lowercase()
            val cosmetics = CosmeticCatalog.all(requireContext())
            val equippedId = repository.getEquippedCosmetic(petId)
            val ownedIds = cosmetics.map { it.productId }
                .filter { repository.isCosmeticOwned(it) }
                .toSet()
            root.removeAllViews()
            val inflater = layoutInflater
            // Orden por categorías: Tintes → Auras → Flotantes (delicado primero).
            val grouped = cosmetics.sortedBy { categoryRank(it.effect) }
                .groupBy { categoryRank(it.effect) }
            grouped.forEach { (rank, items) ->
                root.addView(buildCategoryHeader(inflater, categoryTitle(rank)))
                items.forEach { cosmetic ->
                    root.addView(buildCosmeticCard(inflater, cosmetic, equippedId, ownedIds))
                }
            }
        }
    }

    private fun buildCategoryHeader(inflater: LayoutInflater, title: String): View {
        return TextView(requireContext()).apply {
            text = title
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(4, 28, 4, 10)
        }
    }

    private fun categoryRank(effect: CosmeticEffect): Int = when (effect) {
        is CosmeticEffect.TintEffect -> 0
        is CosmeticEffect.AuraEffect -> 1
        is CosmeticEffect.FloatEffect -> 2
    }

    private fun categoryTitle(rank: Int): String = when (rank) {
        0 -> getString(R.string.store_category_tints)
        1 -> getString(R.string.store_category_auras)
        else -> getString(R.string.store_category_floats)
    }

    private fun buildCosmeticCard(
        inflater: LayoutInflater,
        cosmetic: Cosmetic,
        equippedId: String?,
        ownedIds: Set<String>,
    ): View {
        val card = inflater.inflate(R.layout.item_cosmetic, root, false)
        card.findViewById<TextView>(R.id.txtCosmeticEmoji).text = previewEmoji(cosmetic.effect)
        card.findViewById<TextView>(R.id.txtCosmeticTitle).text = cosmetic.displayName
        card.findViewById<TextView>(R.id.txtCosmeticSubtitle).text = cosmetic.description

        val priceText = card.findViewById<TextView>(R.id.txtCosmeticPrice)
        val btn = card.findViewById<Button>(R.id.btnCosmeticAction)
        val owned = cosmetic.productId in ownedIds
        val isEquipped = equippedId == cosmetic.id
        // Precio visible solo si aún no se ha comprado.
        priceText.text = if (!owned && !isEquipped && cosmetic.coinPrice != null) {
            getString(R.string.cosmetic_price_format, cosmetic.coinPrice)
        } else {
            ""
        }
        btn.text = when (StoreCatalogPolicy.cosmeticAction(owned, isEquipped)) {
            CosmeticAction.EQUIPPED -> getString(R.string.store_equipped_button)
            CosmeticAction.EQUIP -> getString(R.string.store_equip_button)
            CosmeticAction.BUY -> getString(R.string.store_buy_with_coins)
        }
        btn.isEnabled = !isEquipped && storeActivity.getStoreViewModel().uiState.value.activeActionId == null
        btn.setOnClickListener {
            // IMPORTANTE: se relee el pet seleccionado EN EL MOMENTO DEL CLIC.
            // Capturarlo en el render dejaba un petId obsoleto (p.ej. corgi) si el
            // usuario cambiaba de mascota en PetsTab con esta pestaña ya dibujada.
            val petType = selectedPetStore.load()
            if (repository.getEquippedCosmetic(petType.name.lowercase()) == cosmetic.id) return@setOnClickListener
            if (owned) {
                storeActivity.getStoreViewModel().equipCosmetic(cosmetic) { renderCosmetics() }
            } else {
                showPurchaseConfirmation(cosmetic)
            }
        }
        return card
    }

    private fun showPurchaseConfirmation(cosmetic: Cosmetic) {
        val price = cosmetic.coinPrice ?: return
        val balance = storeActivity.getStoreViewModel().uiState.value.balance
        if (balance < price) {
            storeActivity.getStoreViewModel().setMessage(
                getString(R.string.store_not_enough_coins),
                isError = true,
                canRetry = false,
                canOpenCoins = true,
            )
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.store_confirm_purchase_title)
            .setMessage(getString(R.string.store_confirm_cosmetic_purchase, cosmetic.displayName, price, balance - price))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.store_buy_button) { _, _ ->
                storeActivity.getStoreViewModel().purchaseCosmetic(cosmetic) { ok ->
                    if (ok) {
                        analytics.track("cosmetic_purchased", mapOf("cosmetic_id" to cosmetic.id, "price" to price.toString()))
                        Toast.makeText(requireContext(), getString(R.string.cosmetic_unlocked_toast, cosmetic.displayName), Toast.LENGTH_SHORT).show()
                    }
                    renderCosmetics()
                }
            }
            .show()
    }

    private fun previewEmoji(effect: CosmeticEffect): String = when (effect) {
        is CosmeticEffect.TintEffect -> "🎨"
        is CosmeticEffect.AuraEffect -> effect.emoji
        is CosmeticEffect.FloatEffect -> effect.emoji
    }
}
