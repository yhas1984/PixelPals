package com.pixelpals.app.feature.store

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.pixelpals.app.PetService
import com.pixelpals.app.R
import com.pixelpals.app.core.analytics.AnalyticsTracker
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.services.AppServices
import com.pixelpals.app.data.catalog.CoinProduct
import com.pixelpals.app.data.prefs.SelectedPetStore
import com.pixelpals.app.data.repository.PixelPalsRepository
import com.pixelpals.app.feature.store.billing.BillingRepository
import kotlinx.coroutines.launch

class StoreActivity : AppCompatActivity() {

    private lateinit var selectedPetStore: SelectedPetStore
    private val repository: PixelPalsRepository by lazy { AppServices.repository(this) }
    private val analytics: AnalyticsTracker by lazy { AppServices.analytics(this) }
    private val billing: BillingRepository by lazy { AppServices.billingRepository(this) }
    private lateinit var selectedPet: PetType
    private var isStoreCreated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTitle(R.string.store_title)
        edgeToEdge()
        setContentView(R.layout.activity_store)
        selectedPetStore = SelectedPetStore(this)
        selectedPet = selectedPetStore.load()

        applySystemBarsInsets()

        findViewById<com.google.android.material.tabs.TabLayout>(R.id.storeTabs).also { tabs ->
            val pager = findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.storePager)
            pager.adapter = StorePagerAdapter(this)
            TabLayoutMediator(tabs, pager) { tab, position ->
                tab.text = when (position) {
                    0 -> getString(R.string.store_tab_pets)
                    1 -> getString(R.string.store_tab_cosmetics)
                    else -> getString(R.string.store_tab_coins)
                }
            }.attach()
        }

        refreshHeader()
        analytics.track("store_opened_v15")
        isStoreCreated = true
    }

    override fun onResume() {
        super.onResume()
        // onCreate ya refrescó; en onResume solo si ya estábamos creados (retorno de compra).
        if (isStoreCreated) refreshHeader()
    }

    /** Refresca el header (usado por las tabs tras cambios). */
    fun refreshStoreHeader() = refreshHeader()

    private fun refreshHeader() {        findViewById<TextView>(R.id.txtStoreSubtitle).text = getString(R.string.store_subtitle_format, selectedPet.displayName)
        lifecycleScope.launch {
            val balance = repository.getCoinBalance(selectedPet)
            findViewById<TextView>(R.id.txtStoreWallet).text = getString(R.string.coins_wallet_format, balance)
            findViewById<TextView>(R.id.txtStoreHighlight).text = getString(
                R.string.store_featured_message_format,
                selectedPet.displayName,
                getString(R.string.store_owned_hint_default),
            )
        }
    }

    /** Compra un pack de monedas (real money) */
    fun purchaseCoinPack(coinProduct: CoinProduct) {
        billing.launchPurchase(this, coinProduct.productId) { success ->
            if (success) {
                lifecycleScope.launch {
                    repository.grantCoinPack(coinProduct, selectedPet, source = "billing")
                    analytics.track("coins_purchased", mapOf("product_id" to coinProduct.productId))
                    Toast.makeText(
                        this@StoreActivity,
                        getString(R.string.coins_purchase_success, coinProduct.coinAmount),
                        Toast.LENGTH_SHORT
                    ).show()
                    refreshHeader()
                }
            }
        }
    }

    private fun edgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
    }

    private fun applySystemBarsInsets() {
        val view = findViewById<View>(R.id.storeRoot)
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    private class StorePagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = 3

        override fun createFragment(position: Int): androidx.fragment.app.Fragment {
            return when (position) {
                0 -> PetsTabFragment()
                1 -> CosmeticsTabFragment()
                else -> CoinsTabFragment()
            }
        }
    }
}
