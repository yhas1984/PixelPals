package com.pixelpals.app.data.catalog

/**
 * Especificación de un sprite atlas de accesorio (PNG con transparencia).
 *
 * El atlas empaqueta los frames en una grilla [columns] x [rows].
 * Los [clips] definen secuencias animadas (p. ej. "flap" para aleteo de alas,
 * "idle" para reposo). El [anchor] define dónde se ancla respecto al centro
 * del sprite del pet (ratios relativos a petSpriteSize).
 */
data class AccessorySpriteSpec(
    val atlasPath: String,
    val frameWidth: Int,
    val frameHeight: Int,
    val columns: Int,
    val rows: Int,
    val clips: Map<String, SpriteClip>,
    val anchor: SpriteAnchor,
    val zLayer: SpriteZLayer,
    val scale: Float,
) {
    fun clipOrDefault(flapping: Boolean): SpriteClip? {
        if (flapping) clips["flap"]?.let { return it }
        return clips["idle"] ?: clips.values.firstOrNull()
    }
}

data class SpriteClip(
    val frames: List<Int>,
    val frameDurationMs: Long,
    val loop: Boolean = true,
)

data class SpriteAnchor(
    /** Ratio X respecto a petSpriteSize (0 = centro, 0.25 = 25% a la derecha). */
    val xRatio: Float,
    /** Ratio Y respecto a petSpriteSize (negativo = arriba del centro). */
    val yRatio: Float,
)

enum class SpriteZLayer {
    /** Se dibuja detrás del cuerpo (alas, jetpack, capas). */
    BEHIND,

    /** Se dibuja encima del cuerpo (gorros, gafas, bufandas). */
    FRONT,
}
