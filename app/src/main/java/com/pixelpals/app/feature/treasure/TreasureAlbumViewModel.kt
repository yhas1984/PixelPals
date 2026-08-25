package com.pixelpals.app.feature.treasure

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.services.AppServices
import com.pixelpals.app.data.repository.PixelPalsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TreasureAlbumUiState(
    val isLoading: Boolean = true,
    val collection: TreasureCollection? = null,
    val hasError: Boolean = false,
    val isGiftInProgress: Boolean = false,
    val giftResult: TreasureGiftResult? = null,
)

class TreasureAlbumViewModel(
    private val repository: PixelPalsRepository,
    private val petType: PetType,
) : ViewModel() {
    private val mutableUiState: MutableStateFlow<TreasureAlbumUiState> =
        MutableStateFlow(TreasureAlbumUiState())
    val uiState: StateFlow<TreasureAlbumUiState> = mutableUiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh(): Unit {
        viewModelScope.launch {
            mutableUiState.update { state -> state.copy(isLoading = true, hasError = false) }
            runCatching { repository.getTreasureCollection(petType) }
                .onSuccess { collection ->
                    mutableUiState.update { state ->
                        state.copy(isLoading = false, collection = collection, hasError = false)
                    }
                }
                .onFailure {
                    mutableUiState.update { state -> state.copy(isLoading = false, hasError = true) }
                }
        }
    }

    fun giftTreasure(item: TreasureCollectionItem, acceptsNoBondReward: Boolean): Unit {
        if (mutableUiState.value.isGiftInProgress) return
        viewModelScope.launch {
            mutableUiState.update { state -> state.copy(isGiftInProgress = true, giftResult = null) }
            val result: TreasureGiftResult = runCatching {
                repository.giftTreasure(
                    petType = petType,
                    treasureId = item.id,
                    acceptsNoBondReward = acceptsNoBondReward,
                )
            }.getOrElse {
                mutableUiState.update { state ->
                    state.copy(isGiftInProgress = false, hasError = true)
                }
                return@launch
            }
            val collection: TreasureCollection? = runCatching {
                repository.getTreasureCollection(petType)
            }.getOrNull()
            mutableUiState.update { state ->
                state.copy(
                    isGiftInProgress = false,
                    collection = collection ?: state.collection,
                    hasError = collection == null,
                    giftResult = result,
                )
            }
        }
    }

    fun consumeGiftResult(): Unit {
        mutableUiState.update { state -> state.copy(giftResult = null) }
    }

    class Factory(
        private val application: Application,
        private val petType: PetType,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (!modelClass.isAssignableFrom(TreasureAlbumViewModel::class.java)) {
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
            @Suppress("UNCHECKED_CAST")
            return TreasureAlbumViewModel(
                repository = AppServices.repository(application),
                petType = petType,
            ) as T
        }
    }
}
