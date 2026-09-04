package com.pixelpals.app.feature.care

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.pixelpals.app.core.care.scene.*
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.feature.overlay.behavior.PetAtlasSpec
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Shared room/desktop choreography. Corgi retains its separately tuned fetch sequence. */
class SpeciesCareRenderer {
    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val source: Rect = Rect()
    private val actor: RectF = RectF()
    private val transform: Matrix = Matrix()
    private val coordinates: FloatArray = FloatArray(2)
    private val props: CarePropPainter = CarePropPainter()
    private val foam: CareFoamPainter = CareFoamPainter()
    private val cloud: CloudCarePainter = CloudCarePainter()
    private val impWings: ImpWingPainter = ImpWingPainter()
    private val impFire: ImpFirePainter = ImpFirePainter()
    private val impBalloonPlay: ImpBalloonPlayPainter = ImpBalloonPlayPainter()
    private val impBalloonGripAnchor: CarePoint = CarePoint(.70f, .72f)
    private var frame: Int = 0
    private var pose: SpeciesCarePose = SpeciesCarePose()
    private var pet: PetType = PetType.DIABLILLO
    private var profile: PetCareProfile = PetCareProfile.forPet(pet)

    private fun prepare(pack: CarePosePack, action: CareSceneAction, elapsed: Long, progress: Float,
                        width: Float, height: Float, reduced: Boolean, desktopSize: Int?,
                        variation: CarePlayVariation): Unit {
        val next: PetType = PetType.valueOf(pack.spec.atlas.petId.uppercase(java.util.Locale.ROOT))
        if (pet != next) { pet = next; profile = PetCareProfile.forPet(next) }
        val poseElapsed: Long = if (action == CareSceneAction.PLAY && !reduced && profile.play != CarePlayStyle.BALLOON_POP)
            (CarePlayChoreography.sample(progress, variation).poseProgress * pack.spec.timings.getValue(action).durationMs).toLong()
            else elapsed
        frame = pack.spec.getFrame(action, poseElapsed)
        if (pet == PetType.DIABLILLO && reduced) frame = ImpCareMotion.getReducedFrame(action,
            (progress * pack.spec.timings.getValue(action).durationMs).toLong()) ?: frame
        pose = SpeciesCareMotion.sample(profile, action, progress, reduced, variation)
        val size: Float = desktopSize?.times(.94f) ?: minOf(height * .76f, width * .66f)
        // Leave room above a tossed toy and below a hammock, even in a short room panel.
        val baseline: Float = desktopSize?.let { height / 2f + it * .46f }
            ?: height * if (action == CareSceneAction.REST) .75f else .88f
        val ground: CarePoint = pack.spec.anchors[frame].ground
        val left: Float = width / 2f - ground.x * size
        val top: Float = baseline - ground.y * size
        actor.set(left, top, left + size, top + size)
        transform.setScale(pose.scaleX, pose.scaleY, width / 2f, baseline)
        transform.postRotate(pose.rotation, width / 2f, baseline)
        transform.postTranslate(pose.x * size, pose.y * size)
    }

    private fun point(anchor: CarePoint): CarePoint {
        coordinates[0] = actor.left + anchor.x * actor.width()
        coordinates[1] = actor.top + anchor.y * actor.height()
        transform.mapPoints(coordinates)
        return CarePoint(coordinates[0], coordinates[1])
    }

    private fun contact(anchors: CarePoseAnchors, action: CareSceneAction): CarePoint = point(when (action) {
        CareSceneAction.FEED, CareSceneAction.MEDICINE -> anchors.mouth
        CareSceneAction.PET -> if (profile.touch == CareTouchStyle.SHELL) shell(anchors) else anchors.head
        CareSceneAction.CLEAN -> if (profile.touch == CareTouchStyle.SHELL) shell(anchors) else anchors.body
        CareSceneAction.REST -> anchors.ground
        CareSceneAction.PLAY -> when (profile.play) {
            CarePlayStyle.CLOUD_DRIFT -> anchors.head
            CarePlayStyle.PEEK, CarePlayStyle.MAGIC_CHASE -> anchors.mouth
            CarePlayStyle.PAW -> anchors.mouth
            CarePlayStyle.PADDLE, CarePlayStyle.SLIDE, CarePlayStyle.SLITHER, CarePlayStyle.FOLLOW -> anchors.ground
            else -> anchors.body
        }
    })

