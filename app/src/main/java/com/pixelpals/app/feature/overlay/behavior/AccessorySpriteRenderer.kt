package com.pixelpals.app.feature.overlay.behavior

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import com.pixelpals.app.data.catalog.AccessoryCatalogItem
import com.pixelpals.app.data.catalog.AccessorySpriteSpec
import com.pixelpals.app.data.catalog.SpriteClip
import java.util.concurrent.ConcurrentHashMap

/**
 * Renderiza el sprite atlas de un accesorio sobre el pet.
 *
 * Soporta clips animados (idle / flap) con reloj propio por accesorio,
 * ancla relativa al centro del pet y capa (detrás / delante del cuerpo).
 */
class AccessorySpriteRenderer(context: Context) {

    private val appContext = context.applicationContext
    private val atlasCache = ConcurrentHashMap<String, Bitmap?>()
    private val clipClocks = ConcurrentHashMap<String, ClipClock>()

    private data class ClipClock(var elapsedMs: Long, var frameIndex: Int, var currentClipKey: String)

    /** Carga y cachea el atlas PNG del accesorio desde assets. */
    private fun atlasOf(spec: AccessorySpriteSpec): Bitmap? {
        atlasCache[spec.atlasPath]?.let { return it }
        val bitmap = try {
            appContext.assets.open(spec.atlasPath).use { input ->
                BitmapFactory.decodeStream(input)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cannot load accessory atlas ${spec.atlasPath}", e)
            null
        }
        atlasCache[spec.atlasPath] = bitmap
        return bitmap
    }

    /**
     * Devuelve el bitmap del primer frame del clip idle (para la card de la tienda).
     * Si el accesorio no tiene sprite, devuelve null (la card usará el emoji).
     */
    fun loadIdleFrame(accessory: AccessoryCatalogItem): Bitmap? {
        val spec = accessory.sprite ?: return null
        val sheet = atlasOf(spec) ?: return null
        if (sheet.isRecycled) return null

        val clip = spec.clipOrDefault(flapping = false) ?: return null
        if (clip.frames.isEmpty()) return null
        val frameIndex = clip.frames.first().coerceAtLeast(0)

        val cols = spec.columns.coerceAtLeast(1)
        val rows = spec.rows.coerceAtLeast(1)
        val row = frameIndex / cols
        val col = frameIndex % cols

        return try {
            Bitmap.createBitmap(
                sheet,
                col * spec.frameWidth,
                row * spec.frameHeight,
                spec.frameWidth,
                spec.frameHeight,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Cannot extract frame for ${accessory.id}", e)
            null
        }
    }

    /**
     * Dibuja el accesorio anclado a la cabeza del pet.
     *
     * @param headAnchorYRatio fracción negativa del petSpriteSize donde está la
     *   cabeza del pet desde el centro del view (ver PetViewBridge).
     * @param petRotation rotación actual del pet (rad) para sincronizar el accesorio.
     * @param flapping si true, intenta reproducir el clip "flap" (alas/gadgets activos).
     * @param facingRight si false, voltea horizontalmente el accesorio.
     */
    fun draw(
        canvas: Canvas,
        accessory: AccessoryCatalogItem,
        petCenterX: Float,
        petCenterY: Float,
        petSpriteSize: Int,
        dt: Float,
        flapping: Boolean,
        facingRight: Boolean,
        headAnchorYRatio: Float,
        petRotation: Float,
        paint: Paint,
    ) {
        val spec = accessory.sprite ?: return
        val sheet = atlasOf(spec) ?: return
        if (sheet.isRecycled) return

        val clip = spec.clipOrDefault(flapping) ?: return
        val frame = advanceClock(accessory.id, spec, clip, dt, flapping)

        val cols = spec.columns.coerceAtLeast(1)
        val rows = spec.rows.coerceAtLeast(1)
        val totalFrames = cols * rows
        val frameIndex = clip.frames[frame % clip.frames.size].coerceIn(0, totalFrames - 1)
        val row = frameIndex / cols
        val col = frameIndex % cols

        val srcRect = Rect(
            col * spec.frameWidth,
            row * spec.frameHeight,
            (col + 1) * spec.frameWidth,
            (row + 1) * spec.frameHeight,
        )

        // El ancla se calcula desde la CABEZA del pet (no del centro del view).
        val anchorScale = spec.scale * petSpriteSize / spec.frameWidth
        val headY = petCenterY + headAnchorYRatio * petSpriteSize
        val anchorX = petCenterX + spec.anchor.xRatio * petSpriteSize
        val anchorY = headY + spec.anchor.yRatio * petSpriteSize

        val halfW = spec.frameWidth * anchorScale / 2f
        val halfH = spec.frameHeight * anchorScale / 2f
        val dstRect = RectF(anchorX - halfW, anchorY - halfH, anchorX + halfW, anchorY + halfH)

        canvas.save()
        // Rotación y flip alrededor del ancla: el accesorio sigue al pet.
        if (petRotation != 0f) canvas.rotate(Math.toDegrees(petRotation.toDouble()).toFloat(), anchorX, anchorY)
        if (!facingRight) {
            canvas.scale(-1f, 1f, anchorX, anchorY)
        }
        val previousFilter = paint.isFilterBitmap
        paint.isFilterBitmap = true
        canvas.drawBitmap(sheet, srcRect, dstRect, paint)
        paint.isFilterBitmap = previousFilter
        canvas.restore()
    }

    private fun advanceClock(
        accessoryId: String,
        spec: AccessorySpriteSpec,
        clip: SpriteClip,
        dt: Float,
        flapping: Boolean,
    ): Int {
        val clipKey = if (flapping && spec.clips.containsKey("flap")) "flap" else "idle"
        val clock = clipClocks.computeIfAbsent(accessoryId) { ClipClock(0L, 0, clipKey) }

        if (clock.currentClipKey != clipKey) {
            clock.currentClipKey = clipKey
            clock.elapsedMs = 0L
            clock.frameIndex = 0
        }

        clock.elapsedMs += (dt * 1000f).toLong()
        if (clip.frameDurationMs <= 0) return clock.frameIndex

        while (clock.elapsedMs >= clip.frameDurationMs && clip.frames.size > 1) {
            clock.elapsedMs -= clip.frameDurationMs
            if (clip.loop) {
                clock.frameIndex = (clock.frameIndex + 1) % clip.frames.size
            } else {
                clock.frameIndex = (clock.frameIndex + 1).coerceAtMost(clip.frames.size - 1)
            }
        }
        return clock.frameIndex
    }

    /** Libera los bitmaps cacheados (llamar al detach del view). */
    fun clear() {
        atlasCache.values.filterNotNull().forEach { bitmap ->
            if (!bitmap.isRecycled) bitmap.recycle()
        }
        atlasCache.clear()
        clipClocks.clear()
    }

    private companion object {
        const val TAG = "AccessorySpriteRenderer"
    }
}
