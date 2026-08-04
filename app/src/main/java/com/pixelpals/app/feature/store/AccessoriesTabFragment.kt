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
import com.pixelpals.app.core.services.AppServices
import com.pixelpals.app.data.catalog.AccessoryCatalog
import com.pixelpals.app.data.prefs.SelectedPetStore
import com.pixelpals.app.data.repository.PixelPalsRepository
import kotlinx.coroutines.launch

class AccessoriesTabFragment : Fragment() {

    private val repository: PixelPalsRepository by lazy { AppServices.repository(requireContext()) }
    private val analytics: AnalyticsTracker by lazy { AppServices.analytics(requireContext()) }
    private val selectedPetStore by lazy { SelectedPetStore(requireContext()) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val scroll = inflater.inflate(R.layout.fragment_store_scroll, container, false) as android.widget.ScrollView
        val root = scroll.findViewById<LinearLayout>(R.id.scrollContent)

        val selected = selectedPetStore.load()
        val petId = when (selected) {
            com.pixelpals.app.core.domain.PetType.BLOOP -> "bloop"
            com.pixelpals.app.core.domain.PetType.NUBE_MICHI -> "nube_michi"
            com.pixelpals.app.core.domain.PetType.JELLY -> "jelly"
            com.pixelpals.app.core.domain.PetType.CORGI -> "corgi"
            com.pixelpals.app.core.domain.PetType.GINGER -> "ginger"
            com.pixelpals.app.core.domain.PetType.ANGEL -> "angel"
            com.pixelpals.app.core.domain.PetType.PATITO -> "patito"
            com.pixelpals.app.core.domain.PetType.DIABLILLO -> "diablillo"
            com.pixelpals.app.core.domain.PetType.MOKI -> "moki"
        }

        val accessories = AccessoryCatalog.forPet(requireContext(), petId)
            .sortedWith(compareBy({ it.slot.ordinal }, { it.displayName }))

        lifecycleScope.launch {
            val balance = repository.getCoinBalance(selected)
            accessories.forEach { acc ->
                val card = inflater.inflate(R.layout.item_accessory_v15, root, false)
                card.findViewById<TextView>(R.id.txtAccEmoji).text = acc.emoji
                card.findViewById<TextView>(R.id.txtAccTitle).text = acc.displayName
                card.findViewById<TextView>(R.id.txtAccSubtitle).text = acc.description
                card.findViewById<TextView>(R.id.txtAccSlot).text = acc.slot.name

                val btn = card.findViewById<Button>(R.id.btnAccAction)
                val owned = repository.isProductOwned(acc.productId)
                when {
                    owned -> {
                        btn.text = getString(R.string.store_equip_button)
                        btn.setOnClickListener {
                            lifecycleScope.launch {
                                val ok = repository.equipAccessory(selected, acc.id)
                                analytics.track("accessory_equipped", mapOf("accessory_id" to acc.id, "ok" to ok.toString()))
                                if (ok) {
                                    val activity = requireActivity() as? StoreActivity
                                    activity?.notifyAccessoryChanged()
                                    activity?.refreshStoreHeader()
                                    btn.text = getString(R.string.store_unequip_button)
                                    btn.setOnClickListener { activity?.unequipCurrent() }
                                }
                            }
                        }
                    }
                    acc.coinPrice != null -> {
                        btn.text = getString(R.string.store_buy_with_coins_button, acc.coinPrice)
                        btn.isEnabled = balance >= acc.coinPrice
                        btn.setOnClickListener {
                            lifecycleScope.launch {
                                val result = repository.purchaseAccessoryWithCoins(selected, acc.id)
                                analytics.track("accessory_bought_coins", mapOf("accessory_id" to acc.id, "result" to result.name))
                                if (result == com.pixelpals.app.data.catalog.AccessoryPurchaseResult.PURCHASED) {
                                    val equipped = repository.equipAccessory(selected, acc.id)
                                    if (equipped) (requireActivity() as? StoreActivity)?.notifyAccessoryChanged()
                                    (requireActivity() as? StoreActivity)?.refreshStoreHeader()
                                }
                            }
                        }
                    }
                    else -> {
                        btn.text = getString(R.string.store_buy_button)
                        btn.setOnClickListener {
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
                                    }
                                }
                            }
                        }
                    }
                }
                root.addView(card)
            }
        }
        return scroll
    }
}
