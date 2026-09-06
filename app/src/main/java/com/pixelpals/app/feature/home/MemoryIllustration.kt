package com.pixelpals.app.feature.home

import android.content.Context
import android.graphics.*
import android.view.View

/** Small vector illustrations remain readable offline, including devices missing emoji glyphs. */
class MemoryIllustration(context: Context, private val kind: String, private val detail: String) : View(context) {
    private val painter = HomeScenePainter()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val heart = Path()
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onDraw(canvas: Canvas) {
        canvas.save()
        canvas.scale(width / 1000f, height / 760f)
        painter.drawBackground(canvas, ExpeditionDestination.find(detail)?.environment ?: HomeEnvironment.COZY, 14)
        if (kind == "expedition" && detail == "night") {
            paint.color = Color.rgb(243, 219, 158); paint.strokeWidth = 6f
            canvas.drawLine(330f, 480f, 440f, 310f, paint)
            canvas.drawLine(440f, 310f, 580f, 420f, paint)
            canvas.drawLine(580f, 420f, 690f, 260f, paint)
            canvas.drawCircle(330f, 480f, 16f, paint); canvas.drawCircle(440f, 310f, 18f, paint)
            canvas.drawCircle(580f, 420f, 15f, paint); canvas.drawCircle(690f, 260f, 20f, paint)
        } else if (kind == "expedition" && detail == "forest") {
            paint.color = Color.rgb(166, 121, 82)
            canvas.drawOval(365f, 300f, 635f, 595f, paint)
            paint.color = Color.rgb(249, 232, 191)
            canvas.drawCircle(445f, 385f, 68f, paint); canvas.drawCircle(555f, 385f, 68f, paint)
            paint.color = HomeUi.ink
            canvas.drawCircle(445f, 385f, 23f, paint); canvas.drawCircle(555f, 385f, 23f, paint)
            paint.color = Color.rgb(222, 171, 87)
            heart.reset(); heart.moveTo(470f, 450f); heart.lineTo(530f, 450f); heart.lineTo(500f, 495f); heart.close()
            canvas.drawPath(heart, paint)
        } else if (kind == "expedition") {
            val color = when (detail) { "forest" -> Color.rgb(180, 120, 80); "night" -> Color.rgb(234, 212, 140); else -> Color.rgb(233, 174, 111) }
            paint.color = color
            canvas.drawOval(345f, 320f, 495f, 445f, paint)
            canvas.drawOval(505f, 320f, 655f, 445f, paint)
            canvas.drawOval(375f, 440f, 495f, 520f, paint)
            canvas.drawOval(505f, 440f, 625f, 520f, paint)
            paint.color = HomeUi.ink
            canvas.drawRoundRect(490f, 360f, 510f, 520f, 10f, 10f, paint)
        } else {
            paint.color = if (kind == "care") Color.rgb(214, 161, 110) else Color.rgb(206, 125, 133)
            heart.reset(); heart.moveTo(500f, 575f)
            heart.cubicTo(210f, 405f, 350f, 255f, 500f, 375f)
            heart.cubicTo(650f, 255f, 790f, 405f, 500f, 575f)
            canvas.drawPath(heart, paint)
        }
        canvas.restore()
    }
}
