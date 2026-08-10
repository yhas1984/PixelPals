package com.pixelpals.app.core.motion

import kotlin.math.hypot

/**
 * Perfil físico de cada especie. Comparten el mismo motor ([PetPhysics.step])
 * pero con constantes distintas: los terrestres caen y rebotan, los voladores
 * amortiguan en el aire, los acuáticos caen lento con mucha fricción y los
 * trepadores de borde se reacoplan al perímetro al detenerse.
 */
enum class PhysicsProfile(val config: PhysicsConfig) {
    GROUND(
        PhysicsConfig(
            gravity = 1_400f,
            bounceX = 0.45f,
            bounceY = 0.3f,
            groundFriction = 5f,
            airDrag = 0.3f,
            restVelocity = 45f,
            requireFloorToRest = true,
            snapToEdge = false
        )
    ),
    FLYING(
        PhysicsConfig(
            gravity = 60f,
            bounceX = 0.5f,
            bounceY = 0.55f,
            groundFriction = 0f,
            airDrag = 1.8f,
            restVelocity = 60f,
            requireFloorToRest = false,
            snapToEdge = false
        )
    ),
    AQUATIC(
        PhysicsConfig(
            gravity = 120f,
            bounceX = 0.3f,
            bounceY = 0.25f,
            groundFriction = 1f,
            airDrag = 4f,
            restVelocity = 50f,
            requireFloorToRest = false,
            snapToEdge = false
        )
    ),
    EDGE(
        PhysicsConfig(
            gravity = 80f,
            bounceX = 0.15f,
            bounceY = 0.15f,
            groundFriction = 0f,
            airDrag = 2.8f,
            restVelocity = 40f,
            requireFloorToRest = false,
            snapToEdge = true
        )
    )
}

data class PhysicsConfig(
    val gravity: Float,
    val bounceX: Float,
    val bounceY: Float,
    val groundFriction: Float,
    val airDrag: Float,
    val restVelocity: Float,
    val requireFloorToRest: Boolean,
    val snapToEdge: Boolean
)

/** Cuerpo físico en coordenadas LÓGICAS del sprite (esquina superior izquierda). */
data class PhysicsBody(
    var x: Float,
    var y: Float,
    var velocityX: Float,
    var velocityY: Float
)

enum class PhysicsEvent {
    /** El cuerpo sigue en movimiento. */
    MOVING,

    /** El cuerpo se detuvo (y se reacopló al perímetro si el perfil lo exige). */
    SETTLED
}

/**
 * Motor físico puro (sin dependencias de Android): integración semiimplícita,
 * colisiones contra [PetBounds], rebotes, fricción y umbral de reposo.
 */
object PetPhysics {

    fun step(
        body: PhysicsBody,
        dt: Float,
        bounds: PetBounds,
        profile: PhysicsProfile
    ): PhysicsEvent {
        val config = profile.config
        body.velocityY += config.gravity * dt

        val dragFactor = (1f - config.airDrag * dt).coerceAtLeast(0f)
        body.velocityX *= dragFactor
        body.velocityY *= dragFactor

        body.x += body.velocityX * dt
        body.y += body.velocityY * dt

        if (body.x < bounds.left) {
            body.x = bounds.left.toFloat()
            body.velocityX = -body.velocityX * config.bounceX
        } else if (body.x > bounds.right) {
            body.x = bounds.right.toFloat()
            body.velocityX = -body.velocityX * config.bounceX
        }

        if (body.y < bounds.top) {
            body.y = bounds.top.toFloat()
            body.velocityY = -body.velocityY * config.bounceY
        } else if (body.y > bounds.floor) {
            body.y = bounds.floor.toFloat()
            if (body.velocityY > 0f) {
                body.velocityY = -body.velocityY * config.bounceY
                body.velocityX *= (1f - config.groundFriction * dt).coerceAtLeast(0f)
            }
        }

        val speed = hypot(body.velocityX, body.velocityY)
        val onFloor = body.y >= bounds.floor - 1f
        val settled = if (config.requireFloorToRest) {
            onFloor && speed < config.restVelocity
        } else {
            speed < config.restVelocity
        }
        if (!settled) return PhysicsEvent.MOVING

        if (config.snapToEdge) snapToNearestEdge(body, bounds)
        return PhysicsEvent.SETTLED
    }

    /** Reacopla el cuerpo al lado del perímetro más cercano (trepadoras). */
    private fun snapToNearestEdge(body: PhysicsBody, bounds: PetBounds) {
        val left = body.x - bounds.left
        val right = bounds.right - body.x
        val top = body.y - bounds.top
        val bottom = bounds.floor - body.y
        val minDistance = minOf(left, right, top, bottom)
        when (minDistance) {
            left -> body.x = bounds.left.toFloat()
            right -> body.x = bounds.right.toFloat()
            top -> body.y = bounds.top.toFloat()
            else -> body.y = bounds.floor.toFloat()
        }
        body.velocityX = 0f
        body.velocityY = 0f
    }
}
