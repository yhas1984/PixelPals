package com.pixelpals.app.feature.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF

/** Measures once when loading. Transparent atlas padding never changes the actor's floor contact. */
internal class HomeSpriteFrames(frames: List<Pair<Bitmap, Rect>>) {
    private data class Frame(val bitmap: Bitmap, val visible: Rect)
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
        Frame(bitmap, Rect(cell.left + left, cell.top + top, cell.left + right + 1, cell.top + bottom + 1))
    }
    private val extent = this.frames.maxOf { maxOf(it.visible.width(), it.visible.height()) }.toFloat()
    private val destination = RectF()

    fun draw(canvas: Canvas, paint: Paint, target: RectF, index: Int): Unit {
        val frame = frames[index]
        val scale = target.width() * .8f / extent
        val width = frame.visible.width() * scale
        val height = frame.visible.height() * scale
        val ground = target.bottom - target.height() * .04f
        destination.set(target.centerX() - width / 2, ground - height, target.centerX() + width / 2, ground)
        canvas.drawBitmap(frame.bitmap, frame.visible, destination, paint)
    }
}
