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

package dev.thunderid.android

import dev.thunderid.android.auth.FlowExecutionClient
import dev.thunderid.android.auth.PKCEManager
import dev.thunderid.android.http.HttpClient
import dev.thunderid.android.token.JWKSCache
import dev.thunderid.android.token.TokenRefresher
import dev.thunderid.android.token.TokenStore
import dev.thunderid.android.token.TokenValidator

/**
 * ThunderID Android SDK client — Platform layer implementation of the full IAMClient interface (spec §7.1).
 */
class ThunderIDClient {
    private var config: ThunderIDConfig? = null
    private var httpClient: HttpClient? = null
    private var tokenStore: TokenStore? = null
    private var tokenRefresher: TokenRefresher? = null
    private var tokenValidator: TokenValidator? = null
    private var flowClient: FlowExecutionClient? = null
    private val pkceManager = PKCEManager()
    private var loading = false
    private var currentUser: User? = null

    // MARK: - Lifecycle

    suspend fun initialize(
        config: ThunderIDConfig,
        storage: StorageAdapter? = null,
    ): Boolean {
        if (this.config != null) throw IAMException(ThunderIDErrorCode.ALREADY_INITIALIZED, "SDK is already initialized")
        validateConfig(config)
        this.config = config
        val adapter =
            storage ?: config.storage
                ?: throw IAMException(
                    ThunderIDErrorCode.INVALID_CONFIGURATION,
                    "A StorageAdapter is required on Android; pass an EncryptedStorageAdapter(context)",
                )
        val http = HttpClient(config.baseUrl, config.allowInsecureConnections)
        val store = TokenStore(adapter)
        val jwks = JWKSCache(http)
        tokenStore = store
        tokenValidator = TokenValidator(jwks, config)
        tokenRefresher = TokenRefresher(http, store)
        flowClient = FlowExecutionClient(http, config.flowSecret)
        http.setAccessTokenProvider {
            val clientId =
                this.config?.clientId
                    ?: throw IAMException(ThunderIDErrorCode.INVALID_CONFIGURATION, "clientId required")
            tokenRefresher!!.getAccessToken(clientId)
        }
        httpClient = http
        return true
    }

    suspend fun reInitialize(
        baseUrl: String? = null,
        clientId: String? = null,
    ): Boolean {
        val current = config ?: throw IAMException(ThunderIDErrorCode.SDK_NOT_INITIALIZED, "SDK not initialized")
        val updated =
            current.copy(
                baseUrl = baseUrl ?: current.baseUrl,
                clientId = clientId ?: current.clientId,
            )
        this.config = null
        return initialize(updated, tokenStore?.let { null }) // storage is already set internally
    }

    fun getConfiguration(): ThunderIDConfig = config ?: throw IAMException(ThunderIDErrorCode.SDK_NOT_INITIALIZED, "SDK not initialized")

    // MARK: - Authentication

    suspend fun signIn(
        payload: EmbeddedSignInPayload,
        request: EmbeddedFlowRequestConfig,
    ): EmbeddedFlowResponse {
        requireInitialized()
        loading = true
        return try {
            val response =
                if (payload.flowId != null) {
                    flowClient!!.submit(payload.flowId, payload.actionId, payload.inputs, payload.challengeToken)
                } else {
                    flowClient!!.initiate(request.applicationId, request.flowType)
                }
            establishSessionIfNeeded(response)
            response
        } finally {
            loading = false
        }
    }

    fun buildSignInUrl(options: SignInOptions? = null): String {
        val cfg = requireConfig()
        val clientId = cfg.clientId ?: throw IAMException(ThunderIDErrorCode.INVALID_CONFIGURATION, "clientId required for redirect mode")
        val (_, challenge) = pkceManager.generate()
        val params =
            StringBuilder("${cfg.baseUrl}/oauth2/authorize")
                .append("?response_type=code")
                .append("&client_id=")
                .append(clientId)
                .append("&redirect_uri=")
                .append(cfg.afterSignInUrl ?: "")
                .append("&scope=")
                .append(cfg.scopes.joinToString(" "))
                .append("&code_challenge=")
                .append(challenge)
                .append("&code_challenge_method=S256")
        options?.prompt?.let { params.append("&prompt=").append(it) }
        options?.loginHint?.let { params.append("&login_hint=").append(it) }
        options?.fidp?.let { params.append("&fidp=").append(it) }
        return params.toString()
    }

    suspend fun handleRedirectCallback(url: String): User {
        val cfg = requireConfig()
        val clientId = cfg.clientId ?: throw IAMException(ThunderIDErrorCode.INVALID_CONFIGURATION, "clientId required")
        val code =
            url.substringAfter("code=").substringBefore("&").takeIf { it.isNotEmpty() }
                ?: throw IAMException(ThunderIDErrorCode.INVALID_GRANT, "Authorization code missing from callback URL")
        val verifier =
            pkceManager.codeVerifier
                ?: throw IAMException(ThunderIDErrorCode.INVALID_GRANT, "PKCE verifier not found")
        pkceManager.clearVerifier()
        val body =
            mapOf(
                "grant_type" to "authorization_code",
                "code" to code,
                "client_id" to clientId,
                "redirect_uri" to (cfg.afterSignInUrl ?: ""),
                "code_verifier" to verifier,
            )
        val tokenResponse: TokenResponse = httpClient!!.post("/oauth2/token", body, requiresAuth = false)
        tokenResponse.idToken?.let { tokenValidator?.validate(it, null) }
        tokenStore!!.save(tokenResponse)
        return getUser()
    }

