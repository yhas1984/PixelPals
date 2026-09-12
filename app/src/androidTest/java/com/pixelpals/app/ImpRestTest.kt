package com.pixelpals.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.SeededPetRandom
import com.pixelpals.app.feature.overlay.behavior.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImpRestTest {
    @Test fun flightDeceleratesAndCommittedActionsRejectSleep(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val bridge = TestPetBridge(instrumentation.targetContext, PetType.DIABLILLO)
            val behavior = ImpBehavior(bridge, SeededPetRandom(12))
            try {
                val mode = ImpBehavior::class.java.getDeclaredField("impState").apply { isAccessible = true }
                for (value in mode.type.enumConstants!!) {
                    mode.set(behavior, value)
                    behavior.onScheduledRestRequested(true)
                    assertEquals("A request preserves the committed action", value, mode.get(behavior))
                    assertEquals((value as Enum<*>).name == "FLYING", behavior.canStartScheduledSleep(false))
                }
                mode.set(behavior, mode.type.enumConstants!!.first { (it as Enum<*>).name == "FLYING" })
                val speed = BaseBehavior::class.java.getDeclaredField("velX").apply { isAccessible = true }
                speed.setFloat(behavior, 250f)
                assertFalse(behavior.canStartScheduledSleep(false))
                val settle = ImpBehavior::class.java.getDeclaredMethod("settleForRest", Float::class.javaPrimitiveType).apply { isAccessible = true }
                var previous: Float = 250f
                repeat(180) {
                    settle.invoke(behavior, 1f / 60f)
                    assertTrue(speed.getFloat(behavior) <= previous)
                    previous = speed.getFloat(behavior)
                }
                assertTrue(behavior.canStartScheduledSleep(false))
                speed.setFloat(behavior, -.01f)
                val settledX: Int = bridge.getWindowParams().x
                repeat(10) { settle.invoke(behavior, 1f / 60f) }
                assertEquals("Subpixel negative drift must not move one pixel per frame", settledX, bridge.getWindowParams().x)
            } finally { behavior.destroy() }
        }
    }

    @Test fun reducedMotionResetsWallPoseOnlyWhileInvisible(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val bridge = TestPetBridge(instrumentation.targetContext, PetType.DIABLILLO)
            val behavior = ImpBehavior(bridge, SeededPetRandom(12))
            try {
                val mode = ImpBehavior::class.java.getDeclaredField("impState").apply { isAccessible = true }
                mode.set(behavior, mode.type.enumConstants!!.first { (it as Enum<*>).name == "CLIMBING" })
                bridge.animRotation = 90f
                val x: Int = bridge.getWindowParams().x
                val y: Int = bridge.getWindowParams().y
                behavior.onScheduledRestRequested(true)
                behavior.advanceScheduledRestTransition(.05f, true)
                behavior.onScheduledRestRequested(false)
                assertEquals(1f, bridge.alpha, 0f)
                assertEquals(90f, bridge.animRotation, 0f)
                behavior.onScheduledRestRequested(true)
                var oldRotation: Float = bridge.animRotation
                repeat(90) {
                    behavior.advanceScheduledRestTransition(1f / 60f, true)
                    if (oldRotation != bridge.animRotation) assertEquals(0f, bridge.alpha, 0f)
                    oldRotation = bridge.animRotation
                    assertEquals(x, bridge.getWindowParams().x)
                    assertEquals(y, bridge.getWindowParams().y)
                }
                assertEquals(1f, bridge.alpha, 0f)
                assertEquals(0f, bridge.animRotation, 0f)
                assertTrue(behavior.canStartScheduledSleep(true))
            } finally { behavior.destroy() }
        }
    }
}
