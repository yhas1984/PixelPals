package com.pixelpals.app.feature.treasure

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

class TreasureSymbolRenderingTest {
    @Test
    fun everyTreasureHasGlyphOrStableDrawingOnDevice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.getExternalFilesDir(null), "treasure-symbol-review").apply { mkdirs() }
        val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 64f
            textAlign = Paint.Align.CENTER
        }
        TreasureCatalog.all.forEachIndexed { index, definition ->
            val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val bounds = RectF(16f, 16f, 112f, 112f)
            val drawn = TreasureSymbol.draw(canvas, definition.emoji, bounds)
            if (!drawn) {
                assertTrue("${definition.emoji} must have a platform glyph when no vector is supplied", fallbackPaint.hasGlyph(definition.emoji))
                canvas.drawText(definition.emoji, 64f, 80f, fallbackPaint)
            }
            var visible = false
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                if (bitmap.getPixel(x, y) ushr 24 != 0) {
                    visible = true
                    if (drawn) assertTrue("${definition.emoji} must stay inside its drawing bounds", bounds.contains(x.toFloat(), y.toFloat()))
                }
            }
            assertTrue("${definition.emoji} must render visible pixels", visible)
            FileOutputStream(File(output, "%02d-%s.png".format(index, definition.id))).use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
        assertTrue("review directory must contain one PNG per treasure", output.listFiles()?.count { it.extension == "png" } == TreasureCatalog.all.size)
    }
}
