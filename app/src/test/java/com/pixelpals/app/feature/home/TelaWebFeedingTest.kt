package com.pixelpals.app.feature.home

import com.pixelpals.app.core.care.scene.*
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.status.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TelaWebFeedingTest {
    private val status = PetStatusSnapshot("tela",90,50,50,50,30,PetMood.HAPPY,1,10,CareAction.FEED,1)
    @Test fun huntReservesSharedCareAndOnlyMealMarkerCommitsOnce() = runTest {
        var effects=0; var fed=0; var starts=0; var finished=false
        val coordinator=CareSceneCoordinator(backgroundScope,{status},{effects++; CareSceneResult.Completed(status,status.copy(hunger=80))})
        val feeding=TelaWebFeeding(backgroundScope,backgroundScope,coordinator,{starts++;true},{finished=it},{},{fed++},{fail("unexpected error")})
        feeding.request(1); feeding.request(2); runCurrent()
        assertEquals(1,starts); assertEquals(0,effects)
        assertEquals(PetType.TELA,coordinator.session.value?.request?.pet)
        repeat(5) { feeding.complete() }; runCurrent()
        assertEquals(1,effects); assertEquals(1,fed); assertTrue(finished)
        assertNull(coordinator.session.value)
    }
    @Test fun cancellationAndOccupiedCoordinatorNeverFeed() = runTest {
        var effects=0; var starts=0; var errors=0
        val coordinator=CareSceneCoordinator(backgroundScope,{status},{effects++;CareSceneResult.Completed(status,status)})
        val feeding=TelaWebFeeding(backgroundScope,backgroundScope,coordinator,{starts++;true},{},{},{},{errors++})
        feeding.request(0); runCurrent(); feeding.cancel(); runCurrent(); feeding.complete(); runCurrent()
        assertEquals(0,effects); assertNull(coordinator.session.value)
        coordinator.start(CareSceneRequest("other","other",PetType.TELA,CareSceneAction.PLAY,CareSceneOrigin.ROOM,CareSceneMode.AUTOMATIC))
        feeding.request(0); runCurrent()
        assertEquals(1,starts); assertEquals(1,errors); assertEquals(0,effects)
    }
}
