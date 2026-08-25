package com.pixelpals.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.DisplayMetrics
import android.util.Log
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.pixelpals.app.core.analytics.AnalyticsTracker
import com.pixelpals.app.core.domain.PetState
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.MotionEngine
import com.pixelpals.app.core.motion.PetBounds
import com.pixelpals.app.core.motion.PetGestureConfig
import com.pixelpals.app.core.motion.PetGestureRecognizer
import com.pixelpals.app.core.motion.PetGestureType
import com.pixelpals.app.core.motion.PetPhysics
import com.pixelpals.app.core.motion.PhysicsBody
import com.pixelpals.app.core.motion.PhysicsEvent
import com.pixelpals.app.core.motion.PhysicsProfile
import com.pixelpals.app.core.services.AppServices
import com.pixelpals.app.data.repository.PetProgress
import com.pixelpals.app.data.repository.PixelPalsRepository
import com.pixelpals.app.feature.overlay.behavior.*
import com.pixelpals.app.status.CareAction
import com.pixelpals.app.status.PetMood
import com.pixelpals.app.status.PetPersonality
import com.pixelpals.app.status.PetStatusSnapshot
import com.pixelpals.app.feature.treasure.TreasureDiscoveryResult
import com.pixelpals.app.notifications.PetCareNotificationManager
import com.pixelpals.app.notifications.PetCareNotificationScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.*
import kotlin.random.Random

