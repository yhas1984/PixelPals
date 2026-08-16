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
import com.pixelpals.app.data.catalog.CoinProduct
import com.pixelpals.app.feature.store.billing.ProductCatalogResult
import kotlinx.coroutines.launch

class CoinsTabFragment : Fragment() {
    private val analytics: AnalyticsTracker by lazy { com.pixelpals.app.core.services.AppServices.analytics(requireContext()) }
    private val cards: MutableMap<String, Pair<TextView, Button>> = mutableMapOf()
    private lateinit var storeActivity: StoreActivity

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_store_scroll, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        storeActivity = requireActivity() as StoreActivity
        val root = (view as android.widget.ScrollView).findViewById<LinearLayout>(R.id.scrollContent)
        CoinProduct.CATALOG.forEach { pack -> root.addView(createPackCard(pack, root)) }
        storeActivity.setStoreRetryAction { loadPrices() }
        loadPrices()
    }

    private fun createPackCard(pack: CoinProduct, root: LinearLayout): View {
        val card = layoutInflater.inflate(R.layout.item_coin_pack, root, false)
        card.findViewById<TextView>(R.id.txtCoinPackTitle).text = getString(pack.displayNameResId)
        card.findViewById<TextView>(R.id.txtCoinPackSubtitle).text = getString(pack.subtitleResId)
        val badge = card.findViewById<TextView>(R.id.txtCoinPackBadge)
        badge.visibility = if (pack.bestValueFlag) View.VISIBLE else View.GONE
        if (pack.bestValueFlag) badge.text = getString(R.string.coins_pack_best_value)
        val price = card.findViewById<TextView>(R.id.txtCoinPackPrice)
        val buyButton = card.findViewById<Button>(R.id.btnCoinPackBuy)
        buyButton.isEnabled = false
        buyButton.setOnClickListener { purchasePack(pack) }
        cards[pack.productId] = price to buyButton
        return card
    }

    private fun loadPrices() {
        storeActivity.getStoreViewModel().setMessage(getString(R.string.store_loading))
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = storeActivity.getStoreViewModel().loadCoinPrices(CoinProduct.CATALOG.map { it.productId })) {
                is ProductCatalogResult.Available -> {
                    cards.forEach { (productId, views) ->
                        val price = result.prices[productId]
                        views.first.text = price.orEmpty()
                        views.second.isEnabled = price != null
                    }
                    storeActivity.getStoreViewModel().setMessage(null)
                }
                is ProductCatalogResult.Unavailable,
                is ProductCatalogResult.Failure -> {
                    cards.values.forEach { views ->
                        views.first.text = ""
                        views.second.isEnabled = false
                    }
                    storeActivity.getStoreViewModel().setMessage(getString(R.string.store_catalog_unavailable), true)
                }
            }
        }
    }

    private fun purchasePack(pack: CoinProduct) {
        if (storeActivity.getStoreViewModel().uiState.value.activeActionId != null) return
        analytics.track("coin_pack_buy_tap", mapOf("product_id" to pack.productId))
        cards.values.forEach { it.second.isEnabled = false }
        storeActivity.purchaseCoinPack(pack) { loadPrices() }
    }

    override fun onDestroyView() {
        storeActivity.setStoreRetryAction(null)
        cards.clear()
        super.onDestroyView()
    }
}
