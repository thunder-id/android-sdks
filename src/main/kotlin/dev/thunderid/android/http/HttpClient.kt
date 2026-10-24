// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.http

import dev.thunderid.android.IAMException
import dev.thunderid.android.ThunderIDErrorCode
import dev.thunderid.android.ThunderIDFetcher
import dev.thunderid.android.ThunderIDHttpRequest
import dev.thunderid.android.ThunderIDHttpResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Performs HTTP requests against the ThunderID server. Enforces HTTPS (spec §11.5).
 */
internal class HttpClient(
    private val baseUrl: String,
    private val allowInsecureConnections: Boolean = false,
    private var accessTokenProvider: (suspend () -> String)? = null,
    // Replaces the built-in transport for every request. Tests use it to answer without a network.
    private val defaultTransport: ThunderIDFetcher? = null,
) {
    // Set when sign-in runs on a backend: every request is handed to it instead of carrying a token.
    private var backendRouter: (suspend (ThunderIDHttpRequest, ThunderIDFetcher?) -> ThunderIDHttpResponse)? = null

    fun setAccessTokenProvider(provider: suspend () -> String) {
        accessTokenProvider = provider
    }

    /**
     * Hands every request to [router] instead of attaching an access token, so the SDK's own calls go
     * through the backend's proxy when sign-in runs there.
     */
    fun routeThroughBackend(router: suspend (ThunderIDHttpRequest, ThunderIDFetcher?) -> ThunderIDHttpResponse) {
        backendRouter = router
    }

    /**
     * Sends [request] as it is and returns the response whatever its status. Redirects are not
     * followed, so a header meant for one host is never carried to another.
     */
    suspend fun exchange(
        request: ThunderIDHttpRequest,
        fetcher: ThunderIDFetcher? = null,
    ): ThunderIDHttpResponse {
        if (!request.url.startsWith("https://")) {
            throw IAMException(ThunderIDErrorCode.INVALID_CONFIGURATION, "Request URL must use HTTPS")
        }
        return fetcher?.fetch(request) ?: defaultTransport?.fetch(request) ?: transport(request, followRedirects = false)
    }

    suspend inline fun <reified T : Any> get(
        path: String,
        requiresAuth: Boolean = true,
    ): T = request("GET", path, null, requiresAuth)

    suspend inline fun <reified T : Any> post(
        path: String,
        body: Map<String, Any>,
        requiresAuth: Boolean = true,
        headers: Map<String, String> = emptyMap(),
    ): T = request("POST", path, body, requiresAuth, headers)

    suspend inline fun <reified T : Any> put(
        path: String,
        body: Map<String, Any>,
        requiresAuth: Boolean = true,
        headers: Map<String, String> = emptyMap(),
    ): T = request("PUT", path, body, requiresAuth, headers)

    suspend inline fun <reified T : Any> request(
        method: String,
        path: String,
        body: Map<String, Any>?,
        requiresAuth: Boolean,
        headers: Map<String, String> = emptyMap(),
    ): T {
        val urlString = baseUrl + path
        if (!urlString.startsWith("https://")) {
            throw IAMException(ThunderIDErrorCode.INVALID_CONFIGURATION, "baseUrl must use HTTPS")
        }
        val jsonBody = body?.let { JSONObject(it).toString() }
        return parseResponse(execute(method, urlString, jsonBody, requiresAuth, headers, null))
    }

    /**
     * Sends an authenticated request to an absolute [url], which may live on a different host from
     * `baseUrl`. When [fetcher] is set it replaces the built-in transport for this request; the access
     * token is already attached to the request it receives.
     */
    suspend inline fun <reified T : Any> send(
        method: String,
        url: String,
        jsonBody: String? = null,
        fetcher: ThunderIDFetcher? = null,
    ): T {
        if (!url.startsWith("https://")) {
            throw IAMException(ThunderIDErrorCode.INVALID_CONFIGURATION, "Request URL must use HTTPS")
        }
        return parseResponse(execute(method, url, jsonBody, true, emptyMap(), fetcher))
    }

    /**
     * Runs the request through [fetcher], or the built-in transport, and returns the response body of a
     * 2xx response. Any other status is mapped to an [IAMException].
     */
    @PublishedApi
    internal suspend fun execute(
        method: String,
        url: String,
        jsonBody: String?,
        requiresAuth: Boolean,
        headers: Map<String, String>,
        fetcher: ThunderIDFetcher?,
    ): String {
        backendRouter?.let { router ->
            val requestHeaders = mapOf("Content-Type" to "application/json", "Accept" to "application/json") + headers
            return handleResponse(router(ThunderIDHttpRequest(method, url, requestHeaders, jsonBody), fetcher))
        }
        val requestHeaders =
            buildMap {
                put("Content-Type", "application/json")
                put("Accept", "application/json")
                putAll(headers)
                if (requiresAuth) {
                    val token =
                        accessTokenProvider?.invoke()
                            ?: throw IAMException(ThunderIDErrorCode.SDK_NOT_INITIALIZED, "No access token provider")
                    put("Authorization", "Bearer $token")
                }
            }
        val request = ThunderIDHttpRequest(method, url, requestHeaders, jsonBody)
        val response = fetcher?.fetch(request) ?: defaultTransport?.fetch(request) ?: transport(request)
        return handleResponse(response)
    }

    private suspend fun transport(
        request: ThunderIDHttpRequest,
        followRedirects: Boolean = true,
    ): ThunderIDHttpResponse =
        withContext(Dispatchers.IO) {
            val urlString = request.url
            val connection =
                (URL(urlString).openConnection() as HttpURLConnection).apply {
                    // The bypass is deliberately limited to loopback. Its only legitimate use is
                    // reaching a development server through the self-signed certificate ThunderID
                    // generates for localhost, and an attacker cannot sit in the middle of a
                    // loopback connection. Honouring the flag for arbitrary hosts would turn a
                    // convenience switch into a full man-in-the-middle hole on the channel
                    // carrying credentials, assertions and refresh tokens, in any build that
                    // happened to ship with it enabled.
                    if (allowInsecureConnections && this is HttpsURLConnection) {
                        if (isLoopbackHost(URL(urlString).host)) {
                            sslSocketFactory = insecureSslSocketFactory()
                            hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
                        } else {
                            throw IAMException(
                                ThunderIDErrorCode.INVALID_CONFIGURATION,
                                "allowInsecureConnections only applies to loopback hosts " +
                                    "(localhost, 127.0.0.1, ::1, 10.0.2.2); refusing to disable " +
                                    "certificate validation for '${URL(urlString).host}'",
                            )
                        }
                    }
                    instanceFollowRedirects = followRedirects
                    requestMethod = request.method
                    request.headers.forEach { (name, value) -> setRequestProperty(name, value) }
                    if (request.body != null) {
                        doOutput = true
                        OutputStreamWriter(outputStream).use { it.write(request.body) }
                    }
                }
            val statusCode = connection.responseCode
            val responseBody =
                runCatching {
                    if (statusCode in 200..299) {
                        connection.inputStream.bufferedReader().readText()
                    } else {
                        connection.errorStream?.bufferedReader()?.readText() ?: ""
                    }
                }.getOrDefault("")
            ThunderIDHttpResponse(statusCode, responseBody)
        }

    private fun handleResponse(response: ThunderIDHttpResponse): String {
        val statusCode = response.statusCode
        return when (statusCode) {
            in 200..299 -> {
                response.body
            }

            400 -> {
                val msg = runCatching { JSONObject(response.body).optString("message", "Bad request") }.getOrDefault("Bad request")
                throw IAMException(ThunderIDErrorCode.INVALID_INPUT, msg)
            }

            401 -> {
                throw IAMException(ThunderIDErrorCode.AUTHENTICATION_FAILED, "Unauthorized")
            }

            403 -> {
                throw IAMException(ThunderIDErrorCode.FORBIDDEN, "Forbidden")
            }

            404 -> {
                throw IAMException(ThunderIDErrorCode.NOT_FOUND, "Not found")
            }

            409 -> {
                throw IAMException(ThunderIDErrorCode.USER_ALREADY_EXISTS, "Conflict")
            }

            in 500..599 -> {
                throw IAMException(ThunderIDErrorCode.SERVER_ERROR, "Server error: $statusCode")
            }

            else -> {
                throw IAMException(ThunderIDErrorCode.UNKNOWN_ERROR, "Unexpected status: $statusCode")
            }
        }
    }

    @PublishedApi
    @Suppress("UNCHECKED_CAST")
    internal inline fun <reified T : Any> parseResponse(body: String): T {
        if (T::class == Unit::class) return Unit as T
        return com.google.gson
            .Gson()
            .fromJson(body, T::class.java)
    }

    /**
     * Whether [host] is a loopback address, and therefore unreachable by a network attacker.
     *
     * `10.0.2.2` is included because that is the Android emulator's alias for the host machine's
     * loopback interface, which is how an emulator reaches a development server.
     */
    private fun isLoopbackHost(host: String?): Boolean = host in LOOPBACK_HOSTS

    private fun insecureSslSocketFactory(): javax.net.ssl.SSLSocketFactory {
        val trustAll =
            object : X509TrustManager {
                override fun checkClientTrusted(
                    chain: Array<out X509Certificate>?,
                    authType: String?,
                ) = Unit

                override fun checkServerTrusted(
                    chain: Array<out X509Certificate>?,
                    authType: String?,
                ) = Unit

                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, arrayOf<TrustManager>(trustAll), SecureRandom())
        return ctx.socketFactory
    }

    private companion object {
        /**
         * Hosts a network attacker cannot occupy, and therefore the only ones for which
         * certificate validation may be relaxed.
         *
         * `10.0.2.2` is the Android emulator's alias for the host machine's loopback interface,
         * which is how an emulator reaches a development server.
         */
        val LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1", "::1", "[::1]", "10.0.2.2")
    }
}
