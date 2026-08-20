package com.pixelpals.app.core.runtime

import com.pixelpals.app.core.motion.PetBounds
import com.pixelpals.app.core.motion.PhysicsProfile

data class PetSurfaceAttachment(
    val surface: PetSurface,
    val position: PetVector,
)

object PetSurfaceResolver {
    fun attach(position: PetVector, bounds: PetBounds, profile: PhysicsProfile): PetSurfaceAttachment = when (profile) {
        PhysicsProfile.GROUND -> PetSurfaceAttachment(
            surface = PetSurface.FLOOR,
            position = PetVector(position.x.coerceIn(bounds.left.toFloat(), bounds.right.toFloat()), bounds.floor.toFloat()),
        )
        PhysicsProfile.EDGE -> attachToNearestEdge(position, bounds)
        PhysicsProfile.FLYING,
        PhysicsProfile.AQUATIC,
        -> PetSurfaceAttachment(
            surface = PetSurface.FREE,
            position = PetVector(
                position.x.coerceIn(bounds.left.toFloat(), bounds.right.toFloat()),
                position.y.coerceIn(bounds.top.toFloat(), bounds.floor.toFloat()),
            ),
        )
    }

    private fun attachToNearestEdge(position: PetVector, bounds: PetBounds): PetSurfaceAttachment {
        val clampedX: Float = position.x.coerceIn(bounds.left.toFloat(), bounds.right.toFloat())
        val clampedY: Float = position.y.coerceIn(bounds.top.toFloat(), bounds.floor.toFloat())
        val distances: List<Pair<PetSurface, Float>> = listOf(
            PetSurface.LEFT_WALL to clampedX - bounds.left,
            PetSurface.RIGHT_WALL to bounds.right - clampedX,
            PetSurface.CEILING to clampedY - bounds.top,
            PetSurface.FLOOR to bounds.floor - clampedY,
        )
        return when (distances.minBy { entry -> entry.second }.first) {
            PetSurface.LEFT_WALL -> PetSurfaceAttachment(PetSurface.LEFT_WALL, PetVector(bounds.left.toFloat(), clampedY))
            PetSurface.RIGHT_WALL -> PetSurfaceAttachment(PetSurface.RIGHT_WALL, PetVector(bounds.right.toFloat(), clampedY))
            PetSurface.CEILING -> PetSurfaceAttachment(PetSurface.CEILING, PetVector(clampedX, bounds.top.toFloat()))
            else -> PetSurfaceAttachment(PetSurface.FLOOR, PetVector(clampedX, bounds.floor.toFloat()))
        }
    }
}
