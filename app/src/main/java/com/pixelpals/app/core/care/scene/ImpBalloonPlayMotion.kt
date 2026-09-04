package com.pixelpals.app.core.care.scene

import kotlin.math.PI
import kotlin.math.sin

data class ImpBalloonPose(
    val x: Float,
    val y: Float,
    val scale: Float = 1f,
    val alpha: Float = 1f,
    val burst: Float = 0f,
)

data class ImpBalloonPlayPose(
    val balloons: List<ImpBalloonPose>,
    val activeBalloon: Int?,
    val thrust: Float,
    val celebration: Float,
)

/** Three readable trident strikes: prepare, thrust, pop, recover. */
object ImpBalloonPlayMotion {
    const val DURATION_MS: Long = 4_200L
    const val COMPLETION_MS: Long = 3_600L
    private const val POP_WINDOW: Float = .07f
    private const val THRUST_LEAD: Float = .15f
    private val popProgress: List<Float> = listOf(.28f, .54f, .79f)
    private val positions: List<CarePoint> = listOf(
        CarePoint(.30f, -.27f),
        CarePoint(.40f, -.43f),
        CarePoint(.28f, -.56f),
    )

    fun sample(progress: Float, reduced: Boolean): ImpBalloonPlayPose {
        val p: Float = progress.coerceIn(0f, 1f)
        val balloons: List<ImpBalloonPose> = positions.mapIndexed { index: Int, position: CarePoint ->
            getBalloonPose(index, position, p, reduced)
        }
        val active: Int? = popProgress.indices.firstOrNull { index: Int -> p < popProgress[index] + POP_WINDOW }
        val thrust: Float = active?.let { getThrust(p, popProgress[it], reduced) } ?: 0f
        return ImpBalloonPlayPose(
            balloons = balloons,
            activeBalloon = active,
            thrust = thrust,
            celebration = ((p - .84f) / .16f).coerceIn(0f, 1f),
        )
    }

    private fun getBalloonPose(
        index: Int,
        position: CarePoint,
        progress: Float,
        reduced: Boolean,
    ): ImpBalloonPose {
        val pop: Float = popProgress[index]
        val bob: Float = if (reduced) 0f else sin(progress * PI.toFloat() * 4f + index * 1.7f) * .018f
        if (reduced) {
            return ImpBalloonPose(position.x, position.y, alpha = if (progress < pop) 1f else 0f)
        }
        val local: Float = ((progress - pop) / POP_WINDOW).coerceIn(0f, 1f)
        val isBursting: Boolean = progress in pop..(pop + POP_WINDOW)
        return ImpBalloonPose(
            x = position.x,
            y = position.y + bob,
            scale = if (isBursting) 1f + .28f * local else 1f,
            alpha = if (progress < pop) 1f else if (isBursting) 1f - local else 0f,
            burst = if (isBursting) sin(local * PI).toFloat() else 0f,
        )
    }

    private fun getThrust(progress: Float, pop: Float, reduced: Boolean): Float {
        if (reduced) return if (progress < pop) .72f else 0f
        if (progress < pop - THRUST_LEAD || progress > pop + POP_WINDOW) return 0f
        if (progress <= pop) return smooth((progress - pop + THRUST_LEAD) / THRUST_LEAD)
        return 1f - smooth((progress - pop) / POP_WINDOW)
    }

    private fun smooth(value: Float): Float {
        val p: Float = value.coerceIn(0f, 1f)
        return p * p * (3f - 2f * p)
    }
}
