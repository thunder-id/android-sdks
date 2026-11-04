// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.playground

import android.content.Context
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.net.Uri
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import dev.thunderid.android.EmbeddedFlowRequestConfig
import dev.thunderid.android.EmbeddedFlowResponse
import dev.thunderid.android.EmbeddedSignInPayload
import dev.thunderid.android.FlowComponent
import dev.thunderid.android.FlowStatus
import dev.thunderid.android.ThunderIDHttpRequest
import dev.thunderid.android.api.account.AccessibleOrganizationUnitsOptions
import dev.thunderid.android.api.account.OrganizationUnitScope
import dev.thunderid.android.api.auth.CIBAInitiateOptions
import dev.thunderid.android.api.auth.FederatedAuthSession
import dev.thunderid.android.api.auth.OrganizationUnitSwitchResult
import dev.thunderid.android.api.auth.SwitchOrganizationUnitOptions
import dev.thunderid.android.api.auth.customTabBrowser
import dev.thunderid.android.api.management.AccessStatusValue
import dev.thunderid.android.api.management.AccountLifecycleSubjectKind
import dev.thunderid.android.api.management.AdministrationFlowConfigKey
import dev.thunderid.android.api.management.GroupMemberType
import dev.thunderid.android.api.management.NotificationChannel
import dev.thunderid.android.api.management.RoleAssigneeType
import dev.thunderid.android.api.management.accountLifecycleActions
import dev.thunderid.android.api.management.getTokenPermissions
import dev.thunderid.android.api.management.hasPermission
import dev.thunderid.compose.ThunderIDState
import dev.thunderid.compose.i18n.DefaultStrings
import dev.thunderid.compose.i18n.ThunderIDI18n
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

enum class CheckStatus { IDLE, RUNNING, PASS, FAIL, UNAVAILABLE }

data class CheckResult(
    val status: CheckStatus,
    val note: String = "",
)

data class CheckDef(
    val id: String,
    val section: String,
    val name: String,
    val api: String,
    // Interactive checks open a browser or a sheet, so "Run all" only runs their preflight.
    val interactive: Boolean = false,
)

val CheckDefs =
    listOf(
        CheckDef("browser", "Authentication", "Sign in (system browser)", "buildSignInUrl() · Custom Tabs", interactive = true),
        CheckDef("native", "Authentication", "Sign in (app-native flow)", "SignIn() · client.signIn()", interactive = true),
        CheckDef("passkey", "Authentication", "Passkey sign-in", "PasskeyClient · Credential Manager"),
        CheckDef("biometric", "Authentication", "Biometric unlock", "BiometricPrompt"),
        CheckDef("signout", "Authentication", "Sign out", "client.signOut()", interactive = true),
        CheckDef("deeplink", "Authentication", "Redirect callback", "afterSignInUrl · intent filter"),
        CheckDef("backend", "Authentication", "Sign in on a backend", "endpoints.backend · httpRequest()"),
        CheckDef("refresh", "Session", "Token refresh", "getAccessToken() · auto refresh"),
        CheckDef("storage", "Session", "Secure token storage", "EncryptedStorageAdapter"),
        CheckDef("user", "Session", "User info", "client.getUser()"),
        CheckDef("orgs", "Session", "Organization units", "getAccessibleOrganizationUnits() · getCurrentOrganizationUnit()"),
        CheckDef(
            "ouSwitch",
            "Session",
            "Switch organization unit",
            "switchOrganizationUnit() · customTabBrowser()",
            interactive = true,
        ),
        CheckDef("par", "Authentication", "Pushed authorization requests", "signIn(options) · /oauth2/par"),
        CheckDef("ciba", "Authentication", "Backchannel authentication (CIBA)", "initiateCIBA() · pollCIBA()", interactive = true),
        CheckDef(
            "deviceGrant",
            "Authentication",
            "Device authorization",
            "startDeviceAuthorization() · verifyDeviceCode()",
            interactive = true,
        ),
        CheckDef("endpoints", "Endpoints", "Management endpoints", "ThunderIDConfig.endpoints · ThunderIDFetcher"),
        CheckDef("groups", "Management", "Groups", "client.groups.list() · get()"),
        CheckDef("groupMembers", "Management", "Group members", "client.groups.listMembers()"),
        CheckDef("roles", "Management", "Roles", "client.roles.list() · get()"),
        CheckDef("roleAssignments", "Management", "Role assignments", "client.roles.listAssignments()"),
        CheckDef("userGroups", "Management", "User groups", "client.users.listGroups()"),
        CheckDef("userTypes", "Management", "User types", "client.userTypes.list() · get()"),
        CheckDef("agentTypes", "Management", "Agent types", "client.agentTypes.list() · get()"),
        CheckDef("userUsages", "Management", "User usages", "client.users.getUsages()"),
        CheckDef("administration", "Management", "Administration flows", "client.administration.resolveFlows()"),
        CheckDef("agentGroups", "Management", "Agent groups", "client.agents.listGroups()"),
        CheckDef("agentRoles", "Management", "Agent roles", "client.agents.listRoles()"),
        CheckDef("permissions", "Management", "Permission checks", "getGrantedPermissions() · hasPermission()"),
        CheckDef(
            "organizationUnits",
            "Management",
            "Organization units",
            "client.organizationUnits.list() · getByPath() · listUsers()",
        ),
        CheckDef("resourceServers", "Management", "Resource servers", "client.resourceServers.list() · listResources() · listActions()"),
        CheckDef("lifecycle", "Management", "Account lifecycle flows", "client.administration.resolveFlows() for suspend/unlock"),
        CheckDef(
            "platform",
            "Management",
            "Connections, flows, design, translations",
            "client.connections · flows · notificationTemplates · design · translations",
        ),
        CheckDef(
            "operations",
            "Management",
            "Server configuration and gateways",
            "client.serverConfig · gateways (master) · variables · secrets · configurationVersions · " +
                "credentialConfigurations · presentationDefinitions",
        ),
        CheckDef(
            "sharing",
            "Management",
            "Role and flow sharing, groups by path",
            "client.roles · flows: listSharingPolicies() · getOverlayRules() · flows.getVariables() · groups.listByPath()",
        ),
        CheckDef(
            "projects",
            "Management",
            "Projects and authorization details types",
            "client.projects · authorizationDetailTypes · getSupportedAuthorizationDetailsTypes()",
        ),
        CheckDef("theme", "UI", "Theming and dark mode", "MaterialTheme · Material 3"),
        CheckDef("i18n", "UI", "Translations", "ThunderIDI18n"),
    )

