package com.pixelpals.app.core.motion

import com.pixelpals.app.core.care.scene.CarePlayVariation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class JellySpringPlayMotionTest {
    @Test
    fun endpointsAndReducedMotionAreNeutral() {
        for (variation in CarePlayVariation.entries) {
            assertEquals(JellySpringPlayMotion.Pose(com.pixelpals.app.core.care.scene.SpeciesCarePose(), 1f),
                JellySpringPlayMotion.sample(0f, false, variation))
            assertEquals(JellySpringPlayMotion.Pose(com.pixelpals.app.core.care.scene.SpeciesCarePose(), 1f),
                JellySpringPlayMotion.sample(1f, false, variation))
            assertEquals(JellySpringPlayMotion.Pose(com.pixelpals.app.core.care.scene.SpeciesCarePose(), 1f),
                JellySpringPlayMotion.sample(.5f, true, variation))
        }
    }

    @Test
    fun springContactsUseRequestedCompression() {
        for (variation in CarePlayVariation.entries) {
            assertEquals(.45f, JellySpringPlayMotion.sample(.42f, false, variation).springCompression, .0001f)
            assertEquals(.60f, JellySpringPlayMotion.sample(.78f, false, variation).springCompression, .0001f)
            for (step in 30..50) {
                val progress = step / 100f
                val pose = JellySpringPlayMotion.sample(progress, false, variation)
                assertEquals(0f, pose.body.y + JellySpringPlayMotion.SPRING_HEIGHT * pose.springCompression, .0001f)
                assertTrue(pose.springCompression in .45f..1f)
            }
            for (step in 70..84) {
                val pose = JellySpringPlayMotion.sample(step / 100f, false, variation)
                assertEquals(0f, pose.body.y + JellySpringPlayMotion.SPRING_HEIGHT * pose.springCompression, .0001f)
                assertTrue(pose.springCompression in .45f..1f)
            }
        }
    }

    @Test
    fun scaleXPreservesAreaThroughoutAllVariations() {
        for (variation in CarePlayVariation.entries) for (step in 0..120) {
            val body = JellySpringPlayMotion.sample(step / 120f, false, variation).body
            assertEquals(1f, body.scaleX * body.scaleY, .0001f)
            assertTrue(body.scaleX.isFinite() && body.scaleY.isFinite())
        }
    }

    @Test
    fun phasesAreContinuousAtEveryBoundary() {
        val boundaries = listOf(.10f, .30f, .42f, .50f, .70f, .78f, .84f, .94f)
        for (variation in CarePlayVariation.entries) for (boundary in boundaries) {
            val before = JellySpringPlayMotion.sample(boundary - .00001f, false, variation)
            val after = JellySpringPlayMotion.sample(boundary + .00001f, false, variation)
            assertTrue(abs(before.body.x - after.body.x) < .001f)
            assertTrue(abs(before.body.y - after.body.y) < .001f)
            assertTrue(abs(before.body.scaleY - after.body.scaleY) < .002f)
            assertTrue(abs(before.springCompression - after.springCompression) < .002f)
        }
    }

    @Test
    fun flightHasParabolicLiftAndVariationOnlyChangesHeight() {
        for (variation in CarePlayVariation.entries) {
            val start = JellySpringPlayMotion.sample(.50f, false, variation)
            val apex = JellySpringPlayMotion.sample(.60f, false, variation)
            val end = JellySpringPlayMotion.sample(.70f, false, variation)
            assertEquals(start.body.y, end.body.y, .0001f)
            assertTrue(start.body.y <= -JellySpringPlayMotion.SPRING_HEIGHT)
            assertTrue(end.body.y <= -JellySpringPlayMotion.SPRING_HEIGHT)
            assertTrue(apex.body.y < start.body.y)
            assertEquals(1f, start.springCompression, .0001f)
            assertEquals(1f, apex.springCompression, .0001f)
            assertEquals(1f, end.springCompression, .0001f)
        }
    }

    @Test
    fun releaseVelocitiesMatchFollowingFlightSegments() {
        for (variation in CarePlayVariation.entries) {
            val h = .0001f
            val release = (JellySpringPlayMotion.sample(.50f + h, false, variation).body.y -
                JellySpringPlayMotion.sample(.50f - h, false, variation).body.y) / (2f * h)
            val flight = (JellySpringPlayMotion.sample(.50f + h, false, variation).body.y -
                JellySpringPlayMotion.sample(.50f, false, variation).body.y) / h
            assertEquals(flight, release, .03f)
            val second = (JellySpringPlayMotion.sample(.84f + h, false, variation).body.y -
                JellySpringPlayMotion.sample(.84f - h, false, variation).body.y) / (2f * h)
            val returning = (JellySpringPlayMotion.sample(.84f + h, false, variation).body.y -
                JellySpringPlayMotion.sample(.84f, false, variation).body.y) / h
            assertEquals(returning, second, .03f)
        }
    }
}
