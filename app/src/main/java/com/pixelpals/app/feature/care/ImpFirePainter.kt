package com.pixelpals.app.feature.care

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import com.pixelpals.app.core.care.scene.CarePoint
import com.pixelpals.app.core.care.scene.ImpFirePose

/** One warm, mouth-anchored puff, not a detached toy or a flashing screen effect. */
class ImpFirePainter {
    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val flame: Path = Path().apply {
        moveTo(0f, 0f)
        cubicTo(.18f, -.16f, .32f, -.22f, .51f, -.18f)
        quadTo(.46f, -.28f, .47f, -.34f)
        quadTo(.68f, -.24f, .79f, -.11f)
        quadTo(.87f, -.17f, 1f, -.14f)
        quadTo(.85f, -.03f, .75f, .07f)
        quadTo(.72f, .19f, .57f, .23f)
        quadTo(.58f, .16f, .54f, .12f)
        cubicTo(.33f, .25f, .18f, .11f, 0f, 0f)
        close()
    }
    private val glow: LinearGradient = LinearGradient(0f, 0f, 1f, 0f,
        intArrayOf(Color.rgb(255, 219, 117), Color.rgb(255, 149, 46), Color.rgb(235, 79, 33)),
        null, Shader.TileMode.CLAMP)

    fun draw(canvas: Canvas, mouth: CarePoint, actorSize: Float, pose: ImpFirePose): Unit {
        if (pose.strength <= 0f) return
        val size: Float = actorSize * pose.reach
        canvas.save()
        canvas.translate(mouth.x, mouth.y)
        canvas.rotate(-12f)
        canvas.scale(size, size)
        paint.shader = glow
        paint.color = Color.WHITE
        paint.alpha = (pose.strength * 245).toInt()
        canvas.drawPath(flame, paint)
        canvas.scale(.64f, .55f)
        paint.shader = null
        paint.color = Color.rgb(255, 237, 163)
        paint.alpha = (pose.strength * 250).toInt()
        canvas.drawPath(flame, paint)
        canvas.restore()
    }
}
