package com.pixelpals.app.feature.home

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

enum class CompanionActivity { GREET, APPROACH_TOY, PLAY, APPROACH_BED, REST, WAKE, EXPLORE, OBSERVE }

/** Distance-driven gait with anticipation, braking and planted direction changes. */
class CompanionMotion(
    private val selector: CompanionIntentSelector = CompanionIntentSelector(),
    val turnDurationSeconds: Float = TURN_SECONDS,
    private val turnCommitSeconds: Float = turnDurationSeconds / 2f,
    private val gingerPostures: Boolean = false,
    private val gingerRestArtwork: Boolean = false,
) {
    var context: CompanionIntentContext = CompanionIntentContext()
    private var destinationX: Float = 500f
    private var destinationY: Float = 650f
    private var pauseDuration: Float = 3f
    private var pendingIntent: CompanionIntent? = null
    var x: Float = 500f
        private set
    var y: Float = 650f
        private set
    var activity: CompanionActivity = CompanionActivity.GREET
        private set
    var elapsed: Float = 0f
        private set
    internal var restElapsedBeforeWake: Float = Float.POSITIVE_INFINITY
        private set
    var isFacingLeft: Boolean = false
        private set
    var distanceTravelled: Float = 0f
        private set
    var speed: Float = 0f
        private set
    var turnElapsed: Float = 0f
        private set
    var turnFromFacingLeft: Boolean = false
        private set
    var isTurning: Boolean = false
        private set
    internal var gingerPostureTransition: com.pixelpals.app.core.motion.GingerPostureMotion.Transition? = null
        private set
    internal var gingerPostureEndpoint: Int? = null
        private set
    internal val gingerPostureElapsed: Float
        get() = if (gingerPostureTransition == com.pixelpals.app.core.motion.GingerPostureMotion.Transition.STAND_UP && activity == CompanionActivity.WAKE)
            elapsed - (WAKE_SECONDS - com.pixelpals.app.core.motion.GingerPostureMotion.DURATION_SECONDS)
        else elapsed
    internal val gingerIsSeated: Boolean get() = gingerSeated
    internal val gingerPosturesEnabled: Boolean get() = gingerPostures
    internal var gingerRestStartsSeated: Boolean = true
        private set
    private var gingerSeated: Boolean = true
    private var targetFacesLeft: Boolean = false
    private var headingX: Float = 0f
    private var headingY: Float = 0f
    val isApproaching: Boolean get() = activity == CompanionActivity.APPROACH_TOY || activity == CompanionActivity.APPROACH_BED || activity == CompanionActivity.EXPLORE
    val isPreparing: Boolean get() = isApproaching && elapsed < ANTICIPATION_SECONDS
    fun advance(delta: Float, toyX: Float, bedX: Float, tempo: Float, bond: Int, toyY: Float = 650f, bedY: Float = 650f, toyFacesLeft: Boolean? = null): Unit {
        val dt: Float = delta.coerceIn(0f, .1f)
        elapsed += dt
        gingerPostureEndpoint = null
        val hadGingerPosture: Boolean = gingerPostureTransition != null
        if (advanceGingerPosture()) return
        if (hadGingerPosture) return
        if (context.hasBed && wakeForMovedBed(bedX, bedY)) return
        when (activity) {
            CompanionActivity.GREET -> if (elapsed >= 4f - bond.coerceIn(0, 100) * .02f) chooseNext()
            CompanionActivity.APPROACH_TOY -> if (!context.hasToy) chooseNext() else if (approach(toyX, toyY, dt, tempo)) transition(CompanionActivity.PLAY)
            CompanionActivity.PLAY -> {
                val needsTurn: Boolean = toyFacesLeft != null && toyFacesLeft != isFacingLeft
                if (gingerPostures && gingerSeated && needsTurn) {
                    gingerPostureTransition = com.pixelpals.app.core.motion.GingerPostureMotion.Transition.STAND_UP
                    elapsed = 0f
                    return
                }
                val wasTurning: Boolean = isTurning
                if (toyFacesLeft != null) updateTurn(if (toyFacesLeft) -100f else 100f, dt, tempo)
                if (wasTurning && !isTurning) elapsed = 0f
                if (gingerPostures && !gingerSeated && !isTurning) {
                    gingerPostureTransition = com.pixelpals.app.core.motion.GingerPostureMotion.Transition.SIT_DOWN
                    elapsed = 0f
                    return
                }
                if ((!context.hasToy || elapsed >= 5f) && !isTurning) chooseNext()
            }
            CompanionActivity.APPROACH_BED -> if (!context.hasBed || approach(bedX, bedY, dt, tempo)) transition(CompanionActivity.REST)
            CompanionActivity.REST -> if (elapsed >= 8f / tempo.coerceAtLeast(.45f) && context.energy > 25 && !context.isUnwell) {
                pendingIntent = selector.choose(context)
                if (pendingIntent != CompanionIntent.REST) transition(CompanionActivity.WAKE) else elapsed = 2f
            }
            CompanionActivity.WAKE -> if (elapsed >= WAKE_SECONDS) beginIntent(if (context.energy <= 25 || context.isUnwell) CompanionIntent.REST else pendingIntent ?: CompanionIntent.OBSERVE)
            CompanionActivity.EXPLORE -> if (approach(destinationX, destinationY, dt, tempo)) transition(CompanionActivity.OBSERVE)
            CompanionActivity.OBSERVE -> if (elapsed >= pauseDuration) chooseNext()
        }
    }
    fun settleWithoutMovement(shouldRest: Boolean, reduced: Boolean = false): Unit {
        val target: CompanionActivity = if (shouldRest) CompanionActivity.REST else CompanionActivity.OBSERVE
        if (activity != target) transition(target)
        if (reduced && gingerPostures) {
            gingerPostureTransition = null
            gingerSeated = true
            gingerPostureEndpoint = 0
            speed = 0f
        }
    }
    /** A completed return trip hands its heading back to the autonomous actor. */
    internal fun finishExcursion(facingLeft: Boolean): Unit {
        if (gingerPostures) gingerSeated = false
        transition(CompanionActivity.OBSERVE)
        isFacingLeft = facingLeft
        turnFromFacingLeft = facingLeft
        targetFacesLeft = facingLeft
    }
    private var scheduledRest: Boolean = false
    internal fun retainScheduledRestPose(seconds: Float) {
        if (activity == CompanionActivity.REST && seconds.isFinite() && seconds >= 0f)
            elapsed = maxOf(elapsed, seconds)
    }
    fun advanceScheduledRest(rest: Boolean, delta: Float, bedX: Float? = null, bedY: Float = 650f, tempo: Float = 1f): Boolean {
        if (rest) {
            // A clock/DND boundary must not restart an existing sleep or cut a wake pose short.
            if (!scheduledRest && activity != CompanionActivity.REST && activity != CompanionActivity.WAKE)
                transition(if (bedX != null) CompanionActivity.APPROACH_BED else CompanionActivity.REST)
            scheduledRest = true
            val dt: Float = delta.coerceIn(0f, .1f)
            elapsed = (elapsed + dt).coerceAtMost(3600f)
            gingerPostureEndpoint = null
            val hadGingerPosture: Boolean = gingerPostureTransition != null
            if (advanceGingerPosture() || hadGingerPosture) return true
            if (bedX != null && wakeForMovedBed(bedX, bedY)) return true
            if (activity == CompanionActivity.WAKE && elapsed >= WAKE_SECONDS)
                transition(if (bedX != null) CompanionActivity.APPROACH_BED else CompanionActivity.REST)
            if (activity == CompanionActivity.APPROACH_BED &&
                (bedX == null || approach(bedX, bedY, dt, tempo))) transition(CompanionActivity.REST)
            return true
        }
        if (scheduledRest) {
            scheduledRest = false
            pendingIntent = CompanionIntent.OBSERVE
            if (activity != CompanionActivity.WAKE)
                transition(if (activity == CompanionActivity.REST) CompanionActivity.WAKE else CompanionActivity.OBSERVE)
        }
        return false
    }
    private fun wakeForMovedBed(bedX: Float, bedY: Float): Boolean {
        if (activity != CompanionActivity.REST ||
            hypot(bedX.coerceIn(140f, 860f) - x, bedY.coerceIn(430f, 680f) - y) <= .35f) return false
        pendingIntent = CompanionIntent.REST
        transition(CompanionActivity.WAKE)
        return true
    }
    /** Desktop wake keeps its position until the last wake pose has been shown. */
    fun advanceScheduledWake(delta: Float): Boolean {
        if (activity != CompanionActivity.WAKE) return false
        elapsed = (elapsed + delta.coerceIn(0f, .1f)).coerceAtMost(WAKE_SECONDS)
        gingerPostureEndpoint = null
        val hadGingerPosture: Boolean = gingerPostureTransition != null
        if (advanceGingerPosture() || hadGingerPosture) return true
        if (elapsed >= WAKE_SECONDS) {
            transition(CompanionActivity.OBSERVE)
            return false
        }
        return true
    }
    private fun chooseNext(): Unit = beginIntent(selector.choose(context))
    internal fun beginIntent(intent: CompanionIntent): Unit {
        pauseDuration = selector.nextPause()
        destinationX = selector.nextX()
        destinationY = selector.nextY()
        transition(when (intent) {
            CompanionIntent.PLAY -> CompanionActivity.APPROACH_TOY
            CompanionIntent.REST -> if (context.hasBed) CompanionActivity.APPROACH_BED else CompanionActivity.REST
            CompanionIntent.EXPLORE -> CompanionActivity.EXPLORE
            CompanionIntent.OBSERVE -> CompanionActivity.OBSERVE
        })
    }
    private fun transition(next: CompanionActivity): Unit {
        val previous: CompanionActivity = activity
        if (previous == CompanionActivity.REST && next == CompanionActivity.WAKE)
            restElapsedBeforeWake = elapsed
        activity = next
        elapsed = 0f
        speed = 0f
        isTurning = false
        if (!gingerPostures) return
        gingerPostureEndpoint = null
        if (gingerRestArtwork && next == CompanionActivity.REST) gingerRestStartsSeated = gingerSeated
        if (gingerRestArtwork && next == CompanionActivity.WAKE) gingerSeated = false
        gingerPostureTransition = when {
            gingerRestArtwork && (next == CompanionActivity.REST || next == CompanionActivity.WAKE) -> null
            next == CompanionActivity.REST && !gingerSeated -> com.pixelpals.app.core.motion.GingerPostureMotion.Transition.SIT_DOWN
            next == CompanionActivity.WAKE && previous == CompanionActivity.REST -> com.pixelpals.app.core.motion.GingerPostureMotion.Transition.STAND_UP
            next == CompanionActivity.OBSERVE && previous != CompanionActivity.WAKE && !gingerSeated -> com.pixelpals.app.core.motion.GingerPostureMotion.Transition.SIT_DOWN
            next == CompanionActivity.PLAY && !gingerSeated -> com.pixelpals.app.core.motion.GingerPostureMotion.Transition.SIT_DOWN
            next in setOf(CompanionActivity.APPROACH_TOY, CompanionActivity.APPROACH_BED, CompanionActivity.EXPLORE) && gingerSeated -> com.pixelpals.app.core.motion.GingerPostureMotion.Transition.STAND_UP
            else -> null
        }
    }

    private fun advanceGingerPosture(): Boolean {
        if (!gingerPostures) return false
        val transition: com.pixelpals.app.core.motion.GingerPostureMotion.Transition = gingerPostureTransition ?: return false
        val seconds: Float = gingerPostureElapsed
        val pose = com.pixelpals.app.core.motion.GingerPostureMotion.poseAt(transition, seconds)
        if (!pose.finished) return true
        gingerSeated = transition == com.pixelpals.app.core.motion.GingerPostureMotion.Transition.SIT_DOWN
        gingerPostureEndpoint = if (gingerSeated) 0 else 18
        gingerPostureTransition = null
        return false
    }
    private fun updateTurn(dx: Float, dt: Float, tempo: Float): Boolean {
        if (!isTurning && abs(dx) > 3f && (dx < 0) != isFacingLeft) {
            isTurning = true
            turnFromFacingLeft = isFacingLeft
            turnElapsed = 0f
            targetFacesLeft = dx < 0
        }
        if (!isTurning) return false
        if (speed > 0f) {
            speed = (speed - 180f * tempo * dt).coerceAtLeast(0f)
            val oldX: Float = x
            val oldY: Float = y
            x = (x + headingX * speed * dt).coerceIn(140f, 860f)
            y = (y + headingY * speed * dt).coerceIn(430f, 680f)
            distanceTravelled += hypot(x - oldX, y - oldY)
            return true
        }
        turnElapsed += dt
        if (turnElapsed >= turnCommitSeconds) isFacingLeft = targetFacesLeft
        if (turnElapsed >= turnDurationSeconds) isTurning = false
        return true
    }
    private fun approach(target: Float, targetY: Float, dt: Float, tempo: Float): Boolean {
        val dx: Float = target.coerceIn(140f, 860f) - x
        val dy: Float = targetY.coerceIn(430f, 680f) - y
        val distance: Float = hypot(dx, dy)
        if (distance <= .35f) {
            x = target.coerceIn(140f, 860f)
            y = targetY.coerceIn(430f, 680f)
            speed = 0f
            return true
        }
        if (updateTurn(dx, dt, tempo) || isPreparing) return false
        val acceleration: Float = 130f * tempo.coerceIn(.3f, 2f)
        val desiredSpeed: Float = minOf(85f * tempo, sqrt(2f * acceleration * distance))
        speed += (desiredSpeed - speed).coerceIn(-acceleration * dt, acceleration * dt)
        val step: Float = minOf(distance, speed.coerceAtLeast(0f) * dt)
        headingX = dx / distance
        headingY = dy / distance
        x += headingX * step
        y += headingY * step
        distanceTravelled += step
        return false
    }
    companion object {
        const val WAKE_SECONDS: Float = 1.2f
        const val ANTICIPATION_SECONDS: Float = .22f
        const val TURN_SECONDS: Float = .36f
    }
}
