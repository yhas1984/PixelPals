package com.pixelpals.app.feature.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF

/** Measures once when loading. Transparent atlas padding never changes the actor's floor contact. */
internal class HomeSpriteFrames(frames: List<Pair<Bitmap, Rect>>, private val anchor: android.graphics.PointF? = null,
    private val cellScale: Float? = null, private val frameScales: List<Float>? = null) {
    private data class Frame(val bitmap: Bitmap, val visible: Rect, val cell: Rect)
    private val frames: List<Frame> = frames.map { (bitmap, cell) ->
        val pixels = IntArray(cell.width() * cell.height())
        bitmap.getPixels(pixels, 0, cell.width(), cell.left, cell.top, cell.width(), cell.height())
        var left = cell.width(); var top = cell.height(); var right = -1; var bottom = -1
        for (y in 0 until cell.height()) for (x in 0 until cell.width()) {
            if ((pixels[y * cell.width() + x] ushr 24) >= 16) {
                left = minOf(left, x); right = maxOf(right, x)
                top = minOf(top, y); bottom = maxOf(bottom, y)
            }
        }
        require(right >= left && bottom >= top) { "Home frame is transparent" }
        Frame(bitmap, Rect(cell.left + left, cell.top + top, cell.left + right + 1, cell.top + bottom + 1), Rect(cell))
    }
    private val extent = this.frames.maxOf { maxOf(it.visible.width(), it.visible.height()) }.toFloat()
    private val destination = RectF()

    fun draw(canvas: Canvas, paint: Paint, target: RectF, index: Int): Unit {
        val frame = frames[index]
        bounds(target, index, destination)
        canvas.drawBitmap(frame.bitmap, if (anchor != null) frame.cell else frame.visible, destination, paint)
    }

    fun bounds(target: RectF, index: Int, output: RectF): Unit {
        val frame = frames[index]
        val scale = (cellScale?.let { target.width() * it / frame.cell.width() }
            ?: (target.width() * .8f / extent)) * (frameScales?.get(index) ?: 1f)
        val width = frame.visible.width() * scale
        val height = frame.visible.height() * scale
        val ground = target.bottom - target.height() * .04f
        output.set(target.centerX() - width / 2, ground - height, target.centerX() + width / 2, ground)
        if (anchor != null) {
            output.set(target.centerX() - anchor.x * scale, ground - anchor.y * scale,
                target.centerX() + (frame.cell.width() - anchor.x) * scale, ground + (frame.cell.height() - anchor.y) * scale)
        }
    }
}
