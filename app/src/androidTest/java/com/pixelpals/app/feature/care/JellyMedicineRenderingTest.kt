package com.pixelpals.app.feature.care

import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.care.scene.CareSceneAction
import com.pixelpals.app.core.care.scene.CareSceneController
import com.pixelpals.app.core.care.scene.CareSceneMode
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.PetArtworkScale
import com.pixelpals.app.core.motion.SeededPetRandom
import com.pixelpals.app.feature.overlay.behavior.JellyBehavior
import com.pixelpals.app.feature.overlay.behavior.PetAtlasSpec
import com.pixelpals.app.feature.overlay.behavior.PetBehaviorFactory
import com.pixelpals.app.feature.overlay.behavior.PetClipSpec
import com.pixelpals.app.feature.overlay.behavior.TestPetBridge
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs
import org.json.JSONArray
import org.json.JSONObject

/** Real Jelly medicine frames at the desktop and home cameras; exports review sheets to cache. */
@RunWith(AndroidJUnit4::class)
class JellyMedicineRenderingTest {
    private val bodyFilter = PorterDuffColorFilter(Color.MAGENTA, PorterDuff.Mode.SRC_IN)
    private val medicineFrames = listOf(20, 21, 22, 23)
    private val sampleTimesMs = listOf(0L, 750L, 1_750L, 2_750L)
    private var desktopBaseline: Float = 0f

    @Test fun medicineKeepsBaselineCameraAndExportsBothSurfaces(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val pack: CarePosePack = CarePoseLoader.load(context.assets, PetType.JELLY)
        val renderer: SpeciesCareRenderer = SpeciesCareRenderer()
        lateinit var bridge: TestPetBridge
        lateinit var behavior: JellyBehavior
        instrumentation.runOnMainSync {
            bridge = TestPetBridge(context, PetType.JELLY, 160, PetArtworkScale.forPet(PetType.JELLY))
            behavior = PetBehaviorFactory.create(PetType.JELLY, bridge, SeededPetRandom(12)) as JellyBehavior
            bridge.animColorFilter = bodyFilter
        }
        try {
            val baseline: Mask = awaitBaseline(instrumentation, behavior)
            instrumentation.runOnMainSync { desktopBaseline = behavior.careBaselineOffsetY ?: 160f * .46f }
            val errors = ArrayList<String>()
            val metrics = JSONArray()
            for (surface in listOf(Surface.DESKTOP, Surface.HOME)) {
                for (reduced in listOf(false, true)) {
                    val sheet: Bitmap = Bitmap.createBitmap(surface.width * 5, surface.height, Bitmap.Config.ARGB_8888)
                    try {
                        drawBaseline(instrumentation, sheet, behavior, bridge, surface)
                        val masks = medicineFrames.mapIndexed { index, frame ->
                            val rendered: Rendered = renderMedicine(renderer, pack, frame, sampleTimesMs[index], reduced, surface)
                            val mask: Mask = mask(rendered.mask)
                            if (mask.count <= 100) errors += "$surface reduced=$reduced frame=$frame body missing"
                            if (mask.left <= 0 || mask.top <= 0 || mask.right >= surface.width || mask.bottom >= surface.height)
                                errors += "$surface reduced=$reduced frame=$frame clipped"
                            Canvas(sheet).drawBitmap(rendered.color, surface.width * (index + 1).toFloat(), 0f, Paint(Paint.ANTI_ALIAS_FLAG))
                            metrics.put(JSONObject().put("surface", surface.id).put("reduced", reduced)
                                .put("frame", frame).put("elapsedMs", sampleTimesMs[index]).put("mask", mask.json()))
                            rendered.color.recycle()
                            rendered.mask.recycle()
                            mask
                        }
                        if (surface == Surface.DESKTOP) checkDesktopSize(masks, baseline, reduced, errors)
                        checkReducedArea(masks, reduced, errors)
                        exportRealSequence(renderer, pack, context.cacheDir, surface, reduced, baseline, errors, metrics)
                        val directory = File(context.cacheDir, "jelly-medicine").apply { mkdirs() }
                        val name = "${surface.id}-${if (reduced) "reduced" else "normal"}.png"
                        File(directory, name).outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    } finally {
                        sheet.recycle()
                    }
                }
            }
            File(context.cacheDir, "jelly-medicine/metrics.json").writeText(JSONObject()
                .put("baseline", baseline.json()).put("samples", metrics).toString(2))
            assertTrue(errors.joinToString("; "), errors.isEmpty())
        } finally {
            pack.bitmap.recycle()
            instrumentation.runOnMainSync {
                bridge.animColorFilter = null
                behavior.destroy()
            }
        }
    }

