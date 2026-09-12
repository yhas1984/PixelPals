package com.pixelpals.app.core.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class JellyElasticMotionTest {
    @Test
    fun easeClampsAndHasExpectedEndpoints() {
        assertEquals(0f, JellyElasticMotion.ease(-1f), 0f)
        assertEquals(0f, JellyElasticMotion.ease(0f), 0f)
        assertEquals(.5f, JellyElasticMotion.ease(.5f), 0f)
        assertEquals(1f, JellyElasticMotion.ease(1f), 0f)
        assertEquals(1f, JellyElasticMotion.ease(2f), 0f)
    }

    @Test
    fun preparationLandingAndTouchCurvesClampAtTheirContracts() {
        assertEquals(1f, JellyElasticMotion.prepareScaleY(0f), 0f)
        assertEquals(.82f, JellyElasticMotion.prepareScaleY(JellyElasticMotion.PREPARE_SECONDS), .0001f)
        assertEquals(.82f, JellyElasticMotion.landingScaleY(.10f), .0001f)
        assertEquals(1.045f, JellyElasticMotion.landingScaleY(.29f), .0001f)
        assertEquals(1f, JellyElasticMotion.landingScaleY(2f), .0001f)
        assertEquals(.74f, JellyElasticMotion.touchScaleY(.18f), .0001f)
        assertEquals(.78f, JellyElasticMotion.touchScaleY(.40f), .0001f)
        assertEquals(1.035f, JellyElasticMotion.touchScaleY(.72f), .0001f)
        assertEquals(1f, JellyElasticMotion.touchScaleY(2f), .0001f)
    }

    @Test
    fun piecewiseCurvesAreContinuousAcrossBoundaries() {
        val epsilon = .00001f
        listOf(.10f, .29f).forEach { boundary ->
            assertTrue(abs(JellyElasticMotion.landingScaleY(boundary - epsilon) -
                JellyElasticMotion.landingScaleY(boundary + epsilon)) < .001f)
        }
        listOf(.18f, .40f, .72f).forEach { boundary ->
            assertTrue(abs(JellyElasticMotion.touchScaleY(boundary - epsilon) -
                JellyElasticMotion.touchScaleY(boundary + epsilon)) < .001f)
        }
    }

    @Test
    fun airScaleIsSymmetricAndCapped() {
        assertEquals(JellyElasticMotion.airScaleY(-640f, 160f), JellyElasticMotion.airScaleY(640f, 160f), 0f)
        assertEquals(1.12f, JellyElasticMotion.airScaleY(100_000f, 160f), .0001f)
        assertTrue(JellyElasticMotion.airScaleY(100f, 0f).isFinite())
        assertTrue(JellyElasticMotion.airScaleY(100f, 0f) <= 1.12f)
    }

    @Test
    fun homeCycleStartsAndEndsAtNeutralPose() {
        val start = JellyElasticMotion.homePose(0f)
        val end = JellyElasticMotion.homePose(JellyElasticMotion.STRIDE)
        assertEquals(1f, start.scaleY, .0001f)
        assertEquals(0f, start.lift, .0001f)
        assertEquals(start, end)
        assertEquals(-18f, JellyElasticMotion.homePose(JellyElasticMotion.STRIDE * .43f).lift, .001f)
        assertEquals(JellyElasticMotion.homePose(42f), JellyElasticMotion.homePose(-42f))
    }

    @Test
    fun homePoseRemainsBoundedAtOneTwoZeroStepsPerSecond() {
        var previous = JellyElasticMotion.homePose(0f)
        for (step in 1..120) {
            val current = JellyElasticMotion.homePose(JellyElasticMotion.STRIDE * step / 120f)
            assertTrue(abs(current.scaleY - previous.scaleY) < .2f)
            assertTrue(abs(current.lift - previous.lift) < 6f)
            previous = current
        }
        assertEquals(1f, previous.scaleY, .0001f)
        assertEquals(0f, previous.lift, .0001f)
    }
}
