package com.pixelpals.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.RectF
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.pixelpals.app.feature.overlay.behavior.TelaSilkState
import com.pixelpals.app.feature.overlay.behavior.TelaCornerWebState
import com.pixelpals.app.feature.overlay.behavior.TelaWebCorner
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Owns the transparent, non-touchable window used to draw Tela's silk thread. */
class TelaWebOverlayController(
    context: Context,
    private val windowManager: WindowManager,
    private var screenWidth: Int,
    private var screenHeight: Int,
) {
    private val density = context.resources.displayMetrics.density
    private val paddingPx = (12f * density).toInt().coerceAtLeast(4)
    private val view = TelaWebView(context)
    private var attached = false
    private var visible = true
    private var params = createParams()
    private var silkState: TelaSilkState? = null
    private var cornerWebState: TelaCornerWebState? = null

    fun render(state: TelaSilkState?) {
        silkState = state
        renderCurrent()
    }

    fun renderCornerWeb(state: TelaCornerWebState?) {
        cornerWebState = state
        renderCurrent()
    }

    /** Recomputes the overlay viewport after rotation or display-size changes. */
    fun updateViewport(width: Int, height: Int) {
        screenWidth = width.coerceAtLeast(1)
        screenHeight = height.coerceAtLeast(1)
        renderCurrent()
    }

    private fun renderCurrent() {
        if (!visible || (silkState == null && cornerWebState == null)) {
            view.setState(null, null)
            hideView()
            return
        }

        var left = screenWidth.toFloat()
        var top = screenHeight.toFloat()
        var right = 0f
        var bottom = 0f

        fun include(boundsLeft: Float, boundsTop: Float, boundsRight: Float, boundsBottom: Float) {
            left = min(left, boundsLeft)
            top = min(top, boundsTop)
            right = max(right, boundsRight)
            bottom = max(bottom, boundsBottom)
        }

        silkState?.let { state ->
            include(
                min(state.anchorX, state.targetX) - paddingPx,
                min(state.anchorY, state.targetY) - paddingPx,
                max(state.anchorX, state.targetX) + paddingPx,
                max(state.anchorY, state.targetY) + paddingPx,
            )
        }
        cornerWebState?.let { state ->
            include(
                state.centerX - state.radius - paddingPx,
                state.centerY - state.radius - paddingPx,
                state.centerX + state.radius + paddingPx,
                state.centerY + state.radius + paddingPx,
            )
        }

        val clampedLeft = floor(left).toInt().coerceIn(0, (screenWidth - 1).coerceAtLeast(0))
        val clampedTop = floor(top).toInt().coerceIn(0, (screenHeight - 1).coerceAtLeast(0))
        val clampedRight = ceil(right).toInt().coerceIn(clampedLeft + 1, screenWidth)
        val clampedBottom = ceil(bottom).toInt().coerceIn(clampedTop + 1, screenHeight)

        params.x = clampedLeft
        params.y = clampedTop
        params.width = (clampedRight - clampedLeft).coerceAtLeast(1)
        params.height = (clampedBottom - clampedTop).coerceAtLeast(1)
        view.setGeometry(clampedLeft, clampedTop)
        view.setState(silkState, cornerWebState)
        showView()
        if (attached) {
            runCatching { windowManager.updateViewLayout(view, params) }
        }
    }

    fun setVisible(isVisible: Boolean) {
        visible = isVisible
        if (!visible) {
            view.setState(null, null)
            hideView()
        } else {
            renderCurrent()
        }
    }

    fun destroy() {
        silkState = null
        cornerWebState = null
        view.setState(null, null)
        if (attached) {
            runCatching { windowManager.removeViewImmediate(view) }
            attached = false
        }
    }

    private fun createParams() = WindowManager.LayoutParams(
        1,
        1,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        android.graphics.PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            setFitInsetsTypes(0)
            setFitInsetsSides(0)
        }
        x = 0
        y = 0
    }

    private fun showView() {
        view.visibility = View.VISIBLE
        if (attached) return
        runCatching {
            windowManager.addView(view, params)
            attached = true
        }
    }

    private fun hideView() {
        if (attached) view.visibility = View.GONE
    }
}

