package com.pixelpals.app.debug

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.View
import com.pixelpals.app.feature.overlay.behavior.PetAtlasSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.roundToInt

@SuppressLint("ViewConstructor")
internal class TaroDebugOverlayView(
    context: Context,
    val screenWidth: Int,
    val screenHeight: Int,
    val spriteSize: Int,
    private val onMove: (Float, Float) -> Unit,
) : View(context) {
    val viewSize: Int = spriteSize
    private val halfSize = spriteSize / 2f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val frameRunnable = object : Runnable {
        override fun run() {
            if (!animating) return
            val now = System.currentTimeMillis()
            val dt = if (lastFrameAt == 0L) 1f / 60f else ((now - lastFrameAt).coerceIn(1L, 80L) / 1000f)
            lastFrameAt = now
            update(dt)
            invalidate()
            handler.postDelayed(this, 16L)
        }
    }

    private var atlasSpec: PetAtlasSpec? = null
    private var atlasBitmap: Bitmap? = null
    private var animating = false
    private var lastFrameAt = 0L
    private var manualClip: TaroReviewClip? = null
    private var manualDirection = 1f
    private var manualSpeed = 1f
    private var manualElapsed = 0f
    private var manualCycles = 0
    private var playbackSpeed = 1f
    private var centerX = screenWidth / 2f
    private var centerY = screenHeight * 0.62f
    private var autoElapsed = 0f
    private var autoWalking = false
    private var autoDirection = 1f
    private var autoFrame = 0

    init {
        loadAtlas()
    }

    fun startAnimation() {
        if (animating) return
        animating = true
        lastFrameAt = 0L
        handler.post(frameRunnable)
    }

    fun stopAnimation() {
        animating = false
        handler.removeCallbacks(frameRunnable)
        scope.coroutineContext[Job]?.cancel()
        atlasBitmap?.let { if (!it.isRecycled) it.recycle() }
        atlasBitmap = null
    }

    fun onScreenChanged(isScreenOn: Boolean) {
        if (isScreenOn) startAnimation() else {
            animating = false
            handler.removeCallbacks(frameRunnable)
        }
    }

    fun startManualReview(clip: TaroReviewClip, direction: Float, speed: Float) {
        manualClip = clip
        manualDirection = if (direction >= 0f) 1f else -1f
        manualSpeed = speed.coerceIn(0.25f, 1f)
        manualElapsed = 0f
        manualCycles = 0
        autoWalking = false
        centerX = screenWidth / 2f
        centerY = screenHeight * 0.62f
        onMove(centerX, centerY)
        startAnimation()
    }

    fun stopManualReview() {
        manualClip = null
        manualElapsed = 0f
    }

    fun startAutonomous() {
        manualClip = null
        autoElapsed = 0f
        autoWalking = false
        startAnimation()
    }

    fun setPlaybackSpeed(speed: Float) {
        playbackSpeed = speed.coerceIn(0.25f, 4f)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        super.onDraw(canvas)
        val spec = atlasSpec ?: return
        val bitmap = atlasBitmap ?: return
        val clip = manualClip?.let { spec.clip(it.clipId) }
        val frame = when {
            clip != null -> frameForClip(clip.frames, clip.frameDurationMs)
            autoWalking -> frameForClip(spec.clip("walk")?.frames.orEmpty(), 420)
            else -> frameForClip(spec.clip("idle")?.frames.orEmpty(), 900)
        }
        if (frame < 0) return
        val source = Rect(
            (frame % spec.columns) * spec.frameWidth,
            (frame / spec.columns) * spec.frameHeight,
            ((frame % spec.columns) + 1) * spec.frameWidth,
            ((frame / spec.columns) + 1) * spec.frameHeight,
        )
        canvas.save()
        canvas.translate(viewSize / 2f, viewSize / 2f)
        canvas.scale(if (manualClip != null) manualDirection else autoDirection, 1f)
        canvas.drawBitmap(bitmap, source, RectF(-halfSize, -halfSize, halfSize, halfSize), paint)
        canvas.restore()
    }

    private fun update(dt: Float) {
        val spec = atlasSpec ?: return
        val clip = manualClip?.let { spec.clip(it.clipId) }
        if (clip != null) {
            manualElapsed += dt * manualSpeed
            val duration = clip.frames.size * clip.frameDurationMs / 1000f
            if (manualElapsed >= duration) {
                manualElapsed -= duration
                manualCycles++
                if (manualCycles >= if (clip.loop) 2 else 1) manualClip = null
            }
            return
        }

        autoElapsed += dt * playbackSpeed
        if (!autoWalking && autoElapsed >= 1.5f) {
            autoWalking = true
            autoElapsed = 0f
            autoDirection = if (autoDirection > 0f) -1f else 1f
        } else if (autoWalking) {
            centerX += autoDirection * 90f * playbackSpeed * dt
            val minX = halfSize
            val maxX = screenWidth - halfSize
            if (centerX !in minX..maxX || autoElapsed >= 7f) {
                centerX = centerX.coerceIn(minX, maxX)
                autoWalking = false
                autoElapsed = 0f
            }
            onMove(centerX, centerY)
        }
        autoFrame++
    }

    private fun frameForClip(frames: List<Int>, frameDurationMs: Int): Int {
        if (frames.isEmpty()) return -1
        return frames[((if (manualClip != null) manualElapsed else autoElapsed) * 1000f / frameDurationMs).toInt() % frames.size]
    }

    private fun loadAtlas() {
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    val json = context.assets.open(SPEC_PATH).bufferedReader().use { it.readText() }
                    val spec = PetAtlasSpec.fromJson(JSONObject(json))
                    val bitmap = context.assets.open(spec.atlasPath).use(BitmapFactory::decodeStream)
                    spec to bitmap
                }.getOrNull()
            }
            atlasSpec = loaded?.first
            atlasBitmap = loaded?.second
            invalidate()
        }
    }

    companion object {
        private const val SPEC_PATH = "pets/taro/taro_motion_v2.json"
    }
}