    suspend fun signOut(options: SignOutOptions? = null): String {
        requireInitialized()
        loading = true
        return try {
            val refreshToken = tokenStore?.refreshToken()
            val clientId = config?.clientId
            if (refreshToken != null && clientId != null) {
                val body = mapOf("token" to refreshToken, "client_id" to clientId)
                runCatching { httpClient!!.post<Unit>("/oauth2/revoke", body, requiresAuth = false) }
            }
            tokenStore?.clear()
            currentUser = null
            config?.afterSignOutUrl ?: "/"
        } finally {
            loading = false
        }
    }

    suspend fun isSignedIn(): Boolean {
        requireInitialized()
        return tokenStore?.accessToken() != null
    }

    fun isLoading(): Boolean = loading

    // MARK: - Registration

    suspend fun signUp(
        payload: EmbeddedSignInPayload? = null,
        request: EmbeddedFlowRequestConfig? = null,
    ): EmbeddedFlowResponse {
        requireInitialized()
        val appId = request?.applicationId ?: config?.applicationId ?: ""
        val response =
            if (payload?.flowId != null) {
                flowClient!!.submit(payload.flowId, payload.actionId, payload.inputs, payload.challengeToken)
            } else {
                flowClient!!.initiate(appId, request?.flowType ?: FlowType.REGISTRATION)
            }
        establishSessionIfNeeded(response)
        return response
    }

    // MARK: - Token & Session

    suspend fun getAccessToken(): String {
        requireInitialized()
        val clientId = config?.clientId ?: throw IAMException(ThunderIDErrorCode.INVALID_CONFIGURATION, "clientId required")
        return tokenRefresher!!.getAccessToken(clientId)
    }

    fun decodeJwtToken(token: String): Map<String, Any?> {
        val parts = token.split(".")
        if (parts.size != 3) throw IAMException(ThunderIDErrorCode.INVALID_INPUT, "Invalid JWT format")
        val padded =
            parts[1].replace('-', '+').replace('_', '/').let {
                it + "=".repeat((4 - it.length % 4) % 4)
            }
        val json = String(android.util.Base64.decode(padded, android.util.Base64.DEFAULT), Charsets.UTF_8)
        return org.json.JSONObject(json).let { obj ->
            obj.keys().asSequence().associateWith { obj.opt(it) }
        }
    }

    suspend fun exchangeToken(config: TokenExchangeRequestConfig): TokenResponse {
        requireInitialized()
        val body =
            mutableMapOf<String, Any>(
                "grant_type" to "urn:ietf:params:oauth:grant-type:token-exchange",
                "subject_token" to config.subjectToken,
                "subject_token_type" to config.subjectTokenType,
            )
        (this.config?.clientId ?: this.config?.applicationId)?.takeIf { it.isNotEmpty() }?.let { body["client_id"] = it }
        config.requestedTokenType?.let { body["requested_token_type"] = it }
        config.audience?.let { body["audience"] = it }
        val response: TokenResponse = httpClient!!.post("/oauth2/token", body, requiresAuth = false)
        tokenStore!!.save(response)
        return response
    }

    fun clearSession() {
        tokenStore?.clear()
        currentUser = null
    }

    // MARK: - User & Profile

    suspend fun getUser(): User {
        requireInitialized()
        currentUser?.let { return it }
        val user: User = httpClient!!.get("/oauth2/userinfo")
        currentUser = user
        return user
    }

    suspend fun getUserProfile(): UserProfile {
        requireInitialized()
        return httpClient!!.get("/scim2/Me")
    }

    suspend fun updateUserProfile(
        payload: Map<String, Any>,
        userId: String? = null,
    ): User {
        requireInitialized()
        val path = if (userId != null) "/scim2/Users/$userId" else "/scim2/Me"
        val updated: User = httpClient!!.post(path, payload)
        currentUser = updated
        return updated
    }

    // MARK: - Flow Meta

    suspend fun getFlowMeta(
        applicationId: String,
        language: String = "en-US",
    ): Map<String, Any?> {
        requireInitialized()
        val path = "/flow/meta?id=$applicationId&type=APP&language=$language"
        val json: com.google.gson.JsonObject = httpClient!!.get(path, requiresAuth = false)
        val type = object : com.google.gson.reflect.TypeToken<Map<String, Any?>>() {}.type
        return com.google.gson
            .Gson()
            .fromJson(json, type)
    }

    // MARK: - Private helpers

    private fun requireInitialized() {
        config ?: throw IAMException(ThunderIDErrorCode.SDK_NOT_INITIALIZED, "Call initialize() before using the SDK")
    }

    private fun requireConfig(): ThunderIDConfig =
        config ?: throw IAMException(ThunderIDErrorCode.SDK_NOT_INITIALIZED, "Call initialize() before using the SDK")

    private fun validateConfig(config: ThunderIDConfig) {
        if (config.baseUrl.isEmpty()) throw IAMException(ThunderIDErrorCode.INVALID_CONFIGURATION, "baseUrl is required")
        if (!config.baseUrl.startsWith("https://")) throw IAMException(ThunderIDErrorCode.INVALID_CONFIGURATION, "baseUrl must use HTTPS")
    }

    private fun establishSessionIfNeeded(response: EmbeddedFlowResponse) {
        val assertion = response.assertion
        if (response.flowStatus != FlowStatus.COMPLETE || assertion.isNullOrEmpty()) {
            return
        }
        tokenStore!!.save(TokenResponse(accessToken = assertion, tokenType = "Bearer"))
        try {
            val claims = decodeJwtToken(assertion)
            currentUser =
                User(
                    sub = claims["sub"] as? String ?: "",
                    username = claims["username"] as? String ?: claims["preferred_username"] as? String,
                    email = claims["email"] as? String,
                    displayName = claims["name"] as? String ?: claims["displayName"] as? String,
                )
        } catch (_: Exception) {
            // assertion is stored; user info will be fetched via getUser() on next access
        }
    }
}
