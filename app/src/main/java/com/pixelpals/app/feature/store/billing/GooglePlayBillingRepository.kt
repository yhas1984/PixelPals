package com.pixelpals.app.feature.store.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryProductDetailsResult
import com.android.billingclient.api.QueryPurchasesParams
import com.pixelpals.app.data.catalog.CoinProduct
import com.pixelpals.app.data.repository.PixelPalsRepository
import com.pixelpals.app.core.analytics.AnalyticsTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class GooglePlayBillingRepository(
    context: Context,
    private val repository: PixelPalsRepository,
    private val analytics: AnalyticsTracker
) : BillingRepository, PurchasesUpdatedListener {

    companion object {
        private const val BILLING_TIMEOUT_MS = 10_000L
        private val ALLOWED_PRODUCT_IDS = setOf(
            // Premium pets (los nuevos se compran con monedas; IDs por compatibilidad)
            "pet_angel_premium",
            "pet_diablillo_premium",
            "pet_yuki_premium",
            "pet_piru_premium",
            "pet_taro_premium",
            "pet_menta_premium",
            // Coin packs
            "coins_small",
            "coins_medium",
            "coins_large",
            "coins_mega",
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val productDetailsCache = mutableMapOf<String, ProductDetails>()
    private val priceCache = mutableMapOf<String, String>()
    private var purchaseCallbackToken = 0
    private var onPurchaseFinished: ((Boolean) -> Unit)? = null

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    override suspend fun prefetch(productIds: List<String>): Map<String, String> {
        val whitelisted = productIds.distinct().filter(ALLOWED_PRODUCT_IDS::contains)
        if (whitelisted.isEmpty()) return emptyMap()
        val setup = withTimeoutOrNull(BILLING_TIMEOUT_MS) { ensureConnected() } ?: return emptyMap()
        if (setup.responseCode != BillingClient.BillingResponseCode.OK) return emptyMap()
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            whitelisted.map { productId ->
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(productId)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            }
        ).build()
        val result = withTimeoutOrNull(BILLING_TIMEOUT_MS) { queryProductDetails(params) } ?: return emptyMap()
        if (result.first.responseCode != BillingClient.BillingResponseCode.OK) return emptyMap()
        result.second.forEach { details ->
            productDetailsCache[details.productId] = details
            priceCache[details.productId] = details.oneTimePurchaseOfferDetails?.formattedPrice ?: ""
        }
        return whitelisted.associateWith { priceCache[it] ?: "" }
    }

    override fun launchPurchase(activity: Activity, productId: String, onFinished: (Boolean) -> Unit) {
        val details = productDetailsCache[productId] ?: run {
            onFinished(false)
            return
        }
        val callbackId = ++purchaseCallbackToken
        onPurchaseFinished = { success -> if (callbackId == purchaseCallbackToken) onFinished(success) }
        val result = billingClient.launchBillingFlow(activity, BillingFlowParams.newBuilder().setProductDetailsParamsList(
            listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).build())
        ).build())
        if (result.responseCode != BillingClient.BillingResponseCode.OK) finishPurchase(callbackId, false)
    }

    override suspend fun restorePurchases(): Int {
        val setup = withTimeoutOrNull(BILLING_TIMEOUT_MS) { ensureConnected() } ?: return 0
        if (setup.responseCode != BillingClient.BillingResponseCode.OK) return 0
        val result = withTimeoutOrNull(BILLING_TIMEOUT_MS) { queryPurchases() } ?: return 0
        if (result.first.responseCode != BillingClient.BillingResponseCode.OK) return 0
        var restored = 0
        result.second.forEach { purchase ->
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) restored += grantPurchase(purchase, "restore")
        }
        analytics.track("store_restore", mapOf("restored_count" to restored.toString()))
        return restored
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) return finishPurchase(purchaseCallbackToken, false)
        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK || purchases.isNullOrEmpty()) return finishPurchase(purchaseCallbackToken, false)
        scope.launch {
            val granted = purchases.sumOf { purchase ->
                when (purchase.purchaseState) {
                    Purchase.PurchaseState.PURCHASED -> grantPurchase(purchase, "billing")
                    Purchase.PurchaseState.PENDING -> {
                        analytics.track("store_purchase_pending", emptyMap())
                        0
                    }
                    else -> 0
                }
            }
            finishPurchase(purchaseCallbackToken, granted > 0)
        }
    }

    private suspend fun grantPurchase(purchase: Purchase, source: String): Int {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return 0
        var granted = 0
        var hasConsumable = false
        for (productId in purchase.products) {
            if (!isWhitelisted(productId)) continue
            repository.grantOwnedProduct(productId, source)
            // Los coin packs también otorgan su cantidad al balance del pet activo.
            CoinProduct.CATALOG.firstOrNull { it.productId == productId }?.let { coinPack ->
                repository.grantCoins(petType = null, amount = coinPack.coinAmount)
            }
            analytics.track("store_purchase_granted", mapOf("product_id" to productId, "source" to source))
            granted++
            if (productId.startsWith("coins_")) hasConsumable = true
        }
        if (granted > 0) {
            acknowledgeSafely(purchase)
            // Los coin packs son consumibles: Play bloquea comprar el mismo SKU de nuevo
            // hasta consumirlo. Consumimos aquí para permitir compras repetidas.
            if (hasConsumable) consumeSafely(purchase)
        }
        return granted
    }

    private suspend fun consumeSafely(purchase: Purchase) {
        suspendCancellableCoroutine<Unit> { cont ->
            billingClient.consumeAsync(
                ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
            ) { billingResult, _ ->
                analytics.track("store_consume", mapOf("code" to billingResult.responseCode.toString()))
                if (cont.isActive) cont.resume(Unit)
            }
        }
    }

    private suspend fun acknowledgeSafely(purchase: Purchase) {
        if (purchase.isAcknowledged) return
        suspendCancellableCoroutine<Unit> { cont ->
            billingClient.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
            ) { billingResult ->
                analytics.track("store_acknowledge", mapOf("code" to billingResult.responseCode.toString()))
                if (cont.isActive) cont.resume(Unit)
            }
        }
    }

    private suspend fun ensureConnected(): BillingResult {
        if (billingClient.isReady) return okResult()
        return suspendCancellableCoroutine { cont ->
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) { if (cont.isActive) cont.resume(billingResult) }
                override fun onBillingServiceDisconnected() {
                    analytics.track("billing_disconnected")
                    if (cont.isActive) {
                        cont.resume(
                            BillingResult.newBuilder()
                                .setResponseCode(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED)
                                .setDebugMessage("Billing service disconnected")
                                .build()
                        )
                    }
                }
            })
        }
    }

    private suspend fun queryProductDetails(params: QueryProductDetailsParams): Pair<BillingResult, List<ProductDetails>> =
        suspendCancellableCoroutine { cont ->
            billingClient.queryProductDetailsAsync(params) { billingResult, result: QueryProductDetailsResult ->
                if (cont.isActive) cont.resume(billingResult to result.productDetailsList)
            }
        }

    private suspend fun queryPurchases(): Pair<BillingResult, List<Purchase>> =
        suspendCancellableCoroutine { cont -> billingClient.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { billingResult, purchases -> if (cont.isActive) cont.resume(billingResult to purchases) } }

    private fun finishPurchase(token: Int, success: Boolean) {
        if (token != purchaseCallbackToken) return
        onPurchaseFinished?.invoke(success)
        onPurchaseFinished = null
    }

    private fun isWhitelisted(productId: String): Boolean = productId in ALLOWED_PRODUCT_IDS

    private fun okResult(): BillingResult = BillingResult.newBuilder().setResponseCode(BillingClient.BillingResponseCode.OK).setDebugMessage("already_connected").build()

    fun close() {
        billingClient.endConnection()
        scope.cancel()
    }
}
