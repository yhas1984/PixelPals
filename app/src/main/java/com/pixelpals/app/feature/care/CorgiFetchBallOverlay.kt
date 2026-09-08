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
    val toyDecorationId: String? = null,
    val alpha: Float = 1f,
) {
    companion object {
        fun fromPose(plan: CorgiFetchPlan, pose: CorgiFetchPose, anchors: CarePoseAnchors): CorgiFetchFrame {
            val held: Boolean = pose.isCaught || plan.reducedMotion
            val mouth: CarePoint = CarePoint(
                pose.petX + plan.spriteSize * .5f + plan.direction * (anchors.mouth.x - .5f) * plan.spriteSize * com.pixelpals.app.core.motion.CorgiArtworkScale.CARE_CELL,
                pose.petY + plan.spriteSize * .96f + (anchors.mouth.y - anchors.ground.y) * plan.spriteSize * com.pixelpals.app.core.motion.CorgiArtworkScale.CARE_CELL,
            )
            // Arrive at the actual scaled mouth before changing ownership to a held prop.
            // A fixed rolling endpoint otherwise teleports when the care camera changes.
            val pickup: Float = if (held) 1f else pose.pickupProgress
            val ball: CarePoint = CarePoint(
                pose.ballX + (mouth.x - pose.ballX) * pickup,
                if (pose.releaseSeconds >= 0f) com.pixelpals.app.core.care.scene.CorgiBallRelease.height(
                    mouth.y, pose.petY + plan.spriteSize * .86f, pose.releaseSeconds)
                else pose.ballY + (mouth.y - pose.ballY) * pickup,
            )
            return CorgiFetchFrame(CarePoint(pose.petX, pose.petY), ball, pose.regularFrame,
                plan.direction < 0f, if (plan.reducedMotion) 0f else pose.ballRotation, alpha = pose.ballAlpha)
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
        params.alpha = .8f * frame.alpha.coerceIn(0f, 1f)
        view.ballRotation = frame.rotation
        view.toyDecorationId = frame.toyDecorationId
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
        var toyDecorationId: String? = null
        init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
        override fun onDraw(canvas: Canvas): Unit {
            super.onDraw(canvas)
            canvas.save()
            canvas.rotate(ballRotation, width / 2f, height / 2f)
            painter.toyDecorationId = toyDecorationId
            painter.draw(canvas, CareSceneAction.PLAY, width / 2f, height / 2f, width * .86f)
            canvas.restore()
        }
    }
}
