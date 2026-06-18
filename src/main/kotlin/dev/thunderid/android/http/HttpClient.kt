package dev.thunderid.android.http

import dev.thunderid.android.IAMErrorCode
import dev.thunderid.android.IAMException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Performs HTTP requests against the ThunderID server. Enforces HTTPS (spec §11.5).
 */
internal class HttpClient(
    private val baseUrl: String,
    private var accessTokenProvider: (suspend () -> String)? = null
) {
    fun setAccessTokenProvider(provider: suspend () -> String) {
        accessTokenProvider = provider
    }

    suspend inline fun <reified T : Any> get(path: String, requiresAuth: Boolean = true): T =
        request("GET", path, null, requiresAuth)

    suspend inline fun <reified T : Any> post(path: String, body: Map<String, Any>, requiresAuth: Boolean = true): T =
        request("POST", path, body, requiresAuth)

    suspend inline fun <reified T : Any> request(
        method: String,
        path: String,
        body: Map<String, Any>?,
        requiresAuth: Boolean
    ): T = withContext(Dispatchers.IO) {
        val urlString = baseUrl + path
        if (!urlString.startsWith("https://")) {
            throw IAMException(IAMErrorCode.INVALID_CONFIGURATION, "baseUrl must use HTTPS")
        }
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            if (requiresAuth) {
                val token = accessTokenProvider?.invoke()
                    ?: throw IAMException(IAMErrorCode.SDK_NOT_INITIALIZED, "No access token provider")
                setRequestProperty("Authorization", "Bearer $token")
            }
            if (body != null) {
                doOutput = true
                OutputStreamWriter(outputStream).use { it.write(JSONObject(body).toString()) }
            }
        }
        val statusCode = connection.responseCode
        val responseBody = runCatching {
            if (statusCode in 200..299) connection.inputStream.bufferedReader().readText()
            else connection.errorStream?.bufferedReader()?.readText() ?: ""
        }.getOrDefault("")

        when (statusCode) {
            in 200..299 -> parseResponse(responseBody)
            400 -> {
                val msg = runCatching { JSONObject(responseBody).optString("message", "Bad request") }.getOrDefault("Bad request")
                throw IAMException(IAMErrorCode.INVALID_INPUT, msg)
            }
            401 -> throw IAMException(IAMErrorCode.AUTHENTICATION_FAILED, "Unauthorized")
            409 -> throw IAMException(IAMErrorCode.USER_ALREADY_EXISTS, "Conflict")
            in 500..599 -> throw IAMException(IAMErrorCode.SERVER_ERROR, "Server error: $statusCode")
            else -> throw IAMException(IAMErrorCode.UNKNOWN_ERROR, "Unexpected status: $statusCode")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T : Any> parseResponse(body: String): T {
        if (T::class == Unit::class) return Unit as T
        return com.google.gson.Gson().fromJson(body, T::class.java)
    }
}
