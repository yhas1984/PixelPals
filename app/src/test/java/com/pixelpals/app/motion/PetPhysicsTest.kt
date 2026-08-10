package com.pixelpals.app.core.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PetPhysicsTest {

    private val fixedStep = 1f / 60f
    private val bounds = PetBounds.compute(
        screenWidth = 1_080,
        screenHeight = 2_400,
        petSpriteSize = 80,
        topSystemInsetPx = 100,
        bottomSystemInsetPx = 200
    )

    private fun simulate(
        body: PhysicsBody,
        profile: PhysicsProfile,
        seconds: Float
    ): PhysicsEvent {
        var event = PhysicsEvent.MOVING
        val steps = (seconds * 60f).toInt()
        for (i in 0 until steps) {
            event = PetPhysics.step(body, fixedStep, bounds, profile)
            if (event == PhysicsEvent.SETTLED) break
        }
        return event
    }

    @Test
    fun groundPetFallsAndLandsOnTheFloor() {
        val body = PhysicsBody(x = 500f, y = 100f, velocityX = 0f, velocityY = 0f)
        simulate(body, PhysicsProfile.GROUND, 5f)
        assertEquals(bounds.floor.toFloat(), body.y, 1f)
        assertTrue(body.velocityY <= 0f || body.velocityY == 0f)
    }

    @Test
    fun floorBounceReversesVelocityWithLoss() {
        val body = PhysicsBody(
            x = 500f,
            y = bounds.floor + 5f,
            velocityX = 0f,
            velocityY = 500f
        )
        PetPhysics.step(body, fixedStep, bounds, PhysicsProfile.GROUND)
        assertEquals(bounds.floor.toFloat(), body.y, 0.5f)
        // La velocidad al impactar incluye la gravedad del paso; el rebote
        // conserva solo el 30%, así que la salida es mucho menor que la entrada.
        assertTrue(body.velocityY < 0f)
        assertTrue(body.velocityY > -200f)
    }

    @Test
    fun wallsClampAndBounceBack() {
        val body = PhysicsBody(
            x = -50f,
            y = 500f,
            velocityX = -300f,
            velocityY = 0f
        )
        PetPhysics.step(body, fixedStep, bounds, PhysicsProfile.GROUND)
        assertEquals(bounds.left.toFloat(), body.x, 0.5f)
        assertTrue(body.velocityX > 0f)
    }

    @Test
    fun ceilingClampsAndBouncesDown() {
        val body = PhysicsBody(
            x = 500f,
            y = bounds.top - 30f,
            velocityX = 0f,
            velocityY = -400f
        )
        PetPhysics.step(body, fixedStep, bounds, PhysicsProfile.GROUND)
        assertEquals(bounds.top.toFloat(), body.y, 0.5f)
        assertTrue(body.velocityY > 0f)
    }

    @Test
    fun groundProfileDoesNotSettleMidAir() {
        val body = PhysicsBody(x = 500f, y = 1_000f, velocityX = 0f, velocityY = 10f)
        val event = simulate(body, PhysicsProfile.GROUND, 0.5f)
        assertEquals(PhysicsEvent.MOVING, event)
        assertTrue(body.y < bounds.floor)
    }

    @Test
    fun groundProfileSettlesOnceOnFloorAndSlow() {
        val body = PhysicsBody(
            x = 500f,
            y = bounds.floor.toFloat(),
            velocityX = 10f,
            velocityY = 5f
        )
        val event = simulate(body, PhysicsProfile.GROUND, 0.5f)
        assertEquals(PhysicsEvent.SETTLED, event)
    }

    @Test
    fun flyingProfileDampsAndSettlesInAir() {
        val body = PhysicsBody(x = 500f, y = 1_000f, velocityX = 400f, velocityY = 0f)
        val event = simulate(body, PhysicsProfile.FLYING, 3f)
        assertEquals(PhysicsEvent.SETTLED, event)
        assertTrue(body.y < bounds.floor)
    }

    @Test
    fun aquaticProfileFallsSlowerThanGround() {
        fun fallSpeed(profile: PhysicsProfile): Float {
            val body = PhysicsBody(x = 500f, y = 500f, velocityX = 0f, velocityY = 0f)
            simulate(body, profile, 1f)
            return body.velocityY
        }
        assertTrue(fallSpeed(PhysicsProfile.AQUATIC) < fallSpeed(PhysicsProfile.GROUND))
    }

    @Test
    fun edgeProfileSnapsToNearestSideOnSettle() {
        val body = PhysicsBody(
            x = bounds.right - 5f,
            y = 500f,
            velocityX = -20f,
            velocityY = -20f
        )
        val event = simulate(body, PhysicsProfile.EDGE, 3f)
        assertEquals(PhysicsEvent.SETTLED, event)
        assertEquals(bounds.right.toFloat(), body.x, 0.5f)
        assertEquals(0f, body.velocityX, 0.01f)
        assertEquals(0f, body.velocityY, 0.01f)
    }

    @Test
    fun edgeProfileNearFloorSnapsToFloor() {
        val body = PhysicsBody(
            x = 500f,
            y = bounds.floor - 5f,
            velocityX = 5f,
            velocityY = 5f
        )
        simulate(body, PhysicsProfile.EDGE, 3f)
        assertEquals(bounds.floor.toFloat(), body.y, 0.5f)
    }
}
