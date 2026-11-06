// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.management

import dev.thunderid.android.ThunderIDFetcher

/**
 * RFC 9396 authorization details type management operations, reached through
 * `ThunderIDClient.authorizationDetailTypes`, against the `authorization-detail-types` collection. The types a
 * client may request are discovered with `ThunderIDClient.getSupportedAuthorizationDetailsTypes` instead. Each
 * call accepts an optional [ThunderIDFetcher] that takes precedence over `ThunderIDConfig.http.fetcher`.
 */
class AuthorizationDetailTypesClient internal constructor(
    private val transport: ManagementTransport,
) {
    /** Returns a page of authorization details types. */
    suspend fun list(
        limit: Int? = null,
        offset: Int? = null,
        fetcher: ThunderIDFetcher? = null,
    ): AuthorizationDetailTypeListResponse = transport.list(page(limit, offset), fetcher)

    /** Returns a single authorization details type, by its [id] (not its `type` value). */
    suspend fun get(
        id: String,
        fetcher: ThunderIDFetcher? = null,
    ): AuthorizationDetailType = transport.get(id, fetcher = fetcher)

    /** Registers an authorization details type and returns it as the server stored it. */
    suspend fun create(
        type: AuthorizationDetailTypeRequest,
        fetcher: ThunderIDFetcher? = null,
    ): AuthorizationDetailType = transport.create(type, fetcher)

    /** Replaces an authorization details type and returns it. A read-only type cannot be updated. */
    suspend fun update(
        id: String,
        type: AuthorizationDetailTypeRequest,
        fetcher: ThunderIDFetcher? = null,
    ): AuthorizationDetailType = transport.update(id, type, fetcher)

    /** Deletes an authorization details type. A read-only type cannot be deleted. */
    suspend fun delete(
        id: String,
        fetcher: ThunderIDFetcher? = null,
    ) = transport.delete(id, fetcher)
}
