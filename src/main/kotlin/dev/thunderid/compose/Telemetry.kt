// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import dev.thunderid.android.ThunderIDClient

/**
 * Reports a screen view each time this enters the composition, named from [pattern]: put it at the top of
 * a screen. [pattern] is the screen's route pattern, such as `/applications/{id}`, never a route with its
 * values filled in. Does nothing unless `telemetry` is configured.
 *
 * ```kotlin
 * @Composable
 * fun ApplicationScreen(id: String) {
 *     TelemetryScreen("/applications/{id}")
 *     // ...
 * }
 * ```
 */
@Composable
fun TelemetryScreen(
    pattern: String,
    client: ThunderIDClient = LocalThunderID.current.client,
) {
    LaunchedEffect(pattern, client) { client.trackPageView(pattern) }
}

/**
 * A [UriHandler] that reports each link it opens as `link.open`, with its host and path only, then hands it
 * to [delegate]. [ThunderIDProvider] installs one as [LocalUriHandler], so links the SDK's components and
 * the app open through [LocalUriHandler] are reported.
 */
class TelemetryUriHandler(
    internal val client: ThunderIDClient,
    internal val delegate: UriHandler,
) : UriHandler {
    override fun openUri(uri: String) {
        client.trackLink(uri)
        delegate.openUri(uri)
    }

    internal companion object {
        /**
         * A handler that reports through [client]: [handler] itself when it already reports through it,
         * otherwise a new one around the URI handler underneath, so a link is reported once and only
         * through the current client.
         */
        fun reportingTo(
            client: ThunderIDClient,
            handler: UriHandler,
        ): TelemetryUriHandler {
            val reporting = handler as? TelemetryUriHandler
            return when {
                reporting == null -> TelemetryUriHandler(client, handler)
                reporting.client === client -> reporting
                else -> TelemetryUriHandler(client, reporting.delegate)
            }
        }
    }
}

/**
 * Returns a function that opens a link that leaves the app and reports it as `link.open`, with its host and
 * path only. Use it for links to docs, support or pricing.
 *
 * ```kotlin
 * val openExternalLink = rememberOpenExternalLink()
 * TextButton(onClick = { openExternalLink("https://docs.example.com/start") }) { Text("Docs") }
 * ```
 */
@Composable
fun rememberOpenExternalLink(): (url: String) -> Unit {
    val handler = LocalUriHandler.current
    val client = LocalThunderID.current.client
    return remember(handler, client) {
        val reporting = TelemetryUriHandler.reportingTo(client, handler)
        val open: (String) -> Unit = { url -> reporting.openUri(url) }
        open
    }
}
