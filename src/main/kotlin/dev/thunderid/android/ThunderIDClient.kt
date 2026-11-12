// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android

import dev.thunderid.android.api.account.AccessibleOrganizationUnits
import dev.thunderid.android.api.account.AccessibleOrganizationUnitsOptions
import dev.thunderid.android.api.account.AccessibleOrganizationUnitsResponse
import dev.thunderid.android.api.account.CurrentOrganizationUnit
import dev.thunderid.android.api.account.currentOrganizationUnitFrom
import dev.thunderid.android.api.auth.AuthorizationDetailsTypesClient
import dev.thunderid.android.api.auth.CIBAInitiateOptions
import dev.thunderid.android.api.auth.CIBAInitiateResponse
import dev.thunderid.android.api.auth.DeviceAuthorization
import dev.thunderid.android.api.auth.DeviceGrantRelay
import dev.thunderid.android.api.auth.FlowExecutionClient
import dev.thunderid.android.api.auth.OAuthClient
import dev.thunderid.android.api.auth.OrganizationUnitSwitchResult
import dev.thunderid.android.api.auth.PKCEManager
import dev.thunderid.android.api.auth.SupportedAuthorizationDetailsType
import dev.thunderid.android.api.auth.SwitchOrganizationUnitOptions
import dev.thunderid.android.api.auth.encodeQuery
import dev.thunderid.android.api.auth.withErrorAsFailureReason
import dev.thunderid.android.api.management.AdministrationClient
import dev.thunderid.android.api.management.AgentTypesClient
import dev.thunderid.android.api.management.AgentsClient
import dev.thunderid.android.api.management.ApplicationsClient
import dev.thunderid.android.api.management.AuthorizationDetailTypesClient
import dev.thunderid.android.api.management.ClientRegistrationsClient
import dev.thunderid.android.api.management.ConfigurationClient
import dev.thunderid.android.api.management.ConfigurationVersionsClient
import dev.thunderid.android.api.management.ConnectionsClient
import dev.thunderid.android.api.management.CredentialConfigurationsClient
import dev.thunderid.android.api.management.DesignClient
import dev.thunderid.android.api.management.FlowsClient
import dev.thunderid.android.api.management.GatewaysClient
import dev.thunderid.android.api.management.GroupsClient
import dev.thunderid.android.api.management.ManagementTransport
import dev.thunderid.android.api.management.NotificationTemplatesClient
import dev.thunderid.android.api.management.OrganizationUnitsClient
import dev.thunderid.android.api.management.PresentationDefinitionsClient
import dev.thunderid.android.api.management.ProjectsClient
import dev.thunderid.android.api.management.ResourceServersClient
import dev.thunderid.android.api.management.RolesClient
import dev.thunderid.android.api.management.SecretsClient
import dev.thunderid.android.api.management.ServerConfigClient
import dev.thunderid.android.api.management.TranslationsClient
import dev.thunderid.android.api.management.UserTypesClient
import dev.thunderid.android.api.management.UsersClient
import dev.thunderid.android.api.management.VariablesClient
import dev.thunderid.android.api.management.getTokenPermissions
import dev.thunderid.android.backend.BackendClient
import dev.thunderid.android.backend.BackendSessionStore
import dev.thunderid.android.backend.resolveBackendEndpoints
import dev.thunderid.android.http.HttpClient
import dev.thunderid.android.token.JWKSCache
import dev.thunderid.android.token.TokenRefresher
import dev.thunderid.android.token.TokenStore
import dev.thunderid.android.token.TokenValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.net.URLDecoder

