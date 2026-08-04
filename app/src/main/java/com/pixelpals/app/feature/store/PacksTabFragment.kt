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
import com.pixelpals.app.core.services.AppServices
import com.pixelpals.app.data.catalog.PremiumPack
import kotlinx.coroutines.launch

class PacksTabFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val scroll = inflater.inflate(R.layout.fragment_store_scroll, container, false) as android.widget.ScrollView
        val root = scroll.findViewById<LinearLayout>(R.id.scrollContent)

        PremiumPack.CATALOG.forEach { pack ->
            val card = inflater.inflate(R.layout.item_premium_pack, root, false)
            card.findViewById<TextView>(R.id.txtPackTitle).text = pack.displayName
            card.findViewById<TextView>(R.id.txtPackSubtitle).text = pack.subtitle
            val buyBtn = card.findViewById<Button>(R.id.btnPackBuy)
            val price = card.findViewById<TextView>(R.id.txtPackPrice)

            val activity = requireActivity() as StoreActivity
            lifecycleScope.launch {
                val prices = AppServices.billingRepository(requireContext()).prefetch(listOf(pack.productId))
                price.text = prices[pack.productId] ?: getString(R.string.store_preview_price)
                buyBtn.isEnabled = prices[pack.productId] != null
                buyBtn.setOnClickListener {
                    activity.purchasePremiumPack(pack)
                }
            }
            root.addView(card)
        }
        return scroll
    }
}
