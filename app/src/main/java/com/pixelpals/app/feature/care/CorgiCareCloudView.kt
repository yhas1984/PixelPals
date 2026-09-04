package com.pixelpals.app.feature.care

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.pixelpals.app.R
import com.pixelpals.app.core.care.scene.CareSceneAction
import com.pixelpals.app.core.domain.PetType
import kotlin.math.sin

/** Only available controls exist in the view tree. Labels are spoken, never drawn. */
internal class CorgiCareCloudView(
    context: Context,
    pet: PetType,
    actions: List<CareSceneAction>,
    private val onAction: (CareSceneAction) -> Unit,
    private val onDismiss: () -> Unit,
) : FrameLayout(context) {
    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline: Path = Path()
    private val surface: Int = ContextCompat.getColor(context, R.color.surface_base)
    private val stroke: Int = ContextCompat.getColor(context, R.color.stroke_soft)
    private var tailX: Float = dp(76).toFloat()
    private var tailAtTop: Boolean = false
    private var visibleActions: List<CareSceneAction> = emptyList()
    private val controls: Map<CareSceneAction, View> = CareSceneAction.entries.associateWith { action ->
        CloudActionView(context, action, pet).apply { setOnClickListener { onAction(action) } }
    }
    private val animation: Runnable = object : Runnable {
        override fun run(): Unit {
            if (!isAttachedToWindow || !ValueAnimator.areAnimatorsEnabled()) return
            for (index: Int in 0 until childCount) getChildAt(index).invalidate()
            postDelayed(this, 50L)
        }
    }

    init {
        setWillNotDraw(false)
        clipChildren = false
        clipToPadding = false
        updateActions(actions)
    }

    fun updateActions(actions: List<CareSceneAction>): Unit {
        if (visibleActions == actions) return
        controls.filterKeys { it !in actions }.values.forEach { removeView(it) }
        visibleActions = actions.toList()
        actions.forEachIndexed { index, action ->
            val control: View = controls.getValue(action)
            if (control.parent == null) addView(control, index)
            val rowCount: Int = minOf(3, actions.size - index / 3 * 3)
            control.layoutParams = LayoutParams(dp(48), dp(48)).apply {
                leftMargin = dp(4 + (3 - rowCount) * 24 + index % 3 * 48)
                topMargin = dp((if (tailAtTop) 30 else 10) + index / 3 * 48)
            }
        }
        invalidate()
    }

    fun pointTo(x: Float, fromTop: Boolean): Unit {
        tailX = x.coerceIn(dp(12).toFloat(), (width - dp(12)).coerceAtLeast(dp(12)).toFloat())
        if (tailAtTop != fromTop) {
            tailAtTop = fromTop
            for (index: Int in 0 until childCount) {
                val params: LayoutParams = getChildAt(index).layoutParams as LayoutParams
                params.topMargin = dp((if (fromTop) 30 else 10) + index / 3 * 48)
                getChildAt(index).layoutParams = params
            }
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas): Unit {
        super.onDraw(canvas)
        val w: Float = width.toFloat()
        val h: Float = dp(114).toFloat()
        canvas.save()
        if (tailAtTop) canvas.translate(0f, dp(20).toFloat())
        outline.reset()
        outline.moveTo(w * .18f, h * .92f)
        outline.cubicTo(w * .01f, h * .97f, -w * .01f, h * .48f, w * .11f, h * .36f)
        outline.cubicTo(w * .09f, h * .04f, w * .29f, -h * .03f, w * .41f, h * .14f)
        outline.cubicTo(w * .50f, -h * .04f, w * .68f, h * .01f, w * .72f, h * .20f)
        outline.cubicTo(w * .91f, h * .05f, w * 1.01f, h * .34f, w * .95f, h * .56f)
        outline.cubicTo(w * 1.03f, h * .91f, w * .90f, h * 1.03f, w * .76f, h * .93f)
        outline.cubicTo(w * .63f, h * 1.06f, w * .40f, h * .95f, w * .30f, h * .95f)
        outline.cubicTo(w * .26f, h * 1.02f, w * .20f, h, w * .18f, h * .92f)
        outline.close()
        paint.color = surface
        paint.style = Paint.Style.FILL
        canvas.drawPath(outline, paint)
        paint.color = stroke
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = context.resources.displayMetrics.density
        canvas.drawPath(outline, paint)
        val dotY: Float = if (tailAtTop) -dp(5).toFloat() else h + dp(5)
        drawDot(canvas, tailX, dotY, dp(4).toFloat())
        drawDot(canvas, tailX + dp(3), if (tailAtTop) -dp(15).toFloat() else h + dp(15), dp(2).toFloat())
        canvas.restore()
    }

    private fun drawDot(canvas: Canvas, x: Float, y: Float, radius: Float): Unit {
        paint.color = surface
        paint.style = Paint.Style.FILL
        canvas.drawCircle(x, y, radius, paint)
        paint.color = stroke
        paint.style = Paint.Style.STROKE
        canvas.drawCircle(x, y, radius, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) { onDismiss(); return true }
        return super.onTouchEvent(event)
    }

    override fun onAttachedToWindow(): Unit { super.onAttachedToWindow(); post(animation) }
    override fun onDetachedFromWindow(): Unit { removeCallbacks(animation); super.onDetachedFromWindow() }
    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}

private class CloudActionView(
    context: Context,
    private val action: CareSceneAction,
    private val pet: PetType,
) : View(context) {
    private val painter: CarePropPainter = CarePropPainter()
    init {
        contentDescription = context.getString(when (action) {
            CareSceneAction.FEED -> R.string.action_feed
            CareSceneAction.PLAY -> R.string.action_play
            CareSceneAction.PET -> R.string.care_scene_pet
            CareSceneAction.CLEAN -> R.string.action_clean
            CareSceneAction.REST -> R.string.action_rest
            CareSceneAction.MEDICINE -> R.string.action_medicine
        })
        isClickable = true
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        background = RippleDrawable(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.surface_tinted)),
            null, GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) })
    }

    override fun getAccessibilityClassName(): CharSequence = "android.widget.Button"

    override fun onDraw(canvas: Canvas): Unit {
        super.onDraw(canvas)
        val phase: Float = if (ValueAnimator.areAnimatorsEnabled())
            sin(SystemClock.uptimeMillis() / 340f + action.ordinal * 1.5f) else 0f
        val size: Float = minOf(width, height) * .64f
        canvas.save()
        val x: Float = width / 2f
        val y: Float = height / 2f
        when (action) {
            CareSceneAction.FEED -> canvas.rotate(phase * 5f, x, y)
            CareSceneAction.PLAY -> canvas.translate(0f, phase * size * .08f)
            CareSceneAction.PET -> canvas.rotate(phase * 12f, x, y + size * .35f)
            CareSceneAction.CLEAN -> canvas.translate(phase * size * .07f, 0f)
            CareSceneAction.REST -> canvas.scale(1f, 1f + phase * .06f, x, y)
            CareSceneAction.MEDICINE -> canvas.rotate(phase * 7f, x, y)
        }
        painter.draw(canvas, action, x, y, size, pet = pet)
        canvas.restore()
    }
}
