package dev.thunderid.android.token

import dev.thunderid.android.IAMErrorCode
import dev.thunderid.android.IAMException
import dev.thunderid.android.TokenResponse
import dev.thunderid.android.http.HttpClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Handles automatic access token refresh and atomic refresh token rotation (spec §11.7).
 */
internal class TokenRefresher(
    private val httpClient: HttpClient,
    private val tokenStore: TokenStore
) {
    private val mutex = Mutex()

    suspend fun getAccessToken(clientId: String): String {
        val token = tokenStore.accessToken()
        if (token != null) {
            if (!tokenStore.isNearExpiry()) return token
            // Token-exchange flows may return only an access token.
            // If no refresh token exists, use the current token instead of failing.
            if (tokenStore.refreshToken() == null) return token
        }
        return refresh(clientId).accessToken
    }

    suspend fun refresh(clientId: String): TokenResponse = mutex.withLock {
        val refreshToken = tokenStore.refreshToken()
            ?: throw IAMException(IAMErrorCode.SESSION_EXPIRED, "No refresh token available")
        val body = mapOf(
            "grant_type" to "refresh_token",
            "refresh_token" to refreshToken,
            "client_id" to clientId
        )
        val response: TokenResponse = httpClient.post("/oauth2/token", body, requiresAuth = false)
        tokenStore.save(response)
        response
    }
}
