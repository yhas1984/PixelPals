package com.pixelpals.app.feature.overlay.behavior

import com.pixelpals.app.core.motion.PetRandom
import kotlin.math.abs

internal enum class TaroV2Mode {
    IDLE,
    WALK,
    TURN,
    HIDE,
    PEEK,
    FRONT_SOCIAL,
    TOUCH,
    SLEEP,
    CURIOSITY,
}

internal data class TaroV2Output(
    val mode: TaroV2Mode,
    val clipId: String,
    val facing: Float,
    val positionX: Float? = null,
)

/** Pure, deterministic state machine for Taro's V2 ground locomotion. */
internal class TaroV2Controller(
    private val random: PetRandom,
) {
    private var mode: TaroV2Mode = TaroV2Mode.IDLE
    private var modeTimer = 0f
    private var modeDuration = 4f
    private var facing = 1f
    private var walkStart = 0f
    private var walkTarget = 0f
    private var walkDuration = 1f
    private var pendingFacing = 1f

    fun reset(): TaroV2Output {
        mode = TaroV2Mode.IDLE
        modeTimer = 0f
        modeDuration = 3f
        return output("idle")
    }

    fun resetAfterDrop(): TaroV2Output {
        mode = TaroV2Mode.PEEK
        modeTimer = 0f
        return output("peek")
    }

    fun onTap(): TaroV2Output {
        mode = TaroV2Mode.TOUCH
        modeTimer = 0f
        return output("touch")
    }

    fun onDrag(): TaroV2Output {
        mode = TaroV2Mode.HIDE
        modeTimer = 0f
        return output("hide")
    }

    fun onFling(velocityX: Float): TaroV2Output {
        facing = if (velocityX >= 0f) 1f else -1f
        mode = TaroV2Mode.CURIOSITY
        modeTimer = 0f
        return output("curiosity")
    }

    fun update(
        dt: Float,
        currentX: Float,
        minX: Float,
        maxX: Float,
        animationFinished: Boolean,
    ): TaroV2Output {
        val step = dt.coerceAtLeast(0f)
        modeTimer += step
        return when (mode) {
            TaroV2Mode.IDLE -> updateIdle(currentX, minX, maxX)
            TaroV2Mode.WALK -> updateWalk(step)
            TaroV2Mode.TURN -> updateTurn(animationFinished)
            TaroV2Mode.TOUCH -> updateOneShot(animationFinished, TaroV2Mode.HIDE, "hide")
            TaroV2Mode.HIDE -> updateOneShot(animationFinished, TaroV2Mode.PEEK, "peek")
            TaroV2Mode.PEEK -> updateOneShot(animationFinished, TaroV2Mode.FRONT_SOCIAL, "front_social")
            TaroV2Mode.FRONT_SOCIAL -> updateOneShot(animationFinished, TaroV2Mode.IDLE, "idle")
            TaroV2Mode.CURIOSITY -> updateOneShot(animationFinished, TaroV2Mode.IDLE, "idle")
            TaroV2Mode.SLEEP -> updateSleep()
        }
    }

    private fun updateIdle(currentX: Float, minX: Float, maxX: Float): TaroV2Output {
        if (modeTimer < modeDuration) return output("idle")
        modeTimer = 0f
        return when (random.nextFloat()) {
            in 0f..0.55f -> beginWalk(currentX, minX, maxX)
            in 0.55f..0.75f -> beginOneShot(TaroV2Mode.CURIOSITY, "curiosity")
            in 0.75f..0.9f -> beginOneShot(TaroV2Mode.FRONT_SOCIAL, "front_social")
            else -> {
                mode = TaroV2Mode.SLEEP
                modeDuration = 5f + random.nextFloat() * 4f
                output("sleep")
            }
        }
    }

    private fun beginWalk(currentX: Float, minX: Float, maxX: Float): TaroV2Output {
        walkStart = currentX.coerceIn(minX, maxX)
        walkTarget = random.nextFloat() * (maxX - minX).coerceAtLeast(1f) + minX
        if (abs(walkTarget - walkStart) < 12f) walkTarget = if (walkStart < (minX + maxX) / 2f) maxX else minX
        pendingFacing = if (walkTarget >= walkStart) 1f else -1f
        walkDuration = (abs(walkTarget - walkStart) / 42f).coerceIn(3f, 9f)
        if (pendingFacing != facing) {
            mode = TaroV2Mode.TURN
            return output("turn")
        }
        mode = TaroV2Mode.WALK
        return output("walk", walkStart)
    }

    private fun updateWalk(dt: Float): TaroV2Output {
        val progress = (modeTimer / walkDuration).coerceIn(0f, 1f)
        val x = walkStart + (walkTarget - walkStart) * progress
        if (progress >= 1f) {
            mode = TaroV2Mode.IDLE
            modeTimer = 0f
            modeDuration = 3f + random.nextFloat() * 4f
            return output("idle", walkTarget)
        }
        return output("walk", x)
    }

    private fun updateTurn(animationFinished: Boolean): TaroV2Output {
        if (!animationFinished) return output("turn")
        facing = pendingFacing
        mode = TaroV2Mode.WALK
        modeTimer = 0f
        return output("walk", walkStart)
    }

    private fun updateOneShot(
        animationFinished: Boolean,
        nextMode: TaroV2Mode,
        nextClip: String,
    ): TaroV2Output {
        if (!animationFinished) return output(currentClip())
        mode = nextMode
        modeTimer = 0f
        if (nextMode == TaroV2Mode.IDLE) modeDuration = 3f + random.nextFloat() * 4f
        return output(nextClip)
    }

    private fun updateSleep(): TaroV2Output {
        if (modeTimer >= modeDuration) {
            mode = TaroV2Mode.IDLE
            modeTimer = 0f
            modeDuration = 3f + random.nextFloat() * 4f
            return output("idle")
        }
        return output("sleep")
    }

    private fun beginOneShot(nextMode: TaroV2Mode, clipId: String): TaroV2Output {
        mode = nextMode
        modeTimer = 0f
        return output(clipId)
    }

    private fun currentClip(): String = when (mode) {
        TaroV2Mode.TOUCH -> "touch"
        TaroV2Mode.HIDE -> "hide"
        TaroV2Mode.PEEK -> "peek"
        TaroV2Mode.FRONT_SOCIAL -> "front_social"
        TaroV2Mode.CURIOSITY -> "curiosity"
        else -> "idle"
    }

    private fun output(clipId: String, positionX: Float? = null): TaroV2Output =
        TaroV2Output(mode, clipId, facing, positionX)
}
