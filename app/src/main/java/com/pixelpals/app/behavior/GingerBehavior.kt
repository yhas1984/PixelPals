package com.pixelpals.app.behavior

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import com.pixelpals.app.PetState
import com.pixelpals.app.R
import kotlin.math.*
import kotlin.random.Random

/**
 * GingerBehavior — Refactored Feline Elegance.
 */
class GingerBehavior(
    private val bridge: PetViewBridge
) : PetBehavior {

    private val frames = mutableListOf<Bitmap>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // --- State Enums ---
    enum class Pose {
        SITTING,        // Frame 4
        STANDING,       // Frame 2
        WALKING,        // Frames 0-1 alternating
        STRETCHING,     // Frame 0/1 held
        BELLY_RUB,      // Frames 7-10 sequence
        FALLING,        // Air tumble
        JUMPING         // Flying arc
    }

    // --- State Vars ---
    private var currentPose = Pose.SITTING
    private var poseTimer = 0f
    private var totalTime = 0f
    
    private var isTransitioning = false
    private var transitionFrames = listOf<Int>()
    private var transitionDurations = listOf<Float>()
    private var transitionIndex = 0
    private var transitionTimer = 0f

    private var affection = 0
    private var boredom = 0f
    private var isPouting = false
    private var winkTimer = 0f
    private var groomingPhase = 0
    private var groomingPauseTimer = 0f

    private var moveDecisionTimer = 0f
    private var nextMoveTime = 3f + Random.nextFloat() * 2f
    private var walkTimer = 0f
    private var targetX = 0

    init {
        loadFrames()
    }

    private fun loadFrames() {
        val context = (bridge as View).context
        val resIds = listOf(
            R.drawable.ginger_0, R.drawable.ginger_1, R.drawable.ginger_2,
            R.drawable.ginger_3, R.drawable.ginger_4, R.drawable.ginger_5,
            R.drawable.ginger_6, R.drawable.ginger_7, R.drawable.ginger_8,
            R.drawable.ginger_9, R.drawable.ginger_10
        )
        for (id in resIds) {
            val b = BitmapFactory.decodeResource(context.resources, id)
            if (b != null) {
                frames.add(Bitmap.createScaledBitmap(b, bridge.petSpriteSize, bridge.petSpriteSize, true))
            }
        }
    }

    override fun updateIdle(dt: Float) {
        totalTime += dt
        poseTimer += dt
        if (updateTransition(dt)) return
        
        updateBoredom(dt)
        if (isPouting) {
            bridge.currentFrame = 7
            return
        }

        when (currentPose) {
            Pose.SITTING -> updateSitting(dt)
            Pose.STANDING -> updateStanding(dt)
            Pose.WALKING -> updateWalking(dt)
            else -> bridge.currentFrame = 4
        }
    }

    private fun updateSitting(dt: Float) {
        groomingPauseTimer += dt
        when (groomingPhase) {
            0 -> { bridge.currentFrame = 6; if (groomingPauseTimer > 2.5f) { groomingPhase = 1; groomingPauseTimer = 0f } }
            1 -> { bridge.currentFrame = 4; if (groomingPauseTimer > 2.0f) { groomingPhase = 2; groomingPauseTimer = 0f } }
            2 -> { bridge.currentFrame = 5; if (groomingPauseTimer > 2.5f) { groomingPhase = 3; groomingPauseTimer = 0f } }
            3 -> { bridge.currentFrame = 4; if (groomingPauseTimer > 2.0f) { groomingPhase = 0; groomingPauseTimer = 0f } }
        }

        val breathe = sin(totalTime * 1.5f) * 0.015f
        bridge.animScaleY = 1f + breathe
        bridge.animScaleX = 1f - breathe * 0.5f

        moveDecisionTimer += dt
        if (moveDecisionTimer > nextMoveTime) {
            startTransition(listOf(4, 0, 1, 2), listOf(0.4f, 0.6f, 0.6f, 0.3f), Pose.STANDING)
            moveDecisionTimer = 0f
            nextMoveTime = 4f + Random.nextFloat() * 4f
        }
    }

    private fun updateStanding(dt: Float) {
        bridge.currentFrame = 2
        moveDecisionTimer += dt
        if (moveDecisionTimer > 1.5f) {
            if (Random.nextFloat() < 0.7f) {
                currentPose = Pose.WALKING
                walkTimer = 0f
                targetX = Random.nextInt(50, bridge.screenWidth - bridge.petSpriteSize - 50)
                bridge.velocityX = if (targetX > bridge.windowX) 3f else -3f
            } else {
                startTransition(listOf(2, 1, 0, 4), listOf(0.3f, 0.5f, 0.5f, 0.3f), Pose.SITTING)
            }
            moveDecisionTimer = 0f
        }
    }

    private fun updateWalking(dt: Float) {
        walkTimer += dt
        val params = bridge.getWindowParams() ?: return
        params.x += (bridge.velocityX * dt * 60f).toInt()
        params.x = params.x.coerceIn(20, bridge.screenWidth - bridge.petSpriteSize - 20)
        bridge.currentFrame = if ((walkTimer * 3.5f).toInt() % 2 == 0) 0 else 1
        bridge.animScaleX = if (bridge.velocityX > 0) -1f else 1f
        if (abs(params.x - targetX) < 10 || walkTimer > 5f) {
            bridge.velocityX = 0f
            currentPose = Pose.STANDING
            moveDecisionTimer = 0f
        }
        bridge.updateWindowLayout(params)
    }

    override fun updateDrag(dt: Float) {
        bridge.currentFrame = if (currentPose == Pose.STANDING || currentPose == Pose.WALKING) 2 else 4
        if (Random.nextFloat() < 0.05f) affection = (affection + 1).coerceAtMost(100)
    }

    override fun updateFalling(dt: Float) {
        val fallTime = totalTime % 0.8f
        bridge.currentFrame = when {
            fallTime < 0.2f -> 1
            fallTime < 0.4f -> 8
            fallTime < 0.6f -> 9
            else -> 0
        }
    }

    override fun updateJumping(dt: Float) { bridge.currentFrame = 1 }

    override fun updateAutonomous(dt: Float) {}

    override fun onInteract() {
        affection = (affection + 10).coerceAtMost(100)
        boredom = 0f
        isPouting = false
        if (affection >= 85 && currentPose == Pose.SITTING) {
            bridge.state = PetState.INTERACTING
            currentPose = Pose.BELLY_RUB
            poseTimer = 0f
        } else {
            bridge.playHaptic(40)
            bridge.state = PetState.INTERACTING
            poseTimer = 0f
        }
    }

    override fun updateInteracting(dt: Float) {
        if (currentPose == Pose.BELLY_RUB) {
            when {
                dt < 1.0f -> bridge.currentFrame = 7
                dt < 2.5f -> bridge.currentFrame = if ((dt * 4f).toInt() % 2 == 0) 8 else 9
                dt < 6.0f -> bridge.currentFrame = 10
                else -> { bridge.state = PetState.IDLE; currentPose = Pose.SITTING }
            }
        } else {
            if (dt > 2.0f) { bridge.state = PetState.IDLE; reset() } else bridge.currentFrame = 5
        }
    }

    private fun updateBoredom(dt: Float) {
        if (bridge.state != PetState.IDLE) return
        boredom += dt
        if (boredom > 40f && !isPouting) isPouting = true
    }

    private fun startTransition(frames: List<Int>, durs: List<Float>, endPose: Pose) {
        isTransitioning = true
        transitionFrames = frames
        transitionDurations = durs
        transitionIndex = 0
        transitionTimer = 0f
        currentPose = endPose
    }

    private fun updateTransition(dt: Float): Boolean {
        if (!isTransitioning) return false
        transitionTimer += dt
        if (transitionIndex < transitionDurations.size && transitionTimer >= transitionDurations[transitionIndex]) {
            transitionIndex++; transitionTimer = 0f
            if (transitionIndex < transitionFrames.size) bridge.currentFrame = transitionFrames[transitionIndex]
        }
        if (transitionIndex >= transitionFrames.size) isTransitioning = false
        return isTransitioning
    }

    override fun onDraw(canvas: Canvas, cx: Float, cy: Float) {
        if (frames.isEmpty()) return
        val frameIdx = bridge.currentFrame.coerceIn(0, frames.size - 1)
        val bitmap = frames[frameIdx]
        canvas.save()
        canvas.translate(cx + bridge.animOffsetX, cy + bridge.animOffsetY)
        canvas.rotate(bridge.animRotation)
        canvas.scale(bridge.animScaleX, bridge.animScaleY)
        canvas.drawBitmap(bitmap, -bitmap.width / 2f, -bitmap.height / 2f, paint)
        canvas.restore()
    }

    override fun reset() {
        isTransitioning = false
        isPouting = false
        bridge.animScaleX = 1f
        bridge.animScaleY = 1f
        bridge.animRotation = 0f
    }

    override fun onFling(velocityX: Float, velocityY: Float) {
        currentPose = Pose.BELLY_RUB
        bridge.currentFrame = 10
        bridge.state = PetState.INTERACTING
    }
}
