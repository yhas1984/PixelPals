package com.pixelpals.app.core.runtime

import com.pixelpals.app.core.care.PetCondition
import com.pixelpals.app.core.motion.PetBounds
import com.pixelpals.app.status.PetMood

enum class PetIntent {
    IDLE,
    WALK,
    TURN,
    CURIOSITY,
    SOCIAL,
    TOUCH,
    HOLD,
    DRAG,
    AIRBORNE,
    RECOVER,
    SLEEP,
    HIDE,
    PEEK,
    MELT,
    CUSTOM,
}

enum class PetSurface {
    FLOOR,
    LEFT_WALL,
    CEILING,
    RIGHT_WALL,
    SILK,
    FREE,
}

enum class PetFacing(val scaleX: Float) {
    LEFT(-1f),
    RIGHT(1f),
}

enum class PetInteractionState {
    NONE,
    TOUCH_PENDING,
    HOLDING,
    DRAGGING,
    RECOVERING,
}

enum class PetLifecycleState {
    ACTIVE,
    PAUSED,
    DESTROYED,
}

enum class PetCompletedInteraction {
    TAP,
    HOLD,
}

enum class PetBondStage {
    NEW,
    CLOSE,
    TRUSTED,
    SOULMATE;

    companion object {
        fun fromBond(bond: Int): PetBondStage = when (bond.coerceIn(0, 100)) {
            in 0..11 -> NEW
            in 12..34 -> CLOSE
            in 35..69 -> TRUSTED
            else -> SOULMATE
        }
    }
}

data class PetVector(
    val x: Float,
    val y: Float,
) {
    init {
        require(x.isFinite() && y.isFinite()) { "Pet vector values must be finite" }
    }
}

data class PetTransform(
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val rotationDegrees: Float = 0f,
    val alpha: Float = 1f,
) {
    init {
        require(scaleX.isFinite() && scaleY.isFinite()) { "Pet transform scale must be finite" }
        require(offsetX.isFinite() && offsetY.isFinite()) { "Pet transform offset must be finite" }
        require(rotationDegrees.isFinite()) { "Pet transform rotation must be finite" }
        require(alpha in 0f..1f) { "Pet transform alpha must be between zero and one" }
    }
}

data class PetRuntimeStatus(
    val mood: PetMood,
    val health: Int,
    val energy: Int,
    val hunger: Int,
    val hygiene: Int,
    val bond: Int,
    val condition: PetCondition = PetCondition.HEALTHY,
) {
    init {
        require(health in 0..100) { "Health must be between zero and one hundred" }
        require(energy in 0..100) { "Energy must be between zero and one hundred" }
        require(hunger in 0..100) { "Hunger must be between zero and one hundred" }
        require(hygiene in 0..100) { "Hygiene must be between zero and one hundred" }
        require(bond in 0..100) { "Bond must be between zero and one hundred" }
    }

    val bondStage: PetBondStage = PetBondStage.fromBond(bond)
}

data class PetEnvironment(
    val bounds: PetBounds,
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val batteryTemperatureCelsius: Float? = null,
    val isKeyboardVisible: Boolean = false,
    val isAirplaneModeEnabled: Boolean = false,
) {
    init {
        require(batteryPercent in 0..100) { "Battery percent must be between zero and one hundred" }
        require(batteryTemperatureCelsius == null || batteryTemperatureCelsius.isFinite()) {
            "Battery temperature must be finite when present"
        }
    }
}

sealed interface PetEvent {
    data class Tick(val deltaSeconds: Float) : PetEvent {
        init {
            require(deltaSeconds >= 0f && deltaSeconds.isFinite()) {
                "Tick delta must be finite and non-negative"
            }
        }
    }

