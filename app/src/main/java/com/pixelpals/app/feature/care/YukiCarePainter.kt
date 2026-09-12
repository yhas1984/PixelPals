package com.pixelpals.app.feature.care

import android.graphics.Canvas
import android.graphics.Paint
import com.pixelpals.app.core.care.scene.CarePoint

internal class YukiCarePainter {
    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    fun drawSnowImpact(canvas: Canvas, point: CarePoint, size: Float, progress: Float): Unit {
        paint.color = 0xffd2ebf3.toInt()
        paint.alpha = (255 * (1f - progress)).toInt().coerceIn(0, 255)
        repeat(5) { index ->
            val spread: Float = (index - 2) * size * .025f * (1f + progress)
            val lift: Float = size * (.06f + (index % 2) * .035f) * kotlin.math.sin(progress * kotlin.math.PI.toFloat())
            canvas.drawCircle(point.x + spread, point.y - lift, size * .018f * (1f - progress * .5f), paint)
        }
    }
    fun drawPuddle(canvas: Canvas, ground: CarePoint, size: Float, amount: Float): Unit {
        paint.color = 0xffbadfea.toInt()
        paint.alpha = (150 * amount).toInt()
        canvas.drawOval(ground.x - size * .32f * amount, ground.y - size * .025f,
            ground.x + size * .32f * amount, ground.y + size * .045f, paint)
    }
    fun drawWater(canvas: Canvas, head: CarePoint, size: Float, progress: Float, reduced: Boolean): Unit {
        if (progress > .8f) return
        paint.color = 0xff8ecddd.toInt()
        paint.alpha = 210
        paint.strokeWidth = size * .012f
        paint.strokeCap = Paint.Cap.ROUND
        repeat(4) { index ->
            val phase: Float = if (reduced) .5f else (progress * 13f + index * .23f) % 1f
            val x: Float = head.x + (index - 1.5f) * size * .035f
            val y: Float = head.y - size * .12f + phase * size * .22f
            canvas.drawLine(x, y, x, y + size * .028f, paint)
        }
    }
}
