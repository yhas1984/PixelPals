package com.pixelpals.app

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.DisplayMetrics
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.*
import kotlin.random.Random
import android.util.Log
import com.pixelpals.app.behavior.*
import org.json.JSONObject

class PetView(
    context: Context,
    override var screenWidth: Int,
    override var screenHeight: Int,
    override val petSpriteSize: Int,
    private val petType: PetType
) : View(context), PetViewBridge {
    private val progress = PetProgress(context)
    private val uiScope = CoroutineScope(Dispatchers.Main + Job())
    private var activeSecondsAccumulator = 0f

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
    private var treasureEffectScaleX = 1f
    private var treasureEffectScaleY = 1f
    private var treasureEffectOffsetX = 0f
    private var treasureEffectOffsetY = 0f
    private var treasureEffectRotation = 0f
    override val renderScaleX: Float get() = animScaleX * treasureEffectScaleX
    override val renderScaleY: Float get() = animScaleY * treasureEffectScaleY
    override val renderOffsetX: Float get() = animOffsetX + treasureEffectOffsetX
    override val renderOffsetY: Float get() = animOffsetY + treasureEffectOffsetY
    override val renderRotation: Float get() = animRotation + treasureEffectRotation
    override var velocityX = 0f
    override var velocityY = 0f
    override var windowX: Int = 0
    override var windowY: Int = 0

    // Emoji bubble (emoticons de avisos/interacción).
    private var bubbleText: String? = null
    private var bubbleTimer: Float = 0f
    private var bubbleAlpha: Float = 0f
    private val bubbleDurationMs = 2200f
    private var treasureReactionTimer = 0f
    private val treasureReactionDuration = 0.95f
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
        progress.trackInteraction()
        uiScope.launch {
            progress.maybeAwardTreasureFromInteraction()?.let { treasure ->
                showBubble(treasure)
            }
        }
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
        // Actualmente PetView mantiene su propio progreso persistente.
    }

    override fun consumeTreasure(emoji: String) {
        showBubble(emoji)
        playHaptic(35)
        treasureReactionTimer = treasureReactionDuration
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
        refreshScreenMetrics()
        activeSecondsAccumulator += dt
        while (activeSecondsAccumulator >= 60f) {
            progress.trackMinute()
            activeSecondsAccumulator -= 60f
            uiScope.launch {
                progress.maybeAwardTreasureFromActiveMinute()?.let { treasure ->
                    showBubble(treasure)
                }
            }
        }

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

        if (treasureReactionTimer > 0f) {
            treasureReactionTimer = (treasureReactionTimer - dt).coerceAtLeast(0f)
            val progress = 1f - (treasureReactionTimer / treasureReactionDuration)
            val bounce = abs(sin(progress * PI.toFloat() * 3f))
            treasureEffectScaleX = 1f + bounce * 0.08f
            treasureEffectScaleY = 1f - bounce * 0.06f
            treasureEffectOffsetX = sin(progress * PI.toFloat() * 4f) * 2f
            treasureEffectOffsetY = -bounce * 6f
            treasureEffectRotation = sin(progress * PI.toFloat() * 2f) * 3f
        } else {
            treasureEffectScaleX = 1f
            treasureEffectScaleY = 1f
            treasureEffectOffsetX = 0f
            treasureEffectOffsetY = 0f
            treasureEffectRotation = 0f
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
                        // Soltar tras arrastrar debe devolver la mascota a un estado estable
                        // en su nueva posición; FALLING dejaba varios behaviors bloqueados.
                        state = PetState.IDLE
                        behavior?.reset()
                    }
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                state = PetState.IDLE
                behavior?.reset()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        behavior?.destroy()
        uiScope.cancel()
        super.onDetachedFromWindow()
    }

    private fun refreshScreenMetrics() {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            screenWidth = bounds.width()
            screenHeight = bounds.height()
        } else {
            @Suppress("DEPRECATION")
            val metrics = DisplayMetrics().also { wm.defaultDisplay.getMetrics(it) }
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
        }
    }
}
