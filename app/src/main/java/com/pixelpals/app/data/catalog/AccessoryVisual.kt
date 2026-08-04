package com.pixelpals.app.data.catalog

import androidx.annotation.DrawableRes

/** Categoría de accesorio — define dónde se renderiza y cómo interactúa con el comportamiento. */
enum class AccessorySlot {
    /** Gorros, coronas, halos. Se dibuja encima de la cabeza. */
    HEAD,

    /** Gafas, antifaz. Se dibuja a la altura de los ojos. */
    FACE,

    /** Alas, capas. Se dibuja detrás del sprite base. */
    BACK,

    /** Jetpack, paraguas, gadgets funcionales grandes. */
    GADGET,

    /** Bufandas, corbatas, bandas. Se dibuja sobre el cuerpo. */
    BODY,
}

/** Forma de representar visualmente un accesorio. */
sealed class AccessoryVisual {
    /** Emoji centrado, escalado y offset por ratios del sprite. */
    data class EmojiOverlay(
        val offsetXRatio: Float,
        val offsetYRatio: Float,
        val scale: Float,
    ) : AccessoryVisual()

    /** Drawable Android (estático o animado por frames). */
    data class SpriteOverlay(
        @DrawableRes val drawableResId: Int,
        val offsetXRatio: Float,
        val offsetYRatio: Float,
        val scale: Float,
        val frames: List<DrawableFrame> = emptyList(),
    ) : AccessoryVisual()
}

data class DrawableFrame(
    @DrawableRes val drawableResId: Int,
    val durationMs: Long,
)
