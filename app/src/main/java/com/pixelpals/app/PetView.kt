package com.pixelpals.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.DisplayMetrics
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.*
import kotlin.random.Random
import android.util.Log
import com.pixelpals.app.behavior.*
import com.pixelpals.app.motion.MotionEngine
import com.pixelpals.app.catalog.AccessoryCatalogItem
import com.pixelpals.app.status.CareAction
import com.pixelpals.app.status.PetMood
import com.pixelpals.app.status.PetPersonality
import com.pixelpals.app.status.PetStatusSnapshot

@SuppressLint("ViewConstructor")
class PetView(
    context: Context,
    override var screenWidth: Int,
    override var screenHeight: Int,
    override val petSpriteSize: Int,
    private val petType: PetType
) : View(context), PetViewBridge {
    private val progress = PetProgress(context)
    private val repository = AppServices.repository(context)
    private val analytics = AppServices.analytics(context)
    private val uiScope = CoroutineScope(Dispatchers.Main + Job())
    private var activeSecondsAccumulator = 0f
    private var ambientBubbleCooldown = 12f
    private var lastFrameTimeNanos = 0L

    private val motionEngine = MotionEngine()
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val minimumFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity.toFloat()
    private val maximumFlingVelocity = ViewConfiguration.get(context).scaledMaximumFlingVelocity.toFloat()
    private var velocityTracker: VelocityTracker? = null

    override var state = PetState.IDLE
    override var currentFrame = 0
    override var animScaleX = 1f
    override var animScaleY = 1f
    override var animOffsetX = 0f
    override var animOffsetY = 0f
    override var animRotation = 0f
    override var animAlpha = 1f
    override var animColorFilter: ColorFilter? = null
    override var petStatus: PetStatusSnapshot = PetStatusSnapshot(
        petId = petType.name.lowercase(),
        health = 92,
        energy = 78,
        hunger = 72,
        hygiene = 84,
        bond = 0,
        mood = PetMood.HAPPY,
        careStreakDays = 0,
        softCurrency = 0,
        dominantSuggestion = CareAction.CHECK_IN,
        memoriesUnlocked = 0
    )
    override val petPersonality: PetPersonality = repository.getPersonality(petType)
    override var equippedAccessory: AccessoryCatalogItem? = null
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
    private val accessoryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = petSpriteSize.toFloat() * 0.24f
        setShadowLayer(8f, 0f, 4f, Color.BLACK)
    }

    override val groundY: Int
        get() = screenHeight - petSpriteSize -
            (56f * resources.displayMetrics.density).roundToInt()

    private val behavior: PetBehavior? by lazy {
        PetBehaviorFactory.create(petType, this)
    }

    init {
        uiScope.launch {
            petStatus = repository.getStatusSnapshot(petType)
            equippedAccessory = repository.getEquippedAccessory(petType)
            showBubble(welcomeBubble())
            invalidate()
        }
    }

    private var isAnimating = false
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isAnimating) return
            if (lastFrameTimeNanos != 0L && frameTimeNanos > lastFrameTimeNanos) {
                val step = motionEngine.splitDelta((frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f)
                repeat(step.steps) { update(step.stepDt) }
            }
            lastFrameTimeNanos = frameTimeNanos
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
            petStatus = repository.recordInteraction(petType)
            analytics.track(
                "pet_interaction",
                mapOf(
                    "pet_id" to petStatus.petId,
                    "mood" to petStatus.mood.name,
                    "bond" to petStatus.bond.toString()
                )
            )
            progress.maybeAwardTreasureFromInteraction()?.let { treasure ->
                showBubble(treasure)
            } ?: run {
                if (Random.nextFloat() < 0.45f) {
                    showBubble(interactionBubble())
                }
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
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) {
            Log.w("PetView", "Failed to play haptic", e)
        }
    }
    
    override fun resumeAnimation() {
        if (!isAnimating) {
            isAnimating = true
            lastFrameTimeNanos = 0L
            Choreographer.getInstance().postFrameCallback(frameCallback)
            behavior?.resume()
        }
    }

    override fun pauseAnimation() {
        isAnimating = false
        lastFrameTimeNanos = 0L
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

    fun refreshFromRepository(message: String?, celebrate: Boolean) {
        uiScope.launch {
            petStatus = repository.getStatusSnapshot(petType)
            equippedAccessory = repository.getEquippedAccessory(petType)
            if (!message.isNullOrBlank()) showBubble(message)
            if (celebrate) {
                treasureReactionTimer = treasureReactionDuration
                playHaptic(35)
            }
            invalidate()
        }
    }

    override fun recordCareAction(action: CareAction) {
        uiScope.launch {
            petStatus = repository.applyCareAction(petType, action)
            equippedAccessory = repository.getEquippedAccessory(petType)
            showBubble(
                careActionBubble(action)
            )
            analytics.track(
                "care_action",
                mapOf(
                    "pet_id" to petStatus.petId,
                    "action" to action.name.lowercase(),
                    "mood" to petStatus.mood.name
                )
            )
        }
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
                petStatus = repository.recordActiveMinute(petType)
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
        ambientBubbleCooldown -= dt
        if (ambientBubbleCooldown <= 0f && bubbleText == null && state == PetState.IDLE) {
            maybeShowAmbientMoodBubble()
            ambientBubbleCooldown = when (petPersonality) {
                PetPersonality.CHAOTIC -> 10f
                PetPersonality.BOUNCY, PetPersonality.CURIOUS -> 14f
                else -> 18f
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
        drawAccessory(canvas)

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

    private fun drawAccessory(canvas: Canvas) {
        val accessory = equippedAccessory ?: return
        accessoryPaint.textSize = petSpriteSize * accessory.scale
        val cx = width / 2f + renderOffsetX + (accessory.offsetXRatio * petSpriteSize * if (renderScaleX >= 0f) 1f else -1f)
        val cy = height / 2f + renderOffsetY + (accessory.offsetYRatio * petSpriteSize)
        canvas.drawText(accessory.emoji, cx, cy, accessoryPaint)
    }

    private fun maybeShowAmbientMoodBubble() {
        val bubble = ambientBubbleOptions().randomOrNull()
        bubble?.let { showBubble(it) }
    }

    private fun welcomeBubble(): String {
        return when (petPersonality) {
            PetPersonality.ANGELIC -> "✨"
            PetPersonality.CHAOTIC -> "😈"
            PetPersonality.LOYAL -> "🐾"
            PetPersonality.SWEET -> "💖"
            PetPersonality.ELEGANT -> "🎀"
            PetPersonality.BOUNCY -> "🫧"
            PetPersonality.CURIOUS -> "👀"
            PetPersonality.DREAMY -> "☁️"
        }
    }

    private fun interactionBubble(): String {
        val highBond = petStatus.bond >= 35
        val options = when (petPersonality) {
            PetPersonality.ANGELIC -> listOf("✨", "🤍", if (highBond) "🪽" else "💫")
            PetPersonality.CHAOTIC -> listOf("😈", "⚡", if (highBond) "🔥" else "🎉")
            PetPersonality.LOYAL -> listOf("🐾", "💛", if (highBond) "🦴" else "✨")
            PetPersonality.SWEET -> listOf("💖", "🌸", if (highBond) "🥹" else "😊")
            PetPersonality.ELEGANT -> listOf("🎀", "✨", if (highBond) "👑" else "😌")
            PetPersonality.BOUNCY -> listOf("🫧", "🎉", if (highBond) "💥" else "😄")
            PetPersonality.CURIOUS -> listOf("👀", "🌟", if (highBond) "🪿" else "❔")
            PetPersonality.DREAMY -> listOf("☁️", "🌙", if (highBond) "💤" else "⭐")
        }
        return options.random()
    }

    private fun careActionBubble(action: CareAction): String {
        return when (action) {
            CareAction.FEED -> listOf("🍓", "🍪", "😋").random()
            CareAction.CLEAN -> listOf("🫧", "✨", "🛁").random()
            CareAction.PLAY -> listOf("🎉", "💫", "😄").random()
            CareAction.REST -> listOf("💤", "🌙", "☁️").random()
            CareAction.CHECK_IN -> listOf("💖", "✨", "😊").random()
        }
    }

    private fun ambientBubbleOptions(): List<String> {
        val moodOptions = when (petStatus.mood) {
            PetMood.HAPPY -> listOf("✨", "💛")
            PetMood.SLEEPY -> listOf("💤", "🌙")
            PetMood.HUNGRY -> listOf("🍪", "🍓")
            PetMood.DIRTY -> listOf("🫧", "💧")
            PetMood.BORED -> listOf("🎈", "❔")
            PetMood.EXCITED -> listOf("✨", "🎉", "💫")
        }
        val personalityBonus = when (petPersonality) {
            PetPersonality.ANGELIC -> listOf("🤍")
            PetPersonality.CHAOTIC -> listOf("😈")
            PetPersonality.LOYAL -> listOf("🐾")
            PetPersonality.SWEET -> listOf("🌸")
            PetPersonality.ELEGANT -> listOf("🎀")
            PetPersonality.BOUNCY -> listOf("🫧")
            PetPersonality.CURIOUS -> listOf("👀")
            PetPersonality.DREAMY -> listOf("☁️")
        }
        return moodOptions + personalityBonus
    }

    // Manejo de arrastre (drag and drop)
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var hasDragged = false

    override fun performClick(): Boolean {
        super.performClick()
        state = PetState.INTERACTING
        behavior?.onInteract()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val params = getWindowParams() ?: return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (behavior?.onTouchDown(event.rawX, event.rawY) == true) return true

                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain()
                addMovementToVelocityTracker(event)
                initialX = params.x
                initialY = params.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                hasDragged = false
                state = PetState.DRAGGING
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                addMovementToVelocityTracker(event)
                if (state == PetState.DRAGGING) {
                    if (!hasDragged) {
                        hasDragged = hypot(
                            event.rawX - initialTouchX,
                            event.rawY - initialTouchY
                        ) >= touchSlop
                    }
                    params.x = (initialX + (event.rawX - initialTouchX).toInt())
                        .coerceIn(0, (screenWidth - params.width).coerceAtLeast(0))
                    params.y = (initialY + (event.rawY - initialTouchY).toInt())
                        .coerceIn(0, (screenHeight - params.height).coerceAtLeast(0))
                    updateWindowLayout(params)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                addMovementToVelocityTracker(event)
                if (behavior?.onTouchUp() == true) {
                    recycleVelocityTracker()
                    return true
                }
                if (state == PetState.DRAGGING) {
                    if (!hasDragged) {
                        performClick()
                    } else {
                        velocityTracker?.computeCurrentVelocity(1000, maximumFlingVelocity)
                        val flingVX = velocityTracker?.xVelocity ?: 0f
                        val flingVY = velocityTracker?.yVelocity ?: 0f
                        val isFling = abs(flingVX) >= minimumFlingVelocity ||
                            abs(flingVY) >= minimumFlingVelocity
                        if (isFling) {
                            behavior?.onFling(flingVX, flingVY)
                        } else {
                            state = PetState.IDLE
                            behavior?.reset()
                        }
                    }
                }
                recycleVelocityTracker()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                state = PetState.IDLE
                behavior?.reset()
                recycleVelocityTracker()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        recycleVelocityTracker()
        behavior?.destroy()
        uiScope.cancel()
        super.onDetachedFromWindow()
    }

    private fun recycleVelocityTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }

    private fun addMovementToVelocityTracker(event: MotionEvent) {
        val tracker = velocityTracker ?: return
        val rawEvent = MotionEvent.obtain(event)
        rawEvent.offsetLocation(event.rawX - event.x, event.rawY - event.y)
        tracker.addMovement(rawEvent)
        rawEvent.recycle()
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
