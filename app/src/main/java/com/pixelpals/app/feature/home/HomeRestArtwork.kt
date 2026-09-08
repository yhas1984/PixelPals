package com.pixelpals.app.feature.home

import android.content.Context
import android.graphics.*
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.feature.overlay.behavior.PetAtlasSpec
import org.json.JSONObject

/** Uses reviewed care poses only in builds that package the opt-in care artwork. */
internal class HomeRestArtwork(private val sprites: HomeSpriteFrames, private val finalFrame: Int) {
    fun draw(canvas: Canvas, paint: Paint, target: RectF, seconds: Float, waking: Boolean, reduced: Boolean) {
        val step: Int = if (reduced) 2 else (seconds / .4f).toInt().coerceIn(0, 2)
        sprites.draw(canvas, paint, target, if (waking) (finalFrame - step).coerceAtLeast(16) else (16 + step).coerceAtMost(finalFrame))
    }
    companion object {
        fun load(context: Context, pet: PetType): HomeRestArtwork? {
            if (pet != PetType.PATITO && pet != PetType.JELLY) return null
            val folder: String = "pets/${pet.name.lowercase()}"
            if ("care_v1.json" !in context.assets.list(folder).orEmpty()) return null
            val spec: PetAtlasSpec = context.assets.open("$folder/care_v1.json").bufferedReader().use {
                PetAtlasSpec.fromJson(JSONObject(it.readText()))
            }
            val bitmap: Bitmap = context.assets.open(spec.atlasPath).use {
                requireNotNull(BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 1 }))
            }
            val width: Int = bitmap.width / spec.columns
            val height: Int = bitmap.height / spec.rows
            return HomeRestArtwork(HomeSpriteFrames((0 until spec.frameCount).map { index ->
                bitmap to Rect(index % spec.columns * width, index / spec.columns * height,
                    (index % spec.columns + 1) * width, (index / spec.columns + 1) * height)
            }), if (pet == PetType.JELLY) 17 else 18)
        }
    }
}
