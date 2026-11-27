// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.telemetry

import dev.thunderid.android.IAMException
import dev.thunderid.android.StorageAdapter
import dev.thunderid.android.TelemetryConfig
import dev.thunderid.android.TelemetryToken
import dev.thunderid.android.ThunderIDConfig
import dev.thunderid.android.ThunderIDFetcher
import dev.thunderid.android.ThunderIDHttpRequest
import dev.thunderid.android.ThunderIDHttpResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * The client's telemetry on Android: sends over HTTPS in the background, attributes events to the signed-in
 * user's claims, reports a sign-in once per sign-in session, and decides which token the endpoint gets.
 *
 * @param claims The signed-in user's claims, or `null`. Must be quick: it runs as each event is added.
 * @param accessToken The app's stored access token, or `null`. Read without refreshing it.
 */
internal class ClientTelemetry private constructor(
    private val config: TelemetryConfig,
    private val endpoint: String,
    private val storage: StorageAdapter,
    private val signInKey: String,
    private val claims: () -> Map<String, Any?>?,
    tokenFor: () -> String?,
    apiBases: List<String>,
    fetcher: ThunderIDFetcher?,
    scope: CoroutineScope,
) {
    private val telemetry: Telemetry =
        Telemetry(
            config,
            TelemetryDependencies(
                apiBases = apiBases,
                identity = { identity() },
                token = tokenFor,
                sender = { body, token -> send(body, token, fetcher) },
                os = TelemetryOs("Android", runCatching { android.os.Build.VERSION.RELEASE }.getOrNull()),
                scope = scope,
            ),
        )

    /** The sign-in key of a session without `auth_time` or `sid`, kept for the life of the process only. */
    @Volatile private var signInWithoutSession: String? = null

    /** Whether the end of the current session has been reported, so repeated refresh failures report it once. */
    @Volatile private var expiryReported = false

    fun page(pattern: String) = telemetry.page(pattern)

    fun track(
        name: String,
        data: Map<String, String>,
    ) = telemetry.track(name, data)

    fun link(url: String) = telemetry.link(url)

    fun flush(): Job? = telemetry.flush()

    /** Stops for good: cancels the flush timer and drops the events waiting. A send already started goes on. */
    fun stop() = telemetry.stop()

    /**
     * Sees a request through the SDK's HTTP client and how it ended: [response] is `null` without one. Only
     * the API's own name for the call is read from the response headers, and the body only for a failure's
     * error code; neither body is ever sent.
     */
    fun api(
        request: ThunderIDHttpRequest,
        response: ThunderIDHttpResponse?,
    ) {
        val event =
            response
                ?.headers
                ?.entries
                ?.firstOrNull { it.key.equals(Telemetry.TELEMETRY_EVENT_HEADER, ignoreCase = true) }
                ?.value
        telemetry.api(request.method, request.url, request.body, response?.statusCode, response?.body, event)
    }

    /**
     * Reports `auth.sign-in` once per sign-in session, keyed by the user and the ID token's `auth_time` or
     * `sid`. A token refresh, a restored session or an organization switch does not call this; a repeated
     * call for the same session reports nothing.
     */
    fun signedIn() {
        // Reading the claims can fail, such as when storage throws; telemetry never fails the sign-in.
        val current = runCatching { claims() }.getOrNull() ?: return
        val user = current[config.identity.userClaim] as? String ?: return
        expiryReported = false
        val session = sessionMarker(current["auth_time"]) ?: sessionMarker(current["sid"])
        val key = "$user:${session.orEmpty()}"
        if (session == null) {
            if (signInWithoutSession == key) return
            signInWithoutSession = key
        } else {
            val stored = runCatching { storage.retrieve(signInKey) }.getOrNull()
            if (stored == key) return
            runCatching { storage.store(signInKey, key) }
        }
        telemetry.auth(TelemetryAuthAction.SIGN_IN)
    }

    /** Reports a failed sign-in with its error code only, never the message. */
    fun signInFailed(error: Throwable) {
        telemetry.auth(TelemetryAuthAction.SIGN_IN_FAILED, errorData(error))
    }

    /** Reports a failed sign-in step that ended with the server's [code]. */
    fun signInFailed(code: String?) {
        telemetry.auth(TelemetryAuthAction.SIGN_IN_FAILED, errorCodeData(code))
    }

    /**
     * Reports the sign-out and sends what is waiting, taking the endpoint's token now, before the session is
     * cleared. Does not wait for the send.
     */
    fun signedOut() {
        telemetry.auth(TelemetryAuthAction.SIGN_OUT)
        telemetry.flush()
        forgetSignIn()
    }

    /** Reports that the session ended without a sign-out: its refresh was refused, or the backend ended it. */
    fun sessionExpired() {
        if (expiryReported) return
        expiryReported = true
        telemetry.auth(TelemetryAuthAction.SESSION_EXPIRED)
        forgetSignIn()
    }

    private fun forgetSignIn() {
        signInWithoutSession = null
        runCatching { storage.delete(signInKey) }
    }

    private fun identity(): TelemetryIdentity {
        val current = claims() ?: return TelemetryIdentity()
        return TelemetryIdentity(
            userId = current[config.identity.userClaim] as? String,
            organizationId = current[config.identity.organizationClaim] as? String,
        )
    }

    private suspend fun send(
        body: String,
        token: String?,
        fetcher: ThunderIDFetcher?,
    ) {
        val headers =
            buildMap {
                put("Content-Type", "application/json")
                token?.let { put("Authorization", "Bearer $it") }
            }
        if (fetcher != null) {
            fetcher.fetch(ThunderIDHttpRequest("POST", endpoint, headers, body))
            return
        }
        withContext(Dispatchers.IO) {
            val connection = URL(endpoint).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                // A redirect could carry the token to another host.
                connection.instanceFollowRedirects = false
                connection.requestMethod = "POST"
                headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                connection.responseCode
            } finally {
                connection.disconnect()
            }
        }
    }

    companion object {
        private const val TIMEOUT_MS = 10_000

        /**
         * Builds the telemetry for [thunderConfig], or returns `null` when it is off: no `telemetry`, a blank
         * endpoint, or one that is not an absolute HTTPS URL. A refused endpoint never fails initialization.
         *
         * @param apiBases Where the SDK's own API calls go: `baseUrl`, or the API base in backend mode, and the
         * bases of management collections configured on another host.
         * @param backendMode Whether a backend holds the tokens, so the app has none to send.
         */
        fun create(
            thunderConfig: ThunderIDConfig,
            apiBases: List<String>,
            backendMode: Boolean,
            storage: StorageAdapter,
            claims: () -> Map<String, Any?>?,
            accessToken: () -> String?,
            fetcher: ThunderIDFetcher? = null,
            scope: CoroutineScope? = null,
        ): ClientTelemetry? {
            val config = thunderConfig.telemetry ?: return null
            val endpoint = config.endpoint.trim()
            if (endpoint.isEmpty()) return null
            val endpointUri = TelemetryApiRules.parseAbsolute(endpoint)
            if (endpointUri == null || !endpointUri.scheme.equals("https", ignoreCase = true)) {
                debug(thunderConfig.vendor, "Telemetry is off: telemetry.endpoint must be an absolute HTTPS URL")
                return null
            }
            val tokenFor: () -> String? = {
                when (val token = config.token) {
                    is TelemetryToken.Value -> {
                        token.token.takeIf { it.isNotEmpty() }
                    }

                    TelemetryToken.None -> {
                        null
                    }

                    TelemetryToken.App -> {
                        val base = TelemetryApiRules.parseAbsolute(thunderConfig.baseUrl)
                        val sameOrigin = base != null && TelemetryApiRules.origin(base) == TelemetryApiRules.origin(endpointUri)
                        if (!backendMode && sameOrigin) accessToken() else null
                    }
                }
            }
            val instance = thunderConfig.instanceId?.let { "instance_$it-" } ?: ""
            return ClientTelemetry(
                config = config,
                endpoint = endpoint,
                storage = storage,
                signInKey = "${thunderConfig.vendor}.${instance}telemetry_sign_in",
                claims = claims,
                tokenFor = tokenFor,
                apiBases = apiBases,
                fetcher = fetcher,
                scope = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO),
            )
        }

        /** The error code of a failure: the server's, when it has the server's format, else the SDK's. */
        fun errorData(error: Throwable): Map<String, String> {
            val iam = error as? IAMException ?: return emptyMap()
            val server = iam.serverError?.code
            return mapOf("error_code" to if (TelemetryApiRules.isErrorCode(server)) server!! else iam.code.name)
        }

        private fun errorCodeData(code: String?): Map<String, String> =
            if (TelemetryApiRules.isErrorCode(code)) mapOf("error_code" to code!!) else emptyMap()

        /** An `auth_time` or `sid` claim as a string, whole numbers without a fraction. */
        private fun sessionMarker(value: Any?): String? =
            when (value) {
                is Number -> value.toLong().toString()
                is String -> value.takeIf { it.isNotEmpty() }
                else -> null
            }

        private fun debug(
            tag: String,
            message: String,
        ) {
            runCatching { android.util.Log.d(tag, message) }
        }
    }
}
