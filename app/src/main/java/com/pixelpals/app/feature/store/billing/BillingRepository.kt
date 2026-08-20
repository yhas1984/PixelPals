package com.pixelpals.app.feature.store.billing

import android.app.Activity

interface BillingRepository {
    suspend fun prefetch(productIds: List<String>): ProductCatalogResult
    fun launchPurchase(activity: Activity, productId: String, onFinished: (PurchaseResult) -> Unit = {})
    suspend fun reconcilePurchases(): RestoreResult
}

sealed interface ProductCatalogResult {
    data class Available(
        val prices: Map<String, String>,
        val missingProductIds: Set<String> = emptySet(),
    ) : ProductCatalogResult
    data class Unavailable(val reason: String) : ProductCatalogResult
    data class Failure(val reason: String) : ProductCatalogResult
}

sealed interface PurchaseResult {
    data object Success : PurchaseResult
    data object Cancelled : PurchaseResult
    data object Pending : PurchaseResult
    data object Unavailable : PurchaseResult
    data class Failure(val reason: String) : PurchaseResult
}

sealed interface RestoreResult {
    data class Restored(val count: Int) : RestoreResult
    data object NothingToRestore : RestoreResult
    data object Unavailable : RestoreResult
    data class Failure(val reason: String) : RestoreResult
}
