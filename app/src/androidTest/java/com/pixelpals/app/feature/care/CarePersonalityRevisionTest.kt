package com.pixelpals.app.feature.care

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.care.scene.*
import com.pixelpals.app.core.domain.PetType
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated rendering: never opens the repository or changes the user's pets. */
@RunWith(AndroidJUnit4::class)
class CarePersonalityRevisionTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val pets: List<PetType> = listOf(PetType.NUBE_MICHI, PetType.GINGER, PetType.DIABLILLO,
        PetType.MOKI, PetType.LUMI, PetType.TELA)

    @Test fun foodAndToysRemainVisibleAtMenuSizeAndProduceAReviewSheet(): Unit {
        val icon: Bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
        val pixels: IntArray = IntArray(48 * 48)
        val sheet: Bitmap = Bitmap.createBitmap(640, pets.size * 140, Bitmap.Config.ARGB_8888)
        sheet.eraseColor(Color.rgb(242, 237, 247))
        val canvas: Canvas = Canvas(sheet)
        val label: Paint = Paint().apply { color = Color.DKGRAY; textSize = 18f }
        val painter: CarePropPainter = CarePropPainter()
        pets.forEachIndexed { row, pet ->
            canvas.drawText(pet.name, 12f, row * 140 + 32f, label)
            val hashes: MutableSet<Int> = mutableSetOf()
            listOf(CareSceneAction.FEED, CareSceneAction.PLAY).forEachIndexed { column, action ->
                icon.eraseColor(Color.TRANSPARENT)
                painter.draw(Canvas(icon), action, 24f, 24f, 32f, pet = pet)
                icon.getPixels(pixels, 0, 48, 0, 0, 48, 48)
                assertTrue("$pet $action has a readable silhouette", pixels.count { Color.alpha(it) > 128 } > 50)
                assertTrue("$pet does not eat its toy", hashes.add(pixels.contentHashCode()))
                canvas.drawBitmap(icon, 240f + column * 180, row * 140 + 65f, null)
                painter.draw(canvas, action, 330f + column * 180, row * 140 + 80f, 84f, pet = pet)
                canvas.drawText(action.name, 245f + column * 180, row * 140 + 28f, label)
            }
        }
        saveReview(sheet, "revision-props.png")
        icon.recycle(); sheet.recycle()
    }

    @Test fun foamIsVisibleDuringWashingAndGoneAfterRinsing(): Unit {
        val bitmap: Bitmap = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
        val pixels: IntArray = IntArray(160 * 160)
        for (reduced: Boolean in listOf(false, true)) for (progress: Float in listOf(0f, .5f, 1f)) {
            bitmap.eraseColor(Color.TRANSPARENT)
            CareFoamPainter().draw(Canvas(bitmap), CarePoint(80f, 80f), 120f, CareWashMotion.sample(progress, reduced))
            bitmap.getPixels(pixels, 0, 160, 0, 0, 160, 160)
            if (progress == .5f) assertTrue(pixels.count { Color.alpha(it) > 128 } > 500)
            else assertTrue(pixels.all { Color.alpha(it) == 0 })
        }
        bitmap.recycle()
    }

    @Test fun allPlayVariantsRemainInsideTheRoomWithMatchingManualTargets(): Unit = runBlocking {
        val frame: Bitmap = Bitmap.createBitmap(320, 220, Bitmap.Config.ARGB_8888)
        val pixels: IntArray = IntArray(320 * 220)
        val renderer: CareSceneRenderer = CareSceneRenderer()
        val label: Paint = Paint().apply { color = Color.DKGRAY; textSize = 14f }
        for (pet: PetType in PetType.entries.filter { it != PetType.CORGI }) {
            val pack: CarePosePack = CarePoseLoader.load(context.assets, pet)
            val sheet: Bitmap = Bitmap.createBitmap(1600, 720, Bitmap.Config.ARGB_8888)
            sheet.eraseColor(Color.rgb(242, 237, 247))
            val sheetCanvas: Canvas = Canvas(sheet)
            for (variation: CarePlayVariation in CarePlayVariation.entries) {
                val scene: CareSceneController = CareSceneController(CareSceneAction.PLAY, CareSceneMode.AUTOMATIC,
                    pack.spec.timings.getValue(CareSceneAction.PLAY), variation)
                for (step: Int in 0..40) {
                    if (step > 0) scene.advance(scene.timing.durationMs / 40)
                    for (reduced: Boolean in listOf(false, true)) {
                        frame.eraseColor(Color.TRANSPARENT)
                        renderer.draw(Canvas(frame), pack, scene, reduced, false)
                        frame.getPixels(pixels, 0, 320, 0, 0, 320, 220)
                        assertTrue("$pet $variation $step visible", pixels.count { Color.alpha(it) > 128 } > 500)
                        assertTrue("$pet $variation $step top/bottom", (0 until 320).all {
                            Color.alpha(pixels[it]) == 0 && Color.alpha(pixels[219 * 320 + it]) == 0 })
                        assertTrue("$pet $variation $step sides", (0 until 220).all {
                            Color.alpha(pixels[it * 320]) == 0 && Color.alpha(pixels[it * 320 + 319]) == 0 })
                        val target: CarePoint = renderer.getTarget(pack, scene, 320f, 220f, stationary = reduced)
                        assertTrue(target.x in 0f..1f && target.y in 0f..1f)
                        if (!reduced && step % 10 == 0) {
                            sheetCanvas.drawBitmap(frame, step / 10 * 320f, variation.ordinal * 240f, null)
                            sheetCanvas.drawText("${variation.name} ${step * 2.5f}%", step / 10 * 320f + 8,
                                variation.ordinal * 240f + 18, label)
                        }
                    }
                }
                val manual: CareSceneController = CareSceneController(CareSceneAction.PLAY, CareSceneMode.MANUAL,
                    scene.timing, variation)
                val target: CarePoint = renderer.getTarget(pack, manual, 320f, 220f)
                manual.movePointer(target, target, false)
                assertTrue("$pet $variation manual start", manual.hasContact)
                assertEquals(1, (1..200).count { manual.advance(50) })
            }
            if (pet in pets) saveReview(sheet, "revision-play-${pet.name.lowercase()}.png")
            sheet.recycle(); pack.bitmap.recycle()
        }
        frame.recycle()
    }

    private fun saveReview(bitmap: Bitmap, name: String): Unit {
        val directory: File = requireNotNull(context.getExternalFilesDir("care-review"))
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
