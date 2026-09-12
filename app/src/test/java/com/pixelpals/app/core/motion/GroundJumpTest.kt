package com.pixelpals.app.core.motion

import org.junit.Assert.assertEquals
import org.junit.Test

class GroundJumpTest {
    @Test fun jumpLeavesAndReturnsToGroundWithConstantGravity(): Unit {
        assertEquals(600f, GroundJump.heightAt(600f, 500f, 0f), 0f)
        assertEquals(500f, GroundJump.heightAt(600f, 500f, .5f), 0f)
        assertEquals(600f, GroundJump.heightAt(600f, 500f, 1f), 0f)
        for (index: Int in 1..9) {
            val t: Float = index / 10f
            val acceleration: Float = GroundJump.heightAt(600f, 500f, t + .1f) -
                2f * GroundJump.heightAt(600f, 500f, t) + GroundJump.heightAt(600f, 500f, t - .1f)
            assertEquals(8f, acceleration, .001f)
            assertEquals(GroundJump.heightAt(600f, 500f, t),
                GroundJump.heightAt(600f, 500f, 1f - t), .001f)
        }
        assertEquals(600f, GroundJump.heightAt(600f, 500f, 2f), 0f)
    }
}
