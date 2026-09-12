package com.pixelpals.app.core.motion

/** Fixed source-camera calibration, never normalized from a changing silhouette. */
object CorgiArtworkScale {
    const val ORIGINAL_CELL: Float = .996f
    const val ORIGINAL_GROUND: Float = 740f / 768f
    const val CARE_CELL: Float = .78f
    const val SEATED_CARE_CELL: Float = .96f
    // Bath cells were exported closer than the other seated care families.
    const val CLEAN_CARE_CELL: Float = .82f
    const val UPRIGHT_CARE_CELL: Float = .81f
    fun careCell(action: com.pixelpals.app.core.care.scene.CareSceneAction): Float = when (action) {
        com.pixelpals.app.core.care.scene.CareSceneAction.REST,
        -> SEATED_CARE_CELL
        com.pixelpals.app.core.care.scene.CareSceneAction.PET,
        com.pixelpals.app.core.care.scene.CareSceneAction.MEDICINE -> UPRIGHT_CARE_CELL
        com.pixelpals.app.core.care.scene.CareSceneAction.CLEAN -> CLEAN_CARE_CELL
        else -> CARE_CELL
    }
    // The original gait was exported closer to the camera than the standing poses.
    // Keep one correction for the entire cycle, including lifted paws and ears.
    fun originalFrame(index: Int): Float = if (index in 6..7 || index in 10..13) .84f else 1f
}