    private suspend fun awaitBaseline(instrumentation: Instrumentation, behavior: JellyBehavior): Mask {
        val tile: Bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
        try {
            repeat(100) {
                instrumentation.runOnMainSync {
                    tile.eraseColor(Color.TRANSPARENT)
                    behavior.updateIdle(0f)
                    behavior.onDraw(Canvas(tile), 160f, 160f)
                }
                if (mask(tile).count > 100) return mask(tile)
                delay(25)
            }
            return mask(tile)
        } finally {
            tile.recycle()
        }
    }

    private fun drawBaseline(instrumentation: Instrumentation, sheet: Bitmap, behavior: JellyBehavior,
                             bridge: TestPetBridge, surface: Surface): Unit {
        val tile: Bitmap = Bitmap.createBitmap(surface.width, surface.height, Bitmap.Config.ARGB_8888)
        instrumentation.runOnMainSync {
            bridge.animColorFilter = null
            tile.eraseColor(Color.TRANSPARENT)
            behavior.onDraw(Canvas(tile), surface.width / 2f, surface.height / 2f)
            bridge.animColorFilter = bodyFilter
        }
        Canvas(sheet).drawBitmap(tile, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG))
        tile.recycle()
    }

    private fun renderMedicine(renderer: SpeciesCareRenderer, original: CarePosePack, frame: Int, elapsedMs: Long,
                                reduced: Boolean, surface: Surface): Rendered {
        val pack: CarePosePack = original.withMedicineFrame(frame)
        val scene = CareSceneController(CareSceneAction.MEDICINE, CareSceneMode.AUTOMATIC,
            original.spec.timings.getValue(CareSceneAction.MEDICINE))
        if (elapsedMs > 0L) scene.advance(elapsedMs)
        val color: Bitmap = Bitmap.createBitmap(surface.width, surface.height, Bitmap.Config.ARGB_8888)
        val mask: Bitmap = Bitmap.createBitmap(surface.width, surface.height, Bitmap.Config.ARGB_8888)
        color.eraseColor(Color.TRANSPARENT)
        mask.eraseColor(Color.TRANSPARENT)
        renderer.draw(BodyMaskCanvas(color, mask), pack, scene, reduced, false,
            desktopSize = surface.desktopSize, desktopBaselineOffsetY = if (surface == Surface.DESKTOP) desktopBaseline else null,
            colorFilter = bodyFilter)
        color.eraseColor(Color.TRANSPARENT)
        renderer.draw(Canvas(color), pack, scene, reduced, false,
            desktopSize = surface.desktopSize, desktopBaselineOffsetY = if (surface == Surface.DESKTOP) desktopBaseline else null)
        return Rendered(color, mask)
    }

    private fun exportRealSequence(renderer: SpeciesCareRenderer, pack: CarePosePack, cacheDir: File, surface: Surface,
                                   reduced: Boolean, baseline: Mask, errors: MutableList<String>, metrics: JSONArray): Unit {
        val directory: File = File(cacheDir, "jelly-medicine/sequence/${surface.id}-${if (reduced) "reduced" else "normal"}").apply { mkdirs() }
        var firstArea: Int = 0
        for (index in 0..90) {
            val elapsedMs: Long = index * 3_000L / 90L
            val scene = CareSceneController(CareSceneAction.MEDICINE, CareSceneMode.AUTOMATIC,
                pack.spec.timings.getValue(CareSceneAction.MEDICINE))
            if (elapsedMs > 0L) scene.advance(elapsedMs)
            val color: Bitmap = Bitmap.createBitmap(surface.width, surface.height, Bitmap.Config.ARGB_8888)
            val magenta: Bitmap = Bitmap.createBitmap(surface.width, surface.height, Bitmap.Config.ARGB_8888)
            color.eraseColor(Color.TRANSPARENT)
            magenta.eraseColor(Color.TRANSPARENT)
            renderer.draw(BodyMaskCanvas(color, magenta), pack, scene, reduced, false,
                desktopSize = surface.desktopSize, desktopBaselineOffsetY = if (surface == Surface.DESKTOP) desktopBaseline else null,
                colorFilter = bodyFilter)
            color.eraseColor(Color.TRANSPARENT)
            renderer.draw(Canvas(color), pack, scene, reduced, false,
                desktopSize = surface.desktopSize, desktopBaselineOffsetY = if (surface == Surface.DESKTOP) desktopBaseline else null)
            val sample: Mask = mask(magenta)
            if (sample.count <= 100) errors += "sequence $surface reduced=$reduced elapsed=$elapsedMs body missing"
            if (sample.left <= 0 || sample.top <= 0 || sample.right >= surface.width || sample.bottom >= surface.height)
                errors += "sequence $surface reduced=$reduced elapsed=$elapsedMs clipped"
            if (surface == Surface.DESKTOP && abs(sample.bottom - baseline.bottom) > 2)
                errors += "sequence desktop reduced=$reduced elapsed=$elapsedMs floor ${sample.bottom} vs ${baseline.bottom}"
            if (firstArea == 0) firstArea = sample.count
            if (abs(sample.count - firstArea).toFloat() / firstArea > .03f)
                errors += "sequence $surface reduced=$reduced elapsed=$elapsedMs area ${sample.count} vs entry $firstArea"
            metrics.put(JSONObject().put("surface", surface.id).put("reduced", reduced)
                .put("sequence", true).put("elapsedMs", elapsedMs).put("frame", pack.spec.getFrame(CareSceneAction.MEDICINE, elapsedMs))
                .put("mask", sample.json()))
            File(directory, "%04d.png".format(elapsedMs / FRAME_MS)).outputStream().use {
                color.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            color.recycle()
            magenta.recycle()
        }
    }

    private fun checkDesktopSize(masks: List<Mask>, baseline: Mask, reduced: Boolean, errors: MutableList<String>): Unit {
        masks.forEachIndexed { index, mask ->
            if (abs(mask.bottom - baseline.bottom) > 2) errors += "desktop reduced=$reduced frame=${medicineFrames[index]} floor ${mask.bottom} vs ${baseline.bottom}"
            if (!withinEightPercent(mask.width, baseline.width) || !withinEightPercent(mask.height, baseline.height))
                errors += "desktop reduced=$reduced frame=${medicineFrames[index]} size ${mask.width}x${mask.height} vs ${baseline.width}x${baseline.height}"
        }
    }

    private fun checkReducedArea(masks: List<Mask>, reduced: Boolean, errors: MutableList<String>): Unit {
        if (!reduced) return
        val areas: List<Int> = masks.map { it.count }
        val average: Float = areas.average().toFloat()
        if (areas.any { abs(it - average) / average > .03f }) errors += "reduced medicine area varies beyond 3%: $areas"
    }

    private fun withinEightPercent(value: Int, reference: Int): Boolean = abs(value - reference).toFloat() / reference <= .08f

    private fun mask(bitmap: Bitmap): Mask {
        var left = bitmap.width; var top = bitmap.height; var right = -1; var bottom = -1; var count = 0
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        pixels.forEachIndexed { index, color ->
            if (Color.alpha(color) >= 32 && Color.red(color) > 150 && Color.blue(color) > 150 && Color.green(color) < 120) {
                val x = index % bitmap.width; val y = index / bitmap.width
                left = minOf(left, x); top = minOf(top, y); right = maxOf(right, x); bottom = maxOf(bottom, y); count++
            }
        }
        return Mask(left, top, right + 1, bottom + 1, count)
    }

    private data class Mask(val left: Int, val top: Int, val right: Int, val bottom: Int, val count: Int) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top
        fun json(): JSONObject = JSONObject().put("left", left).put("top", top).put("right", right)
            .put("bottom", bottom).put("width", width).put("height", height).put("count", count)
    }

    private data class Rendered(val color: Bitmap, val mask: Bitmap)

    /** Capture the actual bitmap transform before the spoon occludes the face. */
    private class BodyMaskCanvas(scratch: Bitmap, mask: Bitmap) : Canvas(scratch) {
        private val capture: Canvas = Canvas(mask)
        private val transform: Matrix = Matrix()
        @Suppress("DEPRECATION")
        override fun drawBitmap(bitmap: Bitmap, src: Rect?, dst: RectF, paint: Paint?): Unit {
            getMatrix(transform)
            capture.save()
            capture.concat(transform)
            capture.drawBitmap(bitmap, src, dst, paint)
            capture.restore()
        }
    }

    private companion object {
        const val FRAME_MS: Long = 33L
    }

    private enum class Surface(val id: String, val width: Int, val height: Int, val desktopSize: Int?) {
        DESKTOP("desktop", 320, 320, 160),
        HOME("home", 480, 280, null),
    }
}

private fun CarePosePack.withMedicineFrame(frame: Int): CarePosePack {
    val medicine: PetClipSpec = requireNotNull(spec.atlas.clip("medicine")).copy(frames = listOf(frame))
    val atlas: PetAtlasSpec = spec.atlas.copy(clips = spec.atlas.clips.map { if (it.id == "medicine") medicine else it })
    return CarePosePack(spec.copy(atlas = atlas), bitmap)
}
