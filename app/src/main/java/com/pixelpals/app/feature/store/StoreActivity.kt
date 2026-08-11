package com.pixelpals.app.feature.store

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.material.tabs.TabLayoutMediator
import com.pixelpals.app.BuildConfig
import com.pixelpals.app.PetService
import com.pixelpals.app.R
import com.pixelpals.app.core.analytics.AnalyticsTracker
import com.pixelpals.app.core.ads.GoogleMobileAdsConsentManager
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.services.AppServices
import com.pixelpals.app.data.catalog.CoinProduct
import com.pixelpals.app.data.prefs.SelectedPetStore
import com.pixelpals.app.data.repository.PixelPalsRepository
import com.pixelpals.app.feature.store.billing.BillingRepository
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

class StoreActivity : AppCompatActivity() {

    private lateinit var selectedPetStore: SelectedPetStore
    private val repository: PixelPalsRepository by lazy { AppServices.repository(this) }
    private val analytics: AnalyticsTracker by lazy { AppServices.analytics(this) }
    private val billing: BillingRepository by lazy { AppServices.billingRepository(this) }
    private val consentManager: GoogleMobileAdsConsentManager by lazy {
        GoogleMobileAdsConsentManager.getInstance(applicationContext)
    }
    private lateinit var selectedPet: PetType
    private var isStoreCreated = false
    private var adView: AdView? = null
    private val mobileAdsInitialized = AtomicBoolean(false)
    private var purchaseInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTitle(R.string.store_title)
        edgeToEdge()
        setContentView(R.layout.activity_store)
        selectedPetStore = SelectedPetStore(this)
        selectedPet = selectedPetStore.load()

        applySystemBarsInsets()
        findViewById<View>(R.id.btnPrivacyOptions).setOnClickListener {
            consentManager.showPrivacyOptionsForm(this) { error ->
                if (error != null) {
                    android.util.Log.w("UMP", "Privacy options failed: ${error.message}")
                }
                updatePrivacyOptionsVisibility()
            }
        }
        setupConsentAndAds()

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
        lifecycleScope.launch {
            billing.reconcilePurchases()
            refreshHeader()
        }
    }

    override fun onResume() {
        super.onResume()
        // onCreate ya refrescó; en onResume solo si ya estábamos creados (retorno de compra).
        if (isStoreCreated) refreshHeader()
        adView?.resume()
    }

    override fun onPause() {
        adView?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        adView?.destroy()
        super.onDestroy()
    }

    private fun setupConsentAndAds() {
        val container = findViewById<FrameLayout>(R.id.bannerAdContainer) ?: return
        if (!BuildConfig.ADS_ENABLED) {
            container.visibility = View.GONE
            updatePrivacyOptionsVisibility()
            return
        }

        updatePrivacyOptionsVisibility()
        if (consentManager.canRequestAds) initializeMobileAdsSdk()
        consentManager.gatherConsent(this) { error ->
            if (error != null) {
                android.util.Log.w("UMP", "Consent failed: ${error.errorCode} ${error.message}")
            }
            updatePrivacyOptionsVisibility()
            if (consentManager.canRequestAds) initializeMobileAdsSdk()
        }
    }

    private fun updatePrivacyOptionsVisibility() {
        findViewById<View>(R.id.btnPrivacyOptions)?.visibility = if (
            BuildConfig.ADS_ENABLED && consentManager.isPrivacyOptionsRequired
        ) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun initializeMobileAdsSdk() {
        if (!BuildConfig.ADS_ENABLED || !consentManager.canRequestAds) return
        if (!mobileAdsInitialized.compareAndSet(false, true)) return

        if (BuildConfig.DEBUG && BuildConfig.UMP_TEST_DEVICE_ID.isNotBlank()) {
            MobileAds.setRequestConfiguration(
                RequestConfiguration.Builder()
                    .setTestDeviceIds(listOf(BuildConfig.UMP_TEST_DEVICE_ID))
                    .build()
            )
        }
        MobileAds.initialize(this) {
            runOnUiThread {
                if (!isFinishing && !isDestroyed) setupBannerAd()
            }
        }
    }

    /** Banner adaptativo cargado solo después de que UMP permita solicitar anuncios. */
    private fun setupBannerAd() {
        val container = findViewById<FrameLayout>(R.id.bannerAdContainer) ?: return
        if (adView != null) return
        val bannerView = AdView(this).apply {
            setAdUnitId(BuildConfig.ADMOB_BANNER_AD_UNIT_ID)
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    container.visibility = View.VISIBLE
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    android.util.Log.w(
                        "AdMob",
                        "Banner failed: code=${adError.code} msg=${adError.message} " +
                            "domain=${adError.domain} response=${adError.responseInfo?.responseId}"
                    )
                    container.visibility = View.GONE
                }
            }
        }
        container.addView(bannerView)
        // El ancho del adaptive banner se expresa en dp y debe medirse tras el
        // layout del contenedor (20dp de padding a cada lado).
        container.post {
            val widthDp = ((container.width - container.paddingLeft - container.paddingRight) /
                resources.displayMetrics.density)
                .roundToInt()
                .coerceAtLeast(1)
            bannerView.setAdSize(
                AdSize.getLargeAnchoredAdaptiveBannerAdSize(this@StoreActivity, widthDp)
            )
            bannerView.loadAd(AdRequest.Builder().build())
        }
        this.adView = bannerView
    }

    /** Refresca el header (usado por las tabs tras cambios). */
    fun refreshStoreHeader() = refreshHeader()

    private fun refreshHeader() {
        // Re-lee el pet seleccionado: la pestaña de cosméticos puede haber
        // cambiado la selección, y el header no debe quedarse con un pet viejo.
        selectedPet = selectedPetStore.load()
        findViewById<TextView>(R.id.txtStoreSubtitle).text = getString(
            R.string.store_subtitle_format,
            getString(selectedPet.displayNameResId),
        )
        lifecycleScope.launch {
            val balance = repository.getCoinBalance(selectedPet)
            findViewById<TextView>(R.id.txtStoreWallet).text = getString(R.string.coins_wallet_format, balance)
            val equippedId = repository.getEquippedCosmetic(selectedPet.name.lowercase())
            val equippedName = equippedId?.let { id ->
                com.pixelpals.app.data.catalog.CosmeticCatalog.findById(this@StoreActivity, id)?.displayName
            }
            findViewById<TextView>(R.id.txtStoreHighlight).text = getString(
                R.string.store_featured_message_format,
                getString(selectedPet.displayNameResId),
                equippedName ?: getString(R.string.store_owned_hint_default),
            )
        }
    }

    /** Starts a coin purchase; Billing owns fulfillment and the UI only reports the result. */
    fun purchaseCoinPack(coinProduct: CoinProduct, onFinished: (Boolean) -> Unit = {}) {
        if (purchaseInProgress) {
            onFinished(false)
            return
        }
        purchaseInProgress = true
        billing.launchPurchase(this, coinProduct.productId) { success ->
            runOnUiThread {
                purchaseInProgress = false
                if (success) {
                    analytics.track("coins_purchased", mapOf("product_id" to coinProduct.productId))
                    Toast.makeText(
                        this@StoreActivity,
                        getString(R.string.coins_purchase_success, coinProduct.coinAmount),
                        Toast.LENGTH_SHORT
                    ).show()
                    refreshHeader()
                }
                onFinished(success)
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
