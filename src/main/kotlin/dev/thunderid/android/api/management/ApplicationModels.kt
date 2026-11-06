// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.management

/**
 * The fields of an application that a caller can set. Used to create and to update an application.
 *
 * @property type One of `browser`, `fullstack`, `mobile`, `m2m`, `mcp`, `custom`.
 * @property ouId The organization unit the application belongs to. Required on create unless [projectId] is given.
 * @property projectId The project the application belongs to. Without [ouId] the application is placed in the
 *   project's root organization unit; with it, that unit must be the project's root unit or one below it.
 */
data class ApplicationRequest(
    val name: String,
    val description: String? = null,
    val url: String? = null,
    val logoUrl: String? = null,
    val tosUri: String? = null,
    val policyUri: String? = null,
    val contacts: List<String>? = null,
    val authFlowId: String? = null,
    val registrationFlowId: String? = null,
    val isRegistrationFlowEnabled: Boolean? = null,
    val recoveryFlowId: String? = null,
    val isRecoveryFlowEnabled: Boolean? = null,
    val signOutFlowId: String? = null,
    val userAttributes: List<String>? = null,
    val allowedUserTypes: List<String>? = null,
    val allowedAgentTypes: List<String>? = null,
    val themeId: String? = null,
    val layoutId: String? = null,
    val type: String? = null,
    val template: String? = null,
    val flowSecret: String? = null,
    val inboundAuthConfig: List<InboundAuthConfig>? = null,
    val ouId: String? = null,
    val assertion: TokenConfig? = null,
    val attestation: AttestationConfig? = null,
    val passkeyAllowedOrigins: List<String>? = null,
    val isReadOnly: Boolean? = null,
    val projectId: String? = null,
)

/**
 * An application registered in ThunderID. Call [toRequest] to send it back through
 * `ApplicationsClient.update`.
 */
data class Application(
    val id: String,
    val name: String,
    val description: String? = null,
    val url: String? = null,
    val logoUrl: String? = null,
    val tosUri: String? = null,
    val policyUri: String? = null,
    val contacts: List<String>? = null,
    val authFlowId: String? = null,
    val registrationFlowId: String? = null,
    val isRegistrationFlowEnabled: Boolean? = null,
    val recoveryFlowId: String? = null,
    val isRecoveryFlowEnabled: Boolean? = null,
    val signOutFlowId: String? = null,
    val userAttributes: List<String>? = null,
    val allowedUserTypes: List<String>? = null,
    val allowedAgentTypes: List<String>? = null,
    val themeId: String? = null,
    val layoutId: String? = null,
    val type: String? = null,
    val template: String? = null,
    val flowSecret: String? = null,
    val inboundAuthConfig: List<InboundAuthConfig>? = null,
    val ouId: String? = null,
    val assertion: TokenConfig? = null,
    val attestation: AttestationConfig? = null,
    val passkeyAllowedOrigins: List<String>? = null,
    val isReadOnly: Boolean? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    /** `ACTIVE` or `SUSPENDED`. Applications are never locked. */
    val status: AccessStatus? = null,
) {
    /** The caller-settable fields of this application. */
    fun toRequest(): ApplicationRequest =
        ApplicationRequest(
            name = name,
            description = description,
            url = url,
            logoUrl = logoUrl,
            tosUri = tosUri,
            policyUri = policyUri,
            contacts = contacts,
            authFlowId = authFlowId,
            registrationFlowId = registrationFlowId,
            isRegistrationFlowEnabled = isRegistrationFlowEnabled,
            recoveryFlowId = recoveryFlowId,
            isRecoveryFlowEnabled = isRecoveryFlowEnabled,
            signOutFlowId = signOutFlowId,
            userAttributes = userAttributes,
            allowedUserTypes = allowedUserTypes,
            allowedAgentTypes = allowedAgentTypes,
            themeId = themeId,
            layoutId = layoutId,
            type = type,
            template = template,
            flowSecret = flowSecret,
            inboundAuthConfig = inboundAuthConfig,
            ouId = ouId,
            assertion = assertion,
            attestation = attestation,
            passkeyAllowedOrigins = passkeyAllowedOrigins,
            isReadOnly = isReadOnly,
        )
}

/** The summary of an application returned in list responses. */
data class BasicApplication(
    val id: String,
    val name: String,
    val description: String? = null,
    val logoUrl: String? = null,
    val authFlowId: String? = null,
    val registrationFlowId: String? = null,
    val isRegistrationFlowEnabled: Boolean? = null,
    val type: String? = null,
    val template: String? = null,
    val isReadOnly: Boolean? = null,
    val clientId: String? = null,
    val status: AccessStatus? = null,
)

