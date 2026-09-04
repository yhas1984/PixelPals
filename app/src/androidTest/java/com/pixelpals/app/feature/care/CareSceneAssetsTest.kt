package com.pixelpals.app.feature.care

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.care.scene.*
import com.pixelpals.app.core.domain.PetType
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Pure asset/Canvas checks: never opens or clears the installed database. */
@RunWith(AndroidJUnit4::class)
class CareSceneAssetsTest {
    private val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets

    @Test fun allPetsRenderEveryActionInBothModesWithOneCompletion(): Unit = runBlocking {
        assertEquals(15, PetType.entries.size)
        val output: Bitmap = Bitmap.createBitmap(640, 440, Bitmap.Config.ARGB_8888)
        val renderer: CareSceneRenderer = CareSceneRenderer()
        for (pet: PetType in PetType.entries) {
            assertTrue(CarePoseLoader.isAvailable(assets, pet))
            val pack: CarePosePack = CarePoseLoader.load(assets, pet)
            assertTrue(pack.bitmap.hasAlpha())
            assertEquals(6 * 1024 * 1024, pack.bitmap.allocationByteCount)
            val review: Bitmap = Bitmap.createBitmap(960, 440, Bitmap.Config.ARGB_8888)
            review.eraseColor(Color.rgb(240, 234, 248))
            val reviewCanvas: Canvas = Canvas(review)
            for (action: CareSceneAction in CareSceneAction.entries) {
                for (mode: CareSceneMode in CareSceneMode.entries) {
                    val scene: CareSceneController = CareSceneController(action, mode, pack.spec.timings.getValue(action))
                    var completions: Int = 0
                    repeat(150) { step ->
                        if (mode == CareSceneMode.MANUAL && !scene.hasContact) {
                            val target: CarePoint = renderer.getTarget(pack, scene, 640f, 440f)
                            scene.movePointer(target.copy(x = target.x + if (step % 2 == 0) .02f else -.02f), target,
                                action != CareSceneAction.PLAY)
                        }
                        if (scene.advance(100L)) completions++
                        if (step % 5 == 0) {
                            output.eraseColor(Color.TRANSPARENT)
                            renderer.draw(Canvas(output), pack, scene, reducedMotion = false, gentle = false)
                            if (step == 0) {
                                val pixels: IntArray = IntArray(output.width * output.height)
                                output.getPixels(pixels, 0, output.width, 0, 0, output.width, output.height)
                                assertTrue("$pet $action has visible art", pixels.count { Color.alpha(it) > 128 } > 2_000)
                            }
                            if (mode == CareSceneMode.AUTOMATIC && step == 20) {
                                val x: Int = action.ordinal % 3 * 320
                                val y: Int = action.ordinal / 3 * 220
                                reviewCanvas.drawBitmap(output, null, Rect(x, y, x + 320, y + 220), Paint())
                                reviewCanvas.drawText(action.name, x + 8f, y + 18f, Paint().apply { color = Color.DKGRAY; textSize = 14f })
                            }
                            renderer.draw(Canvas(output), pack, scene, reducedMotion = true, gentle = true)
                        }
                    }
                    assertEquals("$pet $action $mode", 1, completions)
                    assertTrue(scene.isComplete)
                }
            }
            val directory: File = requireNotNull(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("care-review"))
            File(directory, "${pet.name.lowercase()}.png").outputStream().use { review.compress(Bitmap.CompressFormat.PNG, 100, it) }
            review.recycle()
            // No view/render thread owns these software-only test bitmaps.
            pack.bitmap.recycle()
        }
        output.recycle()
    }

    @Test fun malformedAnchorsAndMissingClipsAreRejected(): Unit {
        val original: String = assets.open("pets/corgi/care_v1.json").bufferedReader().use { it.readText() }
        val badAnchor: JSONObject = JSONObject(original)
        badAnchor.getJSONArray("anchors").getJSONObject(0).getJSONArray("mouth").put(0, 2.0)
        assertThrows(IllegalArgumentException::class.java) { CarePoseSpec.parse(badAnchor) }
        val missing: JSONObject = JSONObject(original).put("clips", org.json.JSONArray())
        assertThrows(IllegalArgumentException::class.java) { CarePoseSpec.parse(missing) }
    }
}
