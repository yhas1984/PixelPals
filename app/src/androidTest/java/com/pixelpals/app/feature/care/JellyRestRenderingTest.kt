package com.pixelpals.app.feature.care

import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.care.scene.CareSceneAction
import com.pixelpals.app.core.care.scene.CareSceneController
import com.pixelpals.app.core.care.scene.CareSceneMode
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.PetArtworkScale
import com.pixelpals.app.core.motion.JellyRestMotion
import com.pixelpals.app.core.motion.SeededPetRandom
import com.pixelpals.app.feature.overlay.behavior.JellyBehavior
import com.pixelpals.app.feature.overlay.behavior.TestPetBridge
import com.pixelpals.app.feature.overlay.behavior.PetBehaviorFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs
import org.json.JSONArray
import org.json.JSONObject

/** Real Jelly REST sequence at both care surfaces, including the 30-frame atlas route. */
@RunWith(AndroidJUnit4::class)
class JellyRestRenderingTest {
    private val bodyFilter: ColorFilter = PorterDuffColorFilter(Color.MAGENTA, PorterDuff.Mode.SRC_IN)

    @Test fun jellyRestKeepsGroundAndBodyCameraAcrossTheFullFiveSeconds(): Unit = runBlocking {
        val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val pack: CarePosePack = CarePoseLoader.load(context.assets, PetType.JELLY)
        assertTrue("Jelly REST requires the reviewed 30-frame atlas", pack.spec.atlas.frameCount >= 30)
        val renderer: SpeciesCareRenderer = SpeciesCareRenderer()
        lateinit var behavior: JellyBehavior
        instrumentation.runOnMainSync {
            val bridge: TestPetBridge = TestPetBridge(context, PetType.JELLY, 160, PetArtworkScale.forPet(PetType.JELLY))
            behavior = PetBehaviorFactory.create(PetType.JELLY, bridge, SeededPetRandom(42)) as JellyBehavior
            bridge.animColorFilter = bodyFilter
        }
        try {
            val baseline: Mask = awaitBaseline(instrumentation, behavior)
            assertTrue("Jelly desktop artwork loaded", baseline.count > 100)
            var desktopOffset: Float = 160f * .46f
            instrumentation.runOnMainSync { desktopOffset = behavior.careBaselineOffsetY ?: desktopOffset }
            val errors: MutableList<String> = mutableListOf()
            val metrics: JSONArray = JSONArray()
            for (surface: Surface in Surface.entries) {
                val awakeRendered: Rendered = render(renderer, pack, 5_000L, false, surface, desktopOffset)
                val awake: Mask = mask(awakeRendered.mask)
                awakeRendered.color.recycle(); awakeRendered.mask.recycle()
                for (reduced: Boolean in listOf(false, true)) {
                    var firstArea: Int = 0
                    var sleeping: Mask? = null
                    var entry: Mask? = null
                    var end: Mask? = null
                    val sequenceDirectory: File = File(context.cacheDir,
                        "jelly-rest/sequence/${surface.id}-${if (reduced) "reduced" else "normal"}").apply { mkdirs() }
                    for (index: Int in 0..150) {
                        val elapsed: Long = index * 5_000L / 150L
                        val rendered: Rendered = render(renderer, pack, elapsed, reduced, surface, desktopOffset)
                        val body: Mask = mask(rendered.mask)
                        val frame: Int = JellyRestMotion.sampleCare(elapsed / 5_000f, reduced).frame
                        metrics.put(JSONObject().put("surface", surface.id).put("reduced", reduced)
                            .put("elapsedMs", elapsed).put("frame", frame).put("bounds", body.json())
                            .put("area", body.count))
                        if (body.count <= 100) errors += "$surface reduced=$reduced t=$elapsed body missing"
                        if (body.left <= 0 || body.top <= 0 || body.right >= surface.width || body.bottom >= surface.height)
                            errors += "$surface reduced=$reduced t=$elapsed clipped"
                        if (abs(body.bottom - awake.bottom) > 2)
                            errors += "$surface reduced=$reduced t=$elapsed ground drift"
                        if (surface == Surface.DESKTOP && abs(body.bottom - baseline.bottom) > 2)
                            errors += "desktop t=$elapsed changed the actual Jelly support"
                        if (firstArea == 0) firstArea = body.count
                        if (abs(body.count - firstArea).toFloat() / firstArea > .03f)
                            errors += "$surface reduced=$reduced t=$elapsed body area drift"
                        if (index == 75) sleeping = body
                        if (index == 0) entry = body
                        if (index == 150) end = body
                        File(sequenceDirectory, "%04d.png".format(index)).outputStream().use {
                            rendered.color.compress(Bitmap.CompressFormat.PNG, 100, it)
                        }
                        rendered.color.recycle()
                        rendered.mask.recycle()
                    }
                    val sleep: Mask = requireNotNull(sleeping)
                    val entryMask: Mask = requireNotNull(entry)
                    val endMask: Mask = requireNotNull(end)
                    if (sleep.width <= awake.width || sleep.height >= awake.height)
                        errors += "$surface reduced=$reduced sleep does not widen and lower body"
                    if (surface == Surface.DESKTOP &&
                        ((!reduced && (!withinFivePercent(entryMask.width, baseline.width) ||
                            !withinFivePercent(entryMask.height, baseline.height))) ||
                            !withinFivePercent(endMask.width, baseline.width) ||
                            !withinFivePercent(endMask.height, baseline.height)))
                        errors += "desktop REST endpoints differ from JellyBehavior"
                }
            }
            val outputDirectory: File = File(context.cacheDir, "jelly-rest").apply { mkdirs() }
            File(outputDirectory, "metrics.json").writeText(JSONObject()
                .put("samples", metrics).put("errors", JSONArray(errors)).toString(2))
            assertTrue(errors.joinToString("; "), errors.isEmpty())
        } finally {
            pack.bitmap.recycle()
            instrumentation.runOnMainSync { behavior.destroy() }
        }
    }

