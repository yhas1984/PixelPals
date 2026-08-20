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
        assertEquals("front_social", runtime.dispatch(PetEvent.Tick(2.2f)).clipId)
        assertEquals("idle_front", runtime.dispatch(PetEvent.Tick(2.7f)).clipId)
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
    fun everyApprovedClipIsReachable() {
        val reached = linkedSetOf("idle", "idle_front")
        val tapRuntime = runtime(SequenceRandom())
        reached += tapRuntime.dispatch(PetEvent.Tap).clipId
        reached += tapRuntime.dispatch(PetEvent.Tick(1.5f)).clipId
        reached += tapRuntime.dispatch(PetEvent.Tick(2.7f)).clipId
        reached += tapRuntime.dispatch(PetEvent.Tick(2.2f)).clipId

        reached += runtime(SequenceRandom(0.1f, 0f), initialX = 900f)
            .dispatch(PetEvent.Tick(3.1f)).clipId
        reached += runtime(SequenceRandom(0.1f, 0.99f), initialX = 100f)
            .dispatch(PetEvent.Tick(3.1f)).clipId
        reached += runtime(SequenceRandom(0.55f)).dispatch(PetEvent.Tick(3.1f)).clipId
        reached += runtime(SequenceRandom(0.8f)).dispatch(PetEvent.Tick(3.1f)).clipId
        reached += runtime(SequenceRandom(0.99f)).dispatch(PetEvent.Tick(3.1f)).clipId

        assertEquals(TaroRuntimeDefinition.value.requiredClips, reached)
    }

    private fun runtime(random: PetRandom, initialX: Float = 200f): PetRuntime<TaroRuntimeState> = PetRuntime(
        definition = TaroRuntimeDefinition.value,
        brain = TaroRuntimeBrain(random),
        clips = clips(),
        initialStatus = PetRuntimeStatus(PetMood.HAPPY, 90, 80, 75, 85, 40),
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
        clip("front_social", 4, false, 0.35f),
        clip("touch", 4, false, 0.22f),
        clip("sleep", 4, true, 1.6f),
        clip("curiosity", 4, false, 0.36f),
    )

    private fun clip(id: String, count: Int, loop: Boolean, duration: Float): PetAnimationClip =
        PetAnimationClip(id, List(count) { it }, loop, duration)
}

private class SequenceRandom(vararg values: Float) : PetRandom {
    private val remaining = values.toMutableList()

    override fun nextFloat(): Float = remaining.removeFirstOrNull() ?: 0f
    override fun nextInt(from: Int, until: Int): Int = from
}
