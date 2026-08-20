package com.pixelpals.app.core.ads

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppOpenLaunchGateTest {
    @Test
    fun skipsTheFirstTwoLaunchesAndAllowsTheThird() {
        val gate: AppOpenLaunchGate = AppOpenLaunchGate()
        assertFalse(gate.register(0).isEligible)
        assertFalse(gate.register(1).isEligible)
        assertTrue(gate.register(2).isEligible)
    }

    @Test
    fun neverMovesTheCountBelowZero() {
        val decision: AppOpenLaunchDecision = AppOpenLaunchGate().register(-10)
        assertFalse(decision.isEligible)
        assertTrue(decision.launchCount >= 0)
    }
}
