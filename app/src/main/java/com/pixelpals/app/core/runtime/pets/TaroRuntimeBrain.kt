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

enum class TaroRuntimeMode {
    IDLE,
    WALK,
    TURN,
    TOUCH,
    HIDE,
    PEEK,
    FRONT_SOCIAL,
    SLEEP,
    CURIOSITY,
    DRAG,
    AIRBORNE,
}

data class TaroRuntimeState(
    val mode: TaroRuntimeMode = TaroRuntimeMode.IDLE,
    val elapsedSeconds: Float = 0f,
    val durationSeconds: Float = 3f,
    val facing: PetFacing = PetFacing.RIGHT,
    val pendingFacing: PetFacing = PetFacing.RIGHT,
    val walkStartX: Float = 0f,
    val walkTargetX: Float = 0f,
    val walkDurationSeconds: Float = 1f,
) : PetBrainState

class TaroRuntimeBrain(
    private val random: PetRandom,
) : PetBrain<TaroRuntimeState> {
    private val scheduler = PetActionScheduler(random)

    override fun createInitialState(context: PetBrainContext): TaroRuntimeState = TaroRuntimeState(
        facing = context.facing,
        pendingFacing = context.facing,
        walkStartX = context.position.x,
        walkTargetX = context.position.x,
    )

    override fun reduce(
        state: TaroRuntimeState,
        event: PetEvent,
        context: PetBrainContext,
    ): PetBrainResult<TaroRuntimeState> = when (event) {
        PetEvent.Tap -> output(state.copy(mode = TaroRuntimeMode.TOUCH, elapsedSeconds = 0f), "touch")
        PetEvent.HoldStarted -> output(
            state.copy(mode = TaroRuntimeMode.TOUCH, elapsedSeconds = 0f),
            "touch",
            hapticDurationMs = TaroRuntimeDefinition.value.interaction.holdHapticDurationMs,
        )
        PetEvent.HoldReleased -> output(
            state.copy(mode = TaroRuntimeMode.FRONT_SOCIAL, elapsedSeconds = 0f),
            "front_social",
        )
        is PetEvent.DragStarted,
        is PetEvent.DragMoved,
        -> output(
            state.copy(mode = TaroRuntimeMode.DRAG, elapsedSeconds = 0f),
            "hide",
            transform = PetTransform(scaleX = 0.96f, scaleY = 1.04f),
        )
        is PetEvent.Released,
        is PetEvent.Flung,
        -> output(state.copy(mode = TaroRuntimeMode.AIRBORNE, elapsedSeconds = 0f), "hide")
        PetEvent.RecoveryCompleted -> output(
            state.copy(mode = TaroRuntimeMode.PEEK, elapsedSeconds = 0f),
            "peek",
        )
        PetEvent.Cancelled -> idle(state, context)
        PetEvent.Resumed -> if (
            state.mode == TaroRuntimeMode.DRAG || state.mode == TaroRuntimeMode.AIRBORNE
        ) {
            idle(state, context)
        } else {
            output(state, clipFor(state.mode))
        }
        is PetEvent.Tick -> update(state, event.deltaSeconds, context)
        is PetEvent.StatusChanged,
        is PetEvent.EnvironmentChanged,
        PetEvent.Paused,
        PetEvent.Destroyed,
        -> output(state, clipFor(state.mode))
    }

    private fun update(
        state: TaroRuntimeState,
        deltaSeconds: Float,
        context: PetBrainContext,
    ): PetBrainResult<TaroRuntimeState> {
        val advanced = state.copy(elapsedSeconds = state.elapsedSeconds + deltaSeconds)
        return when (state.mode) {
            TaroRuntimeMode.IDLE -> if (advanced.elapsedSeconds >= state.durationSeconds) {
                beginAutonomous(advanced, context)
            } else {
                output(advanced, "idle_front")
            }
            TaroRuntimeMode.WALK -> updateWalk(advanced, context)
            TaroRuntimeMode.TURN -> if (context.playback.isFinished) {
                output(
                    advanced.copy(
                        mode = TaroRuntimeMode.WALK,
                        elapsedSeconds = 0f,
                        facing = advanced.pendingFacing,
                    ),
                    "walk",
                    position = PetVector(advanced.walkStartX, context.environment.bounds.floor.toFloat()),
                )
            } else {
                output(advanced, "turn")
            }
            TaroRuntimeMode.TOUCH -> transitionOneShot(advanced, context, TaroRuntimeMode.HIDE, "hide")
            TaroRuntimeMode.HIDE -> transitionOneShot(advanced, context, TaroRuntimeMode.PEEK, "peek")
            TaroRuntimeMode.PEEK -> transitionOneShot(
                advanced,
                context,
                TaroRuntimeMode.FRONT_SOCIAL,
                "front_social",
            )
            TaroRuntimeMode.FRONT_SOCIAL,
            TaroRuntimeMode.CURIOSITY,
            -> if (context.playback.isFinished) idle(advanced, context) else output(advanced, clipFor(state.mode))
            TaroRuntimeMode.SLEEP -> if (advanced.elapsedSeconds >= state.durationSeconds) {
                idle(advanced, context)
            } else {
                output(advanced, "sleep")
            }
            TaroRuntimeMode.DRAG -> output(advanced, "hide")
            TaroRuntimeMode.AIRBORNE -> output(advanced, "hide")
        }
    }

    private fun beginAutonomous(
        state: TaroRuntimeState,
        context: PetBrainContext,
    ): PetBrainResult<TaroRuntimeState> {
        val temperament = TaroRuntimeDefinition.value.temperament
        val energy = context.status.energy / 100f
        return when (
            scheduler.select(
                candidates = listOf(
                    PetActionCandidate(PetIntent.WALK, 0.55f, temperament.energy, 0.45f + energy),
                    PetActionCandidate(PetIntent.CURIOSITY, 0.20f, temperament.curiosity),
                    PetActionCandidate(PetIntent.SOCIAL, 0.15f, temperament.affection),
                    PetActionCandidate(PetIntent.SLEEP, 0.10f, 1f - temperament.energy * 0.5f, 1.35f - energy),
                ),
                recentActions = context.recentActions,
            )
        ) {
            PetIntent.WALK -> beginWalk(state, context)
            PetIntent.CURIOSITY -> output(
                state.copy(mode = TaroRuntimeMode.CURIOSITY, elapsedSeconds = 0f),
                "curiosity",
            )
            PetIntent.SOCIAL -> output(
                state.copy(mode = TaroRuntimeMode.FRONT_SOCIAL, elapsedSeconds = 0f),
                "front_social",
            )
            else -> output(
                state.copy(
                    mode = TaroRuntimeMode.SLEEP,
                    elapsedSeconds = 0f,
                    durationSeconds = 5f + random.nextFloat() * 4f,
                ),
                "sleep",
            )
        }
    }

    private fun beginWalk(
        state: TaroRuntimeState,
        context: PetBrainContext,
    ): PetBrainResult<TaroRuntimeState> {
        val bounds = context.environment.bounds
        val startX = context.position.x.coerceIn(bounds.left.toFloat(), bounds.right.toFloat())
        var targetX = bounds.left + random.nextFloat() * (bounds.right - bounds.left).coerceAtLeast(1)
        if (abs(targetX - startX) < MINIMUM_WALK_DISTANCE_PIXELS) {
            targetX = if (startX < (bounds.left + bounds.right) / 2f) {
                bounds.right.toFloat()
            } else {
                bounds.left.toFloat()
            }
        }
        val nextFacing = if (targetX >= startX) PetFacing.RIGHT else PetFacing.LEFT
        val walkDuration = (abs(targetX - startX) / TaroRuntimeDefinition.WALK_SPEED_PIXELS_PER_SECOND)
            .coerceAtLeast(MINIMUM_WALK_DURATION_SECONDS)
        val next = state.copy(
            mode = if (nextFacing == state.facing) TaroRuntimeMode.WALK else TaroRuntimeMode.TURN,
            elapsedSeconds = 0f,
            pendingFacing = nextFacing,
            walkStartX = startX,
            walkTargetX = targetX,
            walkDurationSeconds = walkDuration,
        )
        return output(next, if (next.mode == TaroRuntimeMode.TURN) "turn" else "walk")
    }

    private fun updateWalk(
        state: TaroRuntimeState,
        context: PetBrainContext,
    ): PetBrainResult<TaroRuntimeState> {
        val progress = (state.elapsedSeconds / state.walkDurationSeconds).coerceIn(0f, 1f)
        val x = state.walkStartX + (state.walkTargetX - state.walkStartX) * progress
        val position = PetVector(x, context.environment.bounds.floor.toFloat())
        return if (progress >= 1f) {
            idle(state, context, position)
        } else {
            output(state, "walk", position)
        }
    }

    private fun transitionOneShot(
        state: TaroRuntimeState,
        context: PetBrainContext,
        nextMode: TaroRuntimeMode,
        nextClip: String,
    ): PetBrainResult<TaroRuntimeState> = if (context.playback.isFinished) {
        output(state.copy(mode = nextMode, elapsedSeconds = 0f), nextClip)
    } else {
        output(state, clipFor(state.mode))
    }

    private fun idle(
        state: TaroRuntimeState,
        context: PetBrainContext,
        position: PetVector = PetVector(context.position.x, context.environment.bounds.floor.toFloat()),
    ): PetBrainResult<TaroRuntimeState> = output(
        state.copy(
            mode = TaroRuntimeMode.IDLE,
            elapsedSeconds = 0f,
            durationSeconds = 3f + random.nextFloat() * 4f,
        ),
        "idle_front",
        position,
    )

    private fun output(
        state: TaroRuntimeState,
        clipId: String,
        position: PetVector? = null,
        transform: PetTransform = PetTransform(),
        hapticDurationMs: Long? = null,
    ): PetBrainResult<TaroRuntimeState> = PetBrainResult(
        state = state,
        intent = intentFor(state.mode),
        clipId = clipId,
        facing = state.facing,
        targetPosition = position,
        transform = transform,
        hapticDurationMs = hapticDurationMs,
    )

    private fun clipFor(mode: TaroRuntimeMode): String = when (mode) {
        TaroRuntimeMode.IDLE -> "idle_front"
        TaroRuntimeMode.WALK -> "walk"
        TaroRuntimeMode.TURN -> "turn"
        TaroRuntimeMode.TOUCH -> "touch"
        TaroRuntimeMode.HIDE,
        TaroRuntimeMode.DRAG,
        TaroRuntimeMode.AIRBORNE,
        -> "hide"
        TaroRuntimeMode.PEEK -> "peek"
        TaroRuntimeMode.FRONT_SOCIAL -> "front_social"
        TaroRuntimeMode.SLEEP -> "sleep"
        TaroRuntimeMode.CURIOSITY -> "curiosity"
    }

    private fun intentFor(mode: TaroRuntimeMode): PetIntent = when (mode) {
        TaroRuntimeMode.IDLE -> PetIntent.IDLE
        TaroRuntimeMode.WALK -> PetIntent.WALK
        TaroRuntimeMode.TURN -> PetIntent.TURN
        TaroRuntimeMode.TOUCH -> PetIntent.TOUCH
        TaroRuntimeMode.HIDE -> PetIntent.HIDE
        TaroRuntimeMode.PEEK -> PetIntent.PEEK
        TaroRuntimeMode.FRONT_SOCIAL -> PetIntent.SOCIAL
        TaroRuntimeMode.SLEEP -> PetIntent.SLEEP
        TaroRuntimeMode.CURIOSITY -> PetIntent.CURIOSITY
        TaroRuntimeMode.DRAG -> PetIntent.DRAG
        TaroRuntimeMode.AIRBORNE -> PetIntent.AIRBORNE
    }

    private companion object {
        const val MINIMUM_WALK_DISTANCE_PIXELS: Float = 24f
        const val MINIMUM_WALK_DURATION_SECONDS: Float = 0.75f
    }
}
