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
class FloatingRestTest {
    @Test fun angelReducedRestSettlesEveryFloatingModeBeforeHandoff(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val modes: List<String> = listOf("CRUISE", "GLIDE", "GRACE", "HOVER")
            for (modeName: String in modes) {
                val bridge = TestPetBridge(instrumentation.targetContext, PetType.ANGEL)
                val behavior = AngelBehavior(bridge, SeededPetRandom(12))
                try {
                    val modeField = AngelBehavior::class.java.getDeclaredField("mode").apply { isAccessible = true }
                    val modeValue = modeField.type.enumConstants!!.first { (it as Enum<*>).name == modeName }
                    modeField.set(behavior, modeValue)
                    val velocityX = AngelBehavior::class.java.getDeclaredField("velocityX").apply { isAccessible = true }
                    val velocityY = AngelBehavior::class.java.getDeclaredField("velocityY").apply { isAccessible = true }
                    velocityX.setFloat(behavior, 100f)
                    velocityY.setFloat(behavior, 100f)
                    assertFalse("$modeName must not hand off while moving", behavior.canStartScheduledSleep(true))
                    val originalX: Int = bridge.getWindowParams().x
                    val originalY: Int = bridge.getWindowParams().y
                    behavior.onScheduledRestRequested(true)
                    repeat(90) {
                        behavior.advanceScheduledRestTransition(1f / 60f, true)
                        assertEquals(originalX, bridge.getWindowParams().x)
                        assertEquals(originalY, bridge.getWindowParams().y)
                        if (bridge.alpha < 1f) {
                            assertFalse("$modeName handed off during fade", behavior.canStartScheduledSleep(true))
                        }
                    }
                    assertEquals("PRAYER", (modeField.get(behavior) as Enum<*>).name)
                    assertEquals(0f, velocityX.getFloat(behavior), 0.001f)
                    assertEquals(0f, velocityY.getFloat(behavior), 0.001f)
                    val params = bridge.getWindowParams()
                    val targetX = AngelBehavior::class.java.getDeclaredField("flightTargetX").apply { isAccessible = true }
                    val targetY = AngelBehavior::class.java.getDeclaredField("flightTargetY").apply { isAccessible = true }
                    assertEquals(params.x.toFloat(), targetX.getFloat(behavior), 0.001f)
                    assertEquals(params.y.toFloat(), targetY.getFloat(behavior), 0.001f)
                    assertTrue(behavior.canStartScheduledSleep(true))
                } finally { behavior.destroy() }
            }
        }
    }

    @Test fun angelTouchNeverStartsReducedRestFade(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val bridge = TestPetBridge(instrumentation.targetContext, PetType.ANGEL)
            val behavior = AngelBehavior(bridge, SeededPetRandom(12))
            try {
                val mode = AngelBehavior::class.java.getDeclaredField("mode").apply { isAccessible = true }
                mode.set(behavior, mode.type.enumConstants!!.first { (it as Enum<*>).name == "TOUCH" })
                behavior.onScheduledRestRequested(true)
                repeat(10) { behavior.advanceScheduledRestTransition(1f / 60f, true) }
                assertEquals(1f, bridge.alpha, 0f)
                assertFalse(behavior.canStartScheduledSleep(true))
            } finally { behavior.destroy() }
        }
    }

    @Test fun angelRecoveryCanFinishWithoutTravelInReducedMotion(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val bridge = TestPetBridge(instrumentation.targetContext, PetType.ANGEL)
            val behavior = AngelBehavior(bridge, SeededPetRandom(12))
            try {
                val mode = AngelBehavior::class.java.getDeclaredField("mode").apply { isAccessible = true }
                mode.set(behavior, mode.type.enumConstants!!.first { (it as Enum<*>).name == "RECOVER" })
                val originalX: Int = bridge.getWindowParams().x
                val originalY: Int = bridge.getWindowParams().y
                assertFalse(behavior.canStartScheduledSleep(true))
                behavior.onScheduledRestRequested(true)
                behavior.advanceScheduledRestTransition(.05f, true)
                behavior.onScheduledRestRequested(false)
                assertEquals(1f, bridge.alpha, 0f)
                assertEquals("RECOVER", (mode.get(behavior) as Enum<*>).name)
                behavior.onScheduledRestRequested(true)
                var previousMode: String = "RECOVER"
                repeat(90) {
                    behavior.advanceScheduledRestTransition(1f / 60f, true)
                    val currentMode: String = (mode.get(behavior) as Enum<*>).name
                    if (currentMode != previousMode) assertEquals(0f, bridge.alpha, 0f)
                    previousMode = currentMode
                    assertEquals(originalX, bridge.getWindowParams().x)
                    assertEquals(originalY, bridge.getWindowParams().y)
                    if (bridge.alpha < 1f) assertFalse(behavior.canStartScheduledSleep(true))
                }
                assertEquals("PRAYER", previousMode)
                assertTrue(behavior.canStartScheduledSleep(true))
                assertEquals(1f, bridge.alpha, 0f)
                assertEquals(11, bridge.currentFrame)
            } finally { behavior.destroy() }
        }
    }

    @Test fun ghostAndAngelSettleBeforeSleep(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            for (pet: PetType in listOf(PetType.BLOOP, PetType.ANGEL)) {
                val bridge = TestPetBridge(instrumentation.targetContext, pet)
                val behavior: PetBehavior = PetBehaviorFactory.create(pet, bridge, SeededPetRandom(12))
                try {
                    val owner: Class<*> = if (pet == PetType.BLOOP) BaseBehavior::class.java else AngelBehavior::class.java
                    val speedName: String = if (pet == PetType.BLOOP) "velX" else "velocityX"
                    val speed = owner.getDeclaredField(speedName).apply { isAccessible = true }
                    speed.setFloat(behavior, 100f)
                    if (pet == PetType.ANGEL) {
                        for ((name, value) in mapOf("positionX" to 300f, "positionY" to 600f)) {
                            owner.getDeclaredField(name).apply { isAccessible = true }.setFloat(behavior, value)
                        }
                    }
                    assertFalse(behavior.canStartScheduledSleep(false))
                    behavior.onScheduledRestRequested(true)
                    val settle = behavior.javaClass.getDeclaredMethod("settleForRest", Float::class.javaPrimitiveType).apply { isAccessible = true }
                    var previousSpeed: Float = 100f
                    repeat(180) {
                        settle.invoke(behavior, 1f / 60f)
                        assertTrue(speed.getFloat(behavior) <= previousSpeed)
                        previousSpeed = speed.getFloat(behavior)
                    }
                    assertTrue(behavior.canStartScheduledSleep(false))
                } finally { behavior.destroy() }
            }
        }
    }

    @Test fun invisibleGhostFadesInWithoutMovingBeforeReducedMotionSleep(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val bridge = TestPetBridge(instrumentation.targetContext, PetType.BLOOP)
            val behavior = BloopBehavior(bridge, SeededPetRandom(12))
            try {
                val mode = BloopBehavior::class.java.getDeclaredField("mode").apply { isAccessible = true }
                mode.set(behavior, mode.type.enumConstants!!.first { (it as Enum<*>).name == "DISAPPEAR" })
                bridge.animAlpha = 0f
                val originalY: Int = bridge.getWindowParams().y
                behavior.onScheduledRestRequested(true)
                assertFalse(behavior.canStartScheduledSleep(true))
                repeat(20) {
                    behavior.advanceScheduledRestTransition(1f / 60f, true)
                    assertEquals(originalY, bridge.getWindowParams().y)
                    if (bridge.animAlpha < 1f) assertFalse(behavior.canStartScheduledSleep(true))
                }
                assertEquals(1f, bridge.animAlpha, 0f)
                assertTrue(behavior.canStartScheduledSleep(true))
            } finally { behavior.destroy() }
        }
    }
}
