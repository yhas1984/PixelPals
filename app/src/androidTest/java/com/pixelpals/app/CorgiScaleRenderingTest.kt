package com.pixelpals.app

import android.graphics.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.SeededPetRandom
import com.pixelpals.app.feature.overlay.behavior.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Exercises the real desktop renderer, including its asynchronously decoded original frames. */
@RunWith(AndroidJUnit4::class)
class CorgiScaleRenderingTest {
    @Test fun runningAnticipatesWithoutShrinkingOrSlidingAndTouchKeepsBodyProportions(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val bridge: TestPetBridge = TestPetBridge(instrumentation.targetContext, PetType.CORGI)
            val behavior: CorgiBehavior = CorgiBehavior(bridge, SeededPetRandom(8))
            try {
                for (direction: Float in listOf(-1f, 1f)) {
                    behavior.resumeAfterCare(direction < 0f, false)
                    bridge.getWindowParams().x = 500
                    behavior.onFling(direction * 1_000f, 0f)
                    repeat(8) {
                        behavior.updateInteracting(1f / 60f)
                        assertEquals("Anticipation stays on planted feet", 500, bridge.getWindowParams().x)
                        assertEquals("No differently framed lying pose", 0, bridge.currentFrame)
                        assertEquals(direction, bridge.animScaleX, 0f)
                        assertEquals(1f, bridge.animScaleY, 0f)
                    }
                    repeat(24) {
                        behavior.updateInteracting(1f / 60f)
                        assertNotEquals(9, bridge.currentFrame)
                        assertEquals(direction, bridge.animScaleX, 0f)
                        assertEquals(1f, bridge.animScaleY, 0f)
                    }
                    assertTrue(if (direction < 0f) bridge.getWindowParams().x < 500 else bridge.getWindowParams().x > 500)
                    behavior.onInteract()
                    repeat(60) {
                        behavior.updateInteracting(1f / 60f)
                        assertEquals("Affection must not inflate the whole dog", 1f, bridge.animScaleY, 0f)
                    }
                }
            } finally { behavior.destroy() }
        }
    }

    @Test fun shortRunsBrakeBeforeBothEdgesAndFinishOnPlantedFeet() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val bridge = TestPetBridge(instrumentation.targetContext, PetType.CORGI)
            val behavior = CorgiBehavior(bridge, SeededPetRandom(8))
            try {
                for (fps in listOf(30, 60, 120)) for (direction in listOf(-1f, 1f)) for (distance in listOf(0, 12)) {
                    behavior.resumeAfterCare(direction < 0f, false)
                    val edge = if (direction < 0) 0 else bridge.screenWidth - bridge.petSpriteSize
                    bridge.getWindowParams().x = edge - (direction * distance).toInt()
                    behavior.onFling(direction * 1000f, 0f)
                    var elapsed = 0f
                    var lastStep = 0
                    while (bridge.state != com.pixelpals.app.core.domain.PetState.IDLE && elapsed < 1.1f) {
                        val previous = bridge.getWindowParams().x
                        behavior.updateInteracting(1f / fps)
                        elapsed += 1f / fps
                        val x = bridge.getWindowParams().x
                        lastStep = kotlin.math.abs(x - previous)
                        assertTrue(x in 0..(bridge.screenWidth - bridge.petSpriteSize))
                        assertTrue("No reverse slide", (x - previous) * direction >= 0)
                        if (distance == 0) assertEquals("Do not cycle paws against the edge", 0, bridge.currentFrame)
                    }
                    assertTrue("The edge must not abruptly cut off the run", elapsed >= .9f)
                    assertEquals(edge, bridge.getWindowParams().x)
                    assertTrue("Reach the endpoint at rest", lastStep <= 1)
                    assertEquals("Plant before sniffing", 0, bridge.currentFrame)
                    assertEquals(1f, bridge.animScaleY, 0f)
                }
            } finally { behavior.destroy() }
        }
    }

    @Test fun careExitAndEdgePauseUsePlantedFeetBeforeWalking(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var bridge: TestPetBridge
        lateinit var behavior: CorgiBehavior
        instrumentation.runOnMainSync {
            bridge = TestPetBridge(instrumentation.targetContext, PetType.CORGI)
            behavior = CorgiBehavior(bridge, SeededPetRandom(8))
        }
        try {
            val loading = BaseBehavior::class.java.getDeclaredField("isLoading").apply { isAccessible = true }
            repeat(100) { if (loading.getBoolean(behavior)) delay(50) }
            assertFalse(loading.getBoolean(behavior))
            instrumentation.runOnMainSync {
                val spriteRects = BaseBehavior::class.java.getDeclaredField("spriteFrameRects").apply { isAccessible = true }
                    .get(behavior) as List<*>
                val hasTransitionAtlas: Boolean = spriteRects.size == 23
                for (left: Boolean in listOf(false, true)) for (resting: Boolean in listOf(false, true)) {
                    val position = bridge.getWindowParams()
                    position.x = 500
                    position.y = bridge.groundY
                    behavior.resumeAfterCare(left, resting)
                    assertEquals(if (resting && hasTransitionAtlas) 17 else if (resting) 6 else 0, bridge.currentFrame)
                    val initialPauseFrames: Int = if (resting && hasTransitionAtlas) 39 else 30
                    repeat(initialPauseFrames) {
                        behavior.updateIdle(1f / 60f)
                        assertEquals("No sliding during the upright pause", 500, position.x)
                        assertEquals(bridge.groundY, position.y)
                        assertEquals(if (left) -1f else 1f, bridge.animScaleX, 0f)
                        if (resting && hasTransitionAtlas) {
                            assertTrue("Rest transition must use reviewed seated poses", bridge.currentFrame in 15..19)
                        } else {
                            assertEquals(if (resting) 6 else 0, bridge.currentFrame)
                        }
                        assertEquals("A stationary dog must not rock away from its paws", 0f, bridge.animRotation, 0f)
                    }
                    if (resting && hasTransitionAtlas) {
                        // The seated hold is .65s, followed by .52s of stand-up;
                        // the first walking frame must not move the window early.
                        repeat(31) {
                            behavior.updateIdle(1f / 60f)
                            assertEquals("Stand-up must remain planted", 500, position.x)
                        }
                        assertEquals("Stand-up ends on the upright original", 0, bridge.currentFrame)
                    }
                    repeat(40) { behavior.updateIdle(1f / 60f) }
                    assertTrue("Resume in the fetch direction", if (left) position.x < 500 else position.x > 500)
                    val edge: Int = if (left) 0 else bridge.screenWidth - bridge.petSpriteSize
                    position.x = edge
                    behavior.reset()
                    // This tick plants in the old direction. The dedicated turn
                    // test checks every intermediate pose and orientation.
                    behavior.updateIdle(1f / 60f)
                    assertEquals(0, bridge.currentFrame)
                    assertEquals(if (hasTransitionAtlas) (if (left) -1f else 1f) else (if (left) 1f else -1f), bridge.animScaleX, 0f)
                    repeat(if (hasTransitionAtlas) 33 else 15) {
                        behavior.updateIdle(1f / 60f)
                        assertTrue("No walking frame flashed at the edge", bridge.currentFrame in listOf(0, 20, 21, 22))
                        assertEquals("Pause must not slide", edge, position.x)
                    }
                    repeat(20) { behavior.updateIdle(1f / 60f) }
                    assertTrue("Leave the edge facing inward", if (left) position.x > edge else position.x < edge)
                }
            }
        } finally { instrumentation.runOnMainSync { behavior.destroy() } }
    }

    @Test fun gaitKeepsStandingBodyScaleAndFloorInBothDirections(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var bridge: TestPetBridge
        lateinit var behavior: CorgiBehavior
        instrumentation.runOnMainSync {
            bridge = TestPetBridge(instrumentation.targetContext, PetType.CORGI)
            behavior = CorgiBehavior(bridge, SeededPetRandom(8))
        }
        try {
            val loading = BaseBehavior::class.java.getDeclaredField("isLoading").apply { isAccessible = true }
            repeat(100) { if (loading.getBoolean(behavior)) delay(50) }
            assertFalse("Original artwork loaded", loading.getBoolean(behavior))
            instrumentation.runOnMainSync {
                val frames = BaseBehavior::class.java.getDeclaredField("frames").apply { isAccessible = true }.get(behavior) as List<*>
                val spriteRects = BaseBehavior::class.java.getDeclaredField("spriteFrameRects").apply { isAccessible = true }
                    .get(behavior) as List<*>
                if (spriteRects.isNotEmpty()) {
                    assertEquals("Corgi transition atlas must expose all 23 frames", 23, spriteRects.size)
                } else {
                    assertEquals(14, frames.size)
                    assertTrue("Never substitute a sitting pose for an unloaded gait frame", frames.all { it != null })
                }
                val bitmap: Bitmap = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
                val review: Bitmap = Bitmap.createBitmap(800, 320, Bitmap.Config.ARGB_8888)
                review.eraseColor(Color.rgb(250, 247, 239))
                val bottoms: MutableList<Int> = mutableListOf()
                var standingHeight: Int = 0
                for (left: Boolean in listOf(false, true)) {
                    bridge.animScaleX = if (left) -1f else 1f
                    listOf(0, 10, 11, 12, 13).forEachIndexed { column, frame ->
                        bitmap.eraseColor(Color.TRANSPARENT)
                        bridge.currentFrame = frame
                        behavior.onDraw(Canvas(bitmap), 80f, 80f)
                        val pixels: IntArray = IntArray(160 * 160)
                        bitmap.getPixels(pixels, 0, 160, 0, 0, 160, 160)
                        val opaque: List<Int> = pixels.indices.filter { Color.alpha(pixels[it]) >= 128 }
                        assertTrue(opaque.isNotEmpty())
                        val bottom: Int = opaque.maxOf { it / 160 }
                        val height: Int = bottom - opaque.minOf { it / 160 } + 1
                        if (frame == 0) standingHeight = height
                        // Allow real gait compression, but reject the old 20% camera zoom.
                        assertTrue("Frame $frame grew from $standingHeight to $height", height <= standingHeight * 1.15f)
                        assertTrue("Frame $frame shrank excessively", height >= standingHeight * .85f)
                        bottoms.add(bottom)
                        Canvas(review).drawBitmap(bitmap, column * 160f, if (left) 160f else 0f, Paint())
                    }
                }
                assertTrue("Feet stay planted: $bottoms", bottoms.max() - bottoms.min() <= 1)
                File(instrumentation.targetContext.cacheDir, "corgi-scale-review.png").outputStream().use {
                    review.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
                review.recycle()
            }
        } finally { instrumentation.runOnMainSync { behavior.destroy() } }
    }

    @Test fun actionTransitionPlantsBeforeActivatingAndCanBeCancelled(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var bridge: TestPetBridge
        lateinit var behavior: CorgiBehavior
        instrumentation.runOnMainSync {
            bridge = TestPetBridge(instrumentation.targetContext, PetType.CORGI)
            behavior = CorgiBehavior(bridge, SeededPetRandom(8))
        }
        try {
            val loading = BaseBehavior::class.java.getDeclaredField("isLoading").apply { isAccessible = true }
            repeat(100) { if (loading.getBoolean(behavior)) delay(50) }
            assertFalse(loading.getBoolean(behavior))
            val modeClass = CorgiBehavior::class.java.declaredClasses.first { it.simpleName == "Mode" }
            val playBow = modeClass.enumConstants.first { (it as Enum<*>).name == "PLAY_BOW" }
            val begin = CorgiBehavior::class.java.getDeclaredMethod(
                "beginActionAfterPlant", modeClass, Float::class.javaPrimitiveType
            ).apply { isAccessible = true }
            val pending = CorgiBehavior::class.java.getDeclaredField("pendingAction").apply { isAccessible = true }
            val pendingDuration = CorgiBehavior::class.java.getDeclaredField("pendingActionDuration").apply { isAccessible = true }

            instrumentation.runOnMainSync {
                bridge.currentFrame = 13
                bridge.getWindowParams().x = 500
                begin.invoke(behavior, playBow, 1.05f)
                assertEquals("Planting replaces the previous gait frame immediately", 0, bridge.currentFrame)
                assertEquals(playBow, pending.get(behavior))
                assertEquals(1.05f, pendingDuration.getFloat(behavior), 0f)
                behavior.updateIdle(.17f)
                assertEquals("Action remains planted until the full pause elapses", 0, bridge.currentFrame)
                behavior.updateIdle(.02f)
                assertEquals("The transition tick itself remains planted", 0, bridge.currentFrame)
                behavior.updateIdle(.01f)
                assertEquals("Pending action activates after planting", 2, bridge.currentFrame)
                assertEquals(500, bridge.getWindowParams().x)
                begin.invoke(behavior, playBow, 1.05f)
                behavior.onInteract()
                assertNull(pending.get(behavior))
                assertEquals(0f, pendingDuration.getFloat(behavior), 0f)
                begin.invoke(behavior, playBow, 1.05f)
                behavior.onFling(1_000f, 0f)
                assertNull(pending.get(behavior))
                assertEquals(0f, pendingDuration.getFloat(behavior), 0f)
                begin.invoke(behavior, playBow, 1.05f)
                behavior.reset()
                assertNull(pending.get(behavior))
                assertEquals(0f, pendingDuration.getFloat(behavior), 0f)
                begin.invoke(behavior, playBow, 1.05f)
                behavior.resumeAfterCare(false, false)
                assertNull(pending.get(behavior))
                assertEquals(0f, pendingDuration.getFloat(behavior), 0f)
            }
        } finally { instrumentation.runOnMainSync { behavior.destroy() } }
    }
}