private class TelaWebView(context: Context) : View(context) {
    private val silkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(244, 222, 255)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 246, 255)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val knotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(225, 171, 255)
        style = Paint.Style.FILL
    }
    private var state: TelaSilkState? = null
    private var cornerWeb: TelaCornerWebState? = null
    private var originX = 0
    private var originY = 0
    private val silkPath = Path()

    fun setGeometry(left: Int, top: Int) {
        originX = left
        originY = top
    }

    fun setState(nextSilk: TelaSilkState?, nextCornerWeb: TelaCornerWebState?) {
        state = nextSilk
        cornerWeb = nextCornerWeb
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        state?.let { silk ->
            val alpha = (silk.alpha.coerceIn(0f, 1f) * 255f).toInt()
            val anchorX = silk.anchorX - originX
            val anchorY = silk.anchorY - originY
            val targetX = silk.targetX - originX
            val targetY = silk.targetY - originY
            val sway = silk.sway

            silkPaint.alpha = alpha
            silkPaint.strokeWidth = 2.2f * resources.displayMetrics.density
            silkPath.reset()
            silkPath.apply {
                moveTo(anchorX, anchorY)
                cubicTo(
                    anchorX + sway * 0.18f,
                    anchorY + (targetY - anchorY) * 0.32f,
                    targetX + sway * 0.82f,
                    anchorY + (targetY - anchorY) * 0.68f,
                    targetX,
                    targetY,
                )
            }
            canvas.drawPath(silkPath, silkPaint)

            highlightPaint.alpha = (alpha * 0.72f).toInt()
            highlightPaint.strokeWidth = 0.75f * resources.displayMetrics.density
            canvas.save()
            canvas.translate(1.3f * resources.displayMetrics.density, 0f)
            canvas.drawPath(silkPath, highlightPaint)
            canvas.restore()

            knotPaint.alpha = alpha
            canvas.drawCircle(anchorX, anchorY, 3.2f * resources.displayMetrics.density, knotPaint)
            canvas.drawCircle(targetX, targetY, 2.2f * resources.displayMetrics.density, knotPaint)
        }

        drawCornerWeb(canvas)
    }

    private fun drawCornerWeb(canvas: Canvas) {
        val web = cornerWeb ?: return
        val density = resources.displayMetrics.density
        val alpha = (web.alpha.coerceIn(0f, 1f) * 255f).toInt()
        val centerX = web.centerX - originX
        val centerY = web.centerY - originY
        val radius = web.radius.coerceAtLeast(1f)
        val startAngle = when (web.corner) {
            TelaWebCorner.TOP_LEFT -> 0f
            TelaWebCorner.TOP_RIGHT -> 90f
            TelaWebCorner.BOTTOM_RIGHT -> 180f
            TelaWebCorner.BOTTOM_LEFT -> 270f
        }

        silkPaint.alpha = alpha
        silkPaint.strokeWidth = 1.5f * density
        for (ring in 1..4) {
            val ringRadius = radius * ring / 4f
            canvas.drawArc(
                RectF(
                    centerX - ringRadius,
                    centerY - ringRadius,
                    centerX + ringRadius,
                    centerY + ringRadius,
                ),
                startAngle,
                90f,
                false,
                silkPaint,
            )
        }
        for (spoke in 0..6) {
            val angle = Math.toRadians((startAngle + 90f * spoke / 6f).toDouble())
            canvas.drawLine(
                centerX,
                centerY,
                centerX + cos(angle).toFloat() * radius,
                centerY + sin(angle).toFloat() * radius,
                silkPaint,
            )
        }

        highlightPaint.alpha = (alpha * 0.72f).toInt()
        highlightPaint.strokeWidth = 0.55f * density
        for (ring in 1..4) {
            val ringRadius = radius * ring / 4f
            canvas.drawArc(
                RectF(
                    centerX - ringRadius + density,
                    centerY - ringRadius,
                    centerX + ringRadius + density,
                    centerY + ringRadius,
                ),
                startAngle,
                90f,
                false,
                highlightPaint,
            )
        }
    }
}
