package com.pixelpals.app.data.catalog

/**
 * Producto de monedas (IAP real).
 *
 * @param productId Identificador del SKU en Play Console (e.g. "coins_small").
 * @param displayName Nombre legible del pack.
 * @param subtitle Subtítulo con la cantidad de monedas.
 * @param coinAmount Cantidad de monedas que el pack otorga.
 * @param bestValueFlag Si es el pack con mejor ratio monedas/precio.
 */
data class CoinProduct(
    val productId: String,
    val displayName: String,
    val subtitle: String,
    val coinAmount: Int,
    val bestValueFlag: Boolean = false,
) {
    companion object {
        /** Catálogo hardcoded de packs de monedas (los SKUs reales van en Play Console). */
        val CATALOG: List<CoinProduct> = listOf(
            CoinProduct(
                productId = "coins_small",
                displayName = "Starter Pack",
                subtitle = "100 coins",
                coinAmount = 100,
            ),
            CoinProduct(
                productId = "coins_medium",
                displayName = "Sweet Pack",
                subtitle = "350 coins",
                coinAmount = 350,
            ),
            CoinProduct(
                productId = "coins_large",
                displayName = "Mega Pack",
                subtitle = "1000 coins",
                coinAmount = 1000,
                bestValueFlag = true,
            ),
            CoinProduct(
                productId = "coins_mega",
                displayName = "Giga Pack",
                subtitle = "2500 coins +5% bonus",
                coinAmount = 2500,
            ),
        )
    }
}

/**
 * Pack premium que agrupa varios accesorios temáticos + monedas bonus.
 */
data class PremiumPack(
    val productId: String,
    val displayName: String,
    val subtitle: String,
    val accessoryIds: List<String>,
    val bonusCoins: Int,
) {
    companion object {
        val CATALOG: List<PremiumPack> = listOf(
            PremiumPack(
                productId = "pack_celestial",
                displayName = "Celestial Pack",
                subtitle = "Halo + Celestial Wings + 100 coins",
                accessoryIds = listOf("halo_glow", "celestial_wings"),
                bonusCoins = 100,
            ),
            PremiumPack(
                productId = "pack_demonic",
                displayName = "Demonic Pack",
                subtitle = "Crown + Demonic Wings + 100 coins",
                accessoryIds = listOf("royal_crown", "demonic_wings"),
                bonusCoins = 100,
            ),
            PremiumPack(
                productId = "pack_adventure",
                displayName = "Adventure Pack",
                subtitle = "Jetpack + Pilot Glasses + 100 coins",
                accessoryIds = listOf("duck_jetpack", "pilot_glasses"),
                bonusCoins = 100,
            ),
        )

        fun findByProductId(productId: String): PremiumPack? =
            CATALOG.firstOrNull { it.productId == productId }
    }
}
