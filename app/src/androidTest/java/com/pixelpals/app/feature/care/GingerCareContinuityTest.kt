package com.pixelpals.app.feature.care

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.care.scene.CareSceneAction
import com.pixelpals.app.core.care.scene.CareSceneController
import com.pixelpals.app.core.care.scene.CareSceneMode
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.PetArtworkScale
import com.pixelpals.app.core.motion.SeededPetRandom
import com.pixelpals.app.feature.overlay.behavior.BaseBehavior
import com.pixelpals.app.feature.overlay.behavior.GingerBehavior
import com.pixelpals.app.feature.overlay.behavior.TestPetBridge
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/** Measures Ginger's native and care bodies from the same 240px desktop window. */
@RunWith(AndroidJUnit4::class)
class GingerCareContinuityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val magenta = PorterDuffColorFilter(Color.MAGENTA, PorterDuff.Mode.SRC_IN)

    @Test fun sixActionsKeepContactAndAuthoredAnatomyAcrossTheCareTimeline(): Unit = runBlocking {
        val pack = CarePoseLoader.load(context.assets, PetType.GINGER)
        lateinit var bridge: TestPetBridge
        lateinit var behavior: GingerBehavior
        val sheet = Bitmap.createBitmap(8 * TILE, 4 * TILE, Bitmap.Config.ARGB_8888)
        try {
            instrumentation.runOnMainSync {
                bridge = TestPetBridge(context, PetType.GINGER, SPRITE_SIZE, PetArtworkScale.forPet(PetType.GINGER))
                bridge.animColorFilter = magenta
                behavior = GingerBehavior(bridge, SeededPetRandom(17))
            }
            waitForAssets(behavior)
            val native = linkedMapOf<Int, Bounds>()
            val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 16f }
            instrumentation.runOnMainSync {
                sheet.eraseColor(Color.WHITE)
                for (frame in listOf(0, 18, 21)) {
                    bridge.currentFrame = frame
                    native[frame] = renderNative(bridge, behavior)
                    bridge.animColorFilter = null
                    val colorTile = renderNativeTile(bridge, behavior)
                    Canvas(sheet).drawBitmap(colorTile, listOf(0, 1, 2)[listOf(0, 18, 21).indexOf(frame)] * TILE.toFloat(), 0f, null)
                    Canvas(sheet).drawText("native $frame", listOf(0, 1, 2)[listOf(0, 18, 21).indexOf(frame)] * TILE.toFloat() + 8f, 22f, label)
                    colorTile.recycle()
                    bridge.animColorFilter = magenta
                }
            }
            val baseline = requireNotNull(behavior.careBaselineOffsetY)
            val renderer = SpeciesCareRenderer()
            val actions = CareSceneAction.entries.toList()
            val samples = listOf(0f, .35f, .75f, 1f)
            instrumentation.runOnMainSync {
                actions.forEachIndexed { actionIndex, action ->
                    samples.forEachIndexed { sampleIndex, fraction ->
                        val scene = CareSceneController(action, CareSceneMode.AUTOMATIC,
                            pack.spec.timings.getValue(action))
                        if (fraction > 0f) scene.advance((scene.timing.durationMs * fraction).toLong())
                        val tile = Bitmap.createBitmap(TILE, TILE, Bitmap.Config.ARGB_8888)
                        renderer.draw(Canvas(tile), pack, scene, reduced = false, gentle = false,
                            desktopSize = SPRITE_SIZE, desktopBaselineOffsetY = baseline, colorFilter = magenta)
                        val bounds = magentaBounds(tile)
                        assertTrue("$action@$fraction has no isolated body", bounds.hasPixels)
                        assertTrue("$action@$fraction loses contact: ${bounds.bottom}",
                            abs(bounds.bottom - native.getValue(0).bottom) <= CONTACT_TOLERANCE)
                        // Only compare like-for-like poses. Quadruped feeding and
                        // seated affection deliberately have different silhouettes.
                        if (action == CareSceneAction.REST && (fraction == 0f || fraction == .75f)) {
                            val reference = native.getValue(if (fraction == 0f) 0 else 21)
                            assertTrue("REST@$fraction changes the native camera: $bounds vs $reference",
                                abs((bounds.right - bounds.left) - (reference.right - reference.left)) <= 4 &&
                                    abs((bounds.bottom - bounds.top) - (reference.bottom - reference.top)) <= 4)
                        }
                        val colorTile = Bitmap.createBitmap(TILE, TILE, Bitmap.Config.ARGB_8888)
                        renderer.draw(Canvas(colorTile), pack, scene, reduced = false, gentle = false,
                            desktopSize = SPRITE_SIZE, desktopBaselineOffsetY = baseline)
                        val slot = actionIndex * samples.size + sampleIndex
                        Canvas(sheet).drawBitmap(colorTile,
                            slot % 8 * TILE.toFloat(), (slot / 8 + 1) * TILE.toFloat(), null)
                        Canvas(sheet).drawText("$action ${fraction * 100f}%", slot % 8 * TILE.toFloat() + 8f,
                            (slot / 8 + 1) * TILE.toFloat() + 22f, label)
                        colorTile.recycle()
                        tile.recycle()
                    }
                }
                File(context.cacheDir, "ginger-care-continuity.png").outputStream().use {
                    sheet.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        } finally {
            sheet.recycle(); pack.bitmap.recycle()
            instrumentation.runOnMainSync { behavior.destroy() }
        }
    }

    @Test fun reducedRestKeepsFurnitureWidthAcrossGingerCameraChange(): Unit = runBlocking {
        val pack = CarePoseLoader.load(context.assets, PetType.GINGER)
        try {
            val renderer = SpeciesCareRenderer()
            val baseline = TILE / 2f + DESKTOP_SIZE * .46f
            lateinit var start: FurnitureBounds
            lateinit var end: FurnitureBounds
            instrumentation.runOnMainSync {
                start = renderReducedRestFurniture(renderer, pack, complete = false, baseline = baseline)
                end = renderReducedRestFurniture(renderer, pack, complete = true, baseline = baseline)
            }
            assertTrue("REST furniture disappeared at entry: $start", start.hasPixels)
            assertTrue("REST furniture disappeared at completion: $end", end.hasPixels)
            assertTrue("REST furniture width changed with Ginger camera: $start vs $end",
                abs(start.width - end.width) <= FURNITURE_TOLERANCE)
        } finally {
            pack.bitmap.recycle()
        }
    }

    private fun renderReducedRestFurniture(renderer: SpeciesCareRenderer, pack: CarePosePack,
                                           complete: Boolean, baseline: Float): FurnitureBounds {
        val scene = CareSceneController(CareSceneAction.REST, CareSceneMode.AUTOMATIC,
            pack.spec.timings.getValue(CareSceneAction.REST))
        if (complete) scene.advance(scene.timing.durationMs)
        val bitmap = Bitmap.createBitmap(TILE, TILE, Bitmap.Config.ARGB_8888)
        renderer.draw(Canvas(bitmap), pack, scene, reduced = true, gentle = false,
            desktopSize = DESKTOP_SIZE, colorFilter = magenta)
        val bounds = furnitureBounds(bitmap, baseline)
        bitmap.recycle()
        return bounds
    }

    private fun furnitureBounds(bitmap: Bitmap, baseline: Float): FurnitureBounds {
        var left = bitmap.width
        var right = -1
        val firstRow = baseline.toInt() + 4
        for (y in firstRow until bitmap.height) for (x in 0 until bitmap.width) {
            val pixel = bitmap.getPixel(x, y)
            // The sprite is SRC_IN magenta; furniture remains in its authored
            // colors. Below the ground line this excludes body paws and dreams.
            if (Color.alpha(pixel) < 32 ||
                (Color.red(pixel) >= 180 && Color.blue(pixel) >= 180 && Color.green(pixel) < 120)) continue
            left = minOf(left, x)
            right = maxOf(right, x)
        }
        return FurnitureBounds(right >= left, left, right, right - left + 1)
    }

    private fun renderNative(bridge: TestPetBridge, behavior: GingerBehavior): Bounds {
        val tile = Bitmap.createBitmap(TILE, TILE, Bitmap.Config.ARGB_8888)
        behavior.onDraw(Canvas(tile), TILE / 2f, TILE / 2f)
        val bounds = magentaBounds(tile)
        tile.recycle()
        return bounds
    }

    private fun renderNativeTile(bridge: TestPetBridge, behavior: GingerBehavior): Bitmap {
        val tile = Bitmap.createBitmap(TILE, TILE, Bitmap.Config.ARGB_8888)
        behavior.onDraw(Canvas(tile), TILE / 2f, TILE / 2f)
        return tile
    }

    private fun magentaBounds(bitmap: Bitmap): Bounds {
        var left = bitmap.width; var top = bitmap.height; var right = -1; var bottom = -1
        var centralLeft = bitmap.width; var centralTop = bitmap.height; var centralRight = -1; var centralBottom = -1
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            val pixel = bitmap.getPixel(x, y)
            if (Color.alpha(pixel) < 128 || Color.red(pixel) < 180 || Color.blue(pixel) < 180 || Color.green(pixel) >= 120) continue
            left = minOf(left, x); top = minOf(top, y); right = maxOf(right, x); bottom = maxOf(bottom, y)
            if (x in TILE / 5..TILE * 4 / 5 && y >= TILE / 4) {
                centralLeft = minOf(centralLeft, x); centralTop = minOf(centralTop, y)
                centralRight = maxOf(centralRight, x); centralBottom = maxOf(centralBottom, y)
            }
        }
        return Bounds(right >= 0, left, top, right + 1, bottom + 1,
            centralRight - centralLeft + 1, centralBottom - centralTop + 1)
    }

    private fun waitForAssets(behavior: GingerBehavior) {
        val loading = BaseBehavior::class.java.getDeclaredField("isLoading").apply { isAccessible = true }
        repeat(200) {
            if (!loading.getBoolean(behavior)) return
            Thread.sleep(25)
        }
        assertTrue("Ginger atlas failed to load", !loading.getBoolean(behavior))
    }

    private data class Bounds(val hasPixels: Boolean, val left: Int, val top: Int, val right: Int,
                              val bottom: Int, val centralWidth: Int, val centralHeight: Int)

    private data class FurnitureBounds(val hasPixels: Boolean, val left: Int, val right: Int,
                                       val width: Int)

    private companion object {
        const val SPRITE_SIZE = 240
        const val TILE = 320
        const val DESKTOP_SIZE = 160
        const val CONTACT_TOLERANCE = 12
        const val FURNITURE_TOLERANCE = 2
    }
}
