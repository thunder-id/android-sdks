// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android

/**
 * An HTTP request handed to a [ThunderIDFetcher]. [headers] already carry the signed-in user's access token.
 */
data class ThunderIDHttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val body: String?,
)

/**
 * The response a [ThunderIDFetcher] returns. The SDK maps non-2xx statuses to an [IAMException].
 */
data class ThunderIDHttpResponse(
    val statusCode: Int,
    val body: String,
)

/**
 * Performs an HTTP request. Supply one to route the management operations through your own transport.
 */
fun interface ThunderIDFetcher {
    suspend fun fetch(request: ThunderIDHttpRequest): ThunderIDHttpResponse
}

/**
 * HTTP options.
 *
 * @property fetcher Transport for the management operations. Applies to management operations only.
 * Defaults to the SDK's built-in HTTP transport. A fetcher passed to an individual call takes precedence
 * over this one.
 */
data class ThunderIDHttpConfig(
    val fetcher: ThunderIDFetcher? = null,
)

/**
 * Collection URL overrides for the management operations, for a management API that runs on a different
 * host from `baseUrl`. A `null` entry falls back to `{baseUrl}/{collection}`. A single resource is addressed
 * as `{collection}/{id}`.
 *
 * @property backend The app backend's auth endpoints. Setting them hands sign-in to a backend the
 * application owns, which keeps the tokens. It names the application's own backend, not a ThunderID
 * endpoint. See [ThunderIDBackendEndpoints].
 */
data class ThunderIDEndpoints(
    val agents: String? = null,
    val applications: String? = null,
    val users: String? = null,
    val backend: ThunderIDBackendEndpoints? = null,
)
