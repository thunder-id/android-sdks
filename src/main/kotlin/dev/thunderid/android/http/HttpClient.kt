/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package dev.thunderid.android.http

import dev.thunderid.android.IAMException
import dev.thunderid.android.ThunderIDErrorCode
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
) {
    fun setAccessTokenProvider(provider: suspend () -> String) {
        accessTokenProvider = provider
    }

    suspend inline fun <reified T : Any> get(
        path: String,
        requiresAuth: Boolean = true,
    ): T = request("GET", path, null, requiresAuth)

    suspend inline fun <reified T : Any> post(
        path: String,
        body: Map<String, Any>,
        requiresAuth: Boolean = true,
    ): T = request("POST", path, body, requiresAuth)

    suspend inline fun <reified T : Any> request(
        method: String,
        path: String,
        body: Map<String, Any>?,
        requiresAuth: Boolean,
    ): T =
        withContext(Dispatchers.IO) {
            val urlString = baseUrl + path
            if (!urlString.startsWith("https://")) {
                throw IAMException(ThunderIDErrorCode.INVALID_CONFIGURATION, "baseUrl must use HTTPS")
            }
            val connection =
                (URL(urlString).openConnection() as HttpURLConnection).apply {
                    if (allowInsecureConnections && this is HttpsURLConnection) {
                        sslSocketFactory = insecureSslSocketFactory()
                        hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
                    }
                    requestMethod = method
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                    if (requiresAuth) {
                        val token =
                            accessTokenProvider?.invoke()
                                ?: throw IAMException(ThunderIDErrorCode.SDK_NOT_INITIALIZED, "No access token provider")
                        setRequestProperty("Authorization", "Bearer $token")
                    }
                    if (body != null) {
                        doOutput = true
                        OutputStreamWriter(outputStream).use { it.write(JSONObject(body).toString()) }
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

            when (statusCode) {
                in 200..299 -> {
                    parseResponse(responseBody)
                }

                400 -> {
                    val msg = runCatching { JSONObject(responseBody).optString("message", "Bad request") }.getOrDefault("Bad request")
                    throw IAMException(ThunderIDErrorCode.INVALID_INPUT, msg)
                }

                401 -> {
                    throw IAMException(ThunderIDErrorCode.AUTHENTICATION_FAILED, "Unauthorized")
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

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T : Any> parseResponse(body: String): T {
        if (T::class == Unit::class) return Unit as T
        return com.google.gson
            .Gson()
            .fromJson(body, T::class.java)
    }

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
}
