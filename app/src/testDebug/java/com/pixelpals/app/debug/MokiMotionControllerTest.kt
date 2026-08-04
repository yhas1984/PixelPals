package com.pixelpals.app.debug

import com.pixelpals.app.motion.MokiMode
import com.pixelpals.app.motion.MokiMotionController
import com.pixelpals.app.motion.MokiPose
import com.pixelpals.app.motion.MokiSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MokiMotionControllerTest {
    @Test
    fun crawlFollowsPerimeterAndTurnsOntoRightWall(): Unit {
        val controller: MokiMotionController = createController()
        advance(controller, 2.1f)
        assertEquals(MokiMode.CRAWL, controller.mode)
        advance(controller, 7.2f)
        assertEquals(MokiSurface.RIGHT, controller.surface)
        assertEquals(MokiMode.CRAWL, controller.mode)
        assertEquals(-90f, controller.getPose().rotationDegrees, 0.01f)
    }

    @Test
    fun tongueStrikeUsesEveryReactionPhaseAndReturnsToPerch(): Unit {
        val controller: MokiMotionController = createController()
        controller.startTongueStrike()
        val visitedFrames: MutableSet<Int> = mutableSetOf()
        repeat(50) {
            visitedFrames += controller.update(STEP_SECONDS).frameIndex
        }
        assertTrue(visitedFrames.containsAll(setOf(13, 14, 15, 16)))
        assertEquals(MokiMode.PERCH, controller.mode)
    }

    @Test
    fun flingAlwaysReattachesToAValidSurface(): Unit {
        val controller: MokiMotionController = createController()
        controller.startDrag(540f, 1_000f)
        controller.releaseDrag(1_800f, -900f)
        advance(controller, 2.0f)
        val pose: MokiPose = controller.getPose()
        assertFalse(controller.mode in setOf(MokiMode.DRAGGING, MokiMode.FLING, MokiMode.LANDING))
        assertTrue(pose.x in 0f..VIEWPORT_WIDTH.toFloat())
        assertTrue(pose.y in 0f..VIEWPORT_HEIGHT.toFloat())
        assertTrue(pose.frameIndex in 0..19)
    }

    @Test
    fun dragTracksLatestPositionBeforeFling(): Unit {
        val controller: MokiMotionController = createController()
        controller.startDrag(300f, 700f)
        controller.moveDrag(760f, 1_400f)

        val draggedPose: MokiPose = controller.getPose()
        assertEquals(MokiMode.DRAGGING, draggedPose.mode)
        assertEquals(760f, draggedPose.x, 0.01f)
        assertEquals(1_400f, draggedPose.y, 0.01f)

        controller.releaseDrag(900f, -500f)
        assertEquals(MokiMode.FLING, controller.mode)
        assertEquals(760f, controller.getPose().x, 0.01f)
        assertEquals(1_400f, controller.getPose().y, 0.01f)
    }

    @Test
    fun longSimulationKeepsFramesAndCoordinatesFinite(): Unit {
        val controller: MokiMotionController = createController()
        repeat(60 * 45) {
            val pose: MokiPose = controller.update(STEP_SECONDS)
            assertTrue(pose.frameIndex in 0..19)
            assertTrue(pose.x.isFinite())
            assertTrue(pose.y.isFinite())
            assertTrue(pose.rotationDegrees.isFinite())
        }
    }

    @Test
    fun occasionalRestHappensMidRouteAndThenCrawlResumes(): Unit {
        val controller: MokiMotionController = createController()
        advance(controller, 2.1f)
        var observedRest: Boolean = false
        var observedResume: Boolean = false
        repeat(60 * 90) {
            val previousMode: MokiMode = controller.mode
            controller.update(STEP_SECONDS)
            if (previousMode == MokiMode.CRAWL && controller.mode == MokiMode.PERCH) {
                observedRest = true
            }
            if (observedRest && previousMode == MokiMode.PERCH && controller.mode == MokiMode.CRAWL) {
                observedResume = true
            }
        }
        assertTrue(observedRest)
        assertTrue(observedResume)
    }

    private fun createController(): MokiMotionController = MokiMotionController(density = 1f).also {
        it.updateViewport(VIEWPORT_WIDTH, VIEWPORT_HEIGHT, DRAW_SIZE)
    }

    private fun advance(controller: MokiMotionController, seconds: Float): Unit {
        repeat((seconds / STEP_SECONDS).toInt()) { controller.update(STEP_SECONDS) }
    }

    private companion object {
        const val STEP_SECONDS: Float = 1f / 60f
        const val VIEWPORT_WIDTH: Int = 1_080
        const val VIEWPORT_HEIGHT: Int = 2_400
        const val DRAW_SIZE: Float = 420f
    }
}