    data object Tap : PetEvent
    data object HoldStarted : PetEvent
    data object HoldReleased : PetEvent
    data class DragStarted(val pointer: PetVector, val grabOffset: PetVector) : PetEvent
    data class DragMoved(val pointer: PetVector) : PetEvent
    data class Released(val velocity: PetVector = PetVector(0f, 0f)) : PetEvent
    data class Flung(val velocity: PetVector) : PetEvent
    data object RecoveryCompleted : PetEvent
    data object Cancelled : PetEvent
    data class StatusChanged(val status: PetRuntimeStatus) : PetEvent
    data class EnvironmentChanged(val environment: PetEnvironment) : PetEvent
    data object Paused : PetEvent
    data object Resumed : PetEvent
    data object Destroyed : PetEvent
}

sealed interface PetEffectCommand {
    val id: String

    data class Show(val effect: PetEffect) : PetEffectCommand {
        override val id: String = effect.id
    }

    data class Update(val effect: PetEffect) : PetEffectCommand {
        override val id: String = effect.id
    }

    data class Remove(override val id: String) : PetEffectCommand
    data object ClearAll : PetEffectCommand {
        override val id: String = "*"
    }
}

data class PetEffect(
    val id: String,
    val type: String,
    val anchor: PetVector,
    val target: PetVector? = null,
    val alpha: Float = 1f,
    val lifetimeSeconds: Float? = null,
) {
    init {
        require(id.isNotBlank()) { "Pet effect id cannot be blank" }
        require(type.isNotBlank()) { "Pet effect type cannot be blank" }
        require(alpha in 0f..1f) { "Pet effect alpha must be between zero and one" }
        require(lifetimeSeconds == null || lifetimeSeconds >= 0f) {
            "Pet effect lifetime must be non-negative when present"
        }
    }
}

interface PetBrainState

data class PetBrainContext(
    val status: PetRuntimeStatus,
    val environment: PetEnvironment,
    val playback: PetPlaybackState = PetPlaybackState(),
    val position: PetVector = PetVector(0f, 0f),
    val surface: PetSurface = PetSurface.FLOOR,
    val facing: PetFacing = PetFacing.RIGHT,
    val interaction: PetInteractionState = PetInteractionState.NONE,
    val recentActions: List<PetIntent> = emptyList(),
)

data class PetPlaybackState(
    val clipId: String? = null,
    val frame: Int = 0,
    val elapsedSeconds: Float = 0f,
    val isFinished: Boolean = true,
)

data class PetBrainResult<S : PetBrainState>(
    val state: S,
    val intent: PetIntent,
    val clipId: String,
    val facing: PetFacing? = null,
    val targetPosition: PetVector? = null,
    val transform: PetTransform = PetTransform(),
    val effects: List<PetEffectCommand> = emptyList(),
    val bubble: String? = null,
    val hapticDurationMs: Long? = null,
) {
    init {
        require(clipId.isNotBlank()) { "Pet brain result clip id cannot be blank" }
        require(hapticDurationMs == null || hapticDurationMs >= 0L) {
            "Haptic duration must be non-negative when present"
        }
    }
}

interface PetBrain<S : PetBrainState> {
    fun createInitialState(context: PetBrainContext): S
    fun reduce(state: S, event: PetEvent, context: PetBrainContext): PetBrainResult<S>
}

data class PetRuntimeState<S : PetBrainState>(
    val brainState: S,
    val intent: PetIntent,
    val surface: PetSurface,
    val position: PetVector,
    val velocity: PetVector,
    val facing: PetFacing,
    val clipId: String,
    val frame: Int,
    val elapsedSeconds: Float,
    val interaction: PetInteractionState,
    val lifecycle: PetLifecycleState,
    val recentActions: List<PetIntent> = emptyList(),
)

data class PetRuntimeOutput(
    val intent: PetIntent,
    val surface: PetSurface,
    val position: PetVector,
    val velocity: PetVector,
    val facing: PetFacing,
    val clipId: String,
    val frame: Int,
    val transform: PetTransform,
    val effects: List<PetEffectCommand>,
    val bubble: String?,
    val hapticDurationMs: Long?,
    val completedInteraction: PetCompletedInteraction?,
)
