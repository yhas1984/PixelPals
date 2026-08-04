package com.pixelpals.app.feature.store.billing

import android.app.Activity

interface BillingRepository {
    suspend fun prefetch(productIds: List<String>): Map<String, String>
    fun launchPurchase(activity: Activity, productId: String, onFinished: (Boolean) -> Unit = {})
    suspend fun restorePurchases(): Int
}
