package com.pixelpals.app.core.care.scene

import kotlin.math.cos
import kotlin.math.sin

/** Spoon coordinates before the painter's size/2 scale and fixed tilt. */
object CareSpoonGeometry {
    const val TILT_DEGREES: Float = -25f
    const val BOWL_LEFT: Float = -.42f
    const val BOWL_TOP: Float = -.94f
    const val BOWL_RIGHT: Float = .43f
    const val BOWL_BOTTOM: Float = .07f
    private val radians: Double = Math.toRadians(TILT_DEGREES.toDouble())
    private val centerX: Float = (BOWL_LEFT + BOWL_RIGHT) / 2f
    private val centerY: Float = (BOWL_TOP + BOWL_BOTTOM) / 2f
    private val contactX: Float = (centerX * cos(radians) - centerY * sin(radians)).toFloat() / 2f
    private val contactY: Float = (centerX * sin(radians) + centerY * cos(radians)).toFloat() / 2f

    fun originAt(mouth: CarePoint, size: Float): CarePoint =
        CarePoint(mouth.x - contactX * size, mouth.y - contactY * size)
}
