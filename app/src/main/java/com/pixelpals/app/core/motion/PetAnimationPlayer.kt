package com.pixelpals.app.core.motion

/** Immutable animation data decoded from a pet atlas manifest. */
data class PetAnimationClip(
    val id: String,
    val frames: List<Int>,
    val loop: Boolean,
    val frameDurationSeconds: Float,
) {
    init {
        require(id.isNotBlank()) { "Animation clip id must not be blank" }
        require(frames.isNotEmpty()) { "Animation clip $id must contain frames" }
        require(frameDurationSeconds > 0f && frameDurationSeconds.isFinite()) {
            "Animation clip $id must have a positive finite duration"
        }
    }

    val durationSeconds: Float
        get() = frames.size * frameDurationSeconds
}

/**
 * Deterministic frame player shared by V2 behaviors and debug review tools.
 * It deliberately has no Android dependency so timing can be unit tested.
 */
class PetAnimationPlayer(
    clips: Collection<PetAnimationClip> = emptyList(),
) {
    private val clipsById: Map<String, PetAnimationClip> = clips.associateBy { it.id }
    private var activeClip: PetAnimationClip? = null
    private var elapsedSeconds: Float = 0f

    val clipId: String?
        get() = activeClip?.id

    val elapsed: Float
        get() = elapsedSeconds

    val isFinished: Boolean
        get() = activeClip?.let { !it.loop && elapsedSeconds >= it.durationSeconds } ?: true

    fun setClip(id: String, restart: Boolean = true): Boolean {
        val next = clipsById[id] ?: return false
        if (!restart && activeClip?.id == id) return true
        activeClip = next
        elapsedSeconds = 0f
        return true
    }

    fun update(deltaSeconds: Float): Int {
        val clip = activeClip ?: return 0
        if (deltaSeconds.isFinite() && deltaSeconds > 0f) {
            elapsedSeconds += deltaSeconds
        }
        return frameFor(clip, elapsedSeconds)
    }

    fun currentFrame(): Int = activeClip?.let { frameFor(it, elapsedSeconds) } ?: 0

    fun reset(): Unit {
        elapsedSeconds = 0f
    }

    fun hasClip(id: String): Boolean = clipsById.containsKey(id)

    private fun frameFor(clip: PetAnimationClip, elapsed: Float): Int {
        val frameIndex = (elapsed / clip.frameDurationSeconds).toInt()
        val resolvedIndex = if (clip.loop) {
            frameIndex % clip.frames.size
        } else {
            frameIndex.coerceAtMost(clip.frames.lastIndex)
        }
        return clip.frames[resolvedIndex]
    }
}
