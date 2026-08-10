package com.pixelpals.app.core.motion

/**
 * Zona transitable de la mascota en coordenadas LÓGICAS del sprite
 * (esquina superior izquierda del sprite en pantalla).
 *
 * Fuente única de límites para PetView, BaseBehavior y la física: elimina la
 * divergencia entre groundY, safeMaxY y los márgenes duplicados de cada
 * behavior.
 */
data class PetBounds(
    val left: Int,
    val right: Int,
    val top: Int,
    val floor: Int
) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (floor - top).coerceAtLeast(0)

    companion object {
        /** Margen mínimo desde el inset superior para la zona transitable. */
        const val TOP_MARGIN_PX = 50

        /** Margen mínimo desde el inset inferior para la zona transitable. */
        const val FLOOR_MARGIN_PX = 100

        fun compute(
            screenWidth: Int,
            screenHeight: Int,
            petSpriteSize: Int,
            topSystemInsetPx: Int,
            bottomSystemInsetPx: Int,
            keyboardHeightPx: Int = 0
        ): PetBounds {
            val right = (screenWidth - petSpriteSize).coerceAtLeast(0)
            val top = topSystemInsetPx + TOP_MARGIN_PX
            val floor = (screenHeight - bottomSystemInsetPx - petSpriteSize -
                keyboardHeightPx - FLOOR_MARGIN_PX).coerceAtLeast(top)
            return PetBounds(0, right, top, floor)
        }
    }
}
