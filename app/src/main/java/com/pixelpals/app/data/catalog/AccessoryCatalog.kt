package com.pixelpals.app.data.catalog

import android.content.Context
import org.json.JSONObject
import java.io.IOException

/**
 * Carga y expone el catálogo de accesorios desde `assets/accessories_catalog.json`.
 *
 * Singleton thread-safe — el catálogo es estático durante la vida de la app.
 */
object AccessoryCatalog {

    private const val CATALOG_ASSET_PATH = "accessories_catalog.json"

    @Volatile
    private var cache: List<AccessoryCatalogItem>? = null

    fun all(context: Context): List<AccessoryCatalogItem> {
        cache?.let { return it }
        synchronized(this) {
            cache?.let { return it }
            cache = loadFromAssets(context.applicationContext)
            return cache!!
        }
    }

    fun forPet(context: Context, petId: String): List<AccessoryCatalogItem> {
        return all(context).filter { petId in it.supportedPetIds }
    }

    fun findById(context: Context, id: String): AccessoryCatalogItem? {
        return all(context).firstOrNull { it.id == id }
    }

    /** Para tests: invalidar el cache. */
    fun invalidate() {
        synchronized(this) { cache = null }
    }

    private fun loadFromAssets(context: Context): List<AccessoryCatalogItem> {
        val raw = try {
            context.assets.open(CATALOG_ASSET_PATH).bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            android.util.Log.e("AccessoryCatalog", "Cannot read $CATALOG_ASSET_PATH", e)
            return emptyList()
        }
        return try {
            val root = JSONObject(raw)
            val arr = root.getJSONArray("accessories")
            val out = ArrayList<AccessoryCatalogItem>(arr.length())
            for (i in 0 until arr.length()) {
                out.add(parse(arr.getJSONObject(i)))
            }
            out
        } catch (e: Exception) {
            android.util.Log.e("AccessoryCatalog", "Cannot parse catalog", e)
            emptyList()
        }
    }

    private fun parse(json: JSONObject): AccessoryCatalogItem {
        val id = json.getString("id")
        val productId = json.optString("productId", "")
        val displayName = json.getString("displayName")
        val description = json.getString("description")
        val emoji = json.optString("emoji", "✨")
        val slot = AccessorySlot.valueOf(json.getString("slot"))
        val visual = parseVisual(json.getJSONObject("visual"))
        val modifiers = parseModifiers(json.optJSONArray("modifiers"))
        val isPremium = json.optBoolean("isPremium", false)
        val packLabel = json.optString("packLabel", "")
        val supportedPetIds = json.optJSONArray("supportedPetIds")?.let { arr ->
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        } ?: emptySet()
        val coinPrice = if (json.has("coinPrice") && !json.isNull("coinPrice")) json.getInt("coinPrice") else null
        val bondRequired = json.optInt("bondRequired", 0)
        val tags = json.optJSONArray("tags")?.let { arr ->
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        } ?: emptySet()

        return AccessoryCatalogItem(
            id = id,
            productId = productId,
            displayName = displayName,
            description = description,
            emoji = emoji,
            slot = slot,
            visual = visual,
            modifiers = modifiers,
            isPremium = isPremium,
            packLabel = packLabel,
            supportedPetIds = supportedPetIds,
            coinPrice = coinPrice,
            bondRequired = bondRequired,
            tags = tags,
        )
    }

    private fun parseVisual(json: JSONObject): AccessoryVisual {
        val type = json.optString("type", "emoji")
        return when (type) {
            "emoji" -> AccessoryVisual.EmojiOverlay(
                offsetXRatio = json.optDouble("offsetX", 0.0).toFloat(),
                offsetYRatio = json.optDouble("offsetY", 0.0).toFloat(),
                scale = json.optDouble("scale", 0.24).toFloat(),
            )
            "sprite" -> AccessoryVisual.SpriteOverlay(
                drawableResId = json.optInt("drawable", 0),
                offsetXRatio = json.optDouble("offsetX", 0.0).toFloat(),
                offsetYRatio = json.optDouble("offsetY", 0.0).toFloat(),
                scale = json.optDouble("scale", 0.3).toFloat(),
                frames = emptyList(),
            )
            else -> AccessoryVisual.EmojiOverlay(0f, 0f, 0.24f)
        }
    }

    private fun parseModifiers(arr: org.json.JSONArray?): List<PetModifier> {
        if (arr == null || arr.length() == 0) return emptyList()
        val out = ArrayList<PetModifier>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val type = obj.optString("type")
            when (type) {
                "speedBoost" -> out.add(
                    PetModifier.SpeedBoost(
                        multiplier = obj.optDouble("multiplier", 1.0).toFloat()
                    )
                )
                "trailParticles" -> out.add(
                    PetModifier.TrailParticles(
                        type = runCatching { ParticleType.valueOf(obj.optString("particle", "SPARKLES")) }
                            .getOrDefault(ParticleType.SPARKLES),
                        density = obj.optInt("density", 4),
                    )
                )
                "soundEffect" -> out.add(
                    PetModifier.SoundEffect(obj.optInt("sound", 0))
                )
                "animationOverride" -> out.add(
                    PetModifier.AnimationOverride(obj.optString("modeName", ""))
                )
            }
        }
        return out
    }
}
