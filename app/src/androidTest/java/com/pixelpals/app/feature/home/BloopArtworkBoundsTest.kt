package com.pixelpals.app.feature.home

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.R
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BloopArtworkBoundsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun cleanedLegacyPosesKeepOriginalBodyCamera() {
        val originals = (1..3).map { index ->
            instrumentation.context.assets.open("bloop-original/fantasma_$index.png").use {
                requireNotNull(BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 2 }))
            }
        }
        val cleaned = listOf(R.drawable.fantasma_1, R.drawable.fantasma_2, R.drawable.fantasma_3).map {
            BitmapFactory.decodeResource(instrumentation.targetContext.resources, it,
                BitmapFactory.Options().apply { inScaled = false; inSampleSize = 2 })
        }
        try {
            val cells = originals.map { Rect(0, 0, it.width, it.height) }
            compare(originals.zip(cells), cleaned.zip(cells), BloopArtworkBounds.legacy(originals.first().width))
        } finally { (originals + cleaned).forEach(Bitmap::recycle) }
    }

    @Test fun cleanedCarePosesKeepOriginalBodyCameraAndDreamBubbles() {
        val original = instrumentation.context.assets.open("bloop-original/care_v1.png").use { requireNotNull(BitmapFactory.decodeStream(it)) }
        val cleaned = instrumentation.targetContext.assets.open("pets/bloop/care_v1.png").use { requireNotNull(BitmapFactory.decodeStream(it)) }
        try {
            val cells = (0 until 24).map { Rect(it % 4 * 256, it / 4 * 256, (it % 4 + 1) * 256, (it / 4 + 1) * 256) }
            compare(cells.map { original to it }, cells.map { cleaned to it }, BloopArtworkBounds.care(256))
            val dream = cells[19]
            for (y in dream.top until dream.bottom) for (x in dream.left until dream.right) {
                assertEquals("Dream frame changed at $x,$y", original.getPixel(x, y), cleaned.getPixel(x, y))
            }
        } finally { original.recycle(); cleaned.recycle() }
    }

    private fun visible(bitmap: Bitmap, cell: Rect): Rect {
        var left = cell.right; var top = cell.bottom; var right = cell.left - 1; var bottom = cell.top - 1
        for (y in cell.top until cell.bottom) for (x in cell.left until cell.right) {
            if (bitmap.getPixel(x, y) ushr 24 >= 16) {
                left = minOf(left, x); top = minOf(top, y); right = maxOf(right, x); bottom = maxOf(bottom, y)
            }
        }
        return Rect(left - cell.left, top - cell.top, right + 1 - cell.left, bottom + 1 - cell.top)
    }

    private fun compare(originals: List<Pair<Bitmap, Rect>>, cleaned: List<Pair<Bitmap, Rect>>, references: List<Rect>) {
        val source = HomeSpriteFrames(originals)
        val fixed = HomeSpriteFrames(cleaned, referenceBounds = references)
        val target = RectF(20f, 20f, 220f, 220f)
        val before = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888)
        val after = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        try {
            originals.forEachIndexed { index, (original, cell) ->
                val measured = visible(original, cell)
                val reference = references[index]
                val a = RectF(); val b = RectF()
                source.bounds(target, index, a); fixed.bounds(target, index, b)
                val sourceScale = a.width() / measured.width()
                val cleanScale = b.width() / reference.width()
                assertTrue("Frame $index changed body scale", abs(cleanScale / sourceScale - 1f) < .005f)
                // Track the same source point, not the changed outer alpha box.
                val centerX = cell.width() * .5f; val centerY = cell.height() * .5f
                assertEquals("Frame $index shifted body X", a.left + (centerX - measured.left) * sourceScale,
                    b.left + (centerX - reference.left) * cleanScale, 1f)
                assertEquals("Frame $index shifted body Y", a.top + (centerY - measured.top) * sourceScale,
                    b.top + (centerY - reference.top) * cleanScale, 1f)
                before.eraseColor(0); after.eraseColor(0)
                source.draw(Canvas(before), paint, target, index)
                fixed.draw(Canvas(after), paint, target, index)
                // The face is outside the edited wire/shadow region. Check its
                // drawn dark details, allowing Android's half-size decode rounding.
                var details = 0; var matches = 0
                for (sy in cell.height() * 3 / 10 until cell.height() * 7 / 10) {
                    for (sx in cell.width() * 9 / 20 until cell.width() * 4 / 5) {
                        val x = (a.left + (sx - measured.left) * sourceScale).toInt()
                        val y = (a.top + (sy - measured.top) * sourceScale).toInt()
                        if (x !in 1 until 239 || y !in 1 until 239) continue
                        val pixel = before.getPixel(x, y)
                        if (pixel ushr 24 < 200 || (pixel shr 16 and 255) > 160) continue
                        details++
                        if ((-1..1).any { dy -> (-1..1).any { dx ->
                            val other = after.getPixel(x + dx, y + dy)
                            other ushr 24 >= 200 && abs((pixel shr 16 and 255) - (other shr 16 and 255)) <= 30 &&
                                abs((pixel shr 8 and 255) - (other shr 8 and 255)) <= 30 && abs((pixel and 255) - (other and 255)) <= 30
                        } }) matches++
                    }
                }
                assertTrue("Frame $index has no rendered face detail", details > 0)
                assertTrue("Frame $index lost or moved face detail: $matches/$details", matches.toFloat() / details > .97f)
            }
        } finally { before.recycle(); after.recycle() }
    }
}