    private suspend fun awaitBaseline(instrumentation: Instrumentation, behavior: JellyBehavior): Mask {
        val bitmap: Bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
        try {
            repeat(100) {
                instrumentation.runOnMainSync {
                    bitmap.eraseColor(Color.TRANSPARENT)
                    behavior.updateIdle(0f)
                    behavior.onDraw(Canvas(bitmap), 160f, 160f)
                }
                val sample: Mask = mask(bitmap)
                if (sample.count > 100) return sample
                delay(25)
            }
            return mask(bitmap)
        } finally { bitmap.recycle() }
    }

    private fun render(renderer: SpeciesCareRenderer, pack: CarePosePack, elapsed: Long,
                       reduced: Boolean, surface: Surface, desktopOffset: Float): Rendered {
        val scene: CareSceneController = CareSceneController(CareSceneAction.REST, CareSceneMode.AUTOMATIC,
            pack.spec.timings.getValue(CareSceneAction.REST))
        if (elapsed > 0L) scene.advance(elapsed)
        val color: Bitmap = Bitmap.createBitmap(surface.width, surface.height, Bitmap.Config.ARGB_8888)
        val body: Bitmap = Bitmap.createBitmap(surface.width, surface.height, Bitmap.Config.ARGB_8888)
        color.eraseColor(Color.TRANSPARENT)
        body.eraseColor(Color.TRANSPARENT)
        renderer.draw(BodyMaskCanvas(color, body), pack, scene, reduced, false,
            desktopSize = surface.desktopSize,
            desktopBaselineOffsetY = if (surface == Surface.DESKTOP) desktopOffset else null,
            colorFilter = bodyFilter)
        color.eraseColor(Color.TRANSPARENT)
        renderer.draw(Canvas(color), pack, scene, reduced, false,
            desktopSize = surface.desktopSize,
            desktopBaselineOffsetY = if (surface == Surface.DESKTOP) desktopOffset else null)
        return Rendered(color, body)
    }

    private fun mask(bitmap: Bitmap): Mask {
        val pixels: IntArray = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var left: Int = bitmap.width; var top: Int = bitmap.height
        var right: Int = -1; var bottom: Int = -1; var count: Int = 0
        pixels.forEachIndexed { index: Int, color: Int ->
            if (Color.alpha(color) >= 32 && Color.red(color) > 150 && Color.blue(color) > 150 && Color.green(color) < 120) {
                val x: Int = index % bitmap.width; val y: Int = index / bitmap.width
                left = minOf(left, x); top = minOf(top, y); right = maxOf(right, x); bottom = maxOf(bottom, y); count++
            }
        }
        return Mask(left, top, right + 1, bottom + 1, count)
    }

    private fun withinFivePercent(value: Int, reference: Int): Boolean =
        abs(value - reference).toFloat() / reference <= .05f

    private data class Mask(val left: Int, val top: Int, val right: Int, val bottom: Int, val count: Int) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top
        fun json(): JSONObject = JSONObject().put("left", left).put("top", top).put("right", right)
            .put("bottom", bottom).put("width", width).put("height", height)
    }

    private data class Rendered(val color: Bitmap, val mask: Bitmap)

    private class BodyMaskCanvas(scratch: Bitmap, mask: Bitmap) : Canvas(scratch) {
        private val capture: Canvas = Canvas(mask)
        private val matrix: Matrix = Matrix()

        @Suppress("DEPRECATION")
        override fun drawBitmap(bitmap: Bitmap, source: Rect?, destination: RectF, paint: Paint?): Unit {
            getMatrix(matrix)
            capture.save(); capture.concat(matrix)
            capture.drawBitmap(bitmap, source, destination, paint)
            capture.restore()
        }
    }

    private enum class Surface(val id: String, val width: Int, val height: Int, val desktopSize: Int?) {
        DESKTOP("desktop", 320, 320, 160), HOME("home", 480, 280, null),
    }
}
