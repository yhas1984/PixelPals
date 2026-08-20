package com.pixelpals.app.feature.overlay.behavior

import com.pixelpals.app.core.domain.PetState
import com.pixelpals.app.core.motion.PetAnimationClip
import com.pixelpals.app.core.motion.PetRandom
import com.pixelpals.app.core.runtime.PetBrain
import com.pixelpals.app.core.runtime.PetBrainState
import com.pixelpals.app.core.runtime.PetCompletedInteraction
import com.pixelpals.app.core.runtime.PetDefinition
import com.pixelpals.app.core.runtime.PetEffectCommand
import com.pixelpals.app.core.runtime.PetEnvironment
import com.pixelpals.app.core.runtime.PetEvent
import com.pixelpals.app.core.runtime.PetInteractionState
import com.pixelpals.app.core.runtime.PetIntent
import com.pixelpals.app.core.runtime.PetRuntime
import com.pixelpals.app.core.runtime.PetRuntimeOutput
import com.pixelpals.app.core.runtime.PetRuntimeStatus
import com.pixelpals.app.core.runtime.PetSurfaceResolver
import com.pixelpals.app.core.runtime.PetVector
import kotlin.math.roundToInt

/** Android adapter used only by pets selected for the parallel runtime. */
open class RuntimePetBehavior<S : PetBrainState>(
    bridge: PetViewBridge,
    override val random: PetRandom,
    private val definition: PetDefinition,
    private val brain: PetBrain<S>,
    private val simulationSpeedMultiplier: Float = 1f,
    private val effectSink: (PetEffectCommand) -> Unit = {},
) : BaseBehavior(bridge, random) {
    final override val resourceIds: List<Int> = emptyList()
    final override val usesRuntimeInput: Boolean = true

    private var runtime: PetRuntime<S>? = null
    private var batteryPercent: Int = 100
    private var isCharging: Boolean = false
    private var batteryTemperatureCelsius: Float? = null
    private var isKeyboardVisible: Boolean = false
    private var isAirplaneModeEnabled: Boolean = false
    private var lastStatus: PetRuntimeStatus = bridge.petStatus.toRuntimeStatus()
    private var lastEnvironment: PetEnvironment = environment()

    init {
        loadSpriteSheetAssetAsync(definition.atlasSpecPath) { spec ->
            val params = bridge.getWindowParams()
            val requestedPosition = PetVector(
                params?.x?.toFloat() ?: bridge.windowX.toFloat(),
                params?.y?.toFloat() ?: bridge.windowY.toFloat(),
            )
            val attachment = PetSurfaceResolver.attach(
                position = requestedPosition,
                bounds = bridge.bounds,
                profile = definition.locomotion.physicsProfile,
            )
            runtime = PetRuntime(
                definition = definition,
                brain = brain,
                clips = spec.clips.map { clip ->
                    PetAnimationClip(
                        id = clip.id,
                        frames = clip.frames,
                        loop = clip.loop,
                        frameDurationSeconds = clip.frameDurationMs / 1000f,
                    )
                },
                initialStatus = lastStatus,
                initialEnvironment = lastEnvironment,
                initialPosition = attachment.position,
                simulationSpeedMultiplier = simulationSpeedMultiplier,
            ).also { created ->
                applyOutput(created.dispatch(PetEvent.Resumed))
            }
        }
    }

    final override fun updateIdle(dt: Float): Unit = tick(dt)
    final override fun updateDrag(dt: Float): Unit = tick(dt)
    final override fun updateFalling(dt: Float): Unit = tick(dt)
    final override fun updateJumping(dt: Float): Unit = tick(dt)
    final override fun updateAutonomous(dt: Float): Unit = tick(dt)
    final override fun updateInteracting(dt: Float): Unit = tick(dt)

    final override fun onInteract() {
        dispatch(PetEvent.Tap)
    }

    final override fun onHold() {
        dispatch(PetEvent.HoldStarted)
    }

    final override fun onHoldReleased() {
        dispatch(PetEvent.HoldReleased)
    }

    final override fun onDragStart(
        pointerX: Float,
        pointerY: Float,
        grabOffsetX: Float,
        grabOffsetY: Float,
    ) {
        dispatch(
            PetEvent.DragStarted(
                pointer = PetVector(pointerX, pointerY),
                grabOffset = PetVector(grabOffsetX, grabOffsetY),
            )
        )
    }

    final override fun onDragMove(pointerX: Float, pointerY: Float) {
        dispatch(PetEvent.DragMoved(PetVector(pointerX, pointerY)))
    }

    final override fun onRelease(velocityX: Float, velocityY: Float) {
        dispatch(PetEvent.Released(PetVector(velocityX, velocityY)))
    }

    final override fun onFling(velocityX: Float, velocityY: Float) {
        dispatch(PetEvent.Flung(PetVector(velocityX, velocityY)))
    }

    final override fun onGestureCancelled() {
        dispatch(PetEvent.Cancelled)
    }

    final override fun onBatteryStatusChanged(percent: Int, isCharging: Boolean) {
        batteryPercent = percent.coerceIn(0, 100)
        this.isCharging = isCharging
        publishEnvironment()
    }

    final override fun onKeyboardVisibilityChanged(visible: Boolean, height: Int) {
        isKeyboardVisible = visible
        publishEnvironment()
    }

    final override fun onAirplaneModeChanged(isAirplane: Boolean) {
        isAirplaneModeEnabled = isAirplane
        publishEnvironment()
    }

    final override fun onBatteryTemperatureChanged(temperatureCelsius: Float?) {
        batteryTemperatureCelsius = temperatureCelsius?.takeIf(Float::isFinite)
        publishEnvironment()
    }

    final override fun reset() {
        super.reset()
        dispatch(PetEvent.Cancelled)
    }

    final override fun pause() {
        dispatch(PetEvent.Paused)
    }

    final override fun resume() {
        dispatch(PetEvent.Resumed)
    }

    final override fun destroy() {
        runtime?.dispatch(PetEvent.Destroyed)?.effects?.forEach(effectSink)
        runtime = null
        super.destroy()
    }

    protected fun runtimeSnapshot() = runtime?.snapshot()

    private fun tick(dt: Float) {
        val currentStatus = bridge.petStatus.toRuntimeStatus()
        if (currentStatus != lastStatus) {
            lastStatus = currentStatus
            dispatch(PetEvent.StatusChanged(currentStatus))
        }
        val currentEnvironment = environment()
        if (currentEnvironment != lastEnvironment) {
            lastEnvironment = currentEnvironment
            dispatch(PetEvent.EnvironmentChanged(currentEnvironment))
        }
        dispatch(PetEvent.Tick(dt.coerceIn(0f, MAXIMUM_TICK_SECONDS)))
    }

    private fun publishEnvironment() {
        val current = environment()
        if (current == lastEnvironment) return
        lastEnvironment = current
        dispatch(PetEvent.EnvironmentChanged(current))
    }

    private fun dispatch(event: PetEvent) {
        runtime?.dispatch(event)?.let(::applyOutput)
    }

    private fun applyOutput(output: PetRuntimeOutput) {
        bridge.currentFrame = output.frame
        bridge.animScaleX = output.facing.scaleX * output.transform.scaleX
        bridge.animScaleY = output.transform.scaleY
        bridge.animOffsetX = output.transform.offsetX
        bridge.animOffsetY = output.transform.offsetY
        bridge.animRotation = output.transform.rotationDegrees
        bridge.animAlpha = output.transform.alpha
        val params = bridge.getWindowParams()
        if (params != null) {
            val targetX = output.position.x.roundToInt()
            val targetY = output.position.y.roundToInt()
            if (params.x != targetX || params.y != targetY) {
                params.x = targetX
                params.y = targetY
                bridge.updateWindowLayout(params)
            }
        }
        output.effects.forEach(effectSink)
        output.bubble?.let(bridge::showBubble)
        output.hapticDurationMs?.let(bridge::playHaptic)
        when (output.completedInteraction) {
            PetCompletedInteraction.TAP,
            PetCompletedInteraction.HOLD,
            -> bridge.trackInteraction()
            null -> Unit
        }
        val interaction = runtime?.snapshot()?.interaction
        bridge.state = when {
            interaction == PetInteractionState.DRAGGING -> PetState.DRAGGING
            interaction == PetInteractionState.RECOVERING -> PetState.INTERACTING
            output.intent in INTERACTING_INTENTS -> PetState.INTERACTING
            else -> PetState.IDLE
        }
        bridge.invalidate()
    }

    private fun environment(): PetEnvironment = PetEnvironment(
        bounds = bridge.bounds,
        batteryPercent = batteryPercent,
        isCharging = isCharging,
        batteryTemperatureCelsius = batteryTemperatureCelsius,
        isKeyboardVisible = isKeyboardVisible,
        isAirplaneModeEnabled = isAirplaneModeEnabled,
    )

    private fun com.pixelpals.app.status.PetStatusSnapshot.toRuntimeStatus(): PetRuntimeStatus = PetRuntimeStatus(
        mood = mood,
        health = health.coerceIn(0, 100),
        energy = energy.coerceIn(0, 100),
        hunger = hunger.coerceIn(0, 100),
        hygiene = hygiene.coerceIn(0, 100),
        bond = bond.coerceIn(0, 100),
    )

    private companion object {
        const val MAXIMUM_TICK_SECONDS: Float = 1f / 15f
        val INTERACTING_INTENTS: Set<PetIntent> = setOf(
            PetIntent.TOUCH,
            PetIntent.HOLD,
            PetIntent.SOCIAL,
            PetIntent.HIDE,
            PetIntent.PEEK,
            PetIntent.AIRBORNE,
            PetIntent.RECOVER,
        )
    }
}
