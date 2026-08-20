package com.pixelpals.app.core.runtime

import com.pixelpals.app.core.motion.PetAnimationClip
import com.pixelpals.app.core.motion.PetBounds
import com.pixelpals.app.core.motion.PhysicsProfile
import com.pixelpals.app.status.PetMood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PetRuntimeTest {
    private val bounds: PetBounds = PetBounds.compute(
        screenWidth = 1_080,
        screenHeight = 2_400,
        petSpriteSize = 160,
        topSystemInsetPx = 100,
        bottomSystemInsetPx = 200,
    )
    private val environment = PetEnvironment(bounds = bounds)
    private val status = PetRuntimeStatus(
        mood = PetMood.HAPPY,
        health = 90,
        energy = 80,
        hunger = 70,
        hygiene = 85,
        bond = 20,
    )

    @Test
    fun definitionRejectsMissingRequiredClips() {
        val failure = runCatching {
            PetRuntime(
                definition = definition(requiredClips = setOf("idle", "missing")),
                brain = TestBrain(),
                clips = clips(),
                initialStatus = status,
                initialEnvironment = environment,
                initialPosition = PetVector(200f, bounds.floor.toFloat()),
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun dragKeepsTheGrabPointAndDoesNotJump() {
        val runtime = runtime()
        val initial = runtime.snapshot().position
        val grabOffset = PetVector(30f, 40f)

        val started = runtime.dispatch(
            PetEvent.DragStarted(
                pointer = PetVector(initial.x + grabOffset.x, initial.y + grabOffset.y),
                grabOffset = grabOffset,
            )
        )
        assertEquals(initial, started.position)
        assertEquals(PetInteractionState.DRAGGING, runtime.snapshot().interaction)

        val moved = runtime.dispatch(PetEvent.DragMoved(PetVector(430f, 740f)))
        assertEquals(PetVector(400f, 700f), moved.position)
        assertEquals(4f, moved.transform.rotationDegrees, 0.01f)

        val ticked = runtime.dispatch(PetEvent.Tick(1f / 60f))
        assertEquals(PetVector(400f, 700f), ticked.position)
        assertEquals(4f, ticked.transform.rotationDegrees, 0.01f)
    }

    @Test
    fun flingIsLimitedAndAlwaysRecovers() {
        val runtime = runtime()
        runtime.dispatch(
            PetEvent.DragStarted(
                pointer = PetVector(220f, bounds.floor + 20f),
                grabOffset = PetVector(20f, 20f),
            )
        )
        runtime.dispatch(PetEvent.Flung(PetVector(5_000f, -5_000f)))

        assertEquals(300f, runtime.snapshot().velocity.x, 0.01f)
        assertEquals(-300f, runtime.snapshot().velocity.y, 0.01f)
        repeat(2_000) {
            if (runtime.snapshot().interaction == PetInteractionState.NONE) return@repeat
            runtime.dispatch(PetEvent.Tick(1f / 60f))
        }

        val settled = runtime.snapshot()
        assertEquals(PetInteractionState.NONE, settled.interaction)
        assertEquals(PetIntent.IDLE, settled.intent)
        assertEquals(PetSurface.FLOOR, settled.surface)
        assertEquals(bounds.floor.toFloat(), settled.position.y, 0.5f)
        assertTrue(settled.position.x in bounds.left.toFloat()..bounds.right.toFloat())
    }

    @Test
    fun tapAndHoldCompleteExactlyOnce() {
        val runtime = runtime()

        assertEquals(PetCompletedInteraction.TAP, runtime.dispatch(PetEvent.Tap).completedInteraction)
        assertNull(runtime.dispatch(PetEvent.Tick(0.1f)).completedInteraction)

        assertNull(runtime.dispatch(PetEvent.HoldStarted).completedInteraction)
        assertEquals(PetCompletedInteraction.HOLD, runtime.dispatch(PetEvent.HoldReleased).completedInteraction)
        assertNull(runtime.dispatch(PetEvent.HoldReleased).completedInteraction)
    }

    @Test
    fun lifecycleClearsMotionAndIgnoresGesturesWhilePaused() {
        val runtime = runtime()
        runtime.dispatch(
            PetEvent.DragStarted(
                pointer = PetVector(220f, bounds.floor + 20f),
                grabOffset = PetVector(20f, 20f),
            )
        )

        val paused = runtime.dispatch(PetEvent.Paused)
        assertEquals(PetLifecycleState.PAUSED, runtime.snapshot().lifecycle)
        assertEquals(PetInteractionState.NONE, runtime.snapshot().interaction)
        assertEquals(listOf(PetEffectCommand.ClearAll), paused.effects)

        runtime.dispatch(PetEvent.DragMoved(PetVector(800f, 800f)))
        assertEquals(PetVector(200f, bounds.floor.toFloat()), runtime.snapshot().position)
        runtime.dispatch(PetEvent.Resumed)
        assertEquals(PetLifecycleState.ACTIVE, runtime.snapshot().lifecycle)
    }

    @Test
    fun mandatoryEnvironmentDoesNotBreakAnActiveDrag() {
        val runtime = runtime()
        runtime.dispatch(
            PetEvent.DragStarted(
                pointer = PetVector(220f, bounds.floor + 20f),
                grabOffset = PetVector(20f, 20f),
            )
        )
        runtime.dispatch(PetEvent.EnvironmentChanged(environment.copy(batteryTemperatureCelsius = 45f)))

        assertEquals(PetInteractionState.DRAGGING, runtime.snapshot().interaction)
        assertEquals(PetIntent.DRAG, runtime.snapshot().intent)
        assertEquals("drag", runtime.snapshot().clipId)
    }

    @Test
    fun tenThousandTicksRemainInsideValidBounds() {
        val runtime = runtime()

        repeat(10_000) {
            runtime.dispatch(PetEvent.Tick(1f / 60f))
            val snapshot = runtime.snapshot()
            assertTrue(snapshot.position.x in bounds.left.toFloat()..bounds.right.toFloat())
            assertTrue(snapshot.position.y in bounds.top.toFloat()..bounds.floor.toFloat())
            assertTrue(snapshot.velocity.x.isFinite() && snapshot.velocity.y.isFinite())
            assertTrue(snapshot.lifecycle != PetLifecycleState.DESTROYED)
        }
    }

    @Test
    fun replayRequiresOrderedStepsAndReturnsEveryFrame() {
        val invalid = runCatching {
            PetReplayScript(
                id = "invalid",
                seed = 1,
                steps = listOf(
                    PetReplayStep(100L, PetEvent.Tap),
                    PetReplayStep(50L, PetEvent.Cancelled),
                ),
            )
        }.exceptionOrNull()
        assertTrue(invalid is IllegalArgumentException)

        val script = PetReplayScript(
            id = "tap_cancel",
            seed = 1,
            steps = listOf(
                PetReplayStep(0L, PetEvent.Tap),
                PetReplayStep(100L, PetEvent.Cancelled),
            ),
        )
        val frames = PetReplayRunner(runtime()).run(script)

        assertEquals(2, frames.size)
        assertEquals(PetCompletedInteraction.TAP, frames.first().output.completedInteraction)
        assertEquals(PetIntent.IDLE, frames.last().output.intent)
    }

    private fun runtime(): PetRuntime<TestBrainState> = PetRuntime(
        definition = definition(),
        brain = TestBrain(),
        clips = clips(),
        initialStatus = status,
        initialEnvironment = environment,
        initialPosition = PetVector(200f, bounds.floor.toFloat()),
    )

    private fun definition(requiredClips: Set<String> = clips().map { it.id }.toSet()): PetDefinition = PetDefinition(
        petId = "test",
        atlasSpecPath = "pets/test/test.json",
        requiredClips = requiredClips,
        locomotion = PetLocomotionProfile(
            physicsProfile = PhysicsProfile.GROUND,
            primarySurface = PetSurface.FLOOR,
            speedPixelsPerSecond = 42f,
            maximumFlingSpeedPixelsPerSecond = 300f,
        ),
        interaction = PetInteractionProfile(
            holdHapticDurationMs = 25L,
            dragVisualLagPixels = 12f,
        ),
        temperament = PetTemperament(0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f),
        bondBehaviors = emptyList(),
    )

    private fun clips(): List<PetAnimationClip> = listOf(
        PetAnimationClip("idle", listOf(0, 1), loop = true, frameDurationSeconds = 0.1f),
        PetAnimationClip("touch", listOf(2), loop = false, frameDurationSeconds = 0.1f),
        PetAnimationClip("hold", listOf(3), loop = true, frameDurationSeconds = 0.1f),
        PetAnimationClip("drag", listOf(4), loop = true, frameDurationSeconds = 0.1f),
        PetAnimationClip("airborne", listOf(5), loop = true, frameDurationSeconds = 0.1f),
        PetAnimationClip("melt", listOf(6), loop = true, frameDurationSeconds = 0.1f),
    )
}

private data class TestBrainState(
    val intent: PetIntent = PetIntent.IDLE,
    val clipId: String = "idle",
) : PetBrainState

private class TestBrain : PetBrain<TestBrainState> {
    override fun createInitialState(context: PetBrainContext): TestBrainState = TestBrainState()

    override fun reduce(
        state: TestBrainState,
        event: PetEvent,
        context: PetBrainContext,
    ): PetBrainResult<TestBrainState> {
        val next = when (event) {
            PetEvent.Tap -> TestBrainState(PetIntent.TOUCH, "touch")
            PetEvent.HoldStarted -> TestBrainState(PetIntent.HOLD, "hold")
            PetEvent.HoldReleased -> TestBrainState(PetIntent.SOCIAL, "touch")
            is PetEvent.DragStarted,
            is PetEvent.DragMoved,
            -> TestBrainState(PetIntent.DRAG, "drag")
            is PetEvent.Flung,
            is PetEvent.Released,
            -> TestBrainState(PetIntent.AIRBORNE, "airborne")
            PetEvent.RecoveryCompleted,
            PetEvent.Cancelled,
            PetEvent.Resumed,
            -> TestBrainState()
            is PetEvent.EnvironmentChanged -> if (
                (event.environment.batteryTemperatureCelsius ?: 0f) >= 40f
            ) {
                TestBrainState(PetIntent.MELT, "melt")
            } else {
                state
            }
            else -> state
        }
        return PetBrainResult(
            state = next,
            intent = next.intent,
            clipId = next.clipId,
            transform = if (next.intent == PetIntent.DRAG) {
                PetTransform(rotationDegrees = 4f)
            } else {
                PetTransform()
            },
            hapticDurationMs = if (event == PetEvent.HoldStarted) 25L else null,
        )
    }
}
