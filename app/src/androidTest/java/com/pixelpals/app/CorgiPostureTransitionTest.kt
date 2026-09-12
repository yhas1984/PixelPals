package com.pixelpals.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.SeededPetRandom
import com.pixelpals.app.feature.overlay.behavior.BaseBehavior
import com.pixelpals.app.feature.overlay.behavior.CorgiBehavior
import com.pixelpals.app.feature.overlay.behavior.TestPetBridge
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CorgiPostureTransitionTest {
    @Test
    fun realAtlasUsesPostureTimelineAt30To120FpsInBothDirections(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var bridge: TestPetBridge
        lateinit var behavior: CorgiBehavior
        instrumentation.runOnMainSync {
            bridge = TestPetBridge(instrumentation.targetContext, PetType.CORGI)
            behavior = CorgiBehavior(bridge, SeededPetRandom(41))
        }
        try {
            awaitRealAtlas(behavior, instrumentation)
            val begin: java.lang.reflect.Method = beginRestMethod().apply { isAccessible = true }
            instrumentation.runOnMainSync {
                for (fps: Int in listOf(30, 60, 120)) for (left in listOf(false, true)) {
                    behavior.resumeAfterCare(left, false)
                    bridge.getWindowParams().x = 420
                    begin.invoke(behavior, restMode(behavior), .3f)
                    val entering = mutableListOf(bridge.currentFrame)
                    fun tick(frames: MutableList<Int>) {
                        behavior.onScheduledRestRequested(false)
                        behavior.advanceScheduledRestTransition(1f / fps, false)
                        behavior.updateIdle(1f / fps)
                        if (frames.lastOrNull() != bridge.currentFrame) frames += bridge.currentFrame
                        assertEquals("No posture sliding", 420, bridge.getWindowParams().x)
                        assertEquals(bridge.groundY, bridge.getWindowParams().y)
                        assertEquals(if (left) -1f else 1f, bridge.animScaleX, 0f)
                        assertEquals(0f, bridge.animRotation, 0f)
                        assertEquals(1f, bridge.animScaleY, 0f)
                    }
                    var ticks = 0
                    while (modeName(behavior) != "REST" && ticks++ < fps * 2) tick(entering)
                    assertEquals("Complete entry at $fps fps", "REST", modeName(behavior))
                    assertEquals(listOf(0, 15, 16, 17), entering)
                    val leaving = mutableListOf(17)
                    while (modeName(behavior) == "REST" && ticks++ < fps * 3) tick(leaving)
                    assertEquals("STAND_UP", modeName(behavior))
                    while (modeName(behavior) == "STAND_UP" && ticks++ < fps * 4) tick(leaving)
                    assertEquals("Complete exit at $fps fps", "WALK", modeName(behavior))
                    assertEquals(listOf(17, 16, 15, 0), leaving)
                    repeat(fps / 2) { behavior.updateIdle(1f / fps) }
                    assertTrue(if (left) bridge.getWindowParams().x < 420 else bridge.getWindowParams().x > 420)
                }
            }
        } finally {
            instrumentation.runOnMainSync { behavior.destroy() }
        }
    }

    @Test
    fun reducedMotionCompletesTransitionAndWakeDoesNotRepeatSit(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var bridge: TestPetBridge
        lateinit var behavior: CorgiBehavior
        instrumentation.runOnMainSync {
            bridge = TestPetBridge(instrumentation.targetContext, PetType.CORGI)
            behavior = CorgiBehavior(bridge, SeededPetRandom(42))
        }
        try {
            awaitRealAtlas(behavior, instrumentation)
            val begin: java.lang.reflect.Method = beginRestMethod().apply { isAccessible = true }
            instrumentation.runOnMainSync {
                begin.invoke(behavior, restMode(behavior), .1f)
                repeat(3) { behavior.updateIdle(.1f) }
                assertEquals("SIT_DOWN", modeName(behavior))
                behavior.advanceScheduledRestTransition(1f / 60f, true)
                assertEquals(17, bridge.currentFrame)
                assertTrue(behavior.isSeatedForScheduledRest)
                behavior.updateIdle(.11f)
                assertEquals("STAND_UP", modeName(behavior))
                behavior.advanceScheduledRestTransition(1f / 60f, true)
                assertEquals(0, bridge.currentFrame)
                assertTrue(behavior.canStartScheduledSleep(true))
                behavior.resumeAfterCare(false, true)
                behavior.onScheduledWakeCompleted()
                assertEquals(0, bridge.currentFrame)
                assertEquals("ALERT", modeName(behavior))
                repeat(12) { behavior.updateIdle(1f / 60f); assertTrue(bridge.currentFrame !in 15..19) }
            }
        } finally {
            instrumentation.runOnMainSync { behavior.destroy() }
        }
    }

    @Test
    fun inputCancellationDoesNotResumeObsoletePostureFrames(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var bridge: TestPetBridge
        lateinit var behavior: CorgiBehavior
        instrumentation.runOnMainSync {
            bridge = TestPetBridge(instrumentation.targetContext, PetType.CORGI)
            behavior = CorgiBehavior(bridge, SeededPetRandom(43))
        }
        try {
            awaitRealAtlas(behavior, instrumentation)
            val begin: java.lang.reflect.Method = beginRestMethod().apply { isAccessible = true }
            instrumentation.runOnMainSync {
                for (input in listOf("touch", "drag", "fling", "reset")) {
                    behavior.resumeAfterCare(false, false)
                    begin.invoke(behavior, restMode(behavior), 2.2f)
                    repeat(4) { behavior.updateIdle(.1f) }
                    assertEquals("SIT_DOWN", modeName(behavior))
                    when (input) {
                        "touch" -> behavior.onInteract()
                        "drag" -> behavior.updateDrag(1f / 60f)
                        "fling" -> behavior.onFling(1_000f, 0f)
                        else -> behavior.reset()
                    }
                    repeat(20) {
                        behavior.advanceScheduledRestTransition(1f / 60f, false)
                        if (input in listOf("touch", "fling")) behavior.updateInteracting(1f / 60f)
                        else behavior.updateIdle(1f / 60f)
                        assertTrue("$input must cancel the old sit", bridge.currentFrame !in 15..19)
                    }
                }
            }
        } finally {
            instrumentation.runOnMainSync { behavior.destroy() }
        }
    }

    private suspend fun awaitRealAtlas(behavior: CorgiBehavior, instrumentation: android.app.Instrumentation) {
        val loading: java.lang.reflect.Field = BaseBehavior::class.java.getDeclaredField("isLoading").apply { isAccessible = true }
        repeat(120) { if (loading.getBoolean(behavior)) delay(50) }
        instrumentation.runOnMainSync {
            val rects: List<*> = BaseBehavior::class.java.getDeclaredField("spriteFrameRects").apply { isAccessible = true }.get(behavior) as List<*>
            assertEquals("Corgi posture test requires the real 23-frame atlas", 23, rects.size)
        }
    }

    private fun beginRestMethod(): java.lang.reflect.Method = CorgiBehavior::class.java.getDeclaredMethod(
        "beginActionAfterPlant",
        CorgiBehavior::class.java.declaredClasses.first { it.simpleName == "Mode" },
        Float::class.javaPrimitiveType,
    )

    private fun modeName(behavior: CorgiBehavior): String = (CorgiBehavior::class.java.getDeclaredField("mode")
        .apply { isAccessible = true }.get(behavior) as Enum<*>).name

    private fun restMode(behavior: CorgiBehavior): Any = requireNotNull(behavior.javaClass.declaredClasses
        .first { it.simpleName == "Mode" }.enumConstants).first { (it as Enum<*>).name == "REST" }
}
