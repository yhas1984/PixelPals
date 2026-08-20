package com.pixelpals.app.core.runtime.pets

import com.pixelpals.app.core.motion.PetAnimationClip
import com.pixelpals.app.core.motion.PetBounds
import com.pixelpals.app.core.motion.PetRandom
import com.pixelpals.app.core.runtime.PetEnvironment
import com.pixelpals.app.core.runtime.PetEvent
import com.pixelpals.app.core.runtime.PetFacing
import com.pixelpals.app.core.runtime.PetInteractionState
import com.pixelpals.app.core.runtime.PetRuntime
import com.pixelpals.app.core.runtime.PetRuntimeStatus
import com.pixelpals.app.core.runtime.PetSurface
import com.pixelpals.app.core.runtime.PetVector
import com.pixelpals.app.status.PetMood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaroRuntimeBrainTest {
    private val bounds = PetBounds.compute(1_080, 2_400, 160, 100, 200)

    @Test
    fun idleUsesTheStaticFrontFacingClip() {
        val runtime = runtime(SequenceRandom())

        assertEquals("idle_front", runtime.dispatch(PetEvent.Resumed).clipId)
        assertEquals("idle_front", runtime.dispatch(PetEvent.Tick(0.5f)).clipId)
    }

    @Test
    fun tapRunsTheCompleteApprovedSequence() {
        val runtime = runtime(SequenceRandom())

        assertEquals("touch", runtime.dispatch(PetEvent.Tap).clipId)
        assertEquals("hide", runtime.dispatch(PetEvent.Tick(1.5f)).clipId)
        assertEquals("peek", runtime.dispatch(PetEvent.Tick(2.7f)).clipId)
        assertEquals("playful_surprise", runtime.dispatch(PetEvent.Tick(2.2f)).clipId)
        assertEquals("idle_front", runtime.dispatch(PetEvent.Tick(1.3f)).clipId)
    }

    @Test
    fun eligibleHoldWavesOnceAndReturnsToIdle() {
        val runtime = runtime(SequenceRandom(0.5f))

        assertEquals("touch", runtime.dispatch(PetEvent.HoldStarted).clipId)
        assertEquals("playful_wave", runtime.dispatch(PetEvent.HoldReleased).clipId)
        assertEquals(25f, runtime.snapshot().brainState.playfulCooldownSeconds, 0.001f)
        assertEquals("idle_front", runtime.dispatch(PetEvent.Tick(1.3f)).clipId)
    }

    @Test
    fun excitedMoodIsEligibleForAPlayfulWave() {
        val runtime = runtime(
            SequenceRandom(),
            initialStatus = status(mood = PetMood.EXCITED),
        )

        runtime.dispatch(PetEvent.HoldStarted)

        assertEquals("playful_wave", runtime.dispatch(PetEvent.HoldReleased).clipId)
    }

    @Test
    fun autonomousDelightIsOccasionalAndReturnsToIdle() {
        val runtime = runtime(SequenceRandom(0.86f, 0.5f))

        assertEquals("playful_delight", runtime.dispatch(PetEvent.Tick(3.1f)).clipId)
        assertEquals(25f, runtime.snapshot().brainState.playfulCooldownSeconds, 0.001f)
        assertEquals("idle_front", runtime.dispatch(PetEvent.Tick(1.3f)).clipId)
    }

    @Test
    fun negativeMoodLowEnergyAndCooldownBlockPlayfulGestures() {
        val negativeMood = runtime(
            SequenceRandom(),
            initialStatus = status(mood = PetMood.HUNGRY, energy = 80),
        )
        negativeMood.dispatch(PetEvent.HoldStarted)
        assertEquals("idle_front", negativeMood.dispatch(PetEvent.HoldReleased).clipId)

        val lowEnergy = runtime(SequenceRandom(), initialStatus = status(energy = 39))
        lowEnergy.dispatch(PetEvent.HoldStarted)
        assertEquals("idle_front", lowEnergy.dispatch(PetEvent.HoldReleased).clipId)

        val lowEnergyTap = runtime(SequenceRandom(), initialStatus = status(energy = 39))
        lowEnergyTap.dispatch(PetEvent.Tap)
        lowEnergyTap.dispatch(PetEvent.Tick(1.5f))
        lowEnergyTap.dispatch(PetEvent.Tick(2.7f))
        assertEquals("idle_front", lowEnergyTap.dispatch(PetEvent.Tick(2.2f)).clipId)

        val cooldown = runtime(SequenceRandom(0.5f))
        cooldown.dispatch(PetEvent.HoldStarted)
        cooldown.dispatch(PetEvent.HoldReleased)
        cooldown.dispatch(PetEvent.Tick(1.3f))
        cooldown.dispatch(PetEvent.HoldStarted)
        assertEquals("idle_front", cooldown.dispatch(PetEvent.HoldReleased).clipId)
    }

    @Test
    fun lowAutonomousEnergyAndCooldownBlockDelight() {
        val lowEnergy = runtime(
            SequenceRandom(0.86f),
            initialStatus = status(energy = 54),
        )
        assertTrue(lowEnergy.dispatch(PetEvent.Tick(3.1f)).clipId != "playful_delight")

        val negativeMood = runtime(
            SequenceRandom(0.86f),
            initialStatus = status(mood = PetMood.HUNGRY),
        )
        assertTrue(negativeMood.dispatch(PetEvent.Tick(3.1f)).clipId != "playful_delight")

        val cooldown = runtime(
            SequenceRandom(0.5f, 0f, 0f, 0.86f, 0f, 0.86f, 0f, 0.86f),
        )
        cooldown.dispatch(PetEvent.HoldStarted)
        cooldown.dispatch(PetEvent.HoldReleased)
        cooldown.dispatch(PetEvent.Tick(1.3f))

        val idleCycleClips = buildList {
            repeat(3) {
                cooldown.dispatch(PetEvent.Cancelled)
                add(cooldown.dispatch(PetEvent.Tick(3.1f)).clipId)
            }
        }

        assertTrue(idleCycleClips.none { it.startsWith("playful_") })
        assertTrue(cooldown.snapshot().brainState.playfulCooldownSeconds > 0f)
    }

    @Test
    fun cooldownDecreasesOnEveryProcessedTick() {
        val runtime = runtime(SequenceRandom(0.5f))
        runtime.dispatch(PetEvent.HoldStarted)
        runtime.dispatch(PetEvent.HoldReleased)

        runtime.dispatch(PetEvent.Tick(0.5f))

        assertEquals(24.5f, runtime.snapshot().brainState.playfulCooldownSeconds, 0.001f)
    }

    @Test
    fun playfulCooldownIsRandomizedBetweenTwentyAndThirtySeconds() {
        val minimum = wavingRuntime(SequenceRandom(0f))
        val maximum = wavingRuntime(SequenceRandom(1f))

        assertEquals(20f, minimum.snapshot().brainState.playfulCooldownSeconds, 0.001f)
        assertEquals(30f, maximum.snapshot().brainState.playfulCooldownSeconds, 0.001f)
    }

    @Test
    fun tapDragAndHoldInterruptAPlayfulGesture() {
        val tapRuntime = wavingRuntime()
        assertEquals("touch", tapRuntime.dispatch(PetEvent.Tap).clipId)

        val holdRuntime = wavingRuntime()
        assertEquals("touch", holdRuntime.dispatch(PetEvent.HoldStarted).clipId)

        val dragRuntime = wavingRuntime()
        assertEquals(
            "hide",
            dragRuntime.dispatch(
                PetEvent.DragStarted(
                    pointer = PetVector(230f, bounds.floor + 30f),
                    grabOffset = PetVector(30f, 30f),
                ),
            ).clipId,
        )
    }

    @Test
    fun playfulClipsAreOneShotAndEndAtTheFrontIdleFrame() {
        val playful = clips().filter { it.id.startsWith("playful_") }

        assertEquals(
            setOf("playful_wave", "playful_delight", "playful_surprise"),
            playful.map { it.id }.toSet(),
        )
        playful.forEach { clip ->
            assertTrue(!clip.loop)
            assertEquals(24, clip.frames.last())
            assertEquals(0.3f, clip.frameDurationSeconds, 0.001f)
        }
    }

    @Test
    fun motionMultiplierScalesAllClipTimersWithoutChangingManifestDurations() {
        val runtime = PetRuntime(
            definition = TaroRuntimeDefinition.value,
            brain = TaroRuntimeBrain(SequenceRandom()),
            clips = clips(),
            initialStatus = PetRuntimeStatus(PetMood.HAPPY, 90, 80, 75, 85, 40),
            initialEnvironment = PetEnvironment(bounds),
            initialPosition = PetVector(200f, bounds.floor.toFloat()),
            simulationSpeedMultiplier = 2f,
        )

        assertEquals("touch", runtime.dispatch(PetEvent.Tap).clipId)
        // touch is 4 x 0.22s in the manifest; at 2x it completes in 0.44s.
        assertEquals("hide", runtime.dispatch(PetEvent.Tick(0.5f)).clipId)
        // hide is 4 x 0.30s in the manifest; at 2x it completes in 0.60s.
        assertEquals("peek", runtime.dispatch(PetEvent.Tick(0.75f)).clipId)
        assertEquals(0.22f, clips().first { it.id == "touch" }.frameDurationSeconds, 0.001f)

        val playfulRuntime = PetRuntime(
            definition = TaroRuntimeDefinition.value,
            brain = TaroRuntimeBrain(SequenceRandom()),
            clips = clips(),
            initialStatus = status(),
            initialEnvironment = PetEnvironment(bounds),
            initialPosition = PetVector(200f, bounds.floor.toFloat()),
            simulationSpeedMultiplier = 2f,
        )
        playfulRuntime.dispatch(PetEvent.HoldStarted)
        assertEquals("playful_wave", playfulRuntime.dispatch(PetEvent.HoldReleased).clipId)
        assertEquals("idle_front", playfulRuntime.dispatch(PetEvent.Tick(0.61f)).clipId)
    }

    @Test
    fun facingChangesOnlyAfterTheTurnClipEnds() {
        val runtime = runtime(SequenceRandom(0.1f, 0f), initialX = 900f)

        val turn = runtime.dispatch(PetEvent.Tick(3.1f))
        assertEquals("turn", turn.clipId)
        assertEquals(PetFacing.RIGHT, turn.facing)

        val walk = runtime.dispatch(PetEvent.Tick(1.7f))
        assertEquals("walk", walk.clipId)
        assertEquals(PetFacing.LEFT, walk.facing)
        assertEquals(bounds.floor.toFloat(), walk.position.y, 0.01f)
    }

    @Test
    fun walkUsesRealClipTimingWithoutLeavingGround() {
        val runtime = runtime(SequenceRandom(0.1f, 0.99f), initialX = 100f)
        runtime.dispatch(PetEvent.Tick(3.1f))

        var previousX = runtime.snapshot().position.x
        repeat(300) {
            val output = runtime.dispatch(PetEvent.Tick(1f / 60f))
            assertEquals(bounds.floor.toFloat(), output.position.y, 0.01f)
            assertTrue(output.position.x in bounds.left.toFloat()..bounds.right.toFloat())
            assertTrue(
                kotlin.math.abs(output.position.x - previousX) <=
                    TaroRuntimeDefinition.WALK_SPEED_PIXELS_PER_SECOND / 60f + 0.05f,
            )
            previousX = output.position.x
        }
        assertEquals(
            TaroRuntimeDefinition.WALK_SPEED_PIXELS_PER_SECOND * (8f * 0.42f),
            TaroRuntimeDefinition.value.locomotion.cycleDisplacementPixelsByClip.getValue("walk"),
            0.01f,
        )
    }

    @Test
    fun dragAndFlingRecoverThroughPeekInsideBounds() {
        val runtime = runtime(SequenceRandom())
        runtime.dispatch(
            PetEvent.DragStarted(
                pointer = PetVector(230f, bounds.floor + 30f),
                grabOffset = PetVector(30f, 30f),
            )
        )
        runtime.dispatch(PetEvent.Flung(PetVector(-9_000f, -9_000f)))
        repeat(2_000) {
            if (runtime.snapshot().interaction != PetInteractionState.NONE) {
                runtime.dispatch(PetEvent.Tick(1f / 60f))
            }
        }

        assertEquals(PetInteractionState.NONE, runtime.snapshot().interaction)
        assertEquals("peek", runtime.snapshot().clipId)
        assertEquals(PetSurface.FLOOR, runtime.snapshot().surface)
        assertTrue(runtime.snapshot().position.x in bounds.left.toFloat()..bounds.right.toFloat())
        assertEquals(bounds.floor.toFloat(), runtime.snapshot().position.y, 0.5f)
    }

    @Test
    fun recoveryPeekReturnsDirectlyToIdleWithoutSurprise() {
        val runtime = runtime(SequenceRandom())

        assertEquals("peek", runtime.dispatch(PetEvent.RecoveryCompleted).clipId)

        assertEquals("idle_front", runtime.dispatch(PetEvent.Tick(2.2f)).clipId)
    }

    @Test
    fun everyApprovedClipIsReachable() {
        val reached = linkedSetOf("idle", "idle_front")
        val tapRuntime = runtime(SequenceRandom())
        reached += tapRuntime.dispatch(PetEvent.Tap).clipId
        reached += tapRuntime.dispatch(PetEvent.Tick(1.5f)).clipId
        reached += tapRuntime.dispatch(PetEvent.Tick(2.7f)).clipId
        reached += tapRuntime.dispatch(PetEvent.Tick(2.2f)).clipId

        val holdRuntime = runtime(SequenceRandom())
        holdRuntime.dispatch(PetEvent.HoldStarted)
        reached += holdRuntime.dispatch(PetEvent.HoldReleased).clipId

        reached += runtime(SequenceRandom(0.1f, 0f), initialX = 900f)
            .dispatch(PetEvent.Tick(3.1f)).clipId
        reached += runtime(SequenceRandom(0.1f, 0.99f), initialX = 100f)
            .dispatch(PetEvent.Tick(3.1f)).clipId
        reached += runtime(SequenceRandom(0.7f)).dispatch(PetEvent.Tick(3.1f)).clipId
        reached += runtime(SequenceRandom(0.86f)).dispatch(PetEvent.Tick(3.1f)).clipId
        reached += runtime(SequenceRandom(0.99f)).dispatch(PetEvent.Tick(3.1f)).clipId

        assertEquals(
            TaroRuntimeDefinition.value.requiredClips - setOf("front_social"),
            reached,
        )
        assertTrue(clips().any { it.id == "front_social" })
    }

    private fun runtime(
        random: PetRandom,
        initialX: Float = 200f,
        initialStatus: PetRuntimeStatus = status(),
    ): PetRuntime<TaroRuntimeState> = PetRuntime(
        definition = TaroRuntimeDefinition.value,
        brain = TaroRuntimeBrain(random),
        clips = clips(),
        initialStatus = initialStatus,
        initialEnvironment = PetEnvironment(bounds),
        initialPosition = PetVector(initialX, bounds.floor.toFloat()),
    )

    private fun clips(): List<PetAnimationClip> = listOf(
        clip("idle", 4, true, 0.9f),
        clip("idle_front", 1, true, 0.9f),
        clip("walk", 8, true, 0.42f),
        clip("turn", 4, false, 0.3f),
        clip("hide", 4, false, 0.3f),
        clip("peek", 4, false, 0.28f),
        PetAnimationClip("front_social", listOf(24, 25, 26, 27), false, 0.35f),
        PetAnimationClip("playful_wave", listOf(24, 25, 25, 24), false, 0.3f),
        PetAnimationClip("playful_delight", listOf(24, 26, 26, 24), false, 0.3f),
        PetAnimationClip("playful_surprise", listOf(24, 27, 27, 24), false, 0.3f),
        clip("touch", 4, false, 0.22f),
        clip("sleep", 4, true, 1.6f),
        clip("curiosity", 4, false, 0.36f),
    )

    private fun clip(id: String, count: Int, loop: Boolean, duration: Float): PetAnimationClip =
        PetAnimationClip(id, List(count) { it }, loop, duration)

    private fun status(
        mood: PetMood = PetMood.HAPPY,
        energy: Int = 80,
    ): PetRuntimeStatus = PetRuntimeStatus(mood, 90, energy, 75, 85, 40)

    private fun wavingRuntime(
        random: PetRandom = SequenceRandom(),
    ): PetRuntime<TaroRuntimeState> = runtime(random).also { runtime ->
        runtime.dispatch(PetEvent.HoldStarted)
        runtime.dispatch(PetEvent.HoldReleased)
    }
}

private class SequenceRandom(vararg values: Float) : PetRandom {
    private val remaining = values.toMutableList()

    override fun nextFloat(): Float = remaining.removeFirstOrNull() ?: 0f
    override fun nextInt(from: Int, until: Int): Int = from
}
