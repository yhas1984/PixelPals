package com.pixelpals.app.feature.care

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.care.scene.CarePlayVariation
import com.pixelpals.app.core.care.scene.CareSceneAction
import com.pixelpals.app.core.care.scene.CareSceneController
import com.pixelpals.app.core.care.scene.CareSceneMode
import com.pixelpals.app.core.motion.JellySpringPlayMotion
import com.pixelpals.app.core.domain.PetType
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Renderer review for Jelly's shared spring and clean original body. */
@RunWith(AndroidJUnit4::class)
class JellySpringRenderingTest {
    private val bodyFilter = PorterDuffColorFilter(Color.MAGENTA, PorterDuff.Mode.SRC_IN)
    private val variations = CarePlayVariation.entries

    @Test fun springKeepsBodyCameraAndContactAcrossRoomDesktopAndVariations(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pack = CarePoseLoader.load(context.assets, PetType.JELLY)
        val renderer = SpeciesCareRenderer()
        val sheet = Bitmap.createBitmap(320 * 10, 640, Bitmap.Config.ARGB_8888)
        val errors = ArrayList<String>()
        val samples = JSONArray()
        try {
            val progressSamples = ((0..50).map { it * .02f } + listOf(.30f, .42f, .50f, .70f, .78f, .84f, .94f)).distinct().sorted()
            for ((surfaceIndex, desktop) in listOf(false, true).withIndex()) {
                val height = if (desktop) 320f else 220f
                val tile = Bitmap.createBitmap(320, height.toInt(), Bitmap.Config.ARGB_8888)
                val baselineOffset = if (desktop) 92f else null
                var reducedBaseline: Mask? = null
                for (variation in variations) for (reduced in listOf(false, true)) for (progress in progressSamples) {
                    val scene = CareSceneController(CareSceneAction.PLAY, CareSceneMode.AUTOMATIC,
                        pack.spec.timings.getValue(CareSceneAction.PLAY), variation)
                    if (progress > 0f) scene.advance((scene.timing.durationMs * progress).toLong())
                    val sample = JellySpringPlayMotion.sample(progress, reduced, variation)
                    tile.eraseColor(Color.TRANSPARENT)
                    renderer.draw(Canvas(tile), pack, scene, reduced, false, desktopSize = if (desktop) 160 else null,
                        desktopBaselineOffsetY = baselineOffset, colorFilter = bodyFilter)
                    val body = mask(tile, ::isBody)
                    if (body.count <= 100) errors += "$surfaceIndex/$variation reduced=$reduced/$progress body missing"
                    if (body.left <= 0 || body.top <= 0 || body.right >= tile.width || body.bottom >= tile.height)
                        errors += "$surfaceIndex/$variation reduced=$reduced/$progress body clipped"
                    if (reduced) {
                        reducedBaseline?.let { base ->
                            if (body != base) errors += "$surfaceIndex/$variation reduced body camera changed at $progress"
                        } ?: run { reducedBaseline = body }
                    } else if (progress in .30f.. .50f || progress in .70f.. .84f) {
                        val actorSize = if (desktop) 160f * com.pixelpals.app.core.motion.PetArtworkScale.desktopCare(PetType.JELLY)
                            else minOf(height * .76f, 320f * .66f) * com.pixelpals.app.core.motion.PetArtworkScale.speciesSize(PetType.JELLY)
                        val ground = if (desktop) 252f else height * .88f
                        val expectedBottom = ground - JellySpringPlayMotion.SPRING_HEIGHT * actorSize * sample.springCompression
                        if (kotlin.math.abs(body.bottom - expectedBottom) > 2) errors += "$surfaceIndex/$variation/$progress body contact ${body.bottom} expected $expectedBottom"
                    }
                    // The body is still magenta, so its yellow highlights cannot
                    // masquerade as the spring's gold coils in this measurement.
                    val spring = mask(tile, ::isSpring)
                    val ground = if (desktop) 252f else height * .88f
                    if (spring.count > 5 && kotlin.math.abs(spring.bottom - ground) > 2) errors += "$surfaceIndex/$variation/$reduced/$progress spring base ${spring.bottom}"
                    if (!reduced && progress in .50f.. .70f && spring.count > 5 && body.bottom > spring.top + 2)
                        errors += "$surfaceIndex/$variation/$progress body overlaps spring flight"
                    samples.put(JSONObject().put("surface", if (desktop) "desktop" else "room")
                        .put("progress", progress.toDouble()).put("variation", variation.name).put("reduced", reduced)
                        .put("body", body.json()).put("spring", spring.json()))
                }
                val directProgress = listOf(0f, .10f, .20f, .30f, .40f, .50f, .60f, .70f, .85f, 1f)
                for ((sampleIndex, progress) in directProgress.withIndex()) {
                    val scene = CareSceneController(CareSceneAction.PLAY, CareSceneMode.AUTOMATIC,
                        pack.spec.timings.getValue(CareSceneAction.PLAY), CarePlayVariation.DIRECT)
                    if (progress > 0f) scene.advance((scene.timing.durationMs * progress).toLong())
                    tile.eraseColor(Color.TRANSPARENT)
                    renderer.draw(Canvas(tile), pack, scene, false, false, desktopSize = if (desktop) 160 else null,
                        desktopBaselineOffsetY = baselineOffset)
                    Canvas(sheet).drawBitmap(tile, sampleIndex * 320f, surfaceIndex * 320f, Paint(Paint.ANTI_ALIAS_FLAG))
                }
                val frameCount = (pack.spec.timings.getValue(CareSceneAction.PLAY).durationMs / 1000f * 30f).toInt() + 1
                for (frame in 0 until frameCount) {
                    val progress = if (frameCount <= 1) 0f else frame.toFloat() / (frameCount - 1)
                    val variation = CarePlayVariation.DIRECT
                    val scene = CareSceneController(CareSceneAction.PLAY, CareSceneMode.AUTOMATIC,
                        pack.spec.timings.getValue(CareSceneAction.PLAY), variation)
                    if (progress > 0f) scene.advance((scene.timing.durationMs * progress).toLong())
                    tile.eraseColor(Color.TRANSPARENT)
                    renderer.draw(Canvas(tile), pack, scene, false, false, desktopSize = if (desktop) 160 else null,
                        desktopBaselineOffsetY = baselineOffset)
                    val file = File(context.cacheDir, "jelly-spring-sequence/${if (desktop) "desktop" else "room"}").apply { mkdirs() }
                    File(file, "%03d.png".format(frame)).outputStream().use { tile.compress(Bitmap.CompressFormat.PNG, 90, it) }
                }
                tile.recycle()
            }
            val directory = File(context.cacheDir, "jelly-spring").apply { mkdirs() }
            File(directory, "jelly-spring-direct.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
            File(directory, "jelly-spring-bounds.json").writeText(JSONObject().put("samples", samples).put("errors", JSONArray(errors)).toString(2))
            assertTrue(errors.joinToString("; "), errors.isEmpty())
        } finally {
            pack.bitmap.recycle(); sheet.recycle()
        }
    }

    private data class Mask(val left: Int, val top: Int, val right: Int, val bottom: Int, val count: Int) {
        fun json(): JSONObject = JSONObject().put("left", left).put("top", top).put("right", right)
            .put("bottom", bottom).put("count", count)
    }

    private fun mask(bitmap: Bitmap, predicate: (Int) -> Boolean): Mask {
        var left = bitmap.width; var top = bitmap.height; var right = -1; var bottom = -1; var count = 0
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        pixels.forEachIndexed { index, color -> if (predicate(color)) {
            val x = index % bitmap.width; val y = index / bitmap.width
            left = minOf(left, x); top = minOf(top, y); right = maxOf(right, x); bottom = maxOf(bottom, y); count++
        } }
        return Mask(left, top, right + 1, bottom + 1, count)
    }

    private fun isBody(color: Int): Boolean = Color.alpha(color) >= 32 && Color.red(color) > 150 &&
        Color.blue(color) > 150 && Color.green(color) < 120

    private fun isSpring(color: Int): Boolean = Color.alpha(color) >= 80 &&
        ((Color.red(color) > 180 && Color.green(color) in 90..190 && Color.blue(color) in 90..180) ||
            (Color.red(color) > 210 && Color.green(color) in 160..230 && Color.blue(color) < 150))
}
