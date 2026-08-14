package com.pixelpals.app.core.motion

/** Clip data copied from the production atlas spec. */
internal data class LumiMotionClip(
    val id: String,
    val frames: List<Int>,
    val loop: Boolean,
    val frameDurationSeconds: Float,
) {
    val durationSeconds: Float
        get() = frames.size * frameDurationSeconds
}

internal data class LumiMotionSpec(
    val clips: Map<String, LumiMotionClip>,
) {
    fun clip(id: String): LumiMotionClip? = clips[id]
}

internal enum class LumiMode {
    IDLE,
    WALK,
    TURN,
    HOP_UP,
    HOP_DOWN,
    SOCIAL,
    POUNCE,
    SLEEP,
    MAGIC,
}

internal data class LumiPose(
    val x: Float,
    val y: Float,
    val frameIndex: Int,
    val mode: LumiMode,
    val facingRight: Boolean,
)

/**
 * Pure runtime state machine for Lumi. Animation timing comes from the atlas
 * JSON; this class only decides when a clip starts and where the pet moves.
 */
internal class LumiMotionController(
    private val random: PetRandom,
) {
    private var spec = LumiMotionSpec(emptyMap())
    private var activeClipId = "idle"
    private var stateTime = 0f
    private var decisionTime = 2.2f
    private var walkTime = 0f
    private var walkDuration = 0f
    private var directionX = 1f
    private var positionX = 0f
    private var positionY = 0f
    private var minX = 0f
    private var maxX = 1f
    private var minY = 0f
    private var maxY = 1f
    private var initialized = false
    private var nextInteractionIsMagic = false

    var mode: LumiMode = LumiMode.IDLE
        private set

    fun setSpec(nextSpec: LumiMotionSpec) {
        spec = nextSpec
        activeClipId = "idle"
        mode = LumiMode.IDLE
        stateTime = 0f
        decisionTime = 2.2f
    }

    fun updateViewport(
        width: Int,
        height: Int,
        drawSize: Float,
        topSystemInset: Int = 0,
        bottomSystemInset: Int = 0,
    ) {
        val halfSize = drawSize.coerceAtLeast(1f) * 0.5f
        minX = halfSize
        maxX = (width.toFloat() - halfSize).coerceAtLeast(minX)
        minY = topSystemInset.toFloat() + halfSize
        maxY = (height.toFloat() - bottomSystemInset - halfSize).coerceAtLeast(minY)
        positionX = positionX.coerceIn(minX, maxX)
        positionY = positionY.coerceIn(minY, maxY)
    }

    fun setPosition(centerX: Float, centerY: Float) {
        positionX = centerX.coerceIn(minX, maxX)
        positionY = centerY.coerceIn(minY, maxY)
        initialized = true
    }

    fun getPose(): LumiPose = LumiPose(
        x = positionX,
        y = positionY,
        frameIndex = frameForActiveClip(),
        mode = mode,
        facingRight = directionX >= 0f,
    )

    fun update(deltaSeconds: Float, shouldSleep: Boolean): LumiPose {
        if (spec.clip(activeClipId) == null) return getPose()
        if (!initialized) {
            setPosition((minX + maxX) * 0.5f, maxY)
        }

        val dt = deltaSeconds.coerceIn(0f, MAX_STEP_SECONDS)
        when (mode) {
            LumiMode.IDLE -> updateIdle(dt, shouldSleep)
            LumiMode.WALK -> updateWalk(dt)
            LumiMode.SLEEP -> updateSleep(dt, shouldSleep)
            else -> updateOneShot(dt)
        }
        return getPose()
    }

    /** Starts the agreed alternating tap interaction and returns its clip id. */
    fun startInteraction(): String {
        val clipId = if (nextInteractionIsMagic) "magic" else "front_social"
        nextInteractionIsMagic = !nextInteractionIsMagic
        beginClip(clipId, if (clipId == "magic") LumiMode.MAGIC else LumiMode.SOCIAL)
        return clipId
    }

    fun reset() {
        mode = LumiMode.IDLE
        activeClipId = "idle"
        stateTime = 0f
        decisionTime = 1.2f
        walkTime = 0f
        positionX = positionX.coerceIn(minX, maxX)
        positionY = positionY.coerceIn(minY, maxY)
    }

    private fun updateIdle(dt: Float, shouldSleep: Boolean) {
        stateTime += dt
        if (shouldSleep && stateTime >= decisionTime * 0.55f) {
            beginClip("sleep", LumiMode.SLEEP)
            return
        }
        if (stateTime < decisionTime) return

        when (random.nextFloat()) {
            in 0f..0.10f -> beginClip("hop_up", LumiMode.HOP_UP)
            in 0.10f..0.20f -> beginClip("pounce", LumiMode.POUNCE)
            in 0.20f..0.34f -> beginClip("turn", LumiMode.TURN)
            in 0.34f..0.78f -> startWalk()
            else -> {
                stateTime = 0f
                decisionTime = randomRange(1.4f, 3.8f)
            }
        }
    }

    private fun updateWalk(dt: Float) {
        stateTime += dt
        walkTime += dt
        positionX += directionX * walkSpeed() * dt
        if (positionX <= minX || positionX >= maxX) {
            positionX = positionX.coerceIn(minX, maxX)
            beginClip("turn", LumiMode.TURN)
            return
        }
        if (walkTime >= walkDuration) {
            beginClip("idle", LumiMode.IDLE)
            decisionTime = randomRange(1.4f, 3.8f)
        }
    }

    private fun updateSleep(dt: Float, shouldSleep: Boolean) {
        stateTime += dt
        if (!shouldSleep) {
            beginClip("idle", LumiMode.IDLE)
        }
    }

    private fun updateOneShot(dt: Float) {
        val clip = spec.clip(activeClipId) ?: run {
            beginClip("idle", LumiMode.IDLE)
            return
        }
        stateTime += dt
        if (stateTime < clip.durationSeconds) return

        when (mode) {
            LumiMode.HOP_UP -> beginClip("hop_down", LumiMode.HOP_DOWN)
            LumiMode.TURN -> {
                directionX *= -1f
                beginClip("idle", LumiMode.IDLE)
                decisionTime = randomRange(1.2f, 2.8f)
            }
            LumiMode.HOP_DOWN,
            LumiMode.SOCIAL,
            LumiMode.POUNCE,
            LumiMode.MAGIC -> {
                beginClip("idle", LumiMode.IDLE)
                decisionTime = randomRange(1.2f, 3.2f)
            }
            else -> Unit
        }
    }

    private fun startWalk() {
        directionX = if (random.nextBoolean()) 1f else -1f
        walkTime = 0f
        walkDuration = randomRange(2.2f, 5.2f)
        stateTime = 0f
        activeClipId = "walk"
        mode = LumiMode.WALK
    }

    private fun beginClip(clipId: String, nextMode: LumiMode) {
        if (spec.clip(clipId) == null) {
            activeClipId = "idle"
            mode = LumiMode.IDLE
            stateTime = 0f
            return
        }
        activeClipId = clipId
        mode = nextMode
        stateTime = 0f
    }

    private fun frameForActiveClip(): Int {
        val clip = spec.clip(activeClipId) ?: return 0
        if (clip.frames.isEmpty()) return 0
        val frame = (stateTime / clip.frameDurationSeconds).toInt()
        return clip.frames[if (clip.loop) frame % clip.frames.size else frame.coerceAtMost(clip.frames.lastIndex)]
    }

    private fun walkSpeed(): Float = ((maxX - minX) * 0.18f).coerceIn(42f, 115f)

    private fun randomRange(min: Float, max: Float): Float = min + random.nextFloat() * (max - min)

    companion object {
        private const val MAX_STEP_SECONDS = 1f / 20f
    }
}
