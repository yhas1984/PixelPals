package com.pixelpals.app.feature.home

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest

/** A failed query ends only that attempt; a selection change or retry starts a new query. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun <K, T> restartableObservation(keys: Flow<K>, observe: (K) -> Flow<T>, onError: () -> Unit): Flow<T> =
    keys.flatMapLatest { key -> observe(key).catch { onError() } }
