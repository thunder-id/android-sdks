// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.management

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.management.CapabilitiesState

internal enum class CapabilityGuardOutcome { LOADING, ERROR, ALLOWED, DENIED }

/** Loading is checked first, then a failed read, then whether any of [anyOf] is allowed. */
internal fun capabilityGuardOutcome(
    capabilities: CapabilitiesState,
    anyOf: List<Pair<String, String>>,
): CapabilityGuardOutcome =
    when {
        capabilities.isLoading -> CapabilityGuardOutcome.LOADING
        capabilities.error != null -> CapabilityGuardOutcome.ERROR
        anyOf.any { (resource, action) -> capabilities.can(resource, action) } -> CapabilityGuardOutcome.ALLOWED
        else -> CapabilityGuardOutcome.DENIED
    }

/** The root's [CapabilitiesState], read on first use. */
@Composable
internal fun rememberCapabilities(): CapabilitiesState {
    val capabilities = LocalThunderID.current.capabilities
    LaunchedEffect(capabilities) { capabilities.load() }
    return capabilities
}

/**
 * Renders [content] when the server reports the signed-in user may perform [action] on [resource], and [denied]
 * otherwise. [loading] renders while the capabilities load and [error] when they cannot be read, so the app can
 * say they are unavailable rather than that the user lacks them; retry with
 * `LocalThunderID.current.capabilities.retry()`. Every fallback renders nothing by default.
 *
 * It decides what to offer, not what is allowed: it is not an authorization decision. The server authorizes
 * every request, and the SDK never blocks one because of capabilities.
 */
@Composable
fun CapabilityGuard(
    resource: String,
    action: String = "view",
    denied: @Composable () -> Unit = {},
    loading: @Composable () -> Unit = {},
    error: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    CapabilityGuard(
        anyOf = listOf(resource to action),
        denied = denied,
        loading = loading,
        error = error,
        content = content,
    )
}

/**
 * Renders [content] when the server reports the signed-in user may perform any one of [anyOf], each a
 * `resource to action` pair, and [denied] otherwise. See the single-action overload for the fallbacks.
 */
@Composable
fun CapabilityGuard(
    anyOf: List<Pair<String, String>>,
    denied: @Composable () -> Unit = {},
    loading: @Composable () -> Unit = {},
    error: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    when (capabilityGuardOutcome(rememberCapabilities(), anyOf)) {
        CapabilityGuardOutcome.LOADING -> loading()
        CapabilityGuardOutcome.ERROR -> error()
        CapabilityGuardOutcome.ALLOWED -> content()
        CapabilityGuardOutcome.DENIED -> denied()
    }
}
