// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.backend

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import dev.thunderid.android.IAMException
import dev.thunderid.android.ThunderIDErrorCode
import dev.thunderid.android.ThunderIDFetcher
import dev.thunderid.android.ThunderIDHttpRequest
import dev.thunderid.android.ThunderIDHttpResponse
import dev.thunderid.android.TokenResponse
import dev.thunderid.android.http.HttpClient
import dev.thunderid.android.token.TokenStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.URLEncoder

/** Header every backend call carries the session handle in. */
internal const val SESSION_HANDLE_HEADER = "X-Session-Handle"

private const val SESSION_EXPIRED = "session_expired"
private const val LOGIN_REQUIRED = "login_required"

/** A check on returning to the foreground is skipped when the last one is more recent than this. */
private const val FOREGROUND_CHECK_THROTTLE_MS = 5_000L

/** The access token for bypassed requests is requested again this long before it expires. */
private const val TOKEN_RENEWAL_MARGIN_SECONDS = 30L

/**
 * Talks to the app's backend when sign-in runs there: exchanges the one-time code the Custom Tab
 * returns for a session handle, reads the session, refreshes it, ends it, and sends the app's API
 * calls through the proxy. The app never holds the session's tokens; the handle stands for them.
 *
 * @param onSessionEnded Called when a signed-in session turns out to have ended without this client
 * signing out: another app signed the user out, an administrator ended the session, or it timed out.
 */
