package com.pixelpals.app.core.care.scene

import org.junit.Assert.*
import org.junit.Test

class CareWashMotionTest {
    @Test fun washingBuildsLatherThenRinsesItAway(): Unit {
        assertEquals(0f, CareWashMotion.sample(0f, false).foam, 0f)
        assertTrue(CareWashMotion.sample(.2f, false).foam in .1f.. .9f)
        assertEquals(1f, CareWashMotion.sample(.5f, false).foam, 0f)
        assertTrue(CareWashMotion.sample(.8f, false).foam in .1f.. .9f)
        assertEquals(0f, CareWashMotion.sample(1f, false).foam, 0f)
    }

    @Test fun reducedMotionKeepsFoamButStopsBubbleDrift(): Unit {
        for (step: Int in -10..110) {
            val normal: CareWashState = CareWashMotion.sample(step / 100f, false)
            val reduced: CareWashState = CareWashMotion.sample(step / 100f, true)
            assertTrue(normal.foam in 0f..1f && normal.rinse in 0f..1f && normal.drift in 0f.. .12f)
            assertEquals(normal.copy(drift = 0f), reduced)
        }
        assertTrue(CareWashMotion.sample(.8f, false).drift > 0f)
    }
}
