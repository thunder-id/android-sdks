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

package dev.thunderid.android.token

import dev.thunderid.android.IAMException
import dev.thunderid.android.ThunderIDErrorCode
import dev.thunderid.android.TokenResponse
import dev.thunderid.android.http.HttpClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Handles automatic access token refresh and atomic refresh token rotation (spec §11.7).
 */
internal class TokenRefresher(
    private val httpClient: HttpClient,
    private val tokenStore: TokenStore,
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
            val response: TokenResponse = httpClient.post("/oauth2/token", body, requiresAuth = false)
            tokenStore.save(response)
            response
        }
}
