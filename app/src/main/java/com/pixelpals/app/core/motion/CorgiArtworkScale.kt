package com.pixelpals.app.core.motion

/** Fixed source-camera calibration, never normalized from a changing silhouette. */
object CorgiArtworkScale {
    const val ORIGINAL_CELL: Float = .996f
    const val ORIGINAL_GROUND: Float = 740f / 768f
    const val CARE_CELL: Float = .78f
    // The original gait was exported closer to the camera than the standing poses.
    // Keep one correction for the entire cycle, including lifted paws and ears.
    fun originalFrame(index: Int): Float = if (index in 6..7 || index in 10..13) .84f else 1f
}
