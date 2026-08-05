package com.pixelpals.app.data.catalog

import android.content.Context
import org.json.JSONObject
import java.io.IOException

/**
 * Un outfit es un set de frames del pet con el accesorio PINTADO
 * (reemplaza el sprite completo, no se combina con accesorios).
 *
 * Cargado desde `assets/outfits/<petId>_<id>/outfit.json`.
 */
data class PetOutfit(
    val id: String,
    val petId: String,
    val displayName: String,
    val description: String,
    val productId: String,
    val priceCoins: Int,
    val frames: List<String>,
) {
    /** Ruta absoluta del asset del frame i. */
    fun frameAssetPath(index: Int): String? = frames.getOrNull(index)?.let { "outfits/$id/$it" }
}

object OutfitCatalog {

    @Volatile
    private var cache: Map<String, PetOutfit>? = null

    fun all(context: Context): Map<String, PetOutfit> {
        cache?.let { return it }
        synchronized(this) {
            cache?.let { return it }
            cache = load(context.applicationContext)
            return cache!!
        }
    }

    fun forPet(context: Context, petId: String): List<PetOutfit> =
        all(context).values.filter { it.petId == petId }

    fun findById(context: Context, id: String): PetOutfit? = all(context)[id]

    fun invalidate() {
        synchronized(this) { cache = null }
    }

    private fun load(context: Context): Map<String, PetOutfit> {
        val dir = context.assets.list("outfits") ?: return emptyMap()
        val out = LinkedHashMap<String, PetOutfit>()
        for (sub in dir) {
            val jsonPath = "outfits/$sub/outfit.json"
            val raw = try {
                context.assets.open(jsonPath).bufferedReader().use { it.readText() }
            } catch (e: IOException) {
                android.util.Log.w("OutfitCatalog", "No outfit.json en $sub")
                continue
            }
            try {
                val json = JSONObject(raw)
                val framesArr = json.getJSONArray("frames")
                out[json.getString("id")] = PetOutfit(
                    id = json.getString("id"),
                    petId = json.getString("petId"),
                    displayName = json.getString("displayName"),
                    description = json.getString("description"),
                    productId = json.optString("productId", ""),
                    priceCoins = json.optInt("priceCoins", 0),
                    frames = (0 until framesArr.length()).map { framesArr.getString(it) },
                )
            } catch (e: Exception) {
                android.util.Log.w("OutfitCatalog", "Outfit inválido en $sub", e)
            }
        }
        return out
    }
}
