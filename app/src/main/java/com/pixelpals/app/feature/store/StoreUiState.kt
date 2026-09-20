package com.pixelpals.app.feature.store

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pixelpals.app.PetService
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.services.AppServices
import com.pixelpals.app.data.catalog.Cosmetic
import com.pixelpals.app.data.catalog.CosmeticCatalog
import com.pixelpals.app.data.catalog.PetCatalogItem
import com.pixelpals.app.data.prefs.SelectedPetStore
import com.pixelpals.app.data.repository.CoinSpendResult
import com.pixelpals.app.data.repository.PixelPalsRepository
import com.pixelpals.app.feature.store.billing.BillingRepository
import com.pixelpals.app.feature.store.billing.ProductCatalogResult
import com.pixelpals.app.feature.store.billing.PurchaseResult
import com.pixelpals.app.feature.store.billing.RestoreResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface CoinCatalogState {
    data object NotRequested : CoinCatalogState
    data object Loading : CoinCatalogState
    data class Available(
        val prices: Map<String, String>,
        val missingProductIds: Set<String> = emptySet(),
    ) : CoinCatalogState
    data class Unavailable(val reason: String) : CoinCatalogState
}

enum class StoreNoticeType {
    INSUFFICIENT_COINS,
    PURCHASE_CANCELLED,
    PURCHASE_PENDING,
    BILLING_UNAVAILABLE,
    PURCHASE_FAILED,
    STORE_FAILURE,
    EQUIP_AFTER_PURCHASE_FAILED,
    RESTORE_SUCCEEDED,
    NOTHING_TO_RESTORE,
    RESTORE_FAILED,
}

data class StoreNotice(
    val type: StoreNoticeType,
    val detail: String? = null,
    val count: Int = 0,
)

sealed interface ActiveStoreOperation {
    val id: String

    data class UnlockPet(override val id: String) : ActiveStoreOperation
    data class PurchaseCosmetic(override val id: String) : ActiveStoreOperation
    data class EquipCosmetic(override val id: String) : ActiveStoreOperation
    data class PurchaseCoins(override val id: String) : ActiveStoreOperation
    data object RestorePurchases : ActiveStoreOperation { override val id: String = "restore" }
}

data class StoreUiState(
    val selectedPet: PetType = PetType.CORGI,
    val balance: Int = 0,
    val lockedPremiumPets: List<PetCatalogItem> = emptyList(),
    val cosmetics: List<Cosmetic> = emptyList(),
    val ownedCosmeticIds: Set<String> = emptySet(),
    val equippedCosmeticId: String? = null,
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val activeOperation: ActiveStoreOperation? = null,
    val notice: StoreNotice? = null,
    val coinCatalogState: CoinCatalogState = CoinCatalogState.NotRequested,
)

interface StoreDataSource {
    suspend fun getCatalog(selectedPet: PetType): List<PetCatalogItem>
    suspend fun getBalance(): Int
    fun getCosmetics(): List<Cosmetic>
    suspend fun isCosmeticOwned(productId: String): Boolean
    fun getEquippedCosmetic(petId: String): String?
    suspend fun setEquippedCosmetic(petId: String, cosmeticId: String?)
    suspend fun purchasePet(petType: PetType): CoinSpendResult
    suspend fun purchaseCosmetic(petId: String, cosmeticId: String): CoinSpendResult
}

class RepositoryStoreDataSource(context: Context) : StoreDataSource {
    private val applicationContext: Context = context.applicationContext
    private val repository: PixelPalsRepository = AppServices.repository(applicationContext)

    override suspend fun getCatalog(selectedPet: PetType): List<PetCatalogItem> =
        repository.getCatalog(selectedPet)

    override suspend fun getBalance(): Int = repository.getCoinBalance(null)

    override fun getCosmetics(): List<Cosmetic> = CosmeticCatalog.all(applicationContext)

    override suspend fun isCosmeticOwned(productId: String): Boolean =
        repository.isCosmeticOwned(productId)

    override fun getEquippedCosmetic(petId: String): String? =
        repository.getEquippedCosmetic(petId)

    override suspend fun setEquippedCosmetic(petId: String, cosmeticId: String?) {
        repository.setEquippedCosmetic(petId, cosmeticId)
    }

    override suspend fun purchasePet(petType: PetType): CoinSpendResult =
        repository.purchasePetWithCoins(petType)

