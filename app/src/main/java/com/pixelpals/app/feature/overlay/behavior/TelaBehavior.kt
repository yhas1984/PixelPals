package com.pixelpals.app.feature.overlay.behavior

import com.pixelpals.app.core.domain.PetState
import com.pixelpals.app.core.motion.PetAnimationClip
import com.pixelpals.app.core.motion.PetAnimationPlayer
import com.pixelpals.app.core.motion.PetRandom
import com.pixelpals.app.core.motion.GroundGait
import com.pixelpals.app.status.PetMood
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import android.view.View

/**
 * TelaBehavior — Arañita de menta-lavanda.
 * Se comporta como una araña de verdad:
 *  - TREPA por los bordes laterales (sube y baja por las paredes),
 *  - CAMINA por el techo (parte superior de la pantalla, patas arriba),
 *  - BAJA desde el techo por una seda visible, se balancea y vuelve a subir,
 *  - Recorre el perímetro de la pantalla entera.
 * IA atlas V2: idle, walk, climb, ceiling, web descend/hang/ascend, land/touch and sleep.
 */
class TelaBehavior(
    bridge: PetViewBridge,
    override val random: PetRandom,
) : BaseBehavior(bridge, random) {

    private val restFade = com.pixelpals.app.core.rest.RestFadeTransition()
    private var scheduledRestRequested: Boolean = false
    override fun onScheduledRestRequested(requested: Boolean) {
        scheduledRestRequested = requested
        if (!requested) cancelRestFade()
    }
    private fun cancelRestFade() {
        if (!restFade.active) return
        restFade.cancel()
        (bridge as? android.view.View)?.alpha = 1f
    }
    override fun advanceScheduledRestTransition(delta: Float, reducedMotion: Boolean) {
        if (!scheduledRestRequested || !reducedMotion) {
            cancelRestFade()
            return
        }
        if (!restFade.active && canStartScheduledSleep(true)) return
        val params = bridge.getWindowParams() ?: return
        if (!restFade.active) restFade.start()
        val reposition: Boolean = restFade.advance(delta)
        (bridge as? android.view.View)?.alpha = restFade.opacity
        if (reposition) {
            params.y = maxY().roundToInt()
            bridge.updateWindowLayout(params)
            clearWebEffects()
            approachScheduledRest(params.x.toFloat(), params.y.toFloat())
        }
    }
    override fun canStartScheduledSleep(reducedMotion: Boolean): Boolean =
        !restFade.active && (mode == Mode.SLEEP || (reducedMotion && mode == Mode.WALK)) &&
            abs((bridge.getWindowParams()?.y ?: Int.MIN_VALUE).toFloat() - maxY()) <= 1f

    override val isSleeping: Boolean get() = mode == Mode.SLEEP
    override val scheduledRestFrame: Int? get() = if (hasRestAtlas && isSleeping) bridge.currentFrame else null

    override fun onScheduledWakeCompleted() {
        if (hasRestAtlas && (mode == Mode.SLEEP || mode == Mode.WAKE)) {
            pendingTouchAfterWake = false
            bridge.animOffsetY = 0f
            activeClipId = null
            decideNext()
        }
    }

    override fun onScheduledSleepInterrupted(frame: Int) {
        if (!hasRestAtlas || frame !in com.pixelpals.app.core.motion.TelaRestPose.restFrames) return
        scheduledRestRequested = false
        pendingTouchAfterWake = false
        mode = Mode.SLEEP
        modeTimer = 0f
        modeDuration = 4f
        activeClipId = null
        selectClipForMode(mode)
        val index: Int = com.pixelpals.app.core.motion.TelaRestPose.restFrames.indexOf(frame)
        animationPlayer.seek(index * com.pixelpals.app.core.motion.TelaRestPose.STEP_SECONDS)
        bridge.currentFrame = animationPlayer.currentFrame()
        bridge.animOffsetX = 0f
        bridge.animOffsetY = 0f
        bridge.animRotation = 0f
        bridge.animScaleY = 1f
    }

    override val resourceIds: List<Int> = emptyList()
    override fun getFrameCameraScale(index: Int): Float = com.pixelpals.app.core.motion.TelaArtworkScale.frame(index)
    override fun isFrameMirrored(index: Int): Boolean = com.pixelpals.app.core.motion.TelaArtworkScale.mirrored(index)
    override val frameGround: Float get() = com.pixelpals.app.core.motion.TelaArtworkScale.ground(bridge.currentFrame)
    override val facingLeft: Boolean get() = if (mode == Mode.WALK) !facingRight else super.facingLeft

    private enum class Mode {
        HANG,
        WALK,
        CLIMB,
        CEILING,
        WEB_DESCEND,
        WEB_HANG,
        WEB_ASCEND,
        HAPPY,
        TOUCH,
        SLEEP,
        WAKE,
    }

    private var mode = Mode.HANG
    private var modeTimer = 0f
    private var modeDuration = 2.5f
    private var fromX = 0f
    private var fromY = 0f
    private var toX = 0f
    private var toY = 0f
    private var facingRight = true
    private var webAnchorX = 0f
    private var webAnchorY = 0f
    private var webTopY = 0f
    private var cornerWebState: TelaCornerWebState? = null
    private var cornerWebTimer = 0f
    /** Prevents a fresh corner web from masking cleanup immediately after input. */
    private var cornerWebCooldown = 0f
    private var animationPlayer = PetAnimationPlayer()
    private var activeClipId: String? = null
    private var hasRestAtlas: Boolean = false
    private var pendingTouchAfterWake: Boolean = false

    init {
        // Tela V2 has passed the shared validator and the NE2213 visual review.
        // V1 remains in assets as a rollback copy, but is no longer selected.
        val context = (bridge as? View)?.context
        val restAsset = context?.assets?.list("pets/tela")?.firstOrNull { it == "tela_rest_v2.json" }
        val atlasPath: String = if (restAsset != null) "pets/tela/tela_rest_v2.json" else "pets/tela/tela_motion_v2.json"
        loadSpriteSheetAssetAsync(atlasPath) { spec ->
            hasRestAtlas = spec.frameCount == 43
            animationPlayer = PetAnimationPlayer(spec.clips.map { clip ->
                PetAnimationClip(
                    id = clip.id,
                    frames = if (clip.id == "sleep" && hasRestAtlas)
                        com.pixelpals.app.core.motion.TelaRestPose.restFrames
                    else if (clip.id == "sleep") com.pixelpals.app.core.motion.TelaRestPose.legacyFrames else clip.frames,
                    loop = clip.loop,
                    frameDurationSeconds = clip.frameDurationMs / 1000f,
                )
            })
            selectClipForMode(mode)
        }
    }

    override fun getBaseSpeed(): Float = 0f

    private fun minX(): Float = bridge.bounds.left.toFloat()
    private fun maxX(): Float = bridge.bounds.right.toFloat()
    private fun minY(): Float = bridge.bounds.top.toFloat()
    private fun maxY(): Float = bridge.bounds.floor.toFloat()

    private fun startMode(m: Mode, duration: Float, fromX: Float, fromY: Float, toX: Float, toY: Float) {
        mode = m
        modeTimer = 0f
        modeDuration = when (m) {
            Mode.WALK, Mode.CLIMB, Mode.CEILING -> GroundGait.duration(
                kotlin.math.hypot(toX - fromX, toY - fromY),
                bridge.petSpriteSize * bridge.spriteScale * if (m == Mode.CLIMB) 2.2f else 1.5f,
                duration,
            )
            else -> duration
        }
        this.fromX = fromX
        this.fromY = fromY
        this.toX = toX
        this.toY = toY
        facingRight = when (m) {
            // En paredes la X no cambia: la orientación la da el lado de la pared.
            Mode.CLIMB -> toX >= bridge.screenWidth / 2f
            Mode.WALK, Mode.CEILING -> toX >= fromX
            else -> facingRight
        }
        selectClipForMode(m)
        if (m == Mode.WALK || m == Mode.CLIMB || m == Mode.CEILING) {
            bridge.animOffsetX = 0f
            bridge.animScaleX = if (m == Mode.WALK) {
                if (facingRight) -1f else 1f
            } else if (facingRight) 1f else -1f
            bridge.animScaleY = 1f
            bridge.animRotation = 0f
            bridge.animOffsetY = 0f
        }
    }

    private fun selectClipForMode(nextMode: Mode): Unit {
        val clipId = when (nextMode) {
            Mode.HANG -> "idle"
            Mode.WALK -> "walk"
            Mode.CLIMB -> "climb"
            Mode.CEILING -> "ceiling"
            Mode.WEB_DESCEND -> "web_descend"
            Mode.WEB_HANG -> "web_hang"
            Mode.WEB_ASCEND -> "web_ascend"
            Mode.HAPPY -> "happy"
            Mode.TOUCH -> "touch"
            Mode.SLEEP -> "sleep"
            Mode.WAKE -> "wake"
        }
        if (activeClipId == clipId) return
        if (!animationPlayer.setClip(clipId)) return
        activeClipId = clipId
        bridge.currentFrame = animationPlayer.currentFrame()
    }

    private fun advanceClip(dt: Float): Unit {
        if (activeClipId == null) return
        bridge.currentFrame = animationPlayer.update(dt)
    }

    private fun advanceTravelClip(distance: Float): Unit {
        val clip = activeClipId?.let { spriteSheetSpec?.clip(it) } ?: return
        val stride = bridge.petSpriteSize * bridge.spriteScale * spriteAtlasDrawScale * .9f
        val cycles = GroundGait.phase(distance, stride)
        animationPlayer.seek(cycles * clip.frames.size * clip.frameDurationMs / 1000f)
        bridge.currentFrame = animationPlayer.currentFrame()
    }

    /** Decide la siguiente acción según la posición actual (perímetro, como araña real). */
    private fun decideNext() {
        val params = bridge.getWindowParams() ?: return
        val x = params.x.toFloat()
        val y = params.y.toFloat()
        if (scheduledRestRequested) {
            approachScheduledRest(x, y)
            return
        }
        val edge = 30f
        val ceilingY = minY().coerceAtMost(maxY())
        val floorY = maxY().coerceAtLeast(ceilingY)
        val leftX = minX()
        val rightX = maxX()
        val atTop = y <= minY() + edge
        val atBottom = y >= maxY() - edge
        val atLeft = x <= leftX + edge
        val atRight = x >= rightX - edge
        val roll = random.nextFloat()
        val corner = when {
            atTop && atLeft -> TelaWebCorner.TOP_LEFT
            atTop && atRight -> TelaWebCorner.TOP_RIGHT
            atBottom && atRight -> TelaWebCorner.BOTTOM_RIGHT
            atBottom && atLeft -> TelaWebCorner.BOTTOM_LEFT
            else -> null
        }

        if (corner != null && cornerWebCooldown <= 0f && roll >= 0.24f && roll < 0.58f) {
            leaveCornerWeb(corner)
        }

        if (atTop && roll < 0.24f) {
            startWebDescend(x, y)
            return
        }

        if (!atTop && bridge.petStatus.mood == PetMood.SLEEPY && roll < 0.08f) {
            startMode(Mode.SLEEP, 3.5f + random.nextFloat() * 2f, x, y, x, y)
            return
        }

        // Circuito horario fijo por el perímetro cuando está cerca de un borde:
        // techo → pared derecha → suelo → pared izquierda. Nunca se queda
        // colgando al azar pegado a un borde.
        when {
            atTop && atLeft -> startMode(Mode.CEILING, 1.8f + random.nextFloat() * 1.4f, x, y, rightX - 20f, ceilingY)
            atTop && atRight -> startMode(Mode.CLIMB, climbDuration(), x, y, rightX, floorY)
            atBottom && atRight -> startMode(Mode.WALK, 2.0f + random.nextFloat() * 1.6f, x, y, leftX + 20f, floorY)
            atBottom && atLeft -> startMode(Mode.CLIMB, climbDuration(), x, y, leftX, ceilingY)
            atTop -> startMode(Mode.CEILING, 1.8f + random.nextFloat() * 1.4f, x, y, rightX - 20f, ceilingY)
            atRight -> startMode(Mode.CLIMB, climbDuration(), x, y, rightX, floorY)
            atBottom -> startMode(Mode.WALK, 2.0f + random.nextFloat() * 1.6f, x, y, leftX + 20f, floorY)
            atLeft -> startMode(Mode.CLIMB, climbDuration(), x, y, leftX, ceilingY)
            else -> {
                // En el aire (colgando de un hilo lejos de los bordes): se
                // balancea un rato y luego sube al techo o se deja caer al
                // suelo — nunca cruza el centro a lo ancho.
                if (roll < 0.60f) {
                    startMode(Mode.HANG, 2.0f + random.nextFloat() * 2.0f, x, y, x, y)
                } else if (roll < 0.80f) {
                    startMode(Mode.CLIMB, 1.2f + random.nextFloat() * 1.2f, x, y, x, minY())
                } else {
                    startMode(Mode.CLIMB, 1.2f + random.nextFloat() * 1.2f, x, y, x, maxY())
                }
            }
        }
    }

    private fun approachScheduledRest(x: Float, y: Float) {
        if (abs(y - maxY()) <= 1f) {
            bridge.updateTelaSilk(null)
            bridge.animOffsetX = 0f
            bridge.animOffsetY = 0f
            bridge.animRotation = 0f
            bridge.animScaleY = 1f
            startMode(Mode.SLEEP, 4f, x, y, x, y)
            return
        }
        val onWall: Boolean = abs(x - minX()) <= 30f || abs(x - maxX()) <= 30f
        if (onWall) {
            startMode(Mode.CLIMB, GroundGait.duration(maxY() - y, bridge.petSpriteSize * 1.8f, 1f), x, y, x, maxY())
        } else if (abs(y - minY()) <= 30f) {
            startMode(Mode.CEILING, GroundGait.duration(maxX() - x, bridge.petSpriteSize * 1.8f, 1f), x, y, maxX(), minY())
        } else {
            webAnchorX = x + bridge.petSpriteSize / 2f
            webAnchorY = 0f
            webTopY = y
            startMode(Mode.WEB_DESCEND, GroundGait.duration(maxY() - y, bridge.petSpriteSize * 1.8f, 1f), x, y, x, maxY())
        }
    }

    override fun updateIdle(dt: Float) {
        if (isLoading || spriteSheetBitmap == null || spriteFrameRects.isEmpty()) return
        val step = dt.coerceIn(0f, 1f / 30f)
        time += step
        modeTimer += step
        cornerWebCooldown = (cornerWebCooldown - step).coerceAtLeast(0f)
        updateCornerWeb(step)

        when (mode) {
            Mode.HANG -> updateHang(step)
            Mode.WALK -> updateWalk(step)
            Mode.CLIMB -> updateClimb(step)
            Mode.CEILING -> updateCeiling(step)
            Mode.WEB_DESCEND -> updateWebDescend(step)
            Mode.WEB_HANG -> updateWebHang(step)
            Mode.WEB_ASCEND -> updateWebAscend(step)
            Mode.HAPPY -> updateHappy(step)
            Mode.TOUCH -> updateTouch(step)
            Mode.SLEEP -> updateSleep(step)
            Mode.WAKE -> updateWake(step)
        }
        syncWindowPosition()
    }

    private fun updateHang(dt: Float) {
        val params = bridge.getWindowParams() ?: return
        selectClipForMode(Mode.HANG)
        advanceClip(dt)
        // Balanceo de araña colgada
        bridge.animRotation = sin(time * 2.4f) * 5f
        bridge.animOffsetX = sin(time * 2.4f) * 3f
        bridge.animOffsetY = abs(sin(time * 1.8f)) * 2f
        bridge.animScaleX = if (facingRight) 1f else -1f
        bridge.animScaleY = 1f

        if (modeTimer >= modeDuration) {
            modeTimer = 0f
            decideNext()
        }
    }

    private fun updateWake(dt: Float): Unit {
        selectClipForMode(Mode.WAKE)
        advanceClip(dt)
        if (modeTimer >= modeDuration) {
            modeTimer = 0f
            if (pendingTouchAfterWake) {
                pendingTouchAfterWake = false
                beginTouch()
            } else decideNext()
        }
    }

    private fun updateWalk(dt: Float) {
        val params = bridge.getWindowParams() ?: return
        val t = (modeTimer / modeDuration).coerceIn(0f, 1f)
        val eased = GroundGait.progress(modeTimer, modeDuration)
        val x = fromX + (toX - fromX) * eased
        val y = fromY + (toY - fromY) * eased
        params.x = x.roundToInt()
        params.y = y.roundToInt()
        bridge.updateWindowLayout(params)

        selectClipForMode(Mode.WALK)
        advanceTravelClip(x - fromX)
        // The canonical walking artwork faces left; mirrored source frames are
        // normalized separately before applying the travel direction.
        bridge.animScaleX = if (facingRight) -1f else 1f
        bridge.animScaleY = 1f
        // Pitch follows the planted gait and fades at each end of the trip.
        bridge.animRotation = sin((x - fromX) / (bridge.petSpriteSize * bridge.spriteScale).coerceAtLeast(1f) * 2f * PI.toFloat()) *
            2f * sin(t * PI.toFloat())
        bridge.animOffsetY = 0f

        if (t >= 1f) {
            modeTimer = 0f
            decideNext()
        }
    }

    private fun updateClimb(dt: Float) {
        val params = bridge.getWindowParams() ?: return
        val t = (modeTimer / modeDuration).coerceIn(0f, 1f)
        val eased = GroundGait.progress(modeTimer, modeDuration)
        val x = fromX + (toX - fromX) * eased
        val y = fromY + (toY - fromY) * eased
        params.x = x.roundToInt()
        params.y = y.roundToInt()
        bridge.updateWindowLayout(params)

        selectClipForMode(Mode.CLIMB)
        advanceTravelClip(kotlin.math.hypot(x - fromX, y - fromY))
        // En paredes, la araña conserva la orientación de la superficie.
        bridge.animScaleX = if (facingRight) 1f else -1f
        bridge.animScaleY = 1f
        bridge.animRotation = 0f
        bridge.animOffsetY = 0f

        if (t >= 1f) {
            modeTimer = 0f
            decideNext()
        }
    }

    private fun updateCeiling(dt: Float) {
        val params = bridge.getWindowParams() ?: return
        val t = (modeTimer / modeDuration).coerceIn(0f, 1f)
        val eased = GroundGait.progress(modeTimer, modeDuration)
        val x = fromX + (toX - fromX) * eased
        params.x = x.roundToInt()
        // A near-edge release can start below the ceiling. Establish contact
        // smoothly before keeping the body on that surface.
        val contact: Float = GroundGait.progress(modeTimer, CEILING_CONTACT_SECONDS)
        params.y = (fromY + (minY() - fromY) * contact).roundToInt()
        bridge.updateWindowLayout(params)

        selectClipForMode(Mode.CEILING)
        advanceTravelClip(x - fromX)
        // El clip V2 de techo ya está pintado invertido: cabeza hacia abajo y
        // las patas tocando el borde superior. No lo volvemos a voltear en el
        // renderer (hacer scaleY=-1 lo dejaba cabeza arriba).
        bridge.animScaleX = if (facingRight) 1f else -1f
        bridge.animScaleY = 1f
        bridge.animRotation = 0f
        bridge.animOffsetY = 0f

        if (t >= 1f) {
            modeTimer = 0f
            decideNext()
        }
    }

    private fun startWebDescend(x: Float, y: Float) {
        webAnchorX = x + bridge.petSpriteSize / 2f
        webAnchorY = 0f
        webTopY = y
        val descentY = (minY() + (maxY() - minY()) * (0.45f + random.nextFloat() * 0.2f))
            .coerceIn(minY(), maxY())
        startMode(Mode.WEB_DESCEND, 1.7f, x, y, x, descentY)
    }

    private fun leaveCornerWeb(corner: TelaWebCorner) {
        val centerX = when (corner) {
            TelaWebCorner.TOP_LEFT,
            TelaWebCorner.BOTTOM_LEFT -> 0f
            TelaWebCorner.TOP_RIGHT,
            TelaWebCorner.BOTTOM_RIGHT -> bridge.screenWidth.toFloat()
        }
        val centerY = when (corner) {
            TelaWebCorner.TOP_LEFT,
            TelaWebCorner.TOP_RIGHT -> 0f
            TelaWebCorner.BOTTOM_LEFT,
            TelaWebCorner.BOTTOM_RIGHT -> bridge.screenHeight.toFloat()
        }
        val next = TelaCornerWebState(
            corner = corner,
            centerX = centerX,
            centerY = centerY,
            radius = bridge.petSpriteSize * 0.9f,
        )
        cornerWebState = next
        cornerWebTimer = 4.8f
        bridge.updateTelaCornerWeb(next)
    }

    private fun updateCornerWeb(dt: Float) {
        if (cornerWebTimer <= 0f) return
        cornerWebTimer -= dt
        val current = cornerWebState ?: return
        if (cornerWebTimer <= 0f) {
            cornerWebState = null
            bridge.updateTelaCornerWeb(null)
            return
        }
        bridge.updateTelaCornerWeb(
            current.copy(alpha = (cornerWebTimer / 0.8f).coerceIn(0f, 1f))
        )
    }

    private fun clearCornerWeb() {
        if (cornerWebState == null && cornerWebTimer <= 0f) return
        cornerWebState = null
        cornerWebTimer = 0f
        bridge.updateTelaCornerWeb(null)
    }

    fun debugStartWebSequence() {
        val params = bridge.getWindowParams() ?: return
        params.x = params.x.coerceIn(minX().roundToInt(), maxX().roundToInt())
        params.y = minY().roundToInt()
        bridge.updateWindowLayout(params)
        startWebDescend(params.x.toFloat(), params.y.toFloat())
    }

    fun debugLeaveCornerWeb() {
        leaveCornerWeb(TelaWebCorner.TOP_LEFT)
    }

    private fun updateWebDescend(dt: Float) {
        updateWebPosition(dt)
        if (modeTimer >= modeDuration) {
            if (abs(toY - maxY()) <= 1f) {
                bridge.updateTelaSilk(null)
                decideNext()
                return
            }
            modeTimer = 0f
            modeDuration = 2.4f + random.nextFloat() * 1.8f
            mode = Mode.WEB_HANG
            selectClipForMode(mode)
        }
    }

    private fun updateWebHang(dt: Float) {
        updateWebPosition(dt)
        if (modeTimer >= modeDuration) {
            modeTimer = 0f
            startMode(Mode.WEB_ASCEND, 1.7f, fromX, toY, fromX, webTopY)
        }
    }

    private fun updateWebAscend(dt: Float) {
        updateWebPosition(dt)
        if (modeTimer >= modeDuration) {
            bridge.updateTelaSilk(null)
            modeTimer = 0f
            decideNext()
        }
    }

    private fun updateWebPosition(dt: Float) {
        val params = bridge.getWindowParams() ?: return
        val progress = (modeTimer / modeDuration).coerceIn(0f, 1f)
        params.y = when (mode) {
            Mode.WEB_HANG -> toY.roundToInt()
            Mode.WEB_ASCEND -> {
                val eased = GroundGait.progress(modeTimer, modeDuration)
                (fromY + (toY - fromY) * eased).roundToInt()
            }
            else -> {
                val eased = GroundGait.progress(modeTimer, modeDuration)
                (fromY + (toY - fromY) * eased).roundToInt()
            }
        }
        bridge.updateWindowLayout(params)

        selectClipForMode(mode)
        advanceClip(dt)

        val sway = sin(time * 2.2f) * 8f
        bridge.animScaleX = if (facingRight) 1f else -1f
        bridge.animScaleY = 1f
        bridge.animRotation = sin(time * 2.2f) * 3.5f
        bridge.animOffsetX = sin(time * 2.2f) * 3f
        bridge.animOffsetY = abs(sin(time * 1.8f)) * 1.5f
        bridge.updateTelaSilk(
            TelaSilkState(
                anchorX = webAnchorX,
                anchorY = webAnchorY,
                targetX = params.x + bridge.petSpriteSize / 2f + bridge.renderOffsetX,
                targetY = params.y + bridge.petSpriteSize * 0.48f + bridge.renderOffsetY,
                sway = sway,
            )
        )
    }

    private fun updateHappy(dt: Float) {
        if (modeTimer >= modeDuration) {
            modeTimer = 0f
            decideNext()
            return
        }
        selectClipForMode(Mode.HAPPY)
        advanceClip(dt)
        bridge.animScaleY = 1f
        bridge.animScaleX = if (facingRight) 1f else -1f
        bridge.animOffsetY = sin(time * 4f) * 3f
    }

    private fun updateTouch(dt: Float) {
        if (modeTimer >= modeDuration) {
            bridge.state = PetState.IDLE
            modeTimer = 0f
            animationPlayer.reset()
            activeClipId = null
            if (bridge.petStatus.mood == PetMood.HAPPY) {
                mode = Mode.HAPPY
                modeDuration = 1.2f
                selectClipForMode(mode)
            } else {
                decideNext()
            }
            return
        }
        selectClipForMode(Mode.TOUCH)
        advanceClip(dt)
    }

    private fun updateSleep(dt: Float) {
        selectClipForMode(Mode.SLEEP)
        advanceClip(dt)
        bridge.animOffsetY = if (hasRestAtlas) 0f else sin(time * 1.2f) * 1.5f
        if (modeTimer >= modeDuration) {
            modeTimer = 0f
            if (hasRestAtlas) {
                mode = Mode.WAKE
                modeDuration = com.pixelpals.app.core.motion.TelaRestPose.DURATION_SECONDS
                selectClipForMode(mode)
            } else decideNext()
        }
    }

    override fun onInteract() {
        super.onInteract()
        if (mode == Mode.WAKE) {
            pendingTouchAfterWake = true
            return
        }
        if (mode == Mode.SLEEP && hasRestAtlas) {
            pendingTouchAfterWake = true
            beginWake()
            return
        }
        beginTouch()
    }

    private fun beginWake(): Unit {
        val currentFrame: Int = bridge.currentFrame
        val restIndex: Int = com.pixelpals.app.core.motion.TelaRestPose.restFrames.indexOf(currentFrame).coerceAtLeast(0)
        mode = Mode.WAKE
        modeTimer = 0f
        modeDuration = (restIndex + 1) * com.pixelpals.app.core.motion.TelaRestPose.STEP_SECONDS
        selectClipForMode(mode)
        val index: Int = com.pixelpals.app.core.motion.TelaRestPose.wakeFrames.indexOf(currentFrame)
            .coerceAtLeast(0)
        animationPlayer.seek(index * com.pixelpals.app.core.motion.TelaRestPose.STEP_SECONDS)
        bridge.currentFrame = animationPlayer.currentFrame()
    }

    private fun beginTouch(): Unit {
        // Touch owns its pose; silk/sleep offsets must not survive after their effects end.
        bridge.animRotation = 0f
        bridge.animOffsetX = 0f
        bridge.animOffsetY = 0f
        bridge.animScaleX = if (bridge.animScaleX < 0f) -1f else 1f
        bridge.animScaleY = 1f
        bridge.updateTelaSilk(null)
        clearCornerWeb()
        cornerWebCooldown = INTERACTION_WEB_COOLDOWN_SECONDS
        mode = Mode.TOUCH
        modeDuration = 0.9f + random.nextFloat() * 0.6f
        modeTimer = 0f
        selectClipForMode(mode)
    }

    override fun updateInteracting(dt: Float) {
        // El PetView llama a esto mientras state == INTERACTING; si no avanzamos
        // el reloj, la araña se queda congelada en el frame de touch para siempre.
        time += dt
        modeTimer += dt
        if (mode == Mode.TOUCH) updateTouch(dt) else if (mode == Mode.WAKE) updateWake(dt)
    }

    override fun updateDrag(dt: Float) {
        // Al arrastrar, la araña sigue colgando de su hilo y balanceándose
        // (no se congela como en BaseBehavior).
        time += dt
        pendingTouchAfterWake = false
        bridge.updateTelaSilk(null)
        clearCornerWeb()
        cornerWebCooldown = INTERACTION_WEB_COOLDOWN_SECONDS
        selectClipForMode(Mode.HANG)
        advanceClip(dt)
        bridge.animRotation = sin(time * 2.4f) * 3f
        bridge.animOffsetX = sin(time * 2.4f) * 2f
        bridge.animOffsetY = 0f
        bridge.animScaleX = if (bridge.animScaleX < 0f) -1f else 1f
        bridge.animScaleY = 1f
    }

    override fun reset() {
        super.reset()
        pendingTouchAfterWake = false
        bridge.updateTelaSilk(null)
        clearCornerWeb()
        // Al soltar el drag, la araña reanuda su ronda por el perímetro al momento.
        modeTimer = 0f
        animationPlayer.reset()
        activeClipId = null
        decideNext()
    }

    override fun pause(): Unit {
        clearWebEffects()
        super.pause()
    }

    override fun destroy(): Unit {
        cancelRestFade()
        clearWebEffects()
        super.destroy()
    }

    private fun clearWebEffects(): Unit {
        bridge.updateTelaSilk(null)
        clearCornerWeb()
    }

    private fun syncWindowPosition() {
        val params = bridge.getWindowParams() ?: return
        params.x = params.x.coerceIn(minX().roundToInt(), maxX().roundToInt())
        params.y = params.y.coerceIn(minY().roundToInt(), maxY().roundToInt())
        bridge.updateWindowLayout(params)
    }

    /**
     * Las paredes recorren casi toda la altura útil de la pantalla. Su antigua
     * duración de 1.5–2.8 s hacía que Tela se disparase verticalmente respecto
     * al paseo por el suelo. Una duración de 3.6–5.2 s deja una velocidad de
     * trepa más deliberada y mantiene la personalidad de araña exploradora.
     */
    private fun climbDuration(): Float = CLIMB_MIN_DURATION + random.nextFloat() * CLIMB_VARIATION

    private companion object {
        const val CEILING_CONTACT_SECONDS = .25f
        const val INTERACTION_WEB_COOLDOWN_SECONDS = 2.2f
        const val CLIMB_MIN_DURATION = 3.6f
        const val CLIMB_VARIATION = 1.6f
    }
}
