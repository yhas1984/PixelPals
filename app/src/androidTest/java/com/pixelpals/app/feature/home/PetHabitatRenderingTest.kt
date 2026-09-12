package com.pixelpals.app.feature.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.File

class PetHabitatRenderingTest {
    @Test fun renderAllHabitatsInBothLightingStates() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for ((label, hour) in listOf("day" to 14, "night" to 23)) {
            val sheet = Bitmap.createBitmap(1200, 1670, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(sheet)
            canvas.drawColor(Color.rgb(249, 245, 236))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(40, 60, 77); textSize = 21f }
            val signatures = mutableSetOf<Int>()
            for ((index, pet) in PetType.entries.withIndex()) {
                val scene = Bitmap.createBitmap(400, 304, Bitmap.Config.ARGB_8888)
                try {
                    val stage = Canvas(scene); stage.scale(.4f, .4f)
                    HomeScenePainter().drawBackground(stage, HomeEnvironment.COZY, hour, pet)
                    assertNotEquals(0, scene.getPixel(200, 200) ushr 24)
                    val pixels = IntArray(400 * 304)
                    scene.getPixels(pixels, 0, 400, 0, 0, 400, 304)
                    signatures.add(pixels.contentHashCode())
                    val x = index % 3 * 400f; val y = index / 3 * 334f
                    canvas.drawBitmap(scene, x, y, null)
                    canvas.drawText(pet.name, x + 12f, y + 326f, paint)
                } finally { scene.recycle() }
            }
            assertEquals("Each species needs its own scene", 15, signatures.size)
            File(context.getExternalFilesDir(null), "habitats-$label.png").outputStream().use {
                sheet.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            sheet.recycle()
        }
    }
}
