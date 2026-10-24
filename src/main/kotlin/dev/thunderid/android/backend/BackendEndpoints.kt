// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.backend

import dev.thunderid.android.IAMException
import dev.thunderid.android.ThunderIDConfig
import dev.thunderid.android.ThunderIDErrorCode
import java.net.URI

private const val DEFAULT_BACKEND_BASE_PATH = "/api/auth"

/**
 * The backend endpoints, fully resolved to absolute URLs, with the API base whose requests go through
 * the proxy.
 */
internal data class ResolvedBackendEndpoints(
    val apiBaseUrl: String,
    val signIn: String,
    val session: String,
    val checkSession: String,
    val refresh: String,
    val signOut: String,
    val proxy: String,
    val token: String,
    val bypassUrls: List<String>,
    val attachToken: Boolean,
)

/**
 * Resolves the backend endpoints from `endpoints.backend`, or returns `null` when it is not set and
 * sign-in does not run on a backend. `baseUrl` is the API base, as in the browser SDKs.
 *
 * Fails with [ThunderIDErrorCode.INVALID_CONFIGURATION], before any network call, when the backend
 * base URL or a bypass URL does not use HTTPS, or when an endpoint is not a path: every backend call
 * has to go to the one pinned base.
 */
internal fun resolveBackendEndpoints(config: ThunderIDConfig): ResolvedBackendEndpoints? {
    val endpoints = config.endpoints.backend ?: return null
    val baseUrl = endpoints.baseUrl.trimEnd('/')
    if (!isHttpsUrl(baseUrl)) {
        throw IAMException(ThunderIDErrorCode.INVALID_CONFIGURATION, "endpoints.backend.baseUrl must be an HTTPS URL")
    }
    val bypassUrls = endpoints.bypass.urls.map { it.trimEnd('/') }
    if (bypassUrls.any { !isHttpsUrl(it) }) {
        throw IAMException(ThunderIDErrorCode.INVALID_CONFIGURATION, "endpoints.backend.bypass.urls must be HTTPS URLs")
    }

    fun resolve(
        name: String,
        path: String?,
        default: String,
    ): String {
        val value = path ?: "$DEFAULT_BACKEND_BASE_PATH/$default"
        if (!value.startsWith("/") || value.startsWith("//")) {
            throw IAMException(
                ThunderIDErrorCode.INVALID_CONFIGURATION,
                "endpoints.backend.$name must be a path under endpoints.backend.baseUrl",
            )
        }
        return baseUrl + value
    }

    return ResolvedBackendEndpoints(
        apiBaseUrl = config.baseUrl.trimEnd('/'),
        signIn = resolve("signIn", endpoints.signIn, "signin"),
        session = resolve("session", endpoints.session, "session"),
        checkSession = resolve("checkSession", endpoints.checkSession, "checksession"),
        refresh = resolve("refresh", endpoints.refresh, "refresh"),
        signOut = resolve("signOut", endpoints.signOut, "signout"),
        proxy = resolve("proxy", endpoints.proxy, "proxy").trimEnd('/'),
        token = resolve("token", endpoints.token, "token"),
        bypassUrls = bypassUrls,
        attachToken = endpoints.bypass.attachToken,
    )
}

private fun isHttpsUrl(url: String): Boolean {
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    return uri.scheme == "https" && !uri.host.isNullOrEmpty() && uri.rawQuery == null && uri.rawFragment == null
}

/**
 * What the backend's session endpoint reports.
 *
 * @property user The ID token's user claims, without the protocol claims.
 * @property expiresAt When the access token expires, in seconds since the epoch.
 */
internal data class BackendSession(
    val isSignedIn: Boolean,
    val user: Map<String, Any>? = null,
    val expiresAt: Double? = null,
) {
    companion object {
        val SIGNED_OUT = BackendSession(isSignedIn = false)
    }
}
