package com.pixelpals.app.core.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.MainThread
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.appopen.AppOpenAd
import com.pixelpals.app.BuildConfig
import java.lang.ref.WeakReference

/** Process-scoped App Open manager driven by the application foreground lifecycle. */
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

    private var hasRegisteredLaunch: Boolean = false
    private var isLaunchEligible: Boolean = false
    private var hasShown: Boolean = false
    private var isLoading: Boolean = false
    private var isShowing: Boolean = false
    private var isRequestingConsent: Boolean = false
    private var isMobileAdsInitialized: Boolean = false
    private var loadedAd: AppOpenAd? = null
    private var loadedAtMillis: Long = 0L
    private var currentActivity: WeakReference<Activity>? = null
    private var launchWindowOpen: Boolean = false
    private val closeLaunchWindowRunnable: Runnable = Runnable {
        launchWindowOpen = false
        Log.d(TAG, "Foreground window closed; keeping any loaded ad for the next foreground")
    }

    val isShowingAd: Boolean
        get() = isShowing

    /** Starts SDK initialization early when consent from a previous session permits it. */
    @MainThread
    fun preloadIfPossible() {
        if (!BuildConfig.APP_OPEN_ADS_ENABLED) {
            Log.d(TAG, "App Open disabled for this build")
            return
        }
        if (consentManager.canRequestAds) initializeAndLoad()
    }

    @MainThread
    fun onActivityStarted(activity: Activity) {
        if (!isShowing) currentActivity = WeakReference(activity)
    }

    @MainThread
    fun onActivityResumed(activity: Activity) {
        if (!BuildConfig.APP_OPEN_ADS_ENABLED || isShowing) return
        currentActivity = WeakReference(activity)
        if (consentManager.canRequestAds) {
            initializeAndLoad()
            showIfReady()
        } else {
            requestConsent(activity)
        }
    }

    @MainThread
    fun onActivityDestroyed(activity: Activity) {
        if (currentActivity?.get() === activity) currentActivity = null
    }

    /** Called only when the whole app process moves from background to foreground. */
    @MainThread
    fun onAppForegrounded() {
        if (!BuildConfig.APP_OPEN_ADS_ENABLED || hasShown || isShowing) return
        registerLaunchOnce()
        preloadIfPossible()
        if (!isLaunchEligible) return
        openLaunchWindow()
        showIfReady()
    }

    @MainThread
    fun onAppBackgrounded() {
        closeLaunchWindow()
    }

    @MainThread
    fun onUserInteraction() {
        closeLaunchWindow()
    }

    private fun registerLaunchOnce() {
        if (hasRegisteredLaunch) return
        hasRegisteredLaunch = true
        val decision: AppOpenLaunchDecision = launchGate.register(
            launchPreferences.getInt(KEY_LAUNCH_COUNT, 0),
        )
        launchPreferences.edit().putInt(KEY_LAUNCH_COUNT, decision.launchCount).apply()
        isLaunchEligible = decision.isEligible
        Log.d(TAG, "Launch ${decision.launchCount}; eligible=${decision.isEligible}")
    }

    private fun requestConsent(activity: Activity) {
        if (isRequestingConsent) return
        isRequestingConsent = true
        consentManager.gatherConsent(activity) { error ->
            isRequestingConsent = false
            if (error != null) {
                Log.w(TAG, "Consent failed: ${error.errorCode} ${error.message}")
            }
            if (consentManager.canRequestAds) initializeAndLoad()
        }
    }

    private fun openLaunchWindow() {
        launchWindowOpen = true
        mainHandler.removeCallbacks(closeLaunchWindowRunnable)
        mainHandler.postDelayed(closeLaunchWindowRunnable, LAUNCH_WINDOW_MS)
    }

    private fun closeLaunchWindow() {
        launchWindowOpen = false
        mainHandler.removeCallbacks(closeLaunchWindowRunnable)
    }

    private fun initializeAndLoad() {
        if (!consentManager.canRequestAds || hasShown || isLoading || isAdAvailable()) return
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
        if (!consentManager.canRequestAds || hasShown || isLoading || isAdAvailable()) return
        isLoading = true
        AppOpenAd.load(
            applicationContext,
            BuildConfig.ADMOB_APP_OPEN_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(appOpenAd: AppOpenAd) {
                    isLoading = false
                    loadedAd = appOpenAd
                    loadedAtMillis = System.currentTimeMillis()
                    Log.d(TAG, "App Open loaded")
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
        if (!isAdAvailable()) return
        val activity: Activity = currentActivity?.get() ?: return
        val lifecycle = activity as? LifecycleOwner ?: return
        if (!launchWindowOpen || !isLaunchEligible || hasShown || isShowing ||
            activity.isFinishing || activity.isDestroyed ||
            !lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        ) return

        val appOpenAd: AppOpenAd = loadedAd ?: return
        loadedAd = null
        loadedAtMillis = 0L
        hasShown = true
        isShowing = true
        closeLaunchWindow()
        appOpenAd.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                isShowing = false
                Log.d(TAG, "App Open dismissed")
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                isShowing = false
                Log.w(TAG, "App Open could not be shown: ${adError.message}")
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "App Open shown")
            }
        }
        appOpenAd.show(activity)
    }

    private fun isAdAvailable(nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (loadedAd == null) return false
        if (AppOpenAdFreshness.isFresh(loadedAtMillis, nowMillis)) return true
        loadedAd = null
        loadedAtMillis = 0L
        Log.d(TAG, "Discarded expired App Open ad")
        return false
    }

    companion object {
        private const val TAG: String = "AdMobAppOpen"
        private const val PREFERENCES_NAME: String = "app_open_ad_state"
        private const val KEY_LAUNCH_COUNT: String = "launch_count"
        private const val LAUNCH_WINDOW_MS: Long = 5_000L

        @Volatile
        private var instance: AppOpenAdController? = null

        fun getInstance(context: Context): AppOpenAdController =
            instance ?: synchronized(this) {
                instance ?: AppOpenAdController(context).also { instance = it }
            }
    }
}

internal object AppOpenAdFreshness {
    private const val MAX_AGE_MILLIS: Long = 4L * 60L * 60L * 1_000L

    fun isFresh(loadedAtMillis: Long, nowMillis: Long): Boolean {
        val ageMillis = nowMillis - loadedAtMillis
        return loadedAtMillis > 0L && ageMillis in 0 until MAX_AGE_MILLIS
    }
}
