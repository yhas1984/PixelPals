package com.pixelpals.app.core.services

import android.content.Context
import com.pixelpals.app.BuildConfig
import com.pixelpals.app.core.analytics.NoOpAnalyticsTracker
import com.pixelpals.app.core.analytics.AnalyticsTracker
import com.pixelpals.app.core.analytics.LogcatAnalyticsTracker
import com.pixelpals.app.data.repository.PixelPalsRepository
import com.pixelpals.app.feature.store.billing.BillingRepository
import com.pixelpals.app.feature.store.billing.DebugPreviewBillingRepository
import com.pixelpals.app.feature.store.billing.GooglePlayBillingRepository

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
