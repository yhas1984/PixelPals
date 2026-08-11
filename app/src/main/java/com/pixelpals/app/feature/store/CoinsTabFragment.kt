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
        return inflater.inflate(
            R.layout.fragment_store_scroll,
            container,
            false,
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val scroll = view as android.widget.ScrollView
        val root = scroll.findViewById<LinearLayout>(R.id.scrollContent)
        val activity = requireActivity() as StoreActivity
        val billing = AppServices.billingRepository(requireContext())
        val lifecycleOwner = viewLifecycleOwner
        val cards = mutableListOf<Triple<CoinProduct, TextView, Button>>()

        CoinProduct.CATALOG.forEach { pack ->
            val card = layoutInflater.inflate(R.layout.item_coin_pack, root, false)
            card.findViewById<TextView>(R.id.txtCoinPackTitle).text = getString(pack.displayNameResId)
            card.findViewById<TextView>(R.id.txtCoinPackSubtitle).text = getString(pack.subtitleResId)
            val badge = card.findViewById<TextView>(R.id.txtCoinPackBadge)
            if (pack.bestValueFlag) {
                badge.visibility = View.VISIBLE
                badge.text = getString(R.string.coins_pack_best_value)
            } else {
                badge.visibility = View.GONE
            }
            val price = card.findViewById<TextView>(R.id.txtCoinPackPrice)
            val buyBtn = card.findViewById<Button>(R.id.btnCoinPackBuy)
            buyBtn.isEnabled = false
            buyBtn.setOnClickListener {
                analytics.track("coin_pack_buy_tap", mapOf("product_id" to pack.productId))
                buyBtn.isEnabled = false
                activity.purchaseCoinPack(pack) {
                    if (lifecycleOwner.lifecycle.currentState.isAtLeast(
                            androidx.lifecycle.Lifecycle.State.CREATED
                        )
                    ) {
                        buyBtn.isEnabled = price.text.isNotBlank()
                    }
                }
            }
            root.addView(card)
            cards += Triple(pack, price, buyBtn)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val prices = billing.prefetch(CoinProduct.CATALOG.map { it.productId })
            cards.forEach { (pack, price, buyBtn) ->
                val formattedPrice = prices[pack.productId]
                price.text = formattedPrice ?: getString(R.string.store_preview_price)
                buyBtn.isEnabled = formattedPrice != null
            }
        }
    }
}
