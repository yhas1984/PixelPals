package com.pixelpals.app.feature.care

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.pixelpals.app.core.care.scene.CareSceneAction
import com.pixelpals.app.core.care.scene.CorgiFeedingMotion
import com.pixelpals.app.core.care.scene.CorgiAdditionalCareMotion
import com.pixelpals.app.core.care.scene.CorgiAdditionalCarePose
import com.pixelpals.app.core.care.scene.CarePoint
import com.pixelpals.app.core.care.scene.CareWashMotion
import kotlin.math.PI
import kotlin.math.sin

/** Drawn instead of locomotion in the existing PetView; no stage or second actor. */
class CorgiDesktopCareRenderer {
    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val source: Rect = Rect()
    private val destination: RectF = RectF()
    private val props: CarePropPainter = CarePropPainter()
    private val foam: CareFoamPainter = CareFoamPainter()

    fun draw(canvas: Canvas, pack: CarePosePack, spriteSize: Int, elapsedMs: Long,
             facingLeft: Boolean, reducedMotion: Boolean, action: CareSceneAction = CareSceneAction.FEED,
             fetchFrame: Int = 2): Unit {
        val size: Float = spriteSize * .94f
        val cx: Float = canvas.width / 2f
        // Match the feet of the regular Corgi sprites, not the bottom of the overlay window.
        val baseline: Float = canvas.height / 2f + spriteSize * .46f
        val elapsed: Long = if (reducedMotion) 0L else elapsedMs
        val additional: CorgiAdditionalCarePose? = if (action in CorgiAdditionalCareMotion.actions)
            CorgiAdditionalCareMotion.getPose(action, elapsedMs, reducedMotion) else null
        val frame: Int = when (action) {
            CareSceneAction.FEED -> if (reducedMotion) 0 else CorgiFeedingMotion.frameAt(elapsed)
            CareSceneAction.PLAY -> fetchFrame
            else -> additional?.frame ?: pack.spec.getFrame(action, elapsed)
        }
        val duration: Long = pack.spec.timings.getValue(action).durationMs
        val progress: Float = (elapsedMs.toFloat() / duration).coerceIn(0f, 1f)
        val ground: Float = pack.spec.anchors[frame].ground.y
        destination.set(cx - size / 2f, baseline - ground * size,
            cx + size / 2f, baseline + (1f - ground) * size)
        val atlas: com.pixelpals.app.feature.overlay.behavior.PetAtlasSpec = pack.spec.atlas
        source.set(frame % atlas.columns * atlas.frameWidth, frame / atlas.columns * atlas.frameHeight,
            (frame % atlas.columns + 1) * atlas.frameWidth, (frame / atlas.columns + 1) * atlas.frameHeight)
        canvas.save()
        if (facingLeft) canvas.scale(-1f, 1f, cx, baseline)
        if (action == CareSceneAction.REST && additional != null) {
            drawFadedProp(canvas, action, cx, baseline + size * .015f, spriteSize * .80f, additional)
        }
        canvas.save()
        if (additional != null) {
            canvas.rotate(additional.rotation, cx, baseline)
            canvas.scale(1f, additional.breathScale, cx, baseline)
        }
        canvas.drawBitmap(pack.bitmap, source, destination, paint)
        canvas.restore()
        when (action) {
            CareSceneAction.FEED -> {
                // The bowl stays on the ground while the head moves up to chew.
                val bowlX: Float = cx + (pack.spec.anchors[0].mouth.x - .5f) * size
                val bowlSize: Float = spriteSize * .31f
                props.draw(canvas, action, bowlX, baseline - bowlSize * .32f,
                    bowlSize, CorgiFeedingMotion.foodAt(elapsedMs))
            }
            CareSceneAction.PLAY -> Unit // The rolling/held ball is a separate non-touchable prop window.
            CareSceneAction.PET -> {
                val stroke: Float = if (reducedMotion) 0f else sin(progress * PI * 5).toFloat() * size * .055f
                val head = pack.spec.anchors[frame].head
                props.draw(canvas, action, destination.left + head.x * size + stroke,
                    destination.top + head.y * size - size * .05f, size * .26f)
            }
            CareSceneAction.CLEAN -> {
                val pose: CorgiAdditionalCarePose = requireNotNull(additional)
                val body = pack.spec.anchors[frame].body
                val bodyX: Float = destination.left + body.x * size
                val bodyY: Float = destination.top + body.y * size
                foam.draw(canvas, CarePoint(bodyX, bodyY), size, CareWashMotion.sample(progress, reducedMotion))
                drawFadedProp(canvas, action, bodyX + pose.propOffsetX * size,
                    bodyY + pose.propOffsetY * size, size * .29f, pose)
            }
            CareSceneAction.REST -> Unit // The cushion is behind the sleeping pet.
            CareSceneAction.MEDICINE -> {
                val pose: CorgiAdditionalCarePose = requireNotNull(additional)
                val mouth = pack.spec.anchors[frame].mouth
                val spoonSize: Float = size * .29f
                // The spoon bowl is above-left of its origin; align it with the mouth.
                drawFadedProp(canvas, action,
                    destination.left + mouth.x * size + spoonSize * .10f + pose.propOffsetX * size,
                    destination.top + mouth.y * size + spoonSize * .20f + pose.propOffsetY * size,
                    spoonSize, pose)
            }
        }
        canvas.restore()
    }

    private fun drawFadedProp(canvas: Canvas, action: CareSceneAction, x: Float, y: Float,
                              size: Float, pose: CorgiAdditionalCarePose): Unit {
        if (pose.propAlpha <= 0f) return
        val layer: Int = canvas.saveLayerAlpha(x - size, y - size, x + size, y + size,
            (pose.propAlpha * 255).toInt().coerceIn(0, 255))
        props.draw(canvas, action, x, y, size, pose.contentAmount)
        canvas.restoreToCount(layer)
    }

}
