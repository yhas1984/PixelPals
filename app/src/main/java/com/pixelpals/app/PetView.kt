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
import org.json.JSONObject

class PetView(
    context: Context,
    override val screenWidth: Int,
    override val screenHeight: Int,
    override val petSpriteSize: Int,
    private val petType: PetType
) : View(context), PetViewBridge {
    private fun debugLog(runId: String, hypothesisId: String, location: String, message: String, data: JSONObject) {
        // #region agent log
        try {
            val payload = JSONObject().apply {
                put("sessionId", "a40953")
                put("runId", runId)
                put("hypothesisId", hypothesisId)
                put("location", location)
                put("message", message)
                put("data", data)
                put("timestamp", System.currentTimeMillis())
            }
            Log.i("AGENT_DEBUG", payload.toString())
        } catch (_: Exception) {}
        // #endregion
    }

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

    // Emoji bubble (emoticons de avisos/interacción).
    private var bubbleText: String? = null
    private var bubbleTimer: Float = 0f
    private var bubbleAlpha: Float = 0f
    private val bubbleDurationMs = 2200f
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = petSpriteSize.toFloat() * 0.24f
    }

    private val bubbleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = petSpriteSize.toFloat() * 0.06f
        textAlign = Paint.Align.CENTER
        textSize = petSpriteSize.toFloat() * 0.24f
    }

    private val bubbleShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        setShadowLayer(6f, 0f, 4f, Color.BLACK)
        textAlign = Paint.Align.CENTER
        textSize = petSpriteSize.toFloat() * 0.24f
    }

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
        bubbleText = text
        bubbleTimer = bubbleDurationMs
        bubbleAlpha = 1f
    }

    override fun hideBubble() {
        bubbleText = null
        bubbleTimer = 0f
        bubbleAlpha = 0f
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
        // bubbleTimer está en ms conceptualmente; dt viene en segundos.
        if (bubbleTimer > 0f) {
            bubbleTimer -= dt * 1000f
            bubbleAlpha = (bubbleTimer / bubbleDurationMs).coerceIn(0f, 1f)
            if (bubbleTimer <= 0f) {
                bubbleText = null
                bubbleAlpha = 0f
            }
        }
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

        // Dibuja el bubble encima del pet.
        val text = bubbleText ?: return
        if (bubbleAlpha <= 0f) return

        bubblePaint.alpha = (255 * bubbleAlpha).toInt()
        bubbleStrokePaint.alpha = bubblePaint.alpha
        bubbleShadowPaint.alpha = bubblePaint.alpha
        val cx = width / 2f
        // Posición del bubble dentro del canvas (antes podía quedar negativo si petSpriteSize ~ viewHeight).
        val desiredCy = height / 2f - petSpriteSize * 0.28f
        val minCy = bubblePaint.textSize * 1.2f
        val maxCy = height - bubblePaint.textSize * 0.6f
        val cy = desiredCy.coerceIn(minCy, maxCy)

        canvas.drawText(text, cx, cy, bubbleStrokePaint)
        canvas.drawText(text, cx, cy, bubblePaint)
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
                debugLog(
                    runId = "post-fix",
                    hypothesisId = "H2",
                    location = "PetView.kt:onTouchEvent",
                    message = "Touch down capturado por overlay",
                    data = JSONObject().apply {
                        put("rawX", event.rawX)
                        put("rawY", event.rawY)
                        put("viewWidth", width)
                        put("viewHeight", height)
                        put("paramsX", params.x)
                        put("paramsY", params.y)
                        put("screenWidth", screenWidth)
                        put("screenHeight", screenHeight)
                    }
                )
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
                    debugLog(
                        runId = "post-fix",
                        hypothesisId = "H3",
                        location = "PetView.kt:onTouchEvent",
                        message = "Touch up y distancia drag",
                        data = JSONObject().apply {
                            put("dragDistance", dist)
                            put("treatedAsClick", dist < 15f)
                            put("finalX", params.x)
                            put("finalY", params.y)
                        }
                    )
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
