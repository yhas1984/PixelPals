package com.pixelpals.app

import android.content.Context
import com.pixelpals.app.analytics.NoOpAnalyticsTracker
import com.pixelpals.app.analytics.AnalyticsTracker
import com.pixelpals.app.analytics.LogcatAnalyticsTracker
import com.pixelpals.app.billing.BillingRepository
import com.pixelpals.app.billing.DebugPreviewBillingRepository
import com.pixelpals.app.billing.GooglePlayBillingRepository

object AppServices {
    @Volatile
    private var repository: PixelPalsRepository? = null

    @Volatile
    private var analytics: AnalyticsTracker? = null

    @Volatile
    private var billingRepository: BillingRepository? = null

    fun repository(context: Context): PixelPalsRepository {
        return repository ?: synchronized(this) {
            repository ?: PixelPalsRepository(context.applicationContext).also { repository = it }
        }
    }

    fun analytics(context: Context): AnalyticsTracker {
        return analytics ?: synchronized(this) {
            analytics ?: (if (BuildConfig.DEBUG) LogcatAnalyticsTracker() else NoOpAnalyticsTracker()).also { analytics = it }
        }
    }

    fun billingRepository(context: Context): BillingRepository {
        return billingRepository ?: synchronized(this) {
            billingRepository ?: if (BuildConfig.DEBUG) {
                DebugPreviewBillingRepository(
                    repository = repository(context),
                    analytics = analytics(context)
                )
            } else {
                GooglePlayBillingRepository(
                    context = context.applicationContext,
                    repository = repository(context),
                    analytics = analytics(context)
                )
            }.also { billingRepository = it }
        }
    }
}