@SuppressLint("ViewConstructor")
class PetView(
    context: Context,
    override var screenWidth: Int,
    override var screenHeight: Int,
    override val petSpriteSize: Int,
    private val petType: PetType,
    private val onTelaSilkChanged: (com.pixelpals.app.feature.overlay.behavior.TelaSilkState?) -> Unit = {},
    private val onTelaCornerWebChanged: (com.pixelpals.app.feature.overlay.behavior.TelaCornerWebState?) -> Unit = {},
) : View(context), PetViewBridge {
    private val progress = PetProgress(context)
    private val repository: PixelPalsRepository = AppServices.repository(context)
    private val analytics: AnalyticsTracker = AppServices.analytics(context)
    private val uiScope = CoroutineScope(Dispatchers.Main + Job())
    private var activeSecondsAccumulator = 0f
    private var ambientBubbleCooldown = 12f
    private var lastFrameTimeNanos = 0L
    private var lastTimeWindowKey = ""
    private var lastTimeGreetingCheckAt = 0L

    private val motionEngine = MotionEngine()
    private var physicsBody: PhysicsBody? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val minimumFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity.toFloat()
    private val maximumFlingVelocity = ViewConfiguration.get(context).scaledMaximumFlingVelocity.toFloat()
    private val gestureRecognizer = PetGestureRecognizer(
        PetGestureConfig(
            touchSlopPx = touchSlop,
            minimumFlingVelocityPxPerSecond = minimumFlingVelocity,
            longPressTimeoutMillis = ViewConfiguration.getLongPressTimeout().toLong(),
        )
    )
    private var velocityTracker: VelocityTracker? = null
    private var isTouchPending = false
    private var behaviorOwnsTouch = false
    private val holdRunnable: Runnable = Runnable {
        if (!isTouchPending) return@Runnable
        val gesture = gestureRecognizer.onTime(android.os.SystemClock.uptimeMillis())
        if (gesture.type != PetGestureType.HOLD_STARTED) return@Runnable
        isTouchPending = false
        state = PetState.INTERACTING
        behavior?.onHold()
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
    override var cosmeticColorFilter: ColorFilter? = null
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

    /**
     * Normaliza el tamaño visible de todos los pets al de Moki (referencia):
     * cada sprite ocupa un % distinto del alto de su frame (Moki perch 80%,
     * Corgi idle 81%, Jelly idle 62%...), así que sin esto se ven de tamaños
     * distintos en pantalla. Factor = occMoki / occIdleDelPet (0.802 / occ).
     * Solo afecta al DIBUJO. Ocupaciones medidas con bbox de alfa (PIL).
     */
    override val spriteScale: Float = when (petType) {
        PetType.MOKI -> 1.000f          // referencia: idle perch 0.802
        PetType.CORGI -> 0.996f         // idle REST corgi_6 0.805 -> 0.802
        PetType.JELLY -> 1.302f         // idle jelly_0 0.616 -> 0.802
        PetType.BLOOP -> 1.112f         // idle fantasma_1 0.721 -> 0.802
        PetType.NUBE_MICHI -> 1.149f    // idle gato_0 0.698 -> 0.802
        PetType.ANGEL -> 0.875f         // hover/prayer (celda sheet) 0.917 -> 0.802
        PetType.GINGER -> 0.875f        // sit/groom (celda sheet) 0.917 -> 0.802
        PetType.DIABLILLO -> 0.929f     // idle 0.863 -> 0.802
        PetType.PATITO -> 1.317f        // ciclo idle 0.36-0.60 (irregular por diseño)
        PetType.YUKI -> 0.901f          // idle alto Pixar opaco 0.891 -> 0.802
        PetType.PIRU -> 0.881f          // idle pingüino Pixar 0.910 -> 0.802
        PetType.TARO -> 0.929f          // idle tortuga (hoja nueva) 0.863 -> 0.802
        PetType.MENTA -> 0.908f         // idle serpiente Pixar opaco 0.883 -> 0.802
        PetType.TELA -> 0.880f          // idle araña Pixar 0.910 -> 0.802
        PetType.LUMI -> 0.963f          // idle Lumi V2 0.833 -> 0.802
    }

    /**
     * Fracción de contenido del frame IDLE de cada pet (medida con bbox de alfa).
     * Referencia para la normalización por frame: ningún frame se dibuja más alto
     * que el idle (los estiramientos se comprimen; las posturas bajas se mantienen).
     */
    override val spriteIdleContentFraction: Float = when (petType) {
        PetType.MOKI -> 0.8021f         // perch (idx 0)
        PetType.CORGI -> 0.8047f        // REST (idx 6)
        PetType.JELLY -> 0.6159f        // idle blob (idx 0)
        PetType.BLOOP -> 0.7214f        // flotando (idx 0)
        PetType.NUBE_MICHI -> 0.6979f   // flotando (idx 0)
        PetType.ANGEL -> 0.9167f        // hover (idx 0)
        PetType.GINGER -> 0.9167f       // sit (idx 0)
        PetType.DIABLILLO -> 0.875f     // idle (idx 0)
        PetType.PATITO -> 0.5951f       // ciclo natación (idx 0)
        PetType.YUKI -> 0.8906f         // idle Pixar opaco (idx 0), atlas 3D premium
        PetType.PIRU -> 0.9102f         // idle pingüino Pixar (idx 0), atlas 3D premium
        PetType.TARO -> 0.8633f         // idle tortuga (idx 0), hoja nueva importada
        PetType.MENTA -> 0.8828f         // idle serpiente Pixar (idx 0), atlas 3D premium
        PetType.TELA -> 0.9102f         // idle araña Pixar (idx 0), atlas 3D premium
        PetType.LUMI -> 0.8333f         // idle Lumi V2 (idx 0)
    }

    /**
     * Fracción de contenido (alto) de cada frame, índice = frame del pet.
     * Medidas PIL sobre los assets actuales (ver tools/normalize_frames.py).
     * Se usa junto a [spriteIdleContentFraction] para que los frames de animación
     * "estirados" (jelly al interactuar, patito frames 4/9, corgi caminando) no se
     * dibujen más altos que el idle. Las posturas bajas (squash, sniff, dormir,
     * gatear) se mantienen a su tamaño natural.
     */
    override val spriteFrameContentFractions: FloatArray = when (petType) {
        PetType.CORGI -> floatArrayOf(0.7018f, 0.7018f, 0.6693f, 0.4818f, 0.737f, 0.7096f, 0.8047f, 0.8047f, 0.7943f, 0.5807f, 0.8099f, 0.8542f, 0.7956f, 0.8281f)
        PetType.JELLY -> floatArrayOf(0.6159f, 0.3581f, 0.4661f, 0.6797f, 0.6276f, 0.6263f, 0.388f, 0.3958f)  // 6/7 = aplastado
        PetType.BLOOP -> floatArrayOf(0.7214f, 0.7083f, 0.6016f, 0.8451f, 0.7227f, 0.7227f, 0.6758f)
        PetType.NUBE_MICHI -> floatArrayOf(0.6979f, 0.6576f, 0.7083f, 0.7578f, 0.444f, 0.7685f, 0.526f, 0.5339f, 0.4661f, 0.5638f, 0.4896f)
        PetType.PATITO -> floatArrayOf(0.5951f, 0.5846f, 0.4609f, 0.3594f, 0.7982f, 0.6419f, 0.7031f, 0.681f, 0.6823f, 0.7982f)
        PetType.DIABLILLO -> floatArrayOf(0.875f, 0.8568f, 0.875f, 0.8607f, 0.862f, 0.8529f, 0.8503f, 0.8646f, 0.8646f, 0.8672f)
        PetType.MOKI -> floatArrayOf(0.8021f, 0.8021f, 0.8021f, 0.8021f, 0.4401f, 0.4193f, 0.4167f, 0.4271f, 0.362f, 0.5651f, 0.7292f, 0.7292f, 0.8021f, 0.4583f, 0.3281f, 0.3411f, 0.8021f, 0.4427f, 0.8021f, 0.3568f)
        PetType.ANGEL -> floatArrayOf(0.9167f, 0.9167f, 0.9167f, 0.7891f, 0.9167f, 0.9167f, 0.7266f, 0.8802f, 0.9167f, 0.9167f, 0.9167f, 0.9167f, 0.9167f, 0.8724f, 0.9167f, 0.9167f)
        PetType.GINGER -> floatArrayOf(0.9167f, 0.9167f, 0.5703f, 0.6484f, 0.6536f, 0.6615f, 0.6667f, 0.6615f, 0.2682f, 0.3229f, 0.2812f, 0.3542f, 0.4531f, 0.4245f, 0.6432f, 0.5365f)
        PetType.YUKI -> floatArrayOf(0.8906f, 0.8984f, 0.8789f, 0.8984f, 0.8281f, 0.8086f, 0.7461f, 0.7930f, 0.8008f, 0.8047f, 0.8008f, 0.8125f, 0.7422f, 0.7969f, 0.7773f, 0.8125f)
        PetType.PIRU -> floatArrayOf(0.9102f, 0.9141f, 0.8945f, 0.9141f, 0.8711f, 0.8555f, 0.8086f, 0.8164f, 0.8320f, 0.8359f, 0.8633f, 0.8633f, 0.7383f, 0.6719f, 0.8164f, 0.8711f)
        PetType.TARO -> floatArrayOf(0.8633f, 0.8594f, 0.8516f, 0.8125f, 0.8359f, 0.8477f, 0.6758f, 0.6484f, 0.9062f, 0.8789f, 0.8398f, 0.8711f, 0.7656f, 0.7578f, 0.7812f, 0.7656f)
        PetType.MENTA -> floatArrayOf(0.8516f, 0.8516f, 0.8320f, 0.8242f, 0.4141f, 0.4102f, 0.4102f, 0.4141f, 0.4102f, 0.4219f, 0.4102f, 0.4102f, 0.8516f, 0.8516f, 0.8320f, 0.8438f)
        PetType.TELA -> floatArrayOf(0.9102f, 0.9102f, 0.8945f, 0.8984f, 0.6875f, 0.7188f, 0.8164f, 0.7109f, 0.5469f, 0.7305f, 0.7422f, 0.7500f, 0.4648f, 0.5508f, 0.7578f, 0.6211f)
        PetType.LUMI -> floatArrayOf(
            0.8333f, 0.8333f, 0.8333f, 0.8333f,
            0.8203f, 0.8021f, 0.7891f, 0.7865f,
            0.8333f, 0.8255f, 0.7917f, 0.8229f,
            0.8333f, 0.8333f, 0.8333f, 0.8333f,
            0.6797f, 0.7917f, 0.7161f, 0.8125f,
            0.7292f, 0.7526f, 0.7839f, 0.7630f,
            0.8333f, 0.8333f, 0.8333f, 0.8281f,
            0.7708f, 0.8333f, 0.8333f, 0.8333f,
            0.7969f, 0.5833f, 0.6016f, 0.5938f,
            0.8333f, 0.8333f, 0.8333f, 0.8333f,
        )
    }

    /** Cosmético equipado de este pet (efectos que envuelven, sin alineación). */
    private var equippedCosmetic: com.pixelpals.app.data.catalog.Cosmetic? = null
    private var cosmeticClock = 0f

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

    override var topSystemInsetPx: Int = 0
    override var bottomSystemInsetPx: Int = 0

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

    private val cosmeticPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    override val groundY: Int
        get() = bounds.floor

    override val bounds: PetBounds
        get() = PetBounds.compute(
            screenWidth,
            screenHeight,
            petSpriteSize,
            topSystemInsetPx,
            bottomSystemInsetPx,
            keyboardHeightPx
        )

    private var keyboardHeightPx = 0

    private var behaviorLazy: PetBehavior? = null
    private val behavior: PetBehavior?
        get() {
            if (behaviorLazy == null) {
                behaviorLazy = PetBehaviorFactory.create(petType, this)
            }
            return behaviorLazy
        }

    init {
        uiScope.launch {
            updatePetStatus(repository.getStatusSnapshot(petType))
            reloadCosmetic()
            showBubble(welcomeBubble())
            invalidate()
        }
    }

    private var isAnimating = false
    private val frameHandler = Handler(Looper.getMainLooper())
    private val frameRunnable = object : Runnable {
        override fun run() {
            if (!isAnimating) return
            val now = System.nanoTime()
            if (lastFrameTimeNanos != 0L && now > lastFrameTimeNanos) {
                val rawDt = (now - lastFrameTimeNanos) / 1_000_000_000f
                val step = motionEngine.splitDelta(rawDt)
                repeat(step.steps) { update(step.stepDt) }
            }
            lastFrameTimeNanos = now
            val moved = windowX != lastWindowX || windowY != lastWindowY
            lastWindowX = windowX
            lastWindowY = windowY
            invalidate()
            frameHandler.postDelayed(this, nextFrameDelayMs(moved))
        }
    }

    private var lastWindowX = 0
    private var lastWindowY = 0

    /**
     * Frame pacing adaptativo para minimizar batería:
     *   - 60 FPS si hay actividad visible (movimiento, estados, burbuja, tesoro),
     *   - 30 FPS si solo anima un cosmético envolvente (aura/float),
     *   - 12 FPS en idle profundo (la mascota está quieta y sin efectos).
     */
    private fun nextFrameDelayMs(wasMoving: Boolean): Long {
        if (state != PetState.IDLE || bubbleTimer > 0f || treasureReactionTimer > 0f || wasMoving) {
            return FRAME_INTERVAL_ACTIVE_MS
        }
        if (hasAnimatedCosmetic()) return FRAME_INTERVAL_COSMETIC_MS
        return FRAME_INTERVAL_IDLE_MS
    }

    private fun hasAnimatedCosmetic(): Boolean {
        val effect = equippedCosmetic?.effect
        return effect is com.pixelpals.app.data.catalog.CosmeticEffect.AuraEffect ||
            effect is com.pixelpals.app.data.catalog.CosmeticEffect.FloatEffect
    }

    /**
     * Offset entre la esquina del view real (2x el sprite) y la esquina lógica
     * del sprite. Los behaviors posicionan la esquina del sprite; el view real
     * se traslada -inset para que el sprite quede centrado con margen para
     * los cosméticos (aura/floats) alrededor.
     */
    private val cosmeticInset: Int
        get() = ((width - petSpriteSize) / 2).coerceAtLeast(0)

    /** Copia los params con coordenadas LÓGICAS (esquina del sprite en pantalla). */
    override fun getWindowParams(): WindowManager.LayoutParams? {
        val real = layoutParams as? WindowManager.LayoutParams ?: return null
        val copy = WindowManager.LayoutParams(
            real.width, real.height, real.type, real.flags, real.format
        ).apply {
            gravity = real.gravity
            softInputMode = real.softInputMode
            x = real.x + cosmeticInset
            y = real.y + cosmeticInset
        }
        return copy
    }

    override fun updateWindowLayout(params: WindowManager.LayoutParams) {
        try {
            if (params.x == windowX && params.y == windowY) return
            // La posición lógica es la fuente de verdad; se registra aunque la
            // llamada al WindowManager falle (view no attached en pruebas).
            windowX = params.x
            windowY = params.y
            // params llega en coordenadas lógicas (esquina del sprite en pantalla).
            // El view real (2x el sprite, centrado) se posiciona -inset.
            val real = WindowManager.LayoutParams(
                params.width, params.height, params.type, params.flags, params.format
            ).apply {
                gravity = params.gravity
                softInputMode = params.softInputMode
                x = params.x - cosmeticInset
                y = params.y - cosmeticInset
            }
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.updateViewLayout(this, real)
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
        params.x = if (Random.nextBoolean()) bounds.left + 20 else bounds.right - 20
        params.y = Random.nextInt(
            bounds.top,
            (bounds.floor - 100).coerceAtLeast(bounds.top + 1)
        )
        updateWindowLayout(params)
    }

    override fun updateTelaSilk(state: com.pixelpals.app.feature.overlay.behavior.TelaSilkState?) {
        onTelaSilkChanged(state)
    }

    override fun updateTelaCornerWeb(state: com.pixelpals.app.feature.overlay.behavior.TelaCornerWebState?) {
        onTelaCornerWebChanged(state)
    }

    fun debugStartTelaWeb() {
        (behavior as? com.pixelpals.app.feature.overlay.behavior.TelaBehavior)?.debugStartWebSequence()
    }

    fun debugStartTelaCornerWeb() {
        (behavior as? com.pixelpals.app.feature.overlay.behavior.TelaBehavior)?.debugLeaveCornerWeb()
    }

    override fun trackInteraction() {
        progress.trackInteraction()
        uiScope.launch {
            updatePetStatus(repository.recordInteraction(petType))
            PetCareNotificationManager.cancel(context)
            PetCareNotificationScheduler.schedule(context)
            analytics.track(
                "pet_interaction",
                mapOf(
                    "pet_id" to petStatus.petId,
                    "mood" to petStatus.mood.name,
                    "bond" to petStatus.bond.toString()
                )
            )
            repository.maybeAwardTreasureFromInteraction(petType)?.let { result ->
                handleTreasureDiscovery(result, "interaction")
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
            motionEngine.resetAccumulator()
            frameHandler.post(frameRunnable)
            behavior?.resume()
        }
    }

    override fun pauseAnimation() {
        if (isAnimating) progress.flush()
        isAnimating = false
        lastFrameTimeNanos = 0L
        motionEngine.resetAccumulator()
        frameHandler.removeCallbacks(frameRunnable)
        behavior?.pause()
    }

    override fun consumeTreasure(emoji: String) {
        showBubble(emoji)
        playHaptic(35)
        treasureReactionTimer = treasureReactionDuration
        behavior?.onTreasureConsumed(emoji)
        uiScope.launch { updatePetStatus(repository.getStatusSnapshot(petType)) }
    }

    private suspend fun handleTreasureDiscovery(
        result: TreasureDiscoveryResult,
        source: String,
    ): Unit {
        showBubble(result.emoji)
        updatePetStatus(repository.getStatusSnapshot(petType))
        analytics.track(
            "treasure_discovered",
            mapOf(
                "pet_id" to petType.name.lowercase(),
                "treasure_id" to result.treasureId,
                "source" to source,
                "new" to result.isNewDiscovery.toString(),
                "coins" to result.coinsGained.toString(),
                "bond" to result.bondGained.toString(),
            ),
        )
        result.milestone?.let { milestone ->
            analytics.track(
                "treasure_milestone_reached",
                mapOf(
                    "pet_id" to petType.name.lowercase(),
                    "milestone" to milestone.milestone.toString(),
                    "badge" to milestone.name.lowercase(),
                ),
            )
        }
    }

    fun refreshFromRepository(message: String?, celebrate: Boolean) {
        uiScope.launch {
            updatePetStatus(repository.getStatusSnapshot(petType))
            reloadCosmetic()
            if (!message.isNullOrBlank()) showBubble(message)
            if (celebrate) {
                treasureReactionTimer = treasureReactionDuration
                playHaptic(35)
            }
            invalidate()
        }
    }

    private fun reloadCosmetic() {
        val equippedId = repository.getEquippedCosmetic(petType.name.lowercase())
        equippedCosmetic = equippedId?.let { id ->
            com.pixelpals.app.data.catalog.CosmeticCatalog.findById(context, id)
        }
        val tint = (equippedCosmetic?.effect as? com.pixelpals.app.data.catalog.CosmeticEffect.TintEffect)
        cosmeticColorFilter = tint?.let { android.graphics.ColorMatrixColorFilter(it.toColorMatrix()) }
    }

    /** Dibuja aura y float (objetos que envuelven al pet, sin alineación al cuerpo). */
    private fun drawCosmetic(canvas: Canvas) {
        val effect = equippedCosmetic?.effect as? com.pixelpals.app.data.catalog.CosmeticEffect
            ?: return
        // Siguen la posición y escala del pet (saltos, vuelo, inflado) pero NO su
        // rotación: corona/paraguas flotan "arriba" en pantalla, no giran con él.
        val cx = width / 2f + renderOffsetX
        val cy = height / 2f + renderOffsetY
        val scale = renderScaleX.coerceAtLeast(0.4f)
        when (effect) {
            is com.pixelpals.app.data.catalog.CosmeticEffect.TintEffect -> Unit
            is com.pixelpals.app.data.catalog.CosmeticEffect.AuraEffect -> {
                cosmeticPaint.textSize = petSpriteSize.toFloat() * effect.sizeRatio * scale
                cosmeticPaint.alpha = (200 * (animAlpha.coerceIn(0f, 1f))).toInt()
                val fm = cosmeticPaint.fontMetrics
                val radius = petSpriteSize.toFloat() * effect.radiusRatio * scale
                for (i in 0 until effect.count) {
                    val angle = cosmeticClock * effect.speed + (2f * PI.toFloat() * i) / effect.count
                    val x = cx + cos(angle) * radius
                    val y = cy + sin(angle) * radius
                    canvas.drawText(effect.emoji, x, y - (fm.ascent + fm.descent) / 2f, cosmeticPaint)
                }
            }
            is com.pixelpals.app.data.catalog.CosmeticEffect.FloatEffect -> {
                val scaleX = renderScaleX.coerceAtLeast(0.4f)
                val scaleY = renderScaleY.coerceAtLeast(0.4f)
                cosmeticPaint.textSize = petSpriteSize.toFloat() * effect.sizeRatio * scaleX
                cosmeticPaint.alpha = (255 * (animAlpha.coerceIn(0f, 1f))).toInt()
                val fm = cosmeticPaint.fontMetrics
                val bob = sin(cosmeticClock * effect.bobSpeed) * effect.bobAmplitude * scaleY
                // Posición: el eje X sigue el squash horizontal y el eje Y el vertical,
                // para que corona/varita/paraguas acompañen al pet al aplastarse/saltar.
                var x = cx + petSpriteSize.toFloat() * effect.xRatio * scaleX
                var y = cy + petSpriteSize.toFloat() * (effect.yRatio + bob) * scaleY
                // Clamp dentro del view (el view puede ser menor que 2x el sprite por
                // el tope defensivo MAX_VIEW_SIZE_RATIO): el emoji nunca se recorta.
                val marginX = cosmeticPaint.textSize * 0.55f
                val marginY = cosmeticPaint.textSize * 0.6f
                x = x.coerceIn(marginX, width - marginX)
                y = y.coerceIn(marginY, height - marginY)
                canvas.drawText(effect.emoji, x, y - (fm.ascent + fm.descent) / 2f, cosmeticPaint)
            }
        }
    }

    override fun recordCareAction(action: CareAction) {
        uiScope.launch {
            updatePetStatus(repository.applyCareAction(petType, action))
            PetCareNotificationManager.cancel(context)
            PetCareNotificationScheduler.schedule(context)
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

    fun onBatteryTemperatureChanged(temperatureCelsius: Float?) {
        behavior?.onBatteryTemperatureChanged(temperatureCelsius)
    }

    override fun onKeyboardChanged(visible: Boolean, height: Int) {
        keyboardHeightPx = if (visible) height.coerceAtLeast(0) else 0
        behavior?.onKeyboardVisibilityChanged(visible, height)
    }

    override fun onAirplaneModeChanged(isAirplane: Boolean) {
        behavior?.onAirplaneModeChanged(isAirplane)
    }

    private fun update(dt: Float) {
        // No-op: screen metrics are cached at attach/config-change time (see refreshScreenMetrics).
        cosmeticClock += dt
        activeSecondsAccumulator += dt
        while (activeSecondsAccumulator >= 60f) {
            progress.trackMinute()
            progress.flush()
            activeSecondsAccumulator -= 60f
            uiScope.launch {
                updatePetStatus(repository.recordActiveMinute(petType))
                repository.maybeAwardTreasureFromActiveMinute(petType)?.let { result ->
                    handleTreasureDiscovery(result, "active_minute")
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
        maybeShowTimeGreeting()
        // While the pointer is inside touchSlop the pet stays planted. This
        // preserves the exact grab point and prevents a jump when drag starts.
        if (!isTouchPending) {
            when (state) {
                PetState.IDLE -> behavior?.updateIdle(dt)
                PetState.DRAGGING -> behavior?.updateDrag(dt)
                PetState.FALLING -> updatePhysicsFalling(dt)
                PetState.JUMPING -> behavior?.updateJumping(dt)
                PetState.INTERACTING -> behavior?.updateInteracting(dt)
                else -> behavior?.updateAutonomous(dt)
            }
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
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Sprite base del pet.
        behavior?.onDraw(canvas, (width / 2).toFloat(), (height / 2).toFloat())

        drawCosmetic(canvas)

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

    private fun launchPhysics(velocityX: Float, velocityY: Float) {
        physicsBody = PhysicsBody(
            x = windowX.toFloat(),
            y = windowY.toFloat(),
            velocityX = velocityX.coerceIn(-MAX_PHYSICS_LAUNCH_SPEED, MAX_PHYSICS_LAUNCH_SPEED),
            velocityY = velocityY.coerceIn(-MAX_PHYSICS_LAUNCH_SPEED, MAX_PHYSICS_LAUNCH_SPEED)
        )
        state = PetState.FALLING
    }

    private fun updatePhysicsFalling(dt: Float) {
        val body = physicsBody
        if (body == null) {
            state = PetState.IDLE
            behavior?.reset()
            return
        }
        val result = PetPhysics.step(body, dt, bounds, physicsProfileFor(petType))
        physicsBody = result.body
        val params = getWindowParams() ?: return
        params.x = result.body.x.roundToInt()
        params.y = result.body.y.roundToInt()
        updateWindowLayout(params)
        animScaleY = 1.15f
        animScaleX = 0.9f
        if (result.event == PhysicsEvent.SETTLED) {
            physicsBody = null
            state = PetState.IDLE
            behavior?.reset()
        }
    }

    private fun physicsProfileFor(type: PetType): PhysicsProfile = when (type) {
        PetType.BLOOP, PetType.NUBE_MICHI, PetType.ANGEL -> PhysicsProfile.FLYING
        PetType.PATITO, PetType.PIRU -> PhysicsProfile.AQUATIC
        PetType.TELA, PetType.MOKI -> PhysicsProfile.EDGE
        PetType.LUMI -> PhysicsProfile.GROUND
        else -> PhysicsProfile.GROUND
    }

    private fun maybeShowAmbientMoodBubble() {
        val bubble = ambientBubbleOptions().randomOrNull()
        bubble?.let { showBubble(it) }
    }

    private fun maybeShowTimeGreeting() {
        if (bubbleText != null || state != PetState.IDLE) return
        val now = System.currentTimeMillis()
        if (now - lastTimeGreetingCheckAt < TIME_GREETING_CHECK_INTERVAL_MS) return
        lastTimeGreetingCheckAt = now
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val key = when {
            hour in 6..11 -> "morning"
            hour >= 22 || hour <= 4 -> "night"
            else -> "day"
        }
        if (key == lastTimeWindowKey) return
        lastTimeWindowKey = key
        when (key) {
            "morning" -> showBubble("☀️")
            "night" -> showBubble("🌙")
            else -> Unit
        }
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

    private fun updatePetStatus(updated: PetStatusSnapshot) {
        val previous: PetStatusSnapshot = petStatus
        petStatus = updated
        behavior?.onStatusChanged(previous, updated)
        if (previous.condition != updated.condition) {
            analytics.track(
                "pet_condition_changed",
                mapOf(
                    "pet_id" to updated.petId,
                    "from" to previous.condition.name.lowercase(),
                    "to" to updated.condition.name.lowercase(),
                ),
            )
        }
        invalidate()
    }

    private fun careActionBubble(action: CareAction): String {
        return when (action) {
            CareAction.FEED -> listOf("🍓", "🍪", "😋").random()
            CareAction.CLEAN -> listOf("🫧", "✨", "🛁").random()
            CareAction.PLAY -> listOf("🎉", "💫", "😄").random()
            CareAction.REST -> listOf("💤", "🌙", "☁️").random()
            CareAction.CHECK_IN -> listOf("💖", "✨", "😊").random()
            CareAction.MEDICINE -> listOf("💊", "💛", "🤗").random()
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
                // El view es 2x el sprite (espacio para cosméticos): solo capturamos
                // toques cerca del sprite para no bloquear la app de debajo.
                val alphaHit = behavior?.hitTest(event.x, event.y, width, height)
                if (alphaHit == false) return false
                if (alphaHit == null) {
                    val dx = event.x - width / 2f
                    val dy = event.y - height / 2f
                    val touchRadius = petSpriteSize * spriteScale * 0.55f
                    if (dx * dx + dy * dy > touchRadius * touchRadius) return false
                }

                behaviorOwnsTouch = behavior?.onTouchDown(event.rawX, event.rawY) == true
                if (behaviorOwnsTouch) return true
                gestureRecognizer.onDown(event.rawX, event.rawY, event.eventTime)
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain()
                addMovementToVelocityTracker(event)
                initialX = params.x
                initialY = params.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                physicsBody = null
                isTouchPending = true
                frameHandler.removeCallbacks(holdRunnable)
                frameHandler.postDelayed(holdRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (behaviorOwnsTouch) return true
                addMovementToVelocityTracker(event)
                val gesture = gestureRecognizer.onMove(event.rawX, event.rawY, event.eventTime)
                if (gesture.type == PetGestureType.DRAG_STARTED) {
                    isTouchPending = false
                    frameHandler.removeCallbacks(holdRunnable)
                    state = PetState.DRAGGING
                    behavior?.onDragStart(
                        pointerX = event.rawX,
                        pointerY = event.rawY,
                        grabOffsetX = initialTouchX - initialX,
                        grabOffsetY = initialTouchY - initialY,
                    )
                }
                if (state == PetState.DRAGGING && gesture.type != PetGestureType.NONE) {
                    if (behavior?.usesRuntimeInput == true) {
                        behavior?.onDragMove(event.rawX, event.rawY)
                    } else {
                        params.x = (initialX + (event.rawX - initialTouchX).toInt())
                            .coerceIn(bounds.left, bounds.right)
                        params.y = (initialY + (event.rawY - initialTouchY).toInt())
                            .coerceIn(bounds.top, bounds.floor)
                        updateWindowLayout(params)
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (behaviorOwnsTouch) {
                    behaviorOwnsTouch = false
                    behavior?.onTouchUp()
                    return true
                }
                frameHandler.removeCallbacks(holdRunnable)
                isTouchPending = false
                addMovementToVelocityTracker(event)
                if (behavior?.onTouchUp() == true) {
                    gestureRecognizer.onCancel()
                    recycleVelocityTracker()
                    return true
                }
                velocityTracker?.computeCurrentVelocity(1000, maximumFlingVelocity)
                val flingVX = velocityTracker?.xVelocity ?: 0f
                val flingVY = velocityTracker?.yVelocity ?: 0f
                val gesture = gestureRecognizer.onUp(flingVX, flingVY, event.eventTime)
                when (gesture.type) {
                    PetGestureType.TAP -> performClick()
                    PetGestureType.FLING,
                    PetGestureType.RELEASE,
                    -> {
                        if (behavior?.usesRuntimeInput == true) {
                            if (gesture.type == PetGestureType.FLING) {
                                behavior?.onFling(flingVX, flingVY)
                            } else {
                                behavior?.onRelease(flingVX, flingVY)
                            }
                        } else {
                            if (gesture.type == PetGestureType.FLING) behavior?.onFling(flingVX, flingVY)
                        }
                        if (behavior?.usesRuntimeInput != true && state == PetState.DRAGGING) {
                            launchPhysics(
                                if (gesture.type == PetGestureType.FLING) flingVX else 0f,
                                if (gesture.type == PetGestureType.FLING) flingVY else 0f,
                            )
                        }
                    }
                    PetGestureType.HOLD_RELEASED -> behavior?.onHoldReleased()
                    else -> Unit
                }
                recycleVelocityTracker()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                frameHandler.removeCallbacks(holdRunnable)
                isTouchPending = false
                behaviorOwnsTouch = false
                gestureRecognizer.onCancel()
                behavior?.onGestureCancelled()
                physicsBody = null
                state = PetState.IDLE
                behavior?.reset()
                recycleVelocityTracker()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refreshScreenMetrics()
    }

    override fun onDetachedFromWindow() {
        frameHandler.removeCallbacks(holdRunnable)
        isTouchPending = false
        behaviorOwnsTouch = false
        recycleVelocityTracker()
        progress.flush()
        behavior?.destroy()
        uiScope.cancel()
        super.onDetachedFromWindow()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshScreenMetrics()
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
            val metrics = wm.currentWindowMetrics
            screenWidth = metrics.bounds.width()
            screenHeight = metrics.bounds.height()
            val bars = metrics.windowInsets.getInsets(WindowInsets.Type.systemBars())
            topSystemInsetPx = bars.top
            bottomSystemInsetPx = bars.bottom
        } else {
            @Suppress("DEPRECATION")
            val metrics = DisplayMetrics().also { wm.defaultDisplay.getMetrics(it) }
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
            topSystemInsetPx = 0
            bottomSystemInsetPx = 0
        }
    }

    private companion object {
        const val FRAME_INTERVAL_ACTIVE_MS = 16L
        const val FRAME_INTERVAL_COSMETIC_MS = 33L
        const val FRAME_INTERVAL_IDLE_MS = 83L
        const val TIME_GREETING_CHECK_INTERVAL_MS = 60_000L
        const val MAX_PHYSICS_LAUNCH_SPEED = 2_200f
    }
}
