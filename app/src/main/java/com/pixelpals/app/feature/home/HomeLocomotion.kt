package com.pixelpals.app.feature.home

import android.content.Context
import android.graphics.*
import com.pixelpals.app.R
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.feature.overlay.behavior.PetAtlasSpec
import com.pixelpals.app.feature.overlay.behavior.PetClipSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** A coherent actor bank: idle, gait, turn, play and sleep share the same source artwork and scale. */
class HomeLocomotion private constructor(
    frames: List<Pair<Bitmap, Rect>>,
    private val clips: Map<String, PetClipSpec>,
    private val nativeFacesLeft: Boolean,
    normalizeClips: Boolean = false,
) {
    private val sprites: HomeSpriteFrames = HomeSpriteFrames(frames)
    private val clipSprites: Map<String, HomeSpriteFrames> = if (normalizeClips)
        clips.mapValues { (_, clip) -> HomeSpriteFrames(clip.frames.map { frames[it] }) } else emptyMap()
    fun draw(canvas: Canvas, paint: Paint, target: RectF, motion: CompanionMotion, reduced: Boolean): Unit {
        val name: String = when {
            reduced -> if (motion.activity == CompanionActivity.REST) "sleep" else "idle"
            motion.isTurning && motion.speed <= .1f -> "turn"
            motion.isPreparing -> "idle"
            motion.isApproaching -> "walk"
            motion.activity == CompanionActivity.REST -> "sleep"
            motion.activity == CompanionActivity.PLAY -> "play"
            else -> "idle"
        }
        val clip: PetClipSpec = clips[name] ?: requireNotNull(clips["idle"])
        val phase: Long = when {
            reduced -> if (name == "sleep") clip.frames.lastIndex * clip.frameDurationMs.toLong() else 0L
            name == "walk" -> (motion.distanceTravelled / 105f * clip.frames.size * clip.frameDurationMs).toLong()
            name == "turn" -> (motion.turnElapsed / CompanionMotion.TURN_SECONDS * clip.frames.size * clip.frameDurationMs).toLong()
            else -> (motion.elapsed * 1000).toLong()
        }
        val index: Int = (phase / clip.frameDurationMs).toInt().let { if (clip.loop) it % clip.frames.size else it.coerceAtMost(clip.frames.lastIndex) }
        canvas.save()
        if (nativeFacesLeft) canvas.scale(-1f, 1f, target.centerX(), target.bottom)
        val local: HomeSpriteFrames? = clipSprites[name] ?: clipSprites["idle"]
        if (local != null) local.draw(canvas, paint, target, index) else sprites.draw(canvas, paint, target, clip.frames[index])
        canvas.restore()
    }
    companion object {
        suspend fun load(context: Context, pet: PetType): HomeLocomotion = withContext(Dispatchers.IO) {
            val folder: String = "pets/${pet.name.lowercase()}"
            val filename: String? = context.assets.list(folder).orEmpty().filter { it.endsWith(".json") && !it.startsWith("care") }
                .sortedWith(compareByDescending<String> { it.contains("motion_v2") }.thenByDescending { it }).firstOrNull()
            if (filename == null) return@withContext loadLegacy(context, pet)
            val spec: PetAtlasSpec = context.assets.open("$folder/$filename").bufferedReader().use { PetAtlasSpec.fromJson(JSONObject(it.readText())) }
            val bitmap: Bitmap = context.assets.open(spec.atlasPath).use { requireNotNull(BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 2 })) }
            val width: Int = bitmap.width / spec.columns
            val height: Int = bitmap.height / spec.rows
            fun choose(vararg ids: String): PetClipSpec = ids.firstNotNullOfOrNull { spec.clip(it) } ?: spec.clips.first()
            val idle: PetClipSpec = choose("idle", "sit", "perch_loop", "hover")
            val selected: Map<String, PetClipSpec> = mapOf(
                "idle" to idle,
                "walk" to choose("walk", "crawl_loop", "right", "glide", "hover"),
                "turn" to (spec.clip("turn")?.copy(loop = false) ?: idle),
                "play" to choose("playful_delight", "happy", "front_social", "groom", "grace", "tongue_strike", "idle"),
                "sleep" to (if (pet == PetType.MENTA) choose("blink", "idle") else choose("sleep", "prayer", "perch_loop", "idle")).copy(loop = false),
            )
            HomeLocomotion((0 until spec.frameCount).map { bitmap to Rect(it % spec.columns * width, it / spec.columns * height,
                (it % spec.columns + 1) * width, (it / spec.columns + 1) * height) }, selected, pet == PetType.GINGER)
        }
        private fun loadLegacy(context: Context, pet: PetType): HomeLocomotion {
            val bank: HomeLegacyClips = HomeLegacyClips.forPet(pet)
            val frames: List<Pair<Bitmap, Rect>> = bank.resources.map { resource ->
                val bitmap: Bitmap = requireNotNull(BitmapFactory.decodeResource(context.resources, resource, BitmapFactory.Options().apply { inScaled = false; inSampleSize = 2 }))
                bitmap to Rect(0, 0, bitmap.width, bitmap.height)
            }
            return HomeLocomotion(frames, bank.clips, false, normalizeClips = true)
        }
    }
}