    override suspend fun purchaseCosmetic(
        petId: String,
        cosmeticId: String,
    ): CoinSpendResult = repository.purchaseCosmeticWithCoins(petId, cosmeticId)
}

class StoreViewModel(
    application: Application,
    private val dataSource: StoreDataSource = RepositoryStoreDataSource(application),
    private val billing: BillingRepository = AppServices.billingRepository(application),
    private val selectedPetProvider: () -> PetType = { SelectedPetStore(application).load() },
    private val petRefreshRequester: () -> Unit = { PetService.requestPetRefresh(application) },
) : AndroidViewModel(application) {
    private data class StoreSnapshot(
        val selectedPet: PetType,
        val lockedPremiumPets: List<PetCatalogItem>,
        val cosmetics: List<Cosmetic>,
        val balance: Int,
        val ownedCosmeticIds: Set<String>,
        val equippedCosmeticId: String?,
    )

    private val mutableUiState = MutableStateFlow(StoreUiState())
    val uiState: StateFlow<StoreUiState> = mutableUiState.asStateFlow()
    private var refreshJob: Job? = null
    private var coinCatalogJob: Job? = null
    private var refreshGeneration: Long = 0
    private var lastSnapshotAt: Long = 0L
    private var hasReconciledPurchases: Boolean = false

    init {
        refresh()
    }

    fun refresh() {
        if (mutableUiState.value.activeOperation == ActiveStoreOperation.RestorePurchases) return
        val selectedPet: PetType = selectedPetProvider()
        val isBlocking: Boolean = mutableUiState.value.lockedPremiumPets.isEmpty() &&
            mutableUiState.value.cosmetics.isEmpty()
        val generation: Long = beginRefresh()
        refreshJob = viewModelScope.launch {
            mutableUiState.update { state ->
                state.copy(
                    isInitialLoading = isBlocking,
                    isRefreshing = !isBlocking,
                    notice = null,
                )
            }
            publishSnapshot(selectedPet, generation)
        }
    }

    fun refreshIfPetChanged() {
        val state: StoreUiState = mutableUiState.value
        if (state.isInitialLoading || state.activeOperation != null) return
        if (selectedPetProvider() != state.selectedPet) refresh()
    }

    fun refreshIfStale(maxAgeMs: Long = DEFAULT_REFRESH_AGE_MS) {
        val state: StoreUiState = mutableUiState.value
        if (state.isInitialLoading || state.activeOperation != null || state.isRefreshing || state.notice != null) {
            return
        }
        val selectedPet: PetType = selectedPetProvider()
        if (selectedPet != state.selectedPet || System.currentTimeMillis() - lastSnapshotAt > maxAgeMs) {
            refresh()
        }
    }

    fun unlockPremiumPet(item: PetCatalogItem) {
        val petType: PetType = item.petType ?: return
        val operationId: String = item.productId ?: item.id
        if (!startOperation(ActiveStoreOperation.UnlockPet(operationId))) return
        viewModelScope.launch {
            val result: CoinSpendResult = runCatching { dataSource.purchasePet(petType) }
                .getOrElse { CoinSpendResult.Failure(it.message ?: "Purchase failed") }
            completeCoinSpend(result)
        }
    }

    fun purchaseCosmetic(cosmetic: Cosmetic, onCompleted: (Boolean) -> Unit = {}) {
        if (!startOperation(ActiveStoreOperation.PurchaseCosmetic(cosmetic.id))) return
        viewModelScope.launch {
            val selectedPet: PetType = selectedPetProvider()
            val petId: String = selectedPet.name.lowercase()
            val result: CoinSpendResult = runCatching {
                dataSource.purchaseCosmetic(petId, cosmetic.id)
            }.getOrElse { CoinSpendResult.Failure(it.message ?: "Purchase failed") }
            val isOwned: Boolean = result == CoinSpendResult.Purchased ||
                result == CoinSpendResult.AlreadyOwned
            var equipFailure: Throwable? = null
            if (isOwned) {
                runCatching {
                    dataSource.setEquippedCosmetic(petId, cosmetic.id)
                    petRefreshRequester()
                }.onFailure { equipFailure = it }
            }
            completeCoinSpend(result)
            if (equipFailure != null) mutableUiState.update { state ->
                state.copy(notice = StoreNotice(StoreNoticeType.EQUIP_AFTER_PURCHASE_FAILED))
            }
            onCompleted(isOwned && equipFailure == null)
        }
    }

    fun equipCosmetic(cosmetic: Cosmetic, onCompleted: () -> Unit = {}) {
        updateEquippedCosmetic(cosmetic.id, onCompleted)
    }

    fun unequipCosmetic() {
        updateEquippedCosmetic(null)
    }

    private fun updateEquippedCosmetic(cosmeticId: String?, onCompleted: () -> Unit = {}) {
        if (!startOperation(ActiveStoreOperation.EquipCosmetic(cosmeticId ?: "unequip"))) return
        viewModelScope.launch {
            val selectedPet: PetType = selectedPetProvider()
            runCatching {
                dataSource.setEquippedCosmetic(selectedPet.name.lowercase(), cosmeticId)
                petRefreshRequester()
                refreshAfterOperation(selectedPet)
            }.onFailure { error ->
                mutableUiState.update { state ->
                    state.copy(
                        activeOperation = null,
                        notice = StoreNotice(StoreNoticeType.STORE_FAILURE, error.message),
                    )
                }
            }
            onCompleted()
        }
    }

    fun loadCoinCatalog(isForced: Boolean = false) {
        val currentState: CoinCatalogState = mutableUiState.value.coinCatalogState
        if (!isForced && currentState != CoinCatalogState.NotRequested) {
            return
        }
        coinCatalogJob?.cancel()
        coinCatalogJob = viewModelScope.launch {
            mutableUiState.update { it.copy(coinCatalogState = CoinCatalogState.Loading) }
            val productIds: List<String> = com.pixelpals.app.data.catalog.CoinProduct.CATALOG
                .map { it.productId }
            val result: ProductCatalogResult = billing.prefetch(productIds)
            mutableUiState.update { state ->
                state.copy(
                    coinCatalogState = when (result) {
                        is ProductCatalogResult.Available -> CoinCatalogState.Available(
                            prices = result.prices,
                            missingProductIds = result.missingProductIds,
                        )
                        is ProductCatalogResult.Unavailable -> CoinCatalogState.Unavailable(result.reason)
                        is ProductCatalogResult.Failure -> CoinCatalogState.Unavailable(result.reason)
                    },
                )
            }
        }
    }

    fun beginCoinPurchase(productId: String): Boolean =
        startOperation(ActiveStoreOperation.PurchaseCoins(productId))

    fun reconcilePurchasesOnce() {
        if (hasReconciledPurchases || mutableUiState.value.activeOperation != null) return
        hasReconciledPurchases = true
        restorePurchases(manual = false)
    }

    fun restorePurchases(manual: Boolean = true) {
        if (!startOperation(ActiveStoreOperation.RestorePurchases)) return
        // A catalog load started before reconciliation must not clear its operation lock.
        beginRefresh()
        mutableUiState.update { it.copy(isRefreshing = false) }
        viewModelScope.launch {
            val result: RestoreResult = try {
                billing.reconcilePurchases()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                RestoreResult.Failure("")
            }
            refreshAfterOperation(selectedPetProvider())
            // Preserve a catalog error if refreshing local ownership failed.
            if (mutableUiState.value.notice != null) return@launch
            val notice: StoreNotice? = when (result) {
                is RestoreResult.Restored -> if (manual) StoreNotice(StoreNoticeType.RESTORE_SUCCEEDED, count = result.count) else null
                RestoreResult.NothingToRestore -> if (manual) StoreNotice(StoreNoticeType.NOTHING_TO_RESTORE) else null
                RestoreResult.Unavailable -> if (manual) StoreNotice(StoreNoticeType.RESTORE_FAILED) else null
                is RestoreResult.Failure -> StoreNotice(StoreNoticeType.RESTORE_FAILED)
            }
            mutableUiState.update { it.copy(notice = notice) }
        }
    }

    fun handleCoinPurchase(result: PurchaseResult) {
        if (result == PurchaseResult.Success) {
            viewModelScope.launch { refreshAfterOperation(selectedPetProvider()) }
            return
        }
        val noticeType: StoreNoticeType = when (result) {
            PurchaseResult.Cancelled -> StoreNoticeType.PURCHASE_CANCELLED
            PurchaseResult.Pending -> StoreNoticeType.PURCHASE_PENDING
            PurchaseResult.Unavailable -> StoreNoticeType.BILLING_UNAVAILABLE
            is PurchaseResult.Failure -> StoreNoticeType.PURCHASE_FAILED
            PurchaseResult.Success -> return
        }
        mutableUiState.update { state ->
            state.copy(
                activeOperation = null,
                notice = StoreNotice(
                    noticeType,
                    (result as? PurchaseResult.Failure)?.reason,
                ),
            )
        }
    }

    fun clearNotice() {
        mutableUiState.update { it.copy(notice = null) }
    }

    fun reportFailure(detail: String?) {
        mutableUiState.update { state ->
            state.copy(notice = StoreNotice(StoreNoticeType.STORE_FAILURE, detail))
        }
    }

    private fun startOperation(operation: ActiveStoreOperation): Boolean {
        if (mutableUiState.value.activeOperation != null) return false
        mutableUiState.update { state -> state.copy(activeOperation = operation, notice = null) }
        return true
    }

    private suspend fun completeCoinSpend(result: CoinSpendResult) {
        when (result) {
            CoinSpendResult.Purchased,
            CoinSpendResult.AlreadyOwned -> refreshAfterOperation(selectedPetProvider())
            CoinSpendResult.InsufficientFunds -> mutableUiState.update { state ->
                state.copy(
                    activeOperation = null,
                    notice = StoreNotice(StoreNoticeType.INSUFFICIENT_COINS),
                )
            }
            is CoinSpendResult.Failure -> mutableUiState.update { state ->
                state.copy(
                    activeOperation = null,
                    notice = StoreNotice(StoreNoticeType.STORE_FAILURE, result.reason),
                )
            }
        }
    }

    private suspend fun refreshAfterOperation(selectedPet: PetType) {
        val generation: Long = beginRefresh()
        mutableUiState.update { it.copy(isRefreshing = true) }
        publishSnapshot(selectedPet, generation)
    }

    private fun beginRefresh(): Long {
        refreshGeneration += 1
        refreshJob?.cancel()
        return refreshGeneration
    }

    private suspend fun publishSnapshot(selectedPet: PetType, generation: Long) {
        runCatching { loadSnapshot(selectedPet) }
            .onSuccess { snapshot ->
                if (generation != refreshGeneration) return@onSuccess
                lastSnapshotAt = System.currentTimeMillis()
                mutableUiState.update { state ->
                    state.copy(
                        selectedPet = snapshot.selectedPet,
                        balance = snapshot.balance,
                        lockedPremiumPets = snapshot.lockedPremiumPets,
                        cosmetics = snapshot.cosmetics,
                        ownedCosmeticIds = snapshot.ownedCosmeticIds,
                        equippedCosmeticId = snapshot.equippedCosmeticId,
                        isInitialLoading = false,
                        isRefreshing = false,
                        activeOperation = null,
                        notice = null,
                    )
                }
            }
            .onFailure { error ->
                if (generation != refreshGeneration) return@onFailure
                mutableUiState.update { state ->
                    state.copy(
                        isInitialLoading = false,
                        isRefreshing = false,
                        activeOperation = null,
                        notice = StoreNotice(StoreNoticeType.STORE_FAILURE, error.message),
                    )
                }
            }
    }

    private suspend fun loadSnapshot(selectedPet: PetType): StoreSnapshot {
        val catalog: List<PetCatalogItem> = dataSource.getCatalog(selectedPet)
        val cosmetics: List<Cosmetic> = dataSource.getCosmetics()
        val ownedCosmeticIds: Set<String> = cosmetics
            .filter { cosmetic -> dataSource.isCosmeticOwned(cosmetic.productId) }
            .mapTo(linkedSetOf()) { cosmetic -> cosmetic.productId }
        return StoreSnapshot(
            selectedPet = selectedPet,
            lockedPremiumPets = StoreCatalogPolicy.lockedPremium(catalog),
            cosmetics = cosmetics,
            balance = dataSource.getBalance(),
            ownedCosmeticIds = ownedCosmeticIds,
            equippedCosmeticId = dataSource.getEquippedCosmetic(selectedPet.name.lowercase()),
        )
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
            StoreViewModel(application) as T
    }

    companion object {
        private const val DEFAULT_REFRESH_AGE_MS: Long = 2_000L
    }
}
