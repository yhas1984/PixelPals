package com.pixelpals.app.feature.home

import kotlin.math.hypot
import org.junit.Assert.*
import org.junit.Test

class TelaWebTemperamentTest {
    private fun mealTimes(tempo: Float): Pair<Float, Float> {
        val web = TelaHomeWeb()
        assertTrue(web.hunt(1))
        var arrival = -1f
        var finish = -1f
        repeat(2400) { frame ->
            val x = web.x; val y = web.y
            val signal = web.advance(1f / 60f, tempo = tempo)
            assertTrue("movement remains bounded", hypot(web.x-x, web.y-y) <= 125f * 1.3f / 60f + .05f)
            if (web.isEating && arrival < 0f) arrival = frame / 60f
            if (signal) { assertTrue("single meal marker", finish < 0f); finish = frame / 60f }
        }
        assertTrue(arrival >= 0f); assertTrue(finish > arrival)
        return arrival to (finish-arrival)
    }
    @Test fun temperamentChangesTravelWithoutAcceleratingTheMeal() {
        val calm = mealTimes(.85f)
        val playful = mealTimes(1.15f)
        assertTrue("learned pace affects the route", playful.first < calm.first - .5f)
        assertEquals(2f,calm.second,.04f)
        assertEquals(2f,playful.second,.04f)
    }
    @Test fun initiativeChangesDepartureWithoutStartingAFeedingAction() {
        val reserved = TelaHomeWeb()
        val curious = TelaHomeWeb()
        repeat(110) {
            assertFalse(reserved.advance(1f/60f,initiative=.7f))
            assertFalse(curious.advance(1f/60f,initiative=1.3f))
        }
        assertEquals(0f,reserved.distanceTravelled,0f)
        assertTrue(curious.distanceTravelled > 0f)
        assertFalse(curious.isHunting)
        assertTrue(curious.flies.indices.all(curious::isFlyVisible))
    }
    @Test fun changingTempoMidThreadDoesNotMoveTheActorInstantly() {
        val web = TelaHomeWeb()
        web.hunt(0)
        repeat(90) { web.advance(1f/60f,tempo=.85f) }
        val before=WebPoint(web.x,web.y)
        web.advance(0f,tempo=1.15f)
        assertEquals(before,WebPoint(web.x,web.y))
    }
}
