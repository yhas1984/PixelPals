package com.pixelpals.app.feature.care

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.pixelpals.app.core.care.scene.CarePoint

/** A vapor base preserves Michi's face while making the cloud identity visible. */
class CloudCarePainter {
    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun draw(canvas: Canvas, ground: CarePoint, size: Float, expansion: Float): Unit {
        val spread: Float = .24f + expansion * .10f
        paint.color = Color.argb(145, 196, 228, 245)
        canvas.drawOval(ground.x - size * spread, ground.y - size * .12f,
            ground.x + size * spread, ground.y + size * .025f, paint)
        paint.color = Color.argb(195, 245, 251, 255)
        repeat(5) { index ->
            val x: Float = ground.x + (index - 2) * size * spread * .42f
            val y: Float = ground.y - size * (.045f + (index % 2) * .025f)
            canvas.drawCircle(x, y, size * (.07f + expansion * .018f), paint)
        }
    }
}
