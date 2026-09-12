package com.pixelpals.app.feature.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.care.scene.CareSceneAction
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.feature.care.CarePropPainter
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exports a side-by-side home decoration/care-food sheet for visual review. */
@RunWith(AndroidJUnit4::class)
class HomeFoodArtworkReviewTest {
    @Test
    fun exportAllSpeciesHomeFoodComparisons(): Unit {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val variants: List<Pair<PetType, String>> = PetType.entries.map { it to "ceramic_bowl" } +
            listOf("ceramic_bowl", "wood_bowl", "flower_bowl", "moon_bowl").map { PetType.TELA to it }
        val rowWidth = 520
        val rowHeight = 150
        val sheet = Bitmap.createBitmap(rowWidth, rowHeight * variants.size, Bitmap.Config.ARGB_8888)
        val painter = HomeScenePainter()
        val carePainter = CarePropPainter()
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(55, 52, 65)
            textSize = 20f
        }
        try {
            val canvas = Canvas(sheet)
            canvas.drawColor(Color.WHITE)
            variants.forEachIndexed { index, (pet, decorationId) ->
                val top = index * rowHeight.toFloat()
                labelPaint.textSize = 18f
                canvas.drawText("${pet.name} / $decorationId", 12f, top + 22f, labelPaint)
                labelPaint.textSize = 14f
                labelPaint.color = Color.DKGRAY
                canvas.drawText("home", 12f, top + 43f, labelPaint)
                canvas.drawText("care FEED", 270f, top + 43f, labelPaint)
                labelPaint.color = Color.rgb(55, 52, 65)

                val decoration = requireNotNull(DecorationCatalog.find(decorationId))
                painter.drawObject(canvas, decoration, RectF(20f, top + 47f, 140f, top + 147f), pet = pet)
                carePainter.draw(canvas, CareSceneAction.FEED, 395f, top + 96f, 70f, pet = pet)
                labelPaint.color = 0xFFE7E2EE.toInt()
                canvas.drawRect(250f, top + 40f, 251f, top + rowHeight, labelPaint)
                labelPaint.color = Color.rgb(55, 52, 65)
            }
            val outputDirectory = requireNotNull(context.getExternalFilesDir(null)).resolve("food-review")
            outputDirectory.mkdirs()
            val output = outputDirectory.resolve("home-food-comparisons.png")
            output.outputStream().use { stream ->
                assertTrue("PNG export failed", sheet.compress(Bitmap.CompressFormat.PNG, 100, stream))
            }
            assertTrue("Missing food artwork review at ${output.absolutePath}", output.isFile && output.length() > 0L)
        } finally {
            sheet.recycle()
        }
    }
}
