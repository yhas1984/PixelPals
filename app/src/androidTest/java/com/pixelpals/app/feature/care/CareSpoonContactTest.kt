package com.pixelpals.app.feature.care

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.care.scene.*
import com.pixelpals.app.core.domain.PetType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Actual room pixels, without repositories or progress changes. */
@RunWith(AndroidJUnit4::class)
class CareSpoonContactTest {
    @Test fun medicineContentsMeetEveryMouthWithNormalAndReducedMotion(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val renderer: CareSceneRenderer = CareSceneRenderer()
        val medicine: Int = Color.rgb(232, 187, 119)
        for (pet: PetType in PetType.entries) {
            val pack: CarePosePack = CarePoseLoader.load(context.assets, pet)
            try {
                for ((width, height) in listOf(320 to 220, 560 to 320)) {
                    val bitmap: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    try {
                        for (reduced: Boolean in listOf(false, true)) {
                            val scene = CareSceneController(CareSceneAction.MEDICINE, CareSceneMode.AUTOMATIC,
                                pack.spec.timings.getValue(CareSceneAction.MEDICINE))
                            scene.advance(scene.timing.durationMs / 4)
                            bitmap.eraseColor(Color.TRANSPARENT)
                            renderer.draw(Canvas(bitmap), pack, scene, reduced, false)
                            val mouth: CarePoint = renderer.getTarget(pack, scene, width.toFloat(), height.toFloat(),
                                if (reduced) 0L else scene.animationMs, reduced)
                            val pixels: IntArray = IntArray(width * height)
                            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                            val liquid: List<Int> = pixels.indices.filter { pixels[it] == medicine }
                            assertTrue("$pet medicine must be visible", liquid.size > 10)
                            assertEquals("$pet reduced=$reduced spoon contact X", mouth.x * width,
                                liquid.map { it % width + .5f }.average().toFloat(), 2.5f)
                            assertEquals("$pet reduced=$reduced spoon contact Y", mouth.y * height,
                                liquid.map { it / width + .5f }.average().toFloat(), 2.5f)
                            scene.advance(scene.timing.durationMs * 2 / 5)
                            bitmap.eraseColor(Color.TRANSPARENT)
                            renderer.draw(Canvas(bitmap), pack, scene, reduced, false)
                            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                            assertTrue("$pet medicine should be consumed", pixels.count { it == medicine } < liquid.size / 2)
                        }
                    } finally { bitmap.recycle() }
                }
            } finally { pack.bitmap.recycle() }
        }
    }
}
