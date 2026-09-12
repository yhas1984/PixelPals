package com.pixelpals.app.feature.overlay.behavior

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.PetRandom
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class GroundPetGaitTest {
    @Test fun piruRespectsWalkingSpeedOnAndroid(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val predictable: PetRandom = object : PetRandom {
            override fun nextFloat(): Float = .5f
            override fun nextInt(from: Int, until: Int): Int = from
        }
        for (pet: PetType in listOf(PetType.PIRU)) {
            lateinit var bridge: TestPetBridge
            lateinit var behavior: BaseBehavior
            instrumentation.runOnMainSync {
                bridge = TestPetBridge(instrumentation.targetContext, pet)
                behavior = PetBehaviorFactory.create(pet, bridge, predictable) as BaseBehavior
            }
            try {
                val loading = BaseBehavior::class.java.getDeclaredField("isLoading").apply { isAccessible = true }
                var attempts: Int = 0
                while (attempts++ < 40 && loading.getBoolean(behavior)) {
                    Thread.sleep(50)
                    instrumentation.waitForIdleSync()
                }
                assertTrue("Assets not loaded for $pet", !loading.getBoolean(behavior))
                instrumentation.runOnMainSync {
                    val startX: Int = bridge.windowX
                    val peakSpeed: Float = bridge.petSpriteSize * .95f
                    repeat(60 * 12) {
                        val previous: Int = bridge.windowX
                        behavior.updateIdle(1f / 60f)
                        val displacement: Int = abs(bridge.windowX - previous)
                        assertTrue("$pet jumped $displacement pixels in one frame", displacement <= peakSpeed / 60f + 1f)
                        assertTrue(bridge.windowX in bridge.bounds.left..bridge.bounds.right)
                    }
                    assertTrue("$pet never walked", abs(bridge.windowX - startX) > 20)
                }
            } finally {
                instrumentation.runOnMainSync { behavior.destroy() }
            }
        }
    }
}
