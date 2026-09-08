package com.pixelpals.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.*
import com.pixelpals.app.feature.overlay.behavior.MokiBehavior
import com.pixelpals.app.feature.overlay.behavior.TestPetBridge
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MokiReducedRestTest {
    @Test fun hiddenRelocationKeepsControllerAndViewAligned(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val bridge = TestPetBridge(instrumentation.targetContext, PetType.MOKI)
            val behavior = MokiBehavior(bridge, SeededPetRandom(12))
            try {
                val controller = MokiBehavior::class.java.getDeclaredField("controller").apply { isAccessible = true }
                    .get(behavior) as MokiMotionController
                controller.updateViewport(bridge.screenWidth, bridge.screenHeight, bridge.petSpriteSize.toFloat())
                for (tick: Int in 0 until 100_000) {
                    controller.update(1f / 60f)
                    if (controller.surface == MokiSurface.TOP && controller.mode == MokiMode.CRAWL) break
                }
                assertEquals(MokiSurface.TOP, controller.surface)
                MokiBehavior::class.java.getDeclaredField("pose").apply { isAccessible = true }.set(behavior, controller.getPose())
                MokiBehavior::class.java.getDeclaredMethod("syncPoseToBridge").apply { isAccessible = true }.invoke(behavior)
                val originalY: Int = bridge.getWindowParams().y
                behavior.onScheduledRestRequested(true)
                behavior.advanceScheduledRestTransition(.05f, true)
                behavior.onScheduledRestRequested(false)
                assertEquals(1f, bridge.alpha, 0f)
                assertEquals(originalY, bridge.getWindowParams().y)
                behavior.onScheduledRestRequested(true)
                var previousY: Int = originalY
                var moves: Int = 0
                repeat(90) {
                    behavior.advanceScheduledRestTransition(1f / 60f, true)
                    if (bridge.getWindowParams().y != previousY) {
                        moves++
                        assertEquals(0f, bridge.alpha, 0f)
                    }
                    previousY = bridge.getWindowParams().y
                }
                assertEquals(1, moves)
                assertTrue(behavior.canStartScheduledSleep(true))
                assertEquals(1f, bridge.alpha, 0f)
                assertEquals(MokiSurface.BOTTOM, controller.surface)
                val settled = controller.getPose()
                repeat(600) { controller.update(1f / 60f) }
                assertEquals(settled.x, controller.getPose().x, 0f)
                assertEquals(settled.y, controller.getPose().y, 0f)
                assertEquals((settled.y - bridge.petSpriteSize * .5f).toInt(), bridge.getWindowParams().y)
                assertEquals(0f, bridge.animRotation, 0f)
            } finally { behavior.destroy() }
        }
    }
}