    private fun shell(anchors: CarePoseAnchors): CarePoint = CarePoint(
        anchors.body.x + if (anchors.mouth.x < anchors.body.x) .18f else -.18f,
        anchors.body.y - .1f,
    )

    fun getTarget(pack: CarePosePack, scene: CareSceneController, width: Float, height: Float,
                  elapsed: Long, reduced: Boolean): CarePoint {
        val renderedElapsed: Long = if (reduced && !scene.isComplete) 0L else elapsed
        prepare(pack, scene.action, renderedElapsed, scene.progress, width, height, reduced, null, scene.playVariation)
        val target: CarePoint = contact(pack.spec.anchors[frame], scene.action)
        return CarePoint(target.x / width, target.y / height)
    }

    fun draw(canvas: Canvas, pack: CarePosePack, scene: CareSceneController?, reduced: Boolean,
             gentle: Boolean, idleMs: Long = 0L, desktopSize: Int? = null): Unit {
        val action: CareSceneAction = scene?.action ?: CareSceneAction.PET
        val elapsed: Long = when {
            reduced -> if (scene?.isComplete == true) scene.timing.durationMs else 0L
            scene != null -> scene.animationMs
            idleMs % 4_500L > 4_250L -> 500L
            else -> 0L
        }
        prepare(pack, action, elapsed, scene?.progress ?: 0f, canvas.width.toFloat(), canvas.height.toFloat(), reduced,
            desktopSize, scene?.playVariation ?: CarePlayVariation.DIRECT)
        val anchors: CarePoseAnchors = pack.spec.anchors[frame]
        val ground: CarePoint = point(anchors.ground)
        if (action == CareSceneAction.REST && scene != null && profile.bed != CareBed.WING_WRAP) {
            props.draw(canvas, action, ground.x, ground.y, actor.width() * 1.08f, pet = pet)
        }
        if (pet == PetType.NUBE_MICHI) cloud.draw(canvas, ground, actor.width(),
            if (reduced) .2f else sin((scene?.progress ?: 0f) * PI).toFloat())
        val atlas: PetAtlasSpec = pack.spec.atlas
        source.set(frame % atlas.columns * atlas.frameWidth, frame / atlas.columns * atlas.frameHeight,
            (frame % atlas.columns + 1) * atlas.frameWidth, (frame / atlas.columns + 1) * atlas.frameHeight)
        paint.alpha = (255 * pose.alpha).toInt()
        paint.isFilterBitmap = atlas.renderHints.filterBitmap
        canvas.save()
        canvas.concat(transform)
        val isWingRest: Boolean = profile.bed == CareBed.WING_WRAP && action == CareSceneAction.REST &&
            scene?.hasContact == true && !scene.isCancelled
        val wingCenter: CarePoint = CarePoint(actor.left + anchors.body.x * actor.width(), actor.top + anchors.body.y * actor.height())
        val wingFold: Float = ImpCareMotion.getWingFold(scene?.progress ?: 0f, reduced)
        if (isWingRest) impWings.draw(canvas, wingCenter, actor.width(), wingFold, false)
        canvas.drawBitmap(pack.bitmap, source, actor, paint)
        if (isWingRest) impWings.draw(canvas, wingCenter, actor.width(), wingFold, true)
        canvas.restore()
        paint.alpha = 255
        if (scene?.hasContact == true && action == CareSceneAction.CLEAN) {
            foam.draw(canvas, contact(anchors, action), actor.width(), CareWashMotion.sample(scene.progress, reduced))
        }
        if (scene != null && action != CareSceneAction.REST) drawTool(canvas, scene, anchors, reduced)
        if (pet == PetType.DIABLILLO && action == CareSceneAction.FEED && scene?.hasContact == true && !scene.isCancelled) {
            impFire.draw(canvas, point(anchors.mouth), actor.width(), ImpCareMotion.sampleFire(scene.animationMs, reduced || gentle))
        }
        if (scene?.hasContact == true && !reduced && !gentle) drawReaction(canvas, scene, anchors)
    }

