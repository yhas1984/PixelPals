package com.pixelpals.app.debug

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import com.pixelpals.app.feature.overlay.behavior.PetAtlasSpec
import org.json.JSONObject
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

@SuppressLint("ViewConstructor")
internal class LumiDebugOverlayView(
    context: Context,
    val screenWidth: Int,
    val screenHeight: Int,
    val spriteSize: Int,
    private val onMove: (Float, Float) -> Unit,
) : View(context) {
    val viewSize: Int = spriteSize
    private val density: Float = resources.displayMetrics.density
    private val spriteHalfSize: Float = spriteSize / 2f
    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val handler: Handler = Handler(Looper.getMainLooper())
    private val random: Random = Random(System.nanoTime())
    private val motionController: LumiMotionController = LumiMotionController(density)
    private var atlasSpec: PetAtlasSpec? = null
    private var atlasBitmap: Bitmap? = null
    private var isReady: Boolean = false
    private var isAnimating: Boolean = false
    private var mode: Mode = Mode.IDLE
    private var manualReviewClip: LumiReviewClip? = null
    private var manualReviewDirection: Float = 1f
    private var manualReviewSpeed: Float = 1f
    private var manualReviewElapsed: Float = 0f
    private var manualReviewStartX: Float = 0f
    private var manualReviewTargetX: Float = 0f
    private var manualReviewStartY: Float = 0f
    private var manualReviewTargetY: Float = 0f
    private var frames: IntArray = intArrayOf(0)
    private var frameDurations: IntArray = intArrayOf(500)
    private var frameIndex: Int = 0
    private var frameElapsedMs: Float = 0f
    private var stateElapsed: Float = 0f
    private var worldTime: Float = 0f
    private var idleDuration: Float = 2.2f
    private var stalkDuration: Float = 1.1f
    private var walkSpeed: Float = 48f * density
    private var walkPauseRemaining: Float = 0f
    private var walkDistanceSincePause: Float = 0f
    private var walkPauseDistance: Float = 180f * density
    private var walkTimeRemaining: Float = 0f
    private var walkTurnRemaining: Float = 0f
    private var walkTurnTargetX: Float = 0f
    private var walkDistanceSinceHop: Float = 0f
    private var walkHopDistance: Float = 620f * density
    private var walkCycleStartX: Float = screenWidth / 2f
    private var walkCycleDistance: Float = 0f
    private var walkMotionFacing: Float = 1f
    private var lastWalkFrameIndex: Int = 0
    private var stalkSpeed: Float = 16f * density
    private var pounceDuration: Float = 2.1f
    private var baseY: Float = screenHeight * 0.62f
    private var energy: Float = 0.82f
    private var sleepiness: Float = 0.12f
    private var centerX: Float = screenWidth / 2f
    private var centerY: Float = screenHeight * 0.62f
    private var stationaryX: Float = centerX
    private var investigateStartX: Float = centerX
    private var investigateTargetX: Float = centerX
    private val comfortX: Float = screenWidth / 2f
    private var targetX: Float = centerX
    private var facing: Float = 1f
    private var pounceStartX: Float = centerX
    private var pounceTargetX: Float = centerX
    private var hopStartX: Float = centerX
    private var hopTargetX: Float = centerX
    private var hopStartY: Float = baseY
    private var hopTargetY: Float = baseY
    private var hopDuration: Float = 2.16f
    private var settleAfterWalk: Boolean = false
    private var downX: Float = 0f
    private var downY: Float = 0f
    private var wasDragged: Boolean = false
    private var edgeSide: Int = 0
    private var lastTapAtMs: Long = 0L
    private val recentModes: ArrayDeque<Mode> = ArrayDeque()
    private val cooldownUntil: MutableMap<Mode, Float> = mutableMapOf()
    private var lastFrameAtMs: Long = 0L
    private val frameRunnable: Runnable = object : Runnable {
        override fun run(): Unit {
            if (!isAnimating) return
            val now = System.currentTimeMillis()
            val dtMs = if (lastFrameAtMs == 0L) 16L else (now - lastFrameAtMs).coerceIn(1L, 80L)
            lastFrameAtMs = now
            update(dtMs / 1000f)
            invalidate()
            handler.postDelayed(this, 16L)
        }
    }

    init {
        isClickable = true
        loadAtlas()
    }

    fun startAnimation(): Unit {
        if (isAnimating) return
        isAnimating = true
        lastFrameAtMs = 0L
        handler.post(frameRunnable)
    }

    fun stopAnimation(): Unit {
        pauseAnimation()
        atlasBitmap?.let { if (!it.isRecycled) it.recycle() }
        atlasBitmap = null
    }

    fun pauseAnimation(): Unit {
        isAnimating = false
        handler.removeCallbacks(frameRunnable)
    }

    fun resumeAnimation(): Unit {
        if (!isAnimating && atlasBitmap != null) startAnimation()
    }

    fun startManualReview(clip: LumiReviewClip, direction: Float, speed: Float): Unit {
        manualReviewClip = clip
        manualReviewDirection = if (direction >= 0f) 1f else -1f
        manualReviewSpeed = speed.coerceIn(0.25f, 1f)
        manualReviewElapsed = 0f
        mode = Mode.MANUAL_REVIEW
        facing = manualReviewDirection
        baseY = (screenHeight * 0.62f).coerceIn(screenHeight * 0.24f, screenHeight * 0.78f)
        manualReviewStartX = screenWidth / 2f
        val travel = when (clip) {
            LumiReviewClip.HOP_UP, LumiReviewClip.HOP_DOWN -> 120f * density
            LumiReviewClip.POUNCE -> 150f * density
            else -> 0f
        }
        manualReviewTargetX = (manualReviewStartX + manualReviewDirection * travel).coerceIn(spriteHalfSize, screenWidth - spriteHalfSize)
        manualReviewStartY = if (clip == LumiReviewClip.HOP_DOWN) baseY - 120f * density else baseY
        manualReviewTargetY = if (clip == LumiReviewClip.HOP_UP) baseY - 120f * density else baseY
        setFrames(clip.frames.copyOf(), getManualReviewDurations(clip))
        centerX = manualReviewStartX
        centerY = manualReviewStartY
        android.util.Log.d(TAG, "manual_review clip=${clip.name} direction=${manualReviewDirection.format()} speed=${manualReviewSpeed.format()}")
        onMove(centerX, centerY)
        if (!isAnimating) startAnimation()
    }

    fun stopManualReview(): Unit {
        manualReviewClip = null
        if (isReady) enterIdle()
    }

    fun onScreenChanged(isScreenOn: Boolean): Unit {
        if (isScreenOn) {
            if (mode == Mode.SLEEP) enterObserve()
            resumeAnimation()
        } else {
            pauseAnimation()
        }
    }

    fun onBatteryChanged(percent: Int, isCharging: Boolean): Unit {
        if (manualReviewClip != null) return
        if (!isCharging && percent <= 15) {
            energy = 0.12f
            sleepiness = 0.7f
            if (mode != Mode.SLEEP && mode != Mode.REST) enterRest()
        } else if (isCharging && percent >= 30 && mode == Mode.REST) {
            energy = (energy + 0.2f).coerceAtMost(1f)
            enterHappy()
        }
    }

    override fun onDraw(canvas: Canvas): Unit {
        super.onDraw(canvas)
        val spec = atlasSpec ?: return
        val bitmap = atlasBitmap ?: return
        val frame = frames[frameIndex.coerceIn(0, frames.lastIndex)]
        val source = Rect(
            (frame % spec.columns) * spec.frameWidth,
            (frame / spec.columns) * spec.frameHeight,
            ((frame % spec.columns) + 1) * spec.frameWidth,
            ((frame / spec.columns) + 1) * spec.frameHeight,
        )
        val half = spriteSize / 2f
        val center = viewSize / 2f
        canvas.save()
        canvas.translate(center, center)
        canvas.scale(getVisualFacing(), 1f)
        canvas.drawBitmap(bitmap, source, RectF(-half, -half, half, half), paint)
        canvas.restore()
    }

    private fun getVisualFacing(): Float = facing

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (manualReviewClip != null) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                wasDragged = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.rawX - downX
                val deltaY = event.rawY - downY
                if (!wasDragged && abs(deltaX) + abs(deltaY) > 12f * density) {
                    wasDragged = true
                    enterDragged()
                }
                if (wasDragged) {
                    centerX = event.rawX
                    centerY = event.rawY
                    baseY = centerY
                    onMove(centerX, centerY)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!wasDragged) {
                    val now = System.currentTimeMillis()
                    val isDoubleTap = now - lastTapAtMs in 1..320
                    lastTapAtMs = now
                    respondToTap(isDoubleTap)
                    performClick()
                } else {
                    respondToDrop(event.rawX - downX, event.rawY - downY)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> return true
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun loadAtlas(): Unit {
        Thread {
            runCatching {
                val specText = context.assets.open(SPEC_PATH).bufferedReader().use { it.readText() }
                val spec = PetAtlasSpec.fromJson(JSONObject(specText))
                val bitmap = context.assets.open(spec.atlasPath).use { BitmapFactory.decodeStream(it) }
                    ?: error("Unable to decode Lumi trial atlas")
                post {
                    atlasSpec = spec
                    atlasBitmap = bitmap
                    isReady = true
                    if (manualReviewClip == null) enterIdle() else updateManualReview(0f)
                }
            }.onFailure { error ->
                android.util.Log.e(TAG, "Unable to load Lumi debug atlas", error)
            }
        }.start()
    }

    private fun update(dt: Float): Unit {
        if (!isReady) return
        if (manualReviewClip != null) {
            updateManualReview(dt)
            return
        }
        worldTime += dt
        stateElapsed += dt
        frameElapsedMs += dt * 1000f
        while (frameElapsedMs >= frameDurations[frameIndex]) {
            frameElapsedMs -= frameDurations[frameIndex]
            if (frameIndex == frames.lastIndex) {
                if (mode == Mode.WALK && walkTurnRemaining > 0f) {
                    frameElapsedMs = 0f
                    break
                } else if (mode == Mode.IDLE || mode == Mode.WALK || mode == Mode.STALK || mode == Mode.SLEEP) {
                    frameIndex = 0
                } else {
                    finishAction()
                }
            } else {
                frameIndex += 1
            }
        }
        when (mode) {
            Mode.IDLE -> updateIdle(dt)
            Mode.OBSERVE -> updateObserve(dt)
            Mode.WALK -> updateWalk(dt)
            Mode.STALK -> updateStalk(dt)
            Mode.POUNCE -> updatePounce()
            Mode.HOP -> updateHop()
            Mode.RECOVERY -> updateRecovery()
            Mode.SOCIAL -> updateSocial()
            Mode.MAGIC -> updateMagic()
            Mode.HAPPY -> updateHappy()
            Mode.SLEEP -> updateSleep()
            Mode.REST -> updateRest()
            Mode.SETTLE -> updateSettle()
            Mode.DRAGGED -> Unit
            Mode.EDGE_OBSERVE -> updateEdgeObserve()
            Mode.MANUAL_REVIEW -> Unit
        }
    }

    private fun updateManualReview(dt: Float): Unit {
        val clip = manualReviewClip ?: return
        val totalDuration = frameDurations.sum().toFloat() / 1000f
        manualReviewElapsed += dt
        while (manualReviewElapsed >= totalDuration) manualReviewElapsed -= totalDuration
        frameElapsedMs += dt * 1000f
        while (frameElapsedMs >= frameDurations[frameIndex]) {
            frameElapsedMs -= frameDurations[frameIndex]
            frameIndex = if (frameIndex == frames.lastIndex) 0 else frameIndex + 1
        }
        val progress = (manualReviewElapsed / totalDuration).coerceIn(0f, 1f)
        when (clip) {
            LumiReviewClip.HOP_UP, LumiReviewClip.HOP_DOWN, LumiReviewClip.POUNCE -> {
                val point = motionController.hopPoint(
                    progress = progress,
                    startX = manualReviewStartX,
                    targetX = manualReviewTargetX,
                    startY = manualReviewStartY,
                    targetY = manualReviewTargetY,
                )
                centerX = point.x
                centerY = point.y
            }
            else -> {
                centerX = manualReviewStartX
                centerY = baseY
            }
        }
        onMove(centerX, centerY)
    }

    private fun updateIdle(@Suppress("UNUSED_PARAMETER") dt: Float): Unit {
        centerX = stationaryX
        centerY = baseY + sin(stateElapsed * 2.2f) * 1.4f * density
        onMove(centerX, centerY)
        if (stateElapsed >= idleDuration) chooseAutonomousAction()
    }

    private fun updateObserve(dt: Float): Unit {
        // Investigation is a stationary fox behavior. Locomotion belongs to walk,
        // pounce, and hop so a sniff or look cannot visually slide across the screen.
        centerX = stationaryX
        centerY = baseY
        onMove(centerX, centerY)
        if (stateElapsed >= 3.0f && dt > 0f) finishAction()
    }

    private fun updateWalk(dt: Float): Unit {
        walkTimeRemaining -= dt
        if (walkTurnRemaining > 0f) {
            walkTurnRemaining -= dt
            centerY = baseY + sin(stateElapsed * 2.4f) * 0.9f * density
            onMove(centerX, centerY)
            if (walkTurnRemaining <= 0f) {
                targetX = walkTurnTargetX
                walkCycleStartX = centerX
                walkMotionFacing = facing
                lastWalkFrameIndex = 0
                 setFrames(intArrayOf(4, 5, 6, 7, 8, 9, 10, 11), IntArray(8) { 170 })
                stateElapsed = 0f
            }
            return
        }
        val distance = targetX - centerX
        if (abs(distance) < 4f) {
            centerX = targetX
            if (settleAfterWalk) {
                settleAfterWalk = false
                enterSettle()
            } else if (walkTimeRemaining > 0f || isAtWalkEdge()) {
                val atEdge = isAtWalkEdge()
                val nextTarget = if (atEdge) edgeContinueTarget() else nextWalkTarget()
                val nextFacing = if (nextTarget >= centerX) 1f else -1f
                if (nextFacing != facing || atEdge) {
                    if (atEdge) walkTimeRemaining = 4.0f
                    beginWalkTurn(nextTarget, nextFacing)
                } else {
                    targetX = nextTarget
                    facing = nextFacing
                    walkCycleStartX = centerX
                    walkMotionFacing = facing
                    lastWalkFrameIndex = frameIndex
                    stateElapsed = 0f
                }
            } else {
                enterObserve()
            }
            return
        }
        if (walkPauseRemaining > 0f) {
            walkPauseRemaining -= dt
            centerY = baseY + sin(stateElapsed * 2.8f) * 1.1f * density
            onMove(centerX, centerY)
            if (walkPauseRemaining <= 0f) {
                setFrames(intArrayOf(4, 5, 6, 7, 8, 9, 10, 11), IntArray(8) { 170 })
                walkCycleStartX = centerX
                lastWalkFrameIndex = 0
                stateElapsed = 0f
            }
            return
        }
        val previousX = centerX
        facing = if (distance >= 0f) 1f else -1f
        if (facing != walkMotionFacing) {
            walkCycleStartX = centerX
            walkMotionFacing = facing
            lastWalkFrameIndex = frameIndex
        }
        if (frameIndex < lastWalkFrameIndex) {
            walkCycleStartX += walkMotionFacing * walkCycleDistance
        }
        lastWalkFrameIndex = frameIndex
        val phase = walkPhase()
        val desiredX = walkCycleStartX + walkMotionFacing * walkCycleDistance * phase
        centerX = motionController.walkX(
            cycleStartX = walkCycleStartX,
            targetX = targetX,
            facing = walkMotionFacing,
            cycleDistance = walkCycleDistance,
            phase = phase,
        )
        centerY = baseY + sin(phase * Math.PI * 2.0).toFloat() * 1.5f * density
        val actualStep = abs(centerX - previousX)
        walkDistanceSincePause += actualStep
        walkDistanceSinceHop += actualStep
        if (walkDistanceSincePause >= walkPauseDistance && random.nextFloat() < 0.035f) {
            walkPauseRemaining = 0.18f + random.nextFloat() * 0.36f
            walkDistanceSincePause = 0f
            walkPauseDistance = (150f + random.nextFloat() * 180f) * density
            setFrames(intArrayOf(1), intArrayOf((walkPauseRemaining * 1000f).roundToInt().coerceAtLeast(260)))
        }
        if (walkDistanceSinceHop >= walkHopDistance && isEligible(Mode.HOP)) {
            walkDistanceSinceHop = 0f
            walkHopDistance = (180f + random.nextFloat() * 120f) * density
            if (random.nextFloat() < 0.82f) {
                enterHop()
                return
            }
        }
        onMove(centerX, centerY)
    }

    private fun updateStalk(dt: Float): Unit {
        // The current stalk artwork is a crouch/look sequence, not a footstep
        // cycle. Hold the root still until dedicated creep frames exist.
        centerX = stationaryX
        centerY = baseY + 10f * density
        onMove(centerX, centerY)
        if (stateElapsed < stalkDuration) return
        if (random.nextFloat() < 0.72f && energy > 0.24f) enterPounce() else enterObserve()
    }

    private fun updatePounce(): Unit {
        val progress = (stateElapsed / pounceDuration).coerceIn(0f, 1f)
        val launchProgress = ((progress - 0.34f) / 0.18f).coerceIn(0f, 1f)
        val flightProgress = ((progress - 0.52f) / 0.34f).coerceIn(0f, 1f)
        val landingProgress = ((progress - 0.86f) / 0.14f).coerceIn(0f, 1f)
        centerX = when {
            progress < 0.34f -> pounceStartX
            progress < 0.52f -> pounceStartX + (pounceTargetX - pounceStartX) * (launchProgress * launchProgress)
            progress < 0.86f -> pounceStartX + (pounceTargetX - pounceStartX) * (0.28f + flightProgress * 0.72f)
            else -> pounceTargetX
        }
        centerY = when {
            progress < 0.34f -> baseY + 7f * density
            progress < 0.86f -> baseY - sin(flightProgress * Math.PI).toFloat() * 62f * density
            else -> baseY + (1f - landingProgress) * 8f * density
        }
        onMove(centerX, centerY)
    }

    private fun updateHop(): Unit {
        val progress = (stateElapsed / hopDuration).coerceIn(0f, 1f)
        val point = motionController.hopPoint(
            progress = progress,
            startX = hopStartX,
            targetX = hopTargetX,
            startY = hopStartY,
            targetY = hopTargetY,
        )
        centerX = point.x
        centerY = point.y
        onMove(centerX, centerY)
    }

    private fun updateRecovery(): Unit {
        centerX = stationaryX
        centerY = baseY + 5f * density + sin(stateElapsed * 4f) * 1.2f * density
        onMove(centerX, centerY)
    }

    private fun updateSocial(): Unit {
        centerX = stationaryX
        centerY = baseY + sin(stateElapsed * 4f) * 3f * density
        onMove(centerX, centerY)
    }

    private fun updateMagic(): Unit {
        centerX = stationaryX
        centerY = baseY + sin(stateElapsed * 3f) * 2f * density
        onMove(centerX, centerY)
    }

    private fun updateHappy(): Unit {
        centerX = stationaryX
        centerY = baseY - sin((stateElapsed / 0.9f).coerceIn(0f, 1f) * Math.PI).toFloat() * 20f * density
        onMove(centerX, centerY)
    }

    private fun updateSleep(): Unit {
        centerX = stationaryX
        centerY = baseY + sin(stateElapsed * 1.2f) * 1.2f * density
        onMove(centerX, centerY)
        if (stateElapsed >= 20f) finishAction()
    }

    private fun updateRest(): Unit {
        centerX = stationaryX
        centerY = baseY + 4f * density + sin(stateElapsed * 2f) * 1.2f * density
        onMove(centerX, centerY)
    }

    private fun updateSettle(): Unit {
        // Returning to the comfort spot is routed through the walk clip in
        // enterSettle(); this state itself must never slide under a static pose.
        centerX = stationaryX
        centerY = baseY + sin(stateElapsed * 1.8f) * 1.2f * density
        onMove(centerX, centerY)
        if (stateElapsed > 1.2f) finishAction()
    }

    private fun updateEdgeObserve(): Unit {
        centerX = stationaryX
        centerY = baseY + sin(stateElapsed * 2.5f) * 2f * density
        onMove(centerX, centerY)
        if (stateElapsed >= 1.2f) {
            continueWalkFromEdge()
        }
    }

    private fun finishAction(): Unit {
        when (mode) {
            Mode.POUNCE -> {
                energy = (energy - 0.18f).coerceAtLeast(0.05f)
                enterRecovery()
            }
            Mode.HOP -> {
                energy = (energy - 0.03f).coerceAtLeast(0.05f)
                if (abs(hopTargetY - hopStartY) >= 1f) {
                    baseY = hopTargetY
                }
                if (walkTimeRemaining > 0f) {
                    enterWalk(nextWalkTarget(), sustained = false, preserveTime = true)
                } else {
                    enterIdle()
                }
            }
            Mode.RECOVERY -> enterSettle()
            Mode.MAGIC -> {
                energy = (energy - 0.08f).coerceAtLeast(0.05f)
                enterHappy()
            }
            Mode.SOCIAL -> enterSettle()
            Mode.HAPPY -> enterSettle()
            Mode.SLEEP -> {
                energy = (energy + 0.42f).coerceAtMost(1f)
                sleepiness = 0.08f
                enterIdle()
            }
            Mode.REST -> {
                energy = (energy + 0.16f).coerceAtMost(1f)
                enterIdle()
            }
            Mode.SETTLE, Mode.OBSERVE -> enterIdle()
            Mode.EDGE_OBSERVE -> continueWalkFromEdge()
            Mode.DRAGGED -> Unit
            else -> enterIdle()
        }
    }

    private fun enterIdle(): Unit {
        mode = Mode.IDLE
        stationaryX = centerX
        logTransition("idle")
        setFrames(intArrayOf(0, 1, 2, 3), intArrayOf(1000, 1200, 1200, 1000))
        stateElapsed = 0f
        idleDuration = random.nextFloat() * 2.4f + 2.0f
        baseY = baseY.coerceIn(screenHeight * 0.24f, screenHeight * 0.78f)
        centerY = baseY
        onMove(centerX, centerY)
    }

    private fun enterWalk(
        destinationX: Float,
        sustained: Boolean = true,
        preserveTime: Boolean = false,
        settleAtDestination: Boolean = false,
    ): Unit {
        mode = Mode.WALK
        logTransition("walk")
        targetX = destinationX.coerceIn(spriteHalfSize, screenWidth - spriteHalfSize)
        facing = if (targetX >= centerX) 1f else -1f
        walkSpeed = (38f + random.nextFloat() * 24f) * density
        if (!preserveTime) {
            walkTimeRemaining = if (sustained) {
                18.0f + random.nextFloat() * 8.0f
            } else {
                3.5f + random.nextFloat() * 2.0f
            }
        }
        walkPauseRemaining = 0f
        walkTurnRemaining = 0f
        walkTurnTargetX = targetX
        walkDistanceSincePause = 0f
        if (!preserveTime) walkDistanceSinceHop = 0f
        settleAfterWalk = settleAtDestination
        walkHopDistance = (150f + random.nextFloat() * 90f) * density
        walkPauseDistance = (220f + random.nextFloat() * 260f) * density
        walkCycleStartX = centerX
        walkMotionFacing = facing
        walkCycleDistance = walkSpeed * WALK_CYCLE_SECONDS
        lastWalkFrameIndex = 0
        setFrames(intArrayOf(4, 5, 6, 7, 8, 9, 10, 11), IntArray(8) { 170 })
        stateElapsed = 0f
    }

    private fun enterObserve(): Unit {
        mode = Mode.OBSERVE
        stationaryX = centerX
        markAction(mode)
        logTransition("observe")
        investigateStartX = centerX
        investigateTargetX = (centerX + if (random.nextBoolean()) 22f else -22f).coerceIn(
            spriteHalfSize,
            screenWidth - spriteHalfSize,
        )
        setFrames(intArrayOf(24, 25, 26, 27), intArrayOf(900, 1200, 900, 1100))
        stateElapsed = 0f
    }

    private fun enterDragged(): Unit {
        mode = Mode.DRAGGED
        stationaryX = centerX
        logTransition("dragged")
        setFrames(intArrayOf(0), intArrayOf(60_000))
        stateElapsed = 0f
    }

    private fun enterStalk(): Unit {
        mode = Mode.STALK
        stationaryX = centerX
        markAction(mode)
        logTransition("stalk")
        targetX = (centerX + if (facing > 0f) 90f else -90f).coerceIn(
            spriteHalfSize,
            screenWidth - spriteHalfSize,
        )
        stalkDuration = 2.2f + random.nextFloat() * 1.4f
        stalkSpeed = (14f + random.nextFloat() * 8f) * density
        setFrames(intArrayOf(0), intArrayOf(3_600))
        stateElapsed = 0f
    }

    private fun enterPounce(): Unit {
        mode = Mode.POUNCE
        markAction(mode)
        logTransition("pounce")
        pounceStartX = centerX
        pounceTargetX = (centerX + if (facing > 0f) 110f * density else -110f * density)
            .coerceIn(spriteHalfSize, screenWidth - spriteHalfSize)
        setFrames(intArrayOf(28, 29, 30, 31), intArrayOf(420, 520, 420, 720))
        pounceDuration = 2.16f
        stateElapsed = 0f
    }

    private fun enterHop(): Unit {
        mode = Mode.HOP
        markAction(mode)
        logTransition("hop")
        hopStartX = centerX
        hopTargetX = (centerX + if (facing > 0f) 46f * density else -46f * density)
            .coerceIn(spriteHalfSize, screenWidth - spriteHalfSize)
        hopStartY = baseY
        hopTargetY = chooseHopTargetY()
        logTransition("hop vertical=${abs(hopTargetY - hopStartY) >= 1f} from=${hopStartY.roundToInt()} to=${hopTargetY.roundToInt()}")
        val hopFrames = when {
            hopTargetY < hopStartY -> intArrayOf(16, 17, 18, 19)
            hopTargetY > hopStartY -> intArrayOf(20, 21, 22, 23)
            else -> intArrayOf(16, 17, 18, 19)
        }
        setFrames(hopFrames, IntArray(hopFrames.size) { 690 })
        hopDuration = LumiMotionController.HOP_DURATION_SECONDS
        stateElapsed = 0f
    }

    private fun enterRecovery(): Unit {
        mode = Mode.RECOVERY
        stationaryX = centerX
        logTransition("recovery")
        setFrames(intArrayOf(31, 3, 0), intArrayOf(520, 900, 1000))
        stateElapsed = 0f
    }

    private fun enterMagic(): Unit {
        mode = Mode.MAGIC
        stationaryX = centerX
        markAction(mode)
        logTransition("magic")
        setFrames(intArrayOf(36, 37, 38, 39), intArrayOf(520, 720, 900, 700))
        stateElapsed = 0f
    }

    private fun enterSocial(): Unit {
        mode = Mode.SOCIAL
        stationaryX = centerX
        markAction(mode)
        logTransition("social")
        setFrames(intArrayOf(24, 25, 26, 27), intArrayOf(520, 720, 760, 1200))
        stateElapsed = 0f
    }

    private fun enterHappy(): Unit {
        mode = Mode.HAPPY
        stationaryX = centerX
        markAction(mode)
        logTransition("happy")
        setFrames(intArrayOf(28, 29, 30, 31), intArrayOf(520, 720, 520, 760))
        stateElapsed = 0f
    }

    private fun enterSleep(): Unit {
        mode = Mode.SLEEP
        stationaryX = centerX
        markAction(mode)
        logTransition("sleep")
        setFrames(intArrayOf(32, 33, 34, 35), intArrayOf(1500, 1800, 1800, 1500))
        stateElapsed = 0f
    }

    private fun enterRest(): Unit {
        mode = Mode.REST
        stationaryX = centerX
        markAction(mode)
        logTransition("rest")
        setFrames(intArrayOf(0, 1, 0), intArrayOf(760, 2400, 760))
        stateElapsed = 0f
    }

    private fun enterSettle(): Unit {
        if (abs(comfortX - centerX) > 8f) {
            enterWalk(comfortX, sustained = false, settleAtDestination = true)
            return
        }
        mode = Mode.SETTLE
        stationaryX = centerX
        logTransition("settle")
        targetX = comfortX
        setFrames(intArrayOf(3, 0), intArrayOf(760, 820))
        stateElapsed = 0f
    }

    private fun enterEdgeObserve(): Unit {
        mode = Mode.EDGE_OBSERVE
        val leftLimit = spriteHalfSize
        val rightLimit = screenWidth - spriteHalfSize
        centerX = centerX.coerceIn(leftLimit, rightLimit)
        stationaryX = centerX
        markAction(mode)
        logTransition("edge_observe")
        edgeSide = if (centerX <= screenWidth / 2f) -1 else 1
        facing = if (edgeSide < 0) 1f else -1f
        setFrames(intArrayOf(4), intArrayOf(1_800))
        stateElapsed = 0f
    }

    private fun enterInteractiveAction(): Unit {
        val options = listOf(Mode.SOCIAL, Mode.POUNCE, Mode.MAGIC, Mode.HAPPY, Mode.OBSERVE)
            .filter { it != mode && isEligible(it) }
        when (options.ifEmpty { listOf(Mode.SOCIAL) }.random(random)) {
            Mode.SOCIAL -> enterSocial()
            Mode.POUNCE -> enterPounce()
            Mode.MAGIC -> enterMagic()
            Mode.HAPPY -> enterHappy()
            else -> enterObserve()
        }
    }

    private fun respondToTap(isDoubleTap: Boolean): Unit {
        if (mode == Mode.SLEEP) {
            sleepiness = (sleepiness - 0.25f).coerceAtLeast(0.1f)
            enterObserve()
            return
        }
        if (mode == Mode.REST) {
            enterSocial()
            return
        }
        if (isDoubleTap && energy > 0.35f && isEligible(Mode.MAGIC)) {
            enterMagic()
            return
        }
        enterInteractiveAction()
    }

    private fun respondToDrop(deltaX: Float, deltaY: Float): Unit {
        baseY = centerY.coerceIn(screenHeight * 0.20f, screenHeight * 0.80f)
        val atEdge = isAtWalkEdge()
        when {
            deltaY < -64f * density && energy > 0.22f -> enterPounce()
            atEdge -> enterEdgeObserve()
            baseY >= screenHeight * 0.74f -> enterRest()
            baseY <= screenHeight * 0.28f -> enterObserve()
            abs(deltaX) > 48f * density -> enterWalk(
                (centerX + deltaX * 1.8f).coerceIn(spriteHalfSize, screenWidth - spriteHalfSize),
                sustained = false,
            )
            else -> enterSocial()
        }
    }

    private fun chooseAutonomousAction(): Unit {
        sleepiness = (sleepiness + 0.004f).coerceAtMost(1f)
        energy = (energy - 0.002f).coerceAtLeast(0.05f)
        if (sleepiness > 0.82f) {
            enterSleep()
            return
        }
        if (energy < 0.24f) {
            enterRest()
            return
        }
        val pool = mutableListOf<Mode>().apply {
            repeat(8) { add(Mode.WALK) }
            repeat(2) { add(Mode.OBSERVE) }
            add(Mode.STALK)
            add(Mode.SOCIAL)
            add(Mode.HAPPY)
            if (energy > 0.5f) add(Mode.POUNCE)
            if (energy > 0.6f && random.nextFloat() < 0.55f) add(Mode.MAGIC)
            if (sleepiness > 0.55f) add(Mode.REST)
        }
        val eligible = pool.filter { isEligible(it) }
        when (eligible.ifEmpty { listOf(Mode.OBSERVE) }.random(random)) {
            Mode.WALK -> enterWalk(randomTargetX())
            Mode.OBSERVE -> enterObserve()
            Mode.STALK -> enterStalk()
            Mode.SOCIAL -> enterSocial()
            Mode.HAPPY -> enterHappy()
            Mode.POUNCE -> enterPounce()
            Mode.MAGIC -> enterMagic()
            else -> enterRest()
        }
    }

    private fun isEligible(candidate: Mode): Boolean {
        val canRepeatHop = candidate == Mode.HOP
        return candidate != mode && (canRepeatHop || !recentModes.contains(candidate)) &&
            (cooldownUntil[candidate] ?: 0f) <= worldTime
    }

    private fun markAction(action: Mode): Unit {
        recentModes.addLast(action)
        while (recentModes.size > 3) recentModes.removeFirst()
        val cooldown = when (action) {
            Mode.POUNCE -> 5.5f
            Mode.MAGIC -> 10f
            Mode.SOCIAL -> 4f
            Mode.HAPPY -> 3f
            Mode.SLEEP -> 14f
            Mode.STALK -> 2f
            Mode.HOP -> 5f
            Mode.EDGE_OBSERVE -> 3f
            else -> 0.8f
        }
        cooldownUntil[action] = worldTime + cooldown
    }

    private fun setFrames(newFrames: IntArray, durationMs: Int): Unit {
        setFrames(newFrames, IntArray(newFrames.size) { durationMs })
    }

    private fun setFrames(newFrames: IntArray, durationsMs: IntArray): Unit {
        frames = newFrames
        frameDurations = durationsMs
        frameIndex = 0
        frameElapsedMs = 0f
    }

    private fun getManualReviewDurations(clip: LumiReviewClip): IntArray {
        return clip.frameDurationsMs.map { (it / manualReviewSpeed).roundToInt().coerceAtLeast(1) }.toIntArray()
    }

    private fun randomTargetX(): Float {
        val leftLimit = spriteHalfSize.roundToInt()
        val rightLimit = (screenWidth - spriteHalfSize).roundToInt()
        return random.nextInt(leftLimit, (rightLimit + 1).coerceAtLeast(leftLimit + 1)).toFloat()
    }

    private fun nextWalkTarget(): Float {
        val leftLimit = spriteHalfSize
        val rightLimit = screenWidth - spriteHalfSize
        val preferredTarget = when {
            centerX < screenWidth * 0.34f -> screenWidth * 0.82f
            centerX > screenWidth * 0.66f -> screenWidth * 0.18f
            facing > 0f -> screenWidth * 0.82f
            else -> screenWidth * 0.18f
        }
        val jitter = (random.nextFloat() - 0.5f) * screenWidth * 0.12f
        val candidate = (preferredTarget + jitter).coerceIn(leftLimit, rightLimit)
        return if (abs(candidate - centerX) >= screenWidth * 0.24f) {
            candidate
        } else {
            (centerX + if (facing > 0f) screenWidth * 0.32f else -screenWidth * 0.32f)
                .coerceIn(leftLimit, rightLimit)
        }
    }

    private fun beginWalkTurn(nextTarget: Float, nextFacing: Float): Unit {
        val leftLimit = spriteHalfSize
        val rightLimit = screenWidth - spriteHalfSize
        walkTurnTargetX = nextTarget.coerceIn(leftLimit, rightLimit)
        facing = nextFacing
        walkMotionFacing = nextFacing
        walkTurnRemaining = 1.04f + random.nextFloat() * 0.68f
        setFrames(intArrayOf(12, 13, 14, 15), IntArray(4) { 425 })
        stateElapsed = 0f
        logTransition("walk_turn target=${walkTurnTargetX.roundToInt()}")
    }

    private fun isAtWalkEdge(): Boolean {
        val edgeMargin = maxOf(24f * density, screenWidth * 0.025f)
        val leftLimit = spriteHalfSize
        val rightLimit = screenWidth - spriteHalfSize
        return centerX <= leftLimit + edgeMargin || centerX >= rightLimit - edgeMargin
    }

    private fun edgeContinueTarget(): Float {
        val leftLimit = spriteHalfSize
        val rightLimit = screenWidth - spriteHalfSize
        return if (centerX <= (leftLimit + rightLimit) / 2f) {
            rightLimit
        } else {
            leftLimit
        }
    }

    private fun chooseHopTargetY(): Float {
        if (random.nextFloat() >= 0.55f) return hopStartY
        val upperLimit = screenHeight * 0.24f
        val lowerLimit = screenHeight * 0.78f
        val minimumShift = 56f * density
        val roomUp = hopStartY - upperLimit
        val roomDown = lowerLimit - hopStartY
        val canRise = roomUp >= minimumShift
        val canDescend = roomDown >= minimumShift
        if (!canRise && !canDescend) return hopStartY
        val rise = when {
            !canRise -> false
            !canDescend -> true
            else -> random.nextBoolean()
        }
        val shift = (56f + random.nextFloat() * 48f) * density
        return if (rise) {
            (hopStartY - shift).coerceAtLeast(upperLimit)
        } else {
            (hopStartY + shift).coerceAtMost(lowerLimit)
        }
    }

    private fun walkPhase(): Float {
        val segmentStarts = floatArrayOf(0f, 0.08f, 0.22f, 0.34f, 0.50f, 0.62f, 0.76f, 0.88f)
        val segmentEnds = floatArrayOf(0.08f, 0.22f, 0.34f, 0.50f, 0.62f, 0.76f, 0.88f, 1f)
        val index = frameIndex.coerceIn(0, segmentStarts.lastIndex)
        val duration = frameDurations[frameIndex.coerceIn(0, frameDurations.lastIndex)].toFloat()
        val local = (frameElapsedMs / duration).coerceIn(0f, 1f)
        return segmentStarts[index] + (segmentEnds[index] - segmentStarts[index]) * local
    }

    private fun continueWalkFromEdge(): Unit {
        val destination = if (edgeSide < 0) {
            screenWidth - spriteHalfSize
        } else {
            spriteHalfSize
        }
        enterWalk(destination)
        beginWalkTurn(destination, if (destination >= centerX) 1f else -1f)
    }

    private fun logTransition(label: String): Unit {
        android.util.Log.d(TAG, "transition=$label x=${centerX.roundToInt()} y=${centerY.roundToInt()} energy=${energy.format()} sleepiness=${sleepiness.format()}")
    }

    private fun Float.format(): String = "%.2f".format(java.util.Locale.US, this)

    private enum class Mode {
        IDLE,
        OBSERVE,
        WALK,
        STALK,
        POUNCE,
        HOP,
        RECOVERY,
        SOCIAL,
        MAGIC,
        HAPPY,
        SLEEP,
        REST,
        SETTLE,
        DRAGGED,
        EDGE_OBSERVE,
        MANUAL_REVIEW,
    }

    private companion object {
        const val TAG: String = "LumiDebugOverlay"
        const val SPEC_PATH: String = "pets/lumi/lumi_motion_v2.json"
        const val WALK_CYCLE_SECONDS: Float = 1.44f
    }
}
