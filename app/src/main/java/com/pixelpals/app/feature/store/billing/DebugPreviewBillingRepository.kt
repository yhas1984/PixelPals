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
        // Premium packs
        "pack_celestial" to "€2.99",
        "pack_demonic" to "€2.99",
        "pack_adventure" to "€2.99",
        // Accessories (V1.5)
        "acc_halo_glow" to "€0.99",
        "acc_royal_crown" to "€0.99",
        "acc_star_trail" to "€0.99",
        "acc_cozy_scarf" to "€0.99",
        "acc_party_spark" to "€0.99",
        "acc_celestial_wings" to "€1.99",
        "acc_demonic_wings" to "€1.99",
        "acc_duck_jetpack" to "€1.99",
        "acc_round_glasses" to "€0.99",
        "acc_pilot_glasses" to "€0.99",
        "acc_magic_hat" to "€0.99",
        "acc_tiara" to "€0.99",
        "acc_bowtie" to "€0.99",
        "acc_rainbow_scarf" to "€0.99",
        "acc_devil_horns" to "€0.99",
        "acc_duck_inner_tube" to "€1.99",
        "acc_heart_glasses" to "€0.99",
        "acc_magic_wand" to "€1.49",
        "acc_viking_helmet" to "€0.99",
        "acc_ninja_mask" to "€1.49",
        "acc_shield_back" to "€0.99",
        "acc_astronaut_helmet" to "€1.49",
        "acc_alien_antennas" to "€0.99",
        "acc_ufo_jetpack" to "€1.99",
        "acc_pirate_hat" to "€0.99",
        "acc_eye_patch" to "€0.99",
        "acc_treasure_chest" to "€0.99",
        "acc_chef_hat" to "€0.99",
        "acc_unicorn_horn" to "€1.49",
        "acc_lightning_bolt" to "€1.49",
        "acc_robot_antenna" to "€0.99",
        "acc_crown_of_thorns" to "€0.99",
        "acc_ice_crown" to "€1.49",
        "acc_fire_cape" to "€1.49",
        "acc_monocle" to "€0.99",
        "acc_mustache" to "€0.99",
        "acc_sword" to "€1.49",
        "acc_shield_cool" to "€1.49",
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
