package com.pixelpals.app.core.runtime.pets

import com.pixelpals.app.core.motion.PetRandom
import com.pixelpals.app.core.runtime.PetActionCandidate
import com.pixelpals.app.core.runtime.PetActionScheduler
import com.pixelpals.app.core.runtime.PetBrain
import com.pixelpals.app.core.runtime.PetBrainContext
import com.pixelpals.app.core.runtime.PetBrainResult
import com.pixelpals.app.core.runtime.PetBrainState
import com.pixelpals.app.core.runtime.PetEvent
import com.pixelpals.app.core.runtime.PetFacing
import com.pixelpals.app.core.runtime.PetIntent
import com.pixelpals.app.core.runtime.PetTransform
import com.pixelpals.app.core.runtime.PetVector
import kotlin.math.abs
import kotlin.math.sin

enum class YukiRuntimeMode {
    IDLE,
    WALK,
    BLINK,
    CURIOSITY,
    TOUCH,
    MELT,
    SLEEP,
    DRAG,
    AIRBORNE,
    RECOVER,
}

data class YukiRuntimeState(
    val mode: YukiRuntimeMode = YukiRuntimeMode.IDLE,
    val elapsedSeconds: Float = 0f,
    val durationSeconds: Float = 3f,
    val facing: PetFacing = PetFacing.RIGHT,
    val walkStartX: Float = 0f,
    val walkTargetX: Float = 0f,
    val walkDurationSeconds: Float = 1f,
    val meltLatched: Boolean = false,
) : PetBrainState

