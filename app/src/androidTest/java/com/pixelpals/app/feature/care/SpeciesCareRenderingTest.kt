package com.pixelpals.app.feature.care

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.care.scene.*
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.R
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

/** Offscreen Canvas only. No repository, preferences, selection or care effects are touched. */
@RunWith(AndroidJUnit4::class)
class SpeciesCareRenderingTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun statsNameIsLocalizedAndDoesNotClipAtLargeFontSizes(): Unit {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            for (language: String in listOf("es", "en")) for (scale: Float in listOf(1f, 1.6f)) {
                val configuration: Configuration = Configuration(context.resources.configuration).apply {
                    setLocale(Locale.forLanguageTag(language)); fontScale = scale
                }
                val localized = ContextThemeWrapper(context.createConfigurationContext(configuration), R.style.Theme_PixelPals)
                val root: View = LayoutInflater.from(localized).inflate(R.layout.activity_pet_selection, null)
                val button: Button = root.findViewById(R.id.btnOpenDashboard)
                assertEquals(if (language == "es") "Estadísticas de tu mascota" else "Pet Stats", button.text.toString())
                val width: Int = (280 * localized.resources.displayMetrics.density).toInt()
                button.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                button.layout(0, 0, width, button.measuredHeight)
                assertTrue(button.layout.height <= button.height - button.compoundPaddingTop - button.compoundPaddingBottom)
                repeat(button.lineCount) { assertEquals(0, button.layout.getEllipsisCount(it)) }
            }
        }
    }

    @Test fun impNeverPlaysTheMalformedFaceOrQuadrupedApproach(): Unit = runBlocking {
        val pack: CarePosePack = CarePoseLoader.load(context.assets, PetType.DIABLILLO)
        assertEquals(3, pack.spec.getFrame(CareSceneAction.PET, 0))
        for (action: CareSceneAction in CareSceneAction.entries) for (ms: Long in 0L..6_000L step 50) {
            assertFalse(pack.spec.getFrame(action, ms) in setOf(0, 4, 8))
        }
        pack.bitmap.recycle()
    }

    @Test fun everyTrayHasDistinctFoodToyAndBedIllustrations(): Unit {
        val bitmap: Bitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
        val painter: CarePropPainter = CarePropPainter()
        val pixels: IntArray = IntArray(6_400)
        for (action: CareSceneAction in listOf(CareSceneAction.FEED, CareSceneAction.PLAY, CareSceneAction.REST)) {
            val hashes: MutableSet<Int> = mutableSetOf()
            for (pet: PetType in PetType.entries) {
                bitmap.eraseColor(Color.TRANSPARENT)
                painter.draw(Canvas(bitmap), action, 40f, 40f, 64f, pet = pet)
                bitmap.getPixels(pixels, 0, 80, 0, 0, 80, 80)
                assertTrue("$pet $action visible", pixels.count { Color.alpha(it) > 128 } > 70)
                assertTrue("$pet $action unique", hashes.add(pixels.contentHashCode()))
            }
        }
        bitmap.recycle()
    }

    @Test fun speciesHaveVisibleUnclippedDesktopFramesAndReviewSheets(): Unit = runBlocking {
        val frame: Bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
        val room: Bitmap = Bitmap.createBitmap(320, 220, Bitmap.Config.ARGB_8888)
        val roomPixels: IntArray = IntArray(320 * 220)
        val pixels: IntArray = IntArray(320 * 320)
        val renderer: SpeciesCareRenderer = SpeciesCareRenderer()
        val roomRenderer: CareSceneRenderer = CareSceneRenderer()
        for (pet: PetType in PetType.entries.filter { it != PetType.CORGI }) {
            val pack: CarePosePack = CarePoseLoader.load(context.assets, pet)
            val review: Bitmap = Bitmap.createBitmap(960, 1920, Bitmap.Config.ARGB_8888)
            review.eraseColor(Color.rgb(242, 237, 247))
            val canvas: Canvas = Canvas(review)
            val label: Paint = Paint().apply { color = Color.DKGRAY; textSize = 15f }
            for (action: CareSceneAction in CareSceneAction.entries) {
                val scene: CareSceneController = CareSceneController(action, CareSceneMode.AUTOMATIC, pack.spec.timings.getValue(action))
                for (step: Int in 0..20) {
                    if (step > 0) scene.advance(scene.timing.durationMs / 20)
                    for (reduced: Boolean in listOf(false, true)) {
                        frame.eraseColor(Color.TRANSPARENT)
                        renderer.draw(Canvas(frame), pack, scene, reduced, false, desktopSize = 160)
                        frame.getPixels(pixels, 0, 320, 0, 0, 320, 320)
                        assertTrue("$pet $action $step visible", pixels.count { Color.alpha(it) > 128 } > 700)
                        assertTrue("$pet $action $step top/bottom", (0 until 320).all { Color.alpha(pixels[it]) == 0 && Color.alpha(pixels[319 * 320 + it]) == 0 })
                        assertTrue("$pet $action $step sides", (0 until 320).all { Color.alpha(pixels[it * 320]) == 0 && Color.alpha(pixels[it * 320 + 319]) == 0 })
                    }
                    val target: CarePoint = roomRenderer.getTarget(pack, scene, 320f, 220f)
                    assertTrue("$pet $action valid target", target.x in 0f..1f && target.y in 0f..1f)
                    room.eraseColor(Color.TRANSPARENT)
                    roomRenderer.draw(Canvas(room), pack, scene, false, false)
                    room.getPixels(roomPixels, 0, 320, 0, 0, 320, 220)
                    assertTrue("$pet $action $step room top/bottom", (0 until 320).all { Color.alpha(roomPixels[it]) == 0 && Color.alpha(roomPixels[219 * 320 + it]) == 0 })
                    assertTrue("$pet $action $step room sides", (0 until 220).all { Color.alpha(roomPixels[it * 320]) == 0 && Color.alpha(roomPixels[it * 320 + 319]) == 0 })
                    if (step in listOf(0, 7, 15)) {
                        frame.eraseColor(Color.TRANSPARENT)
                        renderer.draw(Canvas(frame), pack, scene, false, false, desktopSize = 190)
                        val column: Int = listOf(0, 7, 15).indexOf(step)
                        val destination: Rect = Rect(column * 320, action.ordinal * 320, column * 320 + 320, action.ordinal * 320 + 320)
                        canvas.drawBitmap(frame, null, destination, null)
                        canvas.drawText("${action.name} ${step * 5}%", destination.left + 8f, destination.top + 18f, label)
                    }
                }
            }
            val directory: File = requireNotNull(context.getExternalFilesDir("care-review"))
            File(directory, "species-${pet.name.lowercase()}.png").outputStream().use { review.compress(Bitmap.CompressFormat.PNG, 100, it) }
            review.recycle(); pack.bitmap.recycle()
        }
        frame.recycle(); room.recycle()
    }
}
