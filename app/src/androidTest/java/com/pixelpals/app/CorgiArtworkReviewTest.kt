package com.pixelpals.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.care.scene.CareSceneAction
import com.pixelpals.app.core.care.scene.CorgiAdditionalCareMotion
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.SeededPetRandom
import com.pixelpals.app.feature.care.CarePoseLoader
import com.pixelpals.app.feature.care.CorgiDesktopCareRenderer
import com.pixelpals.app.feature.overlay.behavior.BaseBehavior
import com.pixelpals.app.feature.overlay.behavior.CorgiBehavior
import com.pixelpals.app.feature.overlay.behavior.TestPetBridge
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Review artifacts, not an automated claim of anatomical quality. No progress mutations. */
@RunWith(AndroidJUnit4::class)
class CorgiArtworkReviewTest {
    @Test fun exportOriginalBankAndCareHandoffsAtTheSameActorSize(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        lateinit var bridge: TestPetBridge
        lateinit var behavior: CorgiBehavior
        instrumentation.runOnMainSync {
            bridge = TestPetBridge(context, PetType.CORGI, 160)
            behavior = CorgiBehavior(bridge, SeededPetRandom(8))
        }
        val pack = CarePoseLoader.load(context.assets, PetType.CORGI)
        val tile: Bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
        val renderer: CorgiDesktopCareRenderer = CorgiDesktopCareRenderer()
        val label: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 18f }
        try {
            val loading = BaseBehavior::class.java.getDeclaredField("isLoading").apply { isAccessible = true }
            repeat(100) { if (loading.getBoolean(behavior)) delay(50) }
            assertFalse(loading.getBoolean(behavior))
            instrumentation.runOnMainSync {
                val spriteRects = BaseBehavior::class.java.getDeclaredField("spriteFrameRects").apply { isAccessible = true }
                    .get(behavior) as List<*>
                val hasTransitionAtlas: Boolean = spriteRects.size == 23
                fun export(name: String, titles: List<String>, draw: (Canvas, Int) -> Unit) {
                    val sheet: Bitmap = Bitmap.createBitmap(320 * 7, 320 * ((titles.size + 6) / 7), Bitmap.Config.ARGB_8888)
                    try {
                        sheet.eraseColor(Color.rgb(250, 247, 239))
                        val canvas: Canvas = Canvas(sheet)
                        titles.forEachIndexed { index, title ->
                            tile.eraseColor(Color.TRANSPARENT)
                            draw(Canvas(tile), index)
                            val x: Float = (index % 7) * 320f
                            val y: Float = (index / 7) * 320f
                            canvas.drawBitmap(tile, x, y, null)
                            canvas.drawText(title, x + 8f, y + 25f, label)
                            canvas.drawLine(x, y + 234f, x + 320f, y + 234f, label)
                        }
                        File(context.cacheDir, name).outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    } finally { sheet.recycle() }
                }
                val originalTitles: List<String> = (0..22).map { frame ->
                    when (frame) {
                        14 -> "REFERENCE 14"
                        in 15..19 -> "TRANSITION $frame"
                        in 20..22 -> "TURN $frame"
                        else -> "ORIGINAL $frame"
                    }
                }.let { if (hasTransitionAtlas) it else it.take(14) }
                export("corgi-original-bank.png", originalTitles) { canvas, index ->
                    bridge.currentFrame = index
                    behavior.onDraw(canvas, 160f, 160f)
                }
                if (hasTransitionAtlas) {
                    val transitionSamples: List<Pair<Boolean, Long>> = listOf(
                        false to 0L, false to 650L, false to 900L, false to 1_170L,
                        true to 0L, true to 650L, true to 900L, true to 1_170L,
                    )
                    export("corgi-rest-transitions.png", transitionSamples.map { (left, ms) ->
                        "${if (left) "LEFT" else "RIGHT"} REST $ms ms"
                    }) { canvas, index ->
                        val (left, ms) = transitionSamples[index]
                        behavior.resumeAfterCare(left, true)
                        repeat((ms / 16L).toInt()) { behavior.updateIdle(16f / 1_000f) }
                        behavior.onDraw(canvas, 160f, 160f)
                    }
                    val modeClass = CorgiBehavior::class.java.declaredClasses.first { it.simpleName == "Mode" }
                    val rest = modeClass.enumConstants.first { (it as Enum<*>).name == "REST" }
                    val begin = CorgiBehavior::class.java.getDeclaredMethod(
                        "beginActionAfterPlant", modeClass, Float::class.javaPrimitiveType
                    ).apply { isAccessible = true }
                    val motionDirectory = File(context.cacheDir, "corgi-sit-motion").apply { mkdirs() }
                    val motionTile = Bitmap.createBitmap(640, 320, Bitmap.Config.ARGB_8888)
                    val trace = org.json.JSONArray()
                    try {
                        for (left in listOf(false, true)) {
                            behavior.resumeAfterCare(left, false)
                            bridge.getWindowParams().x = 320
                            begin.invoke(behavior, rest, 1.25f)
                            repeat(120) { tick ->
                                if (tick > 0) behavior.updateIdle(1f / 30f)
                                motionTile.eraseColor(0xfff5ebd9.toInt())
                                val canvas = Canvas(motionTile)
                                behavior.onDraw(canvas, bridge.getWindowParams().x.toFloat(), 160f)
                                val heading = if (left) "left" else "right"
                                canvas.drawText("$heading ${tick * 1000 / 30} ms | frame ${bridge.currentFrame}", 12f, 25f, label)
                                File(motionDirectory, "${heading}_${tick.toString().padStart(3, '0')}.png").outputStream().use {
                                    motionTile.compress(Bitmap.CompressFormat.PNG, 100, it)
                                }
                                trace.put(org.json.JSONObject().put("left", left).put("seconds", tick / 30f)
                                    .put("frame", bridge.currentFrame).put("x", bridge.getWindowParams().x))
                            }
                        }
                        File(motionDirectory, "trace.json").writeText(trace.toString(2))
                    } finally { motionTile.recycle() }
                    val actionSamples: List<Long> = listOf(0L, 180L, 650L, 900L, 1_170L)
                    export("corgi-rest-action-transition.png", actionSamples.map { "ACTION REST $it ms" }) { canvas, index ->
                        val elapsed: Long = actionSamples[index]
                        begin.invoke(behavior, rest, 2.2f)
                        repeat((elapsed / 16L).toInt()) { behavior.updateIdle(16f / 1_000f) }
                        behavior.onDraw(canvas, 160f, 160f)
                    }
                }
                val samples: List<Pair<CareSceneAction, Long>> = listOf(CareSceneAction.FEED, CareSceneAction.PET,
                    CareSceneAction.CLEAN, CareSceneAction.REST, CareSceneAction.MEDICINE).flatMap { action ->
                    val duration: Long = if (action in CorgiAdditionalCareMotion.actions)
                        CorgiAdditionalCareMotion.getTiming(action).durationMs else if (action == CareSceneAction.FEED) 5600L
                        else pack.spec.timings.getValue(action).durationMs
                    (0..6).map { action to duration * it / 6 }
                }
                export("corgi-care-handoffs.png", samples.map { "${it.first} ${it.second} ms" }) { canvas, index ->
                    val (action, elapsed) = samples[index]
                    renderer.draw(canvas, pack, 160, elapsed, false, false, action)
                }
                fun bodyHeight(): Int {
                    val rows: List<Int> = (0 until tile.height).filter { y ->
                        (0 until tile.width).any { x -> Color.alpha(tile.getPixel(x, y)) >= 128 }
                    }
                    assertTrue("Actual body pixels required", rows.isNotEmpty())
                    return rows.last() - rows.first() + 1
                }
                tile.eraseColor(Color.TRANSPARENT)
                bridge.currentFrame = 6
                behavior.onDraw(Canvas(tile), 160f, 160f)
                val originalHeight: Int = bodyHeight()
                // Only compare upright endpoints: a curled sleeping silhouette should be shorter.
                // Bath cells have their own source camera; matching the action label alone is insufficient.
                for ((action, elapsed) in listOf(CareSceneAction.REST to 7000L,
                    CareSceneAction.CLEAN to 0L, CareSceneAction.CLEAN to 4000L,
                    CareSceneAction.MEDICINE to 4000L)) {
                    tile.eraseColor(Color.TRANSPARENT)
                    renderer.draw(Canvas(tile), pack, 160, elapsed, false, false, action)
                    val careHeight: Int = bodyHeight()
                    val tolerance: Float = if (action == CareSceneAction.CLEAN) .03f else .1f
                    assertTrue("$action seated handoff shrank: $originalHeight -> $careHeight", careHeight >= originalHeight * (1f - tolerance))
                    assertTrue("$action seated handoff grew: $originalHeight -> $careHeight", careHeight <= originalHeight * (1f + tolerance))
                }
            }
        } finally {
            tile.recycle(); pack.bitmap.recycle()
            instrumentation.runOnMainSync { behavior.destroy() }
        }
    }
}
