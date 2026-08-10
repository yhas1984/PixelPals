package com.pixelpals.app.data.catalog

import android.graphics.ColorMatrix
import org.json.JSONObject

/**
 * Cosmético que NO se pega al cuerpo del pet (no requiere alineación).
 *
 * - [TintEffect]: recolorea el sprite completo — sigue todos los movimientos
 *   porque es el mismo sprite con un filtro de color.
 * - [AuraEffect]: partículas que orbitan alrededor del pet (siguen la posición
 *   de la ventana, no el contorno del cuerpo).
 * - [FloatEffect]: objeto que flota adyacente al pet con física propia.
 */
sealed class CosmeticEffect {

    data class TintEffect(
        /** Escala RGB aplicada al sprite (1f = sin cambio). */
        val redScale: Float = 1f,
        val greenScale: Float = 1f,
        val blueScale: Float = 1f,
        /** Saturación: 1f = normal, 0f = gris. */
        val saturation: Float = 1f,
    ) : CosmeticEffect() {
        fun toColorMatrix(): ColorMatrix {
            val m = ColorMatrix()
            m.setSaturation(saturation)
            val scale = ColorMatrix()
            scale.setScale(redScale, greenScale, blueScale, 1f)
            m.postConcat(scale)
            return m
        }
    }

    data class AuraEffect(
        val emoji: String,
        /** Número de partículas orbitando. */
        val count: Int = 6,
        /** Radio de la órbita en fracción del petSpriteSize. */
        val radiusRatio: Float = 0.85f,
        /** Velocidad angular (rad/s). */
        val speed: Float = 1.6f,
        /** Tamaño del emoji en fracción del petSpriteSize. */
        val sizeRatio: Float = 0.18f,
    ) : CosmeticEffect()

    data class FloatEffect(
        val emoji: String,
        /** Offset horizontal en fracción del petSpriteSize (0 = centrado). */
        val xRatio: Float = 0.75f,
        /** Offset vertical en fracción del petSpriteSize (negativo = arriba). */
        val yRatio: Float = -0.55f,
        /** Amplitud del bobbing vertical en fracción del petSpriteSize. */
        val bobAmplitude: Float = 0.08f,
        /** Velocidad del bobbing (rad/s). */
        val bobSpeed: Float = 2.2f,
        /** Tamaño del emoji en fracción del petSpriteSize. */
        val sizeRatio: Float = 0.30f,
    ) : CosmeticEffect()

    companion object {
        fun fromJson(json: JSONObject): CosmeticEffect? {
            return when (json.optString("type")) {
                "tint" -> TintEffect(
                    redScale = json.optDouble("redScale", 1.0).toFloat(),
                    greenScale = json.optDouble("greenScale", 1.0).toFloat(),
                    blueScale = json.optDouble("blueScale", 1.0).toFloat(),
                    saturation = json.optDouble("saturation", 1.0).toFloat(),
                )
                "aura" -> AuraEffect(
                    emoji = json.optString("emoji", "✨"),
                    count = json.optInt("count", 6),
                    radiusRatio = json.optDouble("radiusRatio", 0.85).toFloat(),
                    speed = json.optDouble("speed", 1.6).toFloat(),
                    sizeRatio = json.optDouble("sizeRatio", 0.18).toFloat(),
                )
                "float" -> FloatEffect(
                    emoji = json.optString("emoji", "👑"),
                    xRatio = json.optDouble("xRatio", 0.75).toFloat(),
                    yRatio = json.optDouble("yRatio", -0.55).toFloat(),
                    bobAmplitude = json.optDouble("bobAmplitude", 0.08).toFloat(),
                    bobSpeed = json.optDouble("bobSpeed", 2.2).toFloat(),
                    sizeRatio = json.optDouble("sizeRatio", 0.30).toFloat(),
                )
                else -> null
            }
        }
    }
}

/**
 * Cosmético equipable: nombre, precio y efecto.
 * Cargado desde assets/cosmetics_catalog.json.
 */
data class Cosmetic(
    val id: String,
    val displayName: String,
    val description: String,
    val productId: String,
    val effect: CosmeticEffect,
    val coinPrice: Int? = null,
)
