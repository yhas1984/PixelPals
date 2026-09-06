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
        painter.drawBackground(canvas, if (kind == "expedition" && detail == "forest") HomeEnvironment.GARDEN else ExpeditionDestination.find(detail)?.environment ?: HomeEnvironment.COZY, 14)
        if (kind == "expedition" && detail == "night") {
            paint.color = Color.rgb(243, 219, 158); paint.strokeWidth = 6f
            canvas.drawLine(330f, 240f, 440f, 150f, paint)
            canvas.drawLine(440f, 150f, 580f, 230f, paint)
            canvas.drawCircle(330f, 240f, 12f, paint); canvas.drawCircle(440f, 150f, 15f, paint)
            canvas.drawCircle(580f, 230f, 12f, paint)
            paint.color = 0x44F3DB9E
            canvas.drawOval(320f, 485f, 680f, 630f, paint)
            star(canvas, 500f, 480f, 115f)
        } else if (kind == "expedition" && detail == "forest") {
            // A clearing, rather than the indoor room used by the home environment catalogue.
            for (x in listOf(190f, 750f)) {
                paint.color = Color.rgb(125, 103, 76)
                canvas.drawRoundRect(x, 170f, x + 45f, 490f, 12f, 12f, paint)
                paint.color = Color.rgb(82, 122, 91)
                canvas.drawOval(x - 155f, -70f, x + 190f, 240f, paint)
            }
            paint.color = 0x66F5E6B0
            canvas.drawOval(330f, 490f, 670f, 630f, paint)
            paint.color = Color.rgb(244, 222, 183)
            canvas.drawRoundRect(460f, 430f, 540f, 585f, 20f, 20f, paint)
            paint.color = Color.rgb(196, 128, 111)
            canvas.drawOval(330f, 300f, 670f, 475f, paint)
            paint.color = Color.rgb(255, 236, 204)
            canvas.drawCircle(420f, 365f, 24f, paint); canvas.drawCircle(525f, 338f, 20f, paint)
            canvas.drawCircle(590f, 405f, 27f, paint)
            paint.color = HomeUi.ink
            canvas.drawCircle(485f, 500f, 7f, paint); canvas.drawCircle(515f, 500f, 7f, paint)
        } else if (kind == "expedition") {
            paint.color = Color.rgb(130, 153, 102); paint.strokeWidth = 6f
            canvas.drawLine(500f, 450f, 500f, 315f, paint)
            paint.color = Color.rgb(177, 136, 90)
            canvas.drawOval(483f, 438f, 517f, 488f, paint)
            paint.color = Color.rgb(255, 245, 220); paint.strokeWidth = 4f
            for (i in 0..10) {
                val angle = Math.PI * (1.05 + i * .09)
                val x = 500f + kotlin.math.cos(angle).toFloat() * 100f
                val y = 335f + kotlin.math.sin(angle).toFloat() * 100f
                canvas.drawLine(500f, 350f, x, y, paint)
                canvas.drawCircle(x, y, 9f, paint)
            }
            painter.drawObject(canvas, requireNotNull(DecorationCatalog.find("wildflowers")), RectF(260f, 455f, 415f, 610f))
            painter.drawObject(canvas, requireNotNull(DecorationCatalog.find("wildflowers")), RectF(605f, 455f, 760f, 610f))
        } else {
            paint.color = if (kind == "care") Color.rgb(214, 161, 110) else Color.rgb(206, 125, 133)
            heart.reset(); heart.moveTo(500f, 575f)
            heart.cubicTo(210f, 405f, 350f, 255f, 500f, 375f)
            heart.cubicTo(650f, 255f, 790f, 405f, 500f, 575f)
            canvas.drawPath(heart, paint)
        }
        canvas.restore()
    }
    private fun star(canvas: Canvas, x: Float, y: Float, radius: Float): Unit {
        heart.reset()
        for (i in 0 until 10) {
            val angle = -Math.PI / 2 + i * Math.PI / 5
            val length = if (i % 2 == 0) radius else radius * .46f
            val px = x + kotlin.math.cos(angle).toFloat() * length
            val py = y + kotlin.math.sin(angle).toFloat() * length
            if (i == 0) heart.moveTo(px, py) else heart.lineTo(px, py)
        }
        heart.close(); paint.color = Color.rgb(243, 219, 158)
        canvas.drawPath(heart, paint)
    }

}
