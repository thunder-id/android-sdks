// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.management

import dev.thunderid.android.ThunderIDFetcher

/**
 * OpenID4VCI credential configuration operations, reached through `ThunderIDClient.credentialConfigurations`,
 * against the `openid4vci/credential-configurations` collection. Each call accepts an optional
 * [ThunderIDFetcher] that takes precedence over `ThunderIDConfig.http.fetcher`.
 */
class CredentialConfigurationsClient internal constructor(
    private val transport: ManagementTransport,
) {
    /** Returns every credential configuration, as summaries. */
    suspend fun list(fetcher: ThunderIDFetcher? = null): List<CredentialConfigurationSummary> =
        transport.request<Array<CredentialConfigurationSummary>>("GET", fetcher = fetcher).toList()

    /** Returns a single credential configuration. */
    suspend fun get(
        id: String,
        fetcher: ThunderIDFetcher? = null,
    ): CredentialConfiguration = transport.get(id, fetcher = fetcher)

    /** Creates a credential configuration and returns it as the server stored it. */
    suspend fun create(
        configuration: CredentialConfigurationRequest,
        fetcher: ThunderIDFetcher? = null,
    ): CredentialConfiguration = transport.create(configuration, fetcher)

    /** Replaces a credential configuration and returns it. A declarative configuration cannot be changed. */
    suspend fun update(
        id: String,
        configuration: CredentialConfigurationRequest,
        fetcher: ThunderIDFetcher? = null,
    ): CredentialConfiguration = transport.update(id, configuration, fetcher)

    /** Deletes a credential configuration. Succeeds whether or not it existed. */
    suspend fun delete(
        id: String,
        fetcher: ThunderIDFetcher? = null,
    ) = transport.delete(id, fetcher)
}
