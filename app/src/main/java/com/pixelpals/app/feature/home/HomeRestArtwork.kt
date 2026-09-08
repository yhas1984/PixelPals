package com.pixelpals.app.feature.home

import android.content.Context
import android.graphics.*
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.feature.overlay.behavior.PetAtlasSpec
import org.json.JSONObject

/** Uses reviewed care poses only in builds that package the opt-in care artwork. */
internal class HomeRestArtwork(private val sprites: HomeSpriteFrames, private val sequence: List<Int>, private val wingWrap: Boolean) {
    private val body: RectF = RectF()
    private val wings = com.pixelpals.app.feature.care.ImpWingPainter()
    fun draw(canvas: Canvas, paint: Paint, target: RectF, seconds: Float, waking: Boolean, reduced: Boolean) {
        val step: Int = if (reduced) sequence.lastIndex else (seconds / .4f).toInt().coerceIn(0, sequence.lastIndex)
        val frame: Int = sequence[if (waking) sequence.lastIndex - step else step]
        if (!wingWrap) {
            sprites.draw(canvas, paint, target, frame)
            return
        }
        sprites.bounds(target, frame, body)
        val center = com.pixelpals.app.core.care.scene.CarePoint(body.centerX(), body.top + body.height() * .65f)
        val progress: Float = if (reduced) 1f else (seconds / .8f).coerceIn(0f, 1f)
        val fold: Float = if (waking) 1f - progress else progress
        wings.draw(canvas, center, body.height(), fold, false)
        sprites.draw(canvas, paint, target, frame)
        wings.draw(canvas, center, body.height(), fold, true)
    }
    companion object {
        fun load(context: Context, pet: PetType): HomeRestArtwork? {
            val sequence: List<Int> = when (pet) {
                PetType.CORGI -> listOf(16, 17, 18, 19)
                PetType.PATITO, PetType.BLOOP, PetType.MOKI, PetType.MENTA -> listOf(16, 17, 18)
                PetType.JELLY -> listOf(16, 17, 17)
                PetType.DIABLILLO -> listOf(3, 17, 9)
                else -> return null
            }
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
            }, anchor = if (pet == PetType.CORGI) PointF(width / 2f, height * .93f) else null,
                cellScale = if (pet == PetType.CORGI) com.pixelpals.app.core.motion.CorgiArtworkScale.CARE_CELL else null),
                sequence, pet == PetType.DIABLILLO)
        }
    }
}
