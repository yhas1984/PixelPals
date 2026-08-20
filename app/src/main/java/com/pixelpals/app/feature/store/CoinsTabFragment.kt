package com.pixelpals.app.feature.store

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.pixelpals.app.R
import com.pixelpals.app.core.analytics.AnalyticsTracker
import com.pixelpals.app.core.services.AppServices
import com.pixelpals.app.data.catalog.CoinProduct
import com.pixelpals.app.databinding.FragmentStoreCoinsBinding
import kotlinx.coroutines.launch

class CoinsTabFragment : Fragment() {
    private var bindingReference: FragmentStoreCoinsBinding? = null
    private val binding: FragmentStoreCoinsBinding
        get() = requireNotNull(bindingReference)
    private val analytics: AnalyticsTracker by lazy { AppServices.analytics(requireContext()) }
    private lateinit var storeFragment: StoreFragment
    private lateinit var adapter: CoinPackAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val inflatedBinding: FragmentStoreCoinsBinding = FragmentStoreCoinsBinding.inflate(
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
        adapter = CoinPackAdapter(::purchasePack)
        binding.coinList.layoutManager = LinearLayoutManager(requireContext())
        binding.coinList.adapter = adapter
        binding.coinCatalogRetry.setOnClickListener {
            storeFragment.getStoreViewModel().loadCoinCatalog(isForced = true)
        }
        collectUiState()
    }

    override fun onDestroyView() {
        binding.coinList.adapter = null
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
        val catalogState: CoinCatalogState = state.coinCatalogState
        val activeProductId: String? = (state.activeOperation as? ActiveStoreOperation.PurchaseCoins)?.id
        val prices: Map<String, String> = (catalogState as? CoinCatalogState.Available)?.prices.orEmpty()
        adapter.submitList(
            CoinProduct.CATALOG.map { product ->
                val hasPrice: Boolean = prices.containsKey(product.productId)
                CoinPackRow(
                    product = product,
                    formattedPrice = prices[product.productId],
                    isEnabled = hasPrice && state.activeOperation == null,
                    isActive = activeProductId == product.productId,
                )
            },
        )
        renderCatalogState(catalogState)
    }

    private fun renderCatalogState(state: CoinCatalogState) {
        binding.coinCatalogState.visibility = when (state) {
            CoinCatalogState.NotRequested,
            is CoinCatalogState.Available -> View.GONE
            CoinCatalogState.Loading,
            is CoinCatalogState.Unavailable -> View.VISIBLE
        }
        binding.coinCatalogProgress.visibility = if (state == CoinCatalogState.Loading) {
            View.VISIBLE
        } else {
            View.GONE
        }
        binding.coinCatalogRetry.visibility = if (state is CoinCatalogState.Unavailable) {
            View.VISIBLE
        } else {
            View.GONE
        }
        binding.coinCatalogMessage.text = when (state) {
            CoinCatalogState.Loading -> getString(R.string.store_loading)
            is CoinCatalogState.Unavailable -> getString(R.string.store_catalog_unavailable)
            else -> ""
        }
        binding.coinCatalogMessage.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (state is CoinCatalogState.Unavailable) R.color.red_error else R.color.status_info_fg,
            ),
        )
    }

    private fun purchasePack(pack: CoinProduct) {
        if (storeFragment.getStoreViewModel().uiState.value.activeOperation != null) return
        analytics.track("coin_pack_buy_tap", mapOf("product_id" to pack.productId))
        storeFragment.purchaseCoinPack(pack)
    }
}
