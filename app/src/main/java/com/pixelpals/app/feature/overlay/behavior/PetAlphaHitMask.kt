package com.pixelpals.app.feature.overlay.behavior

import android.graphics.Bitmap

/** Compact, immutable alpha mask created once when an atlas is decoded. */
class PetAlphaHitMask private constructor(
    val frameWidth: Int,
    val frameHeight: Int,
    val frameCount: Int,
    private val longsPerFrame: Int,
    private val opaqueBits: LongArray,
) {
    val byteCount: Int
        get() = opaqueBits.size * Long.SIZE_BYTES

    fun isOpaque(frame: Int, x: Int, y: Int): Boolean {
        if (frame !in 0 until frameCount || x !in 0 until frameWidth || y !in 0 until frameHeight) return false
        val pixelIndex = y * frameWidth + x
        val bitIndex = frame * longsPerFrame * Long.SIZE_BITS + pixelIndex
        return opaqueBits[bitIndex / Long.SIZE_BITS] and (1L shl (bitIndex % Long.SIZE_BITS)) != 0L
    }

    companion object {
        private const val TOUCH_ALPHA_THRESHOLD: Int = 32

        fun fromBitmap(bitmap: Bitmap, spec: PetAtlasSpec): PetAlphaHitMask {
            val pixelsPerFrame = spec.frameWidth * spec.frameHeight
            val longsPerFrame = (pixelsPerFrame + Long.SIZE_BITS - 1) / Long.SIZE_BITS
            val bits = LongArray(longsPerFrame * spec.frameCount)
            val pixels = IntArray(pixelsPerFrame)
            repeat(spec.frameCount) { frame ->
                val column = frame % spec.columns
                val row = frame / spec.columns
                bitmap.getPixels(
                    pixels,
                    0,
                    spec.frameWidth,
                    column * spec.frameWidth,
                    row * spec.frameHeight,
                    spec.frameWidth,
                    spec.frameHeight,
                )
                pixels.forEachIndexed { pixelIndex, color ->
                    if (color ushr 24 >= TOUCH_ALPHA_THRESHOLD) {
                        val bitIndex = frame * longsPerFrame * Long.SIZE_BITS + pixelIndex
                        bits[bitIndex / Long.SIZE_BITS] =
                            bits[bitIndex / Long.SIZE_BITS] or (1L shl (bitIndex % Long.SIZE_BITS))
                    }
                }
            }
            return PetAlphaHitMask(
                frameWidth = spec.frameWidth,
                frameHeight = spec.frameHeight,
                frameCount = spec.frameCount,
                longsPerFrame = longsPerFrame,
                opaqueBits = bits,
            )
        }
    }
}
