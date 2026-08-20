package com.pixelpals.app.feature.store

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.pixelpals.app.R
import com.pixelpals.app.core.analytics.AnalyticsTracker
import com.pixelpals.app.core.services.AppServices
import com.pixelpals.app.data.catalog.Cosmetic
import com.pixelpals.app.data.catalog.CosmeticEffect
import com.pixelpals.app.databinding.FragmentStoreListBinding
import kotlinx.coroutines.launch

class CosmeticsTabFragment : Fragment() {
    private var bindingReference: FragmentStoreListBinding? = null
    private val binding: FragmentStoreListBinding
        get() = requireNotNull(bindingReference)
    private val analytics: AnalyticsTracker by lazy { AppServices.analytics(requireContext()) }
    private lateinit var storeFragment: StoreFragment
    private lateinit var adapter: CosmeticCatalogAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val inflatedBinding: FragmentStoreListBinding = FragmentStoreListBinding.inflate(
            inflater,
            container,
            false,
        )
        bindingReference = inflatedBinding
        return inflatedBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        storeFragment = requireParentFragment() as StoreFragment
        adapter = CosmeticCatalogAdapter(::handleCosmeticAction)
        binding.storeList.layoutManager = LinearLayoutManager(requireContext())
        binding.storeList.adapter = adapter
        collectUiState()
    }

    override fun onDestroyView() {
        binding.storeList.adapter = null
        bindingReference = null
        super.onDestroyView()
    }

    private fun collectUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                storeFragment.getStoreViewModel().uiState.collect(::render)
            }
        }
    }

    private fun render(state: StoreUiState) {
        binding.storeEmptyState.visibility = View.GONE
        binding.storeList.visibility = View.VISIBLE
        val rows = mutableListOf<CosmeticCatalogRow>()
        state.cosmetics
            .sortedBy { cosmetic -> getCategoryRank(cosmetic.effect) }
            .groupBy { cosmetic -> getCategoryRank(cosmetic.effect) }
            .forEach { (category, cosmetics) ->
                rows += CosmeticCatalogRow.Header(
                    stableId = "header_$category",
                    titleResource = getCategoryTitleResource(category),
                )
                cosmetics.forEach { cosmetic ->
                    val action: CosmeticAction = StoreCatalogPolicy.cosmeticAction(
                        isOwned = cosmetic.productId in state.ownedCosmeticIds,
                        isEquipped = cosmetic.id == state.equippedCosmeticId,
                    )
                    rows += CosmeticCatalogRow.Item(
                        cosmetic = cosmetic,
                        action = action,
                        isActionEnabled = state.activeOperation == null,
                    )
                }
            }
        adapter.submitList(rows)
    }

    private fun handleCosmeticAction(cosmetic: Cosmetic) {
        val viewModel: StoreViewModel = storeFragment.getStoreViewModel()
        val state: StoreUiState = viewModel.uiState.value
        val action: CosmeticAction = StoreCatalogPolicy.cosmeticAction(
            isOwned = cosmetic.productId in state.ownedCosmeticIds,
            isEquipped = cosmetic.id == state.equippedCosmeticId,
        )
        when (action) {
            CosmeticAction.EQUIPPED -> Unit
            CosmeticAction.EQUIP -> viewModel.equipCosmetic(cosmetic)
            CosmeticAction.BUY -> requestCosmeticPurchase(cosmetic, state.balance)
        }
    }

    private fun requestCosmeticPurchase(cosmetic: Cosmetic, balance: Int) {
        val price: Int = cosmetic.coinPrice ?: return
        if (balance < price) {
            showInsufficientCoins()
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.store_confirm_purchase_title)
            .setMessage(
                getString(
                    R.string.store_confirm_cosmetic_purchase,
                    cosmetic.displayName,
                    price,
                    balance - price,
                ),
            )
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.store_buy_button) { _, _ ->
                purchaseCosmetic(cosmetic, price)
            }
            .show()
    }

    private fun showInsufficientCoins() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.store_insufficient_coins)
            .setMessage(R.string.store_insufficient_coins_detail)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.store_open_coins) { _, _ -> storeFragment.openCoinsTab() }
            .show()
    }

    private fun purchaseCosmetic(cosmetic: Cosmetic, price: Int) {
        storeFragment.getStoreViewModel().purchaseCosmetic(cosmetic) { isPurchased ->
            if (!isPurchased || !isAdded) return@purchaseCosmetic
            analytics.track(
                "cosmetic_purchased",
                mapOf("cosmetic_id" to cosmetic.id, "price" to price.toString()),
            )
            Toast.makeText(
                requireContext(),
                getString(R.string.cosmetic_unlocked_toast, cosmetic.displayName),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun getCategoryRank(effect: CosmeticEffect): Int = when (effect) {
        is CosmeticEffect.TintEffect -> 0
        is CosmeticEffect.AuraEffect -> 1
        is CosmeticEffect.FloatEffect -> 2
    }

    private fun getCategoryTitleResource(category: Int): Int = when (category) {
        0 -> R.string.store_category_tints
        1 -> R.string.store_category_auras
        else -> R.string.store_category_floats
    }
}
