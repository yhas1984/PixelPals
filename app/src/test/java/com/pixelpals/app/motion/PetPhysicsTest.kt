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
    ): PhysicsStepResult {
        var currentBody = body
        var event = PhysicsEvent.MOVING
        val steps = (seconds * 60f).toInt()
        for (i in 0 until steps) {
            val result = PetPhysics.step(currentBody, fixedStep, bounds, profile)
            currentBody = result.body
            event = result.event
            if (event == PhysicsEvent.SETTLED) break
        }
        return PhysicsStepResult(currentBody, event)
    }

    @Test
    fun groundPetFallsAndLandsOnTheFloor() {
        val body = PhysicsBody(x = 500f, y = 100f, velocityX = 0f, velocityY = 0f)
        val result = simulate(body, PhysicsProfile.GROUND, 5f)
        assertEquals(bounds.floor.toFloat(), result.body.y, 1f)
        assertEquals(0f, result.body.velocityY, 0.01f)
    }

    @Test
    fun floorBounceReversesVelocityWithLoss() {
        val body = PhysicsBody(
            x = 500f,
            y = bounds.floor + 5f,
            velocityX = 0f,
            velocityY = 500f
        )
        val result = PetPhysics.step(body, fixedStep, bounds, PhysicsProfile.GROUND)
        assertEquals(bounds.floor.toFloat(), result.body.y, 0.5f)
        // La velocidad al impactar incluye la gravedad del paso; el rebote
        // conserva solo el 30%, así que la salida es mucho menor que la entrada.
        assertTrue(result.body.velocityY < 0f)
        assertTrue(result.body.velocityY > -200f)
    }

    @Test
    fun wallsClampAndBounceBack() {
        val body = PhysicsBody(
            x = -50f,
            y = 500f,
            velocityX = -300f,
            velocityY = 0f
        )
        val result = PetPhysics.step(body, fixedStep, bounds, PhysicsProfile.GROUND)
        assertEquals(bounds.left.toFloat(), result.body.x, 0.5f)
        assertTrue(result.body.velocityX > 0f)
    }

    @Test
    fun ceilingClampsAndBouncesDown() {
        val body = PhysicsBody(
            x = 500f,
            y = bounds.top - 30f,
            velocityX = 0f,
            velocityY = -400f
        )
        val result = PetPhysics.step(body, fixedStep, bounds, PhysicsProfile.GROUND)
        assertEquals(bounds.top.toFloat(), result.body.y, 0.5f)
        assertTrue(result.body.velocityY > 0f)
    }

    @Test
    fun groundProfileDoesNotSettleMidAir() {
        val body = PhysicsBody(x = 500f, y = 1_000f, velocityX = 0f, velocityY = 10f)
        val result = simulate(body, PhysicsProfile.GROUND, 0.5f)
        assertEquals(PhysicsEvent.MOVING, result.event)
        assertTrue(result.body.y < bounds.floor)
    }

    @Test
    fun groundProfileSettlesOnceOnFloorAndSlow() {
        val body = PhysicsBody(
            x = 500f,
            y = bounds.floor.toFloat(),
            velocityX = 10f,
            velocityY = 5f
        )
        val result = simulate(body, PhysicsProfile.GROUND, 0.5f)
        assertEquals(PhysicsEvent.SETTLED, result.event)
    }

    @Test
    fun flyingProfileDampsAndSettlesInAir() {
        val body = PhysicsBody(x = 500f, y = 1_000f, velocityX = 400f, velocityY = 0f)
        val result = simulate(body, PhysicsProfile.FLYING, 3f)
        assertEquals(PhysicsEvent.SETTLED, result.event)
        assertTrue(result.body.y < bounds.floor)
    }

    @Test
    fun aquaticProfileFallsSlowerThanGround() {
        fun fallSpeed(profile: PhysicsProfile): Float {
            val body = PhysicsBody(x = 500f, y = 500f, velocityX = 0f, velocityY = 0f)
            return simulate(body, profile, 1f).body.velocityY
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
        val result = simulate(body, PhysicsProfile.EDGE, 3f)
        assertEquals(PhysicsEvent.SETTLED, result.event)
        assertEquals(bounds.right.toFloat(), result.body.x, 0.5f)
        assertEquals(0f, result.body.velocityX, 0.01f)
        assertEquals(0f, result.body.velocityY, 0.01f)
    }

    @Test
    fun edgeProfileNearFloorSnapsToFloor() {
        val body = PhysicsBody(
            x = 500f,
            y = bounds.floor - 5f,
            velocityX = 5f,
            velocityY = 5f
        )
        val result = simulate(body, PhysicsProfile.EDGE, 3f)
        assertEquals(bounds.floor.toFloat(), result.body.y, 0.5f)
    }
}
