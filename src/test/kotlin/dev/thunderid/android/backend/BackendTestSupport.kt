// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.backend

import dev.thunderid.android.InMemoryStorageAdapter
import dev.thunderid.android.ThunderIDBackendBypass
import dev.thunderid.android.ThunderIDBackendEndpoints
import dev.thunderid.android.ThunderIDClient
import dev.thunderid.android.ThunderIDConfig
import dev.thunderid.android.ThunderIDEndpoints
import dev.thunderid.android.ThunderIDFetcher
import dev.thunderid.android.ThunderIDHttpRequest
import dev.thunderid.android.ThunderIDHttpResponse
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList

internal const val BACKEND = "https://app.example.com"
internal const val API = "https://api.example.com"
internal const val DATA_PLANE = "https://dp.example.com/api"
internal const val REDIRECT_URI = "com.example.app://callback"
internal const val HANDLE_KEY = "thunderid.backend_session_handle"

internal const val SESSION = "$BACKEND/api/auth/session"
internal const val CHECK_SESSION = "$BACKEND/api/auth/checksession"
internal const val REFRESH = "$BACKEND/api/auth/refresh"
internal const val SIGN_OUT = "$BACKEND/api/auth/signout"
internal const val PROXY = "$BACKEND/api/auth/proxy"
internal const val TOKEN = "$BACKEND/api/auth/token"

internal fun response(
    status: Int,
    body: String = "",
) = ThunderIDHttpResponse(status, body)

internal fun backendError(code: String) = response(401, """{"error":"$code"}""")

internal fun tokenResponse(
    accessToken: String,
    expiresIn: Int = 3600,
) = response(200, """{"access_token":"$accessToken","token_type":"Bearer","expires_in":$expiresIn,"scope":"api"}""")

/**
 * Answers each request from a queue of responses for its method and URL, repeating the last one, and
 * records every request it is sent. Unknown routes answer 404.
 */
internal class FakeBackend : ThunderIDFetcher {
    val requests = CopyOnWriteArrayList<ThunderIDHttpRequest>()
    private val routes = mutableMapOf<String, MutableList<suspend () -> ThunderIDHttpResponse>>()

    fun on(
        method: String,
        url: String,
        vararg responses: ThunderIDHttpResponse,
    ) {
        synchronized(routes) { routes["$method $url"] = responses.map { r -> suspend { r } }.toMutableList() }
    }

    fun onSuspending(
        method: String,
        url: String,
        answer: suspend () -> ThunderIDHttpResponse,
    ) {
        synchronized(routes) { routes["$method $url"] = mutableListOf(answer) }
    }

    fun to(url: String): List<ThunderIDHttpRequest> = requests.filter { it.url == url }

    override suspend fun fetch(request: ThunderIDHttpRequest): ThunderIDHttpResponse {
        requests += request
        val queue = synchronized(routes) { routes["${request.method} ${request.url}"] } ?: return response(404)
        val next = synchronized(queue) { if (queue.size > 1) queue.removeAt(0) else queue.first() }
        return next()
    }
}

internal fun backendConfig(
    bypass: ThunderIDBackendBypass = ThunderIDBackendBypass(urls = listOf(DATA_PLANE)),
    backend: ThunderIDBackendEndpoints = ThunderIDBackendEndpoints(baseUrl = BACKEND, bypass = bypass),
) = ThunderIDConfig(
    baseUrl = API,
    afterSignInUrl = REDIRECT_URI,
    afterSignOutUrl = "com.example.app://signed-out",
    endpoints = ThunderIDEndpoints(backend = backend),
)

/** The S256 PKCE challenge for [verifier]. */
internal fun challengeOf(verifier: String): String =
    Base64
        .getUrlEncoder()
        .withoutPadding()
        .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

internal fun queryOf(url: String): Map<String, String> =
    url.substringAfter('?').split('&').associate {
        java.net.URLDecoder.decode(it.substringBefore('='), "UTF-8") to
            java.net.URLDecoder.decode(it.substringAfter('='), "UTF-8")
    }

/** Initializes [client] and signs it in through the backend, which hands over the handle `h-1`. */
internal suspend fun signIn(
    client: ThunderIDClient,
    fake: FakeBackend,
    storage: InMemoryStorageAdapter,
    config: ThunderIDConfig = backendConfig(),
) {
    fake.on("POST", SESSION, response(200, """{"handle":"h-1","isSignedIn":true,"user":{"sub":"u1"},"expiresAt":1}"""))
    client.initialize(config, storage)
    client.buildSignInUrl()
    client.handleRedirectCallback("$REDIRECT_URI?session_code=one-time")
}
