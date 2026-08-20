package com.pixelpals.app.feature.store

import android.app.Activity
import android.app.Application
import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.data.catalog.CatalogItemState
import com.pixelpals.app.data.catalog.Cosmetic
import com.pixelpals.app.data.catalog.PetCatalogItem
import com.pixelpals.app.data.repository.CoinSpendResult
import com.pixelpals.app.feature.store.billing.BillingRepository
import com.pixelpals.app.feature.store.billing.ProductCatalogResult
import com.pixelpals.app.feature.store.billing.PurchaseResult
import com.pixelpals.app.feature.store.billing.RestoreResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StoreViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var application: Application

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        application = Application()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun coinCatalogIsCachedUntilExplicitRetry() = runTest(dispatcher) {
        val dataSource = FakeStoreDataSource()
        val billing = FakeBillingRepository()
        val viewModel = createViewModel(dataSource, billing)
        advanceUntilIdle()

        viewModel.loadCoinCatalog()
        viewModel.loadCoinCatalog()
        advanceUntilIdle()
        viewModel.loadCoinCatalog()
        advanceUntilIdle()

        assertEquals(1, billing.prefetchCalls)
        assertTrue(viewModel.uiState.value.coinCatalogState is CoinCatalogState.Available)

        viewModel.loadCoinCatalog(isForced = true)
        advanceUntilIdle()
        assertEquals(2, billing.prefetchCalls)
    }

    @Test
    fun unavailableCoinCatalogDoesNotRepeatUntilExplicitRetry() = runTest(dispatcher) {
        val billing = FakeBillingRepository(
            catalogResult = ProductCatalogResult.Unavailable("Play unavailable"),
        )
        val viewModel = createViewModel(FakeStoreDataSource(), billing)
        advanceUntilIdle()

        viewModel.loadCoinCatalog()
        advanceUntilIdle()
        viewModel.loadCoinCatalog()
        advanceUntilIdle()

        assertEquals(1, billing.prefetchCalls)
        assertTrue(viewModel.uiState.value.coinCatalogState is CoinCatalogState.Unavailable)

        viewModel.loadCoinCatalog(isForced = true)
        advanceUntilIdle()
        assertEquals(2, billing.prefetchCalls)
    }

    @Test
    fun partialCoinCatalogPreservesMissingProductIds() = runTest(dispatcher) {
        val result: ProductCatalogResult = ProductCatalogResult.Available(
            prices = mapOf("coins_small" to "\$0.99"),
            missingProductIds = setOf("coins_medium"),
        )
        val viewModel = createViewModel(
            dataSource = FakeStoreDataSource(),
            billing = FakeBillingRepository(catalogResult = result),
        )
        advanceUntilIdle()

        viewModel.loadCoinCatalog()
        advanceUntilIdle()

        val state: CoinCatalogState.Available =
            viewModel.uiState.value.coinCatalogState as CoinCatalogState.Available
        assertEquals(setOf("coins_medium"), state.missingProductIds)
        assertEquals("\$0.99", state.prices["coins_small"])
    }

    @Test
    fun simultaneousUnlockRequestsDeductOnlyOnceAndPublishFinalSnapshot() = runTest(dispatcher) {
        val dataSource = FakeStoreDataSource(balance = 1_000)
        val viewModel = createViewModel(dataSource, FakeBillingRepository())
        advanceUntilIdle()
        val item: PetCatalogItem = requireNotNull(viewModel.uiState.value.lockedPremiumPets.firstOrNull())

        viewModel.unlockPremiumPet(item)
        viewModel.unlockPremiumPet(item)
        advanceUntilIdle()

        assertEquals(1, dataSource.petPurchaseCalls)
        assertEquals(550, viewModel.uiState.value.balance)
        assertTrue(viewModel.uiState.value.lockedPremiumPets.isEmpty())
        assertNull(viewModel.uiState.value.activeOperation)
    }

    @Test
    fun insufficientBalanceDoesNotMutateCatalogOrBalance() = runTest(dispatcher) {
        val dataSource = FakeStoreDataSource(balance = 100)
        val viewModel = createViewModel(dataSource, FakeBillingRepository())
        advanceUntilIdle()
        val item: PetCatalogItem = viewModel.uiState.value.lockedPremiumPets.first()

        viewModel.unlockPremiumPet(item)
        advanceUntilIdle()

        assertEquals(100, viewModel.uiState.value.balance)
        assertEquals(1, viewModel.uiState.value.lockedPremiumPets.size)
        assertEquals(StoreNoticeType.INSUFFICIENT_COINS, viewModel.uiState.value.notice?.type)
        assertNull(viewModel.uiState.value.activeOperation)
    }

    @Test
    fun newerRefreshDiscardsTheOlderPetSnapshot() = runTest(dispatcher) {
        var selectedPet: PetType = PetType.CORGI
        val dataSource = FakeStoreDataSource(slowCatalogPet = PetType.CORGI)
        val viewModel = createViewModel(
            dataSource = dataSource,
            billing = FakeBillingRepository(),
            selectedPetProvider = { selectedPet },
        )
        runCurrent()

        selectedPet = PetType.TELA
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(PetType.TELA, viewModel.uiState.value.selectedPet)
        assertTrue(!viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun billingOutcomesPublishDistinctNoticesAndSuccessUnlocksTheOperation() = runTest(dispatcher) {
        val viewModel = createViewModel(FakeStoreDataSource(), FakeBillingRepository())
        advanceUntilIdle()
        val outcomes = listOf(
            PurchaseResult.Cancelled to StoreNoticeType.PURCHASE_CANCELLED,
            PurchaseResult.Pending to StoreNoticeType.PURCHASE_PENDING,
            PurchaseResult.Unavailable to StoreNoticeType.BILLING_UNAVAILABLE,
            PurchaseResult.Failure("declined") to StoreNoticeType.PURCHASE_FAILED,
        )

        outcomes.forEachIndexed { index, (result, noticeType) ->
            assertTrue(viewModel.beginCoinPurchase("pack_$index"))
            viewModel.handleCoinPurchase(result)
            assertEquals(noticeType, viewModel.uiState.value.notice?.type)
            assertNull(viewModel.uiState.value.activeOperation)
        }

        assertTrue(viewModel.beginCoinPurchase("pack_success"))
        viewModel.handleCoinPurchase(PurchaseResult.Success)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.notice)
        assertNull(viewModel.uiState.value.activeOperation)
    }

    private fun createViewModel(
        dataSource: StoreDataSource,
        billing: BillingRepository,
        selectedPetProvider: () -> PetType = { PetType.CORGI },
    ): StoreViewModel = StoreViewModel(
        application = application,
        dataSource = dataSource,
        billing = billing,
        selectedPetProvider = selectedPetProvider,
        petRefreshRequester = {},
    )

    private class FakeBillingRepository(
        private val catalogResult: ProductCatalogResult? = null,
    ) : BillingRepository {
        var prefetchCalls: Int = 0

        override suspend fun prefetch(productIds: List<String>): ProductCatalogResult {
            prefetchCalls += 1
            return catalogResult
                ?: ProductCatalogResult.Available(productIds.associateWith { "\$1.99" })
        }

        override fun launchPurchase(
            activity: Activity,
            productId: String,
            onFinished: (PurchaseResult) -> Unit,
        ) {
            onFinished(PurchaseResult.Success)
        }

        override suspend fun reconcilePurchases(): RestoreResult = RestoreResult.NothingToRestore
    }

    private class FakeStoreDataSource(
        private var balance: Int = 1_000,
        private val slowCatalogPet: PetType? = null,
    ) : StoreDataSource {
        private var isPetOwned: Boolean = false
        var petPurchaseCalls: Int = 0
        private val item: PetCatalogItem = PetCatalogItem(
            id = "taro",
            displayName = "Taro",
            description = "Test pet",
            previewResId = 0,
            petType = PetType.TARO,
            productId = "pet_taro_premium",
            isPremium = true,
            state = CatalogItemState.LOCKED,
            coinPrice = 450,
        )

        override suspend fun getCatalog(selectedPet: PetType): List<PetCatalogItem> {
            if (selectedPet == slowCatalogPet) delay(1_000)
            return if (isPetOwned) {
                listOf(item.copy(state = CatalogItemState.OWNED))
            } else {
                listOf(item)
            }
        }

        override suspend fun getBalance(): Int = balance

        override fun getCosmetics(): List<Cosmetic> = emptyList()

        override suspend fun isCosmeticOwned(productId: String): Boolean = false

        override fun getEquippedCosmetic(petId: String): String? = null

        override fun setEquippedCosmetic(petId: String, cosmeticId: String?) = Unit

        override suspend fun purchasePet(petType: PetType): CoinSpendResult {
            petPurchaseCalls += 1
            delay(100)
            if (isPetOwned) return CoinSpendResult.AlreadyOwned
            if (balance < 450) return CoinSpendResult.InsufficientFunds
            balance -= 450
            isPetOwned = true
            return CoinSpendResult.Purchased
        }

        override suspend fun purchaseCosmetic(
            petId: String,
            cosmeticId: String,
        ): CoinSpendResult = CoinSpendResult.Failure("Not used")
    }
}
