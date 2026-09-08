package com.pixelpals.app.feature.home

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A small thought cloud. All movement stops with the user's reduced-motion setting. */
internal class HomeDreamPainter {
    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path: Path = Path()
    fun draw(canvas: Canvas, x: Float, floor: Float, size: Float, seconds: Float, reduced: Boolean): Unit {
        val center: Float = (x + size * .26f).coerceIn(100f, 900f)
        val top: Float = (floor - size * .82f).coerceAtLeast(80f)
        val drift: Float = if (reduced) 0f else sin(seconds * .9f) * 3f
        val opacity: Int = if (reduced) 255 else ((seconds - 1.4f) / .6f * 255).toInt().coerceIn(0, 255)
        canvas.save()
        canvas.translate(center, top + drift)
        paint.style = Paint.Style.FILL
        paint.color = 0xfff8f2ff.toInt()
        paint.alpha = opacity
        canvas.drawCircle(-34f, 40f, 6f, paint)
        canvas.drawCircle(-25f, 25f, 10f, paint)
        canvas.drawRoundRect(-63f, -55f, 63f, 12f, 32f, 32f, paint)
        canvas.drawCircle(-22f, -50f, 23f, paint)
        canvas.drawCircle(18f, -52f, 26f, paint)
        paint.color = 0xff87698f.toInt()
        paint.alpha = opacity
        when (if (reduced) 0 else (seconds / 8f).toInt() % 3) {
            1 -> drawHeart(canvas)
            2 -> drawBall(canvas)
            else -> drawMoon(canvas)
        }
        canvas.restore()
    }
    private fun drawMoon(canvas: Canvas): Unit {
        path.reset()
        path.moveTo(1f, -49f)
        path.cubicTo(-30f, -51f, -38f, -9f, -7f, -5f)
        path.cubicTo(5f, -3f, 13f, -11f, 15f, -19f)
        path.cubicTo(-9f, -10f, -22f, -35f, 1f, -49f)
        canvas.drawPath(path, paint)
        drawStar(canvas, 29f, -41f, 9f)
        drawStar(canvas, 39f, -17f, 5f)
    }
    private fun drawHeart(canvas: Canvas): Unit {
        path.reset()
        path.moveTo(0f, -7f)
        path.cubicTo(-46f, -36f, -18f, -65f, 0f, -41f)
        path.cubicTo(18f, -65f, 46f, -36f, 0f, -7f)
        canvas.drawPath(path, paint)
        drawStar(canvas, 40f, -35f, 6f)
    }
    private fun drawBall(canvas: Canvas): Unit {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        canvas.drawCircle(0f, -29f, 20f, paint)
        canvas.drawArc(-20f, -49f, 8f, -9f, -70f, 140f, false, paint)
        paint.style = Paint.Style.FILL
        drawStar(canvas, 35f, -44f, 7f)
    }
    private fun drawStar(canvas: Canvas, x: Float, y: Float, radius: Float): Unit {
        path.reset()
        for (index: Int in 0 until 8) {
            val angle: Float = index * PI.toFloat() / 4f
            val length: Float = if (index % 2 == 0) radius else radius * .35f
            val px: Float = x + cos(angle) * length
            val py: Float = y + sin(angle) * length
            if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        canvas.drawPath(path, paint)
    }
}
