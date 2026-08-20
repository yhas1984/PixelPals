package com.pixelpals.app.core.runtime

import com.pixelpals.app.core.motion.PetBounds
import com.pixelpals.app.core.motion.PetRandom
import com.pixelpals.app.core.motion.PhysicsProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PetRuntimeContractsTest {
    private val bounds = PetBounds.compute(1_080, 2_400, 160, 100, 200)

    @Test
    fun eventPriorityMatchesTheRuntimeContract() {
        assertEquals(PetEventPriority.LIFECYCLE, PetEventPriorities.of(PetEvent.Destroyed, PetInteractionState.DRAGGING))
        assertEquals(PetEventPriority.ACTIVE_GESTURE, PetEventPriorities.of(PetEvent.Tap, PetInteractionState.NONE))
        assertEquals(
            PetEventPriority.MANDATORY_ENVIRONMENT,
            PetEventPriorities.of(PetEvent.EnvironmentChanged(PetEnvironment(bounds)), PetInteractionState.NONE),
        )
        assertEquals(
            PetEventPriority.PHYSICAL_RECOVERY,
            PetEventPriorities.of(PetEvent.Tick(0.1f), PetInteractionState.RECOVERING),
        )
        assertEquals(PetEventPriority.AUTONOMY, PetEventPriorities.of(PetEvent.Tick(0.1f), PetInteractionState.NONE))
    }

    @Test
    fun edgeAttachmentSelectsTheNearestSurface() {
        assertEquals(
            PetSurface.LEFT_WALL,
            PetSurfaceResolver.attach(PetVector(bounds.left + 2f, 600f), bounds, PhysicsProfile.EDGE).surface,
        )
        assertEquals(
            PetSurface.CEILING,
            PetSurfaceResolver.attach(PetVector(500f, bounds.top + 2f), bounds, PhysicsProfile.EDGE).surface,
        )
        assertEquals(
            PetSurface.RIGHT_WALL,
            PetSurfaceResolver.attach(PetVector(bounds.right - 2f, 600f), bounds, PhysicsProfile.EDGE).surface,
        )
        assertEquals(
            PetSurface.FLOOR,
            PetSurfaceResolver.attach(PetVector(500f, bounds.floor - 2f), bounds, PhysicsProfile.EDGE).surface,
        )
    }

    @Test
    fun schedulerPenalizesImmediateSpecialActionRepeats() {
        val scheduler = PetActionScheduler(FixedRandom(0.5f))
        val candidates = listOf(
            PetActionCandidate(PetIntent.CURIOSITY, baseWeight = 1f),
            PetActionCandidate(PetIntent.SOCIAL, baseWeight = 1f),
        )

        val selected = scheduler.select(candidates, recentActions = listOf(PetIntent.CURIOSITY))

        assertNotEquals(PetIntent.CURIOSITY, selected)
        assertEquals(PetIntent.SOCIAL, selected)
    }

    @Test
    fun bondStagesKeepTheExistingThresholds() {
        assertEquals(PetBondStage.NEW, PetBondStage.fromBond(11))
        assertEquals(PetBondStage.CLOSE, PetBondStage.fromBond(12))
        assertEquals(PetBondStage.TRUSTED, PetBondStage.fromBond(35))
        assertEquals(PetBondStage.SOULMATE, PetBondStage.fromBond(70))
        assertTrue(PetBondStage.fromBond(100) == PetBondStage.SOULMATE)
    }
}

private class FixedRandom(private val value: Float) : PetRandom {
    override fun nextFloat(): Float = value
    override fun nextInt(from: Int, until: Int): Int = from
}
