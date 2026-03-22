package com.pixelpals.app

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.*
import kotlin.random.Random

/**
 * PetView — El alma de PixelPals.
 *
 * Renderiza sprites con transparencia perfecta y les da vida mediante:
 *  - State Machine: IDLE → DRAG → FALL → LAND (squash) → IDLE
 *  - Blink: parpadeo cada 3-7s (el truco más viejo y efectivo)
 *  - Idle Animations: respiración, wobble, flotación per-pet
 *  - Landing Squash: física "juicy" al aterrizar
 *  - Dynamic Shadow: sombra que se adapta a la altura
 *  - Secret Life: eventos raros tras inactividad
 *  - Double-tap Dodge: la mascota salta para no molestar
 *  - System Reactions: batería baja, carga, etc.
 *
 * Optimizado: 30 FPS, pausa en screen off, bitmap reciclado.
 */
class PetView(
    context: Context,
    private val screenWidth: Int,
    private val screenHeight: Int,
    private val petSpriteSize: Int,
    private val petType: PetType
) : View(context) {

    // ══════════════════════════════════════════════════════════
    // ▌ STATE MACHINE
    // ══════════════════════════════════════════════════════════

    enum class PetState {
        IDLE,               // Breathing/wobble/float + blink
        DRAGGING,           // Held by user — "pataleo" animation
        FALLING,            // Dropped — gravity pulling down
        LANDING,            // Hit ground — squash sequence
        WALKING,            // Autonomous movement
        JUMPING,            // In air from autonomous jump
        SECRET_IDLE,        // Doing a rare hidden activity
        SYSTEM_REACTION,    // Reacting to battery/charging/etc
        INTERACTING         // Triggered by tap (e.g. Belly rub)
    }

    private var state = PetState.IDLE

    // ══════════════════════════════════════════════════════════
    // ▌ CONSTANTS
    // ══════════════════════════════════════════════════════════

    companion object {
        private const val FRAME_DELAY_MS = 28L     // ~35 FPS (smooth + battery friendly)
        private const val GROUND_MARGIN = 120
        private const val BLINK_MIN_INTERVAL = 3f  // seconds
        private const val BLINK_MAX_INTERVAL = 7f
        private const val BLINK_DURATION = 0.15f   // seconds
        private const val LAND_SQUASH_DURATION = 0.35f
        private const val SECRET_IDLE_WAIT = 25f   // seconds before secret events
        private const val DOUBLE_TAP_THRESHOLD = 300L // ms
        private const val SYSTEM_REACTION_DURATION = 3f
    }

    // ══════════════════════════════════════════════════════════
    // ▌ SPRITE
    // ══════════════════════════════════════════════════════════

    private val spriteBitmap: Bitmap
    private val spritePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val spriteRect = RectF()

    // ══════════════════════════════════════════════════════════
    // ▌ ANIMATION TRANSFORMS
    // ══════════════════════════════════════════════════════════

    private var animScaleX = 1f
    private var animScaleY = 1f
    private var animAlpha = 1f
    private var animOffsetX = 0f
    private var animOffsetY = 0f
    private var animRotation = 0f   // For drag "pataleo"

    // ══════════════════════════════════════════════════════════
    // ▌ PHYSICS
    // ══════════════════════════════════════════════════════════

    private var velocityX = 0f
    private var velocityY = 0f
    private val groundY get() = screenHeight - petSpriteSize - GROUND_MARGIN

    // ══════════════════════════════════════════════════════════
    // ▌ PROGRESSION
    // ══════════════════════════════════════════════════════════

    private var progress: PetProgress? = null
    private var xpMinuteTimer = 0f          // Counts to 60s for 1 XP
    private var treasureTimer = 0f          // Counts to next treasure find
    private var nextTreasureTime = 180f + Random.nextFloat() * 120f  // 3-5 minutes
    private var isAirplaneMode = false
    private var corgiLickTimer = -1f        // -1 = not licking

    /** Set from PetService after creation */
    fun setProgress(p: PetProgress) { progress = p }

    /** Airplane mode changed */
    fun onAirplaneModeChanged(enabled: Boolean) {
        isAirplaneMode = enabled
        if (enabled) triggerReaction("✈️")
    }

    // ══════════════════════════════════════════════════════════
    // ▌ TIMERS
    // ══════════════════════════════════════════════════════════

    private var time = 0f
    private var blinkTimer = 0f
    private var nextBlinkTime = randomBlinkInterval()
    private var isBlinking = false
    private var blinkProgress = 0f

    private var landTimer = 0f
    private var landVelocity = 0f

    private var idleTimer = 0f      // Time since last interaction
    private var secretTimer = 0f
    private var isSecretActive = false
    private var secretEmoji = ""

    private var interactTimer = 0f  // Used for pet-specific tap reactions

    private var moveTimer = 0f
    private var nextMoveTime = Random.nextFloat() * 3f + 2f
    private var isMoving = false
    private var moveActionTimer = 0f

    private var reactionTimer = 0f
    private var reactionEmoji = ""

    // ══════════════════════════════════════════════════════════
    // ▌ BUBBLES
    // ══════════════════════════════════════════════════════════

    private var showBubble = false
    private var bubbleText = ""
    private var bubbleAlpha = 0f
    private var bubbleTimer = 0f

    // ══════════════════════════════════════════════════════════
    // ▌ TOUCH
    // ══════════════════════════════════════════════════════════

    private var isDragging = false
    private var touchOffsetX = 0f
    private var touchOffsetY = 0f
    private var lastTapTime = 0L
    private var tapCount = 0
    private var dragStartTime = 0L

    // ══════════════════════════════════════════════════════════
    // ▌ SYSTEM STATE
    // ══════════════════════════════════════════════════════════

    private var batteryLevel = 100
    private var isCharging = false
    private var isBatteryLow = false

    // ══════════════════════════════════════════════════════════
    // ▌ PERSONALITY PAINTS
    // ══════════════════════════════════════════════════════════

    private val lickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#40FF69B4")  // Semi-transparent pink
        style = Paint.Style.FILL
    }

    // ══════════════════════════════════════════════════════════
    // ▌ PAINTS
    // ══════════════════════════════════════════════════════════

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val bubbleBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        setShadowLayer(4f, 0f, 2f, Color.parseColor("#20000000"))
    }

    private val bubbleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D0D0D8")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }

    private val bubbleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 24f
        textAlign = Paint.Align.CENTER
    }

    // ══════════════════════════════════════════════════════════
    // ▌ HANDLER
    // ══════════════════════════════════════════════════════════

    private var isAnimating = false
    private val handler = Handler(Looper.getMainLooper())
    private val animationRunnable = object : Runnable {
        override fun run() {
            if (!isAnimating) return
            updateFrame()
            invalidate()
            handler.postDelayed(this, FRAME_DELAY_MS)
        }
    }

    // ══════════════════════════════════════════════════════════
    // ▌ INIT
    // ══════════════════════════════════════════════════════════

    init {
        // Load sprite and ensure transparency
        val drawable = ContextCompat.getDrawable(context, petType.spriteResId)!!
        val rawBitmap = drawable.toBitmap(petSpriteSize, petSpriteSize)
        spriteBitmap = removeBackground(rawBitmap)

        // Ensure view draws with full transparency
        setBackgroundColor(Color.TRANSPARENT)
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    /**
     * Remove solid backgrounds of any color. Uses flood-fill (BFS) starting from the corners
     * to identify the background color, completely removing flat backgrounds without
     * eating the sprite's interior (unless the boundary line has gaps).
     * Smooths edges with a distance-based anti-aliasing gradient.
     */
    private fun removeBackground(source: Bitmap): Bitmap {
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val w = result.width
        val h = result.height
        val pixels = IntArray(w * h)
        result.getPixels(pixels, 0, w, 0, 0, w, h)

        // Find the most common background color from the edges
        val edgeColors = mutableMapOf<Int, Int>()
        for (x in 0 until w) {
            edgeColors[pixels[x]] = (edgeColors[pixels[x]] ?: 0) + 1
            edgeColors[pixels[(h - 1) * w + x]] = (edgeColors[pixels[(h - 1) * w + x]] ?: 0) + 1
        }
        for (y in 0 until h) {
            edgeColors[pixels[y * w]] = (edgeColors[pixels[y * w]] ?: 0) + 1
            edgeColors[pixels[y * w + w - 1]] = (edgeColors[pixels[y * w + w - 1]] ?: 0) + 1
        }
        // Exclude completely transparent pixels from being considered "background"
        val solidEdgeColors = edgeColors.filterKeys { Color.alpha(it) > 128 }
        val bgColor = if (solidEdgeColors.isNotEmpty()) {
            solidEdgeColors.maxByOrNull { it.value }?.key ?: Color.WHITE
        } else {
            return result // Already transparent!
        }
        
        val bgR = Color.red(bgColor)
        val bgG = Color.green(bgColor)
        val bgB = Color.blue(bgColor)

        // Flood fill from corners
        val queue = ArrayDeque<Int>()
        val visited = BooleanArray(w * h)

        val corners = listOf(0, w - 1, (h - 1) * w, (h - 1) * w + w - 1)
        for (c in corners) {
            queue.addLast(c)
            visited[c] = true
        }

        while (queue.isNotEmpty()) {
            val i = queue.removeFirst()
            val x = i % w
            val y = i / w

            val p = pixels[i]
            val alpha = Color.alpha(p)

            if (alpha < 5) continue

            val r = Color.red(p)
            val g = Color.green(p)
            val b = Color.blue(p)

            // Euclidean distance to background color
            val dist = sqrt((r - bgR) * (r - bgR).toFloat() + (g - bgG) * (g - bgG).toFloat() + (b - bgB) * (b - bgB).toFloat())

            // Tolerance: 60 for solid removal, up to 100 for antialiasing
            if (dist < 60f) {
                pixels[i] = Color.TRANSPARENT
                
                // Add neighbors
                val neighbors = intArrayOf(
                    if (x > 0) i - 1 else -1,
                    if (x < w - 1) i + 1 else -1,
                    if (y > 0) i - w else -1,
                    if (y < h - 1) i + w else -1
                )
                
                for (n in neighbors) {
                    if (n != -1 && !visited[n]) {
                        visited[n] = true
                        queue.addLast(n)
                    }
                }
            } else if (dist < 100f) {
                // Outer antialiased edge: keep color but make semi-transparent
                // Do not add to queue to stop propagation
                val smoothAlpha = ((dist - 60f) / 40f * alpha).toInt().coerceIn(0, 255)
                pixels[i] = Color.argb(smoothAlpha, r, g, b)
            }
        }

        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    // ══════════════════════════════════════════════════════════
    // ▌ PUBLIC API
    // ══════════════════════════════════════════════════════════

    fun resumeAnimation() {
        if (isAnimating) return
        isAnimating = true
        handler.post(animationRunnable)
    }

    fun pauseAnimation() {
        isAnimating = false
        handler.removeCallbacks(animationRunnable)
    }

    /** Called from PetService when battery state changes */
    fun onBatteryChanged(level: Int, charging: Boolean) {
        val wasBatteryLow = isBatteryLow
        batteryLevel = level
        isCharging = charging
        isBatteryLow = level < 15

        // Trigger reaction on state changes
        if (!wasBatteryLow && isBatteryLow) {
            triggerReaction("😴")
        }
        if (charging && level < 30) {
            triggerReaction("⚡")
        }
    }

    /** Called when keyboard appears */
    fun onKeyboardChanged(keyboardVisible: Boolean, keyboardHeight: Int) {
        if (keyboardVisible) {
            moveAboveKeyboard(keyboardHeight)
        }
    }

    // ══════════════════════════════════════════════════════════
    // ▌ FRAME UPDATE (Main Loop)
    // ══════════════════════════════════════════════════════════

    private fun updateFrame() {
        val dt = FRAME_DELAY_MS / 1000f
        time += dt

        // ── XP minute tracker ──
        xpMinuteTimer += dt
        if (xpMinuteTimer >= 60f) {
            xpMinuteTimer = 0f
            progress?.trackMinute()
        }

        // ── Treasure finding ──
        treasureTimer += dt
        if (treasureTimer >= nextTreasureTime && state == PetState.IDLE && (progress?.unlockedBehaviors ?: 1) >= 3) {
            treasureTimer = 0f
            nextTreasureTime = 180f + Random.nextFloat() * 120f
            val treasure = progress?.rollTreasure() ?: "⭐"
            progress?.addTreasure(treasure)
            showBubble(treasure)
        }

        // ── Corgi lick screen ──
        if (corgiLickTimer >= 0f) {
            corgiLickTimer += dt
            if (corgiLickTimer > 2.5f) corgiLickTimer = -1f
        }

        // Always update blink (universal for all pets)
        updateBlink(dt)

        // Update bubble animation
        updateBubble(dt)

        // State-specific updates
        when (state) {
            PetState.IDLE -> {
                updateIdleAnimation(dt)
                updateAutonomousMovement(dt)
                updateIdleTimer(dt)
                updatePersonality(dt)
            }
            PetState.DRAGGING -> {
                updateDragAnimation(dt)
            }
            PetState.FALLING -> {
                updateFalling(dt)
            }
            PetState.LANDING -> {
                updateLandingSquash(dt)
            }
            PetState.WALKING -> {
                updateIdleAnimation(dt)
                updateWalking(dt)
            }
            PetState.JUMPING -> {
                updateJumping(dt)
            }
            PetState.SECRET_IDLE -> {
                updateSecretIdle(dt)
                updateIdleAnimation(dt)
            }
            PetState.SYSTEM_REACTION -> {
                updateSystemReaction(dt)
            }
            PetState.INTERACTING -> {
                updateInteracting(dt)
            }
        }

        // Battery-low visual effect (global overlay)
        if (isBatteryLow && state == PetState.IDLE) {
            animAlpha = 0.7f + sin(time * 1.5f) * 0.1f
        }
    }

    // ══════════════════════════════════════════════════════════
    // ▌ BLINK ANIMATION (The most effective trick)
    // ══════════════════════════════════════════════════════════

    private fun updateBlink(dt: Float) {
        blinkTimer += dt

        if (isBlinking) {
            blinkProgress += dt
            if (blinkProgress >= BLINK_DURATION) {
                isBlinking = false
                blinkProgress = 0f
                blinkTimer = 0f
                nextBlinkTime = randomBlinkInterval()
            }
        } else if (blinkTimer >= nextBlinkTime) {
            isBlinking = true
            blinkProgress = 0f
        }
    }

    /**
     * Gets the blink scale factor.
     * During blink: scaleY dips to 0.82 and returns in a smooth sine curve.
     * This creates a convincing "blink" for ANY sprite.
     */
    private fun getBlinkScaleY(): Float {
        if (!isBlinking) return 1f
        val t = blinkProgress / BLINK_DURATION
        // Sine curve: 0 → 1 → 0
        return 1f - sin(t * PI.toFloat()) * 0.18f
    }

    private fun randomBlinkInterval(): Float {
        return BLINK_MIN_INTERVAL + Random.nextFloat() * (BLINK_MAX_INTERVAL - BLINK_MIN_INTERVAL)
    }

    // ══════════════════════════════════════════════════════════
    // ▌ IDLE ANIMATIONS (Per Pet Type)
    // ══════════════════════════════════════════════════════════

    private fun updateIdleAnimation(dt: Float) {
        when (petType.idleStyle) {
            IdleStyle.SINE_FLOAT -> {
                // Bloop: ethereal floating
                animOffsetY = sin(time * 1.2f) * 10f
                animOffsetX = sin(time * 0.6f) * 5f
                animAlpha = 0.82f + sin(time * 1.8f) * 0.12f

                // Slow drift via window position
                applyWindowOffset(
                    dx = (sin(time * 0.3f) * 0.5f).toInt(),
                    dy = (sin(time * 0.8f) * 0.3f).toInt()
                )
            }
            IdleStyle.BREATHING -> {
                // Nube-Michi: cloud breathing (expand/contract)
                val t = sin(time * 1.6f)
                animScaleX = 1f + t * 0.05f
                animScaleY = 1f - t * 0.03f
                animOffsetY = t * 2f
            }
            IdleStyle.JELLY_WOBBLE -> {
                // Jelly: constant gelatinous wobble
                animScaleX = 1f + sin(time * 3.5f) * 0.07f
                animScaleY = 1f - sin(time * 3.5f) * 0.05f + cos(time * 2.8f) * 0.03f
                animOffsetY = abs(sin(time * 2f)) * 3f
            }
            IdleStyle.SIT_BARK -> {
                // Corgi: gentle sway + occasional bark
                animOffsetX = sin(time * 2.2f) * 2.5f
                animOffsetY = abs(sin(time * 1.5f)) * 1.5f

                moveTimer += dt
                if (moveTimer > nextMoveTime && !showBubble) {
                    showBubble("🦴❤️🐾".chunked(2).random())
                    nextMoveTime = Random.nextFloat() * 5f + 4f
                    moveTimer = 0f
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════
    // ▌ DRAG ANIMATION ("Pataleo")
    // ══════════════════════════════════════════════════════════

    private fun updateDragAnimation(dt: Float) {
        // Oscillating rotation simulates "kicking legs"
        animRotation = sin(time * 12f) * 8f
        // Slight stretch while held
        animScaleX = 1.05f
        animScaleY = 0.95f
    }

    // ══════════════════════════════════════════════════════════
    // ▌ FALLING & LANDING
    // ══════════════════════════════════════════════════════════

    private fun updateFalling(dt: Float) {
        val params = getWindowParams() ?: return

        // Reset rotation from drag
        animRotation *= 0.85f
        animScaleX = 1f
        animScaleY = 1f + (velocityY / petType.terminalVelocity) * 0.15f // Stretch vertically while falling fast

        // Special case: Bloop doesn't fall, just floats down slowly
        val gravity = if (petType == PetType.BLOOP) 0.3f else petType.gravity
        val terminalV = if (petType == PetType.BLOOP) 4f else petType.terminalVelocity

        velocityY += gravity
        velocityY = velocityY.coerceAtMost(terminalV)
        params.y += velocityY.toInt()

        // Nube-Michi special: sway while falling (feather)
        if (petType == PetType.NUBE_MICHI) {
            params.x += (sin(time * 4f) * 2f).toInt()
            animRotation = sin(time * 3f) * 12f
        }

        // Ground collision
        if (params.y >= groundY) {
            params.y = groundY
            landVelocity = velocityY
            velocityY = 0f
            velocityX = 0f
            animRotation = 0f

            // Juicy landing: start squash if impact was significant
            if (landVelocity > 5f) {
                state = PetState.LANDING
                landTimer = 0f
            } else {
                state = PetState.IDLE
                resetAnimTransforms()
            }
        }

        updateWindowLayout(params)
    }

    /**
     * Landing Squash — The "juicy" feel.
     *
     * 4-frame sequence:
     *   1. SQUASH (0-0.08s): flatten vertically, stretch horizontally
     *   2. OVERSHOOT (0.08-0.18s): bounce back past normal
     *   3. SETTLE (0.18-0.35s): ease to rest
     */
    private fun updateLandingSquash(dt: Float) {
        landTimer += dt
        val intensity = (landVelocity / petType.terminalVelocity).coerceIn(0.3f, 1f)

        when {
            landTimer < 0.08f -> {
                // Phase 1: SQUASH
                val t = landTimer / 0.08f
                animScaleX = 1f + t * 0.3f * intensity
                animScaleY = 1f - t * 0.28f * intensity
            }
            landTimer < 0.18f -> {
                // Phase 2: OVERSHOOT
                val t = (landTimer - 0.08f) / 0.10f
                animScaleX = (1f + 0.3f * intensity) - t * (0.3f * intensity + 0.08f)
                animScaleY = (1f - 0.28f * intensity) + t * (0.28f * intensity + 0.12f)
            }
            landTimer < LAND_SQUASH_DURATION -> {
                // Phase 3: SETTLE (ease out)
                val t = (landTimer - 0.18f) / 0.17f
                animScaleX = (1f - 0.08f) + t * 0.08f
                animScaleY = (1f + 0.12f) - t * 0.12f
            }
            else -> {
                // Done — bounce for Jelly, idle for others
                if (petType == PetType.JELLY && landVelocity > 12f) {
                    velocityY = -landVelocity * petType.bounceDamping
                    state = PetState.FALLING
                } else {
                    state = PetState.IDLE
                }
                resetAnimTransforms()
            }
        }
    }

    // ══════════════════════════════════════════════════════════
    // ▌ AUTONOMOUS MOVEMENT
    // ══════════════════════════════════════════════════════════

    private fun updateAutonomousMovement(dt: Float) {
        val params = getWindowParams() ?: return

        when (petType.movementStyle) {
            MovementStyle.DRIFT_SLOW -> {
                // Bloop: handled in idle animation
            }

            MovementStyle.STATIC_PERCH -> {
                // Nube-Michi: very rarely shifts position
            }

            MovementStyle.PARABOLIC_JUMP -> {
                // Jelly: periodic jumps
                moveTimer += dt
                if (moveTimer > nextMoveTime && params.y >= groundY - 5) {
                    velocityY = -(Random.nextFloat() * 12f + 8f)
                    velocityX = (Random.nextFloat() - 0.5f) * 6f
                    state = PetState.JUMPING
                    nextMoveTime = Random.nextFloat() * 2f + 1f
                    moveTimer = 0f
                }
            }

            MovementStyle.WALK_RUN -> {
                // Corgi: walks left/right
                moveTimer += dt
                if (moveTimer > nextMoveTime && !isMoving) {
                    velocityX = if (Random.nextBoolean()) {
                        Random.nextFloat() * 2.5f + 1f
                    } else {
                        -(Random.nextFloat() * 2.5f + 1f)
                    }
                    isMoving = true
                    moveActionTimer = 0f
                    nextMoveTime = Random.nextFloat() * 4f + 2f
                    moveTimer = 0f
                }

                if (isMoving) {
                    moveActionTimer += dt
                    params.x += velocityX.toInt()
                    params.x = params.x.coerceIn(0, screenWidth - petSpriteSize)

                    // Subtle walking bob
                    animOffsetY = abs(sin(moveActionTimer * 8f)) * 3f

                    if (moveActionTimer > 2.5f || params.x <= 0 || params.x >= screenWidth - petSpriteSize) {
                        velocityX = 0f
                        isMoving = false
                    }
                    updateWindowLayout(params)
                }
            }
        }
    }

    private fun updateJumping(dt: Float) {
        val params = getWindowParams() ?: return

        velocityY += petType.gravity
        velocityY = velocityY.coerceAtMost(petType.terminalVelocity)
        params.y += velocityY.toInt()
        params.x += velocityX.toInt()
        params.x = params.x.coerceIn(0, screenWidth - petSpriteSize)

        // Bounce off walls
        if (params.x <= 0 || params.x >= screenWidth - petSpriteSize) {
            velocityX = -velocityX * 0.8f
        }

        if (params.y >= groundY) {
            params.y = groundY
            landVelocity = velocityY
            velocityY = 0f

            if (landVelocity > 5f) {
                state = PetState.LANDING
                landTimer = 0f
            } else {
                state = PetState.IDLE
            }
        }

        updateWindowLayout(params)
    }

    private fun updateWalking(dt: Float) {
        // Same as autonomous movement walk, but as a state
        updateAutonomousMovement(dt)
        if (!isMoving) state = PetState.IDLE
    }

    // ══════════════════════════════════════════════════════════
    // ▌ SECRET LIFE (Rare events after inactivity)
    // ══════════════════════════════════════════════════════════

    private fun updateIdleTimer(dt: Float) {
        idleTimer += dt

        val unlocked = progress?.unlockedBehaviors ?: 1
        if (idleTimer > SECRET_IDLE_WAIT && !isSecretActive && unlocked >= 2) {
            val roll = Random.nextInt(1000)
            when {
                roll < 200 -> {
                    // Common secret activities (20%)
                    secretEmoji = listOf(
                        "💤", "📖", "🎣", "🎮", "☕", "🧘", "🎨", "🎵",
                        "🔭", "🪴", "🧹", "📱", "🍿", "🎲", "✏️"
                    ).random()
                    startSecretEvent(secretEmoji)
                }
                roll < 250 -> {
                    // Pet-specific secrets
                    secretEmoji = when (petType) {
                        PetType.CORGI -> { corgiLickTimer = 0f; "👅" }
                        PetType.BLOOP -> "👀"
                        PetType.JELLY -> "🫠"
                        PetType.NUBE_MICHI -> "🌧️"
                    }
                    startSecretEvent(secretEmoji)
                }
                roll == 999 -> {
                    // ULTRA RARE: costume! (0.1%)
                    secretEmoji = listOf("🚀", "👑", "🎩", "🏴\u200d☠️", "🦸", "🧙", "🎅", "🤖").random()
                    startSecretEvent(secretEmoji)
                    progress?.trackRareEvent()
                }
            }
            idleTimer = 0f
        }
    }

    private fun startSecretEvent(emoji: String) {
        isSecretActive = true
        secretTimer = 0f
        state = PetState.SECRET_IDLE
        showBubble(emoji)
    }

    private fun updateSecretIdle(dt: Float) {
        secretTimer += dt
        if (secretTimer > 10f) {
            isSecretActive = false
            corgiLickTimer = -1f
            state = PetState.IDLE
            hideBubble()
        }
    }

    // ══════════════════════════════════════════════════════════
    // ▌ PERSONALITY BEHAVIORS
    // ══════════════════════════════════════════════════════════

    private var personalityTimer = 0f

    private fun updatePersonality(dt: Float) {
        personalityTimer += dt

        when (petType) {
            PetType.CORGI -> {
                // Occasionally "lick" the screen (rare, delightful)
                if (personalityTimer > 45f && corgiLickTimer < 0f && !showBubble) {
                    corgiLickTimer = 0f
                    showBubble("👅")
                    personalityTimer = 0f
                }
            }
            PetType.BLOOP -> {
                // Shy: if touched many times recently, hide more
                // (tracked via totalInteractions growing fast)
            }
            else -> {}
        }
    }

    // ══════════════════════════════════════════════════════════
    // ▌ INTERACTION STATE (Tap actions)
    // ══════════════════════════════════════════════════════════

    private fun triggerInteraction() {
        state = PetState.INTERACTING
        interactTimer = 0f
        velocityX = 0f
        velocityY = 0f
        progress?.trackInteraction()  // +5 XP!

        when (petType) {
            PetType.CORGI -> showBubble("💕")
            PetType.BLOOP -> showBubble("🫧")
            PetType.JELLY -> showBubble("✨")
            PetType.NUBE_MICHI -> showBubble("💤")
        }
    }

    private fun updateInteracting(dt: Float) {
        interactTimer += dt
        
        when (petType) {
            PetType.CORGI -> {
                // Tummy rub! Flips over and rolls back
                animRotation = 180f
                animScaleY = 1.05f
                animOffsetY = 15f // lower to ground when flipped
                
                // Roll back after 3 seconds
                if (interactTimer > 2.5f) {
                    animRotation = (180f * (3f - interactTimer) / 0.5f).coerceIn(0f, 180f)
                }
                
                if (interactTimer > 3f) {
                    resetAnimTransforms()
                    state = PetState.IDLE
                }
            }
            PetType.BLOOP -> {
                // Fades and shrinks (shy ghost)
                animScaleX = 0.8f
                animScaleY = 0.8f
                animAlpha = 0.5f
                
                if (interactTimer > 2f) {
                    resetAnimTransforms()
                    state = PetState.IDLE
                }
            }
            PetType.JELLY -> {
                // Massive continuous squish
                animScaleX = 1.4f + sin(interactTimer * 20f) * 0.2f
                animScaleY = 0.7f - sin(interactTimer * 20f) * 0.1f
                animOffsetY = 10f
                
                if (interactTimer > 1.5f) {
                    resetAnimTransforms()
                    state = PetState.IDLE
                }
            }
            PetType.NUBE_MICHI -> {
                // Curls up tighter and glows
                animScaleX = 1.1f
                animScaleY = 0.9f
                animAlpha = 0.9f
                
                if (interactTimer > 2.5f) {
                    resetAnimTransforms()
                    state = PetState.IDLE
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════
    // ▌ SYSTEM REACTIONS
    // ══════════════════════════════════════════════════════════

    private fun triggerReaction(emoji: String) {
        reactionEmoji = emoji
        reactionTimer = 0f
        state = PetState.SYSTEM_REACTION
        showBubble(emoji)
    }

    private fun updateSystemReaction(dt: Float) {
        reactionTimer += dt
        // Happy bounce for positive reactions
        if (reactionEmoji == "⚡") {
            animOffsetY = -abs(sin(reactionTimer * 6f)) * 15f
            animScaleX = 1f + sin(reactionTimer * 8f) * 0.05f
        }
        // Droopy for negative reactions
        if (reactionEmoji == "😴") {
            animScaleY = 0.92f
            animOffsetY = 3f
        }

        if (reactionTimer > SYSTEM_REACTION_DURATION) {
            state = PetState.IDLE
            resetAnimTransforms()
            hideBubble()
        }
    }

    // ══════════════════════════════════════════════════════════
    // ▌ BUBBLE SYSTEM
    // ══════════════════════════════════════════════════════════

    private fun showBubble(text: String) {
        showBubble = true
        bubbleText = text
        bubbleTimer = 0f
        bubbleAlpha = 0f
    }

    private fun hideBubble() {
        showBubble = false
        bubbleAlpha = 0f
    }

    private fun updateBubble(dt: Float) {
        if (!showBubble) return
        bubbleTimer += dt

        // Fade in/hold/fade out
        bubbleAlpha = when {
            bubbleTimer < 0.3f -> (bubbleTimer / 0.3f)
            bubbleTimer > 2.5f -> ((3f - bubbleTimer) / 0.5f).coerceIn(0f, 1f)
            else -> 1f
        }

        if (bubbleTimer > 3f) {
            showBubble = false
        }
    }

    // ══════════════════════════════════════════════════════════
    // ▌ DRAWING
    // ══════════════════════════════════════════════════════════

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height * 0.45f
        val blinkScale = getBlinkScaleY()

        // ── Evolution size scaling ──
        val evoScale = progress?.sizeMultiplier ?: 1f
        val finalScaleX = animScaleX * evoScale
        val finalScaleY = animScaleY * blinkScale * evoScale

        // ── 1. DROP SHADOW ──
        drawDynamicShadow(canvas, cx, finalScaleX)

        // ── 2. SPRITE ──
        spritePaint.alpha = (animAlpha * 255).toInt().coerceIn(0, 255)

        val halfW = (petSpriteSize / 2f) * finalScaleX
        val halfH = (petSpriteSize / 2f) * finalScaleY

        spriteRect.set(
            cx - halfW + animOffsetX,
            cy - halfH + animOffsetY,
            cx + halfW + animOffsetX,
            cy + halfH + animOffsetY
        )

        canvas.save()

        if (animRotation != 0f) {
            canvas.rotate(animRotation, cx + animOffsetX, cy + animOffsetY)
        }

        if (velocityX < -0.5f) {
            canvas.scale(-1f, 1f, cx, cy)
        }

        canvas.drawBitmap(spriteBitmap, null, spriteRect, spritePaint)
        canvas.restore()

        // ── 3. CORGI LICK SCREEN ──
        if (corgiLickTimer in 0f..2.5f) {
            drawCorgiLick(canvas, cx, cy + halfH)
        }

        // ── 4. SPEECH BUBBLE ──
        if (showBubble && bubbleAlpha > 0.01f) {
            drawBubble(canvas, cx, cy - halfH + animOffsetY)
        }
    }

    /**
     * Corgi licks the screen — a semi-transparent pink tongue mark.
     * Fades in and out, creating a delightful "cleaning" effect.
     */
    private fun drawCorgiLick(canvas: Canvas, cx: Float, bottomY: Float) {
        val t = corgiLickTimer
        if (t < 0) return

        // Tongue mark alpha: fade in 0-0.3, hold 0.3-2.0, fade out 2.0-2.5
        val alpha = when {
            t < 0.3f -> (t / 0.3f * 100).toInt()
            t > 2.0f -> ((2.5f - t) / 0.5f * 100).toInt()
            else -> 100
        }.coerceIn(0, 100)

        lickPaint.alpha = alpha

        // Wavy tongue mark sweeping up
        val sweepY = bottomY - (t / 2.5f) * petSpriteSize * 0.6f
        val lickWidth = petSpriteSize * 0.15f
        val wobble = sin(t * 8f) * 8f

        canvas.drawOval(
            cx - lickWidth + wobble, sweepY - lickWidth * 2,
            cx + lickWidth + wobble, sweepY + lickWidth,
            lickPaint
        )
    }

    /**
     * Dynamic shadow that responds to pet's height above ground.
     * Closer to ground → darker, larger. High up → lighter, smaller.
     */
    private fun drawDynamicShadow(canvas: Canvas, cx: Float, scaleX: Float) {
        val params = getWindowParams()
        val distFromGround = if (params != null) {
            ((groundY - params.y).toFloat() / groundY).coerceIn(0f, 1f)
        } else {
            0f
        }

        // Shadow gets smaller and lighter as pet goes higher
        val shadowWidth = petSpriteSize * 0.35f * scaleX * (1f - distFromGround * 0.5f)
        val shadowHeight = 6f * (1f - distFromGround * 0.6f)
        val shadowAlpha = (0.25f * (1f - distFromGround * 0.7f) * animAlpha * 255).toInt().coerceIn(0, 80)

        val shadowY = height * 0.85f

        shadowPaint.color = Color.BLACK
        shadowPaint.alpha = shadowAlpha

        // Soft oval shadow with blur
        canvas.drawOval(
            cx - shadowWidth, shadowY - shadowHeight,
            cx + shadowWidth, shadowY + shadowHeight,
            shadowPaint
        )
    }

    private fun drawBubble(canvas: Canvas, cx: Float, topY: Float) {
        val bx = cx + petSpriteSize * 0.2f
        val by = topY - 20f
        val r = 22f

        val intAlpha = (bubbleAlpha * 255).toInt()
        bubbleBgPaint.alpha = intAlpha
        bubbleStrokePaint.alpha = intAlpha
        bubbleTextPaint.alpha = intAlpha

        // Rounded rectangle bubble
        canvas.drawRoundRect(bx - r, by - r, bx + r, by + r * 0.5f, 10f, 10f, bubbleBgPaint)
        canvas.drawRoundRect(bx - r, by - r, bx + r, by + r * 0.5f, 10f, 10f, bubbleStrokePaint)

        // Tiny triangle pointer
        val path = Path().apply {
            moveTo(bx - 6f, by + r * 0.5f)
            lineTo(bx + 2f, by + r * 0.5f + 8f)
            lineTo(bx + 4f, by + r * 0.5f)
            close()
        }
        canvas.drawPath(path, bubbleBgPaint)

        // Emoji text
        canvas.drawText(bubbleText, bx, by - 2f, bubbleTextPaint)
    }

    // ══════════════════════════════════════════════════════════
    // ▌ TOUCH HANDLING
    // ══════════════════════════════════════════════════════════

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val params = getWindowParams() ?: return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // ── Double-tap detection ──
                val now = System.currentTimeMillis()
                if (now - lastTapTime < DOUBLE_TAP_THRESHOLD) {
                    tapCount++
                    if (tapCount >= 2) {
                        doubleTapDodge()
                        tapCount = 0
                        return true
                    }
                } else {
                    tapCount = 1
                }
                lastTapTime = now

                // ── Start drag ──
                isDragging = true
                state = PetState.DRAGGING
                velocityX = 0f
                velocityY = 0f
                idleTimer = 0f  // Reset idle timer
                isSecretActive = false
                dragStartTime = now

                touchOffsetX = event.rawX - params.x
                touchOffsetY = event.rawY - params.y
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    // Check if we moved out of tap radius
                    val dx = event.rawX - (params.x + touchOffsetX)
                    val dy = event.rawY - (params.y + touchOffsetY)
                    if (sqrt(dx * dx + dy * dy) > 20f) {
                        params.x = (event.rawX - touchOffsetX).toInt()
                        params.y = (event.rawY - touchOffsetY).toInt()
                        updateWindowLayout(params)
                    }
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    
                    val now = System.currentTimeMillis()
                    val dx = event.rawX - (params.x + touchOffsetX)
                    val dy = event.rawY - (params.y + touchOffsetY)
                    
                    // If it was a quick touch with barely any movement, treat as a Tap
                    if (now - dragStartTime < 300 && sqrt(dx * dx + dy * dy) < 20f && event.action == MotionEvent.ACTION_UP) {
                        triggerInteraction()
                        return true
                    }

                    // Otherwise regular drop/fall
                    state = PetState.FALLING
                    velocityY = 2f  // Initial fall velocity
                    animRotation = 0f
                    resetAnimTransforms()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * Double-tap Dodge — Pet jumps away to clear the area.
     * Smart UX: doesn't block what's underneath.
     */
    private fun doubleTapDodge() {
        val params = getWindowParams() ?: return

        // Jump to opposite side of screen
        val targetX = if (params.x < screenWidth / 2) {
            screenWidth - petSpriteSize - 20
        } else {
            20
        }
        val targetY = (Random.nextFloat() * screenHeight * 0.3f).toInt() + 100

        params.x = targetX
        params.y = targetY
        updateWindowLayout(params)

        state = PetState.FALLING
        velocityY = 0f
        showBubble("😮")
    }

    // ══════════════════════════════════════════════════════════
    // ▌ KEYBOARD DODGE
    // ══════════════════════════════════════════════════════════

    private fun moveAboveKeyboard(keyboardHeight: Int) {
        val params = getWindowParams() ?: return
        val safeY = screenHeight - keyboardHeight - petSpriteSize - 30

        if (params.y > safeY) {
            params.y = safeY.coerceAtLeast(50)
            updateWindowLayout(params)
            showBubble("⬆️")
        }
    }

    // ══════════════════════════════════════════════════════════
    // ▌ HELPERS
    // ══════════════════════════════════════════════════════════

    private fun resetAnimTransforms() {
        animScaleX = 1f
        animScaleY = 1f
        animAlpha = 1f
        animOffsetX = 0f
        animOffsetY = 0f
        animRotation = 0f
    }

    private fun getWindowParams(): WindowManager.LayoutParams? {
        return layoutParams as? WindowManager.LayoutParams
    }

    private fun updateWindowLayout(params: WindowManager.LayoutParams) {
        try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.updateViewLayout(this, params)
        } catch (_: Exception) {}
    }

    private fun applyWindowOffset(dx: Int, dy: Int) {
        val params = getWindowParams() ?: return
        params.x = (params.x + dx).coerceIn(0, screenWidth - petSpriteSize)
        params.y = (params.y + dy).coerceIn(50, groundY)
        updateWindowLayout(params)
    }

    override fun onDetachedFromWindow() {
        pauseAnimation()
        super.onDetachedFromWindow()
    }
}
