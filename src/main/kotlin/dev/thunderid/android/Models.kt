// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android

import com.google.gson.annotations.SerializedName

/**
 * The authenticated user.
 *
 * A deployment's claims are dynamic, so the user *is* the claim set: every claim comes
 * through untouched and is readable by key. The few well-known claims mirror the
 * `KnownUser` keys in the JavaScript SDK and are plain accessors, not mapped fields, so
 * they read the claim of the same name and nothing else.
 */
data class User(
    /** Every claim exactly as the server sent it. */
    val claims: Map<String, Any> = emptyMap(),
) {
    /** Reads any claim by name, including ones this SDK has never heard of. */
    operator fun get(claim: String): Any? = claims[claim]

    val sub: String? get() = this["sub"] as? String
    val username: String? get() = this["username"] as? String
    val email: String? get() = this["email"] as? String
    val displayName: String? get() = this["displayName"] as? String
    val givenName: String? get() = this["givenName"] as? String
    val familyName: String? get() = this["familyName"] as? String

    /** Every claim except [RESERVED_CLAIMS]. */
    val profileClaims: Map<String, Any>
        get() = claims.filterKeys { it !in RESERVED_CLAIMS }

    companion object {
        /** Protocol claims: they describe the token, not the user. */
        val RESERVED_CLAIMS: Set<String> =
            setOf(
                "sub",
                "iss",
                "aud",
                "exp",
                "iat",
                "nbf",
                "jti",
                "azp",
                "nonce",
                "typ",
                "at_hash",
                "c_hash",
                "sid",
                "scope",
                "client_id",
                "acr",
                "amr",
                "auth_time",
            )
    }
}

data class UserProfile(
    val id: String,
    val ouId: String? = null,
    val type: String? = null,
    val attributes: Map<String, Any> = emptyMap(),
    val display: String? = null,
    val isReadOnly: Boolean = false,
)

/** Attribute schema metadata returned by `GET /users/me/meta`. */
data class AttributeSchema(
    val credential: Boolean? = null,
    val description: String? = null,
    val displayName: String? = null,
    val mutability: String? = null,
    val readOnly: Boolean? = null,
    val regex: String? = null,
    val required: Boolean? = null,
    val subAttributes: List<AttributeSchema>? = null,
    val type: String? = null,
    val unique: Boolean? = null,
)

data class UsersMeMetaResponse(
    val schema: Map<String, AttributeSchema> = emptyMap(),
)

data class TokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("expires_in") val expiresIn: Int? = null,
    @SerializedName("refresh_token") val refreshToken: String? = null,
    @SerializedName("id_token") val idToken: String? = null,
    val scope: String? = null,
)

data class SignInOptions(
    val prompt: String? = null,
    val loginHint: String? = null,
    val fidp: String? = null,
    val extra: Map<String, Any> = emptyMap(),
)

data class SignUpOptions(
    val appId: String? = null,
    val extra: Map<String, Any> = emptyMap(),
)

data class SignOutOptions(
    val idTokenHint: String? = null,
    val extra: Map<String, Any> = emptyMap(),
)

data class TokenExchangeRequestConfig(
    val subjectToken: String,
    val subjectTokenType: String,
    val requestedTokenType: String? = null,
    val audience: String? = null,
)

data class EmbeddedSignInPayload(
    val flowId: String? = null,
    val actionId: String? = null,
    val inputs: Map<String, String> = emptyMap(),
    val challengeToken: String? = null,
)

data class EmbeddedFlowRequestConfig(
    val applicationId: String,
    val flowType: FlowType = FlowType.AUTHENTICATION,
)

enum class FlowType(
    val value: String,
) {
    AUTHENTICATION("AUTHENTICATION"),
    REGISTRATION("REGISTRATION"),
    PASSWORD_RECOVERY("PASSWORD_RECOVERY"),
    INVITED_USER_REGISTRATION("INVITED_USER_REGISTRATION"),
}

data class EmbeddedFlowResponse(
    @SerializedName("executionId") val flowId: String? = null,
    val flowStatus: FlowStatus,
    val stepId: String? = null,
    val type: String? = null,
    val data: FlowStepData? = null,
    val assertion: String? = null,
    val failureReason: String? = null,
    val challengeToken: String? = null,
)

enum class FlowStatus { PROMPT_ONLY, INCOMPLETE, COMPLETE, ERROR }

data class FlowStepData(
    val actions: List<FlowAction>? = null,
    val inputs: List<FlowInput>? = null,
    val meta: FlowMeta? = null,
    val additionalData: Map<String, Any>? = null,
    /** The provider URL a `REDIRECTION` step (a federated/social TRIGGER action) sends the user to. */
    val redirectURL: String? = null,
)

data class FlowAction(
    val id: String? = null,
    val ref: String? = null,
    val nextNode: String? = null,
    val type: String? = null,
    val label: String? = null,
    val eventType: String? = null,
    val variant: String? = null,
    @SerializedName("image") val icon: String? = null,
)

data class FlowInput(
    @SerializedName("identifier") val name: String,
    val type: String? = null,
    val required: Boolean? = null,
)

data class FlowMeta(
    val components: List<FlowComponent>? = null,
)

data class FlowComponent(
    val id: String? = null,
    val ref: String? = null,
    val type: String? = null,
    val category: String? = null,
    val label: String? = null,
    val placeholder: String? = null,
    val variant: String? = null,
    val eventType: String? = null,
    val align: String? = null,
    @SerializedName("image") val icon: String? = null,
    /** The `additionalData` key a data-bound display component (e.g. `KEY_VALUE_LIST`) reads its value from. */
    val source: String? = null,
    val components: List<FlowComponent>? = null,
)
