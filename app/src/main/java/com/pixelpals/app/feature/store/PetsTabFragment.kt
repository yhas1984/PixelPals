package com.pixelpals.app.feature.store

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.pixelpals.app.PetService
import com.pixelpals.app.R
import com.pixelpals.app.core.analytics.AnalyticsTracker
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.services.AppServices
import com.pixelpals.app.data.catalog.CatalogItemState
import com.pixelpals.app.data.catalog.PetCatalogItem
import com.pixelpals.app.data.prefs.SelectedPetStore
import com.pixelpals.app.data.repository.PixelPalsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Tab de mascotas: permite comprar los pets premium (Querubín, Diablillo)
 * con dinero real y usar cualquier mascota desbloqueada.
 */
class PetsTabFragment : Fragment() {

    private val repository: PixelPalsRepository by lazy { AppServices.repository(requireContext()) }
    private val analytics: AnalyticsTracker by lazy { AppServices.analytics(requireContext()) }
    private val selectedPetStore by lazy { SelectedPetStore(requireContext()) }

    private lateinit var root: LinearLayout
    private var renderJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val scroll = inflater.inflate(R.layout.fragment_store_scroll, container, false) as android.widget.ScrollView
        root = scroll.findViewById(R.id.scrollContent)
        renderPets()
        return scroll
    }

    private fun renderPets() {
        renderJob?.cancel()
        renderJob = lifecycleScope.launch {
            val selected = selectedPetStore.load()
            val items = repository.getCatalog(selected).sortedWith(
                compareBy<PetCatalogItem> { it.isPremium }
                    .thenBy { it.displayName }
            )
            root.removeAllViews()
            val inflater = layoutInflater
            // Categorías: Base primero, Premium después (con cabecera).
            items.groupBy { it.isPremium }.forEach { (premium, pets) ->
                root.addView(
                    TextView(requireContext()).apply {
                        text = getString(
                            if (premium) R.string.store_category_premium
                            else R.string.store_category_base
                        )
                        setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_primary))
                        textSize = 15f
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        setPadding(4, 28, 4, 10)
                    }
                )
                pets.forEach { item ->
                    root.addView(buildPetCard(inflater, item))
                }
            }
        }
    }

    private fun buildPetCard(inflater: LayoutInflater, item: PetCatalogItem): View {
        val card = inflater.inflate(R.layout.item_pet_catalog, root, false)
        card.findViewById<ImageView>(R.id.imgPetPreview).setImageResource(item.previewResId)
        card.findViewById<TextView>(R.id.txtPetName).text = item.displayName
        card.findViewById<TextView>(R.id.txtPetDesc).text = item.description.replace('\n', ' ')
        card.findViewById<TextView>(R.id.txtPetBadge).text = if (item.isPremium) {
            getString(R.string.selection_premium_badge)
        } else {
            getString(R.string.selection_base_badge)
        }

        val stateText = when (item.state) {
            CatalogItemState.LOCKED -> getString(R.string.selection_locked_state)
            CatalogItemState.OWNED -> getString(R.string.selection_owned_state)
            CatalogItemState.SELECTED -> getString(R.string.selection_selected_state)
        }
        card.findViewById<TextView>(R.id.txtPetState).text = stateText

        val priceTv = card.findViewById<TextView>(R.id.txtPetPrice)
        if (item.state == CatalogItemState.LOCKED && item.coinPrice != null) {
            priceTv.text = getString(R.string.cosmetic_price_format, item.coinPrice)
            priceTv.visibility = View.VISIBLE
        } else {
            priceTv.visibility = View.GONE
        }

        val btn = card.findViewById<Button>(R.id.btnPetAction)
        btn.text = when (item.state) {
            CatalogItemState.LOCKED -> getString(R.string.store_buy_pet_with_coins, item.coinPrice ?: 0)
            CatalogItemState.OWNED -> getString(R.string.store_select_button)
            CatalogItemState.SELECTED -> getString(R.string.store_selected_button)
        }
        btn.isEnabled = item.state != CatalogItemState.SELECTED
        btn.setOnClickListener {
            when (item.state) {
                CatalogItemState.LOCKED -> buyPremiumPet(item)
                CatalogItemState.OWNED -> usePet(item)
                CatalogItemState.SELECTED -> Unit
            }
        }
        return card
    }

    private fun buyPremiumPet(item: PetCatalogItem) {
        // TODO: todo se compra con monedas; las monedas se compran con dinero real.
        lifecycleScope.launch {
            val ok = repository.purchasePetWithCoins(item.petType!!)
            if (ok) {
                analytics.track("premium_pet_purchased_coins", mapOf("pet_id" to (item.petType?.name ?: "")))
                android.widget.Toast.makeText(
                    requireContext(),
                    getString(R.string.pet_unlocked_toast, item.displayName),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                renderPets()
            } else {
                android.widget.Toast.makeText(
                    requireContext(),
                    getString(R.string.store_insufficient_coins),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun usePet(item: PetCatalogItem) {
        val type = item.petType ?: return
        selectedPetStore.save(type)
        analytics.track("pet_selected_from_store", mapOf("pet_id" to type.name.lowercase()))
        PetService.requestPetChange(requireContext(), type)
        (activity as? StoreActivity)?.refreshStoreHeader()
        renderPets()
    }
}
