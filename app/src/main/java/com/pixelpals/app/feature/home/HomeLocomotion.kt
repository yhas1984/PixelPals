package com.pixelpals.app.feature.home

import android.content.Context
import android.graphics.*
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
    private val usesCorgiGait: Boolean = false,
    private val usesCorgiPostures: Boolean = false,
    private val usesCorgiTurns: Boolean = false,
    private val usesGingerPostures: Boolean = false,
    private val usesGingerRest: Boolean = false,
    private val usesTelaGait: Boolean = false,
    private val usesDuckGait: Boolean = false,
    private val usesJellyElastic: Boolean = false,
    private val frameMirrors: List<Boolean>? = null,
    private val desktopCellScale: Float? = null,
    referenceBounds: List<Rect>? = null,
) {
    private val sprites: HomeSpriteFrames = HomeSpriteFrames(frames, anchor, cellScale, frameScales, frameMirrors, referenceBounds)
    internal val desktopSizeAdjustment: Float get() = desktopCellScale?.let(sprites::targetSizeAdjustment) ?: 1f
    internal val turnDurationSeconds: Float get() = if (usesCorgiTurns) com.pixelpals.app.core.motion.CorgiTurnMotion.DURATION_SECONDS else CompanionMotion.TURN_SECONDS
    internal val turnCommitSeconds: Float get() = if (usesCorgiTurns) com.pixelpals.app.core.motion.CorgiTurnMotion.DURATION_SECONDS else turnDurationSeconds / 2f
    internal val gingerPosturePack: Boolean get() = usesGingerPostures
    internal val gingerRestPack: Boolean get() = usesGingerRest
    fun draw(canvas: Canvas, paint: Paint, target: RectF, motion: CompanionMotion, reduced: Boolean, poseClip: String? = null, poseSeconds: Float = 0f, restStartsSeated: Boolean = false): Unit {
        if (usesJellyElastic &&
            motion.activity != CompanionActivity.REST && motion.activity != CompanionActivity.WAKE) {
            sprites.draw(canvas, paint, target, 0)
            return
        }
        if (usesGingerPostures && poseClip == null && drawGingerState(canvas, paint, target, motion, reduced, restStartsSeated)) return
        if (poseClip == null && restArtwork != null && (motion.activity == CompanionActivity.REST || motion.activity == CompanionActivity.WAKE)) {
            if (usesCorgiPostures) {
                drawCorgiRest(canvas, paint, target, motion, reduced, restStartsSeated)
                return
            }
            restArtwork.draw(canvas, paint, target, motion.elapsed, motion.activity == CompanionActivity.WAKE, reduced)
            return
        }
        val name: String = when {
            poseClip != null -> poseClip
            reduced -> if (motion.activity == CompanionActivity.REST) "sleep" else "idle"
            motion.isTurning && motion.speed <= .1f -> "turn"
            motion.isPreparing -> "idle"
            usesCorgiTurns && motion.isApproaching && motion.speed <= .1f -> "idle"
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
            name == "turn" -> (motion.turnElapsed / motion.turnDurationSeconds * clip.frames.size * clip.frameDurationMs).toLong()
            else -> (motion.elapsed * 1000).toLong()
        }
        val index: Int = if (usesCorgiGait && name == "walk" && !reduced) {
            com.pixelpals.app.core.motion.CorgiGait.cycleIndexAt(motion.distanceTravelled, target.width())
        } else if (usesDuckGait && name == "walk" && !reduced && poseClip == null) {
            com.pixelpals.app.core.motion.DuckGait.cycleIndexAt(motion.distanceTravelled, target.width() * requireNotNull(cellScale))
        } else (phase / clip.frameDurationMs).toInt().let { if (clip.loop) it % clip.frames.size else it.coerceAtMost(clip.frames.lastIndex) }
        canvas.save()
        if (nativeFacesLeft || (usesTelaGait && name == "walk")) canvas.scale(-1f, 1f, target.centerX(), target.bottom)
        if (usesCorgiTurns && name == "turn") {
            val pose = com.pixelpals.app.core.motion.CorgiTurnMotion.poseAt(if (poseClip != null) poseSeconds else motion.turnElapsed)
            if (pose.mirrored) canvas.scale(-1f, 1f, target.centerX(), target.bottom)
            sprites.draw(canvas, paint, target, pose.frame)
        } else {
            val frame: Int = if (usesTelaGait && name == "wake" && poseClip == null)
                restFrameForHandoff(motion, reduced) ?: clip.frames[index] else clip.frames[index]
            sprites.draw(canvas, paint, target, frame)
        }
        canvas.restore()
    }

    internal fun restElapsedForFrame(frame: Int, startsSeated: Boolean): Float? {
        val frames: List<Int> = if (usesGingerRest) {
            val rest = com.pixelpals.app.core.motion.GingerRestMotion
            if (startsSeated) rest.seatedRestFrames else rest.restFrames
        } else if (usesTelaGait) com.pixelpals.app.core.motion.TelaRestPose.restFrames else return null
        val index: Int = frames.indexOf(frame)
        if (index < 0) return null
        return index * if (usesGingerRest) com.pixelpals.app.core.motion.GingerRestMotion.STEP_SECONDS
            else com.pixelpals.app.core.motion.TelaRestPose.STEP_SECONDS
    }

    /** Optional banks shared with native desktop behavior retain their current pose. */
    internal fun restFrameForHandoff(motion: CompanionMotion, reduced: Boolean, startsSeated: Boolean = false): Int? {
        if (usesGingerRest) {
            val rest = com.pixelpals.app.core.motion.GingerRestMotion
            val seated: Boolean = if (motion.gingerPosturesEnabled) motion.gingerRestStartsSeated else startsSeated
            return when (motion.activity) {
                CompanionActivity.REST -> if (reduced) 21 else rest.restFrame(motion.elapsed, seated)
                CompanionActivity.WAKE -> if (reduced) 18 else rest.wakeFrame(motion.elapsed,
                    rest.restFrame(motion.restElapsedBeforeWake, seated))
                else -> null
            }
        }
        val sleep: PetClipSpec = clips["sleep"] ?: return null
        if (!usesTelaGait || sleep.frames != com.pixelpals.app.core.motion.TelaRestPose.restFrames) return null
        if (motion.activity == CompanionActivity.REST) return sleep.frames[if (reduced) sleep.frames.lastIndex
            else (motion.elapsed * 1000f / sleep.frameDurationMs).toInt().coerceIn(0, sleep.frames.lastIndex)]
        if (motion.activity != CompanionActivity.WAKE) return null
        if (reduced) return null
        // Reverse only the poses already reached when a schedule ends early.
        val wake: PetClipSpec = requireNotNull(clips["wake"])
        val reached: Int = (motion.restElapsedBeforeWake * 1000f / sleep.frameDurationMs)
            .toInt().coerceIn(0, sleep.frames.lastIndex)
        val start: Int = wake.frames.lastIndex - reached
        val step: Int = (motion.elapsed / CompanionMotion.WAKE_SECONDS * (reached + 1)).toInt()
        return wake.frames[(start + step).coerceIn(start, wake.frames.lastIndex)]
    }

    private fun drawGingerState(canvas: Canvas, paint: Paint, target: RectF,
        motion: CompanionMotion, reduced: Boolean, restStartsSeated: Boolean): Boolean {
        val timeline = com.pixelpals.app.core.motion.GingerPostureMotion
        if (usesGingerRest) restFrameForHandoff(motion, reduced, restStartsSeated)?.let { frame ->
            drawGingerFrame(canvas, paint, target, frame)
            return true
        }
        val frame: Int? = when {
            reduced -> when (motion.activity) {
                CompanionActivity.REST -> 2
                CompanionActivity.WAKE -> 18
                else -> 0
            }
            motion.activity == CompanionActivity.WAKE -> {
                val unfold = CompanionMotion.WAKE_SECONDS - timeline.DURATION_SECONDS
                if (motion.elapsed < unfold) when {
                    motion.elapsed < unfold / 3f -> 2
                    motion.elapsed < unfold * 2f / 3f -> 3
                    else -> 0
                } else timeline.poseAt(com.pixelpals.app.core.motion.GingerPostureMotion.Transition.STAND_UP,
                    motion.elapsed - unfold).frame
            }
            motion.gingerPostureTransition != null -> timeline.poseAt(
                requireNotNull(motion.gingerPostureTransition), motion.gingerPostureElapsed).frame
            motion.gingerPostureEndpoint != null -> motion.gingerPostureEndpoint
            motion.activity == CompanionActivity.REST -> {
                // ScheduledPetSleep has a plain motion clock. Its entry pose is
                // supplied explicitly; autonomous home motion tracks it itself.
                if (!motion.gingerPosturesEnabled && !restStartsSeated && motion.elapsed < timeline.DURATION_SECONDS)
                    timeline.poseAt(com.pixelpals.app.core.motion.GingerPostureMotion.Transition.SIT_DOWN, motion.elapsed).frame
                else 2
            }
            (motion.isTurning || motion.isApproaching) && motion.speed <= .1f -> 18
            motion.activity == CompanionActivity.OBSERVE || motion.activity == CompanionActivity.GREET ->
                if (motion.gingerIsSeated) 0 else 18
            else -> null
        }
        if (frame == null) return false
        drawGingerFrame(canvas, paint, target, frame)
        return true
    }

    private fun drawGingerFrame(canvas: Canvas, paint: Paint, target: RectF, frame: Int) {
        canvas.save()
        if (nativeFacesLeft) canvas.scale(-1f, 1f, target.centerX(), target.bottom)
        sprites.draw(canvas, paint, target, frame)
        canvas.restore()
    }

    private fun drawCorgiRest(canvas: Canvas, paint: Paint, target: RectF, motion: CompanionMotion, reduced: Boolean, startsSeated: Boolean) {
        val waking = motion.activity == CompanionActivity.WAKE
        val transition = com.pixelpals.app.core.motion.CorgiPostureMotion
        val seconds = motion.elapsed
        if (reduced) {
            if (waking) sprites.draw(canvas, paint, target, 0)
            else requireNotNull(restArtwork).draw(canvas, paint, target, seconds, false, true)
        } else if (waking) {
            // Within the shared wake duration, unfold first, then put weight on
            // the forepaws and raise the hips before any walking can resume.
            val unfoldSeconds = CompanionMotion.WAKE_SECONDS - transition.DURATION_SECONDS
            if (seconds < unfoldSeconds) {
                requireNotNull(restArtwork).draw(canvas, paint, target, seconds / unfoldSeconds * 1.6f, true, false)
            } else {
                sprites.draw(canvas, paint, target, transition.poseAt(
                    com.pixelpals.app.core.motion.CorgiPostureMotion.Transition.STAND_UP, seconds - unfoldSeconds).frame)
            }
        } else if (!startsSeated && seconds < transition.DURATION_SECONDS) {
            sprites.draw(canvas, paint, target, transition.poseAt(
                com.pixelpals.app.core.motion.CorgiPostureMotion.Transition.SIT_DOWN, seconds).frame)
        } else {
            requireNotNull(restArtwork).draw(canvas, paint, target,
                seconds - if (startsSeated) 0f else transition.DURATION_SECONDS, false, false)
        }
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
            val folder: String = "pets/${pet.name.lowercase()}"
            // Only the reviewed supplemental pack may extend Corgi's original bank.
            if (pet == PetType.CORGI && "corgi_motion_v2.json" !in context.assets.list(folder).orEmpty())
                return@withContext loadLegacy(context, pet)
            val available = context.assets.list(folder).orEmpty()
            val filename: String? = if (pet == PetType.GINGER && "ginger_rest_v2.json" in available) {
                "ginger_rest_v2.json"
            } else if (pet == PetType.GINGER && "ginger_motion_v2.json" in available) {
                "ginger_motion_v2.json"
            } else if (pet == PetType.TELA && "tela_rest_v2.json" in available) {
                "tela_rest_v2.json"
            } else available.filter { it.endsWith(".json") && !it.startsWith("care") }
                .sortedWith(compareByDescending<String> { it.contains("motion_v2") }.thenByDescending { it }).firstOrNull()
            // A debug build must not silently replace a pet's identity when entering its home.
            // Candidate artwork remains in the laboratory until its transitions are reviewed.
            if (filename == null) return@withContext loadLegacy(context, pet)
            val path: String = "$folder/$filename"
            val json: JSONObject = context.assets.open(path).bufferedReader().use { JSONObject(it.readText()) }
            val spec: PetAtlasSpec = PetAtlasSpec.fromJson(json)
            val bitmap: Bitmap = context.assets.open(spec.atlasPath).use { requireNotNull(BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = if (pet == PetType.CORGI) 1 else 2 })) }
            val width: Int = bitmap.width / spec.columns
            val height: Int = bitmap.height / spec.rows
            fun choose(vararg ids: String): PetClipSpec = ids.firstNotNullOfOrNull { spec.clip(it) } ?: spec.clips.first()
            val idle: PetClipSpec = choose("idle", "sit", "perch_loop", "hover")
            val selected: Map<String, PetClipSpec> = mapOf(
                "idle" to idle,
                "stalk" to choose("stalk", "idle"),
                "pounce" to choose("pounce", "idle"),
                "land" to choose("land", "idle"),
                "melt" to choose("melt", "idle").copy(loop = false),
                "walk" to choose("walk", "crawl_loop", "right", "glide", "hover"),
                "turn" to (spec.clip("turn")?.copy(loop = false) ?: idle),
                "play" to choose("play", "playful_delight", "happy", "front_social", "groom", "grace", "tongue_strike", "idle"),
                "sleep" to (if (pet == PetType.ANGEL) choose("prayer").let { it.copy(frames = it.frames.take(2)) } else if (pet == PetType.MENTA) choose("blink", "idle") else choose("sleep", "prayer", "perch_loop", "idle")).copy(loop = false),
            )
            val sleep: PetClipSpec = requireNotNull(selected["sleep"]).let {
                if (pet == PetType.TELA && spec.frameCount == 40)
                    it.copy(frames = com.pixelpals.app.core.motion.TelaRestPose.frames) else it
            }
            val withWake: Map<String, PetClipSpec> = selected + ("sleep" to sleep) + ("wake" to (spec.clip("wake") ?: sleep.copy(id = "wake", frames = sleep.frames.reversed(), loop = false)))
            val anchor: PointF? = if (json.optJSONObject("renderHints")?.optBoolean("preserveFrameAnchors") == true)
                spec.pivot?.let { PointF(it.x * width.toFloat() / spec.frameWidth, it.y * height.toFloat() / spec.frameHeight) } else null
            if (pet == PetType.CORGI) {
                require(spec.frameCount == 20 || spec.frameCount == 23) { "Unsupported Corgi posture pack" }
                return@withContext HomeLocomotion((0 until spec.frameCount).map { bitmap to Rect(it % spec.columns * width, it / spec.columns * height,
                    (it % spec.columns + 1) * width, (it / spec.columns + 1) * height) }, withWake, false,
                    anchor = PointF(width / 2f, height * com.pixelpals.app.core.motion.CorgiArtworkScale.ORIGINAL_GROUND),
                    restArtwork = HomeRestArtwork.load(context, pet),
                    cellScale = com.pixelpals.app.core.motion.CorgiArtworkScale.ORIGINAL_CELL,
                    frameScales = List(spec.frameCount) { com.pixelpals.app.core.motion.CorgiArtworkScale.originalFrame(it) },
                    usesCorgiGait = true, usesCorgiPostures = true, usesCorgiTurns = spec.frameCount == 23)
            }
            if (pet == PetType.GINGER && spec.frameCount in setOf(19, 22)) {
                return@withContext HomeLocomotion((0 until spec.frameCount).map { bitmap to Rect(it % spec.columns * width, it / spec.columns * height,
                    (it % spec.columns + 1) * width, (it / spec.columns + 1) * height) }, withWake, true,
                    anchor = PointF(width / 2f, height * 368f / 384f), cellScale = .875f,
                    frameScales = List(spec.frameCount) { com.pixelpals.app.core.motion.GingerArtworkScale.frame(it) },
                    usesGingerPostures = true, usesGingerRest = spec.frameCount == 22)
            }
            if (pet == PetType.PATITO && spec.frameCount == 16) {
                // The walking board and legacy flight poses use the same fixed cell
                // camera on both surfaces; wings must not resize the fitted body.
                return@withContext HomeLocomotion((0 until spec.frameCount).map { bitmap to Rect(it % spec.columns * width, it / spec.columns * height,
                    (it % spec.columns + 1) * width, (it / spec.columns + 1) * height) },
                    withWake, false, anchor = PointF(width / 2f, height * .92f),
                    restArtwork = HomeRestArtwork.load(context, pet), cellScale = .94f, usesDuckGait = true)
            }
            HomeLocomotion((0 until spec.frameCount).map { bitmap to Rect(it % spec.columns * width, it / spec.columns * height,
                (it % spec.columns + 1) * width, (it / spec.columns + 1) * height) }, withWake, pet == PetType.GINGER, anchor = anchor, restArtwork = HomeRestArtwork.load(context, pet),
                frameScales = if (pet == PetType.TELA && spec.frameCount in setOf(40, 43))
                    List(spec.frameCount) { com.pixelpals.app.core.motion.TelaArtworkScale.frame(it) } else null,
                usesTelaGait = pet == PetType.TELA,
                frameMirrors = if (pet == PetType.TELA && spec.frameCount in setOf(40, 43))
                    List(spec.frameCount) { com.pixelpals.app.core.motion.TelaArtworkScale.mirrored(it) } else null,
                desktopCellScale = if (pet == PetType.TELA && spec.frameCount in setOf(40, 43))
                    com.pixelpals.app.core.motion.PetArtworkScale.forPet(pet) /
                        com.pixelpals.app.core.motion.PetArtworkScale.speciesSize(pet) * spec.renderHints.drawScale else null)
        }
        private fun loadLegacy(context: Context, pet: PetType): HomeLocomotion {
            val bank: HomeLegacyClips = HomeLegacyClips.forPet(pet)
            val frames: List<Pair<Bitmap, Rect>> = bank.resources.map { resource ->
                val bitmap: Bitmap = requireNotNull(BitmapFactory.decodeResource(context.resources, resource, BitmapFactory.Options().apply { inScaled = false; inSampleSize = 2 }))
                bitmap to Rect(0, 0, bitmap.width, bitmap.height)
            }
            if (pet == PetType.JELLY) {
                val size = frames.first().second.width().toFloat()
                return HomeLocomotion(frames, bank.clips, false,
                    anchor = PointF(size / 2f, size * com.pixelpals.app.core.motion.JellyElasticMotion.GROUND),
                    cellScale = .82f, usesJellyElastic = true,
                    desktopCellScale = com.pixelpals.app.core.motion.PetArtworkScale.forPet(pet),
                    restArtwork = HomeRestArtwork.load(context, pet))
            }
            if (pet == PetType.CORGI) {
                val size: Float = frames.first().second.width().toFloat()
                return HomeLocomotion(frames, bank.clips, false,
                    anchor = PointF(size / 2f, size * com.pixelpals.app.core.motion.CorgiArtworkScale.ORIGINAL_GROUND),
                    restArtwork = HomeRestArtwork.load(context, pet),
                    cellScale = com.pixelpals.app.core.motion.CorgiArtworkScale.ORIGINAL_CELL,
                    frameScales = listOf(0, 2, 6, 7, 10, 11, 12, 13).map { com.pixelpals.app.core.motion.CorgiArtworkScale.originalFrame(it) },
                    usesCorgiGait = true)
            }
            return HomeLocomotion(frames, bank.clips, false, restArtwork = HomeRestArtwork.load(context, pet),
                referenceBounds = if (pet == PetType.BLOOP) BloopArtworkBounds.legacy(frames.first().second.width()) else null)
        }
    }
}
