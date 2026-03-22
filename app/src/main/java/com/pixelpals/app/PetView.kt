package com.pixelpals.app

import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.*
import kotlin.random.Random
import android.util.Log

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
        private const val TAG = "PetView"
        private const val FRAME_DELAY_MS = 28L     // ~35 FPS (smooth + battery friendly)
        private const val GROUND_MARGIN = 120
        private const val BLINK_MIN_INTERVAL = 3f  // seconds
        private const val BLINK_MAX_INTERVAL = 7f
        private const val BLINK_DURATION = 0.15f   // seconds
        private const val LAND_SQUASH_DURATION = 0.35f
        private const val SECRET_IDLE_WAIT = 25f   // seconds before secret events
        private const val DOUBLE_TAP_THRESHOLD = 300L // ms
        private const val SYSTEM_REACTION_DURATION = 3f
        private const val GINGER_WINK_INTERVAL = 120f // seconds between winks
        private const val JUMP_VELOCITY_THRESHOLD = 5f // velocity to trigger landing squash
    }

    // ══════════════════════════════════════════════════════════
    // ▌ SPRITE FRAMES (Multi-frame animation system: 4-6 frames per pet)
    // ══════════════════════════════════════════════════════════

    private val spriteFrames: List<Bitmap>      // Bloop: 4, Nube-Michi: 4, Corgi: 12, Jelly: 12, Ginger: 11
    private var currentFrame = 0
    private var frameTimer = 0f
    private val frameInterval = 0.15f           // 150ms per frame
    private val spritePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val spriteRect = RectF()

    // Nube-Michi pluma (feather) for falling
    private var nubePlumaBitmap: Bitmap? = null
    private var showPluma = false

    // ══════════════════════════════════════════════════════════
    // ▌ PARTICLES
    // ══════════════════════════════════════════════════════════

    private data class Particle(
        var x: Float, var y: Float,
        var vx: Float, var vy: Float,
        var alpha: Float, var size: Float,
        var life: Float, val maxLife: Float,
        val color: Int = Color.WHITE
    )

    private val particles = mutableListOf<Particle>()
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // ══════════════════════════════════════════════════════════
    // ▌ HAPTICS
    // ══════════════════════════════════════════════════════════

    @Suppress("DEPRECATION")
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

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

    // ══════════════════════════════════════════════════════════
    // ▌ GINGER STATE MACHINE
    // ══════════════════════════════════════════════════════════
    // Frames: 0=stretchFwd, 1=stretchBack, 2=standing, 3=wink,
    //         4=sitting, 5=lickPaw, 6=cleanFace, 7=pout,
    //         8=bellyStart, 9=rolling, 10=bellyUp

    enum class GingerPose {
        SITTING,        // ginger_4 - base idle
        STANDING,       // ginger_2 - on all fours, ready to move
        STRETCH_UP,     // ginger_4 → ginger_0 → ginger_1 → ginger_2 (sitting to standing)
        STRETCH_DOWN,   // ginger_2 → ginger_1 → ginger_0 → ginger_4 (standing to sitting)
        WALKING,        // ginger_2 with walking animation
        STRETCH_FWD,    // ginger_0 - mid-stretch while walking pause
        GROOMING_FACE,  // ginger_6 - cleaning face
        GROOMING_PAW,   // ginger_5 - licking paw
        WINK,           // ginger_3 - wink
        POUT,           // ginger_7 - ignored pout
        BELLY_RUB,      // ginger_8 → ginger_9 → ginger_10
        FALLING         // ginger_1 - stretch in air, land on feet
    }

    private var gingerPose = GingerPose.SITTING
    private var gingerPoseTimer = 0f
    private var gingerTransitionFrames = listOf<Int>()
    private var gingerTransitionDurations = listOf<Float>()
    private var gingerTransitionIndex = 0
    private var gingerIsTransitioning = false

    // Ginger timers
    private var gingerGroomingTimer = 0f
    private var gingerGroomingCooldown = 0f
    private var gingerWinkTimer = 0f
    private var gingerIsWinking = false
    private var gingerDoubleXPActive = false
    private var gingerPurrIntensity = 0f
    private var gingerIdleSitTimer = 0f  // Time sitting idle before grooming
    private var gingerWalkTimer = 0f
    private var gingerWalkPauseTimer = 0f

    // ══════════════════════════════════════════════════════════
    // ▌ GINGER WHIMSY ENGINE
    // ══════════════════════════════════════════════════════════
    private var gingerBoredomTimer = 0f           // Accumulates when not interacted
    private var gingerPoutActive = false           // Is Ginger pouting?
    private var gingerLaserJumpActive = false      // Is Ginger chasing the "laser"?
    private var gingerLaserTargetX = 0f
    private var gingerLaserTargetY = 0f
    private var gingerAffectionLevel = 0           // 0-100, earns belly rub at 80+
    private var gingerDailyGiftGiven = false       // Once per session
    private var gingerGroomingPauseTimer = 0f      // Pause between grooming actions
    private var gingerGroomingPhase = 0            // 0=groom, 1=pause/look, 2=groom, 3=pause
    private var gingerSequenceFrames = listOf<Int>()
    private var gingerSequenceDurations = listOf<Float>()
    private var gingerSequenceIndex = 0
    private var gingerSequenceTimer = 0f
    private var gingerIsPlayingSequence = false

    // ══════════════════════════════════════════════════════════
    // ▌ DUCK BRAIN - Patito Curioso
    // ══════════════════════════════════════════════════════════
    // Frames: 0=idle side, 1=waddle left, 2=waddle right, 3=peek front,
    //         4=curious close, 5=neutral, 6-8=cleaning, 9-11=pecking,
    //         12=focus, 13=quack start, 14=quack big

    enum class DuckState {
        IDLE_SIDE,      // Frame 0: Looking sideways
        IDLE_PEEK,      // Frame 3: Peeking at user
        WALKING,        // Frames 1-2: Waddle cycle
        CURIOSITY,      // Frames 3→4→12: Looking at user
        CLEANING,       // Frames 6→7→8: Preening feathers
        PECKING,        // Frames 9→10→11: Pecking ground
        QUACK_SUPREMO,  // Frames 3→13→14: Big quack reaction
        CONFUSED,       // Frames 3-4: At screen edge
        DRAGGING,       // Frame 0: Being held
        FALLING,        // Frames 1-2 rapid: Flying attempt
        SWIMMING,       // Frames 1-2 slow: Swimming in water
        WATER_EXIT,     // Frames 13→14: Jumping out of water
        FLYING          // Frames 1-2 rapid: Flying/falling attempt
    }

    private var duckState = DuckState.IDLE_SIDE
    private var duckDecisionTimer = 0f
    private var duckNextDecision = 3f + Random.nextFloat() * 3f // 3-6 seconds
    private var duckWaddleTimer = 0f
    private var duckActivityTimer = 0f
    private var duckIdleTime = 0f
    private var duckSequenceFrames = listOf<Int>()
    private var duckSequenceDurations = listOf<Float>()
    private var duckSequenceIndex = 0
    private var duckSequenceTimer = 0f
    private var duckIsPlayingSequence = false
    private var duckWalkDirection = 1f // 1 = right, -1 = left
    private var duckConfusedTimer = 0f
    private var duckSwimTimer = 0f
    private var duckFlyTimer = 0f
    private val waterZoneY: Int get() = (screenHeight * 0.7).toInt() // Bottom 30% is water

    // ══════════════════════════════════════════════════════════
    // ▌ CHAOS ENGINE - Diablillo Travieso
    // ══════════════════════════════════════════════════════════
    // Frames: 0-1=lurking idle, 2-3=running, 4=surprise/jump, 5=fire/mischief

    enum class ImpState {
        LURKING,        // Frames 0-1: Watching from shadows
        RUNNING,        // Frames 2-3: Snappy sprint
        SURPRISED,      // Frame 4: Caught/surprised
        FIRE_JUMP,      // Frames 4→5: Chaotic jump with fire
        TELEPORTING,    // Invisible, reappearing
        BURNED_OUT      // After being dragged too long
    }

    private var impState = ImpState.LURKING
    private var impDecisionTimer = 0f
    private var impNextDecision = 1f + Random.nextFloat() * 2f // 1-3 seconds (chaotic)
    private var impRunTimer = 0f
    private var impLurkTimer = 0f
    private var impDragTimer = 0f
    private var impIsBurned = false
    private var impTeleportCooldown = 0f

    /** ChaosEngine decision - unpredictable behavior */
    private fun impMakeDecision() {
        val roll = Random.nextFloat()
        when {
            roll < 0.35f -> {
                // 35%: Sprint to random position
                impStartRunning()
            }
            roll < 0.55f -> {
                // 20%: Teleport to edge
                impTeleport()
            }
            roll < 0.75f -> {
                // 20%: Stay lurking
                impState = ImpState.LURKING
                currentFrame = if (Random.nextBoolean()) 0 else 1
            }
            roll < 0.90f -> {
                // 15%: Jump toward center (fire jump)
                impFireJump()
            }
            else -> {
                // 10%: Jump scare toward user
                impJumpScare()
            }
        }
        impNextDecision = 0.8f + Random.nextFloat() * 2f // 0.8-2.8 seconds
    }

    /** Start snappy running */
    private fun impStartRunning() {
        impState = ImpState.RUNNING
        impRunTimer = 0f
        val targetX = Random.nextInt(40, screenWidth - petSpriteSize - 40)
        val params = getWindowParams() ?: return
        velocityX = if (targetX > params.x) (4f + Random.nextFloat() * 3f) else -(4f + Random.nextFloat() * 3f)
        // Snappy: stop abruptly after short distance
    }

    /** Teleport to random edge */
    private fun impTeleport() {
        if (impTeleportCooldown > 0f) return
        impState = ImpState.TELEPORTING
        animAlpha = 0f // Disappear

        handler.postDelayed({
            val params = getWindowParams() ?: return@postDelayed
            // Reappear at random edge
            params.x = if (Random.nextBoolean()) 20 else screenWidth - petSpriteSize - 20
            params.y = Random.nextInt(100, groundY - 100)
            updateWindowLayout(params)
            animAlpha = 1f // Reappear
            impState = ImpState.LURKING
            currentFrame = 4 // Surprised face
            showBubble("😈")
            playHaptic(30)
            impTeleportCooldown = 3f // Can't teleport again for 3s
        }, 200)
    }

    /** Fire jump - chaotic jump with fire emoji */
    private fun impFireJump() {
        impState = ImpState.FIRE_JUMP
        val params = getWindowParams() ?: return
        velocityY = -15f - Random.nextFloat() * 8f
        velocityX = (Random.nextFloat() - 0.5f) * 12f
        currentFrame = 5 // Fire frame
        showBubble("🔥")
        playHaptic(60)
    }

    /** Jump scare toward user's last touch */
    private fun impJumpScare() {
        impState = ImpState.SURPRISED
        currentFrame = 4 // Surprised frame
        val params = getWindowParams() ?: return
        // Jump toward last touch position
        val targetX = lastTouchX.toInt().coerceIn(30, screenWidth - petSpriteSize - 30)
        velocityX = (targetX - params.x) * 0.1f * petType.agility
        velocityY = -12f
        showBubble("👹")
        playHaptic(50)
    }

    /** Staccato haptic - three ultra-fast pulses */
    private fun playStaccatoHaptic() {
        playHaptic(40)
        handler.postDelayed({ playHaptic(30) }, 50)
        handler.postDelayed({ playHaptic(40) }, 100)
    }

    /** Update when burned out from dragging */
    private fun impUpdateBurned(dt: Float) {
        // Red tint handled in onDraw
        animOffsetY = sin(time * 20f) * 3f // Shaking
        impRunTimer += dt
        if (impRunTimer > 1.5f) {
            impIsBurned = false
            impState = ImpState.LURKING
            currentFrame = 0
            animColorFilter = null // Remove red tint
        }
    }

    private var animColorFilter: android.graphics.ColorFilter? = null

    // ── Ginger Transition Functions ──

    /** Start transition: SITTING → STANDING */
    private fun gingerStartStand() {
        if (gingerPose == GingerPose.STANDING || gingerIsTransitioning) return
        gingerIsTransitioning = true
        gingerTransitionFrames = listOf(4, 0, 1, 2) // sit → stretchFwd → stretchBack → stand
        gingerTransitionDurations = listOf(0.5f, 0.8f, 0.8f, 0.5f) // Slower, more elegant
        gingerTransitionIndex = 0
        gingerPoseTimer = 0f
        currentFrame = gingerTransitionFrames[0]
    }

    /** Start transition: STANDING → SITTING */
    private fun gingerStartSit() {
        if (gingerPose == GingerPose.SITTING || gingerIsTransitioning) return
        gingerIsTransitioning = true
        gingerTransitionFrames = listOf(2, 1, 0, 4) // stand → stretchBack → stretchFwd → sit
        gingerTransitionDurations = listOf(0.5f, 0.8f, 0.8f, 0.5f) // Slower, more elegant
        gingerTransitionIndex = 0
        gingerPoseTimer = 0f
        currentFrame = gingerTransitionFrames[0]
    }

    /** Start grooming sequence (only when sitting) */
    private fun gingerStartGrooming() {
        if (gingerPose != GingerPose.SITTING) return
        gingerIsTransitioning = true
        gingerTransitionFrames = listOf(4, 6, 5, 3, 4) // sit → cleanFace → lickPaw → wink → sit
        gingerTransitionDurations = listOf(2.0f, 2.5f, 2.5f, 1.5f, 1.0f) // Much slower grooming
        gingerTransitionIndex = 0
        gingerPoseTimer = 0f
        currentFrame = gingerTransitionFrames[0]
    }

    /** Start belly rub / petting sequence - rolls and tumbles */
    private fun gingerStartBellyRub() {
        gingerIsTransitioning = true
        // ginger_7 (lay down) → ginger_8 (start roll) → ginger_9 (rolling) → ginger_10 (belly up)
        gingerTransitionFrames = listOf(7, 8, 9, 10)
        gingerTransitionDurations = listOf(1.0f, 1.2f, 1.5f, 4.0f) // Slow, playful rolling
        gingerTransitionIndex = 0
        gingerPoseTimer = 0f
        gingerPose = GingerPose.BELLY_RUB
        currentFrame = gingerTransitionFrames[0]
    }

    /** Update transition animation */
    private fun updateGingerTransition(dt: Float): Boolean {
        if (!gingerIsTransitioning) return false
        gingerPoseTimer += dt
        if (gingerTransitionIndex < gingerTransitionDurations.size &&
            gingerPoseTimer >= gingerTransitionDurations[gingerTransitionIndex]) {
            gingerTransitionIndex++
            gingerPoseTimer = 0f
            if (gingerTransitionIndex < gingerTransitionFrames.size) {
                currentFrame = gingerTransitionFrames[gingerTransitionIndex]
                // Haptic feedback on stretch frames
                if (currentFrame == 0 || currentFrame == 1) {
                    playHaptic(15)
                }
            }
        }
        if (gingerTransitionIndex >= gingerTransitionFrames.size) {
            gingerIsTransitioning = false
            // Set final pose
            val finalFrame = gingerTransitionFrames.last()
            when (finalFrame) {
                2 -> gingerPose = GingerPose.STANDING
                4 -> gingerPose = GingerPose.SITTING
                10 -> gingerPose = GingerPose.BELLY_RUB
            }
            currentFrame = finalFrame
            return false
        }
        return true
    }

    /** Ginger always lands on feet elegantly */
    private fun gingerLandOnFeet() {
        currentFrame = 2 // ginger_2: standing pose (on all fours)
        gingerPose = GingerPose.STANDING
        gingerIdleSitTimer = 0f
        playHaptic(25) // Landing haptic
        showBubble("🐱") // Confident landing
        state = PetState.IDLE
        resetAnimTransforms()
    }

    /** Play a micro-sequence of frames */
    private fun playGingerSequence(frames: List<Int>, durations: List<Float>) {
        if (frames.isEmpty() || frames.size != durations.size) return
        gingerIsPlayingSequence = true
        gingerSequenceFrames = frames
        gingerSequenceDurations = durations
        gingerSequenceIndex = 0
        gingerSequenceTimer = 0f
        currentFrame = frames[0]
    }

    /** Update micro-sequence playback */
    private fun updateGingerSequence(dt: Float): Boolean {
        if (!gingerIsPlayingSequence) return false
        gingerSequenceTimer += dt
        if (gingerSequenceIndex < gingerSequenceDurations.size &&
            gingerSequenceTimer >= gingerSequenceDurations[gingerSequenceIndex]) {
            gingerSequenceIndex++
            gingerSequenceTimer = 0f
            if (gingerSequenceIndex < gingerSequenceFrames.size) {
                currentFrame = gingerSequenceFrames[gingerSequenceIndex]
                // Haptic on stretch frames
                if (currentFrame == 0 || currentFrame == 1) playHaptic(15)
            }
        }
        if (gingerSequenceIndex >= gingerSequenceFrames.size) {
            gingerIsPlayingSequence = false
            return false
        }
        return true
    }

    /** Organic grooming with pauses: groom → look → groom → look */
    private fun updateGingerGrooming(dt: Float) {
        if (gingerPose != GingerPose.SITTING) return
        if (gingerIsPlayingSequence) return

        gingerGroomingPauseTimer += dt

        when (gingerGroomingPhase) {
            0 -> { // Grooming: clean face
                currentFrame = 6
                if (gingerGroomingPauseTimer > 2.5f) {
                    gingerGroomingPhase = 1
                    gingerGroomingPauseTimer = 0f
                }
            }
            1 -> { // Pause: look at screen (sitting pose, alert)
                currentFrame = 4
                if (gingerGroomingPauseTimer > 2f) {
                    gingerGroomingPhase = 2
                    gingerGroomingPauseTimer = 0f
                }
            }
            2 -> { // Grooming: lick paw
                currentFrame = 5
                if (gingerGroomingPauseTimer > 2.5f) {
                    gingerGroomingPhase = 3
                    gingerGroomingPauseTimer = 0f
                }
            }
            3 -> { // Pause: look around (alert frame)
                currentFrame = 2 // Briefly stand alert
                if (gingerGroomingPauseTimer > 1.5f) {
                    gingerGroomingPhase = 0
                    gingerGroomingPauseTimer = 0f
                    currentFrame = 4 // Back to sitting
                }
            }
        }
    }

    /** Ginger shows pout when bored */
    private fun updateGingerBoredom(dt: Float) {
        if (petType != PetType.GINGER) return
        if (state != PetState.IDLE) return

        gingerBoredomTimer += dt
        val boredomThreshold = 10f / petType.boredomRate

        if (gingerBoredomTimer > boredomThreshold && !gingerPoutActive) {
            gingerPoutActive = true
            currentFrame = 7 // Pout frame
            showBubble("😤")
            // Stay pouting until interacted
        }
    }

    /** Add affection and check for belly rub / gift */
    private fun gingerAddAffection(amount: Int) {
        gingerAffectionLevel = (gingerAffectionLevel + amount).coerceIn(0, 100)
        gingerBoredomTimer = 0f
        gingerPoutActive = false

        // At 80+ affection, offer belly rub
        if (gingerAffectionLevel >= 80 && gingerPose == GingerPose.STANDING) {
            gingerStartBellyRub()
            gingerAffectionLevel = 30 // Reset partially
            showBubble("😻")

            // Daily gift: ovillo de lana rosa
            if (!gingerDailyGiftGiven) {
                gingerDailyGiftGiven = true
                handler.postDelayed({
                    progress?.addTreasure("🧶")
                    showBubble("🧶💕")
                    playHaptic(50)
                }, 5000) // Give gift after belly rub
            }
        }
    }

    /** Ronroneo háptico durante drag */
    private var purrPhase = 0f
    private fun updateGingerPurrHaptic(dt: Float) {
        if (state != PetState.DRAGGING || petType != PetType.GINGER) return
        purrPhase += dt
        // Low frequency purr: ~50Hz simulation via alternating haptic
        val purrCycle = 0.02f // 50ms = 50Hz
        if (purrPhase >= purrCycle) {
            purrPhase = 0f
            playHaptic(8) // Very short, soft vibration
        }
    }

    /** Set from PetService after creation */
    fun setProgress(p: PetProgress) { progress = p }

    /** Airplane mode changed */
    fun onAirplaneModeChanged(enabled: Boolean) {
        isAirplaneMode = enabled
        if (enabled) triggerReaction("✈️")
    }

    // ══════════════════════════════════════════════════════════
    // ▌ DUCK BRAIN FUNCTIONS
    // ══════════════════════════════════════════════════════════

    /** Play a duck sequence animation */
    private fun playDuckSequence(frames: List<Int>, durations: List<Float>) {
        if (frames.isEmpty() || frames.size != durations.size) return
        duckIsPlayingSequence = true
        duckSequenceFrames = frames
        duckSequenceDurations = durations
        duckSequenceIndex = 0
        duckSequenceTimer = 0f
        currentFrame = frames[0]
    }

    /** Update duck sequence playback */
    private fun updateDuckSequence(dt: Float): Boolean {
        if (!duckIsPlayingSequence) return false
        duckSequenceTimer += dt
        if (duckSequenceIndex < duckSequenceDurations.size &&
            duckSequenceTimer >= duckSequenceDurations[duckSequenceIndex]) {
            duckSequenceIndex++
            duckSequenceTimer = 0f
            if (duckSequenceIndex < duckSequenceFrames.size) {
                currentFrame = duckSequenceFrames[duckSequenceIndex]
            }
        }
        if (duckSequenceIndex >= duckSequenceFrames.size) {
            duckIsPlayingSequence = false
            return false
        }
        return true
    }

    /** Duck decision engine - decides what to do next */
    private fun duckMakeDecision() {
        val roll = Random.nextInt(100)
        when {
            roll < 30 -> {
                // 30%: Walk somewhere
                duckState = DuckState.WALKING
                duckWalkDirection = if (Random.nextBoolean()) 1f else -1f
                velocityX = duckWalkDirection * (2f + Random.nextFloat() * 2f)
                duckActivityTimer = 0f
            }
            roll < 50 -> {
                // 20%: Clean/preen feathers
                duckState = DuckState.CLEANING
                playDuckSequence(listOf(6, 7, 8, 7, 6), listOf(0.8f, 0.8f, 1.0f, 0.8f, 0.6f))
                duckActivityTimer = 0f
            }
            roll < 70 -> {
                // 20%: Peck the ground
                duckState = DuckState.PECKING
                playDuckSequence(listOf(9, 10, 11, 10, 9), listOf(0.3f, 0.4f, 0.3f, 0.4f, 0.3f))
                duckActivityTimer = 0f
            }
            roll < 90 -> {
                // 20%: Look at user curiously
                duckState = DuckState.CURIOSITY
                playDuckSequence(listOf(3, 4, 12, 4, 3), listOf(0.5f, 0.8f, 1.5f, 0.8f, 0.5f))
                duckActivityTimer = 0f
            }
            else -> {
                // 10%: Just idle
                duckState = DuckState.IDLE_SIDE
                currentFrame = 0
                duckActivityTimer = 0f
            }
        }
    }

    /** Update duck idle - alternating between side and peek */
    private fun updateDuckIdle(dt: Float) {
        duckIdleTime += dt
        // Alternate between side view (0) and peek (3) every 1.5s
        val cycle = (duckIdleTime % 3f)
        currentFrame = if (cycle < 1.5f) 0 else 3
    }

    /** Update duck waddle walking with sinusoidal Y movement */
    private fun updateDuckWalk(dt: Float, params: WindowManager.LayoutParams) {
        duckWaddleTimer += dt
        duckActivityTimer += dt

        // Waddle frame cycle: alternate 1 and 2 every 100ms
        currentFrame = if ((duckWaddleTimer * 10f).toInt() % 2 == 0) 1 else 2

        // Sinusoidal Y movement for waddle effect
        val waddleAmplitude = 3f
        animOffsetY = sin(duckWaddleTimer * 12f) * waddleAmplitude

        // Move horizontally
        params.x += (velocityX * dt * 60f).toInt()
        params.x = params.x.coerceIn(20, screenWidth - petSpriteSize - 20)

        // Check if hit screen edge - get confused
        if (params.x <= 25 || params.x >= screenWidth - petSpriteSize - 25) {
            velocityX = 0f
            duckState = DuckState.CONFUSED
            duckConfusedTimer = 0f
            playDuckSequence(listOf(3, 4, 3, 4), listOf(0.5f, 1.0f, 0.5f, 0.5f))
            showBubble("🤔")
        }

        // Stop after walking a bit
        if (duckActivityTimer > 2f + Random.nextFloat() * 2f) {
            velocityX = 0f
            duckState = DuckState.IDLE_SIDE
            currentFrame = 0
        }

        updateWindowLayout(params)
    }

    /** Update duck confused state at screen edge */
    private fun updateDuckConfused(dt: Float) {
        duckConfusedTimer += dt
        // After being confused, turn around and walk opposite direction
        if (duckConfusedTimer > 2f) {
            duckWalkDirection *= -1f
            duckState = DuckState.WALKING
            velocityX = duckWalkDirection * (2f + Random.nextFloat() * 2f)
            duckActivityTimer = 0f
            duckWaddleTimer = 0f
        }
    }

    /** Duck quack supremo reaction */
    private fun duckQuackSupremo() {
        duckState = DuckState.QUACK_SUPREMO
        playDuckSequence(listOf(3, 13, 14, 13, 3), listOf(0.2f, 0.3f, 0.8f, 0.3f, 0.2f))
        showBubble("Quack!")
        playHaptic(80) // Sharp peck-like vibration
        duckActivityTimer = 0f
    }

    /** Duck swimming in water - slow waddle frames with wave motion */
    private fun updateDuckSwimming(dt: Float, params: WindowManager.LayoutParams) {
        duckSwimTimer += dt

        // Slow waddle animation (200ms per frame instead of 100ms)
        currentFrame = if ((duckSwimTimer * 5f).toInt() % 2 == 0) 1 else 2

        // Wave motion: horizontal + vertical sinusoidal
        animOffsetY = sin(duckSwimTimer * 3f) * 5f // Bobbing up and down
        animOffsetX = cos(duckSwimTimer * 2f) * 3f // Slight horizontal drift

        // Move slowly while swimming
        params.x += (velocityX * dt * 30f).toInt() // Slower than walking
        params.x = params.x.coerceIn(20, screenWidth - petSpriteSize - 20)

        // Keep duck at water level
        params.y = waterZoneY - petSpriteSize / 2

        // Random chance to jump out of water
        if (Random.nextFloat() < 0.01f && duckSwimTimer > 3f) {
            duckJumpOutOfWater()
        }

        updateWindowLayout(params)
    }

    /** Duck jumps out of water */
    private fun duckJumpOutOfWater() {
        duckState = DuckState.WATER_EXIT
        playDuckSequence(listOf(2, 13, 14, 13, 3), listOf(0.15f, 0.2f, 0.6f, 0.2f, 0.3f))
        velocityY = -18f // Jump up
        velocityX = (Random.nextFloat() - 0.5f) * 8f // Slight horizontal
        showBubble("💦🦆")
        playHaptic(40)
        duckActivityTimer = 0f
    }

    /** Duck flying/falling - rapid wing flapping */
    private fun updateDuckFlying(dt: Float) {
        duckFlyTimer += dt

        // Rapid frame cycling to simulate wing flapping (50ms per frame)
        currentFrame = if ((duckFlyTimer * 20f).toInt() % 2 == 0) 1 else 2

        // Flapping motion
        animScaleY = 0.95f + sin(duckFlyTimer * 25f) * 0.08f
        animOffsetY = sin(duckFlyTimer * 15f) * 4f

        // Slight horizontal drift
        val params = getWindowParams() ?: return
        params.x += (sin(duckFlyTimer * 2f) * 3f).toInt()
        params.x = params.x.coerceIn(20, screenWidth - petSpriteSize - 20)
        updateWindowLayout(params)
    }

    /** Check if duck is in water zone */
    private fun duckIsInWater(): Boolean {
        val params = getWindowParams() ?: return false
        return params.y >= waterZoneY - petSpriteSize
    }

    /** Start swimming when duck enters water */
    private fun duckStartSwimming() {
        if (duckState != DuckState.SWIMMING) {
            duckState = DuckState.SWIMMING
            duckSwimTimer = 0f
            velocityX = (Random.nextFloat() - 0.5f) * 4f // Slow drift while swimming
        }
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
    private var purrTimer = 0f
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
        if (petType == PetType.BLOOP) {
            // Load 12 frames for Bloop (ghost)
            spriteFrames = listOf(
                ContextCompat.getDrawable(context, R.drawable.fantasma_0)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.fantasma_1)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.fantasma_2)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.fantasma_3)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.fantasma_4)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.fantasma_5)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.fantasma_6)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.fantasma_7)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.fantasma_8)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.fantasma_9)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.fantasma_10)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.fantasma_11)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888)
            )
            // Ghostly transparency baseline
            animAlpha = 0.8f
        } else if (petType == PetType.NUBE_MICHI) {
            // Load custom storyboard frames for Nube-Michi (4 frames - cloud cat)
            spriteFrames = listOf(
                ContextCompat.getDrawable(context, R.drawable.gato_0)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.gato_1)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.gato_2)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.gato_3)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888)
            )
            // Load pluma (feather) - shown ONLY when falling
            nubePlumaBitmap = ContextCompat.getDrawable(context, R.drawable.pluma_0)!!
                .toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888)
        } else if (petType == PetType.CORGI) {
            // Load custom storyboard frames for Corgi (12 frames - playful dog)
            spriteFrames = listOf(
                ContextCompat.getDrawable(context, R.drawable.perro_0)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.perro_1)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.perro_2)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.perro_3)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.perro_4)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.perro_5)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.perro_6)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.perro_7)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.perro_8)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.perro_9)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.perro_10)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.perro_11)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888)
            )
        } else if (petType == PetType.JELLY) {
            // Load custom storyboard frames for Jelly (12 frames - bouncy slime)
            spriteFrames = listOf(
                ContextCompat.getDrawable(context, R.drawable.jelly_0)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.jelly_1)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.jelly_2)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.jelly_3)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.jelly_4)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.jelly_5)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.jelly_6)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.jelly_7)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.jelly_8)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.jelly_9)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.jelly_10)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.jelly_11)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888)
            )
            // Neon translucency baseline
            animAlpha = 0.95f
        } else if (petType == PetType.GINGER) {
            // Load 11 frames for Ginger's feline elegance system
            // Frame mapping: 0=stretch1, 1=stretch2, 2=stretch3/edge_lean, 3=wink,
            //                4=sit, 5=lick_paw, 6=clean_face, 7=pout_pose,
            //                8=belly_start, 9=roll, 10=belly_up
            spriteFrames = listOf(
                ContextCompat.getDrawable(context, R.drawable.ginger_0)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.ginger_1)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.ginger_2)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.ginger_3)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.ginger_4)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.ginger_5)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.ginger_6)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.ginger_7)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.ginger_8)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.ginger_9)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.ginger_10)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888)
            )
            currentFrame = 4 // Start with sitting pose
        } else if (petType == PetType.PATITO) {
            // Load 15 frames for Patito (curious duck)
            // 0=idle side, 1-2=waddle, 3=peek front, 4=curious close,
            // 5=neutral, 6-8=cleaning, 9-11=pecking, 12=focus, 13-14=quack supreme
            spriteFrames = listOf(
                ContextCompat.getDrawable(context, R.drawable.patito_0)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.patito_1)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.patito_2)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.patito_3)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.patito_4)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.patito_5)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.patito_6)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.patito_7)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.patito_8)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.patito_9)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.patito_10)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.patito_11)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.patito_12)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.patito_13)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.patito_14)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888)
            )
            currentFrame = 0 // Start with side view
        } else if (petType == PetType.DIABLILLO) {
            // Load 6 frames for Diablillo (mischievous imp)
            // 0-1: lurking idle, 2-3: running, 4: surprise/jump, 5: fire/mischief
            spriteFrames = listOf(
                ContextCompat.getDrawable(context, R.drawable.diablillo_0)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.diablillo_1)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.diablillo_2)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.diablillo_3)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.diablillo_4)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888),
                ContextCompat.getDrawable(context, R.drawable.diablillo_5)!!.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888)
            )
            currentFrame = 0 // Start lurking
        } else {
            // Load standard sprite and generate 4 animation frames with strict 32-bit depth
            val drawable = ContextCompat.getDrawable(context, petType.spriteResId)!!
            val rawBitmap = drawable.toBitmap(petSpriteSize, petSpriteSize, Bitmap.Config.ARGB_8888)
            val baseBitmap = removeBackground(rawBitmap)
            spriteFrames = generateFrames(baseBitmap)
        }

        setBackgroundColor(Color.TRANSPARENT)
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    /**
     * Generate 4 animation frames from a base sprite:
     *   Frame 0: Base (idle breath-in)
     *   Frame 1: Slight vertical squish (idle breath-out)
     *   Frame 2: Stretch up (action start — jump/excited)
     *   Frame 3: Wide squish (action peak — land/interact)
     */
    private fun generateFrames(base: Bitmap): List<Bitmap> {
        return listOf(
            base,                                          // Frame 0: base
            createScaledFrame(base, 1.03f, 0.97f),         // Frame 1: breath out
            createScaledFrame(base, 0.93f, 1.08f),         // Frame 2: stretch
            createScaledFrame(base, 1.10f, 0.90f)          // Frame 3: squish
        )
    }

    private fun createScaledFrame(base: Bitmap, sx: Float, sy: Float): Bitmap {
        val w = base.width
        val h = base.height
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.argb(0, 255, 255, 255), PorterDuff.Mode.CLEAR)
        val matrix = Matrix()
        matrix.setScale(sx, sy, w / 2f, h / 2f)
        canvas.drawBitmap(base, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return result
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
                pixels[i] = Color.argb(0, bgR, bgG, bgB) // Transparent with bleeding color
                
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

        // ── Frame animation cycling ──
        frameTimer += dt
        if (frameTimer >= frameInterval) {
            frameTimer = 0f
            if (petType == PetType.BLOOP) {
                currentFrame = when(state) {
                    PetState.DRAGGING, PetState.FALLING -> 2
                    PetState.SECRET_IDLE -> 3
                    else -> if (isBlinking) 1 else 0 // Only blink randomly every 3-7s!
                }
            } else if (petType == PetType.NUBE_MICHI) {
                currentFrame = when(state) {
                    PetState.DRAGGING, PetState.INTERACTING -> 4           // Awake/happy
                    PetState.FALLING -> if (sin(time * 4.0f) > 0) 2 else 3 // Curving left/right
                    else -> ((time * 1.0f).toInt() % 2)                    // Slow breathing every 1s
                }
            } else if (petType == PetType.CORGI) {
                currentFrame = when(state) {
                    PetState.DRAGGING, PetState.FALLING -> 1
                    PetState.WALKING -> ((time * 6.6f).toInt() % 3) // Alternates 0, 1, 2 every 150ms
                    PetState.INTERACTING -> {
                        if (isSecretActive) 5 else 3 + ((time * 3f).toInt() % 2)
                    }
                    else -> 0
                }
            } else if (petType == PetType.JELLY) {
                currentFrame = when(state) {
                    PetState.JUMPING -> 3 // Extensión vertical
                    PetState.FALLING -> 3
                    PetState.LANDING, PetState.INTERACTING, PetState.DRAGGING -> 2 // Flattened/Squash
                    else -> ((time * 0.83f).toInt() % 2) // Breathing 1200ms
                }
            } else {
                val isAction = state == PetState.INTERACTING || state == PetState.LANDING || state == PetState.JUMPING
                if (isAction) {
                    currentFrame = 2 + ((currentFrame - 1) % 2)  // Cycle frames 2-3 (action)
                } else {
                    currentFrame = (currentFrame + 1) % 2        // Cycle frames 0-1 (breathing)
                }
            }
        }

        // ── Particle system ──
        updateParticles(dt)

        // ── Nube-Michi purring (Long hold) ──
        if (petType == PetType.NUBE_MICHI && isDragging) {
            purrTimer += dt
            if (purrTimer > 0.55f && System.currentTimeMillis() - dragStartTime > 300) {
                purrTimer = 0f
                playHaptic(50) // Micro-vibration purr
            }
        } else {
            purrTimer = 0f
        }

        // Bloop: emit trail bubbles while moving
        if (petType == PetType.BLOOP && (state == PetState.FALLING || state == PetState.DRAGGING)) {
            if (Random.nextFloat() < 0.3f) emitBubble()
        }
        // Jelly: emit sparkles while jumping
        if (petType == PetType.JELLY && (state == PetState.JUMPING || state == PetState.INTERACTING)) {
            if (Random.nextFloat() < 0.4f) emitSparkle()
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
                // Bloop: Ethereal ghost floating with 12 frames
                // Frames: 0-3 base floating, 4-7 playful poses, 8-11 special effects
                animOffsetY = sin(time * 2.0f) * 20f
                animOffsetX = cos(time * 1.5f) * 10f
                animAlpha = 0.75f + sin(time * 3f) * 0.15f // Ghost transparency pulsating

                // Cycle through floating frames
                val floatCycle = (time * 0.6f) % 8f
                currentFrame = when {
                    floatCycle < 1.5f -> 0  // Base floating
                    floatCycle < 2.5f -> 1  // Float variant 1
                    floatCycle < 3.5f -> 2  // Float variant 2
                    floatCycle < 4.5f -> 3  // Float variant 3
                    floatCycle < 5.5f -> 4  // Playful peek
                    floatCycle < 6.5f -> 5  // Shy hide
                    floatCycle < 7.5f -> 6  // Curious look
                    else -> 7               // Return to base
                }

                // Random cute ghost reactions
                if (Random.nextFloat() < 0.004f && reactionTimer > 8f) {
                    val pop = listOf("👻", "🫧", "✨", "💫", "🌙", "⭐").random()
                    triggerReaction(pop)
                }
            }
            IdleStyle.BREATHING -> {
                // Nube-Michi: Cloud-like floating - gentle up/down, breathing animation
                // Frame 0-1: breathing variants, 2: perch, 3: playful
                val floatY = sin(time * 1.2f) * 15f  // Gentle float up/down
                val floatX = cos(time * 0.8f) * 8f   // Slight horizontal drift
                animOffsetY = floatY
                animOffsetX = floatX

                // Breathing scale animation
                val breathe = sin(time * 1.5f) * 0.04f
                animScaleY = 1f + breathe
                animScaleX = 1f - breathe * 0.3f

                // Cycle through frames slowly for variety
                val frameCycle = (time * 0.5f) % 4f
                currentFrame = when {
                    frameCycle < 1.5f -> 0  // Main cloud pose
                    frameCycle < 2.5f -> 1  // Breathing variant
                    frameCycle < 3.5f -> 2  // Perch/alert
                    else -> 3               // Playful pose
                }

                // Hide pluma when not falling
                showPluma = false
            }
            IdleStyle.SIT_BARK -> {
                // Corgi: Playful idle with 12 frames
                // Frames: 0-3 idle poses, 4-6 happy/playful, 7-9 walking, 10-11 special
                animOffsetX = sin(time * 2.2f) * 3f
                animOffsetY = abs(sin(time * 1.5f)) * 2f

                // Cycle through idle frames for variety
                val idleCycle = (time * 0.8f) % 6f
                currentFrame = when {
                    idleCycle < 1.5f -> 0  // Main idle
                    idleCycle < 2.5f -> 1  // Slight turn
                    idleCycle < 3.5f -> 2  // Alert
                    idleCycle < 4.5f -> 3  // Happy
                    idleCycle < 5.5f -> 4  // Playful pose
                    else -> 5              // Tail wag frame
                }

                // Randomly pop a dialog - more frequent and coquettish
                if (Random.nextFloat() < 0.008f && reactionTimer > 8f) {
                    val pop = listOf("Bark!", "❤️", "🦴", "🐶", "💕", "🎾", "🐾").random()
                    triggerReaction(pop)
                }
            }
            IdleStyle.JELLY_WOBBLE -> {
                // Jelly: Bouncy slime with 12 frames - wobbly and coquettish
                // Frames: 0-3 base idle, 4-6 wobble variants, 7-9 stretch, 10-11 special
                val sine = sin(time * Math.PI / 0.6f).toFloat()
                animScaleY = 1.0f + sine * 0.06f
                animScaleX = 1.0f - sine * 0.04f
                animOffsetY = (petSpriteSize / 2f) * (1f - animScaleY)

                // Cycle through frames for variety
                val wobbleCycle = (time * 1.2f) % 8f
                currentFrame = when {
                    wobbleCycle < 1.0f -> 0  // Base idle
                    wobbleCycle < 2.0f -> 1  // Wobble left
                    wobbleCycle < 3.0f -> 2  // Wobble right
                    wobbleCycle < 4.0f -> 3  // Happy squish
                    wobbleCycle < 5.0f -> 4  // Stretch up
                    wobbleCycle < 6.0f -> 5  // Bounce
                    wobbleCycle < 7.0f -> 6  // Tilt
                    else -> 7                // Return
                }

                // Random cute bubble pop
                if (Random.nextFloat() < 0.006f && reactionTimer > 6f) {
                    val pop = listOf("✨", "💖", "🫧", "🍬", "🌈", "💫").random()
                    triggerReaction(pop)
                }
            }
            IdleStyle.GROOMING -> {
                // Ginger: Whimsical cat with organic grooming and capricious behavior
                // Check if bored (pout)
                updateGingerBoredom(dt)

                // Update any ongoing micro-sequence
                if (updateGingerSequence(dt)) {
                    val breathe = sin(time * 2f) * 0.01f
                    animScaleY = 1f + breathe
                    return
                }

                // Update any ongoing transition
                if (updateGingerTransition(dt)) {
                    val breathe = sin(time * 2f) * 0.01f
                    animScaleY = 1f + breathe
                    return
                }

                // If pouting, stay on pout frame
                if (gingerPoutActive) {
                    currentFrame = 7
                    return
                }

                when (gingerPose) {
                    GingerPose.SITTING -> {
                        // Organic grooming: clean → pause → lick → pause → repeat
                        updateGingerGrooming(dt)

                        // Breathing animation
                        val breathe = sin(time * 1.5f) * 0.015f
                        animScaleY = 1f + breathe
                        animScaleX = 1f - breathe * 0.5f

                        // Wink timer while sitting
                        gingerWinkTimer += dt
                        if (gingerWinkTimer >= GINGER_WINK_INTERVAL && !gingerIsWinking) {
                            gingerIsWinking = true
                            gingerDoubleXPActive = true
                            gingerWinkTimer = 0f
                            // Play wink sequence
                            playGingerSequence(listOf(3, 4), listOf(1.5f, 0.5f))
                            showBubble("😉")
                            handler.postDelayed({
                                gingerDoubleXPActive = false
                                gingerIsWinking = false
                            }, 3000)
                        }
                    }
                    GingerPose.STANDING -> {
                        // Standing on all fours - alert, looking around
                        currentFrame = 2
                        animOffsetX = sin(time * 1.8f) * 1.5f
                        animOffsetY = abs(sin(time * 2.5f)) * 1f

                        // After standing idle, sit down or do a stretch
                        gingerIdleSitTimer += dt
                        if (gingerIdleSitTimer > 6f) {
                            if (Random.nextBoolean()) {
                                // Quick stretch before sitting
                                playGingerSequence(listOf(2, 1, 0, 4), listOf(0.4f, 0.6f, 0.6f, 0.3f))
                                gingerPose = GingerPose.SITTING
                            } else {
                                gingerStartSit()
                            }
                            gingerIdleSitTimer = 0f
                        }
                    }
                    else -> {
                        // Other poses keep their frame
                    }
                }
            }
            IdleStyle.DUCK_IDLE -> {
                // Patito: Curious duck idle with decision engine
                if (duckIsPlayingSequence) {
                    updateDuckSequence(dt)
                    return
                }

                duckDecisionTimer += dt

                when (duckState) {
                    DuckState.IDLE_SIDE, DuckState.IDLE_PEEK -> {
                        updateDuckIdle(dt)

                        // Make a decision after timer expires
                        if (duckDecisionTimer > duckNextDecision) {
                            duckMakeDecision()
                            duckDecisionTimer = 0f
                            duckNextDecision = 3f + Random.nextFloat() * 3f
                        }
                    }
                    DuckState.CURIOSITY -> {
                        duckActivityTimer += dt
                        if (duckActivityTimer > 3f) {
                            duckState = DuckState.IDLE_SIDE
                            currentFrame = 0
                        }
                    }
                    DuckState.CLEANING, DuckState.PECKING -> {
                        duckActivityTimer += dt
                        if (duckActivityTimer > 4f || !duckIsPlayingSequence) {
                            duckState = DuckState.IDLE_SIDE
                            currentFrame = 0
                        }
                    }
                    DuckState.QUACK_SUPREMO -> {
                        duckActivityTimer += dt
                        if (duckActivityTimer > 2f || !duckIsPlayingSequence) {
                            duckState = DuckState.IDLE_SIDE
                            currentFrame = 0
                        }
                    }
                    else -> {
                        currentFrame = 0
                    }
                }

                // Gentle breathing animation
                val breathe = sin(time * 1.8f) * 0.01f
                animScaleY = 1f + breathe
            }
            IdleStyle.LURK_IDLE -> {
                // Diablillo: Chaotic lurking with unpredictable decisions
                if (impIsBurned) {
                    impUpdateBurned(dt)
                    return
                }

                impDecisionTimer += dt
                impTeleportCooldown -= dt

                when (impState) {
                    ImpState.LURKING -> {
                        // Lurking: alternate frames 0 and 1 with long pauses
                        impLurkTimer += dt
                        currentFrame = if (impLurkTimer % 3f < 1.5f) 0 else 1

                        // Subtle menacing sway
                        animOffsetX = sin(time * 0.8f) * 2f
                        animOffsetY = abs(sin(time * 1.2f)) * 1f

                        // Make chaotic decision
                        if (impDecisionTimer > impNextDecision) {
                            impMakeDecision()
                            impDecisionTimer = 0f
                        }
                    }
                    ImpState.RUNNING -> {
                        impRunTimer += dt
                        // Snappy run cycle: frames 2-3 every 80ms
                        currentFrame = if ((impRunTimer * 12f).toInt() % 2 == 0) 2 else 3
                        animOffsetY = abs(sin(impRunTimer * 15f)) * 4f

                        // Snappy stop: abruptly stop after short distance
                        if (impRunTimer > 0.5f + Random.nextFloat() * 0.5f) {
                            velocityX = 0f
                            impState = ImpState.LURKING
                            currentFrame = 4 // Surprised at stopping
                            showBubble("😈")
                        }
                    }
                    ImpState.FIRE_JUMP -> {
                        // In air, will land via updateJumping
                        currentFrame = 5
                    }
                    ImpState.SURPRISED -> {
                        currentFrame = 4
                        impRunTimer += dt
                        if (impRunTimer > 1f) {
                            impState = ImpState.LURKING
                            currentFrame = 0
                        }
                    }
                    ImpState.TELEPORTING -> {
                        // Invisible during teleport
                        animAlpha = 0f
                    }
                    ImpState.BURNED_OUT -> {
                        impUpdateBurned(dt)
                    }
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════
    // ▌ DRAG ANIMATION ("Pataleo")
    // ══════════════════════════════════════════════════════════

    private fun updateDragAnimation(dt: Float) {
        // Drag physics per pet
        if (petType == PetType.NUBE_MICHI) {
            // High air friction for Cloud Cat (like a feather)
            animRotation = sin(time * 3f) * 15f
            showPluma = false // Cat is held, no pluma
            animAlpha = 1f // Ensure cat is visible
        } else if (petType == PetType.GINGER) {
            // Ginger: held gently with ronroneo háptico ~50Hz
            currentFrame = if (gingerPose == GingerPose.STANDING || gingerPose == GingerPose.WALKING) 2 else 4
            animRotation = 0f // Elegant - no wobble

            // Ronroneo háptico: low frequency purr simulation
            updateGingerPurrHaptic(dt)

            // Progressive purring intensity for visual feedback
            purrTimer += dt
            gingerPurrIntensity = (purrTimer / 3f).coerceIn(0f, 1f)

            // Subtle happy squish
            animScaleY = 1f - gingerPurrIntensity * 0.03f
            animScaleX = 1f + gingerPurrIntensity * 0.02f

            // Add affection while holding
            gingerAddAffection(1)
        } else if (petType == PetType.PATITO) {
            // Patito: Being held - look surprised
            currentFrame = 0 // Side view while held
            animRotation = 0f
            duckState = DuckState.DRAGGING
        } else if (petType == PetType.DIABLILLO) {
            // Diablillo: Being held - surprised then burn if held too long
            currentFrame = 4 // Surprised frame
            impDragTimer += dt
            impState = ImpState.SURPRISED

            // After 3 seconds, burn and escape
            if (impDragTimer > 3f && !impIsBurned) {
                impIsBurned = true
                impState = ImpState.BURNED_OUT
                impRunTimer = 0f
                // Red tint to simulate burning
                animColorFilter = android.graphics.LightingColorFilter(0xFFFF4444.toInt(), 0x00000000)
                showBubble("🔥😤")
                playStaccatoHaptic()
                // Force drop
                state = PetState.FALLING
                velocityY = 2f
                impDragTimer = 0f
            }
        } else {
            // General pataleo
            animRotation = sin(time * 15f) * 10f
        }
    }

    // ══════════════════════════════════════════════════════════
    // ▌ FALLING & LANDING
    // ══════════════════════════════════════════════════════════

    private fun updateFalling(dt: Float) {
        val params = getWindowParams() ?: return

        // Reset rotation from drag
        animRotation *= 0.85f
        animScaleX = 1f
        animScaleY = 1f + (velocityY / petType.terminalVelocity.coerceAtLeast(1f)) * 0.15f

        if (petType == PetType.BLOOP) {
            state = PetState.IDLE
            return
        }

        val gravity = petType.gravity
        val terminalV = petType.terminalVelocity

        velocityY += gravity
        velocityY = velocityY.coerceAtMost(terminalV)
        params.y += velocityY.toInt()

        // Nube-Michi special: TRANSFORM TO PLUMA when falling
        // The cat disappears and only the feather is visible
        if (petType == PetType.NUBE_MICHI) {
            params.x += (sin(time * 3.0f) * 8.0f).toInt() // Feather drifts more
            showPluma = true
            animAlpha = 0f // Cat is invisible - only pluma shows
            currentFrame = 0
        }

        // Patito special: Tries to fly when falling
        if (petType == PetType.PATITO) {
            duckState = DuckState.FLYING
            updateDuckFlying(dt)
            // Check if about to land in water
            if (params.y >= waterZoneY - petSpriteSize) {
                // Land in water - start swimming!
                params.y = waterZoneY - petSpriteSize
                velocityY = 0f
                duckStartSwimming()
                state = PetState.IDLE
                showBubble("💦")
                updateWindowLayout(params)
                return
            }
        }

        // Ginger special: Cat always lands on feet - graceful rotation to upright
        if (petType == PetType.GINGER) {
            gingerPose = GingerPose.FALLING

            // Calculate how far from ground (0 = ground, 1 = peak)
            val fallProgress = if (groundY > 0) ((groundY - params.y).toFloat() / groundY).coerceIn(0f, 1f) else 0f

            // Near ground: show landing pose (ginger_1 stretch back)
            // Higher up: tumbling through poses
            if (fallProgress < 0.3f) {
                // Close to ground - prepare to land
                currentFrame = 1 // ginger_1: stretch back (landing pose)
                // Auto-rotate to upright
                animRotation *= 0.7f // Dampen rotation quickly
            } else {
                // In air - graceful tumble
                val fallTime = time % 1.2f
                currentFrame = when {
                    fallTime < 0.3f -> 1  // ginger_1: stretch back
                    fallTime < 0.6f -> 8  // ginger_8: rolling start
                    fallTime < 0.9f -> 9  // ginger_9: rolling
                    else -> 0             // ginger_0: stretch forward
                }
                // Auto-rotate: spin to land on feet
                animRotation += dt * 120f * petType.agility // Faster rotation with agility
            }
            // Slight horizontal drift
            params.x += (sin(time * 3f) * 3f).toInt()
        }

        // Diablillo special: Can "fly" a bit before falling (gravedad inversa)
        if (petType == PetType.DIABLILLO) {
            currentFrame = 5 // Fire/flying frame
            // Random upward force to simulate "flying"
            if (Random.nextFloat() < 0.1f && velocityY > 0) {
                velocityY += -8f // Upward boost
                animOffsetY = sin(time * 10f) * 5f
            }
            // Check if burned out
            if (impIsBurned) {
                animColorFilter = android.graphics.LightingColorFilter(0xFFFF4444.toInt(), 0x00000000)
            }
        }

        // Limit to screen bounds collision
        if (params.y >= groundY) {
            // Patito: Check if landing in water zone
            if (petType == PetType.PATITO && params.y >= waterZoneY - petSpriteSize) {
                // Land in water - splash and swim!
                params.y = waterZoneY - petSpriteSize
                velocityY = 0f
                velocityX = 0f
                duckStartSwimming()
                state = PetState.IDLE
                showBubble("💦")
                updateWindowLayout(params)
                return
            }

            params.y = groundY
            landVelocity = velocityY
            velocityY = 0f
            velocityX = 0f
            animRotation = 0f

            // Ginger: Always lands on feet elegantly - lands standing, no squash
            if (petType == PetType.GINGER) {
                gingerLandOnFeet()
                return
            }

            // Juicy landing: start squash if impact was significant
            if (landVelocity > JUMP_VELOCITY_THRESHOLD) {
                state = PetState.LANDING
                landTimer = 0f
            } else {
                state = PetState.IDLE
                resetAnimTransforms()
            }
            // Hide pluma on landing - cat reappears
            if (petType == PetType.NUBE_MICHI) {
                showPluma = false
                animAlpha = 1f // Cat is visible again
                currentFrame = 2 // Perch frame after landing
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
        // Ginger always lands on feet - skip squash entirely
        if (petType == PetType.GINGER) {
            currentFrame = 4 // sitting pose
            resetAnimTransforms()
            state = PetState.IDLE
            return
        }

        landTimer += dt
        val progress = (landTimer / LAND_SQUASH_DURATION).coerceIn(0f, 1f)

        // Overshoot / springy squash
        // Peak squash at 20% of duration, then overshoot back to 1.0
        val spring = if (progress < 0.2f) {
            val t = progress / 0.2f
            1f + t * 0.4f
        } else {
            val t = (progress - 0.2f) / 0.8f
            1f + 0.4f * (1f - t) * cos(t * Math.PI * 3f).toFloat() // Dampened spring
        }

        val impact = (landVelocity / 1500f).coerceIn(0.5f, 1.5f)
        val materialMultiplier = if (petType == PetType.JELLY) 1.5f else 1.0f

        animScaleX = 1f + (spring - 1f) * impact * materialMultiplier
        animScaleY = 1f - (spring - 1f) * impact * materialMultiplier * 0.5f

        val scaleDiffY = 1f - animScaleY
        animOffsetY = (petSpriteSize / 2f) * scaleDiffY

        if (progress >= 1f) {
            resetAnimTransforms()
            // Done — bounce for Jelly, idle for others
            if (petType == PetType.JELLY && landVelocity > 12f) {
                // Spring-like double haptic feedback for strong landing
                playHaptic(30)
                handler.postDelayed({ playHaptic(15) }, 80)
                
                velocityY = -landVelocity * petType.bounceDamping
                state = PetState.FALLING
            } else {
                state = PetState.IDLE
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
                // Jelly: rhythmic charging and explosive jumping
                moveTimer += dt
                if (params.y >= groundY - 5 && state == PetState.IDLE) {
                    val chargeDuration = 0.4f
                    val jumpTime = (petType.jumpInterval / 1000f) + (nextMoveTime % 2f)
                    
                    if (moveTimer > jumpTime - chargeDuration && moveTimer <= jumpTime) {
                        // PRE-JUMP (SQUASH)
                        val progress = (moveTimer - (jumpTime - chargeDuration)) / chargeDuration
                        animScaleY = 1f - (0.1f * progress) // Compress 10%
                        animScaleX = 1f + (0.1f * progress) 
                        animOffsetY = (petSpriteSize / 2f) * (1f - animScaleY)
                    } else if (moveTimer > jumpTime) {
                        // EXPLOSIVE JUMP
                        velocityY = -22f
                        velocityX = (Random.nextFloat() - 0.5f) * 10f
                        state = PetState.JUMPING
                        animAlpha = 0.7f // Neon mode engaged
                        nextMoveTime = Random.nextFloat() * 2f
                        moveTimer = 0f
                    }
                }
            }

            MovementStyle.WALK_RUN -> {
                // Corgi: walks left/right, climbs edges
                moveTimer += dt
                if (moveTimer > nextMoveTime && !isMoving) {
                    val speedMult = if (petType == PetType.CORGI) 2.5f else 1.0f  // Super speed for Corgi
                    val maxSpeed = (2f + Random.nextFloat() * 1.5f) * speedMult
                    velocityX = if (Random.nextBoolean()) maxSpeed else -maxSpeed
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

                    // Digging for treasures (Corgi special - 1/500 per frame ~0.002f)
                    if (petType == PetType.CORGI && Random.nextFloat() < 0.002f) {
                        isMoving = false
                        velocityX = 0f
                        progress?.addTreasure("🦴")
                        playHaptic(100)
                        triggerReaction("🦴")
                        return
                    }

                    if (moveActionTimer > 2.5f || params.x <= 0 || params.x >= screenWidth - petSpriteSize) {
                        velocityX = 0f
                        isMoving = false
                    }
                    updateWindowLayout(params)
                }
            }

            MovementStyle.ELEGANT_STRETCH -> {
                // Ginger: Playful cat - walks everywhere, jumps occasionally, plays
                moveTimer += dt

                // Don't start moving if still transitioning
                if (gingerIsTransitioning) return

                when (gingerPose) {
                    GingerPose.SITTING -> {
                        // Sitting - stand up to move
                        if (moveTimer > nextMoveTime) {
                            gingerStartStand()
                            moveTimer = 0f
                        }
                    }
                    GingerPose.STANDING -> {
                        // Standing - choose next action: walk, jump, or play
                        if (!isMoving && moveTimer > 0.8f) {
                            val action = Random.nextInt(100)
                            when {
                                action < 60 -> {
                                    // Walk to random position (not just corners)
                                    val targetX = Random.nextInt(60, screenWidth - petSpriteSize - 60)
                                    val speed = 2f + Random.nextFloat() * 1.5f
                                    velocityX = if (targetX > params.x) speed else -speed
                                    isMoving = true
                                    gingerWalkTimer = 0f
                                    gingerPose = GingerPose.WALKING
                                }
                                action < 85 -> {
                                    // Walk to edge/corner
                                    val cornerX = if (Random.nextBoolean()) 40 else screenWidth - petSpriteSize - 40
                                    val speed = 2.5f + Random.nextFloat() * 1f
                                    velocityX = if (cornerX > params.x) speed else -speed
                                    isMoving = true
                                    gingerWalkTimer = 0f
                                    gingerPose = GingerPose.WALKING
                                }
                                else -> {
                                    // Playful jump!
                                    if (params.y >= groundY - 5) {
                                        velocityY = -15f - Random.nextFloat() * 8f
                                        velocityX = (Random.nextFloat() - 0.5f) * 12f
                                        state = PetState.JUMPING
                                        currentFrame = 1 // stretch/jump frame
                                        playHaptic(25)
                                    }
                                }
                            }
                            nextMoveTime = Random.nextFloat() * 3f + 2f // Move frequently
                            moveTimer = 0f
                        }
                    }
                    GingerPose.WALKING -> {
                        // Walking animation
                        gingerWalkTimer += dt
                        params.x += velocityX.toInt()
                        params.x = params.x.coerceIn(20, screenWidth - petSpriteSize - 20)

                        // Alternate stretch frames while walking (slower animation)
                        currentFrame = if ((gingerWalkTimer * 2.5f).toInt() % 2 == 0) 0 else 1
                        // Walking bob
                        animOffsetY = abs(sin(gingerWalkTimer * 5f)) * 3f

                        // Random chance to stop and stretch mid-walk
                        if (gingerWalkTimer > 1.5f && Random.nextFloat() < 0.01f) {
                            velocityX = 0f
                            isMoving = false
                            gingerPose = GingerPose.STRETCH_FWD
                            currentFrame = 0 // stretch forward
                            gingerWalkPauseTimer = 0f
                        }

                        // Reached destination or time to stop
                        val atLeftEdge = params.x <= 40
                        val atRightEdge = params.x >= screenWidth - petSpriteSize - 40
                        if (gingerWalkTimer > 4f || atLeftEdge || atRightEdge) {
                            velocityX = 0f
                            isMoving = false
                            gingerPose = GingerPose.STANDING
                            currentFrame = 2
                            // Flip sprite based on direction
                            animScaleX = if (velocityX > 0 || atRightEdge) -1f else 1f
                            nextMoveTime = Random.nextFloat() * 2f + 1f // Quick next action
                            moveTimer = 0f
                        }
                        updateWindowLayout(params)
                    }
                    GingerPose.STRETCH_FWD -> {
                        // Mid-walk stretch pause
                        gingerWalkPauseTimer += dt
                        currentFrame = 0
                        // After stretching, continue or sit
                        if (gingerWalkPauseTimer > 1.5f) {
                            if (Random.nextBoolean()) {
                                gingerStartSit()
                            } else {
                                gingerPose = GingerPose.STANDING
                                currentFrame = 2
                                moveTimer = 0f
                            }
                        }
                    }
                    else -> {
                        // Other poses: reset move timer
                        moveTimer = 0f
                    }
                }
            }
            MovementStyle.WADDLE_EXPLORE -> {
                // Patito: Waddle exploration with duck brain decisions
                if (petType != PetType.PATITO) return

                val params = getWindowParams() ?: return

                // Don't interrupt quack or curiosity sequences
                if (duckIsPlayingSequence || duckState == DuckState.QUACK_SUPREMO || duckState == DuckState.CURIOSITY) {
                    return
                }

                // Check if duck is in water zone - start swimming
                if (duckIsInWater() && duckState != DuckState.SWIMMING && duckState != DuckState.WATER_EXIT) {
                    duckStartSwimming()
                }

                when (duckState) {
                    DuckState.WALKING -> {
                        updateDuckWalk(dt, params)
                    }
                    DuckState.SWIMMING -> {
                        updateDuckSwimming(dt, params)
                    }
                    DuckState.WATER_EXIT -> {
                        duckActivityTimer += dt
                        if (duckActivityTimer > 1.5f || !duckIsPlayingSequence) {
                            duckState = DuckState.IDLE_SIDE
                            currentFrame = 0
                        }
                    }
                    DuckState.CONFUSED -> {
                        updateDuckConfused(dt)
                    }
                    else -> {
                        // Idle - let DuckBrain handle decisions in updateIdleAnimation
                    }
                }
            }
            MovementStyle.CHAOTIC_ZOOM -> {
                // Diablillo: Chaotic movement with teleports and sprints
                if (petType != PetType.DIABLILLO) return

                val params = getWindowParams() ?: return

                when (impState) {
                    ImpState.RUNNING -> {
                        // Snappy sprint
                        params.x += velocityX.toInt()
                        params.x = params.x.coerceIn(20, screenWidth - petSpriteSize - 20)
                        updateWindowLayout(params)
                    }
                    else -> {
                        // Decisions handled in updateIdleAnimation
                    }
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

        // Ginger: tumble gracefully while jumping
        if (petType == PetType.GINGER) {
            val jumpTime = time % 0.8f
            currentFrame = when {
                jumpTime < 0.2f -> 1  // stretch back
                jumpTime < 0.4f -> 8  // rolling start
                jumpTime < 0.6f -> 9  // rolling
                else -> 0             // stretch forward
            }
        }

        // Bounce off walls
        if (params.x <= 0 || params.x >= screenWidth - petSpriteSize) {
            velocityX = -velocityX * 0.8f
        }

        if (params.y >= groundY) {
            params.y = groundY
            landVelocity = velocityY
            velocityY = 0f
            
            if (petType == PetType.JELLY) {
                animAlpha = 0.95f // Reset Neon Trail
            }

            // Ginger: Always lands on feet
            if (petType == PetType.GINGER) {
                gingerLandOnFeet()
                return
            }

            if (landVelocity > JUMP_VELOCITY_THRESHOLD) {
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
                        PetType.GINGER -> "😤" // Pouty face when ignored
                        PetType.PATITO -> "🤔" // Confused duck
                        PetType.DIABLILLO -> "😈" // Plotting mischief
                    }
                    startSecretEvent(secretEmoji)
                    // Ginger shows pout frame when ignored
                    if (petType == PetType.GINGER) {
                        currentFrame = 7 // ginger_7: pout pose
                    }
                }
                roll == 999 -> {
                    // ULTRA RARE: costume! (0.1%)
                    secretEmoji = listOf("🚀", "👑", "🎩", "🏴‍☠️", "🦸", "🧙", "🎅", "🤖").random()
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
            PetType.CORGI -> {
                showBubble("💕")
                playHaptic(50) // Bark / sharp vibration
            }
            PetType.BLOOP -> {
                showBubble("🫧")
                playHaptic(20) // Soft ghost vibration
            }
            PetType.JELLY -> {
                showBubble("✨")
                playHaptic(100) // Bouncy/longer vibration
            }
            PetType.NUBE_MICHI -> {
                // Coqueta cloud cat - shows different reactions
                val reaction = listOf("☁️", "💕", "✨", "😻").random()
                showBubble(reaction)
                playHaptic(25) // Soft purr vibration
                showPluma = false
                animAlpha = 1f
            }
            PetType.GINGER -> {
                // Ginger: Coquettish reaction with affection tracking
                gingerAddAffection(10)
                if (gingerDoubleXPActive) {
                    progress?.addXP(5) // Bonus XP during wink
                    showBubble("😉💕")
                } else {
                    val reaction = listOf("💕", "✨", "😻", "🐾").random()
                    showBubble(reaction)
                }
                playHaptic(40) // Purr-like vibration
                gingerBoredomTimer = 0f
                gingerPoutActive = false
            }
            PetType.PATITO -> {
                // Patito: Quack Supremo!
                duckQuackSupremo()
                duckIdleTime = 0f
            }
            PetType.DIABLILLO -> {
                // Diablillo: Fire jump with staccato haptic
                impFireJump()
                playStaccatoHaptic()
            }
        }
    }

    private fun updateInteracting(dt: Float) {
        interactTimer += dt
        
        when (petType) {
            PetType.CORGI -> {
                // Corgi: Playful interaction with 12 frames
                when {
                    interactTimer < 0.3f -> {
                        // Excited reaction
                        currentFrame = 6 // Happy/excited frame
                        animScaleY = 0.9f
                        animScaleX = 1.1f
                        playHaptic(40)
                    }
                    interactTimer < 0.8f -> {
                        // Jump of joy
                        currentFrame = 7 // Jump frame
                        animOffsetY = -8f
                        animScaleY = 1.1f
                        animScaleX = 0.95f
                    }
                    interactTimer < 1.5f -> {
                        // Tail wag / happy dance
                        currentFrame = if ((interactTimer * 6f).toInt() % 2 == 0) 3 else 4
                        animScaleY = 1f + sin(interactTimer * 10f) * 0.02f
                        if ((interactTimer * 5f).toInt() % 2 == 0) playHaptic(20)
                    }
                    interactTimer < 2.5f -> {
                        // Lick / affectionate
                        currentFrame = 5 // Lick frame
                        animScaleY = 1f
                        animScaleX = 1f
                    }
                    else -> {
                        // Return to idle
                        resetAnimTransforms()
                        state = PetState.IDLE
                    }
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
                // Jelly: Bouncy slime interaction with 12 frames
                when {
                    interactTimer < 0.15f -> {
                        // Initial squish
                        currentFrame = 8 // Squish frame
                        animScaleX = 1.6f
                        animScaleY = 0.4f
                        animOffsetY = (petSpriteSize / 2f) * 0.6f
                        playHaptic(80)
                    }
                    interactTimer < 0.4f -> {
                        // Bounce up
                        currentFrame = 9 // Stretch frame
                        animScaleX = 0.8f
                        animScaleY = 1.3f
                        animOffsetY = -(petSpriteSize * 0.15f)
                    }
                    interactTimer < 0.8f -> {
                        // Wobble recovery with overshoot
                        val t = (interactTimer - 0.4f) / 0.4f
                        val overshoot = android.view.animation.OvershootInterpolator(2.5f).getInterpolation(t)
                        currentFrame = 4 + (t * 3f).toInt().coerceIn(0, 2) // Cycle frames 4-6
                        animScaleX = 0.8f + (1f - 0.8f) * overshoot
                        animScaleY = 1.3f + (1f - 1.3f) * overshoot
                        if ((interactTimer * 8f).toInt() % 2 == 0) playHaptic(25)
                    }
                    interactTimer < 2.0f -> {
                        // Happy wobble dance
                        currentFrame = if ((interactTimer * 4f).toInt() % 2 == 0) 5 else 6
                        animScaleY = 1f + sin(interactTimer * 6f) * 0.04f
                        animScaleX = 1f - sin(interactTimer * 6f) * 0.03f
                    }
                    else -> {
                        // Return to wobble idle
                        resetAnimTransforms()
                        state = PetState.IDLE
                    }
                }
            }
            PetType.NUBE_MICHI -> {
                // Coqueta cloud cat interaction
                when {
                    interactTimer < 0.5f -> {
                        // Initial reaction - stretch and puff up
                        currentFrame = 3 // Playful pose
                        animScaleX = 1.08f
                        animScaleY = 1.12f
                        playHaptic(15)
                    }
                    interactTimer < 1.5f -> {
                        // Happy cloud wobble
                        currentFrame = if ((interactTimer * 3f).toInt() % 2 == 0) 0 else 1
                        animScaleY = 1f + sin(interactTimer * 8f) * 0.03f
                        animScaleX = 1f - sin(interactTimer * 8f) * 0.02f
                        // Occasional purr haptic
                        if ((interactTimer * 4f).toInt() % 3 == 0) playHaptic(10)
                    }
                    interactTimer < 2.5f -> {
                        // Content breathing
                        currentFrame = 2 // Perch pose
                        val breathe = sin(interactTimer * 3f) * 0.02f
                        animScaleY = 1f + breathe
                    }
                    else -> {
                        // Return to floating
                        state = PetState.IDLE
                        resetAnimTransforms()
                    }
                }
            }
            PetType.GINGER -> {
                if (isSecretActive) {
                    // Belly rub / tumbling sequence: ginger_7 → 8 → 9 → 10
                    // Slow, playful rolling and tumbling
                    when {
                        interactTimer < 1.5f -> {
                            // Lay down gingerly
                            currentFrame = 7 // ginger_7: lay down
                            animScaleY = 0.95f
                            // Soft haptic as she lays down
                            if (interactTimer < 0.3f) playHaptic(15)
                        }
                        interactTimer < 3.0f -> {
                            // Start rolling onto back
                            currentFrame = 8 // ginger_8: start roll
                            animScaleY = 0.88f
                            animScaleX = 1.08f
                            // Rolling haptic
                            if ((interactTimer * 3f).toInt() % 2 == 0) playHaptic(20)
                        }
                        interactTimer < 5.0f -> {
                            // Rolling / tumbling
                            currentFrame = 9 // ginger_9: rolling
                            animScaleY = 0.85f + sin(interactTimer * 4f) * 0.05f
                            animScaleX = 1.1f - sin(interactTimer * 4f) * 0.05f
                            // More intense rolling haptic
                            if ((interactTimer * 5f).toInt() % 2 == 0) playHaptic(25)
                        }
                        interactTimer < 9.0f -> {
                            // Belly up - purring happily!
                            currentFrame = 10 // ginger_10: belly up
                            // Progressive purring haptic
                            gingerPurrIntensity = ((interactTimer - 5.0f) / 4.0f).coerceIn(0f, 1f)
                            if ((interactTimer * 4f).toInt() % 3 == 0) {
                                playHaptic((20 + gingerPurrIntensity * 50).toLong())
                            }
                            // Purring wobble
                            animScaleY = 1f + sin(interactTimer * 8f) * 0.03f
                            animScaleX = 1f - sin(interactTimer * 8f) * 0.02f
                        }
                        else -> {
                            // Finished - get up and stretch
                            gingerPurrIntensity = 0f
                            resetAnimTransforms()
                            gingerPose = GingerPose.STANDING
                            gingerIdleSitTimer = 0f
                            currentFrame = 2 // standing
                            state = PetState.IDLE
                            isSecretActive = false
                            showBubble("😻")
                            playHaptic(30)
                        }
                    }
                } else {
                    // Regular tap/petting - Ginger reacts happily then may lay down
                    when {
                        interactTimer < 0.5f -> {
                            // Initial flinch of pleasure
                            currentFrame = if (gingerPose == GingerPose.SITTING) 5 else 2
                            animScaleY = 0.92f
                            animScaleX = 1.04f
                            playHaptic(20)
                        }
                        interactTimer < 2.0f -> {
                            // Happy reaction - show lick paw or stretch
                            currentFrame = 5 // ginger_5: lick paw (pleased)
                            animScaleY = 1f + sin(interactTimer * 5f) * 0.02f
                            // Occasional happy haptic
                            if ((interactTimer * 4f).toInt() % 3 == 0) playHaptic(15)
                        }
                        interactTimer < 3.5f -> {
                            // Stretch contentedly
                            currentFrame = 0 // ginger_0: stretch forward
                            animScaleY = 1f
                        }
                        else -> {
                            // Return to previous pose
                            resetAnimTransforms()
                            currentFrame = if (gingerPose == GingerPose.SITTING) 4 else 2
                            state = PetState.IDLE
                        }
                    }
                }
            }
            PetType.PATITO -> {
                // Patito: Quack reaction when tapped
                when {
                    interactTimer < 0.2f -> {
                        // Initial surprise - peek at user
                        currentFrame = 3
                        animScaleY = 1.05f
                        playHaptic(60) // Sharp peck vibration
                    }
                    interactTimer < 0.5f -> {
                        // Quack start
                        currentFrame = 13
                        animScaleY = 0.95f
                    }
                    interactTimer < 1.2f -> {
                        // QUACK SUPREMO!
                        currentFrame = 14
                        animScaleY = 1.1f
                        animScaleX = 1.1f
                        animOffsetY = -5f
                        if (interactTimer < 0.6f) playHaptic(80)
                    }
                    interactTimer < 1.8f -> {
                        // Recover
                        currentFrame = 13
                        animScaleY = 1f
                    }
                    else -> {
                        // Return to idle
                        resetAnimTransforms()
                        currentFrame = 0
                        state = PetState.IDLE
                        duckState = DuckState.IDLE_SIDE
                    }
                }
            }
            PetType.DIABLILLO -> {
                // Diablillo: Fire jump scare
                when {
                    interactTimer < 0.2f -> {
                        // Initial scare
                        currentFrame = 4 // Surprised
                        animScaleY = 1.15f
                        playStaccatoHaptic()
                    }
                    interactTimer < 0.6f -> {
                        // Fire jump up
                        currentFrame = 5 // Fire frame
                        animOffsetY = -12f
                        animScaleY = 0.9f
                        animScaleX = 1.15f
                    }
                    interactTimer < 1.2f -> {
                        // Chaotic wobble
                        currentFrame = if ((interactTimer * 10f).toInt() % 2 == 0) 4 else 5
                        animScaleY = 1f + sin(interactTimer * 15f) * 0.05f
                    }
                    else -> {
                        // Return to lurking
                        resetAnimTransforms()
                        currentFrame = 0
                        state = PetState.IDLE
                        impState = ImpState.LURKING
                    }
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════
    // ▌ SYSTEM REACTIONS
    // ══════════════════════════════════════════════════════════

    fun consumeTreasure(emoji: String) {
        // Only react if pet is in a safe state (not dragging, mid-interaction, etc.)
        if (state == PetState.DRAGGING || state == PetState.INTERACTING || state == PetState.SECRET_IDLE) {
            showBubble(emoji)
            playHaptic(100)
            return
        }

        // Play an enthusiastic reaction globally
        triggerReaction(emoji)
        playHaptic(100)
        
        // Characteristic unique animation for receiving a gift
        when (petType) {
            PetType.JELLY -> {
                animScaleY = 0.5f
                animScaleX = 1.5f
                velocityY = -30f // Huge jump of happiness!
                state = PetState.JUMPING
            }
            PetType.CORGI -> {
                velocityY = -15f
                state = PetState.JUMPING
            }
            PetType.NUBE_MICHI -> {
                animScaleX = 1.25f
                animScaleY = 1.35f
            }
            PetType.BLOOP -> {
                animRotation = 360f // Spooky spin!
                animScaleX = 1.3f
                animScaleY = 1.3f
            }
            PetType.GINGER -> {
                // Elegant happy stretch
                currentFrame = 0 // stretch pose
                animScaleX = 1.15f
                animScaleY = 1.1f
            }
            PetType.PATITO -> {
                // Happy quack jump
                duckQuackSupremo()
                velocityY = -12f
                state = PetState.JUMPING
            }
            PetType.DIABLILLO -> {
                // Chaotic fire jump
                impFireJump()
                playStaccatoHaptic()
            }
        }
    }

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
        // Rapid digging for bones
        if (reactionEmoji == "🦴") {
            animScaleX = 1f + sin(reactionTimer * 40f) * 0.1f // rapid scaleX
            animScaleY = 0.95f // squished
        }
        // Jump high for notification / Corgi jumping
        if (reactionEmoji == "Bark!!" || reactionEmoji == "Bark!") {
            animOffsetY = 0f
            if (reactionTimer < 0.1f && state == PetState.SYSTEM_REACTION) {
                velocityY = -15f // Big jump
            }
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

        val frame = spriteFrames[currentFrame.coerceIn(0, spriteFrames.lastIndex)]
        canvas.drawBitmap(frame, null, spriteRect, spritePaint)

        // ── NUBE-MICHI PLUMA (feather) while falling ──
        if (showPluma && nubePlumaBitmap != null) {
            val plumaPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            plumaPaint.alpha = 200
            val plumaSize = petSpriteSize * 0.4f
            val plumaX = cx + sin(time * 5f) * 30f
            val plumaY = cy - petSpriteSize * 0.3f + cos(time * 3f) * 15f
            val plumaRect = RectF(
                plumaX - plumaSize / 2f,
                plumaY - plumaSize / 2f,
                plumaX + plumaSize / 2f,
                plumaY + plumaSize / 2f
            )
            canvas.save()
            canvas.rotate(time * 60f, plumaX, plumaY) // Spin gently
            canvas.drawBitmap(nubePlumaBitmap!!, null, plumaRect, plumaPaint)
            canvas.restore()
        }

        canvas.restore()

        // ── 3. PARTICLES ──
        drawParticles(canvas)

        // ── 4. CORGI LICK SCREEN ──
        if (corgiLickTimer in 0f..2.5f) {
            drawCorgiLick(canvas, cx, cy + halfH)
        }

        // ── 5. SPEECH BUBBLE ──
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
    // ▌ GESTURE DETECTION (Swipe)
    // ══════════════════════════════════════════════════════════
    private val gestureDetector = android.view.GestureDetector(context, object : android.view.GestureDetector.SimpleOnGestureListener() {
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (petType == PetType.CORGI) {
                isSecretActive = true // Swipe triggers panza arriba
                triggerInteraction()
                return true
            }
            if (petType == PetType.GINGER) {
                isSecretActive = true // Swipe triggers belly rub sequence
                triggerInteraction()
                showBubble("😻")
                return true
            }
            return false
        }
    })

    // ══════════════════════════════════════════════════════════
    // ▌ TOUCH HANDLING
    // ══════════════════════════════════════════════════════════

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val params = getWindowParams() ?: return false
        
        // Pass to gesture detector to detect fling/swipes
        if (gestureDetector.onTouchEvent(event)) {
            return true
        }

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
                lastTouchX = event.rawX
                lastTouchY = event.rawY
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

                    // Otherwise regular drop/fall or anti-gravity float
                    if (petType == PetType.BLOOP) {
                        state = PetState.IDLE
                        velocityY = 0f
                        animRotation = 0f
                        // Inertia smoothly blends using the sine offset in IdleStyle instead of a jump
                    } else if (petType == PetType.GINGER) {
                        // Ginger falls elegantly, will land standing
                        state = PetState.FALLING
                        velocityY = 2f
                        animRotation = 0f
                        gingerPose = GingerPose.FALLING
                        resetAnimTransforms()
                    } else {
                        state = PetState.FALLING
                        velocityY = 2f  // Initial fall velocity
                        animRotation = 0f
                        resetAnimTransforms()
                    }
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * Double-tap Dodge — Pet jumps away to clear the area.
     * Smart UX: doesn't block what's underneath.
     * 
     * Ginger: Does an elegant parabolic jump toward the tap position.
     */
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    private fun doubleTapDodge() {
        val params = getWindowParams() ?: return

        if (petType == PetType.GINGER) {
            // Ginger: LASER JUMP - Alert frame then elastic overshoot toward finger
            gingerLaserJumpActive = true
            gingerLaserTargetX = lastTouchX
            gingerLaserTargetY = lastTouchY

            // Show alert frame first (alert! something moved!)
            currentFrame = 0 // ginger_0: stretch forward (alert pose)
            playHaptic(20)

            // After brief alert, jump toward target with elastic overshoot
            handler.postDelayed({
                val p = getWindowParams() ?: return@postDelayed
                val targetX = gingerLaserTargetX.toInt().coerceIn(30, screenWidth - petSpriteSize - 30)
                val jumpHeight = -22f * petType.agility // Higher jump with agility
                velocityX = (targetX - p.x) * 0.08f * petType.agility
                velocityY = jumpHeight
                currentFrame = 1 // ginger_1: stretch/jump frame
                state = PetState.JUMPING
                showBubble("🐾✨")
                playHaptic(30)
                gingerLaserJumpActive = false
                gingerAddAffection(5) // Cats love chasing things
            }, 200) // 200ms alert delay
            return
        }

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

        if (petType == PetType.NUBE_MICHI && params.y > screenHeight - keyboardHeight - petSpriteSize - 30) {
            // Smooth evasion to top edge
            val targetY = 60
            val animator = android.animation.ValueAnimator.ofInt(params.y, targetY)
            animator.duration = 1200
            animator.interpolator = android.view.animation.OvershootInterpolator(0.6f)
            animator.addUpdateListener {
                params.y = it.animatedValue as Int
                updateWindowLayout(params)
            }
            animator.start()
            showBubble("☁️")
            return
        }

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
        animAlpha = when (petType) {
            PetType.BLOOP -> 0.8f    // Ghost transparency baseline
            PetType.JELLY -> 0.95f   // Neon translucency baseline
            else -> 1f
        }
        animOffsetX = 0f
        animOffsetY = 0f
        animRotation = 0f
    }

    // ══════════════════════════════════════════════════════════
    // ▌ PARTICLES LOGIC
    // ══════════════════════════════════════════════════════════

    private fun emitBubble() {
        val cx = width / 2f
        val cy = height / 2f
        val offset = (Random.nextFloat() - 0.5f) * petSpriteSize * 0.5f
        particles.add(
            Particle(
                x = cx + offset,
                y = cy + petSpriteSize * 0.3f,
                vx = (Random.nextFloat() - 0.5f) * 50f,
                vy = -Random.nextFloat() * 100f - 50f,
                alpha = 0.6f,
                size = Random.nextFloat() * 8f + 4f,
                life = 0f,
                maxLife = Random.nextFloat() * 1f + 0.5f,
                color = Color.parseColor("#B0E0E6") // Powder blue
            )
        )
    }

    private fun emitSparkle() {
        val cx = width / 2f
        val cy = height / 2f
        val offsetDX = (Random.nextFloat() - 0.5f) * petSpriteSize * 0.8f
        val offsetDY = (Random.nextFloat() - 0.5f) * petSpriteSize * 0.8f
        particles.add(
            Particle(
                x = cx + offsetDX,
                y = cy + offsetDY,
                vx = (Random.nextFloat() - 0.5f) * 30f,
                vy = -Random.nextFloat() * 60f - 20f,
                alpha = 1f,
                size = Random.nextFloat() * 6f + 3f,
                life = 0f,
                maxLife = Random.nextFloat() * 0.6f + 0.2f,
                color = Color.parseColor("#FFFF66") // Yellow sparkle
            )
        )
    }

    private fun updateParticles(dt: Float) {
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.life += dt
            if (p.life >= p.maxLife) {
                iterator.remove()
            }
        }
    }

    private fun drawParticles(canvas: Canvas) {
        for (p in particles) {
            val lifeRatio = p.life / p.maxLife
            val currentAlpha = (p.alpha * (1f - lifeRatio) * 255).toInt().coerceIn(0, 255)
            particlePaint.color = p.color
            particlePaint.alpha = currentAlpha
            
            // Draw circle for bubble/sparkle
            canvas.drawCircle(p.x, p.y, p.size, particlePaint)
        }
    }

    // ══════════════════════════════════════════════════════════
    // ▌ SYSTEM HAPTICS
    // ══════════════════════════════════════════════════════════

    private fun playHaptic(durationMs: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to play haptic", e)
        }
    }

    private fun getWindowParams(): WindowManager.LayoutParams? {
        return layoutParams as? WindowManager.LayoutParams
    }

    private fun updateWindowLayout(params: WindowManager.LayoutParams) {
        try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.updateViewLayout(this, params)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update window layout", e)
        }
    }

    private fun applyWindowOffset(dx: Int, dy: Int) {
        val params = getWindowParams() ?: return
        params.x = (params.x + dx).coerceIn(0, screenWidth - petSpriteSize)
        params.y = (params.y + dy).coerceIn(50, groundY)
        updateWindowLayout(params)
    }

    override fun onDetachedFromWindow() {
        pauseAnimation()
        // Recycle bitmaps to free memory
        for (bitmap in spriteFrames) {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
        particles.clear()
        super.onDetachedFromWindow()
    }
}
