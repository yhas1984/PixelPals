package com.pixelpals.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.SeededPetRandom
import com.pixelpals.app.feature.overlay.behavior.BaseBehavior
import com.pixelpals.app.feature.overlay.behavior.CorgiBehavior
import com.pixelpals.app.feature.overlay.behavior.TestPetBridge
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Drives the real behavior and renderer, including interruption and both edges. */
@RunWith(AndroidJUnit4::class)
class CorgiTurnRenderingTest {
    @Test fun turnPlantsThenVisitsEveryPoseAt30To120FpsWithoutCameraJumps(): Unit = withActor { bridge, behavior ->
        val expected = listOf(0 to false, 20 to false, 21 to false, 22 to false, 21 to true, 20 to true, 0 to true)
        val tile = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
        try {
            for (fps in listOf(30, 60, 120)) for (left in listOf(false, true)) {
                startAtEdge(bridge, behavior, left)
                val edge = bridge.getWindowParams().x
                val seen = mutableListOf<Pair<Int, Boolean>>()
                val bottoms = mutableListOf<Int>()
                var standingHeight = 0
                var ticks = 0
                while (mode(behavior) == "TURN" && ticks++ < fps) {
                    val pose = bridge.currentFrame to ((bridge.animScaleX < 0f) != left)
                    if (seen.lastOrNull() != pose) seen += pose
                    assertEquals("Logical direction before the endpoint: fps=$fps left=$left tick=$ticks",
                        left, field("walkDirection").getFloat(behavior) < 0f)
                    assertEquals(edge, bridge.getWindowParams().x)
                    assertEquals(bridge.groundY, bridge.getWindowParams().y)
                    assertEquals(1f, bridge.animScaleY, 0f)
                    assertEquals(0f, bridge.animRotation, 0f)
                    assertEquals(0f, bridge.animOffsetX, 0f)
                    assertEquals(0f, bridge.animOffsetY, 0f)
                    tile.eraseColor(Color.TRANSPARENT)
                    behavior.onDraw(Canvas(tile), 80f, 80f)
                    val (top, bottom) = verticalBounds(tile)
                    val height = bottom - top + 1
                    if (standingHeight == 0) standingHeight = height
                    assertTrue("Pose $pose changes camera height ($standingHeight to $height)",
                        kotlin.math.abs(height - standingHeight) <= standingHeight * .05f)
                    bottoms += bottom
                    behavior.updateIdle(1f / fps)
                }
                assertEquals("fps=$fps left=$left", expected, seen)
                assertEquals("WALK", mode(behavior))
                assertEquals(0, bridge.currentFrame)
                assertEquals(!left, bridge.animScaleX < 0f)
                assertEquals(1f, kotlin.math.abs(bridge.animScaleX), 0f)
                assertEquals(edge, bridge.getWindowParams().x)
                assertTrue("Paws move off the floor: $bottoms", bottoms.max() - bottoms.min() <= 1)
                repeat(fps / 3) { behavior.updateIdle(1f / fps) }
                assertTrue(if (left) bridge.getWindowParams().x > edge else bridge.getWindowParams().x < edge)
            }
        } finally { tile.recycle() }
    }

    @Test fun interruptionAndReducedMotionCannotLeaveAnObsoleteTurnPending(): Unit = withActor { bridge, behavior ->
        for (seconds in listOf(.12f, .4f)) for (left in listOf(false, true)) {
            for (input in listOf("touch", "drag", "fling", "reset", "reduced")) {
                startAtEdge(bridge, behavior, left)
                behavior.updateIdle(seconds)
                assertEquals("TURN", mode(behavior))
                val visibleDirection = bridge.animScaleX
                when (input) {
                    "touch" -> behavior.onInteract()
                    "drag" -> behavior.updateDrag(.01f)
                    "fling" -> behavior.onFling(-1_000f, 0f)
                    "reduced" -> behavior.advanceScheduledRestTransition(.01f, true)
                    else -> behavior.reset()
                }
                assertNotEquals("$input cancels the turn", "TURN", mode(behavior))
                assertTrue(bridge.currentFrame !in 20..22)
                if (input !in listOf("fling", "reduced")) assertEquals(visibleDirection, bridge.animScaleX, 0f)
                if (input == "reduced") assertTrue(behavior.canStartScheduledSleep(true))
            }
        }
    }

