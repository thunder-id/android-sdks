// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android

import dev.thunderid.android.auth.FlowExecutionClient
import dev.thunderid.android.auth.PKCEManager
import dev.thunderid.android.backend.BackendClient
import dev.thunderid.android.backend.BackendSessionStore
import dev.thunderid.android.backend.resolveBackendEndpoints
import dev.thunderid.android.http.HttpClient
import dev.thunderid.android.management.AgentsClient
import dev.thunderid.android.management.ApplicationsClient
import dev.thunderid.android.management.ManagementTransport
import dev.thunderid.android.management.UsersClient
import dev.thunderid.android.token.JWKSCache
import dev.thunderid.android.token.TokenRefresher
import dev.thunderid.android.token.TokenStore
import dev.thunderid.android.token.TokenValidator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.net.URLDecoder

/**
 * ThunderID Android SDK client — Platform layer implementation of the full IAMClient interface (spec §7.1).
 *
 * Setting [ThunderIDEndpoints.backend] hands sign-in to a backend the application owns, which keeps the
 * tokens: [buildSignInUrl] and [handleRedirectCallback] start a session there, [isSignedIn] and [getUser]
 * read it, [httpRequest] and the SDK's own calls go through the backend's proxy with the session handle,
 * and [signOut] ends it. The embedded flows and [exchangeToken] are refused while it is on.
 */
