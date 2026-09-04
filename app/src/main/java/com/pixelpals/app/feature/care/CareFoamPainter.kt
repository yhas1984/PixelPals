package com.pixelpals.app.feature.care

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.pixelpals.app.core.care.scene.CarePoint
import com.pixelpals.app.core.care.scene.CareWashState

/** White lather with a blue rim remains readable on fur, clouds and snow. */
class CareFoamPainter {
    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun draw(canvas: Canvas, center: CarePoint, size: Float, state: CareWashState): Unit {
        if (state.foam <= 0f) return
        for (index: Int in 0 until 13) {
            val x: Float = center.x + ((index % 5) - 2) * size * .065f
            val y: Float = center.y + (index / 5) * size * .047f - size * .015f
            val radius: Float = size * (.045f + (index % 3) * .009f) * (.65f + .35f * state.foam)
            drawBubble(canvas, CarePoint(x, y), radius, state.foam)
        }
        if (state.drift <= 0f) return
        for (index: Int in 0..2) {
            val side: Float = if (index % 2 == 0) -1f else 1f
            val x: Float = center.x + size * side * (.23f + state.drift)
            val y: Float = center.y - size * (state.drift + index * .035f)
            drawBubble(canvas, CarePoint(x, y), size * .025f, state.foam * .8f)
        }
    }

    private fun drawBubble(canvas: Canvas, point: CarePoint, radius: Float, alpha: Float): Unit {
        paint.style = Paint.Style.FILL
        paint.color = Color.argb((235 * alpha).toInt(), 242, 251, 253)
        canvas.drawCircle(point.x, point.y, radius, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = radius * .13f
        paint.color = Color.argb((180 * alpha).toInt(), 135, 183, 206)
        canvas.drawCircle(point.x, point.y, radius, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb((245 * alpha).toInt(), 255, 255, 255)
        canvas.drawCircle(point.x - radius * .28f, point.y - radius * .3f, radius * .24f, paint)
    }
}
