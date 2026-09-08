package com.pixelpals.app.core.motion

/** Ballistic arc with constant acceleration and a fixed takeoff/landing height. */
object GroundJump {
    fun heightAt(ground: Float, apex: Float, progress: Float): Float {
        val t: Float = progress.coerceIn(0f, 1f)
        return ground + (apex - ground) * 4f * t * (1f - t)
    }
}
