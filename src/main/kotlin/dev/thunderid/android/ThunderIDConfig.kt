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

    // Token Validation
    val tokenValidation: TokenValidationConfig = TokenValidationConfig(),

    // Storage & Platform
    val storage: StorageAdapter? = null,
    val instanceId: Int? = null
)

data class TokenValidationConfig(
    val validate: Boolean = true,
    val validateIssuer: Boolean = true,
    val clockTolerance: Int = 0
)
