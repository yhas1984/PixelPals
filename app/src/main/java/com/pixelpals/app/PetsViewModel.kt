package com.pixelpals.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.services.AppServices
import com.pixelpals.app.data.catalog.CatalogItemState
import com.pixelpals.app.data.catalog.PetCatalogItem
import com.pixelpals.app.data.prefs.SelectedPetStore
import com.pixelpals.app.status.PetStatusSnapshot
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PetsUiState(
    val selectedPet: PetType = PetType.CORGI,
    val snapshot: PetStatusSnapshot? = null,
    val items: List<PetCatalogItem> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

class PetsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AppServices.repository(application)
    private val selectedPetStore = SelectedPetStore(application)
    private val mutableUiState = MutableStateFlow(PetsUiState())
    val uiState: StateFlow<PetsUiState> = mutableUiState.asStateFlow()
    private var refreshJob: Job? = null
    private var refreshGeneration: Long = 0

    fun refreshIfNeeded(isForced: Boolean = false) {
        val selectedPet: PetType = selectedPetStore.load()
        val currentState: PetsUiState = mutableUiState.value
        if (!isForced && !currentState.isLoading && currentState.selectedPet == selectedPet) return
        refreshGeneration += 1
        val generation: Long = refreshGeneration
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            mutableUiState.update { state ->
                state.copy(
                    selectedPet = selectedPet,
                    isLoading = state.items.isEmpty(),
                    errorMessage = null,
                )
            }
            runCatching {
                val snapshot: PetStatusSnapshot = repository.getStatusSnapshot(selectedPet)
                val items: List<PetCatalogItem> = repository.getCatalog(selectedPet).sortedWith(
                    compareBy<PetCatalogItem> { item ->
                        when (item.state) {
                            CatalogItemState.SELECTED -> 0
                            CatalogItemState.OWNED -> 1
                            CatalogItemState.LOCKED -> 2
                        }
                    }.thenBy { item -> item.displayName },
                )
                snapshot to items
            }.onSuccess { result ->
                if (generation != refreshGeneration) return@onSuccess
                mutableUiState.value = PetsUiState(
                    selectedPet = selectedPet,
                    snapshot = result.first,
                    items = result.second,
                    isLoading = false,
                )
            }.onFailure { error ->
                if (generation != refreshGeneration) return@onFailure
                mutableUiState.update { state ->
                    state.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Unable to load pet catalog",
                    )
                }
            }
        }
    }
}
