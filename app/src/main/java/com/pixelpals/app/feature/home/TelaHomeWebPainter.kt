package com.pixelpals.app.feature.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.sin

/** Uses scene coordinates; the host owns scaling, clipping and the habitat background. */
internal class TelaHomeWebPainter {
    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path: Path = Path()
    private val threads: Bitmap by lazy {
        Bitmap.createBitmap(1000, 760, Bitmap.Config.ARGB_8888).also { drawThreads(Canvas(it)) }
    }
    fun draw(canvas: Canvas, web: TelaHomeWeb, seconds: Float, reduced: Boolean): Unit {
        canvas.drawBitmap(threads, 0f, 0f, null)
        // A meal is drawn above Tela by the host, so its silk remains visible.
        if (!web.isEating) drawFlies(canvas, web, seconds, reduced)
    }
    private fun drawThreads(canvas: Canvas): Unit {
        paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.ROUND
        // A muted outline keeps the fine silk readable in every daylight environment.
        for (outline: Boolean in listOf(true, false)) {
            paint.color = if (outline) 0x55637662 else 0xd9fff0d9.toInt()
            paint.strokeWidth = if (outline) 4f else 1.7f
            for (point: WebPoint in TelaHomeWeb.BOUNDARY)
                canvas.drawLine(500f, 380f, point.x, point.y, paint)
            for (scale: Float in listOf(.16f, .32f, .47f, .62f, .76f, .90f, 1f)) {
                path.reset()
                TelaHomeWeb.BOUNDARY.forEachIndexed { i: Int, p: WebPoint ->
                    val x: Float = 500f + (p.x - 500f) * scale
                    val y: Float = 380f + (p.y - 380f) * scale
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close(); canvas.drawPath(path, paint)
            }
        }
        paint.style = Paint.Style.FILL; paint.color = 0xddf3f7eb.toInt()
        for ((index: Int, point: WebPoint) in TelaHomeWeb.NODES.withIndex()) {
            if (index % 3 == 0) canvas.drawCircle(point.x, point.y, 3.4f, paint)
        }
    }
    fun drawFlies(canvas: Canvas, web: TelaHomeWeb, seconds: Float, reduced: Boolean): Unit {
        for (index: Int in web.flies.indices) if (web.isFlyVisible(index)) {
            val point: WebPoint = web.flies[index]
            val wing: Float = if (reduced || web.isEating) 0f else sin(seconds * 9f + index) * 1.8f
            paint.style = Paint.Style.FILL; paint.color = 0xcfe1eef0.toInt()
            canvas.drawOval(point.x - 18f, point.y - 17f - wing, point.x - 1f, point.y + 1f, paint)
            canvas.drawOval(point.x + 1f, point.y - 17f + wing, point.x + 18f, point.y + 1f, paint)
            paint.color = 0xff42564c.toInt()
            canvas.drawOval(point.x - 6f, point.y - 7f, point.x + 6f, point.y + 12f, paint)
            canvas.drawCircle(point.x, point.y - 9f, 6f, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f
            for (dy: Float in listOf(-2f, 4f, 9f)) {
                canvas.drawLine(point.x - 5f, point.y + dy, point.x - 12f, point.y + dy + 5f, paint)
                canvas.drawLine(point.x + 5f, point.y + dy, point.x + 12f, point.y + dy + 5f, paint)
            }
            if (web.isEating && kotlin.math.hypot(web.x - point.x, web.y - point.y) < 1f) {
                paint.color = 0xddfff6e9.toInt(); paint.strokeWidth = 2f
                val count: Int = (web.clipSeconds / TelaHomeWeb.MEAL_SECONDS * 6f).toInt().coerceIn(1, 6)
                for (line: Int in 0 until count) canvas.drawLine(point.x - 13f, point.y - 12f + line * 5f,
                    point.x + 13f, point.y - 7f + line * 4f, paint)
            }
        }
        paint.style = Paint.Style.FILL
    }
}
