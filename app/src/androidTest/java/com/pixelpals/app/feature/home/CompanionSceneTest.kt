package com.pixelpals.app.feature.home

import android.content.Context
import android.graphics.*
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CompanionSceneTest {
    @Test fun allFifteenCompanionsHaveVisibleHomeArtwork(): Unit {
        val context: Context = ApplicationProvider.getApplicationContext()
        val sheet: Bitmap = Bitmap.createBitmap(1500, 2100, Bitmap.Config.ARGB_8888)
        val sheetCanvas: Canvas = Canvas(sheet)
        sheetCanvas.drawColor(HomeUi.cream)
        val label: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = HomeUi.ink; textSize = 22f }
        for ((index, pet) in PetType.entries.withIndex()) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val scene: HomeSceneView = HomeSceneView(context)
                scene.environment = HomeEnvironment.entries[index % 3]
                runBlocking { scene.loadPet(pet) }
                scene.measure(View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(380, View.MeasureSpec.EXACTLY))
                scene.layout(0, 0, 500, 380)
                val withPet: Bitmap = Bitmap.createBitmap(500, 380, Bitmap.Config.ARGB_8888)
                scene.draw(Canvas(withPet))
                scene.showPet = false
                val withoutPet: Bitmap = Bitmap.createBitmap(500, 380, Bitmap.Config.ARGB_8888)
                scene.draw(Canvas(withoutPet))
                var different: Int = 0
                for (y: Int in 140 until 330) for (x: Int in 120 until 380) if (withPet.getPixel(x, y) != withoutPet.getPixel(x, y)) different++
                assertTrue("Missing actor for $pet", different > 500)
                val left: Float = (index % 3 * 500).toFloat()
                val top: Float = (index / 3 * 420).toFloat()
                sheetCanvas.drawBitmap(withPet, left, top, null)
                sheetCanvas.drawText(pet.name, left + 16, top + 408, label)
                withPet.recycle(); withoutPet.recycle()
            }
        }
        File(context.getExternalFilesDir(null), "companion-catalog.png").outputStream().use {
            sheet.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        sheet.recycle()
    }
}
