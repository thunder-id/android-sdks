// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android

/**
 * Configuration for the ThunderID Android SDK (spec §5.2).
 */
data class ThunderIDConfig(
    // Core
    val baseUrl: String,
    val clientId: String? = null,
    // Redirect URIs
    val afterSignInUrl: String? = null,
    val afterSignOutUrl: String? = null,
    val signInUrl: String? = null,
    val signUpUrl: String? = null,
    // OAuth2 / OIDC
    val scopes: List<String> = listOf("openid"),
    val clientSecret: String? = null,
    val signInOptions: Map<String, Any> = emptyMap(),
    val signOutOptions: Map<String, Any> = emptyMap(),
    val signUpOptions: Map<String, Any> = emptyMap(),
    // Application Identity
    val applicationId: String? = null,
    val organizationHandle: String? = null,
    // User Profile - whether user profile attributes come from /users/me or only from the OIDC access-token/userinfo claims.
    val fetchUserProfile: Boolean = true,
    // Platform Attestation - when enabled, a token from [attestationTokenProvider] is sent with every
    // native flow-initiate request (e.g. Google Play Integrity on Android). The provider is invoked by
    // the SDK; the app supplies it since obtaining a platform attestation token is app/platform-specific.
    val attestationEnabled: Boolean = false,
    val attestationTokenProvider: (suspend () -> String)? = null,
    // Token Validation
    val tokenValidation: TokenValidationConfig = TokenValidationConfig(),
    // Management - collection URL overrides for the management operations.
    val endpoints: ThunderIDEndpoints = ThunderIDEndpoints(),
    // HTTP options.
    val http: ThunderIDHttpConfig = ThunderIDHttpConfig(),
    // Session - behaviour around the session. See [ThunderIDSessionConfig].
    val session: ThunderIDSessionConfig = ThunderIDSessionConfig(),
    // Storage & Platform
    val storage: StorageAdapter? = null,
    val instanceId: Int? = null,
    // Development only - bypasses TLS certificate verification. Never use in production.
    val allowInsecureConnections: Boolean = false,
    /**
     * Vendor/brand namespace used to derive default storage identifiers (e.g. EncryptedSharedPreferences file
     * name). Override this when white-labeling the SDK under a different brand. Defaults to
     * [DEFAULT_VENDOR].
     */
    val vendor: String = DEFAULT_VENDOR,
) {
    companion object {
        const val DEFAULT_VENDOR: String = "thunderid"
    }
}

/**
 * Session options.
 *
 * @property checkInterval How often [ThunderIDClient.watchSession] asks the app's backend whether the
 * session is still live, in seconds. Only applies when sign-in runs on a backend
 * ([ThunderIDEndpoints.backend]): the backend's check endpoint reports liveness without refreshing the
 * session or counting as activity, so polling it does not keep an idle session alive. `0` leaves only
 * the check that runs when the app comes back to the foreground.
 */
data class ThunderIDSessionConfig(
    val checkInterval: Int = 0,
)

data class TokenValidationConfig(
    val validate: Boolean = true,
    val validateIssuer: Boolean = true,
    val clockTolerance: Int = 0,
)
