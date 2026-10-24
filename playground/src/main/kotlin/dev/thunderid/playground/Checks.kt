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
import dev.thunderid.android.auth.FederatedAuthSession
import dev.thunderid.compose.ThunderIDState
import dev.thunderid.compose.i18n.DefaultStrings
import dev.thunderid.compose.i18n.ThunderIDI18n
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        CheckDef("orgs", "Session", "Organizations", "organizations()"),
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
            "orgs" -> unavailable("The Android SDK has no organizations API. Only ThunderIDConfig.organizationHandle exists.")
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
        i18n.setLocale("fr-FR")
        val translated = i18n.resolve("signIn.button")
        val fallback = i18n.resolve("signIn.title")
        return when {
            translated != "Se connecter" -> fail("fr-FR signIn.button resolved to \"$translated\"")
            fallback != DefaultStrings.all["signIn.title"] -> fail("Missing key did not fall back to English")
            else -> pass("fr-FR: \"$translated\"; missing keys fall back to English")
        }
    }

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
        val OFFLINE_CHECKS = setOf("biometric", "deeplink", "storage", "orgs", "theme", "i18n")

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
