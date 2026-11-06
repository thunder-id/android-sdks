// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.management

import com.google.gson.annotations.SerializedName

/**
 * The client metadata of a dynamic client registration (RFC 7591), used to register a client and, in full,
 * to replace its registration. The properties are camelCase here and sent in the protocol's snake_case.
 *
 * @property ouId The organization unit to associate the client with.
 * @property redirectUris The registered redirect URIs. Required.
 * @property grantTypes Such as `authorization_code`, `client_credentials` or `refresh_token`.
 * @property tokenEndpointAuthMethod One of `client_secret_basic`, `client_secret_post`, `private_key_jwt`,
 *   `none`.
 * @property jwks The client's JSON Web Key Set, as a JSON object.
 */
data class ClientRegistrationRequest(
    @SerializedName("redirect_uris")
    val redirectUris: List<String>,
    @SerializedName("ou_id")
    val ouId: String? = null,
    @SerializedName("post_logout_redirect_uris")
    val postLogoutRedirectUris: List<String>? = null,
    @SerializedName("backchannel_logout_uri")
    val backchannelLogoutUri: String? = null,
    @SerializedName("grant_types")
    val grantTypes: List<String>? = null,
    @SerializedName("response_types")
    val responseTypes: List<String>? = null,
    @SerializedName("client_name")
    val clientName: String? = null,
    @SerializedName("client_uri")
    val clientUri: String? = null,
    @SerializedName("logo_uri")
    val logoUri: String? = null,
    @SerializedName("token_endpoint_auth_method")
    val tokenEndpointAuthMethod: String? = null,
    @SerializedName("jwks_uri")
    val jwksUri: String? = null,
    val jwks: Map<String, Any?>? = null,
    val scope: String? = null,
    val contacts: List<String>? = null,
    @SerializedName("tos_uri")
    val tosUri: String? = null,
    @SerializedName("policy_uri")
    val policyUri: String? = null,
    @SerializedName("require_pushed_authorization_requests")
    val requirePushedAuthorizationRequests: Boolean? = null,
    @SerializedName("userinfo_signed_response_alg")
    val userinfoSignedResponseAlg: String? = null,
    @SerializedName("userinfo_encrypted_response_alg")
    val userinfoEncryptedResponseAlg: String? = null,
    @SerializedName("userinfo_encrypted_response_enc")
    val userinfoEncryptedResponseEnc: String? = null,
    @SerializedName("id_token_signed_response_alg")
    val idTokenSignedResponseAlg: String? = null,
    @SerializedName("id_token_encrypted_response_alg")
    val idTokenEncryptedResponseAlg: String? = null,
    @SerializedName("id_token_encrypted_response_enc")
    val idTokenEncryptedResponseEnc: String? = null,
)

/**
 * A dynamically registered client, as the server holds it.
 *
 * @property clientSecret Returned only by the registration itself: the server stores it write-only, so a read
 *   or an update never carries it. Keep it on registration. Left out of [toString].
 * @property clientSecretExpiresAt When the secret expires, in seconds since the epoch, or `0` if it never does.
 * @property appId The server's id for the application behind the client.
 */
data class ClientRegistration(
    @SerializedName("client_id")
    val clientId: String,
    @SerializedName("client_secret_expires_at")
    val clientSecretExpiresAt: Long = 0,
    @SerializedName("client_secret")
    val clientSecret: String? = null,
    @SerializedName("app_id")
    val appId: String? = null,
    @SerializedName("redirect_uris")
    val redirectUris: List<String>? = null,
    @SerializedName("post_logout_redirect_uris")
    val postLogoutRedirectUris: List<String>? = null,
    @SerializedName("backchannel_logout_uri")
    val backchannelLogoutUri: String? = null,
    @SerializedName("grant_types")
    val grantTypes: List<String>? = null,
    @SerializedName("response_types")
    val responseTypes: List<String>? = null,
    @SerializedName("client_name")
    val clientName: String? = null,
    @SerializedName("client_uri")
    val clientUri: String? = null,
    @SerializedName("logo_uri")
    val logoUri: String? = null,
    @SerializedName("token_endpoint_auth_method")
    val tokenEndpointAuthMethod: String? = null,
    @SerializedName("jwks_uri")
    val jwksUri: String? = null,
    val jwks: Map<String, Any?>? = null,
    val scope: String? = null,
    val contacts: List<String>? = null,
    @SerializedName("tos_uri")
    val tosUri: String? = null,
    @SerializedName("policy_uri")
    val policyUri: String? = null,
    @SerializedName("require_pushed_authorization_requests")
    val requirePushedAuthorizationRequests: Boolean? = null,
    @SerializedName("userinfo_signed_response_alg")
    val userinfoSignedResponseAlg: String? = null,
    @SerializedName("userinfo_encrypted_response_alg")
    val userinfoEncryptedResponseAlg: String? = null,
    @SerializedName("userinfo_encrypted_response_enc")
    val userinfoEncryptedResponseEnc: String? = null,
    @SerializedName("id_token_signed_response_alg")
    val idTokenSignedResponseAlg: String? = null,
    @SerializedName("id_token_encrypted_response_alg")
    val idTokenEncryptedResponseAlg: String? = null,
    @SerializedName("id_token_encrypted_response_enc")
    val idTokenEncryptedResponseEnc: String? = null,
) {
    override fun toString(): String =
        "ClientRegistration(clientId=$clientId, clientSecret=${clientSecret?.let { "***" }}, " +
            "clientSecretExpiresAt=$clientSecretExpiresAt, appId=$appId, clientName=$clientName, " +
            "redirectUris=$redirectUris, grantTypes=$grantTypes, responseTypes=$responseTypes, " +
            "tokenEndpointAuthMethod=$tokenEndpointAuthMethod, scope=$scope)"
}
