package com.pixelpals.app.core.motion

import kotlin.math.abs
import kotlin.math.hypot

internal enum class MokiMode {
    PERCH,
    CRAWL,
    CORNER,
    CAMOUFLAGE,
    TONGUE,
    DRAGGING,
    FLING,
    LANDING
}

internal enum class MokiSurface(val rotationDegrees: Float) {
    BOTTOM(0f),
    RIGHT(-90f),
    TOP(180f),
    LEFT(90f);

    fun next(): MokiSurface = entries[(ordinal + 1) % entries.size]
}

internal data class MokiPose(
    val x: Float,
    val y: Float,
    val rotationDegrees: Float,
    val frameIndex: Int,
    val mode: MokiMode,
    val surface: MokiSurface
)

internal class MokiMotionController(
    private val density: Float,
    /** Clearance superior ADICIONAL (dp) para UIs con header (solo preview debug). */
    private val topClearanceDp: Float = 0f,
) {
    var mode: MokiMode = MokiMode.PERCH
        private set
    var surface: MokiSurface = MokiSurface.BOTTOM
        private set
    private var frameIndex: Int = 0
    private var stateTime: Float = 0f
    private var edgeProgress: Float = 0.16f
    private var completedEdges: Int = 0
    private var hasRestedOnCurrentEdge: Boolean = false
    private var viewportWidth: Float = 1f
    private var viewportHeight: Float = 1f
    private var trackLeft: Float = 1f
    private var trackRight: Float = 1f
    private var trackTop: Float = 1f
    private var trackBottom: Float = 1f
    private var positionX: Float = 0f
    private var positionY: Float = 0f
    private var rotationDegrees: Float = 0f
    private var velocityX: Float = 0f
    private var velocityY: Float = 0f
    private var landingStartX: Float = 0f
    private var landingStartY: Float = 0f
    private var landingStartRotation: Float = 0f
    private var landingTargetX: Float = 0f
    private var landingTargetY: Float = 0f
    private var landingSurface: MokiSurface = MokiSurface.BOTTOM
    private var landingProgress: Float = 0f

    fun updateViewport(
        width: Int,
        height: Int,
        drawSize: Float,
        topSystemInset: Int = 0,
        bottomSystemInset: Int = 0,
    ): Unit {
        viewportWidth = width.coerceAtLeast(1).toFloat()
        viewportHeight = height.coerceAtLeast(1).toFloat()
        val contactOffset: Float = drawSize * 0.43f
        val edgeMargin: Float = DEFAULT_EDGE_MARGIN_DP * density
        trackLeft = contactOffset + edgeMargin
        trackRight = viewportWidth - contactOffset - edgeMargin
        trackTop = topSystemInset + contactOffset + topClearanceDp * density + TOP_CLEARANCE_DP * density
        trackBottom = viewportHeight - bottomSystemInset - contactOffset - edgeMargin
        if (mode !in setOf(MokiMode.DRAGGING, MokiMode.FLING, MokiMode.LANDING)) {
            setAnchoredPosition()
        }
    }

    fun update(deltaSeconds: Float): MokiPose {
        val dt: Float = deltaSeconds.coerceIn(0f, MAX_STEP_SECONDS)
        stateTime += dt
        when (mode) {
            MokiMode.PERCH -> updatePerch()
            MokiMode.CRAWL -> updateCrawl(dt)
            MokiMode.CORNER -> updateCorner()
            MokiMode.CAMOUFLAGE -> updateCamouflage()
            MokiMode.TONGUE -> updateTongue()
            MokiMode.DRAGGING -> frameIndex = 17
            MokiMode.FLING -> updateFling(dt)
            MokiMode.LANDING -> updateLanding()
        }
        return getPose()
    }

    fun getPose(): MokiPose = MokiPose(positionX, positionY, rotationDegrees, frameIndex, mode, surface)

    fun startTongueStrike(): Unit {
        mode = MokiMode.TONGUE
        stateTime = 0f
        frameIndex = 13
    }

    fun startDrag(x: Float, y: Float): Unit {
        mode = MokiMode.DRAGGING
        stateTime = 0f
        frameIndex = 17
        rotationDegrees = 0f
        moveDrag(x, y)
    }

    fun moveDrag(x: Float, y: Float): Unit {
        if (mode != MokiMode.DRAGGING) return
        positionX = x.coerceIn(trackLeft, trackRight)
        positionY = y.coerceIn(trackTop, trackBottom)
    }

    fun releaseDrag(releaseVelocityX: Float, releaseVelocityY: Float): Unit {
        if (mode != MokiMode.DRAGGING) return
        mode = MokiMode.FLING
        stateTime = 0f
        frameIndex = 18
        velocityX = releaseVelocityX.coerceIn(-MAX_FLING_SPEED * density, MAX_FLING_SPEED * density)
        velocityY = releaseVelocityY.coerceIn(-MAX_FLING_SPEED * density, MAX_FLING_SPEED * density)
        if (hypot(velocityX, velocityY) < MIN_FLING_SPEED * density) {
            velocityX = MIN_FLING_SPEED * density
            velocityY = -MIN_FLING_SPEED * density * 0.45f
        }
    }

    private fun updatePerch(): Unit {
        setAnchoredPosition()
        frameIndex = when {
            stateTime < 0.45f -> 0
            stateTime < 0.85f -> 1
            stateTime < 1.25f -> 2
            stateTime < 1.65f -> 0
            else -> 3
        }
        if (stateTime >= PERCH_DURATION_SECONDS) changeMode(MokiMode.CRAWL)
    }

    private fun updateCrawl(dt: Float): Unit {
        val edgeLength: Float = getEdgeLength(surface)
        edgeProgress += CRAWL_SPEED_DP * density * dt / edgeLength.coerceAtLeast(1f)
        frameIndex = 4 + ((stateTime / CRAWL_FRAME_SECONDS).toInt() % 4)
        val shouldRest: Boolean = completedEdges > 0 && completedEdges % REST_EDGE_INTERVAL == 0
        if (shouldRest && !hasRestedOnCurrentEdge && edgeProgress >= REST_EDGE_PROGRESS) {
            hasRestedOnCurrentEdge = true
            setAnchoredPosition()
            changeMode(MokiMode.PERCH)
            return
        }
        if (edgeProgress >= 1f) {
            edgeProgress = 1f
            changeMode(MokiMode.CORNER)
        }
        setAnchoredPosition()
    }

    private fun updateCorner(): Unit {
        val progress: Float = (stateTime / CORNER_DURATION_SECONDS).coerceIn(0f, 1f)
        frameIndex = when {
            progress < 0.25f -> 8
            progress < 0.50f -> 9
            progress < 0.75f -> 10
            else -> 11
        }
        // The corner frames already contain the 90-degree body turn.
        rotationDegrees = surface.rotationDegrees
        setCornerPosition()
        if (progress >= 1f) finishCorner()
    }

    private fun updateCamouflage(): Unit {
        setAnchoredPosition()
        frameIndex = if (stateTime in 0.5f..1.55f) 12 else 0
        if (stateTime >= CAMOUFLAGE_DURATION_SECONDS) changeMode(MokiMode.PERCH)
    }

    private fun updateTongue(): Unit {
        setAnchoredPosition()
        frameIndex = when {
            stateTime < 0.16f -> 13
            stateTime < 0.31f -> 14
            stateTime < 0.48f -> 15
            else -> 16
        }
        if (stateTime >= TONGUE_DURATION_SECONDS) changeMode(MokiMode.PERCH)
    }

    private fun updateFling(dt: Float): Unit {
        frameIndex = 18
        positionX += velocityX * dt
        positionY += velocityY * dt
        velocityY += FLING_GRAVITY_DP * density * dt
        rotationDegrees += velocityX * dt * 0.018f
        val hasHitTrack: Boolean = positionX !in trackLeft..trackRight || positionY !in trackTop..trackBottom
        if ((hasHitTrack && stateTime > 0.12f) || stateTime >= FLING_DURATION_SECONDS) startLanding()
    }

    private fun updateLanding(): Unit {
        val progress: Float = (stateTime / LANDING_DURATION_SECONDS).coerceIn(0f, 1f)
        val eased: Float = smoothStep(progress)
        frameIndex = 19
        positionX = landingStartX + (landingTargetX - landingStartX) * eased
        positionY = landingStartY + (landingTargetY - landingStartY) * eased
        val endRotation: Float = unwrapRotation(landingStartRotation, landingSurface.rotationDegrees)
        rotationDegrees = landingStartRotation + (endRotation - landingStartRotation) * eased
        if (progress >= 1f) {
            surface = landingSurface
            edgeProgress = landingProgress
            hasRestedOnCurrentEdge = false
            changeMode(MokiMode.PERCH)
        }
    }

    private fun finishCorner(): Unit {
        surface = surface.next()
        edgeProgress = 0f
        completedEdges += 1
        hasRestedOnCurrentEdge = false
        changeMode(MokiMode.CRAWL)
    }

    private fun startLanding(): Unit {
        landingStartX = positionX
        landingStartY = positionY
        landingStartRotation = rotationDegrees
        landingSurface = getNearestSurface()
        landingProgress = getProgressForSurface(landingSurface, positionX, positionY)
        val target: Pair<Float, Float> = getAnchoredPosition(landingSurface, landingProgress)
        landingTargetX = target.first
        landingTargetY = target.second
        changeMode(MokiMode.LANDING)
    }

    private fun getNearestSurface(): MokiSurface {
        val distances: Map<MokiSurface, Float> = mapOf(
            MokiSurface.BOTTOM to abs(positionY - trackBottom),
            MokiSurface.RIGHT to abs(positionX - trackRight),
            MokiSurface.TOP to abs(positionY - trackTop),
            MokiSurface.LEFT to abs(positionX - trackLeft),
        )
        return distances.minBy { it.value }.key
    }

    private fun getProgressForSurface(targetSurface: MokiSurface, x: Float, y: Float): Float {
        val horizontalLength: Float = (trackRight - trackLeft).coerceAtLeast(1f)
        val verticalLength: Float = (trackBottom - trackTop).coerceAtLeast(1f)
        return when (targetSurface) {
            MokiSurface.BOTTOM -> (x - trackLeft) / horizontalLength
            MokiSurface.RIGHT -> (trackBottom - y) / verticalLength
            MokiSurface.TOP -> (trackRight - x) / horizontalLength
            MokiSurface.LEFT -> (y - trackTop) / verticalLength
        }.coerceIn(0f, 1f)
    }

    private fun setAnchoredPosition(): Unit {
        val position: Pair<Float, Float> = getAnchoredPosition(surface, edgeProgress)
        positionX = position.first
        positionY = position.second
        rotationDegrees = surface.rotationDegrees
    }

    private fun setCornerPosition(): Unit {
        val position: Pair<Float, Float> = getAnchoredPosition(surface, 1f)
        positionX = position.first
        positionY = position.second
    }

    private fun getAnchoredPosition(targetSurface: MokiSurface, progress: Float): Pair<Float, Float> {
        return when (targetSurface) {
            MokiSurface.BOTTOM -> Pair(trackLeft + (trackRight - trackLeft) * progress, trackBottom)
            MokiSurface.RIGHT -> Pair(trackRight, trackBottom - (trackBottom - trackTop) * progress)
            MokiSurface.TOP -> Pair(trackRight - (trackRight - trackLeft) * progress, trackTop)
            MokiSurface.LEFT -> Pair(trackLeft, trackTop + (trackBottom - trackTop) * progress)
        }
    }

    private fun getEdgeLength(targetSurface: MokiSurface): Float = when (targetSurface) {
        MokiSurface.BOTTOM, MokiSurface.TOP -> trackRight - trackLeft
        MokiSurface.RIGHT, MokiSurface.LEFT -> trackBottom - trackTop
    }

    private fun changeMode(nextMode: MokiMode): Unit {
        mode = nextMode
        stateTime = 0f
    }

    private fun smoothStep(value: Float): Float = value * value * (3f - 2f * value)

    private fun unwrapRotation(start: Float, end: Float): Float {
        var adjustedEnd: Float = end
        while (adjustedEnd - start > 180f) adjustedEnd -= 360f
        while (adjustedEnd - start < -180f) adjustedEnd += 360f
        return adjustedEnd
    }

    private companion object {
        const val MAX_STEP_SECONDS: Float = 1f / 30f
        const val PERCH_DURATION_SECONDS: Float = 2.0f
        const val CRAWL_FRAME_SECONDS: Float = 0.15f
        const val CORNER_DURATION_SECONDS: Float = 0.84f
        const val CAMOUFLAGE_DURATION_SECONDS: Float = 2.0f
        const val TONGUE_DURATION_SECONDS: Float = 0.72f
        const val LANDING_DURATION_SECONDS: Float = 0.38f
        const val FLING_DURATION_SECONDS: Float = 0.72f
        const val CRAWL_SPEED_DP: Float = 92f
        const val REST_EDGE_INTERVAL: Int = 3
        const val REST_EDGE_PROGRESS: Float = 0.5f
        const val FLING_GRAVITY_DP: Float = 760f
        const val MIN_FLING_SPEED: Float = 220f
        const val MAX_FLING_SPEED: Float = 1_400f
        const val DEFAULT_EDGE_MARGIN_DP: Float = 18f
        /** Margen extra sobre el inset superior: el camaleón se pega al borde visible. */
        const val TOP_CLEARANCE_DP: Float = 12f
    }
}
