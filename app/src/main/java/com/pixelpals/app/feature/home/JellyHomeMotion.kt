package com.pixelpals.app.feature.home

import com.pixelpals.app.core.motion.JellyElasticMotion

/** Visible-scene clock: drawing or pausing never advances the elastic response. */
internal class JellyHomeMotion {
    data class Pose(val scaleY: Float = 1f, val lift: Float = 0f)
    private enum class Phase { STILL, MOVING, SETTLING, TOUCH }

    var pose: Pose = Pose()
        private set
    private var phase: Phase = Phase.STILL
    private var previousActivity: CompanionActivity? = null
    private var distanceOrigin: Float = 0f
    private var elapsed: Float = 0f
    private var initial: Pose = Pose()
    private var pendingTouch: Boolean = false

    val toyResponse: Float
        get() = if (phase == Phase.TOUCH && elapsed >= .18f) {
            kotlin.math.sin((elapsed - .18f) / (JellyElasticMotion.TOUCH_SECONDS - .18f) * Math.PI).toFloat().coerceAtLeast(0f)
        } else 0f

    fun touch() { pendingTouch = true }

    fun advance(delta: Float, distance: Float, activity: CompanionActivity, speed: Float, reduced: Boolean) {
        val dt: Float = delta.coerceAtLeast(0f)
        if (dt == 0f) return
        if (reduced || activity == CompanionActivity.REST || activity == CompanionActivity.WAKE) {
            pose = Pose()
            phase = Phase.STILL
            pendingTouch = false
            previousActivity = activity
            return
        }
        val moving: Boolean = speed > .1f && (activity == CompanionActivity.APPROACH_TOY ||
            activity == CompanionActivity.APPROACH_BED || activity == CompanionActivity.EXPLORE)
        if (activity == CompanionActivity.PLAY && previousActivity != CompanionActivity.PLAY) pendingTouch = true
        previousActivity = activity

        if (pendingTouch && phase != Phase.SETTLING && phase != Phase.TOUCH) {
            begin(if (pose.lift < -.01f) Phase.SETTLING else Phase.TOUCH)
        } else if (moving && !pendingTouch && phase != Phase.MOVING) {
            distanceOrigin = distance
            begin(Phase.MOVING)
        } else if (!moving && phase == Phase.MOVING) {
            begin(Phase.SETTLING)
        }
        elapsed += dt
        when (phase) {
            Phase.STILL -> pose = Pose()
            Phase.MOVING -> {
                val target = JellyElasticMotion.homePose(distance - distanceOrigin)
                val blend = JellyElasticMotion.ease(elapsed / .14f)
                pose = Pose(initial.scaleY + (target.scaleY - initial.scaleY) * blend,
                    initial.lift + (target.lift - initial.lift) * blend)
            }
            Phase.SETTLING -> {
                val descent: Float = if (initial.lift < -.01f) .16f else 0f
                val lift: Float = if (descent > 0f) initial.lift * (1f - JellyElasticMotion.ease(elapsed / descent)) else 0f
                val shape: Float = if (elapsed < descent) initial.scaleY
                    else JellyElasticMotion.landingScaleY(elapsed - descent, initial.scaleY)
                pose = Pose(shape, lift)
                if (elapsed >= descent + JellyElasticMotion.LAND_SECONDS) {
                    pose = Pose()
                    begin(if (pendingTouch) Phase.TOUCH else Phase.STILL)
                }
            }
            Phase.TOUCH -> {
                pose = Pose(JellyElasticMotion.touchScaleY(elapsed, initial.scaleY), 0f)
                if (elapsed >= JellyElasticMotion.TOUCH_SECONDS) {
                    pendingTouch = false
                    pose = Pose()
                    begin(Phase.STILL)
                }
            }
        }
    }

    private fun begin(next: Phase) {
        initial = pose
        elapsed = 0f
        phase = next
    }
}
