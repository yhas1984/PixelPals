package com.pixelpals.app.core.rest

import com.pixelpals.app.core.motion.MokiMode
import com.pixelpals.app.core.motion.MokiMotionController
import com.pixelpals.app.core.motion.MokiSurface
import org.junit.Assert.*
import org.junit.Test

class MokiRestTest {
    @Test fun restRequestFromEverySurfaceReachesBottomPerchAndHolds() {
        for (surface: MokiSurface in MokiSurface.entries) {
            val controller: MokiMotionController = MokiMotionController(1f)
            controller.updateViewport(400, 800, 100f)
            var found: Boolean = false
            for (tick: Int in 0 until 100_000) {
                controller.update(1f / 60f)
                if (controller.surface == surface && controller.mode == MokiMode.CRAWL) {
                    found = true
                    break
                }
            }
            assertTrue("Reach $surface", found)
            val previousMode: MokiMode = controller.mode
            controller.requestRest(true)
            assertEquals("Request preserves current action", previousMode, controller.mode)
            var ready: Boolean = false
            for (tick: Int in 0 until 100_000) {
                controller.update(1f / 60f)
                if (controller.readyForRest) { ready = true; break }
            }
            assertTrue("Rest from $surface", ready)
            assertEquals(MokiSurface.BOTTOM, controller.surface)
            val position = controller.getPose()
            repeat(600) { controller.update(1f / 60f) }
            assertTrue(controller.readyForRest)
            assertEquals(position.x, controller.getPose().x, 0f)
            assertEquals(position.y, controller.getPose().y, 0f)
            assertEquals(0f, controller.getPose().rotationDegrees, 0f)
            controller.requestRest(false)
            repeat(150) { controller.update(1f / 60f) }
            assertFalse(controller.readyForRest)
        }
    }
}
