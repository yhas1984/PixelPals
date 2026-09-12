package com.pixelpals.app.feature.treasure

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.PixelFormat

/** Stable vector substitutes for treasure glyphs missing from older Android fonts. */
object TreasureSymbol {
    private val supported = setOf("🪙", "🦴", "🧩", "🪶")
    private val drawLock = Any()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    fun hasDrawing(symbol: String): Boolean = symbol in supported

    fun draw(canvas: Canvas, symbol: String, bounds: RectF, alpha: Int = 255): Boolean {
        if (!hasDrawing(symbol) || bounds.isEmpty) return false
        synchronized(drawLock) {
            canvas.save()
            canvas.clipRect(bounds)
            canvas.translate(bounds.left, bounds.top)
            canvas.scale(bounds.width() / 100f, bounds.height() / 100f)
            try {
                when (symbol) {
                    "🪙" -> drawCoin(canvas, paint, path, alpha)
                    "🦴" -> drawBone(canvas, paint, alpha)
                    "🧩" -> drawPuzzle(canvas, paint, path, alpha)
                    "🪶" -> drawFeather(canvas, paint, path, alpha)
                }
            } finally {
                canvas.restore()
            }
        }
        return true
    }

    private fun fill(paint: Paint, color: Int, alpha: Int) {
        paint.reset(); paint.isAntiAlias = true; paint.style = Paint.Style.FILL
        paint.color = color
        paint.alpha = (color ushr 24) * alpha.coerceIn(0, 255) / 255
    }

    private fun drawCoin(canvas: Canvas, paint: Paint, path: Path, alpha: Int) {
        fill(paint, 0xFFE5A928.toInt(), alpha); canvas.drawOval(10f, 5f, 90f, 95f, paint)
        fill(paint, 0xFFFFD86A.toInt(), alpha); canvas.drawOval(18f, 12f, 84f, 86f, paint)
        fill(paint, 0xFFB97818.toInt(), alpha)
        path.reset(); path.moveTo(50f, 24f); path.lineTo(55f, 42f); path.lineTo(74f, 42f); path.lineTo(59f, 53f)
        path.lineTo(65f, 71f); path.lineTo(50f, 60f); path.lineTo(35f, 71f); path.lineTo(41f, 53f); path.lineTo(26f, 42f)
        path.lineTo(45f, 42f); path.close(); canvas.drawPath(path, paint)
        fill(paint, 0x66FFFFFF, alpha); canvas.drawOval(27f, 19f, 47f, 31f, paint)
    }

    private fun drawBone(canvas: Canvas, paint: Paint, alpha: Int) {
        fill(paint, 0xFFEDE2C7.toInt(), alpha)
        paint.strokeWidth = 22f; paint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(30f, 70f, 70f, 30f, paint)
        paint.strokeWidth = 27f
        canvas.drawCircle(24f, 76f, 13f, paint); canvas.drawCircle(39f, 84f, 11f, paint)
        canvas.drawCircle(61f, 16f, 11f, paint); canvas.drawCircle(76f, 24f, 13f, paint)
        fill(paint, 0x55FFFFFF, alpha); canvas.drawCircle(25f, 72f, 4f, paint); canvas.drawCircle(69f, 20f, 4f, paint)
    }

    private fun drawPuzzle(canvas: Canvas, paint: Paint, path: Path, alpha: Int) {
        fill(paint, 0xFFB9A7D9.toInt(), alpha)
        path.reset(); path.moveTo(19f, 18f); path.lineTo(43f, 18f); path.cubicTo(41f, 8f, 58f, 8f, 56f, 18f)
        path.lineTo(81f, 18f); path.lineTo(81f, 42f); path.cubicTo(93f, 39f, 93f, 57f, 81f, 54f)
        path.lineTo(81f, 82f); path.lineTo(56f, 82f); path.cubicTo(59f, 94f, 40f, 94f, 43f, 82f)
        path.lineTo(19f, 82f); path.lineTo(19f, 57f); path.cubicTo(7f, 60f, 7f, 40f, 19f, 43f); path.close(); canvas.drawPath(path, paint)
        fill(paint, 0x55FFFFFF, alpha); canvas.drawCircle(34f, 32f, 6f, paint)
    }

    private fun drawFeather(canvas: Canvas, paint: Paint, path: Path, alpha: Int) {
        fill(paint, 0xFF48B8B0.toInt(), alpha)
        path.reset(); path.moveTo(27f, 87f); path.cubicTo(35f, 55f, 47f, 22f, 82f, 10f)
        path.cubicTo(85f, 48f, 65f, 74f, 27f, 87f); path.close(); canvas.drawPath(path, paint)
        paint.reset(); paint.isAntiAlias = true; paint.color = 0xFF207E83.toInt(); paint.alpha = alpha; paint.strokeWidth = 5f
        canvas.drawLine(20f, 92f, 73f, 17f, paint)
        paint.strokeWidth = 3f
        canvas.drawLine(40f, 65f, 30f, 58f, paint); canvas.drawLine(48f, 54f, 37f, 45f, paint); canvas.drawLine(57f, 42f, 47f, 34f, paint)
    }
}

/** Drawable used by ImageSpan; its bounds are set from the TextView text size. */
class TreasureSymbolDrawable(private val symbol: String, private val size: Int) : Drawable() {
    private var drawableAlpha: Int = 255
    override fun draw(canvas: Canvas) { TreasureSymbol.draw(canvas, symbol, RectF(bounds), drawableAlpha) }
    override fun setAlpha(alpha: Int) { drawableAlpha = alpha.coerceIn(0, 255); invalidateSelf() }
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) = Unit
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth(): Int = size
    override fun getIntrinsicHeight(): Int = size
}
