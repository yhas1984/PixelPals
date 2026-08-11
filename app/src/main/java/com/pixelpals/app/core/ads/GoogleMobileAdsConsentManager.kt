package com.pixelpals.app.core.ads

import android.app.Activity
import android.content.Context
import androidx.annotation.MainThread
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform
import com.pixelpals.app.BuildConfig

/** Keeps the UMP flow in one place so ads cannot bypass consent gating. */
class GoogleMobileAdsConsentManager private constructor(context: Context) {
    private val consentInformation = UserMessagingPlatform.getConsentInformation(context)

    val canRequestAds: Boolean
        get() = consentInformation.canRequestAds()

    val isPrivacyOptionsRequired: Boolean
        get() = consentInformation.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    @MainThread
    fun gatherConsent(activity: Activity, onComplete: (FormError?) -> Unit) {
        val parametersBuilder = ConsentRequestParameters.Builder()
        if (BuildConfig.DEBUG) {
            val debugSettingsBuilder = ConsentDebugSettings.Builder(activity)
            if (BuildConfig.UMP_TEST_DEVICE_ID.isNotBlank()) {
                debugSettingsBuilder.addTestDeviceHashedId(BuildConfig.UMP_TEST_DEVICE_ID)
            }
            if (BuildConfig.UMP_DEBUG_EEA) {
                debugSettingsBuilder.setDebugGeography(
                    ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA
                )
            }
            parametersBuilder.setConsentDebugSettings(debugSettingsBuilder.build())
        }

        consentInformation.requestConsentInfoUpdate(
            activity,
            parametersBuilder.build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity, onComplete)
            },
            onComplete,
        )
    }

    @MainThread
    fun showPrivacyOptionsForm(activity: Activity, onComplete: (FormError?) -> Unit) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity, onComplete)
    }

    companion object {
        @Volatile
        private var instance: GoogleMobileAdsConsentManager? = null

        fun getInstance(context: Context): GoogleMobileAdsConsentManager {
            return instance ?: synchronized(this) {
                instance ?: GoogleMobileAdsConsentManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
