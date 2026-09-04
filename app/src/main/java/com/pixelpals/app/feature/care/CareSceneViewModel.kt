package com.pixelpals.app.feature.care

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pixelpals.app.core.care.scene.*
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.services.AppServices
import com.pixelpals.app.data.repository.PixelPalsRepository
import com.pixelpals.app.status.PetStatusSnapshot
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CareSceneUiState(
    val snapshot: PetStatusSnapshot? = null,
    val session: CareSceneSession? = null,
    val isBusy: Boolean = false,
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
)

class CareSceneViewModel(
    val pet: PetType,
    val origin: CareSceneOrigin,
    private val repository: PixelPalsRepository,
    private val coordinator: CareSceneCoordinator,
) : ViewModel() {
    val owner: String = UUID.randomUUID().toString()
    private val mutableState: MutableStateFlow<CareSceneUiState> = MutableStateFlow(CareSceneUiState())
    val state: StateFlow<CareSceneUiState> = mutableState.asStateFlow()
    private var startJob: Job? = null

    init {
        viewModelScope.launch {
            coordinator.session.collect { session ->
                mutableState.update { it.copy(session = session?.takeIf { value -> value.request.owner == owner },
                    isBusy = session != null) }
            }
        }
        refresh()
    }

    fun refresh(): Unit {
        viewModelScope.launch {
            try {
                val snapshot: PetStatusSnapshot = repository.getStatusSnapshot(pet)
                mutableState.update { it.copy(snapshot = snapshot, isLoading = false, hasError = false) }
            } catch (exception: CancellationException) { throw exception
            } catch (_: Exception) { mutableState.update { it.copy(isLoading = false, hasError = true) } }
        }
    }

    fun start(action: CareSceneAction, mode: CareSceneMode): Unit {
        if (startJob?.isActive == true || state.value.isBusy) return
        startJob = viewModelScope.launch {
            try {
                mutableState.update { it.copy(hasError = false) }
                val request: CareSceneRequest = CareSceneRequest(UUID.randomUUID().toString(), owner, pet, action, origin, mode)
                if (!coordinator.start(request)) mutableState.update { it.copy(hasError = true) }
            } catch (exception: CancellationException) { throw exception
            } catch (_: Exception) { mutableState.update { it.copy(hasError = true) } }
        }
    }

    fun complete(requestId: String): Unit = coordinator.complete(requestId)

    fun cancel(): Unit {
        startJob?.cancel()
        AppServices.applicationScope.launch { coordinator.cancel(owner) }
    }

    fun setRoomVisible(visible: Boolean): Unit {
        if (origin != CareSceneOrigin.ROOM) return
        AppServices.applicationScope.launch { coordinator.setRoomVisible(owner, visible) }
    }

    override fun onCleared(): Unit {
        cancel()
        setRoomVisible(false)
    }

    class Factory(private val application: Application, private val pet: PetType, private val origin: CareSceneOrigin) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(CareSceneViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return CareSceneViewModel(pet, origin, AppServices.repository(application), AppServices.careScenes(application)) as T
        }
    }
}
