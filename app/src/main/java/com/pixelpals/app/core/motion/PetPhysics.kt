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
    val x: Float,
    val y: Float,
    val velocityX: Float,
    val velocityY: Float
) {
    init {
        require(x.isFinite() && y.isFinite()) { "Physics position must be finite" }
        require(velocityX.isFinite() && velocityY.isFinite()) { "Physics velocity must be finite" }
    }
}

enum class PhysicsEvent {
    /** El cuerpo sigue en movimiento. */
    MOVING,

    /** El cuerpo se detuvo (y se reacopló al perímetro si el perfil lo exige). */
    SETTLED
}

data class PhysicsStepResult(
    val body: PhysicsBody,
    val event: PhysicsEvent,
)

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
    ): PhysicsStepResult {
        require(dt >= 0f && dt.isFinite()) { "Physics delta must be finite and non-negative" }
        val config = profile.config
        val dragFactor = (1f - config.airDrag * dt).coerceAtLeast(0f)
        var velocityX: Float = body.velocityX * dragFactor
        var velocityY: Float = (body.velocityY + config.gravity * dt) * dragFactor
        var positionX: Float = body.x + velocityX * dt
        var positionY: Float = body.y + velocityY * dt
        if (positionX < bounds.left) {
            positionX = bounds.left.toFloat()
            velocityX = -velocityX * config.bounceX
        } else if (positionX > bounds.right) {
            positionX = bounds.right.toFloat()
            velocityX = -velocityX * config.bounceX
        }
        if (positionY < bounds.top) {
            positionY = bounds.top.toFloat()
            velocityY = -velocityY * config.bounceY
        } else if (positionY > bounds.floor) {
            positionY = bounds.floor.toFloat()
            if (velocityY > 0f) {
                velocityY = -velocityY * config.bounceY
                velocityX *= (1f - config.groundFriction * dt).coerceAtLeast(0f)
            }
        }
        val speed: Float = hypot(velocityX, velocityY)
        val onFloor: Boolean = positionY >= bounds.floor - 1f
        val isSettled: Boolean = if (config.requireFloorToRest) {
            onFloor && speed < config.restVelocity
        } else {
            speed < config.restVelocity
        }
        val movingBody: PhysicsBody = PhysicsBody(positionX, positionY, velocityX, velocityY)
        if (!isSettled) return PhysicsStepResult(movingBody, PhysicsEvent.MOVING)
        val settledBody: PhysicsBody = if (config.snapToEdge) snapToNearestEdge(movingBody, bounds) else {
            movingBody.copy(velocityX = 0f, velocityY = 0f)
        }
        return PhysicsStepResult(settledBody, PhysicsEvent.SETTLED)
    }

    /** Reacopla el cuerpo al lado del perímetro más cercano (trepadoras). */
    private fun snapToNearestEdge(body: PhysicsBody, bounds: PetBounds): PhysicsBody {
        val left = body.x - bounds.left
        val right = bounds.right - body.x
        val top = body.y - bounds.top
        val bottom = bounds.floor - body.y
        val minDistance = minOf(left, right, top, bottom)
        return when (minDistance) {
            left -> body.copy(x = bounds.left.toFloat(), velocityX = 0f, velocityY = 0f)
            right -> body.copy(x = bounds.right.toFloat(), velocityX = 0f, velocityY = 0f)
            top -> body.copy(y = bounds.top.toFloat(), velocityX = 0f, velocityY = 0f)
            else -> body.copy(y = bounds.floor.toFloat(), velocityX = 0f, velocityY = 0f)
        }
    }
}
