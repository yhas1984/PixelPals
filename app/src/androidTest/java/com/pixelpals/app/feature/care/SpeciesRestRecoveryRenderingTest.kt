package com.pixelpals.app.feature.care

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.care.scene.*
import com.pixelpals.app.core.domain.PetType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SpeciesRestRecoveryRenderingTest {
    @Test fun restEndsAwakeAfterOneRewardAndExportsDesktopSequence(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val times: List<Long> = listOf(3750L, 4000L, 4250L, 4500L, 4750L, 5000L)
        val frameField = SpeciesCareRenderer::class.java.getDeclaredField("frame").apply { isAccessible = true }
        val directory: File = File(context.cacheDir, "rest-recovery").apply { mkdirs() }
        for (pet: PetType in PetType.entries.filter { it != PetType.CORGI }) {
            val expected: List<Int> = when (pet) {
                PetType.DIABLILLO -> listOf(9, 9, 11, 11, 15, 15)
                PetType.TELA -> listOf(19, 18, 17, 16, 15, 15)
                PetType.LUMI -> listOf(19, 18, 17, 10, 1, 1)
                PetType.JELLY -> listOf(28, 27, 26, 25, 24, 20)
                else -> listOf(19, 18, 17, 16, 12, 12)
            }
            val pack: CarePosePack = CarePoseLoader.load(context.assets, pet)
            val sheet: Bitmap = Bitmap.createBitmap(1920, 344, Bitmap.Config.ARGB_8888)
            val tile: Bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
            try {
                val renderer: SpeciesCareRenderer = SpeciesCareRenderer()
                for (reduced: Boolean in listOf(false, true)) {
                    val scene: CareSceneController = CareSceneController(CareSceneAction.REST, CareSceneMode.AUTOMATIC, pack.spec.timings.getValue(CareSceneAction.REST))
                    var elapsed: Long = 0L
                    var rewards: Int = 0
                    for ((index: Int, time: Long) in times.withIndex()) {
                        while (elapsed < time) {
                            val delta: Long = minOf(50L, time - elapsed)
                            if (scene.advance(delta)) rewards++
                            elapsed += delta
                        }
                        tile.eraseColor(Color.TRANSPARENT)
                        renderer.draw(Canvas(tile), pack, scene, reduced, false, desktopSize = 160)
                        val expectedFrame: Int = if (reduced && time < 5000L) {
                            // Ginger's reduced mode holds the native closed-eye curl;
                            // frame 16 is now the awake entry to the normal transition.
                            when (pet) { PetType.DIABLILLO -> 9; PetType.JELLY -> 29; PetType.GINGER -> 19; else -> 16 }
                        } else expected[index]
                        assertEquals("$pet at $time reduced=$reduced", expectedFrame, frameField.getInt(renderer))
                        if (!reduced) {
                            val canvas: Canvas = Canvas(sheet)
                            canvas.drawBitmap(tile, index * 320f, 24f, null)
                            canvas.drawText("$pet $time ms", index * 320f + 8f, 18f, Paint().apply { color = Color.DKGRAY; textSize = 16f })
                        }
                    }
                    assertTrue(scene.isComplete)
                    assertEquals(1, rewards)
                }
                File(directory, "${pet.name.lowercase()}.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally { sheet.recycle(); tile.recycle(); pack.bitmap.recycle() }
        }
    }

    @Test fun settledWingsBecomeNarrowerWithoutChangingTheNextDrawing() {
        val painter: ImpWingPainter = ImpWingPainter()
        val spread: Bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
        val settled: Bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
        val repeated: Bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
        try {
            val shoulder: CarePoint = CarePoint(160f, 120f)
            painter.draw(Canvas(spread), shoulder, 160f, 0f, false)
            painter.draw(Canvas(settled), shoulder, 160f, 0f, false, 1f)
            painter.draw(Canvas(repeated), shoulder, 160f, 0f, false)
            assertTrue("A cached painter must reset the resting geometry", spread.sameAs(repeated))
            fun occupiedColumns(bitmap: Bitmap): List<Int> = (0 until bitmap.width).filter { x ->
                (0 until bitmap.height).any { y -> Color.alpha(bitmap.getPixel(x, y)) > 128 }
            }
            val spreadColumns: List<Int> = occupiedColumns(spread)
            val restColumns: List<Int> = occupiedColumns(settled)
            assertTrue(restColumns.isNotEmpty())
            assertTrue("Wings should lie alongside the torso at completion", restColumns.last() - restColumns.first() < (spreadColumns.last() - spreadColumns.first()) * .6f)
        } finally { spread.recycle(); settled.recycle(); repeated.recycle() }
    }
}
