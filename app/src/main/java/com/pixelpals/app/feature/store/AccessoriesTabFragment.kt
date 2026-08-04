package com.pixelpals.app.feature.store

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.pixelpals.app.R
import com.pixelpals.app.core.analytics.AnalyticsTracker
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.services.AppServices
import com.pixelpals.app.data.catalog.AccessoryCatalog
import com.pixelpals.app.data.catalog.AccessoryPurchaseResult
import com.pixelpals.app.data.prefs.SelectedPetStore
import com.pixelpals.app.data.repository.PixelPalsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AccessoriesTabFragment : Fragment() {

    private val repository: PixelPalsRepository by lazy { AppServices.repository(requireContext()) }
    private val analytics: AnalyticsTracker by lazy { AppServices.analytics(requireContext()) }
    private val selectedPetStore by lazy { SelectedPetStore(requireContext()) }

    private lateinit var root: LinearLayout
    private var renderJob: Job? = null
    private var selected: PetType = PetType.CORGI
    private var petId: String = "corgi"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val scroll = inflater.inflate(R.layout.fragment_store_scroll, container, false) as android.widget.ScrollView
        root = scroll.findViewById(R.id.scrollContent)
        selected = selectedPetStore.load()
        petId = selected.name.lowercase()

        renderAccessories()
        return scroll
    }

    /** Re-renderiza la lista completa — tras equipar/comprar, todos los botones reflejan el estado real. */
    private fun renderAccessories() {
        renderJob?.cancel()
        renderJob = lifecycleScope.launch {
            val balance = repository.getCoinBalance(selected)
            val equippedId = repository.getEquippedAccessory(selected)?.id
            val accessories = AccessoryCatalog.forPet(requireContext(), petId)
                .sortedWith(compareBy({ it.slot.ordinal }, { it.displayName }))

            root.removeAllViews()
            val inflater = layoutInflater
            accessories.forEach { acc ->
                val owned = repository.isProductOwned(acc.productId)
                root.addView(buildCard(inflater, acc, balance, equippedId, owned))
            }
        }
    }

    private fun buildCard(
        inflater: LayoutInflater,
        acc: com.pixelpals.app.data.catalog.AccessoryCatalogItem,
        balance: Int,
        equippedId: String?,
        owned: Boolean,
    ): View {
        val card = inflater.inflate(R.layout.item_accessory_v15, root, false)
        card.findViewById<TextView>(R.id.txtAccEmoji).text = acc.emoji
        card.findViewById<TextView>(R.id.txtAccTitle).text = acc.displayName
        card.findViewById<TextView>(R.id.txtAccSubtitle).text = acc.description
        card.findViewById<TextView>(R.id.txtAccSlot).text = acc.slot.name

        val btn = card.findViewById<Button>(R.id.btnAccAction)
        val isEquipped = equippedId == acc.id

        when {
            isEquipped -> {
                btn.text = getString(R.string.store_unequip_button)
                btn.setOnClickListener { (requireActivity() as? StoreActivity)?.unequipCurrent() }
            }
            owned -> {
                btn.text = getString(R.string.store_equip_button)
                btn.setOnClickListener { equip(acc) }
            }
            acc.coinPrice != null -> {
                btn.text = getString(R.string.store_buy_with_coins_button, acc.coinPrice)
                btn.isEnabled = balance >= acc.coinPrice
                btn.setOnClickListener { buyWithCoins(acc) }
            }
            else -> {
                btn.text = getString(R.string.store_buy_button)
                btn.setOnClickListener { buyWithMoney(acc) }
            }
        }
        return card
    }

    private fun equip(acc: com.pixelpals.app.data.catalog.AccessoryCatalogItem) {
        lifecycleScope.launch {
            val ok = repository.equipAccessory(selected, acc.id)
            analytics.track("accessory_equipped", mapOf("accessory_id" to acc.id, "ok" to ok.toString()))
            if (ok) {
                (requireActivity() as? StoreActivity)?.let {
                    it.notifyAccessoryChanged()
                    it.refreshStoreHeader()
                }
                renderAccessories()
            }
        }
    }

    private fun buyWithCoins(acc: com.pixelpals.app.data.catalog.AccessoryCatalogItem) {
        lifecycleScope.launch {
            val result = repository.purchaseAccessoryWithCoins(selected, acc.id)
            analytics.track("accessory_bought_coins", mapOf("accessory_id" to acc.id, "result" to result.name))
            if (result == AccessoryPurchaseResult.PURCHASED) {
                val equipped = repository.equipAccessory(selected, acc.id)
                if (equipped) (requireActivity() as? StoreActivity)?.notifyAccessoryChanged()
                (requireActivity() as? StoreActivity)?.refreshStoreHeader()
                renderAccessories()
            } else {
                (requireActivity() as? StoreActivity)?.let { it.showPurchaseError(result) }
            }
        }
    }

    private fun buyWithMoney(acc: com.pixelpals.app.data.catalog.AccessoryCatalogItem) {
        val activity = requireActivity() as StoreActivity
        AppServices.billingRepository(requireContext()).launchPurchase(
            requireActivity(),
            acc.productId
        ) { success ->
            if (success) {
                lifecycleScope.launch {
                    val equipped = repository.equipAccessory(selected, acc.id)
                    analytics.track("accessory_bought_real", mapOf("accessory_id" to acc.id, "ok" to equipped.toString()))
                    if (equipped) activity.notifyAccessoryChanged()
                    activity.refreshStoreHeader()
                    renderAccessories()
                }
            }
        }
    }
}
