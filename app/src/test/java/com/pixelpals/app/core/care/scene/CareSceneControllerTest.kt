package com.pixelpals.app.core.care.scene

import org.junit.Assert.*
import org.junit.Test

class CareSceneControllerTest {
    private val timing: CareSceneTiming = CareSceneTiming(4_000L, 3_500L)

    @Test fun automaticActionsEmitExactlyOneCompletion(): Unit {
        CareSceneAction.entries.forEach { action ->
            val controller: CareSceneController = CareSceneController(action, CareSceneMode.AUTOMATIC, timing)
            var completions: Int = 0
            repeat(200) { if (controller.advance(100L)) completions++ }
            assertEquals(action.name, 1, completions)
            assertTrue(controller.isComplete)
        }
    }

    @Test fun manualTimeoutDoesNotGrantEffect(): Unit {
        CareSceneAction.entries.forEach { action ->
            val controller: CareSceneController = CareSceneController(action, CareSceneMode.MANUAL, timing)
            repeat(151) { assertFalse(controller.advance(100L)) }
            assertTrue(controller.isCancelled)
        }
    }

    @Test fun foodRequiresMouthContact(): Unit {
        val controller: CareSceneController = CareSceneController(CareSceneAction.FEED, CareSceneMode.MANUAL, timing)
        controller.movePointer(CarePoint(.9f, .9f), CarePoint(.4f, .4f), true)
        assertFalse(controller.hasContact)
        controller.movePointer(CarePoint(.4f, .4f), CarePoint(.4f, .4f), true)
        assertTrue(controller.hasContact)
        repeat(34) { assertFalse(controller.advance(100L)) }
        assertTrue(controller.advance(100L))
    }

    @Test fun stationaryFingerDoesNotPetOrClean(): Unit {
        listOf(CareSceneAction.PET, CareSceneAction.CLEAN).forEach { action ->
            val controller: CareSceneController = CareSceneController(action, CareSceneMode.MANUAL, timing)
            repeat(50) {
                controller.movePointer(CarePoint(.5f, .5f), CarePoint(.5f, .5f), true)
                controller.advance(100L)
            }
            assertFalse(controller.hasContact)
            repeat(16) { index ->
                controller.movePointer(CarePoint(.48f + (index % 2) * .04f, .5f), CarePoint(.5f, .5f), true)
                controller.advance(100L)
            }
            assertTrue(controller.hasContact)
        }
    }

    @Test fun ballOnlyStartsChaseOnRelease(): Unit {
        val controller: CareSceneController = CareSceneController(CareSceneAction.PLAY, CareSceneMode.MANUAL, timing)
        controller.movePointer(CarePoint(.8f, .7f), CarePoint(.5f, .5f), true)
        assertFalse(controller.hasContact)
        controller.movePointer(CarePoint(.8f, .7f), CarePoint(.5f, .5f), false)
        assertTrue(controller.hasContact)
    }

    @Test fun cancellationIsTerminal(): Unit {
        val controller: CareSceneController = CareSceneController(CareSceneAction.REST, CareSceneMode.AUTOMATIC, timing)
        controller.advance(1_000L)
        controller.cancel()
        assertFalse(controller.advance(10_000L))
        assertTrue(controller.isCancelled)
        assertFalse(controller.isComplete)
    }

    @Test fun releasedBallKeepsItsTargetUntilTheSceneFinishes(): Unit {
        val controller: CareSceneController = CareSceneController(CareSceneAction.PLAY, CareSceneMode.MANUAL, timing)
        val release: CarePoint = CarePoint(.8f, .7f)
        val mouth: CarePoint = CarePoint(.5f, .5f)
        controller.movePointer(release, mouth, false)
        controller.advance(500L)
        controller.movePointer(CarePoint(.2f, .7f), mouth, true)
        controller.movePointer(CarePoint(.1f, .7f), mouth, false)
        assertEquals(release, controller.prop)
        assertTrue(controller.hasContact)
        var completions: Int = 0
        repeat(50) { if (controller.advance(100L)) completions++ }
        assertEquals(1, completions)
        assertTrue(controller.isComplete)
    }

    @Test fun ballReleasedOutsideStageDoesNotStartCare(): Unit {
        val controller: CareSceneController = CareSceneController(CareSceneAction.PLAY, CareSceneMode.MANUAL, timing)
        controller.movePointer(CarePoint(.5f, 1.4f), CarePoint(.5f, .5f), false)
        assertFalse(controller.hasContact)
    }

    @Test fun draggingAwayBetweenStrokesDoesNotAccumulateContact(): Unit {
        val controller: CareSceneController = CareSceneController(CareSceneAction.CLEAN, CareSceneMode.MANUAL, timing)
        repeat(20) {
            controller.movePointer(CarePoint(.5f, .5f), CarePoint(.5f, .5f), true)
            controller.movePointer(CarePoint(.9f, .9f), CarePoint(.5f, .5f), true)
            controller.advance(100L)
        }
        assertFalse(controller.hasContact)
    }
}
