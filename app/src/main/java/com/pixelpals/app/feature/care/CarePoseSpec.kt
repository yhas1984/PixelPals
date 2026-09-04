package com.pixelpals.app.feature.care

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.pixelpals.app.core.care.scene.CarePoint
import com.pixelpals.app.core.care.scene.CareSceneAction
import com.pixelpals.app.core.care.scene.CareSceneTiming
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.feature.overlay.behavior.PetAtlasSpec
import com.pixelpals.app.feature.overlay.behavior.PetClipSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class CarePoseAnchors(val mouth: CarePoint, val head: CarePoint, val body: CarePoint, val ground: CarePoint)

data class CarePoseSpec(
    val atlas: PetAtlasSpec,
    val timings: Map<CareSceneAction, CareSceneTiming>,
    val anchors: List<CarePoseAnchors>,
) {
    fun getFrame(action: CareSceneAction, elapsedMs: Long): Int {
        val clip: PetClipSpec = requireNotNull(atlas.clip(action.name.lowercase()))
        return clip.frames[(elapsedMs / clip.frameDurationMs).toInt().coerceIn(0, clip.frames.lastIndex)]
    }

    companion object {
        fun parse(json: JSONObject): CarePoseSpec {
            val atlas: PetAtlasSpec = PetAtlasSpec.fromJson(json)
            val actions: JSONObject = json.getJSONObject("careActions")
            val timings: Map<CareSceneAction, CareSceneTiming> = CareSceneAction.entries.associateWith { action ->
                val metadata: JSONObject = actions.getJSONObject(action.name.lowercase())
                val clip: PetClipSpec = requireNotNull(atlas.clip(action.name.lowercase()))
                CareSceneTiming(clip.frames.size * clip.frameDurationMs.toLong(), metadata.getLong("completionMs"))
            }
            val anchors: List<CarePoseAnchors> = (0 until atlas.frameCount).map { index ->
                val item: JSONObject = json.getJSONArray("anchors").getJSONObject(index)
                fun point(name: String): CarePoint {
                    val value: org.json.JSONArray = item.getJSONArray(name)
                    val x: Float = value.getDouble(0).toFloat()
                    val y: Float = value.getDouble(1).toFloat()
                    require(x.isFinite() && y.isFinite() && x in 0f..1f && y in 0f..1f)
                    return CarePoint(x, y)
                }
                CarePoseAnchors(point("mouth"), point("head"), point("body"), point("ground"))
            }
            require(atlas.frameWidth.toLong() * atlas.columns * atlas.frameHeight * atlas.rows * 4 <= MAX_BYTES)
            return CarePoseSpec(atlas, timings, anchors)
        }
        const val MAX_BYTES: Long = 16L * 1024L * 1024L
    }
}

/** Owned by one visible stage; never retained in the locomotion/global bitmap caches. */
data class CarePosePack(val spec: CarePoseSpec, val bitmap: Bitmap)

object CarePoseLoader {
    fun isAvailable(assets: AssetManager, pet: PetType): Boolean {
        val files: List<String> = assets.list("pets/${pet.name.lowercase()}")?.toList().orEmpty()
        return "care_v1.json" in files && "care_v1.png" in files
    }

    suspend fun load(assets: AssetManager, pet: PetType): CarePosePack = withContext(Dispatchers.IO) {
        val spec: CarePoseSpec = assets.open("pets/${pet.name.lowercase()}/care_v1.json")
            .bufferedReader().use { CarePoseSpec.parse(JSONObject(it.readText())) }
        require(spec.atlas.petId == pet.name.lowercase())
        val options: BitmapFactory.Options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        assets.open(spec.atlas.atlasPath).use { BitmapFactory.decodeStream(it, null, options) }
        require(options.outWidth == spec.atlas.frameWidth * spec.atlas.columns)
        require(options.outHeight == spec.atlas.frameHeight * spec.atlas.rows)
        val bitmap: Bitmap = requireNotNull(assets.open(spec.atlas.atlasPath).use { BitmapFactory.decodeStream(it) })
        require(bitmap.hasAlpha()) { "Care atlas must contain real transparency" }
        require(bitmap.allocationByteCount <= CarePoseSpec.MAX_BYTES)
        CarePosePack(spec, bitmap)
    }
}
