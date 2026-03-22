package com.pixelpals.app.animation

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import com.pixelpals.app.PetType

/**
 * AssetAuditor — Audita los assets de animación y detecta frames faltantes
 */
class AssetAuditor(private val context: Context) {

    companion object {
        private const val TAG = "AssetAuditor"
    }

    /**
     * Audit assets for a pet type and return list of existing frame indices
     */
    fun audit(petType: PetType): AuditResult {
        val existingFrames = mutableListOf<Int>()
        val missingFrames = mutableListOf<Int>()

        // Get expected frame names based on pet type
        val expectedFrames = getExpectedFrames(petType)

        for ((index, frameName) in expectedFrames.withIndex()) {
            val resId = context.resources.getIdentifier(frameName, "drawable", context.packageName)
            if (resId != 0) {
                // Verify the resource is actually loadable
                try {
                    val bitmap = BitmapFactory.decodeResource(context.resources, resId)
                    if (bitmap != null) {
                        existingFrames.add(index)
                        bitmap.recycle()
                    } else {
                        missingFrames.add(index)
                    }
                } catch (e: Exception) {
                    missingFrames.add(index)
                }
            } else {
                missingFrames.add(index)
            }
        }

        // Also check generated frames in internal storage
        val generatedDir = java.io.File(context.filesDir, "generated_frames/${petType.name.lowercase()}")
        if (generatedDir.exists()) {
            generatedDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("frame_") && file.name.endsWith(".png")) {
                    val frameIndex = file.name.removePrefix("frame_").removeSuffix(".png").toIntOrNull()
                    if (frameIndex != null && frameIndex !in existingFrames) {
                        existingFrames.add(frameIndex)
                        missingFrames.remove(frameIndex)
                    }
                }
            }
        }

        Log.d(TAG, "Audit for ${petType.displayName}: ${existingFrames.size} existing, ${missingFrames.size} missing")

        return AuditResult(
            petType = petType,
            existingFrames = existingFrames,
            missingFrames = missingFrames,
            isComplete = missingFrames.isEmpty()
        )
    }

    /**
     * Get expected frame resource names for each pet type
     */
    private fun getExpectedFrames(petType: PetType): List<String> {
        return when (petType) {
            PetType.BLOOP -> listOf(
                "fantasma_0", "fantasma_1", "fantasma_2", "fantasma_3",
                "fantasma_4", "fantasma_5", "fantasma_6", "fantasma_7",
                "fantasma_8", "fantasma_9", "fantasma_10", "fantasma_11"
            )
            PetType.NUBE_MICHI -> listOf(
                "gato_0", "gato_1", "gato_2", "gato_3"
            )
            PetType.JELLY -> listOf(
                "jelly_0", "jelly_1", "jelly_2", "jelly_3",
                "jelly_4", "jelly_5", "jelly_6", "jelly_7",
                "jelly_8", "jelly_9", "jelly_10", "jelly_11"
            )
            PetType.CORGI -> listOf(
                "perro_0", "perro_1", "perro_2", "perro_3",
                "perro_4", "perro_5", "perro_6", "perro_7",
                "perro_8", "perro_9", "perro_10", "perro_11"
            )
            PetType.GINGER -> listOf(
                "ginger_0", "ginger_1", "ginger_2", "ginger_3",
                "ginger_4", "ginger_5", "ginger_6", "ginger_7",
                "ginger_8", "ginger_9", "ginger_10"
            )
            PetType.PATITO -> listOf(
                "patito_0", "patito_1", "patito_2", "patito_3",
                "patito_4", "patito_5", "patito_6", "patito_7",
                "patito_8", "patito_9", "patito_10", "patito_11",
                "patito_12", "patito_13", "patito_14"
            )
            PetType.DIABLILLO -> listOf(
                "diablillo_0", "diablillo_1", "diablillo_2",
                "diablillo_3", "diablillo_4", "diablillo_5"
            )
        }
    }

    /**
     * Data class for audit results
     */
    data class AuditResult(
        val petType: PetType,
        val existingFrames: List<Int>,
        val missingFrames: List<Int>,
        val isComplete: Boolean
    ) {
        val totalFrames: Int get() = existingFrames.size + missingFrames.size
        val completionPercent: Float get() = if (totalFrames > 0) (existingFrames.size.toFloat() / totalFrames) * 100f else 100f
    }
}
