package com.pixelpals.app.data.catalog

import androidx.annotation.StringRes
import com.pixelpals.app.R

/**
 * Producto de monedas (IAP real).
 *
 * @param productId Identificador del SKU en Play Console (e.g. "coins_small").
 * @param displayNameResId Recurso localizado con el nombre legible del pack.
 * @param subtitleResId Recurso localizado con la cantidad de monedas.
 * @param coinAmount Cantidad de monedas que el pack otorga.
 * @param bestValueFlag Si es el pack con mejor ratio monedas/precio.
 */
data class CoinProduct(
    val productId: String,
    @param:StringRes val displayNameResId: Int,
    @param:StringRes val subtitleResId: Int,
    val coinAmount: Int,
    val bestValueFlag: Boolean = false,
) {
    companion object {
        /** Catálogo hardcoded de packs de monedas (los SKUs reales van en Play Console). */
        val CATALOG: List<CoinProduct> = listOf(
            CoinProduct(
                productId = "coins_small",
                displayNameResId = R.string.coins_pack_small_title,
                subtitleResId = R.string.coins_pack_small_subtitle,
                coinAmount = 100,
            ),
            CoinProduct(
                productId = "coins_medium",
                displayNameResId = R.string.coins_pack_medium_title,
                subtitleResId = R.string.coins_pack_medium_subtitle,
                coinAmount = 350,
            ),
            CoinProduct(
                productId = "coins_large",
                displayNameResId = R.string.coins_pack_large_title,
                subtitleResId = R.string.coins_pack_large_subtitle,
                coinAmount = 1000,
                bestValueFlag = true,
            ),
            CoinProduct(
                productId = "coins_mega",
                displayNameResId = R.string.coins_pack_mega_title,
                subtitleResId = R.string.coins_pack_mega_subtitle,
                coinAmount = 2500,
            ),
        )
    }
}

/**
 * Pack premium que agrupa varios accesorios temáticos + monedas bonus.
 */