internal class BackendClient(
    private val endpoints: ResolvedBackendEndpoints,
    private val handles: BackendSessionStore,
    private val tokens: TokenStore,
    private val http: HttpClient,
    private val onSessionEnded: () -> Unit,
) {
    private val sessionMutex = Mutex()
    private val refreshing = SharedRequest<Boolean>()
    private val tokenRequest = SharedRequest<String>()

    @Volatile private var session: BackendSession? = null

    @Volatile private var hasLoadedSession = false

    /** Whether the session last seen was signed in, so a later read can tell it has ended. */
    @Volatile private var wasSignedIn = false

    @Volatile private var lastCheck = 0L

    /** Whether the first session read is still to settle, so it is not yet known whether the user is signed in. */
    fun isLoading(): Boolean = !hasLoadedSession

    /** The URL that starts sign-in at the backend, bound to this client by [challenge]. */
    fun signInUrl(
        redirectUri: String,
        challenge: String,
    ): String {
        val query =
            listOf(
                "redirect_uri" to redirectUri,
                "code_challenge" to challenge,
                "code_challenge_method" to "S256",
            ).joinToString("&") { (name, value) -> "$name=${encode(value)}" }
        return "${endpoints.signIn}${if (endpoints.signIn.contains('?')) "&" else "?"}$query"
    }

    /**
     * Exchanges the one-time [code] the Custom Tab returned, with the PKCE [verifier] of the sign-in
     * that asked for it, for a session handle, which goes in the secure store.
     */
    suspend fun startSession(
        code: String,
        verifier: String,
    ): BackendSession {
        // A new sign-in replaces whatever this client held before.
        clear()
        val body = Gson().toJson(mapOf("session_code" to code, "code_verifier" to verifier))
        val response = call("POST", endpoints.session, body)
        if (response.statusCode !in 200..299) {
            throw IAMException(
                ThunderIDErrorCode.AUTHENTICATION_FAILED,
                "The backend refused the sign-in code (${response.statusCode})",
            )
        }
        val json = parseObject(response.body)
        val handle = json?.string("handle")
        val started = json?.let { parseSession(it) }
        if (started == null || !started.isSignedIn || handle.isNullOrEmpty()) {
            throw IAMException(ThunderIDErrorCode.AUTHENTICATION_FAILED, "The backend returned no session")
        }
        handles.save(handle)
        wasSignedIn = true
        sessionMutex.withLock {
            session = started
            hasLoadedSession = true
        }
        return started
    }

    /**
     * Returns the session. The result is cached until a sign-in, sign-out, or refresh changes it, so
     * repeated sign-in checks do not reach the backend each time.
     *
     * @param reload Read the session from the backend even when one is cached.
     */
    suspend fun getSession(reload: Boolean = false): BackendSession {
        if (!reload) session?.let { return it }
        return sessionMutex.withLock {
            if (!reload) session?.let { return@withLock it }
            try {
                loadSession().also { session = it }
            } finally {
                hasLoadedSession = true
            }
        }
    }

    /**
     * Asks the backend whether the session is still live. The check neither refreshes the session nor
     * counts as activity, so it can be polled without keeping an idle session alive.
     *
     * @return Whether the session is live. A failed check reports the last known state.
     */
    suspend fun checkSession(): Boolean {
        lastCheck = System.currentTimeMillis()
        if (handles.handle() == null) return false
        val response =
            try {
                call("GET", endpoints.checkSession)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return wasSignedIn
            }
        if (response.statusCode !in 200..299) return wasSignedIn
        val isSignedIn = parseObject(response.body)?.get("isSignedIn")?.takeIf { it.isJsonPrimitive }?.asBoolean
        if (isSignedIn == false) markSignedOut()
        return isSignedIn ?: wasSignedIn
    }

    /**
     * Watches for the session ending while the app is in the foreground: checks it now, unless a check
     * ran in the last few seconds, and then every [intervalSeconds] when that is positive. Runs until
     * the caller's coroutine is cancelled, so run it under `repeatOnLifecycle(Lifecycle.State.STARTED)`.
     */
    suspend fun watch(intervalSeconds: Int) {
        if (wasSignedIn && System.currentTimeMillis() - lastCheck >= FOREGROUND_CHECK_THROTTLE_MS) {
            checkSession()
        }
        if (intervalSeconds <= 0) return
        while (true) {
            delay(intervalSeconds * 1000L)
            if (wasSignedIn) checkSession()
        }
    }

    /**
     * Refreshes the session once, sharing one request between concurrent callers.
     *
     * @return Whether the session was refreshed. When it was not, the session is now signed out.
     */
    suspend fun refresh(): Boolean =
        refreshing.run {
            if (handles.handle() == null) return@run false
            val response = call("POST", endpoints.refresh)
            if (response.statusCode in 200..299) {
                session = null
                true
            } else {
                markSignedOut()
                false
            }
        }

    /**
     * Ends the backend session. Local state is cleared whether or not the backend could be reached, and
     * a failure is still reported. Signing out here is not news to anyone, so [onSessionEnded] is not
     * called.
     *
     * @return The URL that ends the session at the authorization server, or `null` when there was no
     * session or the backend returned none.
     */
    suspend fun signOut(): String? {
        if (handles.handle() == null) {
            clear()
            return null
        }
        val response =
            try {
                call("POST", endpoints.signOut)
            } finally {
                clear()
            }
        // A 401 means the backend has no such session, which is what signing out asks for.
        if (response.statusCode == 401) return null
        if (response.statusCode !in 200..299) {
            throw IAMException(
                ThunderIDErrorCode.SERVER_ERROR,
                "The backend sign-out endpoint returned ${response.statusCode}",
            )
        }
        return parseObject(response.body)?.string("redirectUrl")
    }

    /**
     * Forgets the session, the handle, and the bypass access token without calling [onSessionEnded]:
     * the caller already knows.
     */
    fun clear() {
        wasSignedIn = false
        session = BackendSession.SIGNED_OUT
        handles.clear()
        tokens.clear()
    }

    /**
     * Records that the session has ended, removes the handle and the bypass access token, and calls
     * [onSessionEnded] when it had been signed in.
     */
    fun markSignedOut() {
        val hadSession = wasSignedIn
        clear()
        hasLoadedSession = true
        if (hadSession) onSessionEnded()
    }

    /**
     * Sends an API request: straight to its URL when it is under a bypass URL, otherwise through the
     * proxy with the session handle. Only a backend `401` is acted on: `session_expired` refreshes the
     * session once and retries, and a second failure signs out; `login_required` signs out. Any other
     * response, `401` included, is the API's own answer and comes back untouched.
     *
     * @throws IAMException [ThunderIDErrorCode.INVALID_INPUT] when the URL is neither under the API base
     * nor under a bypass URL. Nothing is sent: sending it would turn an authenticated call into an
     * anonymous one.
     */
    suspend fun send(
        request: ThunderIDHttpRequest,
        fetcher: ThunderIDFetcher? = null,
    ): ThunderIDHttpResponse {
        if (isBypassed(request.url)) return sendBypassed(request, fetcher)
        val proxyUrl =
            toProxyUrl(request.url)
                ?: throw IAMException(
                    ThunderIDErrorCode.INVALID_INPUT,
                    "The request URL is not under the API base or a bypass URL, so it was not sent",
                )
        val headers = withoutCredentials(request.headers)

        suspend fun send() = http.exchange(request.copy(url = proxyUrl, headers = withHandle(headers)), fetcher)

        val response = send()
        if (response.statusCode != 401) return response
        return when (errorCode(response)) {
            SESSION_EXPIRED -> {
                if (!refresh()) return response
                send().also { retried ->
                    if (retried.statusCode == 401 && errorCode(retried) in setOf(SESSION_EXPIRED, LOGIN_REQUIRED)) {
                        markSignedOut()
                    }
                }
            }

            LOGIN_REQUIRED -> {
                markSignedOut()
                response
            }

            else -> {
                response
            }
        }
    }

    /**
     * The access token for requests under a bypass URL. Like a client credentials token it has no
     * refresh token: it is kept in the token store until it nears expiry, then requested again, with
     * concurrent callers sharing one request.
     *
     * @param renew Request a new token even when the stored one is still valid.
     */
    suspend fun getAccessToken(renew: Boolean = false): String {
        if (!renew) {
            val stored = tokens.accessToken()
            if (stored != null && !tokens.isNearExpiry(TOKEN_RENEWAL_MARGIN_SECONDS)) return stored
        }
        return tokenRequest.run { requestAccessToken() }
    }

    /** Maps an API URL to its proxy URL, or returns `null` when it is not under the API base. */
    fun toProxyUrl(url: String): String? {
        val base = endpoints.apiBaseUrl
        val rest =
            when {
                isUnder(url, base) -> url.substring(base.length)
                url.startsWith("/") && !url.startsWith("//") -> url
                else -> return null
            }
        return endpoints.proxy + if (rest.startsWith("/")) rest else "/$rest"
    }

    /** Whether a request to [url] skips the proxy and goes straight from the app. */
    fun isBypassed(url: String): Boolean = endpoints.bypassUrls.any { isUnder(url, it) }

    private suspend fun sendBypassed(
        request: ThunderIDHttpRequest,
        fetcher: ThunderIDFetcher?,
    ): ThunderIDHttpResponse {
        val headers = withoutCredentials(request.headers)
        if (!endpoints.attachToken) return http.exchange(request.copy(headers = headers), fetcher)

        suspend fun send(renew: Boolean): ThunderIDHttpResponse {
            val token = getAccessToken(renew)
            return http.exchange(request.copy(headers = headers + ("Authorization" to "Bearer $token")), fetcher)
        }

        val response = send(renew = false)
        // The API rejected the token, so get a new one and retry once. A second 401 is the API's answer.
        return if (response.statusCode == 401) send(renew = true) else response
    }

    private suspend fun requestAccessToken(): String {
        if (handles.handle() == null) {
            throw IAMException(ThunderIDErrorCode.AUTHENTICATION_FAILED, "Not signed in")
        }
        var response = call("POST", endpoints.token)
        if (response.statusCode == 401) {
            when (errorCode(response)) {
                SESSION_EXPIRED -> if (refresh()) response = call("POST", endpoints.token)
                LOGIN_REQUIRED -> markSignedOut()
            }
        }
        if (response.statusCode !in 200..299) {
            throw IAMException(
                if (response.statusCode == 401) ThunderIDErrorCode.SESSION_EXPIRED else ThunderIDErrorCode.SERVER_ERROR,
                "The backend token endpoint returned ${response.statusCode}",
            )
        }
        val json = parseObject(response.body)
        val accessToken = json?.string("access_token")
        if (json == null || accessToken.isNullOrEmpty()) {
            throw IAMException(ThunderIDErrorCode.SERVER_ERROR, "The backend token endpoint returned no token")
        }
        // Only the access token is kept. The backend keeps the session's refresh and ID tokens.
        tokens.save(
            TokenResponse(
                accessToken = accessToken,
                tokenType = json.string("token_type") ?: "Bearer",
                expiresIn = json.get("expires_in")?.takeIf { it.isJsonPrimitive }?.asInt,
                scope = json.string("scope"),
            ),
        )
        return accessToken
    }

    private suspend fun loadSession(): BackendSession {
        if (handles.handle() == null) return BackendSession.SIGNED_OUT
        var response = call("GET", endpoints.session)
        if (response.statusCode == 401 && errorCode(response) == SESSION_EXPIRED && refresh()) {
            response = call("GET", endpoints.session)
        }
        if (response.statusCode == 401) return signedOutSession()
        if (response.statusCode !in 200..299) {
            throw IAMException(
                ThunderIDErrorCode.SERVER_ERROR,
                "The backend session endpoint returned ${response.statusCode}",
            )
        }
        val loaded =
            parseObject(response.body)?.let { parseSession(it) }
                ?: throw IAMException(ThunderIDErrorCode.SERVER_ERROR, "The backend returned an unreadable session")
        if (!loaded.isSignedIn) return signedOutSession()
        wasSignedIn = true
        return loaded
    }

    private fun signedOutSession(): BackendSession {
        markSignedOut()
        return BackendSession.SIGNED_OUT
    }

    private suspend fun call(
        method: String,
        url: String,
        body: String? = null,
    ): ThunderIDHttpResponse {
        val headers =
            buildMap {
                put("Accept", "application/json")
                if (body != null) put("Content-Type", "application/json")
            }
        return http.exchange(ThunderIDHttpRequest(method, url, withHandle(headers), body))
    }

    private fun withHandle(headers: Map<String, String>): Map<String, String> =
        handles.handle()?.let { headers + (SESSION_HANDLE_HEADER to it) } ?: headers

    /** Drops any credential the caller set, so the SDK alone decides what each request carries. */
    private fun withoutCredentials(headers: Map<String, String>): Map<String, String> =
        headers.filterKeys {
            !it.equals("Authorization", ignoreCase = true) && !it.equals(SESSION_HANDLE_HEADER, ignoreCase = true)
        }

    private fun parseSession(json: JsonObject): BackendSession {
        val user =
            json.get("user")?.takeIf { it.isJsonObject }?.let {
                Gson().fromJson<Map<String, Any>>(it, object : TypeToken<Map<String, Any>>() {}.type)
            }
        return BackendSession(
            isSignedIn = json.get("isSignedIn")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false,
            user = user,
            expiresAt = json.get("expiresAt")?.takeIf { it.isJsonPrimitive }?.asDouble,
        )
    }

    private fun parseObject(body: String): JsonObject? =
        runCatching { JsonParser.parseString(body) }.getOrNull()?.takeIf { it.isJsonObject }?.asJsonObject

    private fun errorCode(response: ThunderIDHttpResponse): String? = parseObject(response.body)?.string("error")

    private fun JsonObject.string(name: String): String? = get(name)?.takeIf { it.isJsonPrimitive }?.asString

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    /** Whether [url] is [base] itself or under it, at a path or query boundary. */
    private fun isUnder(
        url: String,
        base: String,
    ): Boolean = url == base || url.startsWith("$base/") || url.startsWith("$base?")
}

/**
 * Runs one request at a time and hands its result to every caller that asks while it is in flight.
 */
internal class SharedRequest<T> {
    private var inFlight: CompletableDeferred<T>? = null

    suspend fun run(block: suspend () -> T): T {
        val (deferred, owner) =
            synchronized(this) {
                inFlight?.let { it to false } ?: CompletableDeferred<T>().also { inFlight = it }.let { it to true }
            }
        if (owner) {
            try {
                deferred.complete(block())
            } catch (e: Throwable) {
                deferred.completeExceptionally(e)
            } finally {
                synchronized(this) { inFlight = null }
            }
        }
        return deferred.await()
    }
}