    private fun drawTool(canvas: Canvas, scene: CareSceneController, anchors: CarePoseAnchors, reduced: Boolean): Unit {
        val size: Float = actor.width()
        val target: CarePoint = contact(anchors, scene.action)
        val mouth: CarePoint = point(anchors.mouth)
        val body: CarePoint = point(anchors.body)
        val ground: CarePoint = point(anchors.ground)
        val p: Float = scene.progress
        if (scene.action == CareSceneAction.PLAY && profile.play == CarePlayStyle.BALLOON_POP) {
            val grip: CarePoint = point(impBalloonGripAnchor)
            impBalloonPlay.draw(canvas, grip, size, ImpBalloonPlayMotion.sample(p, reduced))
            return
        }
        val phase: Float = p * PI.toFloat() * 6f * profile.tempo
        var position: CarePoint = target
        var amount: Float = 1f
        val manual: CarePoint? = scene.prop?.takeUnless { scene.hasContact }
        if (manual != null) {
            position = CarePoint(manual.x * canvas.width, manual.y * canvas.height)
        } else if (!scene.hasContact) {
            position = CarePoint(canvas.width * .83f, canvas.height * .7f)
        } else when (scene.action) {
            CareSceneAction.FEED -> {
                val feedingProgress: Float = if (pet == PetType.DIABLILLO)
                    (scene.animationMs.toFloat() / ImpCareMotion.EATING_DURATION_MS).coerceIn(0f, 1f) else p
                amount = if (pet == PetType.DIABLILLO) ImpCareMotion.getFoodAmount(scene.animationMs)
                    else (1f - ((p - .2f) / .65f)).coerceIn(0f, 1f)
                position = feedingPosition(mouth, body, ground, size, feedingProgress, reduced)
                if (!reduced && profile.feeding == CareFeedingStyle.TONGUE && p in .2f.. .62f) {
                    line(canvas, mouth, position, Color.rgb(238, 144, 164), size * .018f)
                }
                if (profile.feeding == CareFeedingStyle.WEB) line(canvas, body, position, Color.rgb(223, 217, 237), size * .009f)
            }
            CareSceneAction.PLAY -> {
                val beat: CarePlayBeat = CarePlayChoreography.sample(if (reduced) 0f else p, scene.playVariation)
                position = playingPosition(target, mouth, body, size, beat)
                if (profile.play == CarePlayStyle.WEB) {
                    line(canvas, CarePoint(body.x, actor.top + size * .06f), position, Color.rgb(208, 197, 229), size * .009f)
                }
            }
            CareSceneAction.PET -> {
                val stroke: Float = if (pet == PetType.DIABLILLO) ImpCareMotion.samplePetting(p, reduced).handOffset
                    else if (reduced) 0f else .05f * sin(phase)
                position = CarePoint(target.x + size * stroke, target.y - size * .12f)
            }
            CareSceneAction.CLEAN -> position = CarePoint(target.x + size * .12f * if (reduced) 1f else sin(phase), target.y)
            CareSceneAction.MEDICINE -> {
                position = CarePoint(mouth.x + size * .055f, mouth.y + size * .07f)
                amount = (1f - p).coerceIn(0f, 1f)
            }
            CareSceneAction.REST -> Unit
        }
        val toolScale: Float = when {
            scene.action == CareSceneAction.PLAY && profile.play in setOf(CarePlayStyle.PEEK, CarePlayStyle.CLOUD_DRIFT) -> .36f
            scene.action == CareSceneAction.PLAY -> .28f
            else -> .30f
        }
        props.draw(canvas, scene.action, position.x, position.y, size * toolScale, amount, pet)
    }

    private fun feedingPosition(mouth: CarePoint, body: CarePoint, ground: CarePoint, size: Float, p: Float, reduced: Boolean): CarePoint {
        val direction: Float = if (mouth.x < body.x) -1f else 1f
        return when (profile.feeding) {
            CareFeedingStyle.HANDS -> {
                // One readable hand-to-mouth gesture; fast chewing must not make the food orbit.
                val lift: Float = if (reduced) .6f else ((p - .12f) / .27f).coerceIn(0f, 1f)
                CarePoint(body.x + (mouth.x - body.x) * lift + size * .06f, body.y + (mouth.y - body.y) * lift)
            }
            CareFeedingStyle.TONGUE -> CarePoint(mouth.x + direction * size * .28f * (1f - ((p - .25f) / .4f).coerceIn(0f, 1f)), mouth.y - size * .04f)
            CareFeedingStyle.ABSORB -> CarePoint(mouth.x + size * .18f * (1f - p), mouth.y + size * .04f)
            CareFeedingStyle.PECK -> CarePoint(mouth.x, ground.y - size * .025f)
            CareFeedingStyle.SWALLOW -> CarePoint(mouth.x + direction * size * .10f * (1f - p), mouth.y + size * .025f)
            CareFeedingStyle.WEB -> CarePoint(mouth.x, mouth.y + size * .2f * (1f - p))
            CareFeedingStyle.NIBBLE -> CarePoint(mouth.x, mouth.y + size * .06f)
        }
    }

