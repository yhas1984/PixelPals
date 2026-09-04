package com.pixelpals.app.feature.care

import android.content.Context
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.pixelpals.app.core.care.scene.CarePoint
import com.pixelpals.app.core.care.scene.CareSceneAction
import com.pixelpals.app.core.care.scene.CorgiFetchPlan
import com.pixelpals.app.core.care.scene.CorgiFetchPose
import kotlin.math.roundToInt

data class CorgiFetchFrame(
    val pet: CarePoint,
    val ball: CarePoint,
    val regularFrame: Int?,
    val facingLeft: Boolean,
    val rotation: Float,
) {
    companion object {
        fun fromPose(plan: CorgiFetchPlan, pose: CorgiFetchPose, anchors: CarePoseAnchors): CorgiFetchFrame {
            val held: Boolean = pose.isCaught || plan.reducedMotion
            val ball: CarePoint = if (held) CarePoint(
                pose.petX + plan.spriteSize * .5f + plan.direction * (anchors.mouth.x - .5f) * plan.spriteSize * .94f,
                pose.petY + plan.spriteSize * .96f + (anchors.mouth.y - anchors.ground.y) * plan.spriteSize * .94f,
            ) else CarePoint(pose.ballX, pose.ballY)
            return CorgiFetchFrame(CarePoint(pose.petX, pose.petY), ball, pose.regularFrame,
                plan.direction < 0f, if (held) 0f else pose.ballRotation)
        }
    }
}

/** Only the ball has another window. It cannot capture touches or input focus. */
class CorgiFetchBallOverlay(context: Context, private val windowManager: WindowManager, spriteSize: Int) {
    private val size: Int = (spriteSize * .30f).roundToInt().coerceAtLeast(1)
    private val view: BallView = BallView(context)
    private var attached: Boolean = false
    private val params: WindowManager.LayoutParams = WindowManager.LayoutParams(
        size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        alpha = .8f
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
    }

    fun render(frame: CorgiFetchFrame?): Boolean {
        if (frame == null) { close(); return true }
        params.x = (frame.ball.x - size / 2f).roundToInt()
        params.y = (frame.ball.y - size / 2f).roundToInt()
        view.ballRotation = frame.rotation
        return try {
            if (!attached) {
                windowManager.addView(view, params)
                attached = true
            } else windowManager.updateViewLayout(view, params)
            view.invalidate()
            true
        } catch (exception: RuntimeException) {
            Log.w("CorgiFetchBall", "Unable to display fetch prop", exception)
            close()
            false
        }
    }

    fun close(): Unit {
        if (!attached) return
        attached = false
        try {
            if (view.isAttachedToWindow) windowManager.removeViewImmediate(view)
        } catch (exception: IllegalArgumentException) {
            Log.w("CorgiFetchBall", "Fetch prop already detached", exception)
        }
    }

    private class BallView(context: Context) : View(context) {
        private val painter: CarePropPainter = CarePropPainter()
        var ballRotation: Float = 0f
        init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
        override fun onDraw(canvas: Canvas): Unit {
            super.onDraw(canvas)
            canvas.save()
            canvas.rotate(ballRotation, width / 2f, height / 2f)
            painter.draw(canvas, CareSceneAction.PLAY, width / 2f, height / 2f, width * .86f)
            canvas.restore()
        }
    }
}
