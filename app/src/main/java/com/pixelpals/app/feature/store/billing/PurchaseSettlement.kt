package com.pixelpals.app.feature.store.billing

internal data class PurchaseSettlement(val newlyGranted: Int, val settled: Boolean)

/** Grant must be durable and idempotent. A crash during settlement is safe to retry. */
internal suspend fun fulfillBeforeSettlement(grant: suspend () -> Int, settle: suspend () -> Boolean): PurchaseSettlement {
    val granted = grant()
    return PurchaseSettlement(granted, settle())
}
