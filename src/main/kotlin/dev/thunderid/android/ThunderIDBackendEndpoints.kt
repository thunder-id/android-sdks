// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android

/**
 * The app backend's auth endpoints, set as [ThunderIDEndpoints.backend].
 *
 * Setting them hands sign-in to a backend the application owns (the Backend for Frontend pattern of
 * RFC 10017): the backend runs the OAuth flow and keeps the tokens, and the app holds only an opaque
 * session handle in the platform's secure store. Requests through [ThunderIDClient.httpRequest], and
 * the SDK's own calls under `baseUrl`, go through the backend's [proxy], which attaches the access
 * token.
 *
 * Each endpoint is a path resolved against [baseUrl]. Any endpoint left unset defaults to a fixed path
 * under `/api/auth`.
 *
 * ```kotlin
 * endpoints = ThunderIDEndpoints(backend = ThunderIDBackendEndpoints(baseUrl = "https://app.example.com"))
 * ```
 *
 * @property baseUrl Base URL of the backend. Must use HTTPS. Every backend call goes to it.
 * @property signIn Starts sign-in, opened in a Custom Tab. Defaults to `/api/auth/signin`.
 * @property session Exchanges the one-time sign-in code for a session handle, and reports the signed-in
 * user. Defaults to `/api/auth/session`.
 * @property checkSession Reports only whether the session is still live, without refreshing it or
 * counting as activity, so the SDK can poll it. Defaults to `/api/auth/checksession`.
 * @property refresh Refreshes the session. Defaults to `/api/auth/refresh`.
 * @property signOut Ends the session. Defaults to `/api/auth/signout`.
 * @property proxy Prefix of the API proxy. Requests under `baseUrl` are sent through it. Defaults to
 * `/api/auth/proxy`.
 * @property token Issues the access token for [bypass], as an OAuth token response without a refresh
 * token. Defaults to `/api/auth/token`.
 * @property bypass Requests that skip the proxy and go straight from the app.
 */
data class ThunderIDBackendEndpoints(
    val baseUrl: String,
    val signIn: String? = null,
    val session: String? = null,
    val checkSession: String? = null,
    val refresh: String? = null,
    val signOut: String? = null,
    val proxy: String? = null,
    val token: String? = null,
    val bypass: ThunderIDBackendBypass = ThunderIDBackendBypass(),
)

/**
 * Requests the app sends directly rather than through the backend's proxy, such as calls to a data
 * plane on another host. They never carry the session handle.
 *
 * ```kotlin
 * bypass = ThunderIDBackendBypass(urls = listOf("https://dp.example.com/api"))
 * ```
 *
 * @property urls Base URLs whose requests skip the proxy. A request matches its own base URL or
 * anything under it. Each must use HTTPS.
 * @property attachToken Attach the access token from the backend's `token` endpoint as
 * `Authorization: Bearer`, and get a new one and retry once on a 401. With `false`, requests go out
 * without a token and the `token` endpoint is never called.
 */
data class ThunderIDBackendBypass(
    val urls: List<String> = emptyList(),
    val attachToken: Boolean = true,
)
