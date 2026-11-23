// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
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
    val scope = rememberCoroutineScope()
    val state = remember(client, resolvedI18n) { ThunderIDState(client, resolvedI18n, scope) }

    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(config) {
        state.initialize(config)
        // When sign-in runs on a backend, check the session each time the app comes to the foreground,
        // and every `session.checkInterval` seconds while it stays there.
        if (state.isInitialized && client.isBackendMode()) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { client.watchSession() }
        }
    }

    // Telemetry waiting to be sent goes when the app moves to the background.
    DisposableEffect(lifecycleOwner, client) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) client.flushTelemetry()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Links opened through the URI handler, the SDK components' included, are reported as `link.open`.
    val parentUriHandler = LocalUriHandler.current
    val uriHandler =
        remember(client, parentUriHandler) { TelemetryUriHandler.reportingTo(client, parentUriHandler) }

    CompositionLocalProvider(LocalThunderID provides state, LocalUriHandler provides uriHandler) {
        content()
    }
}
