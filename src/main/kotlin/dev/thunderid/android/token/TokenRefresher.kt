// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.token

import dev.thunderid.android.IAMException
import dev.thunderid.android.ThunderIDErrorCode
import dev.thunderid.android.TokenResponse
import dev.thunderid.android.api.auth.OAuthClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Handles automatic access token refresh and atomic refresh token rotation (spec §11.7).
 */
internal class TokenRefresher(
    private val oauthClient: OAuthClient,
    private val tokenStore: TokenStore,
) {
    private val mutex = Mutex()

    suspend fun getAccessToken(clientId: String?): String {
        val token = tokenStore.accessToken()
        if (token != null) {
            if (!tokenStore.isNearExpiry()) return token
            // Token-exchange flows may return only an access token.
            // If no refresh token exists, use the current token instead of failing.
            if (tokenStore.refreshToken() == null) return token
        }
        val id =
            clientId
                ?: throw IAMException(ThunderIDErrorCode.INVALID_CONFIGURATION, "clientId required to refresh access token")
        return refresh(id).accessToken
    }

    suspend fun refresh(clientId: String): TokenResponse =
        mutex.withLock {
            val refreshToken =
                tokenStore.refreshToken()
                    ?: throw IAMException(ThunderIDErrorCode.SESSION_EXPIRED, "No refresh token available")
            val body =
                mapOf(
                    "grant_type" to "refresh_token",
                    "refresh_token" to refreshToken,
                    "client_id" to clientId,
                )
            // Form-encoded at the bare token endpoint: a refresh token from a bounded sign-in carries its unit.
            val response: TokenResponse = oauthClient.requestToken(null, body, "The token refresh was refused")
            tokenStore.save(response)
            response
        }
}
