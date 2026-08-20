package com.pixelpals.app.core.ads

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.annotation.MainThread
import androidx.lifecycle.Lifecycle
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.appopen.AppOpenAd
import com.pixelpals.app.BuildConfig
import java.lang.ref.WeakReference

/** Process-scoped App Open ad gate for the root activity. */
class AppOpenAdController private constructor(context: Context) {
    private val applicationContext: Context = context.applicationContext
    private val consentManager: GoogleMobileAdsConsentManager =
        GoogleMobileAdsConsentManager.getInstance(applicationContext)
    private val launchPreferences = applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val launchGate: AppOpenLaunchGate = AppOpenLaunchGate()
    private val mainHandler: Handler = Handler(Looper.getMainLooper())
    private var hasStarted: Boolean = false
    private var hasShown: Boolean = false
    private var isLoading: Boolean = false
    private var isShowing: Boolean = false
    private var isMobileAdsInitialized: Boolean = false
    private var loadedAd: AppOpenAd? = null
    private var resumedActivity: WeakReference<ComponentActivity>? = null
    private var launchWindowOpen: Boolean = false
    private var launchWindowClosed: Boolean = false
    private val closeLaunchWindowRunnable: Runnable = Runnable {
        launchWindowOpen = false
        launchWindowClosed = true
    }

    @MainThread
    fun start(activity: ComponentActivity) {
        if (!BuildConfig.APP_OPEN_ADS_ENABLED || hasStarted) return
        hasStarted = true
        resumedActivity = WeakReference(activity)
        if (!registerLaunchAndCheckEligibility()) return
        requestConsentAndLoad(activity)
    }

    @MainThread
    fun onActivityResumed(activity: ComponentActivity) {
        if (!BuildConfig.APP_OPEN_ADS_ENABLED) return
        resumedActivity = WeakReference(activity)
        if (!hasStarted) start(activity)
        showIfReady()
    }

    @MainThread
    fun onActivityPaused(activity: ComponentActivity) {
        if (resumedActivity?.get() === activity) resumedActivity = null
    }

    @MainThread
    fun onUserInteraction() {
        launchWindowOpen = false
        launchWindowClosed = true
        mainHandler.removeCallbacks(closeLaunchWindowRunnable)
    }

    private fun registerLaunchAndCheckEligibility(): Boolean {
        val decision: AppOpenLaunchDecision = launchGate.register(
            launchPreferences.getInt(KEY_LAUNCH_COUNT, 0),
        )
        launchPreferences.edit().putInt(KEY_LAUNCH_COUNT, decision.launchCount).apply()
        return decision.isEligible
    }

    private fun requestConsentAndLoad(activity: ComponentActivity) {
        if (consentManager.canRequestAds) {
            openLaunchWindow()
            initializeAndLoad()
            return
        }
        consentManager.gatherConsent(activity) { error ->
            if (error != null) Log.w(TAG, "Consent failed: ${error.errorCode} ${error.message}")
            if (consentManager.canRequestAds) {
                openLaunchWindow()
                initializeAndLoad()
            }
        }
    }

    private fun openLaunchWindow() {
        if (launchWindowClosed || hasShown) return
        launchWindowOpen = true
        mainHandler.removeCallbacks(closeLaunchWindowRunnable)
        mainHandler.postDelayed(closeLaunchWindowRunnable, LAUNCH_WINDOW_MS)
    }

    private fun initializeAndLoad() {
        if (!consentManager.canRequestAds || hasShown || isLoading || loadedAd != null) return
        if (isMobileAdsInitialized) {
            loadAd()
            return
        }
        isMobileAdsInitialized = true
        if (BuildConfig.DEBUG && BuildConfig.UMP_TEST_DEVICE_ID.isNotBlank()) {
            MobileAds.setRequestConfiguration(
                RequestConfiguration.Builder()
                    .setTestDeviceIds(listOf(BuildConfig.UMP_TEST_DEVICE_ID))
                    .build(),
            )
        }
        MobileAds.initialize(applicationContext) { mainHandler.post(::loadAd) }
    }

    private fun loadAd() {
        if (!consentManager.canRequestAds || hasShown || isLoading || loadedAd != null) return
        isLoading = true
        AppOpenAd.load(
            applicationContext,
            BuildConfig.ADMOB_APP_OPEN_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(appOpenAd: AppOpenAd) {
                    isLoading = false
                    loadedAd = appOpenAd
                    showIfReady()
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    isLoading = false
                    Log.w(TAG, "App Open failed: code=${adError.code} msg=${adError.message}")
                }
            },
        )
    }

    @MainThread
    private fun showIfReady() {
        val activity: ComponentActivity = resumedActivity?.get() ?: return
        val appOpenAd: AppOpenAd = loadedAd ?: return
        if (!launchWindowOpen || hasShown || isShowing || activity.isFinishing ||
            activity.isDestroyed || !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        ) return
        loadedAd = null
        hasShown = true
        isShowing = true
        appOpenAd.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                isShowing = false
            }

            override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                isShowing = false
                Log.w(TAG, "App Open could not be shown: ${adError.message}")
            }
        }
        appOpenAd.show(activity)
    }

    companion object {
        private const val TAG: String = "AdMobAppOpen"
        private const val PREFERENCES_NAME: String = "app_open_ad_state"
        private const val KEY_LAUNCH_COUNT: String = "launch_count"
        private const val LAUNCH_WINDOW_MS: Long = 2_500L

        @Volatile
        private var instance: AppOpenAdController? = null

        fun getInstance(context: Context): AppOpenAdController =
            instance ?: synchronized(this) {
                instance ?: AppOpenAdController(context).also { instance = it }
            }
    }
}