/** OAuth errors that mean the server needs the user, so a switch on a backend signs in through the browser. */
private val INTERACTION_ERRORS =
    setOf("interaction_required", "login_required", "consent_required", "account_selection_required")

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
    private var oauthClient: OAuthClient? = null

    /** The organization unit the redirect sign-in in progress is bounded to, for redeeming its code. */
    private var pendingOrganizationUnitId: String? = null

    /** Whether the embedded flow in progress was started for an organization unit, so it replaces the session. */
    private var boundedFlowInProgress = false
    private val pkceManager = PKCEManager()
    private var loading = false
    private var currentUser: User? = null
    private var backend: BackendClient? = null

    /** Where the SDK's own API calls go: `baseUrl`, or the API base in backend mode. */
    private var apiBaseUrl: String? = null
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
        // In backend mode `baseUrl` is the backend, so the SDK's own API calls (the profile, management)
        // go to the API base instead, which the backend proxies.
        val apiBase = backendEndpoints?.apiBaseUrl ?: config.baseUrl
        apiBaseUrl = apiBase
        val http = HttpClient(apiBase, config.allowInsecureConnections, defaultTransport = transport)
        val store = TokenStore(adapter)
        val oauth = OAuthClient(http, config.baseUrl, config.endpoints.wellKnown)
        // The JWKS, the issuer and the OAuth endpoints come from the provider's discovery document, so `baseUrl`
        // can be any OpenID provider's issuer. Without one they fall back to ThunderID's paths under `baseUrl`.
        val jwks = JWKSCache(http) { oauth.jwksUrl() }
        tokenStore = store
        tokenValidator = TokenValidator(jwks, config) { oauth.expectedIssuer() }
        oauthClient = oauth
        tokenRefresher = TokenRefresher(oauth, store)
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
        get() = UsersClient(managementTransport("users") { it.users }, managementTransport("user-types") { it.userTypes })

    /** Agent management operations. Throws until [initialize] has run. */
    val agents: AgentsClient
        get() = AgentsClient(managementTransport("agents") { it.agents }, managementTransport("agent-types") { it.agentTypes })

    /** Group management operations. Throws until [initialize] has run. */
    val groups: GroupsClient
        get() = GroupsClient(managementTransport("groups") { it.groups })

    /** Role management operations. Throws until [initialize] has run. */
    val roles: RolesClient
        get() = RolesClient(managementTransport("roles") { it.roles })

    /** User type management operations. Throws until [initialize] has run. */
    val userTypes: UserTypesClient
        get() = UserTypesClient(managementTransport("user-types") { it.userTypes })

    /** Agent type management operations. Throws until [initialize] has run. */
    val agentTypes: AgentTypesClient
        get() = AgentTypesClient(managementTransport("agent-types") { it.agentTypes })

    /** Organization unit management operations. Throws until [initialize] has run. */
    val organizationUnits: OrganizationUnitsClient
        get() = OrganizationUnitsClient(managementTransport("organization-units") { it.organizationUnits })

    /** Resource server, resource and action management operations. Throws until [initialize] has run. */
    val resourceServers: ResourceServersClient
        get() = ResourceServersClient(managementTransport("resource-servers") { it.resourceServers })

    /** Connection management operations. Throws until [initialize] has run. */
    val connections: ConnectionsClient
        get() = ConnectionsClient(managementTransport("connections") { it.connections })

    /** Flow definition management operations (not flow execution). Throws until [initialize] has run. */
    val flows: FlowsClient
        get() = FlowsClient(managementTransport("flows") { it.flows })

    /** Notification template management operations. Throws until [initialize] has run. */
    val notificationTemplates: NotificationTemplatesClient
        get() = NotificationTemplatesClient(managementTransport("notification-templates") { it.notificationTemplates })

    /** Theme and layout management operations. Throws until [initialize] has run. */
    val design: DesignClient
        get() = DesignClient(managementTransport("design") { it.design })

    /** Translation management operations, on the `i18n` collection. Throws until [initialize] has run. */
    val translations: TranslationsClient
        get() = TranslationsClient(managementTransport("i18n") { it.i18n })

    /** Server configuration operations. Throws until [initialize] has run. */
    val serverConfig: ServerConfigClient
        get() = ServerConfigClient(managementTransport("server-config") { it.serverConfig })

    /** Gateway management operations. Throws until [initialize] has run. */
    val gateways: GatewaysClient
        get() = GatewaysClient(managementTransport("gateways") { it.gateways })

    /** Server-level variable operations. Throws until [initialize] has run. */
    val variables: VariablesClient
        get() = VariablesClient(managementTransport("variables") { it.variables })

    /** Server-level secret operations. A secret's value is write-only. Throws until [initialize] has run. */
    val secrets: SecretsClient
        get() = SecretsClient(managementTransport("secrets") { it.secrets })

    /** Configuration version operations. Throws until [initialize] has run. */
    val configurationVersions: ConfigurationVersionsClient
        get() = ConfigurationVersionsClient(managementTransport("configuration-versions") { it.configurationVersions })

    /** OpenID4VCI credential configuration operations. Throws until [initialize] has run. */
    val credentialConfigurations: CredentialConfigurationsClient
        get() =
            CredentialConfigurationsClient(
                managementTransport("openid4vci/credential-configurations") { it.credentialConfigurations },
            )

    /** OpenID4VP presentation definition operations. Throws until [initialize] has run. */
    val presentationDefinitions: PresentationDefinitionsClient
        get() =
            PresentationDefinitionsClient(
                managementTransport("openid4vp/presentation-definitions") { it.presentationDefinitions },
            )

    /**
     * Dynamic client registration (RFC 7591) and registration management (RFC 7592). Throws until [initialize]
     * has run.
     */
    val clientRegistrations: ClientRegistrationsClient
        get() = ClientRegistrationsClient(managementTransport("oauth2/dcr/register") { it.clientRegistrations })

    /** Project management operations. Only the Control Plane serves them. Throws until [initialize] has run. */
    val projects: ProjectsClient
        get() = ProjectsClient(managementTransport("projects") { it.projects })

    /** RFC 9396 authorization details type management operations. Throws until [initialize] has run. */
    val authorizationDetailTypes: AuthorizationDetailTypesClient
        get() = AuthorizationDetailTypesClient(managementTransport("authorization-detail-types") { it.authorizationDetailTypes })

    /** Configuration import and export. Throws until [initialize] has run. */
    val configuration: ConfigurationClient
        get() =
            ConfigurationClient(
                importTransport = managementTransport("import") { it.import },
                exportTransport = managementTransport("export") { it.export },
            )

    /**
     * The administration flows the server configures for operations that change state. They always run
     * against `baseUrl`, or the API base in backend mode. Throws until [initialize] has run.
     */
    val administration: AdministrationClient
        get() = AdministrationClient(managementTransport("flows") { null })

    /**
     * Returns the permissions the signed-in user's access token carries, read from its `scopes` or `scope`
     * claim. Returns an empty list when nobody is signed in or the token is not a JWT. Test one against a
     * required permission with [dev.thunderid.android.api.management.hasPermission]. The result only decides what
     * to show: the server still decides every request.
     *
     * In backend mode they come only from the backend's session (`scope`): the token stays on the backend, so
     * a session without `scope` grants nothing.
     */
    suspend fun getGrantedPermissions(): List<String> {
        if (!runCatching { isSignedIn() }.getOrDefault(false)) return emptyList()
        backend?.let { backend ->
            val scope = runCatching { backend.getSession().scope }.getOrNull()
            return scope?.split(' ')?.filter { it.isNotEmpty() } ?: emptyList()
        }
        return getTokenPermissions(runCatching { getAccessToken() }.getOrNull())
    }

    private fun managementTransport(
        collection: String,
        override: (ThunderIDEndpoints) -> String?,
    ): ManagementTransport {
        val cfg = config ?: throw IAMException(ThunderIDErrorCode.SDK_NOT_INITIALIZED, "Call initialize() before using the SDK")
        val http = httpClient ?: throw IAMException(ThunderIDErrorCode.SDK_NOT_INITIALIZED, "Call initialize() before using the SDK")
        val apiBase = apiBaseUrl ?: cfg.baseUrl
        return ManagementTransport(
            http,
            override(cfg.endpoints) ?: "$apiBase/$collection",
            cfg.http.fetcher,
            apiBase,
        )
    }

    // MARK: - Authentication

    suspend fun signIn(
        payload: EmbeddedSignInPayload,
        request: EmbeddedFlowRequestConfig,
    ): EmbeddedFlowResponse = executeSignInFlow(payload, request, sessionData = null)

    /** Runs one sign-in flow step. [sessionData] goes on the request that starts the flow. */
    private suspend fun executeSignInFlow(
        payload: EmbeddedSignInPayload,
        request: EmbeddedFlowRequestConfig,
        sessionData: List<FlowSessionData>?,
    ): EmbeddedFlowResponse {
        requireInitialized()
        refuseOnBackend("Embedded sign-in")
        loading = true
        return try {
            val response =
                if (payload.flowId != null) {
                    flowClient!!.submit(payload.flowId, payload.actionId, payload.inputs, payload.challengeToken)
                } else {
                    val unit = request.organizationUnitId.takeIf { request.flowType == FlowType.AUTHENTICATION }
                    boundedFlowInProgress = unit != null
                    mapOrganizationUnitRefusal(unit) {
                        flowClient!!.initiate(
                            request.applicationId,
                            request.flowType,
                            attestationToken(),
                            unit,
                            sessionData = sessionData,
                        )
                    }
                }
            tokenStore?.updateFlowSessions(response.sessionData, response.clearedSessions)
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
     *
     * The URL goes to the provider's discovered `authorization_endpoint` once the SDK has read the discovery
     * document, which [signIn] does first, and to `{baseUrl}/oauth2/authorize` before then. With an OpenID
     * provider other than ThunderID, start sign-in with [signIn].
     */
    fun buildSignInUrl(options: SignInOptions? = null): String {
        val cfg = requireConfig()
        val unit = options?.organizationUnitId?.also { requireUnitId(it) }
        backend?.let { backend ->
            val redirectUri =
                cfg.afterSignInUrl
                    ?: throw IAMException(
                        ThunderIDErrorCode.INVALID_CONFIGURATION,
                        "afterSignInUrl is required to sign in through the backend",
                    )
            val (_, challenge) = pkceManager.generate()
            pendingOrganizationUnitId = unit
            return backend.signInUrl(redirectUri, challenge, unit)
        }
        if (cfg.pushedAuthorizationRequests) {
            throw IAMException(
                ThunderIDErrorCode.UNSUPPORTED_OPERATION,
                "pushedAuthorizationRequests is on: start sign-in with signIn(options), which pushes the request",
            )
        }
        val clientId = cfg.clientId ?: throw IAMException(ThunderIDErrorCode.INVALID_CONFIGURATION, "clientId required for redirect mode")
        val (_, challenge) = pkceManager.generate()
        pendingOrganizationUnitId = unit
        val params =
            listOfNotNull(
                "response_type" to "code",
                "client_id" to clientId,
                "redirect_uri" to (cfg.afterSignInUrl ?: ""),
                "scope" to cfg.scopes.joinToString(" "),
                "code_challenge" to challenge,
                "code_challenge_method" to "S256",
                options?.prompt?.let { "prompt" to it },
                options?.loginHint?.let { "login_hint" to it },
                options?.fidp?.let { "fidp" to it },
            )
        return "${oauthClient!!.authorizeUrl(unit)}?${encodeQuery(params)}"
    }

    /**
     * Starts a redirect-mode sign-in and returns the URL for the app to open in a Custom Tab, then hand the
     * redirect back to [handleRedirectCallback]. It is [buildSignInUrl] that can also push the request: when
     * `pushedAuthorizationRequests` is on, or the server's metadata sets `require_pushed_authorization_requests`,
     * the authorization request goes to `/oauth2/par` (RFC 9126) and the URL carries only the returned
     * `request_uri`. [SignInOptions.organizationUnitId] bounds the sign-in to an organization unit.
     *
     * @throws IAMException [ThunderIDErrorCode.INVALID_INPUT] when the server refuses the pushed request.
     */
    suspend fun signIn(options: SignInOptions = SignInOptions()): String {
        val cfg = requireConfig()
        if (backend != null) return buildSignInUrl(options)
        val oauth = oauthClient!!
        val push = cfg.pushedAuthorizationRequests || oauth.metadata()?.requirePushedAuthorizationRequests == true
        if (!push) return buildSignInUrl(options)
        val unit = options.organizationUnitId?.also { requireUnitId(it) }
        val clientId = cfg.clientId ?: throw IAMException(ThunderIDErrorCode.INVALID_CONFIGURATION, "clientId required for redirect mode")
        val (_, challenge) = pkceManager.generate()
        val form =
            buildMap {
                put("response_type", "code")
                put("client_id", clientId)
                put("redirect_uri", cfg.afterSignInUrl ?: "")
                put("scope", cfg.scopes.joinToString(" "))
                put("code_challenge", challenge)
                put("code_challenge_method", "S256")
                options.prompt?.let { put("prompt", it) }
                options.loginHint?.let { put("login_hint", it) }
                options.fidp?.let { put("fidp", it) }
            }
        val requestUri = oauth.pushAuthorizationRequest(form)
        pendingOrganizationUnitId = unit
        return "${oauth.authorizeUrl(unit)}?" + encodeQuery(listOf("client_id" to clientId, "request_uri" to requestUri))
    }

    suspend fun handleRedirectCallback(url: String): User = completeRedirect(url, switching = false)

    private suspend fun completeRedirect(
        url: String,
        switching: Boolean,
    ): User {
        val cfg = requireConfig()
        val unit = pendingOrganizationUnitId
        pendingOrganizationUnitId = null
        backend?.let { return completeBackendSignIn(it, url, keepOnFailure = switching, unit = unit) }
        val clientId = cfg.clientId ?: throw IAMException(ThunderIDErrorCode.INVALID_CONFIGURATION, "clientId required")
        if (unit != null) return completeBoundedSignIn(cfg, clientId, unit, url)
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
        val tokenResponse: TokenResponse = oauthClient!!.redeemCode(null, body)
        tokenResponse.idToken?.let { tokenValidator?.validate(it, null) }
        tokenStore!!.save(tokenResponse)
        return getUser()
    }

    /**
     * Redeems the code of a sign-in bounded to organization unit [unit] at the unit's token endpoint, which
     * is the only one that redeems it. The previous session is replaced only once the new tokens are in.
     */
    private suspend fun completeBoundedSignIn(
        cfg: ThunderIDConfig,
        clientId: String,
        unit: String,
        url: String,
    ): User {
        val params = queryParameters(url)
        params["error"]?.let { error ->
            pkceManager.clearVerifier()
            val serverError = ServerError(error, params["error_description"]?.let { ServerErrorMessage(defaultValue = it) })
            val code =
                if (error ==
                    "unauthorized_client"
                ) {
                    ThunderIDErrorCode.ORGANIZATION_UNIT_UNAVAILABLE
                } else {
                    ThunderIDErrorCode.AUTHENTICATION_FAILED
                }
            throw IAMException(code, "Sign-in to the organization unit was refused: $error", serverError = serverError)
        }
        val code =
            params["code"]?.takeIf { it.isNotEmpty() }
                ?: throw IAMException(ThunderIDErrorCode.INVALID_GRANT, "Authorization code missing from callback URL")
        val verifier =
            pkceManager.codeVerifier
                ?: throw IAMException(ThunderIDErrorCode.INVALID_GRANT, "PKCE verifier not found")
        pkceManager.clearVerifier()
        val form =
            mapOf(
                "grant_type" to "authorization_code",
                "code" to code,
                "client_id" to clientId,
                "redirect_uri" to (cfg.afterSignInUrl ?: ""),
                "code_verifier" to verifier,
            )
        replaceSession(oauthClient!!.redeemCode(unit, form))
        return getUser()
    }

    /** Validates [response]'s ID token, then replaces the whole token set with it, so the user is read again. */
    private suspend fun replaceSession(response: TokenResponse) {
        response.idToken?.let { tokenValidator?.validate(it, null) }
        tokenStore!!.replace(response)
        currentUser = null
    }

    // MARK: - Organization units

    /**
     * Lists the organization units the signed-in user can access, from `GET /users/me/ous` with the user's
     * token: the one the user lives in and every one the user is shared into.
     */
    suspend fun getAccessibleOrganizationUnits(
        options: AccessibleOrganizationUnitsOptions = AccessibleOrganizationUnitsOptions(),
    ): AccessibleOrganizationUnits {
        requireInitialized()
        val query =
            listOfNotNull(
                options.scope?.let { "scope=${it.value}" },
                options.limit?.let { "limit=$it" },
                options.offset?.let { "offset=$it" },
            ).joinToString("&")
        val path = if (query.isEmpty()) "/users/me/ous" else "/users/me/ous?$query"
        return httpClient!!.get<AccessibleOrganizationUnitsResponse>(path).toModel()
    }

    /**
     * The organization unit the signed-in session was decided for, from its `ouId`, `ouHandle` and `ouName`
     * claims, or `null` when the session carries none or nobody is signed in. It is for display only, never
     * for an authorization decision: the server decides every request.
     */
    suspend fun getCurrentOrganizationUnit(): CurrentOrganizationUnit? {
        requireInitialized()
        backend?.let { backend ->
            val session = runCatching { backend.getSession() }.getOrNull() ?: return null
            return if (session.isSignedIn) currentOrganizationUnitFrom(session.user) else null
        }
        val store = tokenStore ?: return null
        return listOfNotNull(store.idToken(), store.accessToken())
            .firstNotNullOfOrNull { currentOrganizationUnitFrom(jwtClaims(it)) }
    }

    /**
     * Signs in again bounded to organization unit [ouId]. The server reuses the SSO session when the user can
     * sign in there, so normally no credentials are asked. On success the client replaces its tokens (the
     * refresh token is bound to the unit), keeps the device secret and the flow sessions, and reads the user
     * again. On failure the previous session stays as it was.
     *
     * The switch takes the first path that applies:
     * - When sign-in runs on a backend, the backend's `ouSwitch` endpoint switches the session it holds, with no
     *   browser, and the session is read again. When the backend needs the user (`interaction_required`,
     *   `login_required`, …), a redirect sign-in through [SwitchOrganizationUnitOptions.browser] instead, when one
     *   is given.
     * - With a device secret from a `device_sso` sign-in and a `clientId`, an exchange of the secret at
     *   `{baseUrl}/ou/{ouId}/oauth2/token`, with no browser. A secret refused with `invalid_request` or
     *   `invalid_grant` is dropped, and any refusal but an unavailable unit goes on to the next path.
     * - With [SwitchOrganizationUnitOptions.browser], a redirect sign-in through the browser.
     * - Otherwise the application's `AUTHENTICATION` flow in embedded mode, presenting the SSO session handles
     *   kept from earlier flow steps. It returns [OrganizationUnitSwitchResult.SignInRequired] when the flow
     *   needs the user.
     *
     * @throws IAMException [ThunderIDErrorCode.ORGANIZATION_UNIT_UNAVAILABLE] when the unit does not exist
     * or the application cannot be used from it; the server does not tell the two apart.
     */
    suspend fun switchOrganizationUnit(
        ouId: String,
        options: SwitchOrganizationUnitOptions = SwitchOrganizationUnitOptions(),
    ): OrganizationUnitSwitchResult {
        val cfg = requireConfig()
        requireUnitId(ouId)
        backend?.let { return OrganizationUnitSwitchResult.Switched(switchOnBackend(it, ouId, options)) }
        var exchangeFailure: Exception? = null
        val secret = tokenStore?.deviceSecret()
        if (secret != null && !cfg.clientId.isNullOrEmpty()) {
            val tokens =
                try {
                    redeemDeviceSecret(cfg, ouId, secret)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // An unavailable unit fails every path; any other refusal goes on as if no secret were held.
                    if ((e as? IAMException)?.code == ThunderIDErrorCode.ORGANIZATION_UNIT_UNAVAILABLE) throw e
                    exchangeFailure = e
                    null
                }
            if (tokens != null) {
                tokenStore!!.replace(tokens)
                currentUser = null
                return OrganizationUnitSwitchResult.Switched(getUser())
            }
        }
        options.browser?.let { browser ->
            return OrganizationUnitSwitchResult.Switched(switchThroughBrowser(ouId, browser, options.signInOptions))
        }
        val applicationId = options.applicationId ?: cfg.applicationId
        if (applicationId == null) {
            // With no path left, the exchange's refusal says more than the missing configuration.
            throw exchangeFailure
                ?: IAMException(
                    ThunderIDErrorCode.INVALID_CONFIGURATION,
                    "An embedded switch needs applicationId, a device secret, or SwitchOrganizationUnitOptions.browser",
                )
        }
        val response =
            executeSignInFlow(
                EmbeddedSignInPayload(),
                EmbeddedFlowRequestConfig(applicationId, FlowType.AUTHENTICATION, ouId),
                // The kept session handles let the server reuse the SSO session without its cookie.
                sessionData = tokenStore?.flowSessions(),
            )
        return when (response.flowStatus) {
            FlowStatus.COMPLETE -> {
                OrganizationUnitSwitchResult.Switched(getUser())
            }

            FlowStatus.ERROR -> {
                throw IAMException(
                    ThunderIDErrorCode.AUTHENTICATION_FAILED,
                    response.failureReason ?: "Sign-in to the organization unit was refused",
                    serverError = response.error?.code?.let { ServerError(it) },
                )
            }

            else -> {
                OrganizationUnitSwitchResult.SignInRequired(response)
            }
        }
    }

    /**
     * Switches at the backend's `ouSwitch` endpoint, which switches the session the backend holds, then reads the
     * session again. When the backend needs the user, signs in to the unit through
     * [SwitchOrganizationUnitOptions.browser] instead, when one is given.
     */
    private suspend fun switchOnBackend(
        backend: BackendClient,
        ouId: String,
        options: SwitchOrganizationUnitOptions,
    ): User {
        val refusal = backend.switchOrganizationUnit(ouId)
        if (refusal == null) {
            currentUser = null
            return getUser()
        }
        val error = refusal.error
        val browser = options.browser
        if (error != null && error in INTERACTION_ERRORS && browser != null) {
            return switchThroughBrowser(ouId, browser, options.signInOptions)
        }
        val code =
            if (error == "unauthorized_client") {
                ThunderIDErrorCode.ORGANIZATION_UNIT_UNAVAILABLE
            } else {
                ThunderIDErrorCode.AUTHENTICATION_FAILED
            }
        throw IAMException(
            code,
            "The backend refused the switch: ${error ?: "no error"}",
            serverError = error?.let { ServerError(it) },
        )
    }

    /** Switches with a redirect sign-in bounded to [ouId], opened through [browser]. */
    private suspend fun switchThroughBrowser(
        ouId: String,
        browser: suspend (url: String) -> String,
        signInOptions: SignInOptions,
    ): User {
        val url = signIn(signInOptions.copy(organizationUnitId = ouId))
        val callback =
            try {
                browser(url)
            } catch (e: Throwable) {
                // Dismissed or failed: forget the switch, so a later callback is not redeemed for it.
                pendingOrganizationUnitId = null
                pkceManager.clearVerifier()
                throw e
            }
        return completeRedirect(callback, switching = true)
    }

    /**
     * Exchanges the device secret at organization unit [ouId]'s token endpoint (RFC 8693), which signs the user
     * in to the unit from the SSO session, and validates the ID token it returns. A secret the server refuses as
     * unusable (expired, another user's, revoked) is dropped.
     */
    private suspend fun redeemDeviceSecret(
        cfg: ThunderIDConfig,
        ouId: String,
        secret: String,
    ): TokenResponse {
        val form =
            mapOf(
                "grant_type" to "urn:ietf:params:oauth:grant-type:token-exchange",
                "subject_token" to secret,
                "subject_token_type" to "urn:ietf:params:oauth:token-type:jwt",
                "client_id" to requireClientId(cfg),
                "scope" to cfg.scopes.joinToString(" "),
            )
        val response =
            try {
                oauthClient!!.requestToken(ouId, form, "The device secret exchange was refused")
            } catch (e: IAMException) {
                when (e.serverError?.code) {
                    "invalid_request", "invalid_grant" -> tokenStore?.removeDeviceSecret()
                }
                throw e
            }
        response.idToken?.let { tokenValidator?.validate(it, null) }
        return response
    }

    // MARK: - Backchannel and device authorization

    /**
     * Starts a client-initiated backchannel authentication (CIBA) request at the server's discovered
     * `backchannel_authentication_endpoint`, with the configured scopes. The client authenticates as a public
     * client (`token_endpoint_auth_method: none`), which is the only way an app on a device can: a client
     * registered with a secret cannot use it from this SDK.
     *
     * @throws IAMException [ThunderIDErrorCode.INVALID_INPUT] unless exactly one hint is given, and
     * [ThunderIDErrorCode.UNSUPPORTED_OPERATION] when the server's metadata names no backchannel endpoint or
     * sign-in runs on a backend.
     */
    suspend fun initiateCIBA(options: CIBAInitiateOptions): CIBAInitiateResponse {
        val cfg = requireConfig()
        refuseOnBackend("initiateCIBA")
        return oauthClient!!.initiateCIBA(requireClientId(cfg), cfg.scopes, options)
    }

    /**
     * Polls the token endpoint for [authReqId] with the `urn:openid:params:grant-type:ciba` grant, every
     * [interval] seconds and five more after each `slow_down`, and stores the tokens as a sign-in does.
     * Cancel the coroutine to stop polling.
     *
     * @throws IAMException [ThunderIDErrorCode.AUTHENTICATION_FAILED] when the user denies the request or it
     * expires; [IAMException.serverError] carries the OAuth error (`access_denied`, `expired_token`).
     */
    suspend fun pollCIBA(
        authReqId: String,
        interval: Int,
    ): TokenResponse {
        val cfg = requireConfig()
        refuseOnBackend("pollCIBA")
        val form =
            mapOf(
                "grant_type" to "urn:openid:params:grant-type:ciba",
                "auth_req_id" to authReqId,
                "client_id" to requireClientId(cfg),
            )
        return oauthClient!!.pollToken(form, interval).also { replaceSession(it) }
    }

    /**
     * Starts an OAuth 2.0 device authorization request (RFC 8628) at `/oauth2/device-authorize`, for an app on
     * an input-constrained device such as Android TV. Show the user [DeviceAuthorization.userCode] and
     * [DeviceAuthorization.verificationUri], then [pollDeviceAuthorization]. The device code is a credential:
     * never show or log it.
     */
    suspend fun startDeviceAuthorization(): DeviceAuthorization {
        val cfg = requireConfig()
        refuseOnBackend("startDeviceAuthorization")
        return oauthClient!!.startDeviceAuthorization(requireClientId(cfg), cfg.scopes)
    }

    /**
     * Polls the token endpoint with the `urn:ietf:params:oauth:grant-type:device_code` grant until the user
     * approves the device, under the same rules as [pollCIBA], and stores the tokens on success.
     *
     * @throws IAMException [ThunderIDErrorCode.AUTHENTICATION_FAILED] when the user denies the device or the
     * code expires; [IAMException.serverError] carries the OAuth error (`access_denied`, `expired_token`).
     */
    suspend fun pollDeviceAuthorization(
        deviceCode: String,
        interval: Int,
    ): TokenResponse {
        val cfg = requireConfig()
        refuseOnBackend("pollDeviceAuthorization")
        val form =
            mapOf(
                "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                "device_code" to deviceCode,
                "client_id" to requireClientId(cfg),
            )
        return oauthClient!!.pollToken(form, interval).also { replaceSession(it) }
    }

    /** [pollDeviceAuthorization] for the request [authorization] started. */
    suspend fun pollDeviceAuthorization(authorization: DeviceAuthorization): TokenResponse =
        pollDeviceAuthorization(authorization.deviceCode, authorization.interval)

    /**
     * Drives one step of the server's `DEVICE_GRANT` flow in embedded mode, so a user approves a device from
     * this app. A payload without a flow id starts the flow, with the user code the user typed as the
     * `user_code` input; one with a flow id continues it. When the flow ends, its assertion is relayed to the
     * server, which approves or denies the device's request. The flow never signs this app in.
     */
    suspend fun verifyDeviceCode(
        payload: EmbeddedSignInPayload,
        applicationId: String? = null,
    ): EmbeddedFlowResponse {
        val cfg = requireConfig()
        refuseOnBackend("verifyDeviceCode")
        val flow = flowClient!!
        val (body, headers) =
            if (payload.flowId != null) {
                val submit = flow.submitBody(payload.flowId, payload.actionId, payload.challengeToken).toMutableMap()
                submit["verbose"] = true
                if (payload.inputs.isNotEmpty()) submit["inputs"] = payload.inputs
                submit to emptyMap()
            } else {
                val appId =
                    applicationId ?: cfg.applicationId
                        ?: throw IAMException(ThunderIDErrorCode.INVALID_CONFIGURATION, "applicationId required for verifyDeviceCode")
                val start = flow.initiateBody(appId, FlowType.DEVICE_GRANT, inputs = payload.inputs)
                start to (attestationToken()?.let { mapOf("Attestation-Token" to it) } ?: emptyMap())
            }
        return DeviceGrantRelay(httpClient!!, apiBaseUrl ?: cfg.baseUrl).execute(body, headers).withErrorAsFailureReason()
    }

    /** Starts the server's `DEVICE_GRANT` flow with [userCode], the code the device shows. See [verifyDeviceCode]. */
    suspend fun verifyDeviceCode(
        userCode: String,
        applicationId: String? = null,
    ): EmbeddedFlowResponse = verifyDeviceCode(EmbeddedSignInPayload(inputs = mapOf("user_code" to userCode)), applicationId)

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
                runCatching { oauthClient!!.revoke(body) }
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
     * Sends a request to the application's API when sign-in runs on a backend. A request under the API base
     * (`endpoints.backend.apiBaseUrl`, or `baseUrl` when it is not set) goes through the backend's proxy
     * with the session handle; one under a bypass URL goes straight there, with the access token from the
     * backend's `token` endpoint unless `attachToken` is off. The response comes back whatever its status,
     * so the API's own `401` reaches the caller untouched.
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
        tokenStore?.updateFlowSessions(response.sessionData, response.clearedSessions)
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
            mutableMapOf(
                "grant_type" to "urn:ietf:params:oauth:grant-type:token-exchange",
                "subject_token" to config.subjectToken,
                "subject_token_type" to config.subjectTokenType,
            )
        (this.config?.clientId ?: this.config?.applicationId)?.takeIf { it.isNotEmpty() }?.let { body["client_id"] = it }
        config.requestedTokenType?.let { body["requested_token_type"] = it }
        config.audience?.let { body["audience"] = it }
        val response: TokenResponse = oauthClient!!.requestToken(null, body, "The token exchange was refused")
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
        val http = httpClient!!
        val userinfo: com.google.gson.JsonObject =
            oauthClient!!.userinfoUrl()?.let { http.send("GET", it) } ?: http.get("/oauth2/userinfo")
        val user = User(claimsFrom(userinfo))
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
     * Changing a credential the user already holds needs its [currentValue]. A user setting a
     * credential for the first time has none to send.
     */
    suspend fun updateUserCredentials(
        attribute: String = "password",
        currentValue: String? = null,
        newValue: String,
    ) {
        requireInitialized()
        val body = mutableMapOf("name" to attribute, "newValue" to newValue)
        if (!currentValue.isNullOrEmpty()) body["currentValue"] = currentValue
        httpClient!!.post<Unit>("/users/me/update-credentials", body)
    }

    // MARK: - Self user attribute verification

    /**
     * Starts verifying one of the signed-in user's own attributes, such as `email`, by running the
     * `SELF` flow named by [handle]. The flow sends a code or a link to the attribute's current
     * value; continue it with the other [verifyAttribute] until the step is
     * [FlowStatus.COMPLETE], which means the server recorded the verification. Read the result from
     * [UserProfile.verifiedAttributes].
     *
     * The request goes to `/flow/execute` on the API base (`endpoints.backend.apiBaseUrl`, else `baseUrl`)
     * with the signed-in user's access token, from which the server takes the user, and no user or
     * application ID.
     *
     * @throws IAMException [ThunderIDErrorCode.INVALID_INPUT] without sending anything when [handle] or
     * [attribute] is blank; [ThunderIDErrorCode.VERIFICATION_FAILED] when the server refuses the request
     * (an `ERROR` step, or a 400, 403, 404 or 409 with the server's error), without the response body;
     * [ThunderIDErrorCode.AUTHENTICATION_FAILED] when the server does not accept the user's token.
     */
    suspend fun verifyAttribute(
        attribute: String,
        handle: String = AttributeVerification.DEFAULT_FLOW_HANDLE,
    ): EmbeddedFlowResponse {
        val cfg = requireConfig()
        if (handle.isBlank()) throw IAMException(ThunderIDErrorCode.INVALID_INPUT, "A new SELF flow needs a handle")
        if (attribute.isBlank()) throw IAMException(ThunderIDErrorCode.INVALID_INPUT, "The attribute to verify is required")
        return flowClient!!.startSelfFlow(
            selfFlowUrl(cfg),
            handle,
            mapOf(AttributeVerification.ATTRIBUTE_INPUT to attribute),
        )
    }

    /**
     * Continues an attribute verification started by the other [verifyAttribute] with the step the
     * user submitted: [EmbeddedSignInPayload.flowId] is the step's execution ID. Sent the same way, with
     * the access token.
     *
     * @throws IAMException [ThunderIDErrorCode.INVALID_INPUT] without sending anything when [payload] has
     * no execution ID; [ThunderIDErrorCode.VERIFICATION_FAILED] when the server refuses it, as for the start.
     */
    suspend fun verifyAttribute(payload: EmbeddedSignInPayload): EmbeddedFlowResponse {
        val cfg = requireConfig()
        val flowId =
            payload.flowId?.takeIf { it.isNotBlank() }
                ?: throw IAMException(
                    ThunderIDErrorCode.INVALID_INPUT,
                    "A continuation needs the step's execution ID; start with verifyAttribute(attribute)",
                )
        return flowClient!!.continueSelfFlow(
            selfFlowUrl(cfg),
            flowId,
            payload.actionId,
            payload.inputs,
            payload.challengeToken,
        )
    }

    // On the API base, like the profile, so backend mode sends it through the proxy.
    private fun selfFlowUrl(cfg: ThunderIDConfig) = "${apiBaseUrl ?: cfg.baseUrl}/flow/execute"

    // MARK: - Authorization Details

    /**
     * Returns the RFC 9396 authorization details types a client may request, each with the fields it may send
     * and the changes it may make at the token endpoint. Public: no token is sent. [fetcher] takes precedence
     * over `ThunderIDConfig.http.fetcher`.
     */
    suspend fun getSupportedAuthorizationDetailsTypes(fetcher: ThunderIDFetcher? = null): List<SupportedAuthorizationDetailsType> =
        authorizationDetailsTypesClient().list(fetcher)

    /** Returns one authorization details type a client may request, by its [type] value. See [getSupportedAuthorizationDetailsTypes]. */
    suspend fun getSupportedAuthorizationDetailsType(
        type: String,
        fetcher: ThunderIDFetcher? = null,
    ): SupportedAuthorizationDetailsType = authorizationDetailsTypesClient().get(type, fetcher)

    private fun authorizationDetailsTypesClient(): AuthorizationDetailsTypesClient {
        val cfg = requireConfig()
        val http = httpClient ?: throw IAMException(ThunderIDErrorCode.SDK_NOT_INITIALIZED, "Call initialize() before using the SDK")
        return AuthorizationDetailsTypesClient(http, apiBaseUrl ?: cfg.baseUrl, cfg.http.fetcher)
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
        keepOnFailure: Boolean = false,
        unit: String? = null,
    ): User {
        val params = queryParameters(url)
        params["error"]?.let {
            pkceManager.clearVerifier()
            val code =
                if (unit != null && it == "unauthorized_client") {
                    ThunderIDErrorCode.ORGANIZATION_UNIT_UNAVAILABLE
                } else {
                    ThunderIDErrorCode.AUTHENTICATION_FAILED
                }
            throw IAMException(code, "The backend refused sign-in: $it", serverError = ServerError(it))
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
            val session = backend.startSession(code, verifier, keepOnFailure)
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

    private fun requireUnitId(ouId: String) {
        if (ouId.isBlank()) throw IAMException(ThunderIDErrorCode.INVALID_INPUT, "An organization unit id is required")
    }

    private fun requireClientId(cfg: ThunderIDConfig): String =
        cfg.clientId?.takeIf { it.isNotEmpty() }
            ?: throw IAMException(ThunderIDErrorCode.INVALID_CONFIGURATION, "clientId required")

    /**
     * Runs [start], the request that starts a flow bounded to [unit], and reports the server's refusal of the
     * unit (`FES-1023`: it does not exist, or the application cannot be used from it) as
     * [ThunderIDErrorCode.ORGANIZATION_UNIT_UNAVAILABLE].
     */
    private suspend fun mapOrganizationUnitRefusal(
        unit: String?,
        start: suspend () -> EmbeddedFlowResponse,
    ): EmbeddedFlowResponse =
        try {
            start()
        } catch (e: IAMException) {
            if (unit == null || e.serverError?.code != "FES-1023") throw e
            throw IAMException(
                ThunderIDErrorCode.ORGANIZATION_UNIT_UNAVAILABLE,
                "The organization unit is not accessible to this application",
                e,
                e.serverError,
            )
        }

    /** A JWT's claims, or `null` when [token] is not one. Reads the payload without verifying the signature. */
    private fun jwtClaims(token: String): Map<String, Any?>? {
        val parts = token.split(".")
        if (parts.size != 3) return null
        return runCatching {
            val json =
                String(
                    java.util.Base64
                        .getUrlDecoder()
                        .decode(parts[1].trimEnd('=')),
                    Charsets.UTF_8,
                )
            val type = object : com.google.gson.reflect.TypeToken<Map<String, Any?>>() {}.type
            com.google.gson
                .Gson()
                .fromJson<Map<String, Any?>>(json, type)
        }.getOrNull()
    }

    private fun requireInitialized() {
        config ?: throw IAMException(ThunderIDErrorCode.SDK_NOT_INITIALIZED, "Call initialize() before using the SDK")
    }

    private fun requireConfig(): ThunderIDConfig =
        config ?: throw IAMException(ThunderIDErrorCode.SDK_NOT_INITIALIZED, "Call initialize() before using the SDK")

    private fun validateConfig(config: ThunderIDConfig) {
        if (config.baseUrl.isEmpty()) throw IAMException(ThunderIDErrorCode.INVALID_CONFIGURATION, "baseUrl is required")
        if (!config.baseUrl.startsWith("https://")) throw IAMException(ThunderIDErrorCode.INVALID_CONFIGURATION, "baseUrl must use HTTPS")
        config.endpoints.wellKnown?.let { wellKnown ->
            val uri = runCatching { java.net.URI(wellKnown) }.getOrNull()
            if (uri?.scheme != "https" || uri.host.isNullOrEmpty()) {
                throw IAMException(ThunderIDErrorCode.INVALID_CONFIGURATION, "endpoints.wellKnown must be an absolute HTTPS URL")
            }
        }
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
        val tokens = TokenResponse(accessToken = assertion, tokenType = "Bearer")
        // A flow bounded to an organization unit replaces the whole session, as a switch must.
        if (boundedFlowInProgress) tokenStore!!.replace(tokens) else tokenStore!!.save(tokens)
        boundedFlowInProgress = false
        try {
            val claims = decodeJwtToken(assertion)
            currentUser = User(claims.filterValues { it != null }.mapValues { it.value as Any })
        } catch (_: Exception) {
            // assertion is stored; user info will be fetched via getUser() on next access
        }
    }
}
