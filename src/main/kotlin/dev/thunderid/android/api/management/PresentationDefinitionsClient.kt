// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.management

import dev.thunderid.android.ThunderIDFetcher

/**
 * OpenID4VP presentation definition operations, reached through `ThunderIDClient.presentationDefinitions`,
 * against the `openid4vp/presentation-definitions` collection. Each call accepts an optional
 * [ThunderIDFetcher] that takes precedence over `ThunderIDConfig.http.fetcher`.
 */
class PresentationDefinitionsClient internal constructor(
    private val transport: ManagementTransport,
) {
    /** Returns every presentation definition, as summaries. */
    suspend fun list(fetcher: ThunderIDFetcher? = null): List<PresentationDefinitionSummary> =
        transport.request<Array<PresentationDefinitionSummary>>("GET", fetcher = fetcher).toList()

    /** Returns a single presentation definition. */
    suspend fun get(
        id: String,
        fetcher: ThunderIDFetcher? = null,
    ): PresentationDefinition = transport.get(id, fetcher = fetcher)

    /** Creates a presentation definition and returns it as the server stored it. */
    suspend fun create(
        definition: PresentationDefinitionRequest,
        fetcher: ThunderIDFetcher? = null,
    ): PresentationDefinition = transport.create(definition, fetcher)

    /** Replaces a presentation definition and returns it. A declarative definition cannot be changed. */
    suspend fun update(
        id: String,
        definition: PresentationDefinitionRequest,
        fetcher: ThunderIDFetcher? = null,
    ): PresentationDefinition = transport.update(id, definition, fetcher)

    /** Deletes a presentation definition. Succeeds whether or not it existed. */
    suspend fun delete(
        id: String,
        fetcher: ThunderIDFetcher? = null,
    ) = transport.delete(id, fetcher)
}
