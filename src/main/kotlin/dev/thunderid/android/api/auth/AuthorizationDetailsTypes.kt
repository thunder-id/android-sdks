// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.auth

import dev.thunderid.android.IAMException
import dev.thunderid.android.ThunderIDErrorCode
import dev.thunderid.android.ThunderIDFetcher
import dev.thunderid.android.http.HttpClient
import java.net.URLEncoder

/**
 * The request view of an RFC 9396 authorization details type: what a client may send in
 * `authorization_details` for it, and how it may change a granted detail at the token endpoint.
 *
 * @property type The `type` value a client sends.
 * @property version Increments when a change affects earlier approvals and grants.
 * @property schema The JSON Schema 2020-12 of the fields a client may send.
 */
data class SupportedAuthorizationDetailsType(
    val type: String,
    val name: String,
    val description: String? = null,
    val version: Int? = null,
    val schema: Map<String, Any?> = emptyMap(),
    val modifications: List<AuthorizationDetailsModification> = emptyList(),
)

/**
 * A change a client may make to a granted detail at the token endpoint: [field] is a dot-separated path, and
 * [modification] is `decrease`, `increase`, `removeItems` or `addItems`.
 */
data class AuthorizationDetailsModification(
    val field: String,
    val modification: String,
)

private data class SupportedAuthorizationDetailsTypesResponse(
    val types: List<SupportedAuthorizationDetailsType>? = null,
)

/**
 * Reads the public authorization details type discovery at `{baseUrl}/oauth2/authorization-details-types`. The
 * endpoint needs no token, and is not served when the server turns type metadata off.
 */
internal class AuthorizationDetailsTypesClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val fetcher: ThunderIDFetcher?,
) {
    suspend fun list(fetcher: ThunderIDFetcher?): List<SupportedAuthorizationDetailsType> =
        fetch<SupportedAuthorizationDetailsTypesResponse>(PATH, fetcher).types.orEmpty()

    suspend fun get(
        type: String,
        fetcher: ThunderIDFetcher?,
    ): SupportedAuthorizationDetailsType {
        if (type.isBlank()) {
            throw IAMException(ThunderIDErrorCode.INVALID_INPUT, "An authorization details type is required")
        }
        val encoded = URLEncoder.encode(type, Charsets.UTF_8.name()).replace("+", "%20")
        return fetch("$PATH/$encoded", fetcher)
    }

    private suspend inline fun <reified T : Any> fetch(
        path: String,
        fetcher: ThunderIDFetcher?,
    ): T {
        val url = baseUrl.trimEnd('/') + path
        if (!url.startsWith("https://")) {
            throw IAMException(ThunderIDErrorCode.INVALID_CONFIGURATION, "baseUrl must use HTTPS")
        }
        val body = httpClient.execute("GET", url, null, false, emptyMap(), fetcher ?: this.fetcher)
        return httpClient.parseResponse(body)
    }

    private companion object {
        const val PATH = "/oauth2/authorization-details-types"
    }
}
