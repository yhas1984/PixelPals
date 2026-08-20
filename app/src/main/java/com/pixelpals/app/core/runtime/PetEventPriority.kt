package com.pixelpals.app.core.runtime

/**
 * The single ordering used by runtimes and deterministic replays. Lower values
 * always win when more than one condition is eligible during the same tick.
 */
enum class PetEventPriority(val order: Int) {
    LIFECYCLE(0),
    ACTIVE_GESTURE(1),
    MANDATORY_ENVIRONMENT(2),
    PHYSICAL_RECOVERY(3),
    AUTONOMY(4),
}

object PetEventPriorities {
    fun of(event: PetEvent, interaction: PetInteractionState): PetEventPriority = when (event) {
        PetEvent.Paused,
        PetEvent.Resumed,
        PetEvent.Destroyed,
        PetEvent.Cancelled,
        -> PetEventPriority.LIFECYCLE

        PetEvent.Tap,
        PetEvent.HoldStarted,
        PetEvent.HoldReleased,
        is PetEvent.DragStarted,
        is PetEvent.DragMoved,
        is PetEvent.Released,
        is PetEvent.Flung,
        -> PetEventPriority.ACTIVE_GESTURE

        is PetEvent.EnvironmentChanged,
        is PetEvent.StatusChanged,
        -> PetEventPriority.MANDATORY_ENVIRONMENT

        PetEvent.RecoveryCompleted -> PetEventPriority.PHYSICAL_RECOVERY
        is PetEvent.Tick -> if (interaction == PetInteractionState.RECOVERING) {
            PetEventPriority.PHYSICAL_RECOVERY
        } else {
            PetEventPriority.AUTONOMY
        }
    }
}
