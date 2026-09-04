package com.pixelpals.app.core.care.scene

import com.pixelpals.app.core.care.PetCondition
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.status.CareAction
import com.pixelpals.app.status.PetMood
import com.pixelpals.app.status.PetStatusSnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CareSceneCoordinatorTest {
    private val snapshot: PetStatusSnapshot = PetStatusSnapshot("corgi", 90, 50, 50, 50, 30,
        PetMood.HAPPY, 1, 10, CareAction.FEED, 1)
    private fun request(id: String = "one", owner: String = "room", origin: CareSceneOrigin = CareSceneOrigin.ROOM,
        action: CareSceneAction = CareSceneAction.FEED): CareSceneRequest =
        CareSceneRequest(id, owner, PetType.CORGI, action, origin, CareSceneMode.AUTOMATIC)

    @Test fun duplicateCallbacksAndReopenedRequestsNeverRepeatEffect(): Unit = runTest {
        var count: Int = 0
        val coordinator: CareSceneCoordinator = CareSceneCoordinator(this, { snapshot }, {
            count++; CareSceneResult.Completed(snapshot, snapshot.copy(hunger = 80))
        })
        assertTrue(coordinator.start(request()))
        repeat(10) { coordinator.complete("one") }
        runCurrent()
        assertEquals(1, count)
        coordinator.cancel("room")
        assertFalse(coordinator.start(request()))
        assertTrue(coordinator.start(request(id = "two")))
    }

    @Test fun cancelledBeforeMarkerDoesNotMutate(): Unit = runTest {
        var count: Int = 0
        val coordinator: CareSceneCoordinator = CareSceneCoordinator(this, { snapshot }, { count++; CareSceneResult.Error })
        coordinator.start(request())
        coordinator.cancel("room")
        coordinator.complete("one")
        runCurrent()
        assertEquals(0, count)
        assertNull(coordinator.session.value)
    }

    @Test fun commitSurvivesHostDestructionAndBlocksConcurrentCare(): Unit = runTest {
        val gate: CompletableDeferred<Unit> = CompletableDeferred()
        var count: Int = 0
        val coordinator: CareSceneCoordinator = CareSceneCoordinator(this, { snapshot }, {
            gate.await(); count++; CareSceneResult.Completed(snapshot, snapshot)
        })
        coordinator.start(request())
        coordinator.complete("one")
        runCurrent()
        coordinator.cancel("room")
        assertFalse(coordinator.start(request("two", "overlay", CareSceneOrigin.OVERLAY)))
        gate.complete(Unit)
        runCurrent()
        assertEquals(1, count)
        assertNull(coordinator.session.value)
    }

    @Test fun roomOwnsPresentationWithoutStartingTheOverlay(): Unit = runTest {
        val coordinator: CareSceneCoordinator = CareSceneCoordinator(this, { snapshot }, { CareSceneResult.Error })
        coordinator.start(request("one", "overlay", CareSceneOrigin.OVERLAY))
        coordinator.setRoomVisible("room", true)
        assertNull(coordinator.session.value)
        assertFalse(coordinator.start(request("two", "overlay", CareSceneOrigin.OVERLAY)))
        coordinator.setRoomVisible("room", false)
        assertTrue(coordinator.start(request("two", "overlay", CareSceneOrigin.OVERLAY)))
    }

    @Test fun medicineCooldownAndHealthAreCheckedBeforeAnimation(): Unit = runTest {
        val coordinator: CareSceneCoordinator = CareSceneCoordinator(this, { snapshot }, { error("must not execute") })
        assertTrue(coordinator.start(request(action = CareSceneAction.MEDICINE)))
        assertEquals(CareSceneResult.Unavailable, coordinator.session.value?.result)
        coordinator.complete("one")
        runCurrent()
        assertFalse(isMedicineAvailable(snapshot.copy(condition = PetCondition.SICK, medicineAvailableAt = 101L), 100L))
        assertTrue(isMedicineAvailable(snapshot.copy(condition = PetCondition.SICK, medicineAvailableAt = 100L), 100L))
    }

    @Test fun databaseFailureProducesErrorNotSuccess(): Unit = runTest {
        val coordinator: CareSceneCoordinator = CareSceneCoordinator(this, { snapshot }, { throw IllegalStateException("disk") })
        coordinator.start(request())
        coordinator.complete("one")
        runCurrent()
        assertEquals(CareSceneResult.Error, coordinator.session.value?.result)
    }

    @Test fun unavailableMedicineNeverAppliesEffectsForHealthyOrCooldownPets(): Unit = runTest {
        for (status: PetStatusSnapshot in listOf(snapshot,
            snapshot.copy(condition = PetCondition.SICK, medicineAvailableAt = 101L))) {
            var effects: Int = 0
            val coordinator: CareSceneCoordinator = CareSceneCoordinator(this, { status }, {
                effects++; CareSceneResult.Completed(status, status)
            }, clock = { 100L })
            coordinator.start(request(action = CareSceneAction.MEDICINE, origin = CareSceneOrigin.OVERLAY))
            assertEquals(CareScenePhase.FINISHED, coordinator.session.value?.phase)
            assertEquals(CareSceneResult.Unavailable, coordinator.session.value?.result)
            repeat(10) { coordinator.complete("one") }
            runCurrent()
            assertEquals(0, effects)
            assertEquals(status, coordinator.session.value?.snapshot)
            coordinator.cancel("room")
            assertNull(coordinator.session.value)
        }
    }
}
