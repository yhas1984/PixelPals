package com.pixelpals.app.behavior

import com.pixelpals.app.PetState
import com.pixelpals.app.launcher.LauncherPlatformRepository
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * GingerBehavior — Gatita juguetona que se sienta, salta entre plataformas
 * del escritorio y camina un poco sobre cada una.
 */
class GingerBehavior(
    bridge: PetViewBridge
) : BaseBehavior(bridge) {
    private val context = (bridge as android.view.View).context

    override val resourceIds = (0..12).map { i ->
        context.resources.getIdentifier(
            "ginger_$i", "drawable", context.packageName
        )
    }

    private enum class Mode {
        SITTING,
        PREPARE_JUMP,
        JUMPING,
        WALKING
    }

    private data class Platform(val x: Float, val y: Float)

    private val platforms = mutableListOf<Platform>()
    private var platformsReady = false
    private var lastPlatformRefreshAt = 0L

    private var mode = Mode.SITTING
    private var modeTimer = 0f

    private var currentPlatformIndex = 0
    private var targetPlatformIndex = 0

    private var jumpStartX = 0f
    private var jumpStartY = 0f
    private var jumpEndX = 0f
    private var jumpEndY = 0f
    private var jumpDir = 1f

    private var walkDir = 1f
    private var facingDir = 1f
    private var walkStartX = 0f
    private var walkTargetX = 0f

    override fun getBaseSpeed(): Float = 78f

    private fun facingScale(directionX: Float, stretch: Float = 1f): Float {
        val magnitude = abs(stretch)
        return if (directionX >= 0f) magnitude else -magnitude
    }

    private fun ensurePlatforms(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && platformsReady && now - lastPlatformRefreshAt < 2_500L) return

        val currentParams = bridge.getWindowParams()
        val currentX = currentParams?.x?.toFloat() ?: bridge.windowX.toFloat()
        val currentY = currentParams?.y?.toFloat() ?: bridge.windowY.toFloat()

        platforms.clear()

        val launcherPlatforms = LauncherPlatformRepository.loadPlatformPoints(
            context = context,
            petSpriteSize = bridge.petSpriteSize
        )
        if (launcherPlatforms.isNotEmpty()) {
            launcherPlatforms.forEach { point ->
                platforms += Platform(
                    x = point.x.coerceIn(
                        0f,
                        (bridge.screenWidth - bridge.petSpriteSize).coerceAtLeast(0).toFloat()
                    ),
                    y = point.y.coerceIn(
                        110f,
                        (bridge.screenHeight - bridge.petSpriteSize - 160)
                            .coerceAtLeast(110)
                            .toFloat()
                    )
                )
            }
        }

        if (platforms.isEmpty()) {
            val minX = 0f
            val maxX = (bridge.screenWidth - bridge.petSpriteSize).coerceAtLeast(0).toFloat()
            val minY = 110f
            val maxY = (bridge.screenHeight - bridge.petSpriteSize - 160).coerceAtLeast(minY.toInt()).toFloat()

            val cols = 4
            val rows = listOf(0.22f, 0.38f, 0.54f, 0.68f)
            val horizontalStep = if (cols > 1) (maxX - minX) / (cols - 1) else 0f

            for (rowRatio in rows) {
                val y = (maxY * rowRatio).coerceIn(minY, maxY)
                for (col in 0 until cols) {
                    val x = (minX + horizontalStep * col).coerceIn(minX, maxX)
                    platforms += Platform(x, y)
                }
            }
        }

        if (platforms.isNotEmpty()) {
            currentPlatformIndex = nearestPlatformIndex(currentX, currentY)
        }

        platformsReady = true
        lastPlatformRefreshAt = now
    }

    private fun nearestPlatformIndex(x: Float, y: Float): Int {
        if (platforms.isEmpty()) return 0
        var bestIdx = 0
        var bestDist = Float.MAX_VALUE
        platforms.forEachIndexed { index, platform ->
            val dx = platform.x - x
            val dy = platform.y - y
            val dist = dx * dx + dy * dy
            if (dist < bestDist) {
                bestDist = dist
                bestIdx = index
            }
        }
        return bestIdx
    }

    private fun placeOnCurrentPlatform() {
        val params = bridge.getWindowParams() ?: return
        val platform = platforms.getOrNull(currentPlatformIndex) ?: return
        params.x = platform.x.roundToInt()
        params.y = platform.y.roundToInt()
        bridge.updateWindowLayout(params)
    }

    private fun startSitting(resetTimer: Boolean = true) {
        mode = Mode.SITTING
        if (resetTimer) modeTimer = 0f
        velX = 0f
        velY = 0f
        bridge.animRotation = 0f
        bridge.animOffsetX = 0f
        bridge.animOffsetY = 0f
        bridge.animScaleX = facingScale(facingDir)
        bridge.animScaleY = 1f
    }

    private fun startJump() {
        ensurePlatforms(force = true)
        if (platforms.size < 2) return

        val current = platforms[currentPlatformIndex]
        val candidates = platforms.indices.filter { it != currentPlatformIndex }
        targetPlatformIndex = candidates.random()
        val target = platforms[targetPlatformIndex]

        jumpStartX = current.x
        jumpStartY = current.y
        jumpEndX = target.x
        jumpEndY = target.y
        jumpDir = if (jumpEndX >= jumpStartX) 1f else -1f
        facingDir = jumpDir

        mode = Mode.PREPARE_JUMP
        modeTimer = 0f
    }

    private fun startWalking() {
        mode = Mode.WALKING
        modeTimer = 0f
        currentPlatformIndex = targetPlatformIndex
        val platform = platforms[currentPlatformIndex]
        val minX = 0f
        val maxX = (bridge.screenWidth - bridge.petSpriteSize).coerceAtLeast(0).toFloat()
        val desiredDistance = Random.nextInt(52, 97).toFloat()
        val preferredDir = jumpDir
        val preferredTarget = platform.x + desiredDistance * preferredDir

        walkDir = when {
            preferredTarget in minX..maxX -> preferredDir
            platform.x - desiredDistance >= minX -> -1f
            platform.x + desiredDistance <= maxX -> 1f
            else -> preferredDir
        }

        walkStartX = platform.x
        walkTargetX = (platform.x + desiredDistance * walkDir).coerceIn(minX, maxX)
        facingDir = walkDir
    }

    override fun updateIdle(dt: Float) {
        if (isLoading || frames.isEmpty()) return
        ensurePlatforms()
        time += dt
        modeTimer += dt

        when (mode) {
            Mode.SITTING -> {
                placeOnCurrentPlatform()
                bridge.animRotation = 0f
                bridge.animOffsetX = 0f
                bridge.animOffsetY = sin(time * 1.5f) * 2f
                bridge.animScaleX = facingScale(facingDir)
                bridge.animScaleY = 1f + sin(time * 1.1f) * 0.015f

                bridge.currentFrame = when {
                    modeTimer < 1.6f -> 0
                    modeTimer < 3.1f -> 1
                    else -> 2
                }

                if (modeTimer >= 4.2f) {
                    startJump()
                }
            }

            Mode.PREPARE_JUMP -> {
                placeOnCurrentPlatform()
                bridge.currentFrame = 2
                bridge.animRotation = 0f
                bridge.animOffsetX = 0f
                bridge.animOffsetY = sin(modeTimer * 10f) * 2f
                bridge.animScaleX = facingScale(facingDir)
                bridge.animScaleY = 1f

                if (modeTimer >= 0.35f) {
                    mode = Mode.JUMPING
                    modeTimer = 0f
                }
            }

            Mode.JUMPING -> {
                val params = bridge.getWindowParams() ?: return
                val t = (modeTimer / 1.0f).coerceIn(0f, 1f)
                val arcHeight = bridge.petSpriteSize * 0.6f + abs(jumpEndX - jumpStartX) * 0.08f

                val x = jumpStartX + (jumpEndX - jumpStartX) * t
                val groundY = jumpStartY + (jumpEndY - jumpStartY) * t
                val y = groundY - sin((t * Math.PI).toFloat()) * arcHeight

                params.x = x.roundToInt()
                params.y = y.roundToInt()
                bridge.updateWindowLayout(params)

                bridge.animScaleX = facingScale(facingDir)
                bridge.animScaleY = 1f
                bridge.animOffsetX = 0f
                bridge.animOffsetY = 0f
                bridge.animRotation = jumpDir * 4f

                bridge.currentFrame = when {
                    t < 0.12f -> 4
                    t < 0.24f -> 5
                    t < 0.40f -> 6
                    t < 0.58f -> 7
                    t < 0.76f -> 8
                    else -> 9
                }

                if (t >= 1f) {
                    startWalking()
                }
            }

            Mode.WALKING -> {
                val params = bridge.getWindowParams() ?: return
                val platform = platforms[currentPlatformIndex]
                val walkDuration = 1.9f
                val t = (modeTimer / walkDuration).coerceIn(0f, 1f)
                val easedT = sin((t * Math.PI.toFloat()) / 2f)
                val x = walkStartX + (walkTargetX - walkStartX) * easedT

                params.x = x.roundToInt()
                params.y = platform.y.roundToInt()
                bridge.updateWindowLayout(params)

                val walkFrame = if (((modeTimer / 0.12f).toInt() % 2) == 0) 11 else 12
                bridge.currentFrame = walkFrame
                bridge.animScaleX = if (walkFrame == 12) {
                    facingScale(-walkDir)
                } else {
                    facingScale(walkDir)
                }
                bridge.animScaleY = 1f
                bridge.animRotation = 0f
                bridge.animOffsetX = 0f
                bridge.animOffsetY = sin(time * 8f) * 1.5f

                if (t >= 1f) {
                    startSitting()
                }
            }
        }

        if (Random.nextFloat() < 0.0012f) {
            bridge.showBubble("miau")
        }
    }

    override fun onInteract() {
        super.onInteract()
        bridge.showBubble("😻")
    }

    override fun updateInteracting(dt: Float) {
        if (frames.isEmpty()) return
        ensurePlatforms()
        interactionTimer += dt

        placeOnCurrentPlatform()
        bridge.currentFrame = 3
        bridge.animScaleX = facingScale(facingDir)
        bridge.animScaleY = 1f + sin(interactionTimer * 8f) * 0.03f
        bridge.animRotation = sin(interactionTimer * 6f) * 4f
        bridge.animOffsetX = 0f
        bridge.animOffsetY = sin(interactionTimer * 8f) * 2f

        if (interactionTimer >= 1.35f) {
            bridge.state = PetState.IDLE
            startSitting()
            reset()
        }
    }

    override fun updateDrag(dt: Float) {
        bridge.currentFrame = 1
        bridge.animRotation = 0f
        bridge.animScaleX = facingScale(facingDir)
        bridge.animScaleY = 1f
        bridge.animOffsetX = 0f
        bridge.animOffsetY = 0f
    }

    override fun updateFalling(dt: Float) {
        ensurePlatforms()
        val params = bridge.getWindowParams() ?: return
        currentPlatformIndex = nearestPlatformIndex(params.x.toFloat(), params.y.toFloat())
        bridge.state = PetState.IDLE
        startSitting()
        reset()
    }

    override fun reset() {
        super.reset()
        if (bridge.state == PetState.IDLE) {
            bridge.animAlpha = 1f
            bridge.animScaleX = facingScale(facingDir)
            bridge.animScaleY = 1f
            bridge.animRotation = 0f
            bridge.animOffsetX = 0f
            bridge.animOffsetY = 0f
        }
    }
}
