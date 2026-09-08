package com.pixelpals.app.feature.care

import android.graphics.Canvas
import android.graphics.Paint
import com.pixelpals.app.core.care.scene.CarePoint

internal class YukiCarePainter {
    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
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
