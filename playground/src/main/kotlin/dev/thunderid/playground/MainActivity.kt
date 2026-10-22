// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.playground

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import dev.thunderid.android.EncryptedStorageAdapter
import dev.thunderid.android.ThunderIDClient
import dev.thunderid.android.ThunderIDConfig
import dev.thunderid.android.ThunderIDEndpoints
import dev.thunderid.android.auth.FederatedAuthSession
import dev.thunderid.compose.ThunderIDProvider
import dev.thunderid.compose.i18n.ThunderIDI18n

/** Values from config.properties, baked into BuildConfig at build time. */
object PlaygroundSettings {
    val baseUrl: String = BuildConfig.THUNDERID_BASE_URL
    val applicationId: String = BuildConfig.THUNDERID_APPLICATION_ID
    val clientId: String = BuildConfig.THUNDERID_CLIENT_ID
    val afterSignInUrl: String = BuildConfig.THUNDERID_AFTER_SIGN_IN_URL
    val afterSignOutUrl: String = BuildConfig.THUNDERID_AFTER_SIGN_OUT_URL
    val scopes: List<String> = BuildConfig.THUNDERID_SCOPES.split(" ").filter { it.isNotBlank() }

    val hostLabel: String
        get() = baseUrl.substringAfter("://").ifBlank { "no server configured" }

    /** Per-collection keys from config.properties. Blank means "not overridden". */
    val endpointKeys: Map<String, String> =
        mapOf(
            "THUNDERID_ENDPOINT_APPLICATIONS" to BuildConfig.THUNDERID_ENDPOINT_APPLICATIONS,
            "THUNDERID_ENDPOINT_USERS" to BuildConfig.THUNDERID_ENDPOINT_USERS,
            "THUNDERID_ENDPOINT_USER_TYPES" to BuildConfig.THUNDERID_ENDPOINT_USER_TYPES,
            "THUNDERID_ENDPOINT_AGENTS" to BuildConfig.THUNDERID_ENDPOINT_AGENTS,
            "THUNDERID_ENDPOINT_AGENT_TYPES" to BuildConfig.THUNDERID_ENDPOINT_AGENT_TYPES,
            "THUNDERID_ENDPOINT_GROUPS" to BuildConfig.THUNDERID_ENDPOINT_GROUPS,
            "THUNDERID_ENDPOINT_ROLES" to BuildConfig.THUNDERID_ENDPOINT_ROLES,
        )

    /** Where a collection's URL comes from: its own key, or baseUrl. */
    fun endpointSource(collection: ManagementCollection): String =
        when {
            endpointKeys[collection.configKey].orEmpty().isNotBlank() -> collection.configKey
            else -> "baseUrl"
        }

    private fun endpoint(collection: String): String? {
        val match = ManagementCollections.first { it.name == collection }
        return endpointKeys[match.configKey].orEmpty().ifBlank { null }
    }

    /** The ThunderIDEndpoints handed to the SDK. A null entry lets the SDK fall back to baseUrl. */
    val endpoints: ThunderIDEndpoints
        get() =
            ThunderIDEndpoints(
                applications = endpoint("applications"),
                users = endpoint("users"),
                userTypes = endpoint("userTypes"),
                agents = endpoint("agents"),
                agentTypes = endpoint("agentTypes"),
                groups = endpoint("groups"),
                roles = endpoint("roles"),
            )
}

/** Translations the playground registers, so the translations check and LanguageSwitcher have a second locale. */
val PlaygroundBundles: Map<String, Map<String, String>> =
    mapOf(
        "fr-FR" to
            mapOf(
                "signIn.button" to "Se connecter",
                "signUp.button" to "Créer un compte",
                "signOut.button" to "Se déconnecter",
            ),
        "es-ES" to
            mapOf(
                "signIn.button" to "Iniciar sesión",
                "signUp.button" to "Registrarse",
                "signOut.button" to "Cerrar sesión",
            ),
    )

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )

        val storage = RecordingStorageAdapter(EncryptedStorageAdapter(applicationContext))
        val config =
            ThunderIDConfig(
                baseUrl = PlaygroundSettings.baseUrl,
                clientId = PlaygroundSettings.clientId.takeIf { it.isNotBlank() },
                applicationId = PlaygroundSettings.applicationId.takeIf { it.isNotBlank() },
                afterSignInUrl = PlaygroundSettings.afterSignInUrl,
                afterSignOutUrl = PlaygroundSettings.afterSignOutUrl,
                scopes = PlaygroundSettings.scopes,
                endpoints = PlaygroundSettings.endpoints,
                storage = storage,
                allowInsecureConnections = BuildConfig.DEBUG,
            )

        setContent {
            val client = remember { ThunderIDClient() }
            val i18n =
                remember {
                    ThunderIDI18n(bundles = PlaygroundBundles, storageKey = "playground_locale", context = applicationContext)
                }
            PlaygroundTheme {
                ThunderIDProvider(config = config, client = client, i18n = i18n) {
                    PlaygroundApp(storage = storage)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { uri ->
            val params = uri.queryParameterNames.joinToString("&") { "$it=…" }
            EventLog.info("callback:received", "${uri.scheme}://${uri.host}?$params", source = "redirect")
            FederatedAuthSession.onRedirect(uri)
        }
    }

    override fun onResume() {
        super.onResume()
        // A callback arrives in onNewIntent before this runs, so a sign-in still pending here
        // means the user dismissed the browser.
        FederatedAuthSession.cancelIfPending()
    }
}
