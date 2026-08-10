package com.pixelpals.app.feature.store.billing

import android.app.Activity
import com.pixelpals.app.core.analytics.AnalyticsTracker
import com.pixelpals.app.data.repository.PixelPalsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Billing para debug que muestra precios formateados realistas
 * (no "debug_preview") y otorga el producto como si fuera una compra real.
 *
 * En release se usa [GooglePlayBillingRepository] que conecta con Play Console.
 */
class DebugPreviewBillingRepository(
    private val repository: PixelPalsRepository,
    private val analytics: AnalyticsTracker
) : BillingRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Precio fake por SKU — sólo usado para que la UI se vea realista. */
    private val fakePrices = mapOf(
        // Coin packs
        "coins_small" to "€0.99",
        "coins_medium" to "€1.99",
        "coins_large" to "€4.99",
        "coins_mega" to "€9.99",
        // Premium pets
        "pet_angel_premium" to "€2.99",
        "pet_diablillo_premium" to "€2.99",
        "pet_yuki_premium" to "€3.99",
        "pet_piru_premium" to "€3.99",
        "pet_taro_premium" to "€3.49",
        "pet_menta_premium" to "€3.49",
        "pet_tela_premium" to "€3.99",
    )

    override suspend fun prefetch(productIds: List<String>): Map<String, String> {
        return productIds.distinct().associateWith { id -> fakePrices[id] ?: "€0.99" }
    }

    override fun launchPurchase(activity: Activity, productId: String, onFinished: (Boolean) -> Unit) {
        scope.launch {
            // Simula latencia de Play (300-600ms) para que el botón se vea en "loading".
            delay((300L..600L).random())
            repository.grantOwnedProduct(productId, source = "debug_preview")
            analytics.track("store_purchase_granted", mapOf("product_id" to productId, "source" to "debug_preview"))
            withContext(Dispatchers.Main) { onFinished(true) }
        }
    }

    override suspend fun restorePurchases(): Int = withContext(Dispatchers.IO) { 0 }
}
