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
import com.pixelpals.app.data.catalog.CoinProduct
import kotlinx.coroutines.launch

class CoinsTabFragment : Fragment() {

    private val analytics: AnalyticsTracker by lazy { AppServices.analytics(requireContext()) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val scroll = inflater.inflate(R.layout.fragment_store_scroll, container, false) as android.widget.ScrollView
        val root = scroll.findViewById<LinearLayout>(R.id.scrollContent)

        CoinProduct.CATALOG.forEach { pack ->
            val card = inflater.inflate(R.layout.item_coin_pack, root, false)
            card.findViewById<TextView>(R.id.txtCoinPackTitle).text = pack.displayName
            card.findViewById<TextView>(R.id.txtCoinPackSubtitle).text = pack.subtitle
            val badge = card.findViewById<TextView>(R.id.txtCoinPackBadge)
            if (pack.bestValueFlag) {
                badge.visibility = View.VISIBLE
                badge.text = getString(R.string.coins_pack_best_value)
            } else {
                badge.visibility = View.GONE
            }
            val price = card.findViewById<TextView>(R.id.txtCoinPackPrice)
            val buyBtn = card.findViewById<Button>(R.id.btnCoinPackBuy)
            // Prefetch price from billing
            val activity = requireActivity() as StoreActivity
            val billing = AppServices.billingRepository(requireContext())
            lifecycleScope.launch {
                val prices = billing.prefetch(listOf(pack.productId))
                price.text = prices[pack.productId] ?: getString(R.string.store_preview_price)
                buyBtn.isEnabled = prices[pack.productId] != null
                buyBtn.setOnClickListener {
                    analytics.track("coin_pack_buy_tap", mapOf("product_id" to pack.productId))
                    activity.purchaseCoinPack(pack)
                }
            }
            root.addView(card)
        }
        return scroll
    }
}
