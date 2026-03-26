package com.pixelpals.app

import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.*
import kotlin.random.Random
import android.util.Log
import com.pixelpals.app.behavior.*

class PetView(
    context: Context,
    override val screenWidth: Int,
    override val screenHeight: Int,
    override val petSpriteSize: Int,
    private val petType: PetType
) : View(context), PetViewBridge {

    override var state = PetState.IDLE
    override var currentFrame = 0
    override var animScaleX = 1f
    override var animScaleY = 1f
    override var animOffsetX = 0f
    override var animOffsetY = 0f
    override var animRotation = 0f
    override var animAlpha = 1f
    override var animColorFilter: ColorFilter? = null
    override var velocityX = 0f
    override var velocityY = 0f
    override var windowX: Int = 0
    override var windowY: Int = 0

    override val groundY get() = screenHeight - petSpriteSize - 120

    private val behavior: PetBehavior? by lazy {
        PetBehaviorFactory.create(petType, this)
    }

    private var isAnimating = false
    private val frameCallback = object : Choreographer.FrameCallback {
        private var lastFrameTime = 0L
        override fun doFrame(frameTimeNanos: Long) {
            if (!isAnimating) return
            
            if (lastFrameTime != 0L) {
                val dt = (frameTimeNanos - lastFrameTime) / 1_000_000_000f
                update(dt)
            }
            lastFrameTime = frameTimeNanos
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun getWindowParams(): WindowManager.LayoutParams? = layoutParams as? WindowManager.LayoutParams
    
    override fun updateWindowLayout(params: WindowManager.LayoutParams) {
        try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.updateViewLayout(this, params)
            windowX = params.x
            windowY = params.y
        } catch (e: Exception) {
            Log.w("PetView", "Failed to update window layout", e)
        }
    }

    override fun showBubble(text: String) {
        // Implementación básica
    }

    override fun hideBubble() {
        // Implementación básica
    }

    override fun teleportToRandomEdge() {
        val params = getWindowParams() ?: return
        params.x = if (Random.nextBoolean()) 20 else screenWidth - petSpriteSize - 20
        params.y = Random.nextInt(100, screenHeight - petSpriteSize - 200)
        updateWindowLayout(params)
    }

    override fun trackInteraction() {
        // Implementación básica
    }
    
    override fun playHaptic(durationMs: Long) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            }
        } catch (e: Exception) {
            Log.w("PetView", "Failed to play haptic", e)
        }
    }
    
    override fun resumeAnimation() {
        if (!isAnimating) {
            isAnimating = true
            Choreographer.getInstance().postFrameCallback(frameCallback)
            behavior?.resume()
        }
    }

    override fun pauseAnimation() {
        isAnimating = false
        behavior?.pause()
    }

    override fun setProgress(progress: PetProgress) {
        // Actualizar UI según progreso si es necesario
    }

    override fun consumeTreasure(emoji: String) {
        behavior?.onTreasureConsumed(emoji)
    }

    override fun onBatteryChanged(percent: Int, isCharging: Boolean) {
        behavior?.onBatteryStatusChanged(percent, isCharging)
    }

    override fun onKeyboardChanged(visible: Boolean, height: Int) {
        behavior?.onKeyboardVisibilityChanged(visible, height)
    }

    override fun onAirplaneModeChanged(isAirplane: Boolean) {
        behavior?.onAirplaneModeChanged(isAirplane)
    }
    
    private fun update(dt: Float) {
        when (state) {
            PetState.IDLE -> behavior?.updateIdle(dt)
            PetState.DRAGGING -> behavior?.updateDrag(dt)
            PetState.FALLING -> behavior?.updateFalling(dt)
            PetState.JUMPING -> behavior?.updateJumping(dt)
            PetState.INTERACTING -> behavior?.updateInteracting(dt)
            else -> behavior?.updateAutonomous(dt)
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        behavior?.onDraw(canvas, (width / 2).toFloat(), (height / 2).toFloat())
    }

    // Manejo de arrastre (drag and drop)
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    override fun performClick(): Boolean {
        super.performClick()
        behavior?.onInteract()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val params = getWindowParams() ?: return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (behavior?.onTouchDown(event.rawX, event.rawY) == true) return true
                
                initialX = params.x
                initialY = params.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                state = PetState.DRAGGING
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (state == PetState.DRAGGING) {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    updateWindowLayout(params)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (behavior?.onTouchUp() == true) return true
                
                if (state == PetState.DRAGGING) {
                    val dist = hypot(event.rawX - initialTouchX, event.rawY - initialTouchY)
                    if (dist < 15) { // Threshold para considerar click
                        performClick()
                        // No entramos en FALLING si es un click
                    } else {
                        state = PetState.FALLING
                    }
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
