package com.pixelpals.app.billing

import android.app.Activity
import com.pixelpals.app.PixelPalsRepository
import com.pixelpals.app.analytics.AnalyticsTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DebugPreviewBillingRepository(
    private val repository: PixelPalsRepository,
    private val analytics: AnalyticsTracker
) : BillingRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun prefetch(productIds: List<String>): Map<String, String> {
        return productIds.distinct().associateWith { "debug_preview" }
    }

    override fun launchPurchase(activity: Activity, productId: String, onFinished: (Boolean) -> Unit) {
        scope.launch {
            repository.grantOwnedProduct(productId, source = "debug_preview")
            analytics.track("store_preview_unlock", mapOf("product_id" to productId))
            withContext(Dispatchers.Main) {
                onFinished(true)
            }
        }
    }

    override suspend fun restorePurchases(): Int = withContext(Dispatchers.IO) { 0 }
}
