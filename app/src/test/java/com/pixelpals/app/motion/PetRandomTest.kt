package com.pixelpals.app.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PetRandomTest {
    private class SequenceRandom(private val floats: FloatArray, private val ints: IntArray) : PetRandom {
        private var fi = 0
        private var ii = 0
        override fun nextFloat(): Float = floats[fi++ % floats.size]
        override fun nextInt(from: Int, until: Int): Int = (ints[ii++ % ints.size]).coerceIn(from, until - 1)
    }

    @Test fun deterministicSequence() {
        val r = SequenceRandom(floatArrayOf(0.1f, 0.9f), intArrayOf(3, 7))
        assertEquals(0.1f, r.nextFloat(), 0.0001f)
        assertFalse(r.nextBoolean())
        assertEquals(3, r.nextInt(0, 10))
    }

    @Test fun helpersStayInRange() {
        val r = SequenceRandom(floatArrayOf(0.8f), intArrayOf(-4, 99))
        assertTrue(r.nextInt(5, 8) in 5..7)
        assertFalse(r.nextBoolean())
    }
}