    private fun playingPosition(target: CarePoint, mouth: CarePoint, body: CarePoint, size: Float, beat: CarePlayBeat): CarePoint {
        val wave: Float = beat.travel
        val orbit: Float = beat.lift
        val direction: Float = if (mouth.x < body.x) -1f else 1f
        val offset: CarePoint = when (profile.play) {
            // The imp's catching hands are on its right; do not toss through its eyes.
            CarePlayStyle.BALLOON_POP -> CarePoint(.35f, -.35f)
            CarePlayStyle.PAW -> CarePoint((if (mouth.x < body.x) -1f else 1f) * (.30f + .07f * wave), .03f + .05f * orbit)
            CarePlayStyle.FLOAT -> CarePoint(.30f * orbit, -.17f + .23f * wave)
            CarePlayStyle.GLIDE -> CarePoint(.30f + .07f * wave, -.03f + .08f * orbit)
            CarePlayStyle.MAGIC_CHASE -> CarePoint(direction * (.30f + .08f * wave), -.025f - .16f * orbit)
            // Float beside the head; leave room above Michi in the compact care panel.
            CarePlayStyle.CLOUD_DRIFT -> CarePoint(.46f + .04f * wave, -.08f - .07f * orbit)
            CarePlayStyle.BOUNCE -> CarePoint(.2f, -.06f - .17f * abs(wave))
            CarePlayStyle.PADDLE -> CarePoint(.23f + .07f * wave, -.025f)
            CarePlayStyle.PEEK -> CarePoint(direction * (.30f + .045f * wave), .055f - .06f * orbit)
            CarePlayStyle.TWIRL -> CarePoint(.32f + .06f * wave, -.07f + .12f * orbit)
            CarePlayStyle.SLIDE -> CarePoint(.28f + .10f * wave, -.03f)
            CarePlayStyle.FOLLOW -> CarePoint(.27f, -.16f)
            CarePlayStyle.SLITHER -> CarePoint(.23f * wave, -.12f)
            CarePlayStyle.WEB -> CarePoint(.15f * wave, .08f + .08f * orbit)
            CarePlayStyle.FETCH -> CarePoint(.23f, .1f)
        }
        return CarePoint(target.x + size * offset.x, target.y + size * offset.y)
    }

    private fun drawReaction(canvas: Canvas, scene: CareSceneController, anchors: CarePoseAnchors): Unit {
        val size: Float = actor.width()
        val p: Float = scene.progress
        if (scene.action == CareSceneAction.CLEAN || (scene.action == CareSceneAction.PET && profile.touch in
                setOf(CareTouchStyle.SHIMMER, CareTouchStyle.GLOW, CareTouchStyle.FLURRY, CareTouchStyle.SILK))) {
            val center: CarePoint = point(anchors.body)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = size * .008f
            paint.color = if (pet == PetType.MOKI) Color.rgb(134, 207, 149) else Color.rgb(191, 208, 236)
            paint.alpha = (150 * sin(p * PI).toFloat()).toInt().coerceIn(0, 255)
            repeat(4) { i ->
                val angle: Double = i * PI / 2 + p * PI
                val x: Float = center.x + cos(angle).toFloat() * size * .27f
                val y: Float = center.y + sin(angle).toFloat() * size * .22f
                canvas.drawCircle(x, y, size * .016f, paint)
            }
            paint.alpha = 255; paint.style = Paint.Style.FILL
        }
    }

    private fun line(canvas: Canvas, from: CarePoint, to: CarePoint, color: Int, width: Float): Unit {
        paint.color = color; paint.style = Paint.Style.STROKE; paint.strokeWidth = width; paint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(from.x, from.y, to.x, to.y, paint)
        paint.style = Paint.Style.FILL
    }
}
