package com.pixelpals.app.feature.store

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.pixelpals.app.R
import com.pixelpals.app.PetService
import com.pixelpals.app.core.analytics.AnalyticsTracker
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.services.AppServices
import com.pixelpals.app.data.catalog.AccessoryCatalog
import com.pixelpals.app.data.catalog.AccessoryCatalogItem
import com.pixelpals.app.data.catalog.AccessoryPurchaseResult
import com.pixelpals.app.data.catalog.AccessorySlot
import com.pixelpals.app.data.catalog.CoinProduct
import com.pixelpals.app.data.catalog.PetCatalogItem
import com.pixelpals.app.data.catalog.PremiumPack
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
                    0 -> getString(R.string.store_tab_coins)
                    1 -> getString(R.string.store_tab_accessories)
                    else -> getString(R.string.store_tab_packs)
                }
            }.attach()
        }

        refreshHeader()
        analytics.track("store_opened_v15")
    }

    override fun onResume() {
        super.onResume()
        refreshHeader()
    }

    private fun refreshHeader() {
        findViewById<TextView>(R.id.txtStoreSubtitle).text = getString(R.string.store_subtitle_format, selectedPet.displayName)
        lifecycleScope.launch {
            val balance = repository.getCoinBalance(selectedPet)
            val equipped = repository.getEquippedAccessory(selectedPet)
            findViewById<TextView>(R.id.txtStoreWallet).text = getString(R.string.coins_wallet_format, balance)
            findViewById<TextView>(R.id.txtStoreHighlight).text = getString(
                R.string.store_featured_message_format,
                selectedPet.displayName,
                equipped?.displayName ?: getString(R.string.store_owned_hint_default),
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

    /** Notifica al PetService que el accesorio equipado cambió para refrescar el overlay. */
    fun notifyAccessoryChanged() {
        PetService.requestPetRefresh(this, message = null, celebrate = true)
    }

    /** Refresca el header (wallet, equipped, etc.) — usado tras compras. */
    fun refreshStoreHeader() = refreshHeader()

    /** Quita el accesorio del pet activo. */
    fun unequipCurrent() {
        lifecycleScope.launch {
            repository.equipAccessory(selectedPet, null)
            notifyAccessoryChanged()
        }
    }

    /** Compra un pack premium (accesorios + monedas) */
    fun purchasePremiumPack(pack: PremiumPack) {
        billing.launchPurchase(this, pack.productId) { success ->
            if (success) {
                lifecycleScope.launch {
                    val autoEquipped = repository.grantPremiumPack(
                        pack = pack,
                        petType = selectedPet,
                        source = "billing",
                    )
                    analytics.track(
                        "premium_pack_purchased",
                        mapOf("product_id" to pack.productId, "auto_equipped" to (autoEquipped ?: "none"))
                    )
                    val msg = if (autoEquipped != null) {
                        "Pack comprado. Equipado: ${autoEquipped}"
                    } else {
                        getString(R.string.coins_purchase_success, pack.bonusCoins)
                    }
                    Toast.makeText(this@StoreActivity, msg, Toast.LENGTH_SHORT).show()
                    if (autoEquipped != null) notifyAccessoryChanged()
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
                0 -> CoinsTabFragment()
                1 -> AccessoriesTabFragment()
                else -> PacksTabFragment()
            }
        }
    }
}
