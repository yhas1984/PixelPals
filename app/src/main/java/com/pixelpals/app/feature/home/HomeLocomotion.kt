package com.pixelpals.app.feature.home

import android.content.Context
import android.graphics.*
import com.pixelpals.app.BuildConfig
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
    private val anchor: android.graphics.PointF? = null,
    private val restArtwork: HomeRestArtwork? = null,
    private val cellScale: Float? = null,
    private val frameScales: List<Float>? = null,
) {
    private val sprites: HomeSpriteFrames = HomeSpriteFrames(frames, anchor, cellScale, frameScales)
    fun draw(canvas: Canvas, paint: Paint, target: RectF, motion: CompanionMotion, reduced: Boolean, poseClip: String? = null, poseSeconds: Float = 0f): Unit {
        if (poseClip == null && restArtwork != null && (motion.activity == CompanionActivity.REST || motion.activity == CompanionActivity.WAKE)) {
            restArtwork.draw(canvas, paint, target, motion.elapsed, motion.activity == CompanionActivity.WAKE, reduced)
            return
        }
        val name: String = when {
            poseClip != null -> poseClip
            reduced -> if (motion.activity == CompanionActivity.REST) "sleep" else "idle"
            motion.isTurning && motion.speed <= .1f -> "turn"
            motion.isPreparing -> "idle"
            motion.isApproaching -> "walk"
            motion.activity == CompanionActivity.WAKE -> "wake"
            motion.activity == CompanionActivity.REST -> "sleep"
            motion.activity == CompanionActivity.PLAY -> "play"
            else -> "idle"
        }
        val clip: PetClipSpec = clips[name] ?: requireNotNull(clips["idle"])
        val phase: Long = when {
            poseClip != null -> (poseSeconds * 1000f).toLong()
            reduced -> if (name == "sleep") clip.frames.lastIndex * clip.frameDurationMs.toLong() else 0L
            name == "walk" -> (motion.distanceTravelled / 105f * clip.frames.size * clip.frameDurationMs).toLong()
            name == "wake" -> (motion.elapsed / CompanionMotion.WAKE_SECONDS * clip.frames.size * clip.frameDurationMs).toLong()
            name == "turn" -> (motion.turnElapsed / CompanionMotion.TURN_SECONDS * clip.frames.size * clip.frameDurationMs).toLong()
            else -> (motion.elapsed * 1000).toLong()
        }
        val index: Int = (phase / clip.frameDurationMs).toInt().let { if (clip.loop) it % clip.frames.size else it.coerceAtMost(clip.frames.lastIndex) }
        canvas.save()
        if (nativeFacesLeft) canvas.scale(-1f, 1f, target.centerX(), target.bottom)
        sprites.draw(canvas, paint, target, clip.frames[index])
        canvas.restore()
    }
    fun toyResponse(elapsed: Float): Float {
        val clip: PetClipSpec = clips["play"] ?: return 0f
        val duration: Float = clip.frames.size * clip.frameDurationMs / 1000f
        val contact: Float = minOf(2, clip.frames.lastIndex) * clip.frameDurationMs / 1000f
        val phase: Float = elapsed % duration
        if (phase < contact) return 0f
        val response: Float = ((phase - contact) / (duration - contact)).coerceIn(0f, 1f)
        return kotlin.math.sin(response * Math.PI.toFloat()).coerceAtLeast(0f)
    }
    companion object {
        suspend fun load(context: Context, pet: PetType): HomeLocomotion = withContext(Dispatchers.IO) {
            // Corgi shares the original desktop camera and gait in every build.
            if (pet == PetType.CORGI) return@withContext loadLegacy(context, pet)
            val folder: String = "pets/${pet.name.lowercase()}"
            val filename: String? = context.assets.list(folder).orEmpty().filter { it.endsWith(".json") && !it.startsWith("care") }
                .sortedWith(compareByDescending<String> { it.contains("motion_v2") }.thenByDescending { it }).firstOrNull()
            val candidate: String = "${pet.name.lowercase()}.json"
            val hasCandidate: Boolean = BuildConfig.DEBUG && candidate in context.assets.list("companion/pets").orEmpty()
            if (filename == null && !hasCandidate) return@withContext loadLegacy(context, pet)
            val path: String = if (hasCandidate) "companion/pets/$candidate" else "$folder/$filename"
            val json: JSONObject = context.assets.open(path).bufferedReader().use { JSONObject(it.readText()) }
            val spec: PetAtlasSpec = PetAtlasSpec.fromJson(json)
            val bitmap: Bitmap = context.assets.open(spec.atlasPath).use { requireNotNull(BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 2 })) }
            val width: Int = bitmap.width / spec.columns
            val height: Int = bitmap.height / spec.rows
            fun choose(vararg ids: String): PetClipSpec = ids.firstNotNullOfOrNull { spec.clip(it) } ?: spec.clips.first()
            val idle: PetClipSpec = choose("idle", "sit", "perch_loop", "hover")
            val selected: Map<String, PetClipSpec> = mapOf(
                "idle" to idle,
                "stalk" to choose("stalk", "idle"),
                "pounce" to choose("pounce", "idle"),
                "land" to choose("land", "idle"),
                "walk" to choose("walk", "crawl_loop", "right", "glide", "hover"),
                "turn" to (spec.clip("turn")?.copy(loop = false) ?: idle),
                "play" to choose("play", "playful_delight", "happy", "front_social", "groom", "grace", "tongue_strike", "idle"),
                "sleep" to (if (pet == PetType.ANGEL) choose("prayer").let { it.copy(frames = it.frames.take(2)) } else if (pet == PetType.MENTA) choose("blink", "idle") else choose("sleep", "prayer", "perch_loop", "idle")).copy(loop = false),
            )
            val sleep: PetClipSpec = requireNotNull(selected["sleep"])
            val withWake: Map<String, PetClipSpec> = selected + ("wake" to (spec.clip("wake") ?: sleep.copy(id = "wake", frames = sleep.frames.reversed(), loop = false)))
            val anchor: PointF? = if (json.optJSONObject("renderHints")?.optBoolean("preserveFrameAnchors") == true)
                spec.pivot?.let { PointF(it.x * width.toFloat() / spec.frameWidth, it.y * height.toFloat() / spec.frameHeight) } else null
            HomeLocomotion((0 until spec.frameCount).map { bitmap to Rect(it % spec.columns * width, it / spec.columns * height,
                (it % spec.columns + 1) * width, (it / spec.columns + 1) * height) }, withWake, pet == PetType.GINGER, anchor = anchor, restArtwork = HomeRestArtwork.load(context, pet))
        }
        private fun loadLegacy(context: Context, pet: PetType): HomeLocomotion {
            val bank: HomeLegacyClips = HomeLegacyClips.forPet(pet)
            val frames: List<Pair<Bitmap, Rect>> = bank.resources.map { resource ->
                val bitmap: Bitmap = requireNotNull(BitmapFactory.decodeResource(context.resources, resource, BitmapFactory.Options().apply { inScaled = false; inSampleSize = 2 }))
                bitmap to Rect(0, 0, bitmap.width, bitmap.height)
            }
            if (pet == PetType.CORGI) {
                val size: Float = frames.first().second.width().toFloat()
                return HomeLocomotion(frames, bank.clips, false,
                    anchor = PointF(size / 2f, size * com.pixelpals.app.core.motion.CorgiArtworkScale.ORIGINAL_GROUND),
                    restArtwork = HomeRestArtwork.load(context, pet),
                    cellScale = com.pixelpals.app.core.motion.CorgiArtworkScale.ORIGINAL_CELL,
                    frameScales = listOf(0, 2, 6, 7, 10, 11, 12, 13).map { com.pixelpals.app.core.motion.CorgiArtworkScale.originalFrame(it) })
            }
            return HomeLocomotion(frames, bank.clips, false, restArtwork = HomeRestArtwork.load(context, pet))
        }
    }
}
