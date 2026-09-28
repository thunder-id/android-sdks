// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.presentation.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.thunderid.android.management.SelectOption
import dev.thunderid.android.management.SelectOptionsBatch
import dev.thunderid.android.management.SelectOptionsLoadContext

private const val LOG_TAG = "InfiniteOptions"

/**
 * Remembers the [InfiniteOptionsState] of a [SelectField]. Its loads run in the composition's
 * coroutine scope, so they are cancelled when the composable leaves, and the latest [load] and
 * [onError] are used from the next request on without restarting the list.
 *
 * - [initialOptions] are shown before anything is loaded, and again after a reset.
 * - [load] loads the next batch. Returning `null` counts as an empty batch.
 * - [onError] is called with the error of every failed load.
 */
@Composable
fun <C : Any> rememberInfiniteOptionsState(
    initialOptions: List<SelectOption> = emptyList(),
    onError: ((Throwable) -> Unit)? = null,
    load: suspend (SelectOptionsLoadContext<C>) -> SelectOptionsBatch<C>?,
): InfiniteOptionsState<C> {
    val scope = rememberCoroutineScope()
    val state =
        remember(scope) {
            InfiniteOptionsState(scope, initialOptions, load, onError) { message ->
                android.util.Log.w(LOG_TAG, message)
            }
        }
    SideEffect { state.update(load, onError) }
    return state
}
