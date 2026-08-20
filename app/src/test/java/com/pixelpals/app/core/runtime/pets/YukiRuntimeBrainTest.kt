package com.pixelpals.app.core.runtime.pets

import com.pixelpals.app.core.motion.PetAnimationClip
import com.pixelpals.app.core.motion.PetBounds
import com.pixelpals.app.core.motion.PetRandom
import com.pixelpals.app.core.runtime.PetEnvironment
import com.pixelpals.app.core.runtime.PetEvent
import com.pixelpals.app.core.runtime.PetInteractionState
import com.pixelpals.app.core.runtime.PetRuntime
import com.pixelpals.app.core.runtime.PetRuntimeStatus
import com.pixelpals.app.core.runtime.PetSurface
import com.pixelpals.app.core.runtime.PetVector
import com.pixelpals.app.status.PetMood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YukiRuntimeBrainTest {
    private val bounds = PetBounds.compute(1_080, 2_400, 160, 100, 200)

    @Test
    fun temperatureUsesHysteresisBeforeLeavingMelt() {
        val runtime = runtime(YukiSequenceRandom(), temperatureCelsius = 24f)

        assertEquals("melt", runtime.dispatch(PetEvent.EnvironmentChanged(environment(41f))).clipId)
        assertEquals("melt", runtime.dispatch(PetEvent.EnvironmentChanged(environment(39f))).clipId)
        assertEquals("idle", runtime.dispatch(PetEvent.EnvironmentChanged(environment(38f))).clipId)
    }

    @Test
    fun tapRunsCuriosityAndReturnsToIdle() {
        val runtime = runtime(YukiSequenceRandom())

        val curiosity = runtime.dispatch(PetEvent.Tap)
        assertEquals("happy", curiosity.clipId)
        assertEquals(YukiRuntimeMode.CURIOSITY, runtime.snapshot().brainState.mode)

        assertEquals("idle", runtime.dispatch(PetEvent.Tick(1.7f)).clipId)
    }

    @Test
    fun walkStaysGroundedBoundedAndWithinDeclaredSpeed() {
        val runtime = runtime(YukiSequenceRandom(0.1f, 0.9f), initialX = 100f)
        assertEquals("walk", runtime.dispatch(PetEvent.Tick(3.1f)).clipId)

        var previousX = runtime.snapshot().position.x
        repeat(300) {
            val output = runtime.dispatch(PetEvent.Tick(1f / 60f))
            assertEquals(bounds.floor.toFloat(), output.position.y, 0.01f)
            assertTrue(output.position.x in bounds.left.toFloat()..bounds.right.toFloat())
            assertTrue(
                kotlin.math.abs(output.position.x - previousX) <=
                    YukiRuntimeDefinition.WALK_SPEED_PIXELS_PER_SECOND / 60f + 0.05f,
            )
            previousX = output.position.x
        }
    }

    @Test
    fun flingUsesSoftGroundRecoveryAndExitsInteraction() {
        val runtime = runtime(YukiSequenceRandom())
        runtime.dispatch(
            PetEvent.DragStarted(
                pointer = PetVector(230f, bounds.floor + 30f),
                grabOffset = PetVector(30f, 30f),
            )
        )
        assertEquals("jump", runtime.dispatch(PetEvent.Flung(PetVector(-9_000f, -9_000f))).clipId)

        repeat(2_000) {
            if (runtime.snapshot().interaction != PetInteractionState.NONE) {
                runtime.dispatch(PetEvent.Tick(1f / 60f))
            }
        }

        assertEquals(PetInteractionState.NONE, runtime.snapshot().interaction)
        assertEquals("happy", runtime.snapshot().clipId)
        assertEquals(PetSurface.FLOOR, runtime.snapshot().surface)
        assertTrue(runtime.snapshot().position.x in bounds.left.toFloat()..bounds.right.toFloat())
        assertEquals(bounds.floor.toFloat(), runtime.snapshot().position.y, 0.5f)
        assertEquals("idle", runtime.dispatch(PetEvent.Tick(1.2f)).clipId)
    }

    @Test
    fun everyManifestClipIsReachable() {
        val reached = linkedSetOf("idle")
        reached += runtime(YukiSequenceRandom()).dispatch(PetEvent.Tap).clipId
        reached += runtime(YukiSequenceRandom()).dispatch(PetEvent.HoldStarted).clipId

        val flingRuntime = runtime(YukiSequenceRandom())
        flingRuntime.dispatch(
            PetEvent.DragStarted(
                pointer = PetVector(230f, bounds.floor + 30f),
                grabOffset = PetVector(30f, 30f),
            )
        )
        reached += flingRuntime.dispatch(PetEvent.Flung(PetVector(500f, -800f))).clipId
        reached += runtime(YukiSequenceRandom(), temperatureCelsius = 41f).snapshot().clipId
        reached += runtime(YukiSequenceRandom(0.1f, 0.9f)).dispatch(PetEvent.Tick(3.1f)).clipId
        reached += runtime(YukiSequenceRandom(0.75f)).dispatch(PetEvent.Tick(3.1f)).clipId
        reached += runtime(YukiSequenceRandom(0.95f, 0.5f)).dispatch(PetEvent.Tick(3.1f)).clipId

        assertEquals(YukiRuntimeDefinition.value.requiredClips, reached)
    }

    private fun runtime(
        random: PetRandom,
        temperatureCelsius: Float = 24f,
        initialX: Float = 200f,
    ): PetRuntime<YukiRuntimeState> = PetRuntime(
        definition = YukiRuntimeDefinition.value,
        brain = YukiRuntimeBrain(random),
        clips = clips(),
        initialStatus = PetRuntimeStatus(PetMood.HAPPY, 90, 80, 75, 85, 40),
        initialEnvironment = environment(temperatureCelsius),
        initialPosition = PetVector(initialX, bounds.floor.toFloat()),
    )

    private fun environment(temperatureCelsius: Float): PetEnvironment = PetEnvironment(
        bounds = bounds,
        batteryTemperatureCelsius = temperatureCelsius,
    )

    private fun clips(): List<PetAnimationClip> = listOf(
        clip("idle", 3, true, 0.38f),
        clip("blink", 1, false, 0.22f),
        clip("walk", 2, true, 0.22f),
        clip("jump", 2, false, 0.18f),
        clip("happy", 4, true, 0.23f),
        clip("melt", 3, false, 0.32f),
        clip("touch", 3, false, 0.30f),
        clip("sleep", 1, false, 0.50f),
    )

    private fun clip(id: String, count: Int, loop: Boolean, duration: Float): PetAnimationClip =
        PetAnimationClip(id, List(count) { it }, loop, duration)
}

private class YukiSequenceRandom(vararg values: Float) : PetRandom {
    private val remaining = values.toMutableList()

    override fun nextFloat(): Float = remaining.removeFirstOrNull() ?: 0f
    override fun nextInt(from: Int, until: Int): Int = from
}
