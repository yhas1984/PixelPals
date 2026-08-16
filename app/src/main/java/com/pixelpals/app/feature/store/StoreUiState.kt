package com.pixelpals.app.feature.store

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pixelpals.app.R
import com.pixelpals.app.PetService
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.services.AppServices
import com.pixelpals.app.data.catalog.Cosmetic
import com.pixelpals.app.data.catalog.CosmeticCatalog
import com.pixelpals.app.data.catalog.PetCatalogItem
import com.pixelpals.app.data.prefs.SelectedPetStore
import com.pixelpals.app.data.repository.PixelPalsRepository
import com.pixelpals.app.feature.store.billing.BillingRepository
import com.pixelpals.app.feature.store.billing.ProductCatalogResult
import com.pixelpals.app.feature.store.billing.PurchaseResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StoreUiState(
    val selectedPet: PetType = PetType.CORGI,
    val balance: Int = 0,
    val lockedPremiumPets: List<PetCatalogItem> = emptyList(),
    val ownedCosmeticIds: Set<String> = emptySet(),
    val equippedCosmeticId: String? = null,
    val isLoading: Boolean = false,
    val activeActionId: String? = null,
    val message: String? = null,
    val isError: Boolean = false,
    val canRetry: Boolean = false,
    val canOpenCoins: Boolean = false,
)

class StoreViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: PixelPalsRepository = AppServices.repository(application)
    private val billing: BillingRepository = AppServices.billingRepository(application)
    private val selectedPetStore: SelectedPetStore = SelectedPetStore(application)
    private val _uiState = MutableStateFlow(StoreUiState(isLoading = true))
    val uiState: StateFlow<StoreUiState> = _uiState.asStateFlow()

    private data class StoreSnapshot(
        val selectedPet: PetType,
        val lockedPremiumPets: List<PetCatalogItem>,
        val balance: Int,
        val ownedCosmeticIds: Set<String>,
        val equippedCosmeticId: String?,
    )

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = null, isError = false, canRetry = false, canOpenCoins = false) }
            runCatching {
                val selected = selectedPetStore.load()
                val catalog = repository.getCatalog(selected)
                val balance = repository.getCoinBalance(null)
                val cosmetics = CosmeticCatalog.all(getApplication())
                val ownedCosmetics = cosmetics.map { it.productId }
                    .filter { repository.isCosmeticOwned(it) }
                    .toSet()
                StoreSnapshot(
                    selectedPet = selected,
                    lockedPremiumPets = StoreCatalogPolicy.lockedPremium(catalog),
                    balance = balance,
                    ownedCosmeticIds = ownedCosmetics,
                    equippedCosmeticId = repository.getEquippedCosmetic(selected.name.lowercase()),
                )
            }.onSuccess { snapshot ->
                _uiState.update {
                    it.copy(
                        selectedPet = snapshot.selectedPet,
                        balance = snapshot.balance,
                        lockedPremiumPets = snapshot.lockedPremiumPets,
                        ownedCosmeticIds = snapshot.ownedCosmeticIds,
                        equippedCosmeticId = snapshot.equippedCosmeticId,
                        isLoading = false,
                        message = null,
                        isError = false,
                        canRetry = false,
                        canOpenCoins = false,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        message = error.message ?: getApplication<Application>().getString(R.string.store_error),
                        isError = true,
                        canRetry = true,
                        canOpenCoins = false,
                    )
                }
            }
        }
    }

    fun unlockPremiumPet(item: PetCatalogItem) {
        if (item.petType == null || _uiState.value.activeActionId != null) return
        val actionId = item.productId ?: item.id
        viewModelScope.launch {
            _uiState.update { it.copy(activeActionId = actionId, message = null, isError = false, canRetry = false, canOpenCoins = false) }
            val success = repository.purchasePetWithCoins(item.petType)
            _uiState.update { it.copy(activeActionId = null) }
            if (success) {
                refresh()
            } else {
                _uiState.update {
                    it.copy(
                        message = getApplication<Application>().getString(R.string.store_insufficient_coins),
                        isError = true,
                        canRetry = false,
                        canOpenCoins = true,
                    )
                }
            }
        }
    }

    fun purchaseCosmetic(cosmetic: Cosmetic, onCompleted: (Boolean) -> Unit = {}) {
        if (_uiState.value.activeActionId != null) return
        viewModelScope.launch {
            val selected = selectedPetStore.load()
            _uiState.update { it.copy(activeActionId = cosmetic.id, message = null, isError = false, canRetry = false, canOpenCoins = false) }
            val purchased = repository.purchaseCosmeticWithCoins(selected.name.lowercase(), cosmetic.id)
            if (purchased) {
                repository.setEquippedCosmetic(selected.name.lowercase(), cosmetic.id)
                PetService.requestPetRefresh(getApplication())
            }
            _uiState.update { it.copy(activeActionId = null) }
            if (purchased) {
                refresh()
            } else {
                setMessage(
                    getApplication<Application>().getString(R.string.store_not_enough_coins),
                    isError = true,
                    canRetry = false,
                    canOpenCoins = true,
                )
            }
            onCompleted(purchased)
        }
    }

    fun equipCosmetic(cosmetic: Cosmetic, onCompleted: () -> Unit = {}) {
        if (_uiState.value.activeActionId != null) return
        viewModelScope.launch {
            val selected = selectedPetStore.load()
            repository.setEquippedCosmetic(selected.name.lowercase(), cosmetic.id)
            PetService.requestPetRefresh(getApplication())
            refresh()
            onCompleted()
        }
    }

    fun setMessage(message: String?, isError: Boolean = false, canRetry: Boolean = isError, canOpenCoins: Boolean = false) {
        _uiState.update {
            it.copy(
                message = message,
                isError = isError,
                canRetry = canRetry,
                canOpenCoins = canOpenCoins,
            )
        }
    }

    fun setCoinPurchaseActive(productId: String?) {
        _uiState.update { it.copy(activeActionId = productId) }
    }

    fun handleCoinPurchase(result: PurchaseResult) {
        when (result) {
            PurchaseResult.Success -> {
                setMessage(getApplication<Application>().getString(R.string.coins_purchase_success_generic))
                refresh()
            }
            PurchaseResult.Cancelled -> setMessage(getApplication<Application>().getString(R.string.store_purchase_cancelled))
            PurchaseResult.Pending -> setMessage(getApplication<Application>().getString(R.string.store_purchase_pending))
            PurchaseResult.Unavailable -> setMessage(getApplication<Application>().getString(R.string.store_billing_unavailable), true)
            is PurchaseResult.Failure -> setMessage(getApplication<Application>().getString(R.string.store_purchase_failed), true)
        }
        setCoinPurchaseActive(null)
    }

    suspend fun loadCoinPrices(productIds: List<String>): ProductCatalogResult = billing.prefetch(productIds)

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            return StoreViewModel(application) as T
        }
    }
}
