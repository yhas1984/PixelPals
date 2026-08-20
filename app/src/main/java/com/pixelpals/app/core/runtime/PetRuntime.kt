package com.pixelpals.app.core.runtime

import com.pixelpals.app.core.motion.PetAnimationClip
import com.pixelpals.app.core.motion.PetAnimationPlayer
import com.pixelpals.app.core.motion.PetPhysics
import com.pixelpals.app.core.motion.PhysicsBody
import com.pixelpals.app.core.motion.PhysicsEvent

class PetRuntime<S : PetBrainState>(
    private val definition: PetDefinition,
    private val brain: PetBrain<S>,
    clips: Collection<PetAnimationClip>,
    initialStatus: PetRuntimeStatus,
    initialEnvironment: PetEnvironment,
    private val initialPosition: PetVector,
    private val initialFacing: PetFacing = PetFacing.RIGHT,
    private val initialSurface: PetSurface = definition.locomotion.primarySurface,
    private val simulationSpeedMultiplier: Float = 1f,
) {
    init {
        require(simulationSpeedMultiplier > 0f && simulationSpeedMultiplier.isFinite()) {
            "Simulation speed multiplier must be positive and finite"
        }
    }

    private val player: PetAnimationPlayer = PetAnimationPlayer(clips)
    private var status: PetRuntimeStatus = initialStatus
    private var environment: PetEnvironment = initialEnvironment
    private var lastTransform: PetTransform = PetTransform()
    private var pendingEffects: List<PetEffectCommand> = emptyList()
    private var pendingBubble: String? = null
    private var pendingHapticDurationMs: Long? = null
    private var pendingCompletedInteraction: PetCompletedInteraction? = null
    private var grabOffset: PetVector = PetVector(0f, 0f)
    private var state: PetRuntimeState<S>

    init {
        validateClips(clips)
        val context: PetBrainContext = createInitialContext()
        val initialBrainState: S = brain.createInitialState(context)
        val initialResult: PetBrainResult<S> = brain.reduce(initialBrainState, PetEvent.Resumed, context)
        require(player.setClip(initialResult.clipId)) { "Initial clip ${initialResult.clipId} is missing" }
        state = PetRuntimeState(
            brainState = initialResult.state,
            intent = initialResult.intent,
            surface = initialSurface,
            position = initialPosition,
            velocity = PetVector(0f, 0f),
            facing = initialResult.facing ?: initialFacing,
            clipId = initialResult.clipId,
            frame = player.currentFrame(),
            elapsedSeconds = 0f,
            interaction = PetInteractionState.NONE,
            lifecycle = PetLifecycleState.ACTIVE,
        )
        applyTransientOutput(initialResult)
    }

    fun dispatch(event: PetEvent): PetRuntimeOutput {
        clearTransientOutput()
        updateContext(event)
        when (event) {
            PetEvent.Destroyed -> destroy()
            PetEvent.Paused -> pause()
            PetEvent.Resumed -> resume(event)
            else -> dispatchWhileActive(event)
        }
        return output()
    }

    fun snapshot(): PetRuntimeState<S> = state

    private fun validateClips(clips: Collection<PetAnimationClip>) {
        val available: Set<String> = clips.map(PetAnimationClip::id).toSet()
        val missing: Set<String> = definition.requiredClips - available
        require(missing.isEmpty()) { "Pet ${definition.petId} is missing required clips: ${missing.sorted()}" }
    }

    private fun updateContext(event: PetEvent) {
        when (event) {
            is PetEvent.StatusChanged -> status = event.status
            is PetEvent.EnvironmentChanged -> environment = event.environment
            else -> Unit
        }
    }

    private fun dispatchWhileActive(event: PetEvent) {
        if (state.lifecycle != PetLifecycleState.ACTIVE) return
        when (event) {
            is PetEvent.DragStarted -> startDrag(event)
            is PetEvent.DragMoved -> moveDrag(event)
            is PetEvent.Flung -> finishDrag(event.velocity, event)
            is PetEvent.Released -> finishDrag(event.velocity, event)
            is PetEvent.Tick -> updateTick(event)
            PetEvent.Cancelled -> cancel(event)
            PetEvent.HoldStarted -> startHold(event)
            PetEvent.HoldReleased -> finishHold(event)
            PetEvent.Tap -> completeTap(event)
            is PetEvent.StatusChanged,
            is PetEvent.EnvironmentChanged,
            -> {
                if (state.interaction != PetInteractionState.DRAGGING &&
                    state.interaction != PetInteractionState.HOLDING
                ) {
                    reduce(event, state.interaction)
                }
            }
            PetEvent.RecoveryCompleted -> reduce(event, state.interaction)

            PetEvent.Paused,
            PetEvent.Resumed,
            PetEvent.Destroyed,
            -> Unit
        }
    }

    private fun startDrag(event: PetEvent.DragStarted) {
        interruptRecovery()
        grabOffset = event.grabOffset
        val position: PetVector = positionForPointer(event.pointer)
        state = state.copy(
            position = constrain(position),
            velocity = PetVector(0f, 0f),
            interaction = PetInteractionState.DRAGGING,
        )
        reduce(event, PetInteractionState.DRAGGING)
    }

    private fun moveDrag(event: PetEvent.DragMoved) {
        if (state.interaction != PetInteractionState.DRAGGING) return
        state = state.copy(position = constrain(positionForPointer(event.pointer)))
        reduce(event, PetInteractionState.DRAGGING)
    }

    private fun finishDrag(velocity: PetVector, event: PetEvent) {
        if (state.interaction != PetInteractionState.DRAGGING) return
        val maximumSpeed: Float = definition.locomotion.maximumFlingSpeedPixelsPerSecond
        val limited: PetVector = PetVector(
            velocity.x.coerceIn(-maximumSpeed, maximumSpeed),
            velocity.y.coerceIn(-maximumSpeed, maximumSpeed),
        )
        state = state.copy(velocity = limited, interaction = PetInteractionState.RECOVERING)
        reduce(event, PetInteractionState.RECOVERING)
    }

    private fun updateTick(event: PetEvent.Tick) {
        val scaledDeltaSeconds: Float = event.deltaSeconds * simulationSpeedMultiplier
        val frame: Int = player.update(scaledDeltaSeconds)
        state = state.copy(frame = frame, elapsedSeconds = state.elapsedSeconds + scaledDeltaSeconds)
        if (state.interaction == PetInteractionState.RECOVERING) {
            updateRecovery(scaledDeltaSeconds)
            return
        }
        if (state.interaction == PetInteractionState.DRAGGING || state.interaction == PetInteractionState.HOLDING) {
            return
        }
        reduce(PetEvent.Tick(scaledDeltaSeconds), state.interaction)
    }

    private fun updateRecovery(deltaSeconds: Float) {
        val result = PetPhysics.step(
            body = PhysicsBody(
                x = state.position.x,
                y = state.position.y,
                velocityX = state.velocity.x,
                velocityY = state.velocity.y,
            ),
            dt = deltaSeconds,
            bounds = environment.bounds,
            profile = definition.locomotion.physicsProfile,
        )
        state = state.copy(
            position = PetVector(result.body.x, result.body.y),
            velocity = PetVector(result.body.velocityX, result.body.velocityY),
        )
        if (result.event != PhysicsEvent.SETTLED) return
        val attachment: PetSurfaceAttachment = PetSurfaceResolver.attach(
            position = state.position,
            bounds = environment.bounds,
            profile = definition.locomotion.physicsProfile,
        )
        state = state.copy(
            position = attachment.position,
            velocity = PetVector(0f, 0f),
            surface = attachment.surface,
            interaction = PetInteractionState.NONE,
        )
        reduce(PetEvent.RecoveryCompleted, PetInteractionState.NONE)
    }

    private fun cancel(event: PetEvent) {
        state = state.copy(velocity = PetVector(0f, 0f), interaction = PetInteractionState.NONE)
        pendingEffects = listOf(PetEffectCommand.ClearAll)
        reduce(event, PetInteractionState.NONE)
    }

    private fun startHold(event: PetEvent) {
        interruptRecovery()
        reduce(event, PetInteractionState.HOLDING)
    }

    private fun finishHold(event: PetEvent) {
        if (state.interaction != PetInteractionState.HOLDING) return
        reduce(event, PetInteractionState.NONE)
        pendingCompletedInteraction = PetCompletedInteraction.HOLD
    }

    private fun completeTap(event: PetEvent) {
        if (state.interaction == PetInteractionState.DRAGGING || state.interaction == PetInteractionState.HOLDING) return
        interruptRecovery()
        reduce(event, PetInteractionState.NONE)
        pendingCompletedInteraction = PetCompletedInteraction.TAP
    }

    private fun interruptRecovery() {
        if (state.interaction != PetInteractionState.RECOVERING) return
        state = state.copy(velocity = PetVector(0f, 0f), interaction = PetInteractionState.NONE)
    }

    private fun pause() {
        state = state.copy(
            lifecycle = PetLifecycleState.PAUSED,
            interaction = PetInteractionState.NONE,
            velocity = PetVector(0f, 0f),
        )
        pendingEffects = listOf(PetEffectCommand.ClearAll)
    }

    private fun resume(event: PetEvent) {
        if (state.lifecycle == PetLifecycleState.DESTROYED) return
        state = state.copy(lifecycle = PetLifecycleState.ACTIVE)
        reduce(event, state.interaction)
    }

    private fun destroy() {
        state = state.copy(
            lifecycle = PetLifecycleState.DESTROYED,
            interaction = PetInteractionState.NONE,
            velocity = PetVector(0f, 0f),
        )
        pendingEffects = listOf(PetEffectCommand.ClearAll)
    }

    private fun reduce(event: PetEvent, interaction: PetInteractionState) {
        if (state.lifecycle == PetLifecycleState.DESTROYED) return
        val result: PetBrainResult<S> = brain.reduce(state.brainState, event, createContext())
        val nextFacing: PetFacing = result.facing ?: state.facing
        val nextPosition: PetVector = result.targetPosition?.let(::constrain) ?: state.position
        if (state.clipId != result.clipId) {
            require(player.setClip(result.clipId)) { "Brain selected missing clip ${result.clipId}" }
        }
        val recentActions: List<PetIntent> = updateRecentActions(result.intent)
        state = state.copy(
            brainState = result.state,
            intent = result.intent,
            position = nextPosition,
            facing = nextFacing,
            clipId = result.clipId,
            frame = player.currentFrame(),
            interaction = interaction,
            recentActions = recentActions,
        )
        applyTransientOutput(result)
    }

    private fun updateRecentActions(nextIntent: PetIntent): List<PetIntent> {
        if (state.intent == nextIntent) return state.recentActions
        return (state.recentActions + nextIntent).takeLast(RECENT_ACTION_LIMIT)
    }

    private fun positionForPointer(pointer: PetVector): PetVector = PetVector(
        x = pointer.x - grabOffset.x,
        y = pointer.y - grabOffset.y,
    )

    private fun constrain(position: PetVector): PetVector = PetVector(
        x = position.x.coerceIn(environment.bounds.left.toFloat(), environment.bounds.right.toFloat()),
        y = position.y.coerceIn(environment.bounds.top.toFloat(), environment.bounds.floor.toFloat()),
    )

    private fun createInitialContext(): PetBrainContext = PetBrainContext(
        status = status,
        environment = environment,
        position = initialPosition,
        surface = initialSurface,
        facing = initialFacing,
    )

    private fun createContext(): PetBrainContext = PetBrainContext(
        status = status,
        environment = environment,
        playback = PetPlaybackState(
            clipId = player.clipId,
            frame = player.currentFrame(),
            elapsedSeconds = player.elapsed,
            isFinished = player.isFinished,
        ),
        position = state.position,
        surface = state.surface,
        facing = state.facing,
        interaction = state.interaction,
        recentActions = state.recentActions,
    )

    private fun applyTransientOutput(result: PetBrainResult<S>) {
        lastTransform = result.transform
        pendingEffects = pendingEffects + result.effects
        pendingBubble = result.bubble
        pendingHapticDurationMs = result.hapticDurationMs
    }

    private fun clearTransientOutput() {
        pendingEffects = emptyList()
        pendingBubble = null
        pendingHapticDurationMs = null
        pendingCompletedInteraction = null
    }

    private fun output(): PetRuntimeOutput = PetRuntimeOutput(
        intent = state.intent,
        surface = state.surface,
        position = state.position,
        velocity = state.velocity,
        facing = state.facing,
        clipId = state.clipId,
        frame = state.frame,
        transform = lastTransform,
        effects = pendingEffects,
        bubble = pendingBubble,
        hapticDurationMs = pendingHapticDurationMs,
        completedInteraction = pendingCompletedInteraction,
    )

    private companion object {
        const val RECENT_ACTION_LIMIT: Int = 4
    }
}
