package com.pixelpals.app.feature.store

import android.os.Bundle
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.material.tabs.TabLayoutMediator
import androidx.viewpager2.widget.ViewPager2
import com.pixelpals.app.BuildConfig
import com.pixelpals.app.R
import com.pixelpals.app.core.ads.GoogleMobileAdsConsentManager
import com.pixelpals.app.core.analytics.AnalyticsTracker
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.services.AppServices
import com.pixelpals.app.data.catalog.CoinProduct
import com.pixelpals.app.data.prefs.SelectedPetStore
import com.pixelpals.app.databinding.ActivityStoreBinding
import com.pixelpals.app.feature.store.billing.BillingRepository
import com.pixelpals.app.feature.store.billing.PurchaseResult
import com.pixelpals.app.feature.store.billing.RestoreResult
import com.pixelpals.app.navigation.StoreSection
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

class StoreFragment : Fragment() {
    private var bindingReference: ActivityStoreBinding? = null
    private val binding: ActivityStoreBinding
        get() = requireNotNull(bindingReference)
    private val analytics: AnalyticsTracker by lazy { AppServices.analytics(requireContext()) }
    private val billing: BillingRepository by lazy { AppServices.billingRepository(requireContext()) }
    private val consentManager: GoogleMobileAdsConsentManager by lazy {
        GoogleMobileAdsConsentManager.getInstance(requireContext().applicationContext)
    }
    private lateinit var selectedPetStore: SelectedPetStore
    private lateinit var selectedPet: PetType
    private lateinit var storeViewModel: StoreViewModel
    private var adView: AdView? = null
    private var tabMediator: TabLayoutMediator? = null
    private val isMobileAdsInitialized = AtomicBoolean(false)
    private var isPurchaseInProgress: Boolean = false
    private var bannerWidthDp: Int? = null
    private var pendingSection: StoreSection? = null
    private var hasReconciledPurchases: Boolean = false
    private val pageChangeCallback: ViewPager2.OnPageChangeCallback =
        object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position == StoreSection.COINS.pageIndex) storeViewModel.loadCoinCatalog()
            }
        }
    private val bannerLayoutChangeListener = View.OnLayoutChangeListener {
        _, left, _, right, _, oldLeft, _, oldRight, _ ->
        val width: Int = right - left
        val oldWidth: Int = oldRight - oldLeft
        if (width > 0 && width != oldWidth && adView != null) configureBannerAd()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        storeViewModel = ViewModelProvider(
            this,
            StoreViewModel.Factory(requireActivity().application),
        )[StoreViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val inflatedBinding: ActivityStoreBinding = ActivityStoreBinding.inflate(inflater, container, false)
        bindingReference = inflatedBinding
        return inflatedBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        selectedPetStore = SelectedPetStore(requireContext())
        selectedPet = selectedPetStore.load()
        configurePrivacyAction()
        configurePager()
        binding.storePager.registerOnPageChangeCallback(pageChangeCallback)
        if (binding.storePager.currentItem == StoreSection.COINS.pageIndex) {
            storeViewModel.loadCoinCatalog()
        }
        refreshHeader()
        collectStoreState()
        configureConsentAndAds()
        binding.storeRoot.addOnLayoutChangeListener(bannerLayoutChangeListener)
        reconcilePurchasesOnce()
        analytics.track("store_opened_v16")
    }

    override fun onResume() {
        super.onResume()
        if (bindingReference == null) return
        refreshHeader()
        storeViewModel.refreshIfStale()
        adView?.resume()
    }

    override fun onPause() {
        adView?.pause()
        super.onPause()
    }

    override fun onDestroyView() {
        tabMediator?.detach()
        tabMediator = null
        binding.storePager.unregisterOnPageChangeCallback(pageChangeCallback)
        binding.storeRoot.removeOnLayoutChangeListener(bannerLayoutChangeListener)
        adView?.destroy()
        adView = null
        bannerWidthDp = null
        bindingReference = null
        super.onDestroyView()
    }

    fun selectSection(section: StoreSection) {
        pendingSection = section
        bindingReference?.storePager?.setCurrentItem(section.pageIndex, false)
    }

    fun getStoreViewModel(): StoreViewModel = storeViewModel

    fun getCurrentSection(): StoreSection = StoreSection.entries.firstOrNull { section ->
        section.pageIndex == binding.storePager.currentItem
    } ?: StoreSection.PREMIUM

    fun openCoinsTab() {
        selectSection(StoreSection.COINS)
    }

    fun purchaseCoinPack(
        coinProduct: CoinProduct,
        onFinished: (PurchaseResult) -> Unit = {},
    ) {
        if (isPurchaseInProgress) {
            onFinished(PurchaseResult.Unavailable)
            return
        }
        if (!storeViewModel.beginCoinPurchase(coinProduct.productId)) {
            onFinished(PurchaseResult.Unavailable)
            return
        }
        isPurchaseInProgress = true
        val hostActivity = requireActivity()
        billing.launchPurchase(hostActivity, coinProduct.productId) { result ->
            hostActivity.runOnUiThread {
                isPurchaseInProgress = false
                if (result == PurchaseResult.Success) {
                    analytics.track("coins_purchased", mapOf("product_id" to coinProduct.productId))
                    Toast.makeText(
                        hostActivity.applicationContext,
                        hostActivity.getString(R.string.coins_purchase_success, coinProduct.coinAmount),
                        Toast.LENGTH_SHORT,
                    ).show()
                    if (bindingReference != null) refreshHeader()
                }
                storeViewModel.handleCoinPurchase(result)
                onFinished(result)
            }
        }
    }

    private fun configurePrivacyAction() {
        binding.btnPrivacyOptions.setOnClickListener {
            consentManager.showPrivacyOptionsForm(requireActivity()) { error ->
                if (error != null) {
                    android.util.Log.w("UMP", "Privacy options failed: ${error.message}")
                }
                updatePrivacyOptionsVisibility()
            }
        }
    }

    private fun configurePager() {
        binding.storePager.adapter = StorePagerAdapter(this)
        binding.storePager.offscreenPageLimit = StoreSection.entries.size - 1
        tabMediator = TabLayoutMediator(binding.storeTabs, binding.storePager) { tab, position ->
            tab.text = getString(
                when (position) {
                    StoreSection.PREMIUM.pageIndex -> R.string.store_tab_premium
                    StoreSection.COSMETICS.pageIndex -> R.string.store_tab_cosmetics
                    else -> R.string.store_tab_coins
                },
            )
        }.also(TabLayoutMediator::attach)
        pendingSection?.let(::selectSection)
    }

    private fun collectStoreState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                storeViewModel.uiState.collect { state ->
                    if (bindingReference == null) return@collect
                    binding.txtStoreWallet.text = getString(R.string.coins_wallet_format, state.balance)
                    renderStoreState(state)
                }
            }
        }
    }

    private fun renderStoreState(state: StoreUiState) {
        val notice: StoreNotice? = state.notice
        binding.cardStoreState.visibility = if (state.isInitialLoading || notice != null) {
            View.VISIBLE
        } else {
            View.GONE
        }
        binding.progressStoreLoading.visibility = if (state.isInitialLoading) View.VISIBLE else View.GONE
        binding.txtStoreStatus.text = notice?.let(::getNoticeText).orEmpty()
        binding.txtStoreStatus.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (isErrorNotice(notice)) R.color.red_error else R.color.status_info_fg,
            ),
        )
        val canOpenCoins: Boolean = notice?.type == StoreNoticeType.INSUFFICIENT_COINS
        val canRetry: Boolean = notice?.type == StoreNoticeType.STORE_FAILURE
        binding.btnStoreRetry.visibility = if (canRetry || canOpenCoins) {
            View.VISIBLE
        } else {
            View.GONE
        }
        binding.btnStoreRetry.setText(
            if (canOpenCoins) R.string.store_open_coins else R.string.selection_retry,
        )
        binding.btnStoreRetry.setOnClickListener {
            if (canOpenCoins) {
                storeViewModel.clearNotice()
                openCoinsTab()
            } else {
                storeViewModel.refresh()
            }
        }
    }

    private fun refreshHeader() {
        selectedPet = selectedPetStore.load()
        binding.txtStoreSubtitle.text = getString(
            R.string.store_subtitle_format,
            getString(selectedPet.displayNameResId),
        )
    }

    private fun reconcilePurchasesOnce() {
        if (hasReconciledPurchases) return
        hasReconciledPurchases = true
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result: RestoreResult = billing.reconcilePurchases()) {
                is RestoreResult.Failure -> storeViewModel.reportFailure(result.reason)
                is RestoreResult.Restored -> storeViewModel.refresh()
                RestoreResult.NothingToRestore,
                RestoreResult.Unavailable -> Unit
            }
        }
    }

    private fun getNoticeText(notice: StoreNotice): String = when (notice.type) {
        StoreNoticeType.INSUFFICIENT_COINS -> getString(R.string.store_insufficient_coins)
        StoreNoticeType.PURCHASE_CANCELLED -> getString(R.string.store_purchase_cancelled)
        StoreNoticeType.PURCHASE_PENDING -> getString(R.string.store_purchase_pending)
        StoreNoticeType.BILLING_UNAVAILABLE -> getString(R.string.store_billing_unavailable)
        StoreNoticeType.PURCHASE_FAILED -> getString(R.string.store_purchase_failed)
        StoreNoticeType.STORE_FAILURE -> notice.detail ?: getString(R.string.store_error)
    }

    private fun isErrorNotice(notice: StoreNotice?): Boolean = when (notice?.type) {
        StoreNoticeType.PURCHASE_CANCELLED,
        StoreNoticeType.PURCHASE_PENDING,
        null -> false
        else -> true
    }

    private fun configureConsentAndAds() {
        if (!BuildConfig.BANNER_ADS_ENABLED) {
            binding.bannerAdContainer.visibility = View.GONE
            updatePrivacyOptionsVisibility()
            return
        }
        updatePrivacyOptionsVisibility()
        if (consentManager.canRequestAds) initializeMobileAds()
        consentManager.gatherConsent(requireActivity()) { error ->
            if (error != null) {
                android.util.Log.w("UMP", "Consent failed: ${error.errorCode} ${error.message}")
            }
            updatePrivacyOptionsVisibility()
            if (consentManager.canRequestAds) initializeMobileAds()
        }
    }

    private fun updatePrivacyOptionsVisibility() {
        bindingReference?.btnPrivacyOptions?.visibility = if (
            BuildConfig.ADS_ENABLED && consentManager.isPrivacyOptionsRequired
        ) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun initializeMobileAds() {
        if (!BuildConfig.BANNER_ADS_ENABLED || !consentManager.canRequestAds) return
        // UMP completes on a later main-thread callback. The store can have
        // been hidden or its view destroyed in the meantime, so never start
        // banner work without an attached view/context.
        if (!isAdded || bindingReference == null) return
        if (!isMobileAdsInitialized.compareAndSet(false, true)) {
            configureBannerAd()
            return
        }
        if (BuildConfig.DEBUG && BuildConfig.UMP_TEST_DEVICE_ID.isNotBlank()) {
            MobileAds.setRequestConfiguration(
                RequestConfiguration.Builder()
                    .setTestDeviceIds(listOf(BuildConfig.UMP_TEST_DEVICE_ID))
                    .build(),
            )
        }
        MobileAds.initialize(requireContext()) {
            activity?.runOnUiThread {
                if (isAdded && bindingReference != null) configureBannerAd()
            }
        }
    }

    private fun configureBannerAd() {
        if (!isAdded) return
        val currentBinding: ActivityStoreBinding = bindingReference ?: return
        val container: FrameLayout = currentBinding.bannerAdContainer
        val currentContext: Context = context ?: return
        container.post {
            if (!isAdded || bindingReference?.bannerAdContainer !== container) return@post
            val widthDp: Int = calculateBannerWidthDp(currentBinding)
            if (adView != null && bannerWidthDp == widthDp) return@post
            adView?.destroy()
            container.removeAllViews()
            bannerWidthDp = widthDp
            val bannerView: AdView = createBannerView(currentContext, container, widthDp)
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

    private fun createBannerView(context: Context, container: FrameLayout, widthDp: Int): AdView =
        AdView(context).apply {
            setAdUnitId(BuildConfig.ADMOB_BANNER_AD_UNIT_ID)
            setAdSize(
                AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp),
            )
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    if (bindingReference == null) return
                    container.visibility = View.VISIBLE
                    container.requestLayout()
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    android.util.Log.w(
                        "AdMob",
                        "Banner failed: code=${adError.code} msg=${adError.message}",
                    )
                    if (bindingReference != null) container.visibility = View.GONE
                }
            }
        }

    private fun calculateBannerWidthDp(storeBinding: ActivityStoreBinding): Int =
        ((storeBinding.storeRoot.width - storeBinding.bannerAdContainer.paddingLeft -
            storeBinding.bannerAdContainer.paddingRight) / resources.displayMetrics.density)
            .roundToInt().coerceAtLeast(1)

    private class StorePagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun getItemCount(): Int = StoreSection.entries.size

        override fun createFragment(position: Int): Fragment = when (position) {
            StoreSection.PREMIUM.pageIndex -> PetsTabFragment()
            StoreSection.COSMETICS.pageIndex -> CosmeticsTabFragment()
            else -> CoinsTabFragment()
        }
    }
}