enum class Health { UNKNOWN, CHECKING, HEALTHY, UNHEALTHY }

/** Runs the capability checks against the real SDK and holds their results across tab switches. */
class ChecksController(
    private val context: Context,
    private val storage: RecordingStorageAdapter,
) {
    val results = mutableStateMapOf<String, CheckResult>()
    var runningAll by mutableStateOf(false)
        private set
    var health by mutableStateOf(Health.UNKNOWN)
        private set
    var healthNote by mutableStateOf("")
        private set

    /** The collection URLs the SDK requested during the endpoints check, keyed by collection name. */
    val observedEndpoints = mutableStateMapOf<String, String>()

    /** Set while the app-native SignIn sheet is open for the "native" check. */
    var signInSheetOpen by mutableStateOf(false)

    fun result(id: String): CheckResult = results[id] ?: CheckResult(CheckStatus.IDLE)

    suspend fun runAll(
        state: ThunderIDState,
        scheme: ColorScheme,
    ) {
        if (runningAll) return
        runningAll = true
        EventLog.info("checks:runAll", "${CheckDefs.size} checks", source = "playground")
        CheckDefs.forEach { results[it.id] = CheckResult(CheckStatus.RUNNING) }
        try {
            checkHealth()
            CheckDefs.forEach { run(it, state, scheme, interactive = false) }
        } finally {
            runningAll = false
        }
    }

    suspend fun run(
        def: CheckDef,
        state: ThunderIDState,
        scheme: ColorScheme,
        interactive: Boolean = true,
    ) {
        results[def.id] = CheckResult(CheckStatus.RUNNING)
        val result =
            try {
                if (!state.isInitialized && def.id !in OFFLINE_CHECKS) {
                    fail("SDK not initialized: ${state.error ?: "check config.properties"}")
                } else {
                    execute(def, state, scheme, interactive)
                }
            } catch (e: CancellationException) {
                fail("Cancelled: the browser was closed before the redirect")
            } catch (e: Exception) {
                fail(e.message ?: e.javaClass.simpleName)
            }
        results[def.id] = result
        val detail = result.note.ifBlank { result.status.name.lowercase() }
        when (result.status) {
            CheckStatus.PASS -> EventLog.ok("check:${def.id}", detail, source = "check")
            CheckStatus.FAIL -> EventLog.error("check:${def.id}", detail, source = "check")
            else -> EventLog.info("check:${def.id}", detail, source = "check")
        }
    }

    /** Called by the SignIn sheet when the app-native flow finishes. */
    fun completeNativeSignIn(
        success: Boolean,
        note: String,
    ) {
        signInSheetOpen = false
        results["native"] = if (success) pass(note) else fail(note)
        if (success) EventLog.ok("signIn:completed", "mode=app-native", source = "sdk")
    }

    suspend fun checkHealth() {
        health = Health.CHECKING
        val url = "${PlaygroundSettings.baseUrl}/health/readiness"
        val outcome =
            withContext(Dispatchers.IO) {
                runCatching {
                    val connection = URL(url).openConnection() as HttpURLConnection
                    if (BuildConfig.DEBUG && connection is HttpsURLConnection) trustAll(connection)
                    connection.connectTimeout = 4000
                    connection.readTimeout = 4000
                    try {
                        connection.responseCode
                    } finally {
                        connection.disconnect()
                    }
                }
            }
        val code = outcome.getOrNull()
        health = if (code in 200..299) Health.HEALTHY else Health.UNHEALTHY
        healthNote =
            when {
                PlaygroundSettings.baseUrl.isBlank() -> "THUNDERID_BASE_URL not set"
                code != null -> "HTTP $code"
                else -> outcome.exceptionOrNull()?.message ?: "unreachable"
            }
        if (health == Health.HEALTHY) {
            EventLog.ok("server:healthy", url, source = "playground")
        } else {
            EventLog.error("server:unhealthy", healthNote, source = "playground")
        }
    }

    private suspend fun execute(
        def: CheckDef,
        state: ThunderIDState,
        scheme: ColorScheme,
        interactive: Boolean,
    ): CheckResult =
        when (def.id) {
            "browser" -> browserSignIn(state, interactive)
            "native" -> nativeSignIn(state, interactive)
            "passkey" -> passkey(state)
            "biometric" -> biometric()
            "signout" -> signOut(state, interactive)
            "deeplink" -> redirectCallback()
            "backend" -> backendSession(state)
            "refresh" -> tokenRefresh(state)
            "storage" -> secureStorage()
            "user" -> userInfo(state)
            "orgs" -> organizationUnitsOfUser(state)
            "ouSwitch" -> switchOrganizationUnit(state, interactive)
            "par" -> pushedAuthorization(state)
            "ciba" -> ciba(state, interactive)
            "deviceGrant" -> deviceGrant(state, interactive)
            "endpoints" -> endpoints(state)
            "groups" -> management(state) { groups(state) }
            "groupMembers" -> management(state) { groupMembers(state) }
            "roles" -> management(state) { roles(state) }
            "roleAssignments" -> management(state) { roleAssignments(state) }
            "userGroups" -> management(state) { userGroups(state) }
            "userTypes" -> management(state) { userTypes(state) }
            "agentTypes" -> management(state) { agentTypes(state) }
            "userUsages" -> management(state) { userUsages(state) }
            "administration" -> management(state) { administration(state) }
            "agentGroups" -> management(state) { agentGroups(state) }
            "agentRoles" -> management(state) { agentRoles(state) }
            "permissions" -> permissions(state)
            "organizationUnits" -> management(state) { organizationUnits(state) }
            "resourceServers" -> management(state) { resourceServers(state) }
            "lifecycle" -> management(state) { lifecycle(state) }
            "platform" -> management(state) { platform(state) }
            "operations" -> management(state) { operations(state) }
            "sharing" -> management(state) { sharing(state) }
            "projects" -> management(state) { projects(state) }
            "theme" -> theme(scheme)
            "i18n" -> translations()
            else -> fail("Unknown check")
        }

    private suspend fun browserSignIn(
        state: ThunderIDState,
        interactive: Boolean,
    ): CheckResult {
        if (PlaygroundSettings.clientId.isBlank() && !state.client.isBackendMode()) {
            return fail("THUNDERID_CLIENT_ID is not set in config.properties")
        }
        val provider =
            context.packageManager
                .queryIntentServices(Intent("android.support.customtabs.action.CustomTabsService"), 0)
                .firstOrNull()
                ?.serviceInfo
                ?.packageName
                ?: return fail("No browser with Custom Tabs support is installed")
        val url = state.client.buildSignInUrl()
        if (!interactive) return idle("Ready ($provider). Tap to sign in through the browser.")
        EventLog.info("signIn:started", "mode=system-browser", source = "sdk")
        val redirect = FederatedAuthSession.launch(context, url)
        redirect.getQueryParameter("error")?.let { return fail("Server returned error=$it") }
        val user = state.client.handleRedirectCallback(redirect.toString())
        state.refresh()
        EventLog.ok("signIn:completed", "mode=system-browser", source = "sdk")
        return pass("Signed in as ${user.username ?: user.email ?: user.sub ?: "user"} via $provider")
    }

    private suspend fun nativeSignIn(
        state: ThunderIDState,
        interactive: Boolean,
    ): CheckResult {
        if (interactive) {
            if (PlaygroundSettings.applicationId.isBlank()) return fail("THUNDERID_APPLICATION_ID is not set")
            EventLog.info("signIn:started", "mode=app-native", source = "sdk")
            signInSheetOpen = true
            return CheckResult(CheckStatus.RUNNING, "Complete the flow in the sheet")
        }
        val response = initiateFlow(state)
        val inputs = response.data?.inputs?.size ?: 0
        val actions = flowComponents(response).size
        return idle("Flow ready: $inputs inputs, $actions components. Tap to sign in.")
    }

    private suspend fun passkey(state: ThunderIDState): CheckResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return fail("Credential Manager passkeys need Android 9 or later")
        }
        val response = initiateFlow(state)
        val offered =
            flowComponents(response).any { component ->
                listOf(component.ref, component.label, component.icon, component.id, component.type)
                    .any { it?.contains("passkey", ignoreCase = true) == true }
            } ||
                response.data?.actions.orEmpty().any { action ->
                    listOf(action.ref, action.label, action.icon, action.id)
                        .any { it?.contains("passkey", ignoreCase = true) == true }
                }
        return if (offered) {
            pass("Flow offers a passkey action; SignIn() runs it through PasskeyClient")
        } else {
            fail("The sign-in flow for this application has no passkey action")
        }
    }

    private fun biometric(): CheckResult {
        val device =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val manager = context.getSystemService(BiometricManager::class.java)
                val code =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                    } else {
                        @Suppress("DEPRECATION")
                        manager.canAuthenticate()
                    }
                when (code) {
                    BiometricManager.BIOMETRIC_SUCCESS -> "biometrics enrolled"
                    BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "no biometrics enrolled"
                    BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "no biometric hardware"
                    else -> "biometrics unavailable ($code)"
                }
            } else {
                "BiometricManager needs Android 10"
            }
        return unavailable("The Android SDK has no biometric unlock API. Device: $device.")
    }

    private suspend fun signOut(
        state: ThunderIDState,
        interactive: Boolean,
    ): CheckResult {
        if (!state.isSignedIn) return fail("Not signed in. Sign in first.")
        if (!interactive) return idle("Signed in. Tap to sign out (ends the session).")
        EventLog.info("signOut:requested", "client.signOut()", source = "sdk")
        val after = state.client.signOut()
        state.refresh()
        return if (state.isSignedIn) {
            fail("Session still present after signOut()")
        } else {
            EventLog.ok("signOut:completed", after, source = "sdk")
            pass("Tokens cleared and revoked; afterSignOutUrl=$after")
        }
    }

    private suspend fun backendSession(state: ThunderIDState): CheckResult {
        if (!state.client.isBackendMode()) {
            return unavailable("Set THUNDERID_BACKEND_URL in config.properties to sign in on a backend.")
        }
        if (!state.client.checkSession()) return idle("No backend session. Sign in through the system browser first.")
        val request =
            ThunderIDHttpRequest(
                method = "GET",
                url = "${PlaygroundSettings.baseUrl}/users/me",
                headers = mapOf("Accept" to "application/json"),
                body = null,
            )
        val response = state.client.httpRequest(request)
        EventLog.info("backend:proxy", "GET /users/me -> ${response.statusCode}", source = "sdk")
        return if (response.statusCode in 200..299) {
            pass("Session live; /users/me answered ${response.statusCode} through the proxy")
        } else {
            fail("The proxy answered ${response.statusCode}")
        }
    }

    private fun redirectCallback(): CheckResult {
        val uri = Uri.parse(PlaygroundSettings.afterSignInUrl)
        val intent = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
        val handler =
            context.packageManager
                .queryIntentActivities(intent, 0)
                .firstOrNull { it.activityInfo.packageName == context.packageName }
                ?: return fail("No activity in this app handles ${uri.scheme}://")
        return pass("${uri.scheme}://${uri.host} opens ${handler.activityInfo.name.substringAfterLast('.')}")
    }

    private suspend fun tokenRefresh(state: ThunderIDState): CheckResult {
        if (!state.isSignedIn) return fail("Not signed in. Sign in first.")
        val before = state.client.getAccessToken()
        if (storage.find("refresh_token") == null) {
            return fail("No refresh token stored. App-native sign-in returns an assertion only.")
        }
        storage.expireAccessToken()
        val after = state.client.getAccessToken()
        EventLog.ok("token:refreshed", "grant_type=refresh_token", source = "sdk")
        val exp = runCatching { state.client.decodeJwtToken(after)["exp"] }.getOrNull()
        return if (after != before) pass("Access token rotated; new exp=$exp") else fail("Token did not change after refresh")
    }

    private fun secureStorage(): CheckResult {
        val probeKey = "playground.probe"
        val probeValue = "probe-${System.nanoTime()}"
        storage.store(probeKey, probeValue)
        val readBack = storage.retrieve(probeKey)
        val raw = context.getSharedPreferences("dev.thunderid.sdk.prefs", Context.MODE_PRIVATE).all
        storage.delete(probeKey)
        if (readBack != probeValue) return fail("Round trip through EncryptedStorageAdapter failed")
        val leaked = raw.keys.any { it == probeKey } || raw.values.any { it == probeValue }
        return if (leaked) {
            fail("Plaintext found in the shared preferences file")
        } else {
            pass("Round trip ok; the prefs file holds only ciphertext (Android Keystore AES256)")
        }
    }

    private suspend fun userInfo(state: ThunderIDState): CheckResult {
        if (!state.isSignedIn) return fail("Not signed in. Sign in first.")
        val user = state.client.getUser()
        val profile = runCatching { state.client.getUserProfile() }
        val profileNote =
            profile.fold(
                { "profile ${it.attributes.size} attributes" },
                { "profile unavailable (${it.message})" },
            )
        return pass("sub=${user.sub}, ${user.claims.size} claims, $profileNote")
    }

    private suspend fun organizationUnitsOfUser(state: ThunderIDState): CheckResult {
        if (!state.isSignedIn) return fail("Not signed in. Sign in first.")
        val page = state.client.getAccessibleOrganizationUnits(AccessibleOrganizationUnitsOptions(limit = 10))
        val siblings =
            state.client.getAccessibleOrganizationUnits(AccessibleOrganizationUnitsOptions(OrganizationUnitScope.SIBLINGS))
        val current = state.client.getCurrentOrganizationUnit()
        return pass(
            "${page.totalResults} accessible, ${siblings.totalResults} siblings, current=${page.current}, " +
                "session unit=${current?.handle ?: current?.id ?: "none in claims"}",
        )
    }

    private suspend fun switchOrganizationUnit(
        state: ThunderIDState,
        interactive: Boolean,
    ): CheckResult {
        if (!state.isSignedIn) return fail("Not signed in. Sign in first.")
        val page = state.client.getAccessibleOrganizationUnits()
        val target =
            page.organizationUnits.firstOrNull { it.id != page.current }
                ?: return unavailable("The user can access only one organization unit")
        if (!interactive) return idle("Ready: tap to switch to ${target.handle ?: target.id}")
        val browser = if (PlaygroundSettings.clientId.isNotBlank() || state.client.isBackendMode()) customTabBrowser(context) else null
        val options = SwitchOrganizationUnitOptions(browser = browser, applicationId = PlaygroundSettings.applicationId.ifBlank { null })
        return when (val result = state.client.switchOrganizationUnit(target.id, options)) {
            is OrganizationUnitSwitchResult.Switched -> {
                state.refresh()
                pass("Switched to ${target.handle ?: target.id}")
            }

            is OrganizationUnitSwitchResult.SignInRequired -> {
                idle(
                    "The flow needs the user (${result.response.data
                        ?.inputs
                        ?.size ?: 0} inputs); the session is unchanged",
                )
            }
        }
    }

    private suspend fun pushedAuthorization(state: ThunderIDState): CheckResult {
        if (state.client.isBackendMode()) return unavailable("Sign-in runs on a backend")
        if (PlaygroundSettings.clientId.isBlank()) return fail("THUNDERID_CLIENT_ID is not set in config.properties")
        val url = state.client.signIn()
        return if (url.contains("request_uri=")) {
            pass("Pushed: the authorization URL carries only a request_uri")
        } else {
            pass("Not pushed: neither pushedAuthorizationRequests nor the server's metadata asks for it")
        }
    }

    private suspend fun ciba(
        state: ThunderIDState,
        interactive: Boolean,
    ): CheckResult {
        if (state.client.isBackendMode()) return unavailable("Sign-in runs on a backend")
        if (PlaygroundSettings.clientId.isBlank()) return fail("THUNDERID_CLIENT_ID is not set in config.properties")
        val hint = state.user?.username ?: state.user?.email
        if (!interactive || hint == null) return idle("Sign in, then tap to start a backchannel request for the same user")
        val started = state.client.initiateCIBA(CIBAInitiateOptions(loginHint = hint, bindingMessage = "Playground"))
        EventLog.info("ciba:started", "expires in ${started.expiresIn}s, approve it on the other device", source = "sdk")
        state.client.pollCIBA(started.authReqId, started.interval)
        state.refresh()
        return pass("Approved: tokens stored")
    }

    private suspend fun deviceGrant(
        state: ThunderIDState,
        interactive: Boolean,
    ): CheckResult {
        if (state.client.isBackendMode()) return unavailable("Sign-in runs on a backend")
        if (PlaygroundSettings.clientId.isBlank()) return fail("THUNDERID_CLIENT_ID is not set in config.properties")
        if (!interactive) return idle("Tap to start a device request and open its verification flow")
        val authorization = state.client.startDeviceAuthorization()
        EventLog.info("device:started", "user code ${authorization.userCode} at ${authorization.verificationUri}", source = "sdk")
        val step = state.client.verifyDeviceCode(authorization.userCode, PlaygroundSettings.applicationId.ifBlank { null })
        return pass("Device code ${authorization.userCode}: verification flow is ${step.flowStatus.name.lowercase()}")
    }

    private fun theme(scheme: ColorScheme): CheckResult {
        val dark = scheme.background.luminance() < 0.5f
        val primary = String.format("#%06X", scheme.primary.toArgb() and 0xFFFFFF)
        return if (dark) {
            pass("Material 3 dark scheme, primary $primary; SDK components read MaterialTheme")
        } else {
            fail("The active color scheme is not dark")
        }
    }

    private fun translations(): CheckResult {
        val i18n = ThunderIDI18n(bundles = PlaygroundBundles)
        // A built-in locale: the SDK ships ja-JP strings, the admin ones included.
        i18n.setLocale("ja-JP")
        val builtIn = i18n.resolve("admin.roles.permissions.add")
        // The app's bundle wins over the SDK's own fr-FR strings.
        i18n.setLocale("fr-FR")
        val overridden = i18n.resolve("signUp.button")
        // es-ES is not built in: the app's bundle supplies a few keys and the rest fall back to English.
        i18n.setLocale("es-ES")
        val appOnly = i18n.resolve("signIn.button")
        val fallback = i18n.resolve("signIn.title")
        val untranslated =
            ThunderIDI18n.BUILT_IN_LOCALES.drop(1).filter { locale ->
                ThunderIDI18n(language = locale).resolve("admin.roles.permissions.title") ==
                    DefaultStrings.all["admin.roles.permissions.title"]
            }
        return when {
            builtIn == DefaultStrings.all["admin.roles.permissions.add"] -> {
                fail("ja-JP fell back to English: \"$builtIn\"")
            }

            untranslated.isNotEmpty() -> {
                fail("Not translated in ${untranslated.joinToString()}")
            }

            overridden != "Créer un compte" -> {
                fail("The app bundle did not override fr-FR: \"$overridden\"")
            }

            appOnly != "Iniciar sesión" -> {
                fail("es-ES signIn.button resolved to \"$appOnly\"")
            }

            fallback != DefaultStrings.all["signIn.title"] -> {
                fail("Missing key did not fall back to English")
            }

            else -> {
                pass(
                    "${ThunderIDI18n.BUILT_IN_LOCALES.size} built-in locales (ja-JP: \"$builtIn\"); app bundles override them; " +
                        "es-ES from the app, missing keys fall back to English",
                )
            }
        }
    }

    private suspend fun endpoints(state: ThunderIDState): CheckResult {
        val config = state.client.getConfiguration()
        val expected = ManagementCollections.associate { it.name to it.resolve(config.baseUrl, config.endpoints) }
        val baseHost = Uri.parse(config.baseUrl).authority
        val elsewhere = expected.values.count { Uri.parse(it).authority != baseHost }
        val split = if (elsewhere == 0) "all on the baseUrl host" else "$elsewhere on another host"
        // The SDK resolves URLs inside its transport, and every management call asks for an access token
        // before the fetcher runs, so the URLs can only be observed once signed in.
        if (!state.isSignedIn) {
            observedEndpoints.clear()
            return idle("${expected.size} collections from config, $split. Sign in to verify the URLs the SDK requests.")
        }
        val mismatches = mutableListOf<String>()
        ManagementCollections.forEach { collection ->
            val probe = RecordingFetcher()
            collection.probe(state.client, probe)
            val url =
                probe.request
                    ?.url
                    ?.substringBefore('?')
                    .orEmpty()
            observedEndpoints[collection.name] = url
            if (url != expected[collection.name]) mismatches += "${collection.name}: $url"
        }
        return if (mismatches.isEmpty()) {
            pass("${expected.size} collections requested where config says, $split")
        } else {
            fail("Unexpected URLs: ${mismatches.joinToString("; ")}")
        }
    }

    /** Management calls send the signed-in user's access token, so they all need a session. */
    private suspend fun management(
        state: ThunderIDState,
        check: suspend () -> CheckResult,
    ): CheckResult = if (!state.isSignedIn) fail("Not signed in. Management calls send the access token.") else check()

    private suspend fun groups(state: ThunderIDState): CheckResult {
        val page = state.client.groups.list(limit = 5)
        val first = page.groups.firstOrNull() ?: return pass("0 groups. Create one with GroupCreate in Components.")
        ManagementFixtures.groupId = first.id
        val group = state.client.groups.get(first.id)
        return pass("${page.totalResults} groups: ${names(page.groups.map { it.name })}; get(${group.name}) ok")
    }

    private suspend fun groupMembers(state: ThunderIDState): CheckResult {
        val id =
            ManagementFixtures.groupId.ifBlank { null }
                ?: state.client.groups
                    .list(limit = 1)
                    .groups
                    .firstOrNull()
                    ?.id
                ?: return idle("No groups to list members of")
        ManagementFixtures.groupId = id
        val page = state.client.groups.listMembers(id, limit = 20)
        val byType = GroupMemberType.entries.map { type -> "${page.members.count { it.type == type }} ${type.value}" }
        return pass("${page.totalResults} members of $id: ${byType.joinToString(", ")}")
    }

    private suspend fun roles(state: ThunderIDState): CheckResult {
        val page = state.client.roles.list(limit = 5)
        val first = page.roles.firstOrNull() ?: return pass("0 roles. Create one with RoleCreate in Components.")
        ManagementFixtures.roleId = first.id
        val role = state.client.roles.get(first.id)
        val granted = role.permissions.sumOf { it.permissions.size }
        return pass("${page.totalResults} roles: ${names(page.roles.map { it.name })}; ${role.name} grants $granted permissions")
    }

    private suspend fun roleAssignments(state: ThunderIDState): CheckResult {
        val id =
            ManagementFixtures.roleId.ifBlank { null }
                ?: state.client.roles
                    .list(limit = 1)
                    .roles
                    .firstOrNull()
                    ?.id
                ?: return idle("No roles to list assignments of")
        ManagementFixtures.roleId = id
        val page = state.client.roles.listAssignments(id, limit = 20)
        val users = state.client.roles.listAssignments(id, limit = 1, type = RoleAssigneeType.USER)
        val byType = RoleAssigneeType.entries.map { type -> "${page.assignments.count { it.type == type }} ${type.value}" }
        return pass("${page.totalResults} assignees of $id: ${byType.joinToString(", ")}; type=user filter ${users.totalResults}")
    }

    private suspend fun userGroups(state: ThunderIDState): CheckResult {
        // The signed-in user's sub is its user ID in ThunderID.
        val id = state.user?.sub ?: return fail("The signed-in user has no sub claim")
        val page = state.client.users.listGroups(id, limit = 10)
        ManagementFixtures.userId = id
        return pass("Signed-in user is in ${page.totalResults} groups${namesSuffix(page.groups.map { it.name })}")
    }

    private suspend fun userTypes(state: ThunderIDState): CheckResult {
        val page = state.client.userTypes.list(limit = 10)
        val first = page.types.firstOrNull() ?: return pass("0 user types")
        val details = state.client.userTypes.get(first.id)
        return pass(
            "${page.totalResults} user types${namesSuffix(page.types.map { it.handle })}; " +
                "${details.handle} has ${details.schema.size} attributes",
        )
    }

    private suspend fun agentTypes(state: ThunderIDState): CheckResult {
        val page = state.client.agentTypes.list(limit = 10)
        val first = page.types.firstOrNull() ?: return pass("0 agent types")
        val details = state.client.agentTypes.get(first.id)
        return pass(
            "${page.totalResults} agent types${namesSuffix(page.types.map { it.handle })}; " +
                "${details.handle} has ${details.schema.size} attributes",
        )
    }

    private suspend fun userUsages(state: ThunderIDState): CheckResult {
        // The signed-in user's sub is its user ID in ThunderID.
        val id = state.user?.sub ?: return fail("The signed-in user has no sub claim")
        val usages = state.client.users.getUsages(id)
        val byType = usages.summary?.entries?.joinToString(", ") { "${it.value} ${it.key}" } ?: "unknown"
        return pass("Signed-in user is referenced by ${usages.totalResults ?: "an unknown number of"} resources ($byType)")
    }

    private suspend fun administration(state: ThunderIDState): CheckResult {
        // Read-only: resolves which operations the server runs through a flow, without running any.
        val keys =
            listOf(
                AdministrationFlowConfigKey.USER_DELETION,
                AdministrationFlowConfigKey.GROUP_DELETION,
                AdministrationFlowConfigKey.GROUP_MEMBERSHIP_REMOVAL,
                AdministrationFlowConfigKey.ROLE_DELETION,
                AdministrationFlowConfigKey.ROLE_ASSIGNMENT_REMOVAL,
                AdministrationFlowConfigKey.ROLE_PERMISSION_REMOVAL,
                AdministrationFlowConfigKey.APPLICATION_DELETION,
                AdministrationFlowConfigKey.SECRET_REGENERATION,
            )
        val flows = state.client.administration.resolveFlows(keys)
        return pass("${flows.size} of ${keys.size} operations run through a flow${namesSuffix(flows.keys.toList())}")
    }

    private suspend fun organizationUnits(state: ThunderIDState): CheckResult {
        val page = state.client.organizationUnits.list(limit = 5)
        val first = page.organizationUnits.firstOrNull() ?: return pass("0 organization units")
        val byPath = state.client.organizationUnits.getByPath(first.handle)
        val users = state.client.organizationUnits.listUsersByPath(first.handle, limit = 5)
        return pass(
            "${page.totalResults} root organization units${namesSuffix(page.organizationUnits.map { it.handle })}; " +
                "tree/${first.handle} is ${byPath.name} with ${users.totalResults} users",
        )
    }

    private suspend fun resourceServers(state: ThunderIDState): CheckResult {
        val page = state.client.resourceServers.list(limit = 5)
        val first = page.resourceServers.firstOrNull() ?: return pass("0 resource servers")
        val resources = state.client.resourceServers.listResources(first.id, limit = 5)
        val actions = state.client.resourceServers.listActions(first.id, limit = 5)
        return pass(
            "${page.totalResults} resource servers${namesSuffix(page.resourceServers.map { it.name })}; " +
                "${first.name} has ${resources.totalResults} top-level resources and ${actions.totalResults} actions",
        )
    }

    private suspend fun lifecycle(state: ThunderIDState): CheckResult {
        // Read-only: the lifecycle operations are flow-only, so this reports which of them can run.
        val keys =
            AccountLifecycleSubjectKind.entries.flatMap { it.flowConfigKeys.values } + AdministrationFlowConfigKey.ACTION_DELETION
        val flows = state.client.administration.resolveFlows(keys)
        // What UserDetails(lifecycleActions = true) offers a locked user with these flows.
        val lockedUser = accountLifecycleActions(AccountLifecycleSubjectKind.USER, AccessStatusValue.LOCKED, flows)
        return pass(
            "${flows.size} of ${keys.size} lifecycle operations have a flow${namesSuffix(flows.keys.toList())}; " +
                "a locked user offers ${lockedUser.joinToString { it.value }.ifEmpty { "nothing" }}",
        )
    }

    private suspend fun platform(state: ThunderIDState): CheckResult {
        val client = state.client
        val connections = client.connections.list(limit = 5)
        val flows = client.flows.list(limit = 5)
        val templates = client.notificationTemplates.list(NotificationChannel.EMAIL)
        val themes = client.design.listThemes(limit = 5)
        val languages = client.translations.getLanguages()
        return pass(
            "${connections.totalResults} connections, ${flows.totalResults} flows, " +
                "${templates.templates.size} email templates, ${themes.totalResults} themes, " +
                "languages${namesSuffix(languages.languages)}",
        )
    }

    private suspend fun operations(state: ThunderIDState): CheckResult {
        val client = state.client
        val sections = client.serverConfig.list()
        val gateways = client.gateways.list()
        val variables = client.variables.list(limit = 5)
        val secrets = client.secrets.list(limit = 5)
        val versions = client.configurationVersions.list()
        val master = gateways.firstOrNull()?.let { client.gateways.getMaster(it.id) }
        val credentials = client.credentialConfigurations.list()
        val presentations = client.presentationDefinitions.list()
        return pass(
            "${sections.size} config sections, ${gateways.size} gateways, ${variables.totalResults} variables, " +
                "${secrets.totalResults} secrets, ${versions.size} configuration versions, " +
                "${master?.let { "master #${it.number}, " } ?: ""}${credentials.size} credential configurations, " +
                "${presentations.size} presentation definitions",
        )
    }

    private suspend fun sharing(state: ThunderIDState): CheckResult {
        val client = state.client
        val role =
            client.roles
                .list(limit = 1)
                .roles
                .firstOrNull()
        val flow =
            client.flows
                .list(limit = 1)
                .flows
                .firstOrNull()
        val unit =
            client.organizationUnits
                .list(limit = 1)
                .organizationUnits
                .firstOrNull()
        val rolePolicies = role?.let { client.roles.listSharingPolicies(it.id, limit = 5) }
        val flowPolicies = flow?.let { client.flows.listSharingPolicies(it.id, limit = 5) }
        // A unit no policy reaches answers 404, which is an answer here, not a failure.
        val overlay =
            if (role != null && unit != null) runCatching { client.roles.getOverlayRules(role.id, unit.id) }.getOrNull() else null
        val variables =
            if (flow != null && unit != null) runCatching { client.flows.getVariables(flow.id, unit.id) }.getOrNull() else null
        val groups = unit?.let { client.groups.listByPath(it.handle, limit = 5) }
        return pass(
            listOfNotNull(
                role?.let { "role ${it.name}: ${rolePolicies?.totalResults ?: 0} sharing policies" },
                unit?.let { "owned by ${it.handle}: ${overlay?.isOwned?.toString() ?: "not reached"}" },
                flow?.let { "flow ${it.id}: ${flowPolicies?.totalResults ?: 0} sharing policies" },
                variables?.let { "${it.values.size} variable overrides" },
                unit?.let { "groups/tree/${it.handle}: ${groups?.totalResults ?: 0} groups" },
            ).joinToString("; ").ifEmpty { "No roles, flows or organization units to read" },
        )
    }

    private suspend fun projects(state: ThunderIDState): CheckResult {
        val client = state.client
        // Projects are served by the Control Plane only, and type discovery can be turned off on the server.
        val projects = runCatching { client.projects.list(limit = 5) }
        val types = runCatching { client.authorizationDetailTypes.list(limit = 5) }
        val supported = runCatching { client.getSupportedAuthorizationDetailsTypes() }
        if (projects.isFailure && types.isFailure && supported.isFailure) {
            return fail("No projects or authorization details types answered: ${projects.exceptionOrNull()?.message}")
        }
        return pass(
            listOf(
                projects.fold({
                    "${it.totalResults} projects${namesSuffix(
                        it.projects.map { p ->
                            p.handle
                        },
                    )}"
                }) { "projects unavailable" },
                types.fold({ "${it.totalResults} authorization details types" }) { "types unavailable" },
                supported.fold({ "${it.size} requestable${namesSuffix(it.map { t -> t.type })}" }) { "discovery off" },
            ).joinToString("; "),
        )
    }

    private suspend fun firstAgentId(state: ThunderIDState): String? =
        ManagementFixtures.agentId.ifBlank { null }
            ?: state.client.agents
                .list(limit = 1)
                .agents
                .firstOrNull()
                ?.id
                ?.also { ManagementFixtures.agentId = it }

    private suspend fun agentGroups(state: ThunderIDState): CheckResult {
        val id = firstAgentId(state) ?: return idle("No agents. Create one with AgentCreate in Components.")
        val page = state.client.agents.listGroups(id, limit = 10)
        return pass("Agent $id is in ${page.totalResults} groups${namesSuffix(page.groups.map { it.name })}")
    }

    private suspend fun agentRoles(state: ThunderIDState): CheckResult {
        val id = firstAgentId(state) ?: return idle("No agents. Create one with AgentCreate in Components.")
        val page = state.client.agents.listRoles(id, limit = 10)
        return pass("Agent $id has ${page.totalResults} roles${namesSuffix(page.roles)}")
    }

    private suspend fun permissions(state: ThunderIDState): CheckResult {
        // The hierarchy and claim parsing run offline against synthetic tokens.
        val hierarchy =
            hasPermission(listOf("system"), "system:user:view") &&
                hasPermission(listOf("system:user:view"), "system:user:view") &&
                !hasPermission(listOf("system:user"), "system:group:view") &&
                !hasPermission(listOf("system:user:view"), "system:user") &&
                hasPermission(emptyList(), "")
        if (!hierarchy) return fail("hasPermission() does not follow the system > system:user > system:user:view hierarchy")
        val fromScope = getTokenPermissions(unsignedJwt(JSONObject().put("scope", "openid system:user:view")))
        val fromScopes = getTokenPermissions(unsignedJwt(JSONObject().put("scopes", JSONArray(listOf("system:group")))))
        if (fromScope != listOf("openid", "system:user:view") || fromScopes != listOf("system:group")) {
            return fail("getTokenPermissions() read scope=$fromScope, scopes=$fromScopes")
        }
        if (getTokenPermissions("opaque-token").isNotEmpty()) return fail("An opaque token yielded permissions")
        if (!state.isSignedIn) return pass("Hierarchy and scope/scopes parsing ok. Sign in to read granted permissions.")
        val granted = state.client.getGrantedPermissions()
        val admin = AdminPermissions.count { (permission, _) -> hasPermission(granted, permission) }
        return pass("${granted.size} granted; $admin of ${AdminPermissions.size} admin permissions; hierarchy ok")
    }

    private fun unsignedJwt(payload: JSONObject): String {
        val flags = android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING

        fun encode(json: String) = android.util.Base64.encodeToString(json.toByteArray(), flags)
        return "${encode("""{"alg":"none"}""")}.${encode(payload.toString())}.sig"
    }

    private fun names(list: List<String>): String = list.take(3).joinToString(", ") + if (list.size > 3) ", …" else ""

    private fun namesSuffix(list: List<String>): String = if (list.isEmpty()) "" else ": ${names(list)}"

    private suspend fun initiateFlow(state: ThunderIDState): EmbeddedFlowResponse {
        val applicationId = PlaygroundSettings.applicationId
        require(applicationId.isNotBlank()) { "THUNDERID_APPLICATION_ID is not set in config.properties" }
        val response = state.client.signIn(EmbeddedSignInPayload(), EmbeddedFlowRequestConfig(applicationId))
        if (response.flowStatus == FlowStatus.ERROR) {
            error(response.failureReason ?: response.error?.message?.defaultValue ?: "Flow returned ERROR")
        }
        EventLog.info("flow:initiated", "executionId=${response.flowId}", source = "sdk")
        return response
    }

    private fun flowComponents(response: EmbeddedFlowResponse): List<FlowComponent> {
        fun flatten(list: List<FlowComponent>?): List<FlowComponent> = list.orEmpty().flatMap { listOf(it) + flatten(it.components) }
        return flatten(response.data?.meta?.components)
    }

    private fun pass(note: String) = CheckResult(CheckStatus.PASS, note)

    private fun fail(note: String) = CheckResult(CheckStatus.FAIL, note)

    private fun idle(note: String) = CheckResult(CheckStatus.IDLE, note)

    private fun unavailable(note: String) = CheckResult(CheckStatus.UNAVAILABLE, note)

    private companion object {
        // Checks that do not need an initialized SDK.
        val OFFLINE_CHECKS = setOf("biometric", "deeplink", "storage", "permissions", "theme", "i18n")

        // Development only: the local ThunderID server uses a self-signed certificate.
        fun trustAll(connection: HttpsURLConnection) {
            val trustManager =
                object : X509TrustManager {
                    override fun checkClientTrusted(
                        chain: Array<X509Certificate>,
                        authType: String,
                    ) = Unit

                    override fun checkServerTrusted(
                        chain: Array<X509Certificate>,
                        authType: String,
                    ) = Unit

                    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                }
            val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(trustManager), null) }
            connection.sslSocketFactory = ssl.socketFactory
            connection.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
        }
    }
}
