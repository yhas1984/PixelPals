package com.pixelpals.app.debug

import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.PetAnimationClip
import com.pixelpals.app.core.motion.PetBounds
import com.pixelpals.app.core.motion.SeededPetRandom
import com.pixelpals.app.core.runtime.PetEnvironment
import com.pixelpals.app.core.runtime.PetEvent
import com.pixelpals.app.core.runtime.PetRuntime
import com.pixelpals.app.core.runtime.PetRuntimeOutput
import com.pixelpals.app.core.runtime.PetRuntimeStatus
import com.pixelpals.app.core.runtime.PetSurface
import com.pixelpals.app.core.runtime.PetVector
import com.pixelpals.app.core.runtime.pets.TaroRuntimeBrain
import com.pixelpals.app.core.runtime.pets.TaroRuntimeDefinition
import com.pixelpals.app.core.runtime.pets.TaroRuntimeState
import com.pixelpals.app.core.runtime.pets.YukiRuntimeBrain
import com.pixelpals.app.core.runtime.pets.YukiRuntimeDefinition
import com.pixelpals.app.core.runtime.pets.YukiRuntimeState
import com.pixelpals.app.feature.overlay.behavior.PetAtlasSpec
import com.pixelpals.app.status.PetMood

internal data class RuntimeLabConfig(
    val seed: Int = 1,
    val mood: PetMood = PetMood.HAPPY,
    val bond: Int = 0,
    val temperatureCelsius: Float = 24f,
    val surface: PetSurface = PetSurface.FLOOR,
)

internal interface PetRuntimeLabSession {
    val output: PetRuntimeOutput
    fun dispatch(event: PetEvent): PetRuntimeOutput

    companion object {
        fun create(
            petType: PetType,
            spec: PetAtlasSpec,
            config: RuntimeLabConfig,
            bounds: PetBounds,
        ): PetRuntimeLabSession? = when (petType) {
            PetType.TARO -> TaroPetRuntimeLabSession(spec, config, bounds)
            PetType.YUKI -> YukiPetRuntimeLabSession(spec, config, bounds)
            else -> null
        }
    }
}

private class TaroPetRuntimeLabSession(
    spec: PetAtlasSpec,
    config: RuntimeLabConfig,
    bounds: PetBounds,
) : PetRuntimeLabSession {
    private val runtime = PetRuntime<TaroRuntimeState>(
        definition = TaroRuntimeDefinition.value,
        brain = TaroRuntimeBrain(SeededPetRandom(config.seed)),
        clips = spec.clips.map { clip ->
            PetAnimationClip(clip.id, clip.frames, clip.loop, clip.frameDurationMs / 1000f)
        },
        initialStatus = PetRuntimeStatus(
            mood = config.mood,
            health = 90,
            energy = 78,
            hunger = 72,
            hygiene = 84,
            bond = config.bond,
        ),
        initialEnvironment = PetEnvironment(
            bounds = bounds,
            batteryTemperatureCelsius = config.temperatureCelsius,
        ),
        initialPosition = runtimeStartPosition(config.surface, bounds),
        initialSurface = config.surface,
        simulationSpeedMultiplier = TaroRuntimeDefinition.MOTION_SPEED_MULTIPLIER,
    )

    override var output: PetRuntimeOutput = runtime.dispatch(PetEvent.Resumed)
        private set

    override fun dispatch(event: PetEvent): PetRuntimeOutput {
        output = runtime.dispatch(event)
        return output
    }
}

private class YukiPetRuntimeLabSession(
    spec: PetAtlasSpec,
    config: RuntimeLabConfig,
    bounds: PetBounds,
) : PetRuntimeLabSession {
    private val runtime = PetRuntime<YukiRuntimeState>(
        definition = YukiRuntimeDefinition.value,
        brain = YukiRuntimeBrain(SeededPetRandom(config.seed)),
        clips = spec.clips.map { clip ->
            PetAnimationClip(clip.id, clip.frames, clip.loop, clip.frameDurationMs / 1000f)
        },
        initialStatus = PetRuntimeStatus(
            mood = config.mood,
            health = 90,
            energy = 78,
            hunger = 72,
            hygiene = 84,
            bond = config.bond,
        ),
        initialEnvironment = PetEnvironment(
            bounds = bounds,
            batteryTemperatureCelsius = config.temperatureCelsius,
        ),
        initialPosition = runtimeStartPosition(config.surface, bounds),
        initialSurface = config.surface,
    )

    override var output: PetRuntimeOutput = runtime.dispatch(PetEvent.Resumed)
        private set

    override fun dispatch(event: PetEvent): PetRuntimeOutput {
        output = runtime.dispatch(event)
        return output
    }
}

private fun runtimeStartPosition(surface: PetSurface, bounds: PetBounds): PetVector {
    val centerX = (bounds.left + bounds.right) / 2f
    val centerY = (bounds.top + bounds.floor) / 2f
    return when (surface) {
        PetSurface.FLOOR -> PetVector(centerX, bounds.floor.toFloat())
        PetSurface.LEFT_WALL -> PetVector(bounds.left.toFloat(), centerY)
        PetSurface.CEILING -> PetVector(centerX, bounds.top.toFloat())
        PetSurface.RIGHT_WALL -> PetVector(bounds.right.toFloat(), centerY)
        PetSurface.SILK,
        PetSurface.FREE,
        -> PetVector(centerX, centerY)
    }
}
