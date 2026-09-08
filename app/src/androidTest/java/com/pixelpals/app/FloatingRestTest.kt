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
