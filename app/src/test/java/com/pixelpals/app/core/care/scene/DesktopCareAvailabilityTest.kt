package com.pixelpals.app.core.care.scene

import com.pixelpals.app.core.care.PetCondition
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.feature.care.DesktopCarePlayback
import com.pixelpals.app.status.CareAction
import com.pixelpals.app.status.PetMood
import com.pixelpals.app.status.PetStatusSnapshot
import org.junit.Assert.*
import org.junit.Test

class DesktopCareAvailabilityTest {
    private val snapshot: PetStatusSnapshot = PetStatusSnapshot("corgi", 90, 50, 50, 50, 30,
        PetMood.HAPPY, 1, 10, CareAction.FEED, 1)
    private val everydayActions: List<CareSceneAction> = listOf(CareSceneAction.FEED, CareSceneAction.PLAY,
        CareSceneAction.PET, CareSceneAction.CLEAN, CareSceneAction.REST)

    @Test fun everyCatalogPetSupportsDesktopCare(): Unit {
        assertEquals(PetType.entries.toSet(), DesktopCarePlayback.SUPPORTED_PETS)
    }

    @Test fun medicineIsHiddenForHealthyAtRiskAndHibernatingPets(): Unit {
        for (condition: PetCondition in listOf(PetCondition.HEALTHY, PetCondition.AT_RISK, PetCondition.HIBERNATING)) {
            assertEquals(everydayActions, getAvailableDesktopCareActions(snapshot.copy(condition = condition), 1_000L))
        }
    }

    @Test fun unknownHealthHidesMedicine(): Unit {
        assertEquals(everydayActions, getAvailableDesktopCareActions(null, 1_000L))
    }

    @Test fun sickAndRecoveringPetsSeeMedicineOnlyAfterTheCooldown(): Unit {
        for (condition: PetCondition in listOf(PetCondition.SICK, PetCondition.RECOVERING)) {
            val status: PetStatusSnapshot = snapshot.copy(condition = condition, medicineAvailableAt = 1_000L)
            assertEquals(everydayActions, getAvailableDesktopCareActions(status, 999L))
            assertEquals(CareSceneAction.entries, getAvailableDesktopCareActions(status, 1_000L))
            assertEquals(CareSceneAction.entries, getAvailableDesktopCareActions(status, 1_001L))
        }
    }

    @Test fun medicineDisappearsAfterDosingOrRecoveryWithoutChangingOtherActions(): Unit {
        val sick: PetStatusSnapshot = snapshot.copy(condition = PetCondition.SICK, medicineAvailableAt = 0L)
        assertTrue(CareSceneAction.MEDICINE in getAvailableDesktopCareActions(sick, 100L))
        val dosed: PetStatusSnapshot = sick.copy(condition = PetCondition.RECOVERING, medicineAvailableAt = 200L)
        assertEquals(everydayActions, getAvailableDesktopCareActions(dosed, 100L))
        assertEquals(everydayActions, getAvailableDesktopCareActions(dosed.copy(condition = PetCondition.HEALTHY), 200L))
    }
}
