package com.pixelpals.app.feature.home

import com.pixelpals.app.core.care.scene.*
import com.pixelpals.app.core.domain.PetType
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** The web is another presentation of the shared, transactional feeding action. */
internal class TelaWebFeeding(
    private val scope: CoroutineScope,
    private val cleanupScope: CoroutineScope,
    private val coordinator: CareSceneCoordinator,
    private val beginHunt: (Int) -> Boolean,
    private val finishHunt: (Boolean) -> Unit,
    private val setBusy: (Boolean) -> Unit,
    private val onFed: () -> Unit,
    private val onError: () -> Unit,
) {
    private val owner: String = UUID.randomUUID().toString()
    private var requestId: String? = null
    private var startJob: Job? = null
    private var pending: Boolean = false
    val isBusy: Boolean get() = pending
    init {
        scope.launch {
            coordinator.session.collect { session ->
                if (session?.request?.id != requestId || session == null || session.phase != CareScenePhase.FINISHED) return@collect
                requestId = null
                val success: Boolean = session.result is CareSceneResult.Completed
                finishHunt(success)
                if (success) onFed() else onError()
                coordinator.cancel(owner)
                pending = false
                setBusy(false)
            }
        }
    }
    fun request(index: Int): Unit {
        if (pending) return
        pending = true
        setBusy(true)
        startJob = scope.launch {
            try {
                val request: CareSceneRequest = CareSceneRequest(UUID.randomUUID().toString(), owner,
                    PetType.TELA, CareSceneAction.FEED, CareSceneOrigin.ROOM, CareSceneMode.AUTOMATIC)
                if (!coordinator.start(request)) { pending = false; setBusy(false); onError(); return@launch }
                requestId = request.id
                if (!beginHunt(index)) { cancel(); onError() }
            } catch (exception: kotlinx.coroutines.CancellationException) { throw exception
            } catch (_: Exception) { cancel(); onError() }
        }
    }
    fun complete(): Unit { requestId?.let(coordinator::complete) }
    fun cancel(): Unit {
        startJob?.cancel()
        requestId = null
        pending = false
        finishHunt(false)
        setBusy(false)
        cleanupScope.launch { coordinator.cancel(owner) }
    }
}
