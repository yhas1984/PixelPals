package com.pixelpals.app.core.care.scene

import kotlin.math.PI
import kotlin.math.sin

data class CorgiAdditionalCarePose(
    val frame: Int,
    val propOffsetX: Float = 0f,
    val propOffsetY: Float = 0f,
    val propAlpha: Float = 1f,
    val contentAmount: Float = 1f,
    val foamAmount: Float = 0f,
    val rotation: Float = 0f,
    val breathScale: Float = 1f,
)

/** Local sprite-space choreography; it never moves or duplicates the desktop pet window. */
object CorgiAdditionalCareMotion {
    val actions: Set<CareSceneAction> = setOf(CareSceneAction.CLEAN, CareSceneAction.REST, CareSceneAction.MEDICINE)
    private val cleanTiming: CareSceneTiming = CareSceneTiming(4_000L, 3_400L)
    private val restTiming: CareSceneTiming = CareSceneTiming(7_000L, 5_800L)
    private val medicineTiming: CareSceneTiming = CareSceneTiming(4_000L, 3_200L)

    fun getTiming(action: CareSceneAction): CareSceneTiming = when (action) {
        CareSceneAction.CLEAN -> cleanTiming
        CareSceneAction.REST -> restTiming
        CareSceneAction.MEDICINE -> medicineTiming
        else -> error("Unsupported additional care action: $action")
    }

    fun getPose(action: CareSceneAction, elapsedMs: Long, reducedMotion: Boolean): CorgiAdditionalCarePose {
        val elapsed: Long = elapsedMs.coerceIn(0L, getTiming(action).durationMs)
        return when (action) {
            CareSceneAction.CLEAN -> getCleaningPose(elapsed, reducedMotion)
            CareSceneAction.REST -> getRestingPose(elapsed, reducedMotion)
            CareSceneAction.MEDICINE -> getMedicinePose(elapsed, reducedMotion)
            else -> error("Unsupported additional care action: $action")
        }
    }

    private fun getCleaningPose(elapsed: Long, reduced: Boolean): CorgiAdditionalCarePose {
        val entry: Float = fraction(elapsed, 0L, 450L)
        val exit: Float = fraction(elapsed, 2_550L, 2_950L)
        val scrubbing: Boolean = elapsed in 450L..2_550L
        val stroke: Float = if (!reduced && scrubbing) sin((elapsed - 450L) / 150f) else 0f
        val shaking: Boolean = !reduced && elapsed in 2_850L..3_350L
        val frame: Int = if (reduced) 13 else when {
            elapsed < 450L -> 12
            elapsed < 2_850L -> 13
            elapsed < 3_400L -> 14
            else -> 15
        }
        return CorgiAdditionalCarePose(frame,
            propOffsetX = if (reduced) 0f else (1f - entry) * .28f + stroke * .11f + exit * .22f,
            propOffsetY = if (reduced) 0f else -kotlin.math.abs(stroke) * .025f,
            propAlpha = if (reduced) 1f else entry * (1f - exit),
            foamAmount = if (reduced) 0f else entry * (1f - fraction(elapsed, 2_700L, 3_400L)),
            rotation = if (shaking) sin((elapsed - 2_850L) / 35f) * 3f else 0f)
    }

    private fun getRestingPose(elapsed: Long, reduced: Boolean): CorgiAdditionalCarePose {
        val frame: Int = if (reduced) 18 else when {
            elapsed < 700L -> 16
            elapsed < 1_500L -> 17
            elapsed < 2_300L -> 18
            else -> 19
        }
        val breath: Float = if (reduced || elapsed < 1_500L) 0f
            else sin((elapsed - 1_500L) / 2_200f * PI.toFloat()) * .012f
        return CorgiAdditionalCarePose(frame, propAlpha = if (reduced) 1f else fraction(elapsed, 0L, 400L),
            breathScale = 1f + breath)
    }

    private fun getMedicinePose(elapsed: Long, reduced: Boolean): CorgiAdditionalCarePose {
        val approach: Float = fraction(elapsed, 200L, 1_000L)
        val retreat: Float = fraction(elapsed, 2_200L, 2_800L)
        val frame: Int = if (reduced) 20 else when {
            elapsed < 650L -> 20
            elapsed < 2_200L -> 21
            elapsed < 3_200L -> 22
            else -> 23
        }
        return CorgiAdditionalCarePose(frame,
            propOffsetX = if (reduced) 0f else (1f - approach + retreat) * .24f,
            propOffsetY = if (reduced) 0f else (1f - approach + retreat) * .08f,
            propAlpha = if (reduced) 1f else fraction(elapsed, 0L, 200L) * (1f - retreat),
            contentAmount = 1f - fraction(elapsed, 1_100L, 2_150L))
    }

    private fun fraction(elapsed: Long, start: Long, end: Long): Float =
        ((elapsed - start).toFloat() / (end - start)).coerceIn(0f, 1f)
}
