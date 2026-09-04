package com.pixelpals.app.feature.care

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.care.scene.CarePoint
import com.pixelpals.app.core.care.scene.CareSceneAction
import com.pixelpals.app.core.care.scene.CareSceneController
import com.pixelpals.app.core.care.scene.CareSceneMode
import com.pixelpals.app.core.care.scene.CareSceneTiming
import com.pixelpals.app.core.care.scene.ImpBalloonPlayMotion
import com.pixelpals.app.core.domain.PetType
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Safe visual checks: fake scene controller, offscreen rendering and no repository access. */
@RunWith(AndroidJUnit4::class)
class ImpBalloonPlayTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun balloonsTridentAndThreePopsRenderInsideRoomAndDesktop(): Unit = runBlocking {
        val pack: CarePosePack = CarePoseLoader.load(context.assets, PetType.DIABLILLO)
        val renderer: SpeciesCareRenderer = SpeciesCareRenderer()
        val frame: Bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
        val roomFrame: Bitmap = Bitmap.createBitmap(320, 220, Bitmap.Config.ARGB_8888)
        val sheet: Bitmap = Bitmap.createBitmap(2560, 360, Bitmap.Config.ARGB_8888)
        val sheetCanvas: Canvas = Canvas(sheet)
        val label: Paint = Paint().apply { color = Color.DKGRAY; textSize = 14f }
        val times: List<Long> = listOf(0L, 900L, 1200L, 2200L, 2400L, 3300L, 3700L, 4200L)
        try {
            sheet.eraseColor(Color.rgb(242, 237, 247))
            for ((column: Int, elapsed: Long) in times.withIndex()) {
                val scene: CareSceneController = CareSceneController(
                    CareSceneAction.PLAY,
                    CareSceneMode.AUTOMATIC,
                    pack.spec.timings.getValue(CareSceneAction.PLAY),
                )
                scene.advance(elapsed)
                frame.eraseColor(Color.TRANSPARENT)
                renderer.draw(Canvas(frame), pack, scene, reduced = false, gentle = false, desktopSize = 180)
                assertBounds(frame, "$elapsed ms")
                roomFrame.eraseColor(Color.TRANSPARENT)
                renderer.draw(Canvas(roomFrame), pack, scene, reduced = false, gentle = false)
                assertBounds(roomFrame, "room $elapsed ms")
                sheetCanvas.drawBitmap(frame, column * 320f, 20f, null)
                sheetCanvas.drawText("$elapsed ms", column * 320f + 8f, 18f, label)
            }
            val directory: File = requireNotNull(context.getExternalFilesDir("care-review"))
            File(directory, "imp-balloon-trident-play.png").outputStream().use {
                sheet.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally {
            frame.recycle()
            roomFrame.recycle()
            sheet.recycle()
            pack.bitmap.recycle()
        }
    }

    @Test
    fun metadataAndReducedMotionKeepTheGameReadable(): Unit = runBlocking {
        val pack: CarePosePack = CarePoseLoader.load(context.assets, PetType.DIABLILLO)
        try {
            assertEquals(
                CareSceneTiming(ImpBalloonPlayMotion.DURATION_MS, ImpBalloonPlayMotion.COMPLETION_MS),
                pack.spec.timings.getValue(CareSceneAction.PLAY),
            )
            val renderer: SpeciesCareRenderer = SpeciesCareRenderer()
            val frame: Bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
            try {
                for (elapsed: Long in 0L..ImpBalloonPlayMotion.DURATION_MS step 40L) {
                    val scene: CareSceneController = CareSceneController(
                        CareSceneAction.PLAY,
                        CareSceneMode.AUTOMATIC,
                        pack.spec.timings.getValue(CareSceneAction.PLAY),
                    )
                    scene.advance(elapsed)
                    assertEquals(5, pack.spec.getFrame(CareSceneAction.PLAY, elapsed))
                    frame.eraseColor(Color.TRANSPARENT)
                    renderer.draw(Canvas(frame), pack, scene, reduced = true, gentle = false, desktopSize = 180)
                    assertBounds(frame, "reduced $elapsed ms")
                }
            } finally {
                frame.recycle()
            }
        } finally {
            pack.bitmap.recycle()
        }
    }

    @Test
    fun tridentShaftAlwaysCrossesTheGripPoint(): Unit {
        val grip: CarePoint = CarePoint(100f, 120f)
        val bitmap: Bitmap = Bitmap.createBitmap(260, 220, Bitmap.Config.ARGB_8888)
        try {
            for (progress: Float in listOf(0f, .28f, .54f, .79f, .9f, 1f)) {
                bitmap.eraseColor(Color.TRANSPARENT)
                ImpBalloonPlayPainter().draw(Canvas(bitmap), grip, 120f, ImpBalloonPlayMotion.sample(progress, false))
                assertEquals("grip at $progress", Color.rgb(122, 61, 42), bitmap.getPixel(grip.x.toInt(), grip.y.toInt()))
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun assertBounds(bitmap: Bitmap, description: String): Unit {
        val pixels: IntArray = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        assertTrue("$description visible", pixels.count { Color.alpha(it) > 128 } > 700)
        assertTrue("$description top/bottom", (0 until bitmap.width).all { x: Int ->
            Color.alpha(pixels[x]) == 0 && Color.alpha(pixels[(bitmap.height - 1) * bitmap.width + x]) == 0
        })
        assertTrue("$description sides", (0 until bitmap.height).all { y: Int ->
            Color.alpha(pixels[y * bitmap.width]) == 0 && Color.alpha(pixels[y * bitmap.width + bitmap.width - 1]) == 0
        })
    }
}
