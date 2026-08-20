package com.pixelpals.app.core.runtime

data class PetReplayStep(
    val atMillis: Long,
    val event: PetEvent,
) {
    init {
        require(atMillis >= 0L) { "Replay timestamps cannot be negative" }
    }
}

data class PetReplayScript(
    val id: String,
    val seed: Int,
    val steps: List<PetReplayStep>,
) {
    init {
        require(id.isNotBlank()) { "Replay id cannot be blank" }
        require(steps.isNotEmpty()) { "Replay must contain at least one step" }
        require(steps.zipWithNext().all { (current, next) -> current.atMillis <= next.atMillis }) {
            "Replay steps must be ordered by timestamp"
        }
    }
}

data class PetReplayFrame(
    val atMillis: Long,
    val event: PetEvent,
    val output: PetRuntimeOutput,
)

class PetReplayRunner<S : PetBrainState>(
    private val runtime: PetRuntime<S>,
) {
    fun run(script: PetReplayScript): List<PetReplayFrame> = script.steps.map { step ->
        PetReplayFrame(
            atMillis = step.atMillis,
            event = step.event,
            output = runtime.dispatch(step.event),
        )
    }
}
