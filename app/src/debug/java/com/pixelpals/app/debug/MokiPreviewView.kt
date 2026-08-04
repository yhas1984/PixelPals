package com.pixelpals.app.debug

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.Choreographer
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.pixelpals.app.R
import com.pixelpals.app.behavior.PetAtlasSpec
import com.pixelpals.app.motion.MotionEngine
import com.pixelpals.app.motion.MokiMotionController
import com.pixelpals.app.motion.MokiPose
import org.json.JSONObject
import kotlin.math.hypot

@SuppressLint("ViewConstructor")
internal class MokiPreviewView(context: Context) : View(context) {
    private val density: Float = resources.displayMetrics.density
    private val motionEngine: MotionEngine = MotionEngine()
    private val controller: MokiMotionController = MokiMotionController(density, topClearanceDp = HEADER_CLEARANCE_DP)
    private val touchSlop: Float = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val atlasSpec: PetAtlasSpec
    private val atlasBitmap: Bitmap
    private val spritePaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val backgroundPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(13, 23, 38) }
    private val gridPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(34, 159, 232, 217)
        strokeWidth = density
    }
    private val trackPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(110, 255, 138, 122)
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }
    private val titlePaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(244, 249, 248)
        textSize = 18f * density
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val captionPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(159, 232, 217)
        textSize = 12f * density
    }
    private var drawSize: Float = DEFAULT_DRAW_SIZE_DP * density
    private var pose: MokiPose = controller.getPose()
    private var isAnimating: Boolean = false
    private var isTrackingTouch: Boolean = false
    private var isDragging: Boolean = false
    private var downX: Float = 0f
    private var downY: Float = 0f
    private var dragOffsetX: Float = 0f
    private var dragOffsetY: Float = 0f
    private var safeTopInset: Int = 0
    private var safeBottomInset: Int = 0
    private var velocityTracker: VelocityTracker? = null
    private var lastFrameTimeNanos: Long = 0L
    private val frameCallback: Choreographer.FrameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long): Unit {
            if (!isAnimating) return
            if (lastFrameTimeNanos != 0L && frameTimeNanos > lastFrameTimeNanos) {
                val elapsedSeconds: Float = (frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f
                val step = motionEngine.splitDelta(elapsedSeconds)
                repeat(step.steps) { pose = controller.update(step.stepDt) }
            }
            lastFrameTimeNanos = frameTimeNanos
            invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    init {
        val specText: String = context.assets.open(SPEC_PATH).bufferedReader().use { it.readText() }
        atlasSpec = PetAtlasSpec.fromJson(JSONObject(specText))
        atlasBitmap = context.assets.open(atlasSpec.atlasPath).use { BitmapFactory.decodeStream(it) }
            ?: error("Unable to decode Moki debug atlas")
        isFocusable = true
        contentDescription = context.getString(R.string.debug_moki_instructions)
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            safeTopInset = systemBars.top
            safeBottomInset = systemBars.bottom
            controller.updateViewport(width, height, drawSize, safeTopInset, safeBottomInset)
            pose = controller.getPose()
            invalidate()
            insets
        }
    }

    override fun onAttachedToWindow(): Unit {
        super.onAttachedToWindow()
        startAnimation()
    }

    override fun onDetachedFromWindow(): Unit {
        stopAnimation()
        recycleVelocityTracker()
        if (!atlasBitmap.isRecycled) atlasBitmap.recycle()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int): Unit {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) startAnimation() else stopAnimation()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int): Unit {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        drawSize = minOf(DEFAULT_DRAW_SIZE_DP * density, width * 0.48f, height * 0.30f)
        controller.updateViewport(width, height, drawSize, safeTopInset, safeBottomInset)
        pose = controller.getPose()
        ViewCompat.requestApplyInsets(this)
    }

    override fun onDraw(canvas: Canvas): Unit {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        drawGrid(canvas)
        drawTrack(canvas)
        drawInstructions(canvas)
        drawMoki(canvas)
    }

    private fun drawGrid(canvas: Canvas): Unit {
        val step: Float = 32f * density
        var x: Float = 0f
        while (x <= width) {
            canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
            x += step
        }
        var y: Float = 0f
        while (y <= height) {
            canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
            y += step
        }
    }

    private fun drawTrack(canvas: Canvas): Unit {
        val inset: Float = 18f * density
        canvas.drawRoundRect(
            RectF(inset, safeTopInset + HEADER_CLEARANCE_DP * density, width - inset, height - safeBottomInset - inset),
            18f * density,
            18f * density,
            trackPaint,
        )
    }

    private fun drawInstructions(canvas: Canvas): Unit {
        val left: Float = 20f * density
        val titleY: Float = safeTopInset + 32f * density
        canvas.drawText(context.getString(R.string.debug_moki_title), left, titleY, titlePaint)
        canvas.drawText(context.getString(R.string.debug_moki_instructions), left, titleY + 22f * density, captionPaint)
        val stateText: String = context.getString(R.string.debug_moki_state, pose.mode.name, pose.surface.name)
        canvas.drawText(stateText, left, titleY + 42f * density, captionPaint)
    }

    private fun drawMoki(canvas: Canvas): Unit {
        val frameIndex: Int = pose.frameIndex.coerceIn(0, atlasSpec.frameCount - 1)
        val column: Int = frameIndex % atlasSpec.columns
        val row: Int = frameIndex / atlasSpec.columns
        val source = Rect(
            column * atlasSpec.frameWidth,
            row * atlasSpec.frameHeight,
            (column + 1) * atlasSpec.frameWidth,
            (row + 1) * atlasSpec.frameHeight,
        )
        val halfSize: Float = drawSize / 2f
        val destination = RectF(-halfSize, -halfSize, halfSize, halfSize)
        canvas.save()
        canvas.translate(pose.x, pose.y)
        canvas.rotate(pose.rotationDegrees)
        canvas.drawBitmap(atlasBitmap, source, destination, spritePaint)
        canvas.restore()
    }

    override fun performClick(): Boolean {
        super.performClick()
        controller.startTongueStrike()
        performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return handleTouchDown(event)
            MotionEvent.ACTION_MOVE -> return handleTouchMove(event)
            MotionEvent.ACTION_UP -> return handleTouchUp(event)
            MotionEvent.ACTION_CANCEL -> {
                finishTouch(cancelled = true)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleTouchDown(event: MotionEvent): Boolean {
        if (hypot(event.x - pose.x, event.y - pose.y) > drawSize * 0.48f) return false
        isTrackingTouch = true
        isDragging = false
        downX = event.x
        downY = event.y
        dragOffsetX = pose.x - event.x
        dragOffsetY = pose.y - event.y
        velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
        return true
    }

    private fun handleTouchMove(event: MotionEvent): Boolean {
        if (!isTrackingTouch) return false
        velocityTracker?.addMovement(event)
        if (!isDragging && hypot(event.x - downX, event.y - downY) > touchSlop) {
            isDragging = true
            controller.startDrag(event.x + dragOffsetX, event.y + dragOffsetY)
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        if (isDragging) controller.moveDrag(event.x + dragOffsetX, event.y + dragOffsetY)
        pose = controller.getPose()
        invalidate()
        return true
    }

    private fun handleTouchUp(event: MotionEvent): Boolean {
        if (!isTrackingTouch) return false
        velocityTracker?.addMovement(event)
        if (isDragging) {
            velocityTracker?.computeCurrentVelocity(1_000)
            controller.releaseDrag(velocityTracker?.xVelocity ?: 0f, velocityTracker?.yVelocity ?: 0f)
        } else {
            performClick()
        }
        finishTouch(cancelled = false)
        return true
    }

    private fun finishTouch(cancelled: Boolean): Unit {
        if (cancelled && isDragging) controller.releaseDrag(0f, 0f)
        isTrackingTouch = false
        isDragging = false
        recycleVelocityTracker()
    }

    private fun recycleVelocityTracker(): Unit {
        velocityTracker?.recycle()
        velocityTracker = null
    }

    private fun startAnimation(): Unit {
        if (isAnimating) return
        isAnimating = true
        lastFrameTimeNanos = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun stopAnimation(): Unit {
        isAnimating = false
        lastFrameTimeNanos = 0L
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    private companion object {
        const val SPEC_PATH: String = "pets/moki/moki_sheet_v1.json"
        const val HEADER_CLEARANCE_DP: Float = 96f
        const val DEFAULT_DRAW_SIZE_DP: Float = 70f
    }
}
