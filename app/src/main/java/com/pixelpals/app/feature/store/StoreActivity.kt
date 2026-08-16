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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.core.content.ContextCompat
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.material.tabs.TabLayoutMediator
import com.pixelpals.app.BuildConfig
import com.pixelpals.app.R
import com.pixelpals.app.core.analytics.AnalyticsTracker
import com.pixelpals.app.core.ads.GoogleMobileAdsConsentManager
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.services.AppServices
import com.pixelpals.app.data.catalog.CoinProduct
import com.pixelpals.app.data.prefs.SelectedPetStore
import com.pixelpals.app.feature.store.billing.BillingRepository
import com.pixelpals.app.feature.store.billing.PurchaseResult
import com.pixelpals.app.feature.store.billing.RestoreResult
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.pixelpals.app.navigation.PixelPalsDestination
import com.pixelpals.app.navigation.RootNavigation

class StoreActivity : AppCompatActivity() {

    private lateinit var selectedPetStore: SelectedPetStore
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
    private lateinit var storeViewModel: StoreViewModel
    private var bannerWidthDp: Int? = null
    private var storeRetryAction: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTitle(R.string.store_title)
        edgeToEdge()
        setContentView(R.layout.activity_store)
        storeViewModel = ViewModelProvider(this, StoreViewModel.Factory(application))[StoreViewModel::class.java]
        selectedPetStore = SelectedPetStore(this)
        selectedPet = selectedPetStore.load()

        applySystemBarsInsets()
        setupRootNavigation()
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
                    0 -> getString(R.string.store_tab_premium)
                    1 -> getString(R.string.store_tab_cosmetics)
                    else -> getString(R.string.store_tab_coins)
                }
            }.attach()
        }

        refreshHeader()
        analytics.track("store_opened_v15")
        isStoreCreated = true
        observeStoreState()
        lifecycleScope.launch {
            when (val result = billing.reconcilePurchases()) {
                is RestoreResult.Failure -> storeViewModel.setMessage(result.reason, true)
                RestoreResult.Unavailable -> Unit
                RestoreResult.NothingToRestore -> Unit
                is RestoreResult.Restored -> refreshHeader()
            }
            refreshHeader()
        }
    }

    override fun onResume() {
        super.onResume()
        // Al volver de Google Play, vuelve a leer la selección y el estado local
        // para reflejar inmediatamente una compra restaurada o pendiente.
        if (isStoreCreated) {
            refreshHeader()
            storeViewModel.refresh()
        }
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

    private fun observeStoreState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                storeViewModel.uiState.collect { state ->
                    findViewById<TextView>(R.id.txtStoreWallet).text =
                        getString(R.string.coins_wallet_format, state.balance)
                    renderStoreState(state)
                }
            }
        }
    }

    private fun renderStoreState(state: StoreUiState) {
        val card = findViewById<View>(R.id.cardStoreState)
        val progress = findViewById<View>(R.id.progressStoreLoading)
        val status = findViewById<TextView>(R.id.txtStoreStatus)
        val retry = findViewById<android.widget.Button>(R.id.btnStoreRetry)
        card.visibility = if (state.isLoading || state.message != null) View.VISIBLE else View.GONE
        progress.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        status.text = state.message.orEmpty()
        status.setTextColor(ContextCompat.getColor(this, if (state.isError) R.color.red_error else R.color.status_info_fg))
        retry.visibility = if (state.canRetry || state.canOpenCoins) View.VISIBLE else View.GONE
        retry.setText(if (state.canOpenCoins) R.string.store_open_coins else R.string.selection_retry)
        retry.setOnClickListener {
            if (state.canOpenCoins) openCoinsTab() else storeRetryAction?.invoke() ?: storeViewModel.refresh()
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
        container.post {
            val widthDp = calculateBannerWidthDp(container)
            if (adView != null && bannerWidthDp == widthDp) return@post
            adView?.destroy()
            container.removeAllViews()
            bannerWidthDp = widthDp
            val bannerView = createBannerView(container, widthDp)
            container.visibility = View.GONE
            container.addView(
                bannerView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            bannerView.loadAd(AdRequest.Builder().build())
            adView = bannerView
        }
    }

    private fun createBannerView(container: FrameLayout, widthDp: Int): AdView {
        return AdView(this).apply {
            setAdUnitId(BuildConfig.ADMOB_BANNER_AD_UNIT_ID)
            setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this@StoreActivity, widthDp))
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    container.visibility = View.VISIBLE
                    container.requestLayout()
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
    }

    private fun calculateBannerWidthDp(container: FrameLayout): Int {
        val availableWidth = findViewById<View>(R.id.storeRoot).width
        return ((availableWidth - container.paddingLeft - container.paddingRight) /
            resources.displayMetrics.density).roundToInt().coerceAtLeast(1)
    }

    private fun setupRootNavigation() {
        val navigation = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        RootNavigation.install(this, PixelPalsDestination.STORE, navigation)
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
    }

    /** Starts a coin purchase; Billing owns fulfillment and the UI only reports the result. */
    fun purchaseCoinPack(coinProduct: CoinProduct, onFinished: (PurchaseResult) -> Unit = {}) {
        if (purchaseInProgress) {
            onFinished(PurchaseResult.Unavailable)
            return
        }
        purchaseInProgress = true
        storeViewModel.setCoinPurchaseActive(coinProduct.productId)
        storeViewModel.setMessage(getString(R.string.store_loading))
        billing.launchPurchase(this, coinProduct.productId) { success ->
            runOnUiThread {
                purchaseInProgress = false
                if (success == PurchaseResult.Success) {
                    analytics.track("coins_purchased", mapOf("product_id" to coinProduct.productId))
                    Toast.makeText(
                        this@StoreActivity,
                        getString(R.string.coins_purchase_success, coinProduct.coinAmount),
                        Toast.LENGTH_SHORT
                    ).show()
                    refreshHeader()
                }
                storeViewModel.handleCoinPurchase(success)
                onFinished(success)
            }
        }
    }

    private fun edgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = ContextCompat.getColor(this, R.color.surface_base)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.surface_base)
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
            if (v.width > 0 && adView != null && bannerWidthDp != calculateBannerWidthDp(findViewById(R.id.bannerAdContainer))) {
                setupBannerAd()
            }
            insets
        }
    }

    fun getStoreViewModel(): StoreViewModel = storeViewModel

    fun openCoinsTab() {
        findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.storePager).setCurrentItem(2, true)
    }

    fun setStoreRetryAction(action: (() -> Unit)?) {
        storeRetryAction = action
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
