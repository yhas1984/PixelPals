package com.pixelpals.app.data.catalog

import android.content.Context
import org.json.JSONObject
import java.io.IOException

/** Carga el catálogo de cosméticos desde assets/cosmetics_catalog.json. */
object CosmeticCatalog {

    private const val ASSET = "cosmetics_catalog.json"

    @Volatile
    private var cache: List<Cosmetic>? = null

    fun all(context: Context): List<Cosmetic> {
        cache?.let { return it }
        synchronized(this) {
            cache?.let { return it }
            cache = load(context.applicationContext)
            return cache!!
        }
    }

    fun findById(context: Context, id: String): Cosmetic? = all(context).firstOrNull { it.id == id }

    fun invalidate() {
        synchronized(this) { cache = null }
    }

    private fun load(context: Context): List<Cosmetic> {
        val raw = try {
            context.assets.open(ASSET).bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            android.util.Log.w("CosmeticCatalog", "No se pudo leer $ASSET")
            return emptyList()
        }
        return try {
            val root = JSONObject(raw)
            val arr = root.getJSONArray("cosmetics")
            val out = ArrayList<Cosmetic>(arr.length())
            for (i in 0 until arr.length()) {
                val json = arr.getJSONObject(i)
                val effect = CosmeticEffect.fromJson(json.getJSONObject("effect")) ?: continue
                out.add(
                    Cosmetic(
                        id = json.getString("id"),
                        displayName = json.getString("displayName"),
                        description = json.getString("description"),
                        productId = json.optString("productId", ""),
                        effect = effect,
                        coinPrice = if (json.has("coinPrice") && !json.isNull("coinPrice")) json.getInt("coinPrice") else null,
                    )
                )
            }
            out
        } catch (e: Exception) {
            android.util.Log.w("CosmeticCatalog", "Catálogo inválido", e)
            emptyList()
        }
    }
}
