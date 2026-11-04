// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.management

import dev.thunderid.android.ThunderIDFetcher

/**
 * Server configuration operations, reached through `ThunderIDClient.serverConfig`, against the
 * `serverConfig` collection. Each call accepts an optional [ThunderIDFetcher] that takes precedence over
 * `ThunderIDConfig.http.fetcher`.
 */
class ServerConfigClient internal constructor(
    private val transport: ManagementTransport,
) {
    /** Returns the names of the supported configuration sections. */
    suspend fun list(fetcher: ThunderIDFetcher? = null): List<String> = transport.request<Array<String>>("GET", fetcher = fetcher).toList()

    /** Returns the configuration section [name] as its three layers. */
    suspend fun get(
        name: String,
        fetcher: ThunderIDFetcher? = null,
    ): ServerConfigLayers = transport.request("GET", listOf(name), fetcher = fetcher)

    /**
     * Replaces the writable layer of the section [name] with [value] and returns the recomputed layers. The
     * value's shape is section-specific, such as [AccountAccessConfig] for `accountAccess` or
     * [ServerNotificationConfig] for `notification`; an empty value clears the section.
     */
    suspend fun update(
        name: String,
        value: Any,
        fetcher: ThunderIDFetcher? = null,
    ): ServerConfigLayers = transport.request("PUT", listOf(name), body = value, fetcher = fetcher)
}
