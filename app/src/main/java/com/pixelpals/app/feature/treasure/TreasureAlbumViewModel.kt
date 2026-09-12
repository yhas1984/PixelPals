package com.pixelpals.app.feature.treasure

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.services.AppServices
import com.pixelpals.app.data.repository.PixelPalsRepository
import kotlinx.coroutines.CancellationException
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

class TreasureAlbumViewModel internal constructor(
    private val loadCollection: suspend () -> TreasureCollection,
    private val sendGift: suspend (String, Boolean) -> TreasureGiftResult,
) : ViewModel() {
    constructor(repository: PixelPalsRepository, petType: PetType) : this(
        loadCollection = { repository.getTreasureCollection(petType) },
        sendGift = { id, acceptsNoReward -> repository.giftTreasure(petType, id, acceptsNoReward) },
    )

    private val mutableUiState: MutableStateFlow<TreasureAlbumUiState> =
        MutableStateFlow(TreasureAlbumUiState())
    val uiState: StateFlow<TreasureAlbumUiState> = mutableUiState.asStateFlow()
    private var isRefreshing: Boolean = false

    init {
        refresh()
    }

    fun refresh(): Unit {
        if (isRefreshing || mutableUiState.value.isGiftInProgress) return
        isRefreshing = true
        mutableUiState.update { state -> state.copy(isLoading = true, hasError = false) }
        viewModelScope.launch {
            try {
                val collection = loadCollection()
                mutableUiState.update { state ->
                    state.copy(isLoading = false, collection = collection, hasError = false)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableUiState.update { state -> state.copy(isLoading = false, hasError = true) }
            } finally {
                isRefreshing = false
            }
        }
    }

    fun giftTreasure(item: TreasureCollectionItem, acceptsNoBondReward: Boolean): Unit {
        val state = mutableUiState.value
        if (state.isGiftInProgress || state.isLoading || state.hasError) return
        val currentItem = state.collection?.items?.firstOrNull { it.id == item.id } ?: return
        if (!currentItem.canGift) return
        // Reserve synchronously, before dispatch, so repeated taps share one operation.
        mutableUiState.update { it.copy(isGiftInProgress = true, giftResult = null) }
        viewModelScope.launch {
            val result: TreasureGiftResult = runCatching {
                sendGift(item.id, acceptsNoBondReward)
            }.getOrElse {
                if (it is CancellationException) throw it
                mutableUiState.update { state ->
                    state.copy(isGiftInProgress = false, hasError = true)
                }
                return@launch
            }
            val collection: TreasureCollection? = runCatching {
                loadCollection()
            }.getOrElse {
                if (it is CancellationException) throw it
                null
            }
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
