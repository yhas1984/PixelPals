package com.pixelpals.app.feature.overlay.behavior

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.PetRandom
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class GingerPostureTransitionTest {
    @Test fun realWalkAndStalkEntriesPlantAtEveryRateAndWalkEndsSeated(): Unit = withPet { bridge, behavior ->
        val sheet = Bitmap.createBitmap(320 * 4, 320 * 2, Bitmap.Config.ARGB_8888)
        try {
            for (fps in listOf(30, 60, 120)) for (left in listOf(false, true)) for (action in listOf("startWalk", "startStalk")) {
                bridge.getWindowParams().apply { x = if (left) 700 else 100; y = bridge.groundY }
                setFloat(behavior, "facingDirection", if (left) -1f else 1f)
                behavior.reset()
                invoke(behavior, action)
                assertEquals("STAND_UP", mode(behavior))
                val start = bridge.getWindowParams().x
                val frames = mutableListOf(bridge.currentFrame)
                var ticks = 0
                while (mode(behavior) == "STAND_UP" && ticks++ < fps) {
                    behavior.updateIdle(1f / fps)
                    assertPlanted(bridge, start, left)
                    if (frames.last() != bridge.currentFrame) frames += bridge.currentFrame
                }
                assertEquals(listOf(0, 16, 17, 18), frames)
                assertEquals(if (action == "startWalk") "WALK" else "STALK", mode(behavior))
                assertEquals(18, bridge.currentFrame)
                if (fps == 30 && action == "startWalk") {
                    for ((column, frame) in listOf(0, 16, 17, 18).withIndex()) {
                        bridge.currentFrame = frame
                        val tile = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
                        behavior.onDraw(Canvas(tile), 160f, 280f)
                        var bottom = -1
                        for (y in 0 until 320) for (x in 0 until 320)
                            if (Color.alpha(tile.getPixel(x, y)) >= 128) bottom = maxOf(bottom, y)
                        assertTrue("Drawn support must remain on the same floor: frame $frame bottom $bottom", bottom in 278..280)
                        Canvas(sheet).drawBitmap(tile, column * 320f, if (left) 320f else 0f, null)
                        tile.recycle()
                    }
                }
                ticks = 0
                val walkingMode = mode(behavior)
                while (mode(behavior) == walkingMode && ticks++ < fps * 8) behavior.updateIdle(1f / fps)
                assertTrue("Travel must move toward its planned destination", if (left) bridge.windowX < start else bridge.windowX > start)
                if (action == "startWalk") {
                    assertEquals("SIT_DOWN", mode(behavior))
                    val end = bridge.windowX
                    val sittingFrames = mutableListOf(bridge.currentFrame)
                    ticks = 0
                    while (mode(behavior) == "SIT_DOWN" && ticks++ < fps) {
                        behavior.updateIdle(1f / fps)
                        assertPlanted(bridge, end, left)
                        if (sittingFrames.last() != bridge.currentFrame) sittingFrames += bridge.currentFrame
                    }
                    assertEquals(listOf(18, 17, 16, 0), sittingFrames)
                    assertEquals("SIT", mode(behavior))
                    assertTrue(behavior.isSeatedForScheduledRest)
                } else assertEquals("POUNCE_COIL", mode(behavior))
            }
            File(instrumentation.targetContext.cacheDir, "ginger-desktop-postures.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { sheet.recycle() }
    }

    @Test fun interruptionsKeepActualWindowPositionAndDiscardPendingActions(): Unit = withPet { bridge, behavior ->
        for (posture in listOf("up", "down")) for (seconds in listOf(.05f, .4f)) for (input in listOf("drag", "fling", "reset", "touch")) {
            bridge.getWindowParams().apply { x = 400; y = bridge.groundY }
            behavior.reset()
            if (posture == "up") invoke(behavior, "startWalk") else {
                GingerBehavior::class.java.getDeclaredMethod("startSitDown", Float::class.javaPrimitiveType)
                    .apply { isAccessible = true }.invoke(behavior, 1.5f)
            }
            repeat((seconds * 60).toInt().coerceAtLeast(1)) { behavior.updateIdle(1f / 60f) }
            assertTrue(mode(behavior) in listOf("STAND_UP", "SIT_DOWN"))
            val params = bridge.getWindowParams().apply { x = 460; y = bridge.groundY - 220 }
            bridge.updateWindowLayout(params)
            when (input) {
                "drag" -> behavior.updateDrag(.016f)
                "fling" -> behavior.onFling(900f, -300f)
                "reset" -> behavior.reset()
                else -> behavior.onInteract()
            }
            assertEquals("$input $posture must not teleport X", 460, params.x)
            assertEquals("$input $posture must not teleport Y", bridge.groundY - 220, params.y)
            val callback = GingerBehavior::class.java.getDeclaredField("postureCompletion").apply { isAccessible = true }.get(behavior)
            assertNull("Interrupted action must not resume", callback)
            if (input != "drag") {
                assertEquals("AIRBORNE", mode(behavior))
                assertEquals(12, bridge.currentFrame)
                repeat(10) { behavior.updateIdle(1f / 60f); assertEquals("AIRBORNE", mode(behavior)) }
            } else assertEquals(15, bridge.currentFrame)
        }
    }

    @Test fun reducedMotionAndScheduledWakeFinishWithoutRepeatingTheRise(): Unit = withPet { bridge, behavior ->
        bridge.getWindowParams().apply { x = 400; y = bridge.groundY }
        behavior.reset()
        for (action in listOf("startWalk", "startStalk")) {
            behavior.reset()
            invoke(behavior, action)
            behavior.updateIdle(.2f)
            behavior.advanceScheduledRestTransition(.016f, true)
            assertEquals(18, bridge.currentFrame)
            assertTrue(behavior.canStartScheduledSleep(true))
        }
        behavior.onScheduledWakeCompleted()
        assertEquals("STANDING", mode(behavior))
        assertEquals(18, bridge.currentFrame)
        repeat(20) { behavior.updateIdle(1f / 60f); assertEquals(18, bridge.currentFrame) }
        behavior.updateIdle(.05f)
        assertEquals("WALK", mode(behavior))
        assertEquals(18, bridge.currentFrame)
        val field = GingerBehavior::class.java.getDeclaredField("mode").apply { isAccessible = true }
        field.set(behavior, field.type.enumConstants!!.first { it.toString() == "SLEEP" })
        behavior.onScheduledSleepInterrupted(21)
        assertEquals(21, behavior.scheduledRestFrame)
        assertEquals(21, bridge.currentFrame)
    }

    private fun assertPlanted(bridge: TestPetBridge, x: Int, left: Boolean) {
        assertEquals(x, bridge.getWindowParams().x)
        assertEquals(bridge.groundY, bridge.getWindowParams().y)
        assertEquals(if (left) 1f else -1f, bridge.animScaleX, 0f)
        assertEquals(1f, bridge.animScaleY, 0f)
        assertEquals(0f, bridge.animRotation, 0f)
        assertEquals(0f, bridge.animOffsetY, 0f)
    }
    private fun withPet(test: (TestPetBridge, GingerBehavior) -> Unit): Unit = runBlocking {
        lateinit var bridge: TestPetBridge
        lateinit var behavior: GingerBehavior
        instrumentation.runOnMainSync {
            bridge = TestPetBridge(instrumentation.targetContext, PetType.GINGER, 160, .875f)
            behavior = GingerBehavior(bridge, object : PetRandom {
                override fun nextFloat(): Float = .25f
                override fun nextInt(from: Int, until: Int): Int = from
            })
        }
        try {
            val loading = BaseBehavior::class.java.getDeclaredField("isLoading").apply { isAccessible = true }
            repeat(120) { if (loading.getBoolean(behavior)) delay(50) }
            instrumentation.runOnMainSync {
                val rects = BaseBehavior::class.java.getDeclaredField("spriteFrameRects").apply { isAccessible = true }.get(behavior) as List<*>
                assertEquals("Use real debug posture and turn assets", 24, rects.size)
                test(bridge, behavior)
            }
        } finally { instrumentation.runOnMainSync { behavior.destroy() } }
    }
    private fun mode(behavior: GingerBehavior): String = (GingerBehavior::class.java.getDeclaredField("mode").apply { isAccessible = true }.get(behavior) as Enum<*>).name
    private fun setFloat(behavior: GingerBehavior, name: String, value: Float) = GingerBehavior::class.java.getDeclaredField(name).apply { isAccessible = true }.setFloat(behavior, value)
    private fun invoke(behavior: GingerBehavior, name: String) { GingerBehavior::class.java.getDeclaredMethod(name).apply { isAccessible = true }.invoke(behavior) }
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
}
