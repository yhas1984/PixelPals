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
import com.pixelpals.app.core.analytics.AnalyticsTracker
import com.pixelpals.app.data.catalog.CoinProduct
import com.pixelpals.app.data.repository.PixelPalsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class GooglePlayBillingRepository(
    context: Context,
    private val repository: PixelPalsRepository,
    private val analytics: AnalyticsTracker,
) : BillingRepository, PurchasesUpdatedListener {

    companion object {
        private const val BILLING_TIMEOUT_MS = 10_000L
        private val ALLOWED_PRODUCT_IDS = setOf(
            // Premium pets remain supported for already-configured products.
            "pet_angel_premium",
            "pet_diablillo_premium",
            "pet_yuki_premium",
            "pet_piru_premium",
            "pet_taro_premium",
            "pet_menta_premium",
            "pet_tela_premium",
            "pet_lumi_premium",
            // Consumable coin packs.
            "coins_small",
            "coins_medium",
            "coins_large",
            "coins_mega",
        )
    }

    private data class CachedProduct(
        val details: ProductDetails,
        val offerToken: String,
        val formattedPrice: String,
    )

    private data class PurchaseProcessingResult(
        val eligible: Boolean,
        val newlyGranted: Int,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectionMutex = Mutex()
    private val productCache = mutableMapOf<String, CachedProduct>()
    private var purchaseCallbackToken = 0
    private var purchaseInProgress = false
    private var activeProductId: String? = null
    private var onPurchaseFinished: ((PurchaseResult) -> Unit)? = null

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    override suspend fun prefetch(productIds: List<String>): ProductCatalogResult {
        val whitelisted = productIds.distinct().filter(ALLOWED_PRODUCT_IDS::contains)
        if (whitelisted.isEmpty()) return ProductCatalogResult.Unavailable("No hay productos configurados")

        val setup = withTimeoutOrNull(BILLING_TIMEOUT_MS) { ensureConnected() }
            ?: return ProductCatalogResult.Unavailable("Billing no respondió a tiempo")
        if (setup.responseCode != BillingClient.BillingResponseCode.OK) {
            return ProductCatalogResult.Unavailable(setup.debugMessage.ifBlank { "Google Play no está disponible" })
        }
        whitelisted.forEach { productCache.remove(it) }

        val params = QueryProductDetailsParams.newBuilder().setProductList(
            whitelisted.map { productId ->
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(productId)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            }
        ).build()
        val result = withTimeoutOrNull(BILLING_TIMEOUT_MS) { queryProductDetails(params) }
            ?: return ProductCatalogResult.Unavailable("Google Play no respondió")
        if (result.first.responseCode != BillingClient.BillingResponseCode.OK) {
            return ProductCatalogResult.Failure(result.first.debugMessage.ifBlank { "No se pudieron cargar los productos" })
        }

        result.second.forEach { details ->
            val offer = selectOffer(details.oneTimePurchaseOfferDetailsList)
            val offerToken = offer?.offerToken
            val formattedPrice = offer?.formattedPrice
            if (offerToken.isNullOrBlank() || formattedPrice.isNullOrBlank()) {
                productCache.remove(details.productId)
                analytics.track(
                    "store_product_unavailable",
                    mapOf("product_id" to details.productId),
                )
            } else {
                productCache[details.productId] = CachedProduct(
                    details = details,
                    offerToken = offerToken,
                    formattedPrice = formattedPrice,
                )
            }
        }

        val prices = whitelisted.mapNotNull { productId ->
            productCache[productId]?.let { productId to it.formattedPrice }
        }.toMap()
        return if (prices.isEmpty()) {
            ProductCatalogResult.Unavailable("Los productos no están disponibles en Google Play")
        } else {
            ProductCatalogResult.Available(prices)
        }
    }

    override fun launchPurchase(
        activity: Activity,
        productId: String,
        onFinished: (PurchaseResult) -> Unit,
    ) {
        if (purchaseInProgress) {
            onFinished(PurchaseResult.Unavailable)
            return
        }
        val cached = productCache[productId] ?: run {
            onFinished(PurchaseResult.Unavailable)
            return
        }

        purchaseInProgress = true
        activeProductId = productId
        val callbackId = ++purchaseCallbackToken
        onPurchaseFinished = { result ->
            if (callbackId == purchaseCallbackToken) {
                purchaseInProgress = false
                activeProductId = null
                onFinished(result)
            }
        }

        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(cached.details)
                        .setOfferToken(cached.offerToken)
                        .build()
                )
            )
            .build()
        val result = billingClient.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            finishPurchase(callbackId, PurchaseResult.Failure(result.debugMessage.ifBlank { "No se pudo abrir Google Play" }))
        }
    }

    override suspend fun reconcilePurchases(): RestoreResult {
        val setup = withTimeoutOrNull(BILLING_TIMEOUT_MS) { ensureConnected() }
            ?: return RestoreResult.Unavailable
        if (setup.responseCode != BillingClient.BillingResponseCode.OK) return RestoreResult.Unavailable

        val result = withTimeoutOrNull(BILLING_TIMEOUT_MS) { queryPurchases() }
            ?: return RestoreResult.Unavailable
        if (result.first.responseCode != BillingClient.BillingResponseCode.OK) return RestoreResult.Failure(result.first.debugMessage)

        var restored = 0
        result.second.forEach { purchase ->
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                restored += processPurchase(purchase, "reconcile").newlyGranted
            }
        }
        analytics.track("store_reconcile", mapOf("granted_count" to restored.toString()))
        return if (restored > 0) RestoreResult.Restored(restored) else RestoreResult.NothingToRestore
    }

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?,
    ) {
        val callbackId = purchaseCallbackToken
        if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            return finishPurchase(callbackId, PurchaseResult.Cancelled)
        }
        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK || purchases.isNullOrEmpty()) {
            return finishPurchase(
                callbackId,
                PurchaseResult.Failure(billingResult.debugMessage.ifBlank { "La compra no se completó" })
            )
        }

        scope.launch {
            var newlyGranted = 0
            var eligible = false
            var pending = false
            purchases.forEach { purchase ->
                when (purchase.purchaseState) {
                    Purchase.PurchaseState.PURCHASED -> {
                        val result = processPurchase(purchase, "billing")
                        newlyGranted += result.newlyGranted
                        eligible = eligible || result.eligible
                    }
                    Purchase.PurchaseState.PENDING -> {
                        pending = true
                        analytics.track("store_purchase_pending", emptyMap())
                    }
                    else -> Unit
                }
            }
            if (newlyGranted > 0) {
                analytics.track(
                    "store_purchase_batch_granted",
                    mapOf("count" to newlyGranted.toString()),
                )
            }
            val matchesActiveRequest = purchases.any { purchase ->
                activeProductId?.let(purchase.products::contains) == true
            }
            if (matchesActiveRequest) {
                finishPurchase(
                    callbackId,
                    when {
                        pending -> PurchaseResult.Pending
                        eligible -> PurchaseResult.Success
                        else -> PurchaseResult.Failure("La compra no contiene un producto válido")
                    }
                )
            }
        }
    }

    private suspend fun processPurchase(
        purchase: Purchase,
        source: String,
    ): PurchaseProcessingResult {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) {
            return PurchaseProcessingResult(eligible = false, newlyGranted = 0)
        }

        var eligible = false
        var newlyGranted = 0
        var hasConsumable = false
        var hasNonConsumable = false

        purchase.products.forEach { productId ->
            if (!isWhitelisted(productId)) return@forEach
            eligible = true
            val isConsumable = CoinProduct.CATALOG.any { it.productId == productId }
            if (isConsumable) hasConsumable = true else hasNonConsumable = true

            if (
                repository.grantPlayPurchaseOnce(
                    purchaseToken = purchase.purchaseToken,
                    productId = productId,
                    quantity = purchase.quantity,
                    purchaseTime = purchase.purchaseTime,
                    source = source,
                )
            ) {
                newlyGranted++
                analytics.track(
                    "store_purchase_granted",
                    mapOf("product_id" to productId, "source" to source),
                )
            }
            repository.markPlayPurchaseSeen(purchase.purchaseToken, productId)
        }

        if (!eligible) return PurchaseProcessingResult(eligible = false, newlyGranted = 0)

        if (hasConsumable) {
            val result = withTimeoutOrNull(BILLING_TIMEOUT_MS) { consumeSafely(purchase) }
                ?: errorResult("Consume timed out")
            val consumed = result.responseCode == BillingClient.BillingResponseCode.OK ||
                result.responseCode == BillingClient.BillingResponseCode.ITEM_NOT_OWNED
            analytics.track("store_consume", mapOf("code" to result.responseCode.toString()))
            if (consumed) repository.markPlayPurchaseConsumed(purchase.purchaseToken)
        }

        if (hasNonConsumable) {
            purchase.products.filterNot { productId ->
                CoinProduct.CATALOG.any { it.productId == productId }
            }.filter(::isWhitelisted).forEach { productId ->
                if (purchase.isAcknowledged) {
                    repository.markPlayPurchaseAcknowledged(purchase.purchaseToken, productId)
                } else {
                    val result = withTimeoutOrNull(BILLING_TIMEOUT_MS) {
                        acknowledgeSafely(purchase)
                    } ?: errorResult("Acknowledge timed out")
                    analytics.track(
                        "store_acknowledge",
                        mapOf("code" to result.responseCode.toString()),
                    )
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        repository.markPlayPurchaseAcknowledged(purchase.purchaseToken, productId)
                    }
                }
            }
        }

        return PurchaseProcessingResult(eligible = true, newlyGranted = newlyGranted)
    }

    private suspend fun consumeSafely(purchase: Purchase): BillingResult =
        suspendCancellableCoroutine { cont ->
            billingClient.consumeAsync(
                ConsumeParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
            ) { billingResult, _ ->
                if (cont.isActive) cont.resume(billingResult)
            }
        }

    private suspend fun acknowledgeSafely(purchase: Purchase): BillingResult =
        suspendCancellableCoroutine { cont ->
            billingClient.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
            ) { billingResult ->
                if (cont.isActive) cont.resume(billingResult)
            }
        }

    private suspend fun ensureConnected(): BillingResult {
        return connectionMutex.withLock {
            if (billingClient.isReady) return@withLock okResult()
            suspendCancellableCoroutine { cont ->
                billingClient.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(billingResult: BillingResult) {
                        if (cont.isActive) cont.resume(billingResult)
                    }

                    override fun onBillingServiceDisconnected() {
                        analytics.track("billing_disconnected")
                        if (cont.isActive) cont.resume(errorResult("Billing service disconnected"))
                    }
                }
                )
            }
        }
    }

    private suspend fun queryProductDetails(
        params: QueryProductDetailsParams,
    ): Pair<BillingResult, List<ProductDetails>> =
        suspendCancellableCoroutine { cont ->
            billingClient.queryProductDetailsAsync(params) { billingResult, result: QueryProductDetailsResult ->
                if (cont.isActive) cont.resume(billingResult to result.productDetailsList)
            }
        }

    private suspend fun queryPurchases(): Pair<BillingResult, List<Purchase>> =
        suspendCancellableCoroutine { cont ->
            billingClient.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            ) { billingResult, purchases ->
                if (cont.isActive) cont.resume(billingResult to purchases)
            }
        }

    /** Prefer the single base offer and fail closed when Play returns ambiguity. */
    private fun selectOffer(
        offers: List<ProductDetails.OneTimePurchaseOfferDetails>?,
    ): ProductDetails.OneTimePurchaseOfferDetails? {
        val availableOffers = offers.orEmpty()
        val baseOffers = availableOffers.filter { it.offerId == null && it.purchaseOptionId == null }
        return when {
            baseOffers.size == 1 -> baseOffers.single()
            availableOffers.size == 1 -> availableOffers.single()
            else -> null
        }
    }

    private fun finishPurchase(token: Int, result: PurchaseResult) {
        if (token != purchaseCallbackToken) return
        onPurchaseFinished?.invoke(result)
        onPurchaseFinished = null
    }

    private fun isWhitelisted(productId: String): Boolean = productId in ALLOWED_PRODUCT_IDS

    private fun okResult(): BillingResult = BillingResult.newBuilder()
        .setResponseCode(BillingClient.BillingResponseCode.OK)
        .setDebugMessage("already_connected")
        .build()

    private fun errorResult(message: String): BillingResult = BillingResult.newBuilder()
        .setResponseCode(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED)
        .setDebugMessage(message)
        .build()

    fun close() {
        billingClient.endConnection()
        scope.cancel()
    }
}
