package com.pixelpals.app.feature.treasure

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TreasureAlbumViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val item = TreasureCollectionItem("bone", "bone", "Bone", "Story", "Hint", 2, 2, 1L, true, true)
    private val original = TreasureCollection(
        TreasureCollectionSummary(1, 19, TreasureBadge.NONE, 5, 25, true, false, 10), listOf(item),
    )
    private val gifted = original.copy(
        summary = original.summary.copy(hasGiftedToday = true, currentBond = 15),
        items = listOf(item.copy(inventoryCount = 1, canGift = false)),
    )

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun failedGiftKeepsSnapshotAndRetryOnlyReloads() = runTest(dispatcher) {
        var gifts = 0
        var reads = 0
        val model = TreasureAlbumViewModel(
            loadCollection = { reads++; original },
            sendGift = { _, _ -> gifts++; error("Disk write failed") },
        )
        advanceUntilIdle()
        model.giftTreasure(item, false)
        advanceUntilIdle()
        assertTrue(model.uiState.value.hasError)
        assertSame(original, model.uiState.value.collection)
        assertFalse(model.uiState.value.isGiftInProgress)
        assertNull(model.uiState.value.giftResult)
        model.giftTreasure(item, false)
        model.refresh()
        model.refresh()
        assertTrue(model.uiState.value.isLoading)
        advanceUntilIdle()
        assertEquals(1, gifts)
        assertEquals(2, reads)
        assertFalse(model.uiState.value.hasError)
    }

    @Test fun confirmedGiftThenReadFailureCannotReplayTheGift() = runTest(dispatcher) {
        var gifts = 0
        var failRead = false
        var current = original
        val model = TreasureAlbumViewModel(
            loadCollection = { if (failRead) error("Disk read failed") else current },
            sendGift = { _, _ ->
                gifts++
                current = gifted
                failRead = true
                TreasureGiftResult.Success(item.id, item.emoji, true, 5, 1)
            },
        )
        advanceUntilIdle()
        model.giftTreasure(item, false)
        advanceUntilIdle()
        assertTrue(model.uiState.value.hasError)
        assertSame(original, model.uiState.value.collection)
        assertTrue(model.uiState.value.giftResult is TreasureGiftResult.Success)
        model.consumeGiftResult()
        model.giftTreasure(item, false)
        failRead = false
        model.refresh()
        model.giftTreasure(item, false)
        advanceUntilIdle()
        assertEquals(1, gifts)
        assertEquals(gifted, model.uiState.value.collection)
        assertFalse(model.uiState.value.hasError)
        assertNull(model.uiState.value.giftResult)
        model.giftTreasure(item, false) // An old dialog cannot gift an unavailable item.
        advanceUntilIdle()
        assertEquals(1, gifts)
    }

    @Test fun duplicateTapsAndRefreshDuringGiftShareOneOperation() = runTest(dispatcher) {
        var gifts = 0
        var reads = 0
        val model = TreasureAlbumViewModel(
            loadCollection = { reads++; if (gifts == 0) original else gifted },
            sendGift = { _, _ ->
                gifts++
                delay(100)
                TreasureGiftResult.Success(item.id, item.emoji, true, 5, 1)
            },
        )
        advanceUntilIdle()
        model.giftTreasure(item, false)
        model.giftTreasure(item, false)
        model.refresh()
        runCurrent()
        assertTrue(model.uiState.value.isGiftInProgress)
        assertEquals(1, gifts)
        advanceUntilIdle()
        assertEquals(1, gifts)
        assertEquals(2, reads)
        assertEquals(gifted, model.uiState.value.collection)
    }

    @Test fun refreshFailureKeepsCollectionAndCanRecover() = runTest(dispatcher) {
        var failRead = false
        val model = TreasureAlbumViewModel(
            loadCollection = { if (failRead) error("Disk read failed") else original },
            sendGift = { _, _ -> error("A refresh must never send a gift") },
        )
        advanceUntilIdle()
        failRead = true
        model.refresh()
        assertSame(original, model.uiState.value.collection)
        advanceUntilIdle()
        assertTrue(model.uiState.value.hasError)
        failRead = false
        model.refresh()
        advanceUntilIdle()
        assertFalse(model.uiState.value.isLoading)
        assertFalse(model.uiState.value.hasError)
        assertEquals(original, model.uiState.value.collection)
    }
}
