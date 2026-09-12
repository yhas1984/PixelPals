package com.pixelpals.app.feature.overlay.behavior

import android.app.Instrumentation
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetState
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.PetRandom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises Patito's complete interaction flight without creating an overlay window. */
@RunWith(AndroidJUnit4::class)
class DuckDesktopMotionTest {
    @Test fun interactionFliesLandsAndReturnsToIdleWithinBounds(): Unit {
        assumeTrue(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("Emulator"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var bridge: TestPetBridge
        lateinit var behavior: DuckBehavior
        instrumentation.runOnMainSync {
            bridge = TestPetBridge(instrumentation.targetContext, PetType.PATITO)
            bridge.getWindowParams()?.y = bridge.groundY
            bridge.windowY = bridge.groundY
            behavior = DuckBehavior(bridge, deterministicRandom())
        }
        try {
            waitForAssets(behavior, instrumentation)
            instrumentation.runOnMainSync {
                behavior.onInteract()
                val observed = linkedSetOf<String>()
                var previousY = bridge.windowY
                var previousRotation = bridge.animRotation
                var completed = false
                repeat(60 * 12) {
                    if (completed) return@repeat
                    behavior.updateInteracting(1f / 60f)
                    val mode = modeName(behavior)
                    observed += mode
                    assertTrue("Duck x escaped bounds: ${bridge.windowX}", bridge.windowX in bridge.bounds.left..bridge.bounds.right)
                    assertTrue("Duck y escaped bounds: ${bridge.windowY}", bridge.windowY in bridge.bounds.top..bridge.bounds.floor)
                    // Allow three body lengths/second plus pixel rounding at 60 Hz.
                    assertTrue("Duck flight jumped vertically", kotlin.math.abs(bridge.windowY - previousY) <= bridge.petSpriteSize * 3f / 60f + 1f)
                    assertTrue("Duck pitch snapped at a flight transition", kotlin.math.abs(bridge.animRotation - previousRotation) <= 3f)
                    assertEquals("Duck $mode scaleX changed body size", 1f, kotlin.math.abs(bridge.animScaleX), .001f)
                    assertEquals("Duck $mode scaleY changed body size", 1f, bridge.animScaleY, .001f)
                    if (mode == "LAND_END" || mode == "QUACK") {
                        assertEquals("Duck $mode should have no vertical render offset", 0f, bridge.animOffsetY, .001f)
                    }
                    previousY = bridge.windowY
                    previousRotation = bridge.animRotation
                    completed = mode == "WADDLE" && observed.contains("QUACK")
                }
                assertEquals(setOf("TAKEOFF", "FLUTTER", "LANDING", "LAND_END", "QUACK", "WADDLE"), observed)
                assertEquals("Completed duck interaction should return to IDLE", PetState.IDLE, bridge.state)
                assertEquals("Duck should land on ground before swimming", bridge.groundY, bridge.windowY)
            }
        } finally {
            instrumentation.runOnMainSync { behavior.destroy() }
        }
    }

    @Test fun flingFromTopBoundaryCompletesAtLeftCenterAndRight(): Unit {
        assumeTrue(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("Emulator"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val positions = listOf(
            Triple(0, 1_800f, "left"),
            Triple(540, -1_800f, "center"),
            Triple(1_000, -1_800f, "right"),
        )
        positions.forEach { (x, velocityX, label) ->
            lateinit var bridge: TestPetBridge
            lateinit var behavior: DuckBehavior
            instrumentation.runOnMainSync {
                bridge = TestPetBridge(instrumentation.targetContext, PetType.PATITO)
                val params = bridge.getWindowParams()!!
                params.x = x.coerceIn(bridge.bounds.left, bridge.bounds.right)
                params.y = bridge.bounds.top
                bridge.windowX = params.x
                bridge.windowY = params.y
                behavior = DuckBehavior(bridge, deterministicRandom())
            }
            try {
                waitForAssets(behavior, instrumentation)
                instrumentation.runOnMainSync {
                    behavior.onFling(velocityX, -1_250f)
                    val observed = linkedSetOf<String>()
                    var completed = false
                    // The full-height descent is speed-limited and can exceed 12 seconds.
                    repeat(60 * 30) {
                        if (completed) return@repeat
                        behavior.updateInteracting(1f / 60f)
                        val mode = modeName(behavior)
                        observed += mode
                        assertTrue("Duck $label x escaped bounds: ${bridge.windowX}", bridge.windowX in bridge.bounds.left..bridge.bounds.right)
                        assertTrue("Duck $label y escaped bounds: ${bridge.windowY}", bridge.windowY in bridge.bounds.top..bridge.bounds.floor)
                        assertTrue("Duck $label render crossed top bound", bridge.windowY + bridge.animOffsetY >= bridge.bounds.top - .01f)
                        completed = mode == "WADDLE" && observed.contains("QUACK")
                    }
                    assertEquals("Duck $label fling did not complete", setOf("TAKEOFF", "FLUTTER", "LANDING", "LAND_END", "QUACK", "WADDLE"), observed)
                    assertEquals("Duck $label fling should return to IDLE", PetState.IDLE, bridge.state)
                    assertEquals("Duck $label fling should land at ground", bridge.groundY, bridge.windowY)
                }
            } finally {
                instrumentation.runOnMainSync { behavior.destroy() }
            }
        }
    }

    @Test fun releaseFromMidairUsesProgressiveLandingAt30And120Fps(): Unit {
        assumeTrue(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("Emulator"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        for (fps in listOf(30, 120)) {
            lateinit var bridge: TestPetBridge
            lateinit var behavior: DuckBehavior
            instrumentation.runOnMainSync {
                bridge = TestPetBridge(instrumentation.targetContext, PetType.PATITO)
                bridge.getWindowParams().y = bridge.groundY / 2
                bridge.windowY = bridge.groundY / 2
                behavior = DuckBehavior(bridge, deterministicRandom())
            }
            try {
                waitForAssets(behavior, instrumentation)
                instrumentation.runOnMainSync {
                    behavior.updateDrag(0f)
                    behavior.onRelease(0f, 0f)
                    var previousY = bridge.windowY
                    var landed = false
                    for (tick in 0 until fps * 12) {
                        behavior.updateInteracting(1f / fps)
                        val y = bridge.windowY
                        assertTrue("Landing exceeded speed at ${fps}fps", y - previousY <= bridge.petSpriteSize * 2.5f / fps + 1f)
                        assertTrue("Landing reversed vertically", y >= previousY)
                        previousY = y
                        if (modeName(behavior) == "QUACK") { landed = true; break }
                    }
                    assertTrue("Release did not reach QUACK at ${fps}fps", landed)
                    assertEquals(bridge.groundY, bridge.windowY)
                    assertEquals(8, bridge.currentFrame)
                }
            } finally { instrumentation.runOnMainSync { behavior.destroy() } }
        }
    }

    @Test fun flutterUsesWingFramesAndQuackUsesPlantedFeet() {
        assumeTrue(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("Emulator"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var bridge: TestPetBridge
        lateinit var behavior: DuckBehavior
        instrumentation.runOnMainSync {
            bridge = TestPetBridge(instrumentation.targetContext, PetType.PATITO)
            bridge.getWindowParams().y = bridge.groundY
            behavior = DuckBehavior(bridge, deterministicRandom())
        }
        try {
            waitForAssets(behavior, instrumentation)
            instrumentation.runOnMainSync {
                behavior.onInteract()
                val wingFrames = mutableSetOf<Int>()
                var paused = false
                for (tick in 0 until 720) {
                    behavior.updateInteracting(1f / 60f)
                    when (modeName(behavior)) {
                        "FLUTTER" -> { assertTrue(bridge.currentFrame == 4 || bridge.currentFrame == 9); wingFrames += bridge.currentFrame }
                        "QUACK" -> { assertEquals(8, bridge.currentFrame); paused = true; break }
                    }
                }
                assertEquals(setOf(4, 9), wingFrames)
                assertTrue("Flight must reach a planted pause", paused)
            }
        } finally { instrumentation.runOnMainSync { behavior.destroy() } }
    }

    @Test fun draggingFromWaddleKeepsWingFrameAndLeftHeading() {
        assumeTrue(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("Emulator"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var bridge: TestPetBridge
        lateinit var behavior: DuckBehavior
        instrumentation.runOnMainSync {
            bridge = TestPetBridge(instrumentation.targetContext, PetType.PATITO)
            behavior = DuckBehavior(bridge, deterministicRandom())
            bridge.animScaleX = -1f
        }
        try {
            waitForAssets(behavior, instrumentation)
            instrumentation.runOnMainSync {
                repeat(30) {
                    behavior.updateDrag(1f / 60f)
                    assertEquals(9, bridge.currentFrame)
                    assertEquals(-1f, bridge.animScaleX, 0f)
                }
                val heldY = bridge.windowY
                behavior.reset()
                assertEquals("Cancelled midair drag must finish even with reduced idle motion", PetState.INTERACTING, bridge.state)
                assertEquals(heldY, bridge.windowY)
                assertEquals(-1f, bridge.animScaleX, 0f)
            }
        } finally { instrumentation.runOnMainSync { behavior.destroy() } }
    }

    @Test fun walkingUsesOrderedSixPoseAtlasGaitAt30And120Fps() {
        assumeTrue(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("Emulator"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        for (fps in listOf(30, 120)) {
            lateinit var bridge: TestPetBridge
            lateinit var behavior: DuckBehavior
            instrumentation.runOnMainSync {
                bridge = TestPetBridge(instrumentation.targetContext, PetType.PATITO)
                val params = bridge.getWindowParams()!!
                params.x = 360
                params.y = bridge.groundY
                bridge.updateWindowLayout(params)
                behavior = DuckBehavior(bridge, deterministicRandom())
            }
            try {
                waitForAssets(behavior, instrumentation)
                instrumentation.runOnMainSync {
                    val ordered = mutableListOf<Int>()
                    var previousX = bridge.windowX
                    var reachedStop = false
                    for (tick in 0 until fps * 10) {
                        behavior.updateIdle(1f / fps)
                        val mode = modeName(behavior)
                        val frame = bridge.currentFrame
                        assertTrue("Patito must use walk or neutral frames at ${fps}fps", frame == 8 || frame in 10..15)
                        assertEquals("Patito walk must preserve height scale at ${fps}fps", 1f, bridge.animScaleY, 0f)
                        assertEquals("Patito walk must preserve body width at ${fps}fps", 1f, kotlin.math.abs(bridge.animScaleX), 0f)
                        assertTrue(
                            "Patito walk exceeded 95 px/s at ${fps}fps",
                            kotlin.math.abs(bridge.windowX - previousX) <= 95f / fps + 2f,
                        )
                        if (mode == "WADDLE" && frame in 10..15 && ordered.lastOrNull() != frame) ordered += frame
                        previousX = bridge.windowX
                        if (mode == "QUACK") {
                            reachedStop = true
                            break
                        }
                    }
                    assertTrue("Patito walk must reach its destination and stop", reachedStop)
                    assertTrue("Patito walk must cover about 300 px", bridge.windowX <= 3)
                    assertEquals("Patito must stop on the planted frame", 8, bridge.currentFrame)
                    assertTrue("Patito must expose all six walk poses", ordered.containsAll((10..15).toList()))
                    assertTrue(
                        "Patito walk poses must appear in atlas order",
                        ordered.zipWithNext().all { (from, to) -> to == from + 1 || (from == 15 && to == 10) },
                    )
                }
            } finally {
                instrumentation.runOnMainSync { behavior.destroy() }
            }
        }
    }

    private fun waitForAssets(behavior: DuckBehavior, instrumentation: Instrumentation): Unit {
        val owner = BaseBehavior::class.java
        val loading = owner.getDeclaredField("isLoading").apply { isAccessible = true }
        val sheet = owner.getDeclaredField("spriteSheetBitmap").apply { isAccessible = true }
        val frames = owner.getDeclaredField("frames").apply { isAccessible = true }
        val rects = owner.getDeclaredField("spriteFrameRects").apply { isAccessible = true }
        var loaded = false
        for (attempt in 0 until 80) {
            instrumentation.runOnMainSync {
                val legacy = frames.get(behavior) as List<*>
                loaded = !loading.getBoolean(behavior) &&
                    ((sheet.get(behavior) != null && (rects.get(behavior) as List<*>).size == 16) ||
                        (legacy.size == 10 && legacy.all { it != null }))
            }
            if (loaded) break
            Thread.sleep(50)
        }
        assertTrue("Duck's real artwork must load before motion tests", loaded)
    }

    private fun modeName(behavior: DuckBehavior): String =
        behavior.javaClass.getDeclaredField("mode").apply { isAccessible = true }.get(behavior).toString()

    private fun deterministicRandom(): PetRandom = object : PetRandom {
        override fun nextFloat(): Float = .5f
        override fun nextInt(from: Int, until: Int): Int = from
    }
}
