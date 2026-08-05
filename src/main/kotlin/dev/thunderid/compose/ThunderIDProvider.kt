// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.thunderid.android.ThunderIDClient
import dev.thunderid.android.ThunderIDConfig
import dev.thunderid.compose.i18n.ThunderIDI18n

/**
 * Provides ThunderID auth state to all descendant composables via [LocalThunderID] (spec §7.2).
 *
 * ```kotlin
 * ThunderIDProvider(config = ThunderIDConfig(baseUrl = "...", clientId = "...")) {
 *     MyApp()
 * }
 * ```
 */
@Composable
fun ThunderIDProvider(
    config: ThunderIDConfig,
    client: ThunderIDClient = remember { ThunderIDClient() },
    i18n: ThunderIDI18n? = null,
    content: @Composable () -> Unit,
) {
    val resolvedI18n =
        i18n ?: remember(config.vendor) { ThunderIDI18n(storageKey = "${config.vendor}_locale") }
    val state = remember(client, resolvedI18n) { ThunderIDState(client, resolvedI18n) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(config) {
        state.initialize(config)
    }

    CompositionLocalProvider(LocalThunderID provides state) {
        content()
    }
}
