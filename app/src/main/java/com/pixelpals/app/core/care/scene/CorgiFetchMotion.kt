package com.pixelpals.app.core.care.scene

import com.pixelpals.app.core.motion.PetBounds
import kotlin.math.abs
import kotlin.math.sin

data class CorgiFetchPlan(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val floorY: Float,
    val spriteSize: Float,
    val direction: Float,
    val catchMs: Long,
    val reducedMotion: Boolean,
) {
    val timing: CareSceneTiming = CareSceneTiming(catchMs + 900L, catchMs)
}

data class CorgiFetchPose(
    val petX: Float,
    val petY: Float,
    val regularFrame: Int?,
    val careFrame: Int,
    val ballX: Float,
    val ballY: Float,
    val ballRotation: Float,
    val isCaught: Boolean,
)

/** World-space fetch: the pet window runs after an independent rolling prop. */
object CorgiFetchMotion {
    private const val REACTION_MS: Long = 160L
    private const val LOWER_HEAD_MS: Long = 220L
    private const val RUN_SPEED_SPRITES_PER_SECOND: Float = 2.3f
    private const val MAX_DISTANCE_SPRITES: Float = 2.6f

    fun createPlan(start: CarePoint, bounds: PetBounds, spriteSize: Int,
                   facingLeft: Boolean, reducedMotion: Boolean): CorgiFetchPlan {
        require(spriteSize > 0 && bounds.right >= bounds.left)
        val size: Float = spriteSize.toFloat()
        val x: Float = start.x.coerceIn(bounds.left.toFloat(), bounds.right.toFloat())
        val leftRoom: Float = x - bounds.left
        val rightRoom: Float = bounds.right - x
        val preferred: Float = if (facingLeft) -1f else 1f
        val preferredRoom: Float = if (facingLeft) leftRoom else rightRoom
        val direction: Float = if (preferredRoom >= size * 1.5f) preferred
            else if (rightRoom >= leftRoom) 1f else -1f
        val available: Float = if (direction > 0f) rightRoom else leftRoom
        val distance: Float = if (reducedMotion) 0f else minOf(size * MAX_DISTANCE_SPRITES, available * .90f)
        val runMs: Long = (distance / size / RUN_SPEED_SPRITES_PER_SECOND * 1_000f).toLong().coerceIn(650L, 1_400L)
        return CorgiFetchPlan(x, start.y, x + direction * distance, bounds.floor.toFloat(), size,
            direction, if (reducedMotion) 360L else REACTION_MS + runMs, reducedMotion)
    }

    fun getPose(plan: CorgiFetchPlan, elapsedMs: Long): CorgiFetchPose {
        val elapsed: Long = elapsedMs.coerceAtLeast(0L)
        val run: Float = ((elapsed - REACTION_MS).toFloat() / (plan.catchMs - REACTION_MS)).coerceIn(0f, 1f)
        val travel: Float = run * run * (3f - 2f * run)
        val petX: Float = plan.startX + (plan.endX - plan.startX) * travel
        val landing: Float = (elapsed.toFloat() / REACTION_MS).coerceIn(0f, 1f)
        val petY: Float = if (plan.reducedMotion) plan.startY else plan.startY + (plan.floorY - plan.startY) * landing
        val regularFrame: Int? = when {
            plan.reducedMotion -> null
            elapsed < REACTION_MS -> 0
            elapsed < plan.catchMs - LOWER_HEAD_MS -> 10 + (abs(petX - plan.startX) / (plan.spriteSize * .10f)).toInt() % 4
            else -> null
        }
        val careFrame: Int = when {
            plan.reducedMotion -> 2
            elapsed < plan.catchMs + 100L -> 0
            elapsed < plan.catchMs + 240L -> 1
            else -> 2
        }
        val initialBall: Float = plan.startX + plan.spriteSize * .5f + plan.direction * plan.spriteSize * .44f
        val targetBall: Float = plan.endX + plan.spriteSize * .5f + plan.direction * plan.spriteSize * .25f
        val roll: Float = (elapsed.toFloat() / plan.catchMs).coerceIn(0f, 1f)
        val ballX: Float = initialBall + (targetBall - initialBall) * (1f - (1f - roll) * (1f - roll))
        val bounce: Float = if (plan.reducedMotion) 0f else abs(sin(elapsed / 75f)) *
            (1f - elapsed / 450f).coerceAtLeast(0f) * plan.spriteSize * .06f
        return CorgiFetchPose(petX, petY, regularFrame, careFrame, ballX,
            plan.floorY + plan.spriteSize * .86f - bounce,
            (ballX - initialBall) / (plan.spriteSize * .10f) * 57.29578f, elapsed >= plan.catchMs)
    }
}