class ThunderIDClient internal constructor(
    // Replaces the built-in HTTP transport. Tests use it to answer without a network.
    private val transport: ThunderIDFetcher?,
) {
    constructor() : this(null)

    private var config: ThunderIDConfig? = null
    private var httpClient: HttpClient? = null
    private var tokenStore: TokenStore? = null
    private var tokenRefresher: TokenRefresher? = null
    private var tokenValidator: TokenValidator? = null
    private var flowClient: FlowExecutionClient? = null
    private val pkceManager = PKCEManager()
    private var loading = false
    private var currentUser: User? = null
    private var backend: BackendClient? = null
    private val sessionEndedEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * Emits when a signed-in session ends without this client signing out, which only a backend can
     * report: another app signed the user out, an administrator ended the session, or it timed out. By
     * then the user is signed out locally. A sign-out the app starts itself does not emit.
     */
    val sessionEnded: SharedFlow<Unit> = sessionEndedEvents.asSharedFlow()

    // MARK: - Lifecycle

    suspend fun initialize(
        config: ThunderIDConfig,
        storage: StorageAdapter? = null,
    ): Boolean {
        if (this.config != null) throw IAMException(ThunderIDErrorCode.ALREADY_INITIALIZED, "SDK is already initialized")
        validateConfig(config)
        val backendEndpoints = resolveBackendEndpoints(config)
        this.config = config
        val adapter =
            storage ?: config.storage
                ?: throw IAMException(
                    ThunderIDErrorCode.INVALID_CONFIGURATION,
                    "A StorageAdapter is required on Android; pass an EncryptedStorageAdapter(context)",
                )
        val http = HttpClient(config.baseUrl, config.allowInsecureConnections, defaultTransport = transport)
        val store = TokenStore(adapter)
        val jwks = JWKSCache(http)
        tokenStore = store
        tokenValidator = TokenValidator(jwks, config)
        tokenRefresher = TokenRefresher(http, store)
        flowClient = FlowExecutionClient(http)
        http.setAccessTokenProvider {
            val cfg =
                this.config
                    ?: throw IAMException(ThunderIDErrorCode.SDK_NOT_INITIALIZED, "Not initialized")
            tokenRefresher!!.getAccessToken(cfg.clientId)
        }
        httpClient = http
        backend =
            backendEndpoints?.let { endpoints ->
                BackendClient(endpoints, BackendSessionStore(adapter, config.vendor, config.instanceId), store, http) {
                    currentUser = null
                    sessionEndedEvents.tryEmit(Unit)
                }
            }
        backend?.let { backend ->
            // The backend holds the tokens, so the SDK's own calls carry the session handle instead.
            http.routeThroughBackend { request, fetcher -> backend.send(request, fetcher) }
            // Read the session now, so the client reports loading until it is known whether the user is
            // signed in. A failed read is tried again on the next isSignedIn().
            runCatching { backend.getSession() }
        }
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

    // MARK: - Management

    /** Application management operations. Throws until [initialize] has run. */
    val applications: ApplicationsClient
        get() = ApplicationsClient(managementTransport("applications") { it.applications })

    /** User management operations. Throws until [initialize] has run. */
    val users: UsersClient
        get() = UsersClient(managementTransport("users") { it.users })

    /** Agent management operations. Throws until [initialize] has run. */
    val agents: AgentsClient
        get() = AgentsClient(managementTransport("agents") { it.agents })

    private fun managementTransport(
        collection: String,
        override: (ThunderIDEndpoints) -> String?,
    ): ManagementTransport {
        val cfg = config ?: throw IAMException(ThunderIDErrorCode.SDK_NOT_INITIALIZED, "Call initialize() before using the SDK")
        val http = httpClient ?: throw IAMException(ThunderIDErrorCode.SDK_NOT_INITIALIZED, "Call initialize() before using the SDK")
        return ManagementTransport(http, override(cfg.endpoints) ?: "${cfg.baseUrl}/$collection", cfg.http.fetcher)
    }

    // MARK: - Authentication

    suspend fun signIn(
        payload: EmbeddedSignInPayload,
        request: EmbeddedFlowRequestConfig,
    ): EmbeddedFlowResponse {
        requireInitialized()
        refuseOnBackend("Embedded sign-in")
        loading = true
        return try {
            val response =
                if (payload.flowId != null) {
                    flowClient!!.submit(payload.flowId, payload.actionId, payload.inputs, payload.challengeToken)
                } else {
                    flowClient!!.initiate(request.applicationId, request.flowType, attestationToken())
                }
            establishSessionIfNeeded(response)
            response
        } finally {
            loading = false
        }
    }

    /**
     * The URL that starts sign-in, for the app to open in a Custom Tab and hand the redirect back to
     * [handleRedirectCallback]. When sign-in runs on a backend it is the backend's sign-in endpoint,
     * bound to this client by a PKCE challenge, returning to `afterSignInUrl`.
     */
    fun buildSignInUrl(options: SignInOptions? = null): String {
        val cfg = requireConfig()
        backend?.let { backend ->
            val redirectUri =
                cfg.afterSignInUrl
                    ?: throw IAMException(
                        ThunderIDErrorCode.INVALID_CONFIGURATION,
                        "afterSignInUrl is required to sign in through the backend",
                    )
            val (_, challenge) = pkceManager.generate()
            return backend.signInUrl(redirectUri, challenge)
        }
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
        backend?.let { return completeBackendSignIn(it, url) }
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
        backend?.let { backend ->
            return try {
                currentUser = null
                backend.signOut() ?: config?.afterSignOutUrl ?: "/"
            } finally {
                loading = false
            }
        }
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
        backend?.let { return it.getSession().isSignedIn }
        return tokenStore?.accessToken() != null
    }

    /** True while signing in or out, and, when sign-in runs on a backend, until the first session read settles. */
    fun isLoading(): Boolean = loading || backend?.isLoading() == true

    /**
     * Whether sign-in runs on a backend the application owns ([ThunderIDEndpoints.backend]).
     */
    fun isBackendMode(): Boolean = backend != null

    /**
     * Asks the backend whether the session is still live, without refreshing it. When it has ended,
     * the user is signed out locally and [sessionEnded] emits. Returns `false` when sign-in does not
     * run on a backend.
     */
    suspend fun checkSession(): Boolean {
        requireInitialized()
        return backend?.checkSession() ?: false
    }

    /**
     * Watches for the backend session ending while the app is in the foreground: checks it now, then
     * every `session.checkInterval` seconds when that is positive. Runs until cancelled, so run it under
     * `repeatOnLifecycle(Lifecycle.State.STARTED)`; [dev.thunderid.compose.ThunderIDProvider] does.
     * Returns at once when sign-in does not run on a backend.
     */
    suspend fun watchSession() {
        val cfg = requireConfig()
        backend?.watch(cfg.session.checkInterval)
    }

    // MARK: - HTTP

    /**
     * Sends a request to the application's API when sign-in runs on a backend. A request under `baseUrl`
     * goes through the backend's proxy with the session handle; one under a bypass URL goes straight
     * there, with the access token from the backend's `token` endpoint unless `attachToken` is off. The
     * response comes back whatever its status, so the API's own `401` reaches the caller untouched.
     *
     * @throws IAMException [ThunderIDErrorCode.INVALID_INPUT] when the URL is under neither, without
     * sending it, and [ThunderIDErrorCode.UNSUPPORTED_OPERATION] when sign-in does not run on a backend.
     */
    suspend fun httpRequest(request: ThunderIDHttpRequest): ThunderIDHttpResponse {
        requireInitialized()
        val backend =
            backend ?: throw IAMException(
                ThunderIDErrorCode.UNSUPPORTED_OPERATION,
                "httpRequest needs endpoints.backend: without it, call your API with getAccessToken()",
            )
        return backend.send(request)
    }

    // MARK: - Registration

    suspend fun signUp(
        payload: EmbeddedSignInPayload? = null,
        request: EmbeddedFlowRequestConfig? = null,
    ): EmbeddedFlowResponse {
        requireInitialized()
        refuseOnBackend("Embedded sign-up")
        val appId = request?.applicationId ?: config?.applicationId ?: ""
        val response =
            if (payload?.flowId != null) {
                flowClient!!.submit(payload.flowId, payload.actionId, payload.inputs, payload.challengeToken)
            } else {
                flowClient!!.initiate(appId, request?.flowType ?: FlowType.REGISTRATION, attestationToken())
            }
        establishSessionIfNeeded(response)
        return response
    }

    // MARK: - Token & Session

    /**
     * Returns a valid access token. When sign-in runs on a backend it is the token for the bypass URLs,
     * from the backend's `token` endpoint, reused until 30 seconds before it expires.
     */
    suspend fun getAccessToken(): String {
        requireInitialized()
        backend?.let { return it.getAccessToken() }
        return tokenRefresher!!.getAccessToken(config?.clientId)
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
        refuseOnBackend("exchangeToken")
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
        backend?.clear()
        tokenStore?.clear()
        currentUser = null
    }

    // MARK: - User & Profile

    fun setCachedUser(user: User) {
        currentUser = user
    }

    suspend fun getUser(): User {
        requireInitialized()
        currentUser?.let { return it }
        backend?.let { backend ->
            val session = backend.getSession()
            if (!session.isSignedIn) throw IAMException(ThunderIDErrorCode.AUTHENTICATION_FAILED, "Not signed in")
            return User(session.user.orEmpty()).also { currentUser = it }
        }
        tokenStore?.accessToken()?.let { token ->
            val claims = runCatching { decodeJwtToken(token) }.getOrNull()
            if (claims != null && !(claims["sub"] as? String).isNullOrEmpty()) {
                val user = User(claims.filterValues { it != null }.mapValues { it.value as Any })
                currentUser = user
                return user
            }
        }
        val user = User(claimsFrom(httpClient!!.get("/oauth2/userinfo")))
        currentUser = user
        return user
    }

    suspend fun getUserProfile(): UserProfile {
        requireInitialized()
        return httpClient!!.get("/users/me")
    }

    suspend fun getUserSchema(): Map<String, AttributeSchema> {
        requireInitialized()
        return httpClient!!.get<UsersMeMetaResponse>("/users/me/meta").schema
    }

    suspend fun updateUserProfile(payload: Map<String, Any>): UserProfile {
        requireInitialized()
        return httpClient!!.put("/users/me", mapOf("attributes" to payload))
    }

    /**
     * Changes one of the signed-in user's own credentials via `POST /users/me/update-credentials`.
     *
     * The self-service write path does not verify the account's existing value today, so this
     * call collects only the new value, matching the React/Vue SDKs.
     */
    suspend fun updateUserCredentials(
        attribute: String = "password",
        newValue: String,
    ) {
        requireInitialized()
        httpClient!!.post<Unit>("/users/me/update-credentials", mapOf("attributes" to mapOf(attribute to newValue)))
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

    /** Reads a JSON object response as a claim map, without interpreting any of the keys. */
    private fun claimsFrom(json: com.google.gson.JsonObject): Map<String, Any> {
        val type = object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type
        return com.google.gson
            .Gson()
            .fromJson(json, type)
    }

    /**
     * Completes a backend sign-in from the Custom Tab's redirect: exchanges its one-time `session_code`,
     * with the PKCE verifier of the sign-in this client started, for a session handle.
     */
    private suspend fun completeBackendSignIn(
        backend: BackendClient,
        url: String,
    ): User {
        val params = queryParameters(url)
        params["error"]?.let {
            pkceManager.clearVerifier()
            throw IAMException(ThunderIDErrorCode.AUTHENTICATION_FAILED, "The backend refused sign-in: $it")
        }
        val code =
            params["session_code"]?.takeIf { it.isNotEmpty() }
                ?: throw IAMException(ThunderIDErrorCode.INVALID_GRANT, "Session code missing from callback URL")
        // A code for a sign-in this client did not start has no verifier to go with it.
        val verifier =
            pkceManager.codeVerifier
                ?: throw IAMException(ThunderIDErrorCode.INVALID_GRANT, "PKCE verifier not found")
        pkceManager.clearVerifier()
        loading = true
        return try {
            val session = backend.startSession(code, verifier)
            User(session.user.orEmpty()).also { currentUser = it }
        } finally {
            loading = false
        }
    }

    private fun queryParameters(url: String): Map<String, String> =
        url
            .substringAfter('?', "")
            .substringBefore('#')
            .split('&')
            .filter { it.isNotEmpty() }
            .associate {
                URLDecoder.decode(it.substringBefore('='), Charsets.UTF_8.name()) to
                    URLDecoder.decode(it.substringAfter('=', ""), Charsets.UTF_8.name())
            }

    /** Fails [operation] while sign-in runs on a backend, which holds every token this would need or return. */
    private fun refuseOnBackend(operation: String) {
        if (backend != null) {
            throw IAMException(
                ThunderIDErrorCode.UNSUPPORTED_OPERATION,
                "$operation is not available when sign-in runs on the backend",
            )
        }
    }

    private fun requireInitialized() {
        config ?: throw IAMException(ThunderIDErrorCode.SDK_NOT_INITIALIZED, "Call initialize() before using the SDK")
    }

    private fun requireConfig(): ThunderIDConfig =
        config ?: throw IAMException(ThunderIDErrorCode.SDK_NOT_INITIALIZED, "Call initialize() before using the SDK")

    private fun validateConfig(config: ThunderIDConfig) {
        if (config.baseUrl.isEmpty()) throw IAMException(ThunderIDErrorCode.INVALID_CONFIGURATION, "baseUrl is required")
        if (!config.baseUrl.startsWith("https://")) throw IAMException(ThunderIDErrorCode.INVALID_CONFIGURATION, "baseUrl must use HTTPS")
        if (config.attestationEnabled && config.attestationTokenProvider == null) {
            throw IAMException(
                ThunderIDErrorCode.INVALID_CONFIGURATION,
                "attestationTokenProvider is required when attestationEnabled is true",
            )
        }
    }

    private suspend fun attestationToken(): String? {
        val cfg = config ?: return null
        if (!cfg.attestationEnabled) return null
        return cfg.attestationTokenProvider?.invoke()
    }

    private fun establishSessionIfNeeded(response: EmbeddedFlowResponse) {
        val assertion = response.assertion
        if (response.flowStatus != FlowStatus.COMPLETE || assertion.isNullOrEmpty()) {
            return
        }
        tokenStore!!.save(TokenResponse(accessToken = assertion, tokenType = "Bearer"))
        try {
            val claims = decodeJwtToken(assertion)
            currentUser = User(claims.filterValues { it != null }.mapValues { it.value as Any })
        } catch (_: Exception) {
            // assertion is stored; user info will be fetched via getUser() on next access
        }
    }
}
