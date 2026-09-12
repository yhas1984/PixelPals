package com.pixelpals.app.feature.home

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.assertEquals
import org.junit.Test

class RestartableObservationTest {
    @Test fun retryResumesUpdatesAfterTheInitialQueryFails() = runBlocking {
        val revision = MutableStateFlow(0)
        val failed = CompletableDeferred<Unit>()
        var calls = 0
        val values = async(start = CoroutineStart.UNDISPATCHED) {
            restartableObservation(revision, { flow {
                if (calls++ == 0) { emit(1); error("temporary database failure") }
                emit(2)
            } }, { failed.complete(Unit) }).take(2).toList()
        }
        failed.await()
        revision.value += 1
        assertEquals(listOf(1, 2), withTimeout(1000) { values.await() })
    }
}