    @Test fun exportActualTurnAndDepartureFrames(): Unit = withActor { bridge, behavior ->
        val directory = File(bridge.context.cacheDir, "corgi-turn-motion").apply { mkdirs() }
        val tile = Bitmap.createBitmap(640, 300, Bitmap.Config.ARGB_8888)
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 18f }
        val trace = org.json.JSONArray()
        try {
            for (left in listOf(false, true)) {
                startAtEdge(bridge, behavior, left)
                val edge = bridge.getWindowParams().x
                val direction = if (left) "left" else "right"
                repeat(45) { tick ->
                    if (tick > 0) behavior.updateIdle(1f / 30f)
                    tile.eraseColor(0xfff5ebd9.toInt())
                    val canvas = Canvas(tile)
                    behavior.onDraw(canvas, 320f + bridge.getWindowParams().x - edge, 160f)
                    canvas.drawText("$direction ${tick * 1000 / 30} ms | frame ${bridge.currentFrame}", 12f, 25f, label)
                    File(directory, "$direction-${tick.toString().padStart(3, '0')}.png").outputStream().use {
                        tile.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    trace.put(org.json.JSONObject().put("direction", direction).put("tick", tick)
                        .put("frame", bridge.currentFrame).put("scaleX", bridge.animScaleX)
                        .put("x", bridge.getWindowParams().x).put("mode", mode(behavior)))
                }
            }
            File(directory, "trace.json").writeText(trace.toString(2))
        } finally { tile.recycle() }
    }

    private fun startAtEdge(bridge: TestPetBridge, behavior: CorgiBehavior, left: Boolean) {
        behavior.resumeAfterCare(left, false)
        behavior.reset()
        bridge.getWindowParams().x = if (left) 0 else bridge.screenWidth - bridge.petSpriteSize
        behavior.updateIdle(.001f)
        assertEquals("TURN", mode(behavior))
        assertEquals(0, bridge.currentFrame)
    }

    private fun field(name: String) = CorgiBehavior::class.java.getDeclaredField(name).apply { isAccessible = true }
    private fun mode(behavior: CorgiBehavior): String = (field("mode").get(behavior) as Enum<*>).name

    private fun verticalBounds(bitmap: Bitmap): Pair<Int, Int> {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val opaque = pixels.indices.filter { Color.alpha(pixels[it]) >= 128 }
        assertTrue(opaque.isNotEmpty())
        return opaque.minOf { it / bitmap.width } to opaque.maxOf { it / bitmap.width }
    }

    private fun withActor(block: (TestPetBridge, CorgiBehavior) -> Unit): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var bridge: TestPetBridge
        lateinit var behavior: CorgiBehavior
        instrumentation.runOnMainSync {
            bridge = TestPetBridge(instrumentation.targetContext, PetType.CORGI, 160)
            behavior = CorgiBehavior(bridge, SeededPetRandom(53))
        }
        try {
            val loading = BaseBehavior::class.java.getDeclaredField("isLoading").apply { isAccessible = true }
            repeat(100) { if (loading.getBoolean(behavior)) delay(50) }
            assertFalse(loading.getBoolean(behavior))
            instrumentation.runOnMainSync {
                val rects = BaseBehavior::class.java.getDeclaredField("spriteFrameRects").apply { isAccessible = true }.get(behavior) as List<*>
                assertEquals(23, rects.size)
                block(bridge, behavior)
            }
        } finally { instrumentation.runOnMainSync { behavior.destroy() } }
    }
}
