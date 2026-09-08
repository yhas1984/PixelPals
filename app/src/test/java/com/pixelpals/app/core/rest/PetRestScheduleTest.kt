package com.pixelpals.app.core.rest

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PetRestScheduleTest {
    @Test fun overnightIncludesStartAndExcludesWakeTime() {
        val schedule = PetRestSchedule()
        assertFalse(schedule.shouldRest(22 * 60 + 59, false))
        assertTrue(schedule.shouldRest(23 * 60, false))
        assertTrue(schedule.shouldRest(0, false))
        assertTrue(schedule.shouldRest(6 * 60 + 59, false))
        assertFalse(schedule.shouldRest(7 * 60, false))
    }

    @Test fun systemRestAndScheduleCanBeDisabledIndependently() {
        assertTrue(PetRestSchedule(enabled = false).shouldRest(12 * 60, true))
        assertFalse(PetRestSchedule(enabled = false).shouldRest(0, false))
        assertFalse(PetRestSchedule(followDoNotDisturb = false).shouldRest(12 * 60, true))
        assertTrue(PetRestSchedule(followDoNotDisturb = false).shouldRest(0, true))
    }

    @Test fun daytimeAndEqualEndpointsHaveDefinedBehavior() {
        val daytime = PetRestSchedule(startMinute = 8 * 60, endMinute = 16 * 60)
        assertTrue(daytime.shouldRest(8 * 60, false))
        assertFalse(daytime.shouldRest(16 * 60, false))
        assertFalse(daytime.shouldRest(0, false))
        assertFalse(PetRestSchedule(startMinute = 0, endMinute = 0).shouldRest(0, false))
    }
}
