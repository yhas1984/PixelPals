package com.pixelpals.app.feature.care

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.pixelpals.app.core.care.scene.*

class CareStageView(context: Context) : View(context) {
    var pack: CarePosePack? = null
        set(value) { field = value; if (value != null) scheduleFrame(); invalidate() }
    var isGentle: Boolean = false
    var onCompletion: (() -> Unit)? = null
    var onFinished: (() -> Unit)? = null
    var onTimeout: (() -> Unit)? = null
    private val renderer: CareSceneRenderer = CareSceneRenderer()
    var bedDecorationId: String?
        get() = renderer.bedDecorationId
        set(value) { renderer.bedDecorationId = value; invalidate() }
    var toyDecorationId: String?
        get() = renderer.toyDecorationId
        set(value) { renderer.toyDecorationId = value; invalidate() }
    private var controller: CareSceneController? = null
    private var lastFrame: Long = 0L
    private var didFinish: Boolean = false
    private var isStarted: Boolean = false
    private var idleTimeMs: Long = 0L
    private val preferences = com.pixelpals.app.feature.home.CompanionPreferences(context)
    var environment: com.pixelpals.app.feature.home.HomeEnvironment? = null
    private val backgroundPainter = com.pixelpals.app.feature.home.HomeScenePainter()
    private fun isMotionEnabled(): Boolean = ValueAnimator.areAnimatorsEnabled() && !preferences.reducedMotion
    private val frame: Runnable = object : Runnable {
        override fun run(): Unit {
            if (!isAttachedToWindow || !isShown) { isStarted = false; return }
            val now: Long = SystemClock.uptimeMillis()
            val delta: Long = if (lastFrame == 0L) 0L else (now - lastFrame).coerceAtMost(100L)
            lastFrame = now
            idleTimeMs += delta
            val scene: CareSceneController? = controller
            if (scene?.advance(delta) == true) onCompletion?.invoke()
            if (scene?.isCancelled == true && !didFinish) { didFinish = true; onTimeout?.invoke() }
            if (scene?.isComplete == true && !didFinish) { didFinish = true; onFinished?.invoke() }
            invalidate()
            isStarted = false
            scheduleFrame()
        }
    }

    fun start(scene: CareSceneController): Unit {
        controller = scene
        didFinish = false
        lastFrame = 0L
        scheduleFrame()
    }

    fun stop(): Unit {
        removeCallbacks(frame)
        controller?.cancel()
        controller = null
        isStarted = false
        lastFrame = 0L
        if (isAttachedToWindow && pack != null) scheduleFrame()
        invalidate()
    }

    fun sendPointer(rawX: Float, rawY: Float, isDown: Boolean): Unit {
        val scene: CareSceneController = controller ?: return
        val poses: CarePosePack = pack ?: return
        if (width <= 0 || height <= 0) return
        val location: IntArray = IntArray(2)
        getLocationOnScreen(location)
        // Do not turn out-of-bounds input into a valid edge contact.
        val point: CarePoint = CarePoint((rawX - location[0]) / width, (rawY - location[1]) / height)
        scene.movePointer(point, renderer.getTarget(poses, scene, width.toFloat(), height.toFloat(),
            if (isMotionEnabled()) scene.animationMs else 0L, !isMotionEnabled()), isDown)
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (controller?.mode != CareSceneMode.MANUAL) return super.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_CANCEL) { onTimeout?.invoke(); return true }
        parent?.requestDisallowInterceptTouchEvent(true)
        sendPointer(event.rawX, event.rawY, event.actionMasked != MotionEvent.ACTION_UP)
        if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    override fun onDraw(canvas: Canvas): Unit {
        super.onDraw(canvas)
        val poses: CarePosePack = pack ?: return
        environment?.let {
            canvas.save(); canvas.scale(width / 1000f, height / 760f)
            backgroundPainter.drawBackground(canvas, it, java.time.LocalTime.now().hour,
                pack?.spec?.atlas?.petId?.let { id -> com.pixelpals.app.core.domain.PetType.entries.firstOrNull { type -> type.name.equals(id, true) } })
            canvas.restore()
        }
        renderer.draw(canvas, poses, controller, !isMotionEnabled(), isGentle, idleTimeMs)
    }

    private fun scheduleFrame(): Unit {
        if (isStarted || pack == null || !isAttachedToWindow || !isShown) return
        isStarted = true
        if (controller != null && !didFinish) postOnAnimation(frame)
        else postDelayed(frame, if (isMotionEnabled()) 80L else 500L)
    }

    override fun onAttachedToWindow(): Unit { super.onAttachedToWindow(); scheduleFrame() }

    override fun onWindowVisibilityChanged(visibility: Int): Unit {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) scheduleFrame()
    }

    fun celebrate(): Unit {
        if (preferences.haptics) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        if (preferences.sound) playSoundEffect(android.view.SoundEffectConstants.CLICK)
    }

    override fun onDetachedFromWindow(): Unit {
        pack = null
        stop()
        // Drop references, not recycle(): a render thread may still be drawing the previous bitmap.
        pack = null
        super.onDetachedFromWindow()
    }
}
