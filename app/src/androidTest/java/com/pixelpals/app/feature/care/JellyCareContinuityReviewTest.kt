package com.pixelpals.app.feature.care

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.care.scene.CareSceneAction
import com.pixelpals.app.core.care.scene.CareSceneController
import com.pixelpals.app.core.care.scene.CareSceneMode
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.PetArtworkScale
import com.pixelpals.app.feature.overlay.behavior.JellyBehavior
import com.pixelpals.app.feature.overlay.behavior.PetBehaviorFactory
import com.pixelpals.app.feature.overlay.behavior.TestPetBridge
import com.pixelpals.app.core.motion.SeededPetRandom
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** Real Jelly desktop/care body masks for visual review; no state or preferences are written. */
@RunWith(AndroidJUnit4::class)
class JellyCareContinuityReviewTest {
    private val magenta = PorterDuffColorFilter(Color.MAGENTA, PorterDuff.Mode.SRC_IN)

    @Test fun exportAndCheckJellyCareEntryAndReturnContinuity(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        lateinit var bridge: TestPetBridge
        lateinit var behavior: JellyBehavior
        val tile = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
        val sheet = Bitmap.createBitmap(320 * 5, 320 * 5, Bitmap.Config.ARGB_8888)
        val renderer = SpeciesCareRenderer()
        val pack = CarePoseLoader.load(context.assets, PetType.JELLY)
        lateinit var baseline: Mask
        val supportErrors = ArrayList<String>()
        instrumentation.runOnMainSync {
            bridge = TestPetBridge(context, PetType.JELLY, 160, PetArtworkScale.forPet(PetType.JELLY))
            behavior = PetBehaviorFactory.create(PetType.JELLY, bridge, SeededPetRandom(8)) as JellyBehavior
        }
        try {
            instrumentation.runOnMainSync { bridge.animColorFilter = magenta }
            var loaded = false
            for (attempt in 0 until 100) {
                instrumentation.runOnMainSync {
                    tile.eraseColor(Color.TRANSPARENT)
                    behavior.updateIdle(0f)
                    behavior.onDraw(Canvas(tile), 160f, 160f)
                    loaded = mask(tile).count > 100
                }
                if (loaded) break
                delay(25)
            }
            if (!loaded) supportErrors += "Jelly behavior did not finish loading"
            baseline = mask(tile)
            instrumentation.runOnMainSync { bridge.animColorFilter = null }
            instrumentation.runOnMainSync {
                tile.eraseColor(Color.TRANSPARENT)
                behavior.onDraw(Canvas(tile), 160f, 160f)
            }
            drawTile(sheet, 0, tile)
            val records = JSONArray().put(JSONObject().put("source", "behavior").put("frame", bridge.currentFrame).put("mask", baseline.jsonObject()))

            var column = 1
            for (reduced in listOf(false, true)) for (action in CareSceneAction.entries) {
                val scene = CareSceneController(action, CareSceneMode.AUTOMATIC,
                    pack.spec.timings.getValue(action))
                for (entry in listOf(true, false)) {
                    if (!entry) {
                        scene.advance(scene.timing.durationMs)
                        scene.advance(1L)
                    }
                    instrumentation.runOnMainSync { bridge.animColorFilter = magenta }
                    tile.eraseColor(Color.TRANSPARENT)
                    renderer.draw(Canvas(tile), pack, scene, reduced, false,
                        desktopSize = 160, desktopBaselineOffsetY = behavior.careBaselineOffsetY ?: 160f * .46f,
                        colorFilter = magenta)
                    val body = mask(tile)
                    val phase = if (entry) "entry" else "return"
                    if (body.count <= 100) supportErrors += "Jelly $action $phase body not visible"
                    if (kotlin.math.abs(body.bottom - baseline.bottom) > 2)
                        supportErrors += "Jelly $action $phase reduced=$reduced support moved ${body.bottom - baseline.bottom}px"
                    instrumentation.runOnMainSync { bridge.animColorFilter = null }
                    tile.eraseColor(Color.TRANSPARENT)
                    renderer.draw(Canvas(tile), pack, scene, reduced, false,
                        desktopSize = 160, desktopBaselineOffsetY = behavior.careBaselineOffsetY ?: 160f * .46f)
                    drawTile(sheet, column++, tile)
                    records.put(JSONObject().put("action", action.name).put("phase", phase)
                        .put("reduced", reduced).put("mask", body.jsonObject()))
                }
            }
            val directory = File(context.cacheDir, "jelly-care-continuity").apply { mkdirs() }
            File(directory, "jelly-care-continuity.png").outputStream().use {
                sheet.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            File(directory, "jelly-care-continuity.json").writeText(JSONObject()
                .put("spriteSize", 160)
                .put("scale", PetArtworkScale.forPet(PetType.JELLY).toDouble())
                .put("baseline", baseline.jsonObject())
                .put("samples", records).toString(2))
            assertTrue(supportErrors.joinToString("; "), supportErrors.isEmpty())
        } finally {
            pack.bitmap.recycle()
            tile.recycle()
            sheet.recycle()
            instrumentation.runOnMainSync { behavior.destroy() }
        }
    }

    private fun drawTile(sheet: Bitmap, column: Int, tile: Bitmap): Unit =
        Canvas(sheet).drawBitmap(tile, column % 5 * 320f, column / 5 * 320f, Paint(Paint.ANTI_ALIAS_FLAG))

    private data class Mask(val left: Int, val top: Int, val right: Int, val bottom: Int, val count: Int) {
        fun jsonObject(): JSONObject = JSONObject()
            .put("left", left).put("top", top).put("right", right).put("bottom", bottom).put("count", count)
    }

    private fun mask(bitmap: Bitmap): Mask {
        var left = bitmap.width; var top = bitmap.height; var right = -1; var bottom = -1; var count = 0
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        pixels.forEachIndexed { index, pixel ->
            if (Color.alpha(pixel) >= 32 && Color.red(pixel) > 150 && Color.blue(pixel) > 150 && Color.green(pixel) < 120) {
                val x = index % bitmap.width; val y = index / bitmap.width
                left = minOf(left, x); top = minOf(top, y); right = maxOf(right, x); bottom = maxOf(bottom, y); count++
            }
        }
        return Mask(left, top, right + 1, bottom + 1, count)
    }
}
