package com.pixelpals.app.feature.home

import android.content.Context
import android.graphics.*
import com.pixelpals.app.R
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.feature.overlay.behavior.PetAtlasSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Reuses the shipped species locomotion rather than playing a care gesture while walking. */
class HomeLocomotion private constructor(frames: List<Pair<Bitmap, Rect>>, private val duration: Int, private val nativeFacesLeft: Boolean = false) {
    private val sprites = HomeSpriteFrames(frames)
    private val count = frames.size
    fun draw(canvas: Canvas, paint: Paint, target: RectF, elapsed: Long): Unit {
        canvas.save()
        if (nativeFacesLeft) canvas.scale(-1f, 1f, target.centerX(), target.bottom)
        sprites.draw(canvas, paint, target, ((elapsed / duration) % count).toInt())
        canvas.restore()
    }
    companion object {
        suspend fun load(context: Context, pet: PetType): HomeLocomotion = withContext(Dispatchers.IO) {
            val id = pet.name.lowercase()
            val folder = "pets/$id"
            val filename = context.assets.list(folder).orEmpty().filter { it.endsWith(".json") && !it.startsWith("care") }
                .sortedWith(compareByDescending<String> { it.contains("motion_v2") }.thenByDescending { it }).firstOrNull()
            if (filename != null) {
                val spec = context.assets.open("$folder/$filename").bufferedReader().use { PetAtlasSpec.fromJson(JSONObject(it.readText())) }
                val clip = listOf("walk", "crawl_loop", "right", "glide", "hover").firstNotNullOf { spec.clip(it) }
                val bitmap = context.assets.open(spec.atlasPath).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 2 }) }!!
                val w = bitmap.width / spec.columns
                val h = bitmap.height / spec.rows
                HomeLocomotion(clip.frames.map { bitmap to Rect(it % spec.columns * w, it / spec.columns * h, (it % spec.columns + 1) * w, (it / spec.columns + 1) * h) }, clip.frameDurationMs.coerceAtLeast(60), nativeFacesLeft = pet == PetType.GINGER)
            } else {
                val ids = when (pet) {
                    PetType.CORGI -> listOf(R.drawable.corgi_10, R.drawable.corgi_11, R.drawable.corgi_12, R.drawable.corgi_13)
                    PetType.PATITO -> listOf(R.drawable.patito_4, R.drawable.patito_5)
                    PetType.DIABLILLO -> listOf(R.drawable.diablillo_2, R.drawable.diablillo_3)
                    else -> listOf(pet.spriteResId)
                }
                HomeLocomotion(ids.map { res ->
                    val bitmap = BitmapFactory.decodeResource(context.resources, res, BitmapFactory.Options().apply { inScaled = false })!!
                    bitmap to Rect(0, 0, bitmap.width, bitmap.height)
                }, 150)
            }
        }
    }
}