class YukiRuntimeBrain(
    private val random: PetRandom,
) : PetBrain<YukiRuntimeState> {
    private val scheduler = PetActionScheduler(random)

    override fun createInitialState(context: PetBrainContext): YukiRuntimeState = YukiRuntimeState(
        mode = if (isHot(context)) YukiRuntimeMode.MELT else YukiRuntimeMode.IDLE,
        facing = context.facing,
        walkStartX = context.position.x,
        walkTargetX = context.position.x,
        meltLatched = isHot(context),
    )

    override fun reduce(
        state: YukiRuntimeState,
        event: PetEvent,
        context: PetBrainContext,
    ): PetBrainResult<YukiRuntimeState> = when (event) {
        PetEvent.Tap -> output(
            state.copy(mode = YukiRuntimeMode.CURIOSITY, elapsedSeconds = 0f),
            "happy",
        )
        PetEvent.HoldStarted -> output(
            state.copy(mode = YukiRuntimeMode.TOUCH, elapsedSeconds = 0f),
            "touch",
            hapticDurationMs = YukiRuntimeDefinition.value.interaction.holdHapticDurationMs,
        )
        PetEvent.HoldReleased -> output(
            state.copy(mode = YukiRuntimeMode.RECOVER, elapsedSeconds = 0f),
            "happy",
        )
        is PetEvent.DragStarted,
        is PetEvent.DragMoved,
        -> output(
            state.copy(mode = YukiRuntimeMode.DRAG, elapsedSeconds = 0f),
            "happy",
            transform = dragTransform(state.elapsedSeconds),
        )
        is PetEvent.Released,
        is PetEvent.Flung,
        -> output(
            state.copy(mode = YukiRuntimeMode.AIRBORNE, elapsedSeconds = 0f),
            "jump",
            transform = PetTransform(scaleX = 0.94f, scaleY = 1.08f),
        )
        PetEvent.RecoveryCompleted -> if (isHot(context)) {
            melt(state)
        } else {
            output(
                state.copy(mode = YukiRuntimeMode.RECOVER, elapsedSeconds = 0f),
                "happy",
                transform = PetTransform(scaleX = 1.04f, scaleY = 0.96f),
            )
        }
        PetEvent.Cancelled -> thermalIdle(state, context)
        PetEvent.Resumed -> thermalOutput(state, context)
        is PetEvent.EnvironmentChanged -> thermalOutput(state, context)
        is PetEvent.Tick -> update(state, event.deltaSeconds, context)
        is PetEvent.StatusChanged,
        PetEvent.Paused,
        PetEvent.Destroyed,
        -> output(state, clipFor(state.mode))
    }

    private fun update(
        state: YukiRuntimeState,
        deltaSeconds: Float,
        context: PetBrainContext,
    ): PetBrainResult<YukiRuntimeState> {
        val thermal = thermalMode(state, context)
        if (thermal == YukiRuntimeMode.MELT && state.mode != YukiRuntimeMode.MELT) return melt(state)
        if (state.mode == YukiRuntimeMode.MELT) {
            return if (thermal == YukiRuntimeMode.MELT) {
                output(
                    state.copy(elapsedSeconds = state.elapsedSeconds + deltaSeconds),
                    "melt",
                    transform = meltTransform(),
                )
            } else {
                idle(state.copy(meltLatched = false), context)
            }
        }

        val advanced = state.copy(elapsedSeconds = state.elapsedSeconds + deltaSeconds)
        return when (state.mode) {
            YukiRuntimeMode.IDLE -> if (advanced.elapsedSeconds >= state.durationSeconds) {
                beginAutonomous(advanced, context)
            } else {
                output(advanced, "idle", transform = dreamyTransform(advanced.elapsedSeconds))
            }
            YukiRuntimeMode.WALK -> updateWalk(advanced, context)
            YukiRuntimeMode.BLINK -> if (context.playback.isFinished) idle(advanced, context) else output(advanced, "blink")
            YukiRuntimeMode.CURIOSITY -> if (advanced.elapsedSeconds >= CURIOSITY_SECONDS) {
                idle(advanced, context)
            } else {
                output(advanced, "happy", transform = curiousTransform(advanced.elapsedSeconds))
            }
            YukiRuntimeMode.TOUCH -> if (context.playback.isFinished || advanced.elapsedSeconds >= TOUCH_SECONDS) {
                idle(advanced, context)
            } else {
                output(advanced, "touch")
            }
            YukiRuntimeMode.SLEEP -> if (advanced.elapsedSeconds >= state.durationSeconds) {
                idle(advanced, context)
            } else {
                output(advanced, "sleep", transform = dreamyTransform(advanced.elapsedSeconds, 0.7f))
            }
            YukiRuntimeMode.RECOVER -> if (advanced.elapsedSeconds >= RECOVERY_SECONDS) {
                thermalIdle(advanced, context)
            } else {
                output(advanced, "happy", transform = recoveryTransform(advanced.elapsedSeconds))
            }
            YukiRuntimeMode.DRAG -> output(advanced, "happy", transform = dragTransform(advanced.elapsedSeconds))
            YukiRuntimeMode.AIRBORNE -> output(advanced, "jump", transform = PetTransform(scaleX = 0.94f, scaleY = 1.08f))
            YukiRuntimeMode.MELT -> error("Melt is handled before regular modes")
        }
    }

    private fun beginAutonomous(
        state: YukiRuntimeState,
        context: PetBrainContext,
    ): PetBrainResult<YukiRuntimeState> {
        val temperament = YukiRuntimeDefinition.value.temperament
        val energy = context.status.energy / 100f
        return when (
            scheduler.select(
                candidates = listOf(
                    PetActionCandidate(PetIntent.WALK, 0.50f, temperament.energy, 0.55f + energy),
                    PetActionCandidate(PetIntent.CURIOSITY, 0.25f, temperament.curiosity),
                    PetActionCandidate(PetIntent.CUSTOM, 0.10f),
                    PetActionCandidate(PetIntent.SLEEP, 0.15f, 1.25f - energy * 0.5f),
                ),
                recentActions = context.recentActions,
            )
        ) {
            PetIntent.WALK -> beginWalk(state, context)
            PetIntent.CURIOSITY -> output(
                state.copy(mode = YukiRuntimeMode.CURIOSITY, elapsedSeconds = 0f),
                "happy",
            )
            PetIntent.CUSTOM -> output(
                state.copy(mode = YukiRuntimeMode.BLINK, elapsedSeconds = 0f),
                "blink",
            )
            else -> output(
                state.copy(
                    mode = YukiRuntimeMode.SLEEP,
                    elapsedSeconds = 0f,
                    durationSeconds = 4f + random.nextFloat() * 4f,
                ),
                "sleep",
            )
        }
    }

    private fun beginWalk(
        state: YukiRuntimeState,
        context: PetBrainContext,
    ): PetBrainResult<YukiRuntimeState> {
        val bounds = context.environment.bounds
        val startX = context.position.x.coerceIn(bounds.left.toFloat(), bounds.right.toFloat())
        var targetX = bounds.left + random.nextFloat() * (bounds.right - bounds.left).coerceAtLeast(1)
        if (abs(targetX - startX) < MINIMUM_WALK_DISTANCE_PIXELS) {
            targetX = if (startX < (bounds.left + bounds.right) / 2f) bounds.right.toFloat() else bounds.left.toFloat()
        }
        val facing = if (targetX >= startX) PetFacing.RIGHT else PetFacing.LEFT
        val duration = (abs(targetX - startX) / YukiRuntimeDefinition.WALK_SPEED_PIXELS_PER_SECOND)
            .coerceAtLeast(MINIMUM_WALK_DURATION_SECONDS)
        val next = state.copy(
            mode = YukiRuntimeMode.WALK,
            elapsedSeconds = 0f,
            facing = facing,
            walkStartX = startX,
            walkTargetX = targetX,
            walkDurationSeconds = duration,
        )
        return output(next, "walk")
    }

    private fun updateWalk(
        state: YukiRuntimeState,
        context: PetBrainContext,
    ): PetBrainResult<YukiRuntimeState> {
        val progress = (state.elapsedSeconds / state.walkDurationSeconds).coerceIn(0f, 1f)
        val x = state.walkStartX + (state.walkTargetX - state.walkStartX) * progress
        val position = PetVector(x, context.environment.bounds.floor.toFloat())
        return if (progress >= 1f) {
            idle(state, context, position)
        } else {
            output(state, "walk", position, dreamyTransform(state.elapsedSeconds, 0.8f))
        }
    }

    private fun thermalOutput(
        state: YukiRuntimeState,
        context: PetBrainContext,
    ): PetBrainResult<YukiRuntimeState> = when {
        thermalMode(state, context) == YukiRuntimeMode.MELT -> melt(state)
        state.mode == YukiRuntimeMode.MELT -> idle(state.copy(meltLatched = false), context)
        else -> output(state, clipFor(state.mode))
    }

    private fun thermalIdle(state: YukiRuntimeState, context: PetBrainContext): PetBrainResult<YukiRuntimeState> =
        if (thermalMode(state, context) == YukiRuntimeMode.MELT) melt(state) else idle(state, context)

    private fun thermalMode(state: YukiRuntimeState, context: PetBrainContext): YukiRuntimeMode {
        val temperature = context.environment.batteryTemperatureCelsius
        val remainsMelted = state.meltLatched && (temperature == null || temperature > YukiRuntimeDefinition.MELT_EXIT_CELSIUS)
        return if (remainsMelted || temperature != null && temperature >= YukiRuntimeDefinition.MELT_ENTER_CELSIUS) {
            YukiRuntimeMode.MELT
        } else {
            YukiRuntimeMode.IDLE
        }
    }

    private fun isHot(context: PetBrainContext): Boolean =
        (context.environment.batteryTemperatureCelsius ?: Float.NEGATIVE_INFINITY) >=
            YukiRuntimeDefinition.MELT_ENTER_CELSIUS

    private fun melt(state: YukiRuntimeState): PetBrainResult<YukiRuntimeState> = output(
        state.copy(mode = YukiRuntimeMode.MELT, elapsedSeconds = 0f, meltLatched = true),
        "melt",
        transform = meltTransform(),
    )

    private fun idle(
        state: YukiRuntimeState,
        context: PetBrainContext,
        position: PetVector = PetVector(context.position.x, context.environment.bounds.floor.toFloat()),
    ): PetBrainResult<YukiRuntimeState> = output(
        state.copy(
            mode = YukiRuntimeMode.IDLE,
            elapsedSeconds = 0f,
            durationSeconds = 2.4f + random.nextFloat() * 2.8f,
            meltLatched = false,
        ),
        "idle",
        position,
    )

    private fun output(
        state: YukiRuntimeState,
        clipId: String,
        position: PetVector? = null,
        transform: PetTransform = PetTransform(),
        hapticDurationMs: Long? = null,
    ): PetBrainResult<YukiRuntimeState> = PetBrainResult(
        state = state,
        intent = intentFor(state.mode),
        clipId = clipId,
        facing = state.facing,
        targetPosition = position,
        transform = transform,
        hapticDurationMs = hapticDurationMs,
    )

    private fun clipFor(mode: YukiRuntimeMode): String = when (mode) {
        YukiRuntimeMode.IDLE -> "idle"
        YukiRuntimeMode.WALK -> "walk"
        YukiRuntimeMode.BLINK -> "blink"
        YukiRuntimeMode.CURIOSITY,
        YukiRuntimeMode.DRAG,
        YukiRuntimeMode.RECOVER,
        -> "happy"
        YukiRuntimeMode.TOUCH -> "touch"
        YukiRuntimeMode.MELT -> "melt"
        YukiRuntimeMode.SLEEP -> "sleep"
        YukiRuntimeMode.AIRBORNE -> "jump"
    }

    private fun intentFor(mode: YukiRuntimeMode): PetIntent = when (mode) {
        YukiRuntimeMode.IDLE -> PetIntent.IDLE
        YukiRuntimeMode.WALK -> PetIntent.WALK
        YukiRuntimeMode.BLINK -> PetIntent.CUSTOM
        YukiRuntimeMode.CURIOSITY -> PetIntent.CURIOSITY
        YukiRuntimeMode.TOUCH -> PetIntent.TOUCH
        YukiRuntimeMode.MELT -> PetIntent.MELT
        YukiRuntimeMode.SLEEP -> PetIntent.SLEEP
        YukiRuntimeMode.DRAG -> PetIntent.DRAG
        YukiRuntimeMode.AIRBORNE -> PetIntent.AIRBORNE
        YukiRuntimeMode.RECOVER -> PetIntent.RECOVER
    }

    private fun dreamyTransform(elapsed: Float, amplitude: Float = 1f): PetTransform = PetTransform(
        offsetY = sin(elapsed * 2.1f) * 1.4f * amplitude,
        rotationDegrees = sin(elapsed * 1.4f) * 1.2f * amplitude,
    )

    private fun curiousTransform(elapsed: Float): PetTransform = PetTransform(
        scaleX = 1f + sin(elapsed * 5f) * 0.025f,
        scaleY = 1f + sin(elapsed * 5f) * 0.04f,
        offsetY = -abs(sin(elapsed * 4f)) * 2.5f,
    )

    private fun dragTransform(elapsed: Float): PetTransform = PetTransform(
        scaleX = 0.97f,
        scaleY = 1.04f,
        offsetY = -abs(sin(elapsed * 6f)) * 2.5f,
        rotationDegrees = sin(elapsed * 7f) * 7f,
    )

    private fun recoveryTransform(elapsed: Float): PetTransform = PetTransform(
        scaleX = 1f + abs(sin(elapsed * 7f)) * 0.04f,
        scaleY = 1f - abs(sin(elapsed * 7f)) * 0.04f,
        offsetY = -abs(sin(elapsed * 6f)) * 3f,
    )

    private fun meltTransform(): PetTransform = PetTransform(scaleX = 1.16f, scaleY = 0.84f)

    private companion object {
        const val MINIMUM_WALK_DISTANCE_PIXELS: Float = 24f
        const val MINIMUM_WALK_DURATION_SECONDS: Float = 0.8f
        const val CURIOSITY_SECONDS: Float = 1.6f
        const val TOUCH_SECONDS: Float = 1.0f
        const val RECOVERY_SECONDS: Float = 1.15f
    }
}
