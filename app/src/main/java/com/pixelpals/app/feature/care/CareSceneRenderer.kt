package com.pixelpals.app.feature.care

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.pixelpals.app.core.care.scene.CarePoint
import com.pixelpals.app.core.care.scene.CareSceneAction
import com.pixelpals.app.core.care.scene.CareSceneController
import com.pixelpals.app.core.care.scene.CareWashMotion
import kotlin.math.PI
import kotlin.math.sin

class CareSceneRenderer {
    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val source: Rect = Rect()
    private val actor: RectF = RectF()
    private val props: CarePropPainter = CarePropPainter()
    private val species: SpeciesCareRenderer = SpeciesCareRenderer()
    private val foam: CareFoamPainter = CareFoamPainter()

    private fun getPlayDestination(pack: CarePosePack, scene: CareSceneController): Float =
        scene.prop?.x?.coerceIn(.22f, .78f) ?: if (pack.spec.anchors[4].mouth.x < .5f) .24f else .76f

    private fun isPlayMirrored(pack: CarePosePack, scene: CareSceneController): Boolean =
        scene.action == CareSceneAction.PLAY && scene.hasContact &&
            (pack.spec.anchors[4].mouth.x - .5f) * (getPlayDestination(pack, scene) - .5f) < 0f

    private fun moveActor(pack: CarePosePack, scene: CareSceneController, width: Float): Unit {
        if (scene.action != CareSceneAction.PLAY || !scene.hasContact) return
        val approach: Float = (scene.progress / .65f).coerceIn(0f, 1f)
        val smooth: Float = approach * approach * (3f - 2f * approach)
        val offset: Float = (getPlayDestination(pack, scene) - .5f) * width * .6f * smooth
        actor.offset(offset, 0f)
    }

    fun getActorBounds(width: Float, height: Float): RectF {
        val size: Float = minOf(height * .90f, width * .70f)
        val baseline: Float = height * .90f
        actor.set((width - size) / 2f, baseline - size, (width + size) / 2f, baseline)
        return actor
    }

    fun getTarget(pack: CarePosePack, scene: CareSceneController, width: Float, height: Float,
                  elapsedMs: Long = scene.animationMs, stationary: Boolean = false): CarePoint {
        if (pack.spec.atlas.petId != "corgi") return species.getTarget(pack, scene, width, height, elapsedMs, stationary)
        val frame: Int = pack.spec.getFrame(scene.action, elapsedMs)
        val anchors: CarePoseAnchors = pack.spec.anchors[frame]
        val point: CarePoint = when (scene.action) {
            CareSceneAction.FEED, CareSceneAction.MEDICINE, CareSceneAction.PLAY -> anchors.mouth
            CareSceneAction.PET -> anchors.head
            CareSceneAction.CLEAN -> anchors.body
            CareSceneAction.REST -> anchors.ground
        }
        val rect: RectF = getActorBounds(width, height)
        if (!stationary) moveActor(pack, scene, width)
        val x: Float = if (!stationary && isPlayMirrored(pack, scene)) 1f - point.x else point.x
        return CarePoint((rect.left + x * rect.width()) / width, (rect.top + point.y * rect.height()) / height)
    }

    fun draw(canvas: Canvas, pack: CarePosePack, scene: CareSceneController?, reducedMotion: Boolean, gentle: Boolean, idleMs: Long = 0L): Unit {
        if (pack.spec.atlas.petId != "corgi") {
            species.draw(canvas, pack, scene, reducedMotion, gentle, idleMs)
            return
        }
        val width: Float = canvas.width.toFloat()
        val height: Float = canvas.height.toFloat()
        val rect: RectF = getActorBounds(width, height)
        val action: CareSceneAction = scene?.action ?: CareSceneAction.PET
        val elapsed: Long = when {
            reducedMotion -> if (scene?.isComplete == true) scene.timing.durationMs else 0L
            scene != null -> scene.animationMs
            idleMs % 4_500L > 4_250L -> 500L
            else -> 0L
        }
        val frame: Int = pack.spec.getFrame(action, elapsed)
        val target: CarePoint? = scene?.let { getTarget(pack, it, width, height, elapsed, reducedMotion) }
        val progress: Float = scene?.progress ?: 0f
        if (scene != null && target != null && action == CareSceneAction.REST) drawProp(canvas, scene, target, reducedMotion)
        val spec: com.pixelpals.app.feature.overlay.behavior.PetAtlasSpec = pack.spec.atlas
        source.set((frame % spec.columns) * spec.frameWidth, (frame / spec.columns) * spec.frameHeight,
            (frame % spec.columns + 1) * spec.frameWidth, (frame / spec.columns + 1) * spec.frameHeight)
        paint.alpha = 255
        paint.isFilterBitmap = spec.renderHints.filterBitmap
        canvas.save()
        if (!reducedMotion && (scene == null || action == CareSceneAction.REST)) {
            val breath: Float = sin(idleMs / 2_500f * PI).toFloat() * if (gentle) .004f else .008f
            canvas.scale(1f, 1f + breath, rect.centerX(), rect.bottom)
        }
        if (scene != null && !reducedMotion && isPlayMirrored(pack, scene)) canvas.scale(-1f, 1f, rect.centerX(), rect.centerY())
        canvas.drawBitmap(pack.bitmap, source, rect, paint)
        canvas.restore()
        if (scene != null && target != null && action == CareSceneAction.CLEAN && scene.hasContact) {
            foam.draw(canvas, CarePoint(target.x * width, target.y * height), rect.width(), CareWashMotion.sample(progress, reducedMotion))
        }
        if (scene != null && target != null && action != CareSceneAction.REST) drawProp(canvas, scene, target, reducedMotion)
    }

    private fun drawProp(canvas: Canvas, scene: CareSceneController, target: CarePoint, reduced: Boolean): Unit {
        val progress: Float = scene.progress
        val manual: CarePoint? = scene.prop?.takeIf { !scene.hasContact || scene.action == CareSceneAction.PLAY }
        var x: Float = manual?.x ?: target.x
        var y: Float = manual?.y ?: target.y
        if (!scene.hasContact && manual == null) { x = .83f; y = .70f }
        if (!reduced && scene.hasContact) {
            when (scene.action) {
                CareSceneAction.PLAY -> {
                    // Throw, approach, then a visible catch attached to the mouth.
                    val aim: Float = manual?.x?.coerceIn(.22f, .78f) ?: if (target.x < .5f) .24f else .76f
                    val catch: Float = ((progress - .3f) / .35f).coerceIn(0f, 1f)
                    x = aim + (target.x - aim) * catch
                    y = .76f + (target.y - .76f) * catch - .18f * sin((progress / .3f).coerceIn(0f, 1f) * PI).toFloat()
                }
                CareSceneAction.CLEAN, CareSceneAction.PET -> x += .05f * sin(progress * PI * 6).toFloat()
                CareSceneAction.FEED -> y += .06f
                CareSceneAction.MEDICINE -> { x += .035f; y += .05f }
                CareSceneAction.REST -> Unit
            }
        }
        val size: Float = if (scene.action == CareSceneAction.REST) minOf(canvas.width * .48f, canvas.height * .76f)
            else minOf(canvas.width * .20f, canvas.height * .30f) * if (scene.action == CareSceneAction.PLAY) .6f else 1f
        props.draw(canvas, scene.action, x * canvas.width, y * canvas.height, size,
            if (scene.action == CareSceneAction.FEED) (1f - progress * 1.25f).coerceAtLeast(0f) else 1f)
    }
}
