package com.pixelpals.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.care.scene.*
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.PetArtworkScale
import com.pixelpals.app.core.motion.SeededPetRandom
import com.pixelpals.app.feature.care.*
import com.pixelpals.app.feature.overlay.behavior.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Actual default behavior and care at the production source scale; no PetView or progress writes. */
@RunWith(AndroidJUnit4::class)
class DesktopCareHandoffReviewTest {
    @Test fun exportEveryDesktopToCareHandoffAtTheSameWindowSize(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val directory: File = File(context.cacheDir, "desktop-care-handoffs").apply { mkdirs() }
        val tile: Bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
        val pixels: IntArray = IntArray(320 * 320)
        val actions: List<CareSceneAction> = listOf(CareSceneAction.FEED, CareSceneAction.PET,
            CareSceneAction.REST, CareSceneAction.MEDICINE)
        val label: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 14f }
        try {
            for (pet: PetType in PetType.entries) {
                lateinit var bridge: TestPetBridge
                lateinit var behavior: PetBehavior
                instrumentation.runOnMainSync {
                    bridge = TestPetBridge(context, pet, 160, PetArtworkScale.forPet(pet))
                    behavior = PetBehaviorFactory.create(pet, bridge, SeededPetRandom(8))
                }
                val pack: CarePosePack = CarePoseLoader.load(context.assets, pet)
                val sheet: Bitmap = Bitmap.createBitmap(1600, 344, Bitmap.Config.ARGB_8888)
                try {
                    var visible: Boolean = false
                    for (attempt: Int in 0 until 100) {
                        instrumentation.runOnMainSync {
                            tile.eraseColor(Color.TRANSPARENT)
                            behavior.updateIdle(.016f)
                            behavior.onDraw(Canvas(tile), 160f, 160f)
                            tile.getPixels(pixels, 0, 320, 0, 0, 320, 320)
                            visible = pixels.count { Color.alpha(it) >= 128 } > 500
                        }
                        if (visible) break
                        delay(50)
                    }
                    assertTrue("$pet original behavior must finish loading", visible)
                    val originalPixels: IntArray = pixels.copyOf()
                    instrumentation.runOnMainSync {
                        sheet.eraseColor(Color.rgb(250, 247, 239))
                        val canvas: Canvas = Canvas(sheet)
                        canvas.drawBitmap(tile, 0f, 24f, null)
                        canvas.drawText("$pet desktop frame ${bridge.currentFrame}", 6f, 18f, label)
                        val baseline: Float = behavior.careBaselineOffsetY ?: 160f * .46f
                        val renderer: SpeciesCareRenderer = SpeciesCareRenderer()
                        val corgi: CorgiDesktopCareRenderer = CorgiDesktopCareRenderer()
                        actions.forEachIndexed { index, action ->
                            tile.eraseColor(Color.TRANSPARENT)
                            if (pet == PetType.CORGI) {
                                corgi.draw(Canvas(tile), pack, 160, 0L, false, false, action,
                                    baselineOffsetY = baseline)
                            } else {
                                val scene = CareSceneController(action, CareSceneMode.AUTOMATIC, pack.spec.timings.getValue(action))
                                renderer.draw(Canvas(tile), pack, scene, false, false, desktopSize = 160,
                                    desktopBaselineOffsetY = baseline)
                            }
                            tile.getPixels(pixels, 0, 320, 0, 0, 320, 320)
                            assertTrue("$pet $action handoff must be visible", pixels.count { Color.alpha(it) >= 128 } > 500)
                            val x: Float = (index + 1) * 320f
                            canvas.drawBitmap(tile, x, 24f, null)
                            canvas.drawText("$action entry", x + 6f, 18f, label)
                            if (pet == PetType.JELLY && action == CareSceneAction.FEED) {
                                tile.eraseColor(Color.TRANSPARENT)
                                renderer.draw(Canvas(tile), pack,
                                    CareSceneController(action, CareSceneMode.AUTOMATIC, pack.spec.timings.getValue(action)),
                                    false, false, desktopSize = 160, desktopBaselineOffsetY = baseline,
                                    colorFilter = android.graphics.PorterDuffColorFilter(Color.MAGENTA, android.graphics.PorterDuff.Mode.SRC_IN))
                                tile.getPixels(pixels, 0, 320, 0, 0, 320, 320)
                                val original: List<Int> = originalPixels.indices.filter { Color.alpha(originalPixels[it]) >= 128 }
                                val body: List<Int> = pixels.indices.filter { pixels[it] == Color.MAGENTA }
                                assertTrue("Jelly body mask must be visible", body.size > 500)
                                val widthRatio: Float = (body.maxOf { it % 320 } - body.minOf { it % 320 }).toFloat() /
                                    (original.maxOf { it % 320 } - original.minOf { it % 320 })
                                val heightRatio: Float = (body.maxOf { it / 320 } - body.minOf { it / 320 }).toFloat() /
                                    (original.maxOf { it / 320 } - original.minOf { it / 320 })
                                assertTrue("Jelly handoff width ratio $widthRatio", widthRatio in .90f..1.10f)
                                assertTrue("Jelly handoff height ratio $heightRatio", heightRatio in .90f..1.10f)
                            }
                        }
                        File(directory, "${pet.name.lowercase()}.png").outputStream().use {
                            sheet.compress(Bitmap.CompressFormat.PNG, 100, it)
                        }
                    }
                } finally {
                    pack.bitmap.recycle(); sheet.recycle()
                    instrumentation.runOnMainSync { behavior.destroy() }
                }
            }
        } finally { tile.recycle() }
    }
}
