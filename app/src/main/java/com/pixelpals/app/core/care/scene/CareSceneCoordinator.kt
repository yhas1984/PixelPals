package com.pixelpals.app.core.care.scene

import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.status.PetStatusSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One in-process action across all hosts. Completed requests are never replayed after recreation. */
class CareSceneCoordinator(
    private val scope: CoroutineScope,
    private val readSnapshot: suspend (PetType) -> PetStatusSnapshot,
    private val applyEffect: suspend (CareSceneRequest) -> CareSceneResult,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex: Mutex = Mutex()
    private val mutableSession: MutableStateFlow<CareSceneSession?> = MutableStateFlow(null)
    private val mutableRoomOwners: MutableStateFlow<Set<String>> = MutableStateFlow(emptySet())
    private val usedRequests: MutableSet<String> = mutableSetOf()
    val session: StateFlow<CareSceneSession?> = mutableSession.asStateFlow()
    val roomOwners: StateFlow<Set<String>> = mutableRoomOwners.asStateFlow()

    suspend fun start(request: CareSceneRequest): Boolean = mutex.withLock {
        if (request.id in usedRequests || mutableSession.value != null) return@withLock false
        if (request.origin == CareSceneOrigin.OVERLAY && roomOwners.value.isNotEmpty()) return@withLock false
        val snapshot: PetStatusSnapshot = readSnapshot(request.pet)
        usedRequests.add(request.id)
        val unavailable: Boolean = request.action == CareSceneAction.MEDICINE &&
            !isMedicineAvailable(snapshot, clock())
        mutableSession.value = CareSceneSession(
            request, snapshot,
            if (unavailable) CareScenePhase.FINISHED else CareScenePhase.READY,
            if (unavailable) CareSceneResult.Unavailable else null,
        )
        true
    }

    /** Application scope owns the transaction, never a View or Activity scope. */
    fun complete(requestId: String): Unit {
        scope.launch {
            val current: CareSceneSession = mutex.withLock {
                val value: CareSceneSession = mutableSession.value ?: return@launch
                if (value.request.id != requestId || value.phase != CareScenePhase.READY) return@launch
                mutableSession.value = value.copy(phase = CareScenePhase.COMMITTING)
                value
            }
            val result: CareSceneResult = try {
                applyEffect(current.request)
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                CareSceneResult.Error
            }
            mutex.withLock {
                val latest: CareSceneSession = mutableSession.value ?: return@withLock
                if (latest.request.id != requestId) return@withLock
                mutableSession.value = if (latest.isDetached) null else
                    latest.copy(phase = CareScenePhase.FINISHED, result = result)
            }
        }
    }

    suspend fun cancel(owner: String): Unit = mutex.withLock {
        val value: CareSceneSession = mutableSession.value ?: return@withLock
        if (value.request.owner != owner) return@withLock
        mutableSession.value = if (value.phase == CareScenePhase.COMMITTING)
            value.copy(isDetached = true) else null
    }

    suspend fun setRoomVisible(owner: String, visible: Boolean): Unit = mutex.withLock {
        mutableRoomOwners.value = if (visible) roomOwners.value + owner else roomOwners.value - owner
        val current: CareSceneSession = mutableSession.value ?: return@withLock
        if (visible && current.request.origin == CareSceneOrigin.OVERLAY) {
            mutableSession.value = if (current.phase == CareScenePhase.COMMITTING)
                current.copy(isDetached = true) else null
        }
    }
}
