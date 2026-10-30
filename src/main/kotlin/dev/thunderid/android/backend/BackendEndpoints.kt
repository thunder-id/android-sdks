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
 * sign-in does not run on a backend. The config's `baseUrl` is the backend: a relative endpoint resolves
 * against it, and an absolute one is used as is. The API base is `endpoints.backend.apiBaseUrl`, or
 * `baseUrl` when it is not set.
 *
 * Fails with [ThunderIDErrorCode.INVALID_CONFIGURATION], before any network call, when `baseUrl`, the API
 * base, an absolute endpoint or a bypass URL does not use HTTPS, or when an endpoint is neither a path nor
 * an absolute URL.
 */
internal fun resolveBackendEndpoints(config: ThunderIDConfig): ResolvedBackendEndpoints? {
    val endpoints = config.endpoints.backend ?: return null
    val baseUrl = config.baseUrl.trimEnd('/')
    if (!isHttpsUrl(baseUrl)) {
        throw IAMException(ThunderIDErrorCode.INVALID_CONFIGURATION, "baseUrl must be an HTTPS URL in backend mode")
    }
    val apiBaseUrl = endpoints.apiBaseUrl?.trimEnd('/') ?: baseUrl
    if (!isHttpsUrl(apiBaseUrl)) {
        throw IAMException(ThunderIDErrorCode.INVALID_CONFIGURATION, "endpoints.backend.apiBaseUrl must be an HTTPS URL")
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
        return when {
            value.startsWith("/") && !value.startsWith("//") -> baseUrl + value

            isHttpsUrl(value) -> value

            else -> throw IAMException(
                ThunderIDErrorCode.INVALID_CONFIGURATION,
                "endpoints.backend.$name must be a path under baseUrl or an HTTPS URL",
            )
        }
    }

    return ResolvedBackendEndpoints(
        apiBaseUrl = apiBaseUrl,
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
 * @property scope The scopes the access token was granted, space separated, as the token response's `scope`
 *   (RFC 6749 §5.1). The app reads its permissions from here, so the token can stay on the backend.
 */
internal data class BackendSession(
    val isSignedIn: Boolean,
    val user: Map<String, Any>? = null,
    val expiresAt: Double? = null,
    val scope: String? = null,
) {
    companion object {
        val SIGNED_OUT = BackendSession(isSignedIn = false)
    }
}