/** A page of applications. */
data class ApplicationListResponse(
    val totalResults: Int,
    val count: Int,
    val applications: List<BasicApplication>,
)

/** Inbound authentication configuration of an application. [type] is currently always `oauth2`. */
data class InboundAuthConfig(
    val type: String = "oauth2",
    val config: OAuth2Config,
)

/** OAuth 2.0 / OIDC configuration of an application. */
data class OAuth2Config(
    val grantTypes: List<String>,
    val responseTypes: List<String>,
    val clientId: String? = null,
    val clientSecret: String? = null,
    val redirectUris: List<String>? = null,
    val postLogoutRedirectUris: List<String>? = null,
    val tokenEndpointAuthMethod: String? = null,
    val pkceRequired: Boolean? = null,
    val publicClient: Boolean? = null,
    val scopes: List<String>? = null,
    val token: OAuth2TokenConfig? = null,
    val userInfo: UserInfoConfig? = null,
    val scopeClaims: Map<String, List<String>>? = null,
    val requirePushedAuthorizationRequests: Boolean? = null,
    val certificate: ApplicationCertificate? = null,
    val acrValues: List<String>? = null,
)

/** A certificate attached to an OAuth 2.0 application, e.g. a JWKS URI. */
data class ApplicationCertificate(
    val type: String,
    val value: String? = null,
)

/** Base token configuration shared by the tokens an application issues. */
data class TokenConfig(
    val validityPeriod: Int,
    val userAttributes: List<String>,
)

/** Token settings of an OAuth 2.0 application. */
data class OAuth2TokenConfig(
    val accessToken: AccessTokenConfig? = null,
    val idToken: IDTokenConfig? = null,
    val refreshToken: RefreshTokenConfig? = null,
    val idJag: IDJAGConfig? = null,
    val validityPeriod: Int? = null,
    val userAttributes: List<String>? = null,
)

/** Access token settings for one kind of subject (a user or the client itself). */
data class AccessTokenSubConfig(
    val validityPeriod: Int? = null,
    val attributes: List<String>? = null,
)

/** Access token configuration. */
data class AccessTokenConfig(
    val userConfig: AccessTokenSubConfig? = null,
    val clientConfig: AccessTokenSubConfig? = null,
    val defaultAudience: String? = null,
)

/** ID token configuration. [responseType] is one of `JWT`, `JWE`, `NESTED_JWT`. */
data class IDTokenConfig(
    val validityPeriod: Int? = null,
    val userAttributes: List<String>? = null,
    val responseType: String? = null,
    val encryptionAlg: String? = null,
    val encryptionEnc: String? = null,
)

/** Refresh token configuration. */
data class RefreshTokenConfig(
    val validityPeriod: Int,
)

/** Identity assertion JWT authorization grant (ID-JAG) configuration. */
data class IDJAGConfig(
    val enabled: Boolean,
    val allowedAudiences: List<String>? = null,
    val validityPeriod: Int? = null,
)

/** Userinfo endpoint configuration. [responseType] is one of `JSON`, `JWS`, `JWE`, `NESTED_JWT`. */
data class UserInfoConfig(
    val userAttributes: List<String>? = null,
    val responseType: String? = null,
    val signingAlg: String? = null,
    val encryptionAlg: String? = null,
    val encryptionEnc: String? = null,
)

/** App attestation configuration. At most one platform can be configured. */
data class AttestationConfig(
    val devMode: Boolean? = null,
    val android: AndroidAttestationConfig? = null,
    val apple: AppleAttestationConfig? = null,
)

/** Android app attestation configuration. */
data class AndroidAttestationConfig(
    val packageName: String? = null,
    val certificateSha256Digests: List<String>? = null,
    val serviceAccountCredentials: String? = null,
)

/** Apple app attestation configuration. */
data class AppleAttestationConfig(
    val teamId: String,
    val bundleId: String,
)

/**
 * What the server read from a client ID metadata document (CIMD): the client's name and links, and the
 * OAuth configuration it declares.
 */
data class CimdPreviewResponse(
    val name: String,
    val inboundAuthConfig: List<InboundAuthConfig>,
    val url: String? = null,
    val tosUri: String? = null,
    val policyUri: String? = null,
    val contacts: List<String>? = null,
)

/**
 * A regenerated client secret. [application] is the updated application when the SDK saved the secret
 * itself, and `null` when an administration flow regenerated it.
 */
data class RegenerateClientSecretResult(
    val clientSecret: String,
    val application: Application? = null,
)

/** A regenerated flow secret and the updated application. */
data class RegenerateFlowSecretResult(
    val application: Application,
    val flowSecret: String,
)
