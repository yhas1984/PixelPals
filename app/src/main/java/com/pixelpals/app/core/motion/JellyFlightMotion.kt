package com.pixelpals.app.core.motion

import kotlin.math.abs
import kotlin.math.sqrt

/** Analytic, non-bouncing flight used by Jelly's release gesture. */
class JellyFlightMotion(
    startX: Float,
    startY: Float,
    velocityX: Float,
    velocityY: Float,
    private val gravity: Float,
    private val minX: Float,
    private val maxX: Float,
    private val ceilingY: Float,
    private val floorY: Float,
) {
    var x: Float = startX
        private set
    var y: Float = startY
        private set
    var velocityX: Float = velocityX
        private set
    var velocityY: Float = velocityY
        private set
    var landed: Boolean = startY == floorY && velocityY >= 0f
        private set

    init {
        require(gravity.isFinite() && gravity > 0f) { "Gravity must be positive and finite" }
        require(startX.isFinite() && startY.isFinite() && velocityX.isFinite() && velocityY.isFinite()) {
            "Initial flight values must be finite"
        }
        require(minX.isFinite() && maxX.isFinite() && ceilingY.isFinite() && floorY.isFinite()) {
            "Flight bounds must be finite"
        }
        require(minX <= maxX) { "Horizontal bounds are inverted" }
        require(ceilingY <= floorY) { "Vertical bounds are inverted" }
        require(startX in minX..maxX && startY in ceilingY..floorY) { "Initial position is outside bounds" }
        if (x == minX && this.velocityX < 0f) this.velocityX = 0f
        if (x == maxX && this.velocityX > 0f) this.velocityX = 0f
        if (y == ceilingY && this.velocityY < 0f) this.velocityY = 0f
        if (landed) {
            y = floorY
            this.velocityX = 0f
            this.velocityY = 0f
        }
    }

    fun advance(dt: Float): Unit {
        if (dt <= 0f || landed) return
        var remaining: Float = dt
        var iterations: Int = 0
        while (remaining > EPSILON && iterations++ < MAX_EVENTS) {
            // Resolve outward contact before solving roots, avoiding zero-time loops.
            if (x <= minX + EPSILON && velocityX < 0f) {
                x = minX
                velocityX = 0f
            } else if (x >= maxX - EPSILON && velocityX > 0f) {
                x = maxX
                velocityX = 0f
            }
            if (y <= ceilingY + EPSILON && velocityY < 0f) {
                y = ceilingY
                velocityY = 0f
            }
            if (y >= floorY - EPSILON && velocityY >= 0f) {
                y = floorY
                velocityX = 0f
                velocityY = 0f
                landed = true
                return
            }

            val hitX: Float = timeToHorizontalBoundary()
            val ceilingTime: Float = timeToBoundary(ceilingY)
            val floorTime: Float = timeToBoundary(floorY)
            val hitY: Float = minOf(ceilingTime, floorTime)
            val eventTime: Float = minOf(remaining, hitX, hitY)
            integrate(eventTime)
            remaining -= eventTime

            val hitHorizontal: Boolean = hitX <= eventTime + EPSILON
            val hitVertical: Boolean = hitY <= eventTime + EPSILON
            if (hitHorizontal) {
                x = if (velocityX >= 0f) maxX else minX
                velocityX = 0f
            }
            if (hitVertical) {
                val hitFloor: Boolean = floorTime <= ceilingTime
                if (hitFloor) {
                    y = floorY
                    velocityX = 0f
                    velocityY = 0f
                    landed = true
                    return
                }
                y = ceilingY
                velocityY = 0f
            }
            if (!hitHorizontal && !hitVertical) break
        }
    }

    private fun integrate(seconds: Float): Unit {
        if (seconds <= 0f) return
        x += velocityX * seconds
        y += velocityY * seconds + .5f * gravity * seconds * seconds
        velocityY += gravity * seconds
    }

    private fun timeToHorizontalBoundary(): Float {
        if (velocityX > EPSILON) return ((maxX - x) / velocityX).coerceAtLeast(0f)
        if (velocityX < -EPSILON) return ((minX - x) / velocityX).coerceAtLeast(0f)
        return Float.POSITIVE_INFINITY
    }

    private fun timeToBoundary(boundary: Float): Float {
        val a: Float = .5f * gravity
        val b: Float = velocityY
        val c: Float = y - boundary
        if (abs(a) < EPSILON) return if (abs(b) < EPSILON) Float.POSITIVE_INFINITY
        else ((-c / b).takeIf { it > EPSILON } ?: Float.POSITIVE_INFINITY)
        val discriminant: Float = b * b - 4f * a * c
        if (discriminant < 0f) return Float.POSITIVE_INFINITY
        val root: Float = sqrt(discriminant)
        val first: Float = (-b - root) / (2f * a)
        val second: Float = (-b + root) / (2f * a)
        return when {
            first > EPSILON && second > EPSILON -> minOf(first, second)
            first > EPSILON -> first
            second > EPSILON -> second
            else -> Float.POSITIVE_INFINITY
        }
    }

    private companion object {
        const val EPSILON: Float = 0.00001f
        const val MAX_EVENTS: Int = 32
    }
}
