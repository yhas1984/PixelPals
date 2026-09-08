package com.pixelpals.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.SeededPetRandom
import com.pixelpals.app.feature.overlay.behavior.MentaBehavior
import com.pixelpals.app.feature.overlay.behavior.TestPetBridge
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MentaTravelTest {
    @Test fun bothTravelModesFinishAtTheirDestinationWithStableBodyScale(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            for (methodName: String in listOf("updateSlither", "updateClimb")) {
                val bridge = TestPetBridge(instrumentation.targetContext, PetType.MENTA)
                val behavior = MentaBehavior(bridge, SeededPetRandom(12))
                try {
                    fun set(name: String, value: Float): Unit {
                        MentaBehavior::class.java.getDeclaredField(name).apply { isAccessible = true }.setFloat(behavior, value)
                    }
                    set("startX", 300f); set("startY", 600f)
                    set("cruiseTargetX", 800f); set("cruiseTargetY", 1200f)
                    set("modeDuration", 10f)
                    val update = MentaBehavior::class.java.getDeclaredMethod(methodName,
                        Float::class.javaPrimitiveType).apply { isAccessible = true }
                    set("modeTimer", 5f)
                    update.invoke(behavior, .016f)
                    assertEquals(550, bridge.getWindowParams().x)
                    assertEquals(900, bridge.getWindowParams().y)
                    assertEquals(1f, bridge.animScaleY, 0f)
                    set("modeTimer", 10f)
                    update.invoke(behavior, .016f)
                    assertEquals(800, bridge.getWindowParams().x)
                    assertEquals(1200, bridge.getWindowParams().y)
                    assertEquals(0f, bridge.animOffsetX, 0f)
                    assertEquals(0f, bridge.animOffsetY, 0f)
                } finally { behavior.destroy() }
            }
        }
    }
}
