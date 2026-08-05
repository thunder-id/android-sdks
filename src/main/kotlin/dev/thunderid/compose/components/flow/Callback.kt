// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.flow

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.thunderid.compose.LocalThunderID

/**
 * Handles the OAuth2 redirect callback URL after a browser-based flow (spec §8.4 Flow).
 *
 * Pass the callback URL received via intent / deep link to [url].
 */
@Composable
fun Callback(
    url: String,
    modifier: Modifier = Modifier,
    onComplete: (() -> Unit)? = null,
    onError: ((String) -> Unit)? = null,
) {
    val state = LocalThunderID.current
    BaseCallback(url = url, modifier = modifier, onResult = { result ->
        result
            .onSuccess {
                onComplete?.invoke()
            }.onFailure { e ->
                onError?.invoke(e.message ?: "Callback failed")
            }
    }) { isLoading, error ->
        when {
            isLoading -> {
                BasicText(state.i18n.resolve("callback.loading"))
            }

            error != null -> {
                BasicText(error)
            }

            else -> {}
        }
    }
}

/** Unstyled base variant (spec §8.3). */
@Composable
fun BaseCallback(
    url: String,
    modifier: Modifier = Modifier,
    onResult: (Result<Unit>) -> Unit,
    content: @Composable (isLoading: Boolean, error: String?) -> Unit,
) {
    val state = LocalThunderID.current
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(url) {
        try {
            state.client.handleRedirectCallback(url)
            state.refresh()
            isLoading = false
            onResult(Result.success(Unit))
        } catch (e: Exception) {
            error = e.message
            isLoading = false
            onResult(Result.failure(e))
        }
    }

    content(isLoading, error)
}
