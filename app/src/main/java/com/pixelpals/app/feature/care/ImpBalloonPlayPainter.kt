package com.pixelpals.app.feature.care

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.pixelpals.app.core.care.scene.CarePoint
import com.pixelpals.app.core.care.scene.ImpBalloonPlayPose
import com.pixelpals.app.core.care.scene.ImpBalloonPose
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Balloons sit behind a foreground trident so every strike has an obvious cause and effect. */
class ImpBalloonPlayPainter {
    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val colors: List<Int> = listOf(
        Color.rgb(226, 71, 83),
        Color.rgb(132, 91, 196),
        Color.rgb(245, 181, 67),
    )

    fun draw(canvas: Canvas, grip: CarePoint, size: Float, pose: ImpBalloonPlayPose): Unit {
        val centers: List<CarePoint> = pose.balloons.map { balloon: ImpBalloonPose ->
            CarePoint(grip.x + balloon.x * size, grip.y + balloon.y * size)
        }
        pose.balloons.forEachIndexed { index: Int, balloon: ImpBalloonPose ->
            drawBalloon(canvas, centers[index], size, colors[index], balloon)
        }
        drawTrident(canvas, grip, size, pose, centers)
        pose.balloons.forEachIndexed { index: Int, balloon: ImpBalloonPose ->
            if (balloon.burst > 0f) drawBurst(canvas, centers[index], size, colors[index], balloon.burst)
        }
    }

    private fun drawBalloon(
        canvas: Canvas,
        center: CarePoint,
        size: Float,
        color: Int,
        pose: ImpBalloonPose,
    ): Unit {
        if (pose.alpha <= 0f) return
        paint.alpha = (pose.alpha * 255f).toInt().coerceIn(0, 255)
        paint.color = Color.rgb(118, 91, 121)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = size * .009f
        canvas.drawLine(center.x, center.y + size * .15f, center.x - size * .035f, center.y + size * .34f, paint)
        canvas.save()
        canvas.scale(pose.scale, pose.scale, center.x, center.y)
        paint.style = Paint.Style.FILL
        paint.color = color
        canvas.drawOval(
            center.x - size * .12f,
            center.y - size * .15f,
            center.x + size * .12f,
            center.y + size * .15f,
            paint,
        )
        val knot = android.graphics.Path().apply {
            moveTo(center.x, center.y + size * .13f)
            lineTo(center.x - size * .035f, center.y + size * .19f)
            lineTo(center.x + size * .035f, center.y + size * .19f)
            close()
        }
        canvas.drawPath(knot, paint)
        paint.color = Color.argb(180, 255, 255, 255)
        canvas.drawOval(
            center.x - size * .065f,
            center.y - size * .105f,
            center.x - size * .025f,
            center.y - size * .035f,
            paint,
        )
        canvas.restore()
        paint.alpha = 255
    }

    private fun drawTrident(
        canvas: Canvas,
        grip: CarePoint,
        size: Float,
        pose: ImpBalloonPlayPose,
        centers: List<CarePoint>,
    ): Unit {
        val target: CarePoint = pose.activeBalloon?.let(centers::get)
            ?: CarePoint(grip.x + size * .36f, grip.y - size * .48f)
        val deltaX: Float = target.x - grip.x
        val deltaY: Float = target.y - grip.y
        val length: Float = sqrt(deltaX * deltaX + deltaY * deltaY).coerceAtLeast(1f)
        val unitX: Float = deltaX / length
        val unitY: Float = deltaY / length
        val perpendicularX: Float = -unitY
        val perpendicularY: Float = unitX
        val handle: CarePoint = CarePoint(grip.x - unitX * size * .22f, grip.y - unitY * size * .22f)
        val reach: Float = if (pose.activeBalloon == null) .82f else .45f + .55f * pose.thrust
        val tip: CarePoint = CarePoint(grip.x + deltaX * reach, grip.y + deltaY * reach)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = Color.rgb(122, 61, 42)
        paint.strokeWidth = size * .035f
        canvas.drawLine(handle.x, handle.y, tip.x, tip.y, paint)
        paint.color = Color.rgb(247, 188, 64)
        paint.strokeWidth = size * .025f
        val forkBaseX: Float = tip.x - unitX * size * .055f
        val forkBaseY: Float = tip.y - unitY * size * .055f
        canvas.drawLine(
            forkBaseX + perpendicularX * size * .09f,
            forkBaseY + perpendicularY * size * .09f,
            tip.x + unitX * size * .105f + perpendicularX * size * .09f,
            tip.y + unitY * size * .105f + perpendicularY * size * .09f,
            paint,
        )
        canvas.drawLine(tip.x, tip.y, tip.x + unitX * size * .14f, tip.y + unitY * size * .14f, paint)
        canvas.drawLine(
            forkBaseX - perpendicularX * size * .09f,
            forkBaseY - perpendicularY * size * .09f,
            tip.x + unitX * size * .105f - perpendicularX * size * .09f,
            tip.y + unitY * size * .105f - perpendicularY * size * .09f,
            paint,
        )
        canvas.drawLine(
            forkBaseX + perpendicularX * size * .09f,
            forkBaseY + perpendicularY * size * .09f,
            forkBaseX - perpendicularX * size * .09f,
            forkBaseY - perpendicularY * size * .09f,
            paint,
        )
        paint.style = Paint.Style.FILL
    }

    private fun drawBurst(canvas: Canvas, center: CarePoint, size: Float, color: Int, amount: Float): Unit {
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = size * .025f
        paint.color = color
        paint.alpha = (amount * 255f).toInt().coerceIn(0, 255)
        repeat(10) { index: Int ->
            val angle: Double = index * PI * 2.0 / 10.0
            val inner: Float = size * (.11f + .03f * amount)
            val outer: Float = size * (.20f + .08f * amount)
            canvas.drawLine(
                center.x + cos(angle).toFloat() * inner,
                center.y + sin(angle).toFloat() * inner,
                center.x + cos(angle).toFloat() * outer,
                center.y + sin(angle).toFloat() * outer,
                paint,
            )
        }
        paint.alpha = 255
        paint.style = Paint.Style.FILL
    }
}
