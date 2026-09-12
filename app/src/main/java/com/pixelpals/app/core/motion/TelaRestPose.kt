package com.pixelpals.app.core.motion

/** Fixed-camera sleep poses, with an optional drawn curl and its reversible wake. */
object TelaRestPose {
    val legacyFrames: List<Int> = listOf(38, 39)
    val restFrames: List<Int> = listOf(39, 40, 41, 42)
    val wakeFrames: List<Int> = restFrames.asReversed()
    const val STEP_SECONDS: Float = .24f
    const val DURATION_SECONDS: Float = .96f
    val frames: List<Int> get() = legacyFrames
}
