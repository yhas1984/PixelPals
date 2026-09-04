package com.pixelpals.app.feature.care

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.R
import com.pixelpals.app.core.care.scene.*
import com.pixelpals.app.core.domain.PetType
import java.io.File
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Safe on the user's phone: offscreen rendering only, with no repository access. */
@RunWith(AndroidJUnit4::class)
class ImpCareRefinementTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun metadataKeepsTheFastBitesAndAddsTimeForFireAndGentlePetting(): Unit = runBlocking {
        val pack: CarePosePack = CarePoseLoader.load(context.assets, PetType.DIABLILLO)
        assertEquals(CareSceneTiming(3300L, 2100L), pack.spec.timings.getValue(CareSceneAction.FEED))
        assertEquals(CareSceneTiming(4200L, 3500L), pack.spec.timings.getValue(CareSceneAction.PET))
        assertEquals(CareSceneTiming(4200L, 3600L), pack.spec.timings.getValue(CareSceneAction.PLAY))
        for (elapsed: Long in 2160L..2910L step 30L) assertEquals(6, pack.spec.getFrame(CareSceneAction.FEED, elapsed))
        assertEquals(3, pack.spec.getFrame(CareSceneAction.FEED, 3000L))
        assertEquals(9, pack.spec.getFrame(CareSceneAction.REST, 5000L))
        pack.bitmap.recycle()
    }

    @Test fun wingsFireAndPettingAreUnclippedAndProduceReviewFrames(): Unit = runBlocking {
        val pack: CarePosePack = CarePoseLoader.load(context.assets, PetType.DIABLILLO)
        val renderer: CareSceneRenderer = CareSceneRenderer()
        val desktopRenderer: SpeciesCareRenderer = SpeciesCareRenderer()
        val frame: Bitmap = Bitmap.createBitmap(320, 220, Bitmap.Config.ARGB_8888)
        val desktop: Bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
        val sheet: Bitmap = Bitmap.createBitmap(2240, 720, Bitmap.Config.ARGB_8888)
        sheet.eraseColor(Color.rgb(242, 237, 247))
        val canvas: Canvas = Canvas(sheet)
        val label: Paint = Paint().apply { color = Color.DKGRAY; textSize = 14f }
        val reviewTimes: Map<CareSceneAction, List<Long>> = mapOf(
            CareSceneAction.FEED to listOf(0L, 600L, 1500L, 2100L, 2400L, 2800L, 3300L),
            CareSceneAction.PET to listOf(0L, 700L, 1400L, 2100L, 2800L, 3500L, 4200L),
            CareSceneAction.REST to listOf(0L, 500L, 1000L, 1500L, 2000L, 3500L, 5000L))
        reviewTimes.entries.forEachIndexed { row, (action, times) ->
            val timing: CareSceneTiming = pack.spec.timings.getValue(action)
            val samples: List<Long> = ((0L..timing.durationMs step 40L).toList() + times).distinct().sorted()
            for (elapsed: Long in samples) for (reduced: Boolean in listOf(false, true)) {
                val scene: CareSceneController = CareSceneController(action, CareSceneMode.AUTOMATIC, timing)
                scene.advance(elapsed)
                scene.advance(1L)
                frame.eraseColor(Color.TRANSPARENT)
                renderer.draw(Canvas(frame), pack, scene, reduced, false)
                assertFrameBounds(frame, "$action $elapsed reduced=$reduced room")
                desktop.eraseColor(Color.TRANSPARENT)
                desktopRenderer.draw(Canvas(desktop), pack, scene, reduced, false, desktopSize = 180)
                assertFrameBounds(desktop, "$action $elapsed reduced=$reduced desktop")
                if (!reduced && elapsed in times) {
                    val column: Int = times.indexOf(elapsed)
                    canvas.drawBitmap(frame, column * 320f, row * 240f + 20f, null)
                    canvas.drawText("${action.name} ${elapsed}ms", column * 320f + 6f, row * 240f + 17f, label)
                }
            }
        }
        val directory: File = requireNotNull(context.getExternalFilesDir("care-review"))
        File(directory, "imp-wings-fire-petting.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
        frame.recycle(); desktop.recycle(); sheet.recycle(); pack.bitmap.recycle()
    }

    @Test fun cancelledAndUnacceptedFeedingNeverRenderTheFirePuff(): Unit = runBlocking {
        val pack: CarePosePack = CarePoseLoader.load(context.assets, PetType.DIABLILLO)
        val renderer: CareSceneRenderer = CareSceneRenderer()
        val frame: Bitmap = Bitmap.createBitmap(320, 220, Bitmap.Config.ARGB_8888)
        val canvas: Canvas = Canvas(frame)
        val scene: CareSceneController = CareSceneController(CareSceneAction.FEED, CareSceneMode.AUTOMATIC,
            pack.spec.timings.getValue(CareSceneAction.FEED))
        scene.advance(2400L)
        renderer.draw(canvas, pack, scene, false, false)
        val withFire: IntArray = readPixels(frame)
        scene.cancel()
        frame.eraseColor(Color.TRANSPARENT)
        renderer.draw(canvas, pack, scene, false, false)
        val cancelled: IntArray = readPixels(frame)
        assertTrue(withFire.indices.count { withFire[it] != cancelled[it] } > 100)
        val manual: CareSceneController = CareSceneController(CareSceneAction.FEED, CareSceneMode.MANUAL, scene.timing)
        manual.advance(2400L)
        assertEquals(0L, manual.animationMs)
        assertEquals(ImpFirePose(), ImpCareMotion.sampleFire(manual.animationMs, false))
        frame.recycle(); pack.bitmap.recycle()
    }

    @Test fun wingHintIsLocalizedInsteadOfAskingForABed(): Unit {
        for (language: String in listOf("es", "en")) {
            val configuration: Configuration = Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(language))
            }
            val text: String = context.createConfigurationContext(configuration).getString(R.string.care_scene_manual_imp_rest)
            assertTrue(text.contains(if (language == "es") "alas" else "wings"))
        }
    }

    private fun assertFrameBounds(bitmap: Bitmap, description: String): Unit {
        val pixels: IntArray = readPixels(bitmap)
        assertTrue("$description visible", pixels.count { Color.alpha(it) > 128 } > 700)
        assertTrue("$description top/bottom", (0 until bitmap.width).all {
            Color.alpha(pixels[it]) == 0 && Color.alpha(pixels[(bitmap.height - 1) * bitmap.width + it]) == 0 })
        assertTrue("$description sides", (0 until bitmap.height).all {
            Color.alpha(pixels[it * bitmap.width]) == 0 && Color.alpha(pixels[it * bitmap.width + bitmap.width - 1]) == 0 })
    }

    private fun readPixels(bitmap: Bitmap): IntArray = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }
}
