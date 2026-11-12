// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.auth

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import dev.thunderid.android.IAMException
import dev.thunderid.android.ServerError
import dev.thunderid.android.ThunderIDErrorCode
import dev.thunderid.android.ThunderIDHttpRequest
import dev.thunderid.android.ThunderIDHttpResponse
import dev.thunderid.android.TokenResponse
import dev.thunderid.android.http.HttpClient
import kotlinx.coroutines.delay
import java.net.URLEncoder

/** The OpenID provider metadata fields the SDK reads. */
internal data class ProviderMetadata(
    @SerializedName("issuer") val issuer: String? = null,
    @SerializedName("authorization_endpoint") val authorizationEndpoint: String? = null,
    @SerializedName("token_endpoint") val tokenEndpoint: String? = null,
    @SerializedName("backchannel_authentication_endpoint") val backchannelAuthenticationEndpoint: String? = null,
    @SerializedName("device_authorization_endpoint") val deviceAuthorizationEndpoint: String? = null,
    @SerializedName("pushed_authorization_request_endpoint") val pushedAuthorizationRequestEndpoint: String? = null,
    @SerializedName("require_pushed_authorization_requests") val requirePushedAuthorizationRequests: Boolean? = null,
    @SerializedName("jwks_uri") val jwksUri: String? = null,
    @SerializedName("userinfo_endpoint") val userinfoEndpoint: String? = null,
    @SerializedName("revocation_endpoint") val revocationEndpoint: String? = null,
)

/**
 * The OAuth 2.0 endpoints that take form-encoded requests from a public client: pushed authorization
 * requests, backchannel authentication, device authorization, and the grants polled at the token
 * endpoint. The client identifies itself with `client_id` only (`token_endpoint_auth_method: none`):
 * an app on a device cannot keep a client secret.
 */
internal class OAuthClient(
    private val http: HttpClient,
    private val baseUrl: String,
    // The discovery document's URL (`endpoints.wellKnown`), when the app names one.
    private val wellKnown: String? = null,
) {
    private var metadata: ProviderMetadata? = null

    /**
     * The server's metadata, read once and kept from `endpoints.wellKnown`, else from
     * `{baseUrl}/.well-known/openid-configuration`. `null` when it cannot be read; it is tried again next time.
     */
    suspend fun metadata(): ProviderMetadata? {
        metadata?.let { return it }
        val url = wellKnown ?: "$baseUrl$WELL_KNOWN_SUFFIX"
        val response =
            runCatching {
                http.exchange(ThunderIDHttpRequest("GET", url, mapOf("Accept" to JSON), null))
            }.getOrNull() ?: return null
        if (response.statusCode !in 200..299) return null
        return runCatching { Gson().fromJson(response.body, ProviderMetadata::class.java) }
            .getOrNull()
            ?.also { metadata = it }
    }

    /**
     * The token endpoint: the discovered `token_endpoint`, else `{baseUrl}/oauth2/token`. For organization unit
     * [ouId] it is always the unit's own, `{baseUrl}/ou/{ouId}/oauth2/token`.
     */
    suspend fun tokenUrl(ouId: String?): String =
        if (ouId == null) discovered(metadata()?.tokenEndpoint, "token") else "${ouBase(ouId)}/oauth2/token"

    /**
     * The authorization endpoint: the discovered `authorization_endpoint`, else `{baseUrl}/oauth2/authorize`. For
     * organization unit [ouId] it is always the unit's own. It does not read the metadata, so that a URL can be
     * built without suspending: it uses the metadata once something has read it, as `ThunderIDClient.signIn` does.
     */
    fun authorizeUrl(ouId: String?): String =
        if (ouId == null) {
            discovered(metadata?.authorizationEndpoint, "authorize")
        } else {
            "${ouBase(ouId)}/oauth2/authorize"
        }

    /** The JWKS URL the server's metadata names in `jwks_uri`, or `null` when it names none. */
    suspend fun jwksUrl(): String? = metadata()?.jwksUri?.takeIf { it.isNotBlank() }

    /** The UserInfo endpoint the server's metadata names in `userinfo_endpoint`, or `null` when it names none. */
    suspend fun userinfoUrl(): String? = metadata()?.userinfoEndpoint?.takeIf { it.isNotBlank() }

    /**
     * The issuer an ID token's `iss` must equal (OpenID Connect Discovery 1.0 §4.3). The discovery base is
     * `endpoints.wellKnown` without its `/.well-known/openid-configuration` suffix, else `baseUrl`. The issuer is
     * the discovered `issuer` when it is the discovery base with or without one trailing slash, else the discovery
     * base itself. When `endpoints.wellKnown` does not end with that suffix the app chose the document itself, so
     * the issuer is the discovered `issuer`, and `null` when the document cannot be read or names none.
     */
    suspend fun expectedIssuer(): String? {
        if (wellKnown != null && !wellKnown.endsWith(WELL_KNOWN_SUFFIX)) {
            return metadata()?.issuer?.takeIf { it.isNotBlank() }
        }
        val base = wellKnown?.removeSuffix(WELL_KNOWN_SUFFIX) ?: baseUrl
        val issuer = metadata()?.issuer ?: return base
        return if (issuer.removeSuffix("/") == base.removeSuffix("/")) issuer else base
    }

    private fun discovered(
        endpoint: String?,
        path: String,
    ): String = endpoint?.takeIf { it.isNotBlank() } ?: "$baseUrl/oauth2/$path"

    private fun ouBase(ouId: String): String = "$baseUrl/ou/${encodePathSegment(ouId)}"

    /**
     * Redeems an authorization code at the token endpoint, or at organization unit [ouId]'s when the sign-in was
     * bounded to one.
     */
    suspend fun redeemCode(
        ouId: String?,
        form: Map<String, String>,
    ): TokenResponse = requestToken(ouId, form, "The authorization code was refused")

    /**
     * Posts a token request to the token endpoint, or to organization unit [ouId]'s. The body is form-encoded, as
     * RFC 6749 requires and the server reads.
     */
    suspend fun requestToken(
        ouId: String?,
        form: Map<String, String>,
        failureMessage: String = "The token request was refused",
    ): TokenResponse {
        val response = postForm(tokenUrl(ouId), form)
        if (response.statusCode !in 200..299) throw failure(response, failureMessage, ouId)
        return parseToken(response)
    }

    /**
     * Revokes a token (RFC 7009) at the discovered `revocation_endpoint`, else `{baseUrl}/oauth2/revoke`,
     * form-encoded. The server answers 200 whether or not it knew the token.
     */
    suspend fun revoke(form: Map<String, String>) {
        val response = postForm(discovered(metadata()?.revocationEndpoint, "revoke"), form)
        if (response.statusCode !in 200..299) throw failure(response, "The token revocation was refused")
    }

    /** Pushes the authorization request [form] (RFC 9126) and returns its `request_uri`. */
    suspend fun pushAuthorizationRequest(form: Map<String, String>): String {
        val endpoint = metadata()?.pushedAuthorizationRequestEndpoint ?: "$baseUrl/oauth2/par"
        val response = postForm(endpoint, form)
        if (response.statusCode !in 200..299) throw failure(response, "The pushed authorization request was refused")
        return parseObject(response.body)?.string("request_uri")
            ?: throw IAMException(ThunderIDErrorCode.SERVER_ERROR, "The server returned no request_uri")
    }

    suspend fun initiateCIBA(
        clientId: String,
        scopes: List<String>,
        options: CIBAInitiateOptions,
    ): CIBAInitiateResponse {
        val hints = listOfNotNull(options.loginHint, options.loginHintToken, options.idTokenHint).filter { it.isNotEmpty() }
        if (hints.size != 1) {
            throw IAMException(
                ThunderIDErrorCode.INVALID_INPUT,
                "Provide exactly one of loginHint, loginHintToken or idTokenHint",
            )
        }
        val endpoint =
            metadata()?.backchannelAuthenticationEndpoint?.takeIf { it.isNotBlank() }
                ?: throw IAMException(
                    ThunderIDErrorCode.UNSUPPORTED_OPERATION,
                    "The server's metadata names no backchannel_authentication_endpoint",
                )
        val form =
            buildMap {
                put("client_id", clientId)
                scopes.joinToString(" ").takeIf { it.isNotEmpty() }?.let { put("scope", it) }
                options.loginHint?.takeIf { it.isNotEmpty() }?.let { put("login_hint", it) }
                options.loginHintToken?.takeIf { it.isNotEmpty() }?.let { put("login_hint_token", it) }
                options.idTokenHint?.takeIf { it.isNotEmpty() }?.let { put("id_token_hint", it) }
                options.bindingMessage?.let { put("binding_message", it) }
                options.acrValues?.let { put("acr_values", it) }
                options.requestedExpiry?.let { put("requested_expiry", it.toString()) }
                options.authorizationDetails?.takeIf { it.isNotEmpty() }?.let { put("authorization_details", Gson().toJson(it)) }
            }
        val response = postForm(endpoint, form)
        if (response.statusCode !in 200..299) throw failure(response, "The backchannel authentication request was refused")
        val json = parseObject(response.body)
        val authReqId =
            json?.string("auth_req_id")
                ?: throw IAMException(ThunderIDErrorCode.SERVER_ERROR, "The server returned no auth_req_id")
        return CIBAInitiateResponse(
            authReqId = authReqId,
            expiresIn = json.int("expires_in") ?: DEFAULT_CIBA_EXPIRY,
            interval = json.int("interval") ?: DEFAULT_INTERVAL,
        )
    }

    suspend fun startDeviceAuthorization(
        clientId: String,
        scopes: List<String>,
    ): DeviceAuthorization {
        val endpoint = metadata()?.deviceAuthorizationEndpoint ?: "$baseUrl/oauth2/device-authorize"
        val form =
            buildMap {
                put("client_id", clientId)
                scopes.joinToString(" ").takeIf { it.isNotEmpty() }?.let { put("scope", it) }
            }
        val response = postForm(endpoint, form)
        if (response.statusCode !in 200..299) throw failure(response, "The device authorization request was refused")
        val json = parseObject(response.body)
        val deviceCode = json?.string("device_code")
        val userCode = json?.string("user_code")
        val verificationUri = json?.string("verification_uri")
        if (deviceCode == null || userCode == null || verificationUri == null) {
            throw IAMException(ThunderIDErrorCode.SERVER_ERROR, "The device authorization response is incomplete")
        }
        return DeviceAuthorization(
            deviceCode = deviceCode,
            userCode = userCode,
            verificationUri = verificationUri,
            verificationUriComplete = json.string("verification_uri_complete"),
            expiresIn = json.int("expires_in") ?: DEFAULT_DEVICE_EXPIRY,
            interval = json.int("interval") ?: DEFAULT_INTERVAL,
        )
    }

    /**
     * Polls the token endpoint with [form] every [interval] seconds, waiting first, until it issues tokens.
     * `authorization_pending` keeps polling and `slow_down` adds five seconds to the interval; any other
     * error ends the polling. Cancelling the coroutine stops it.
     */
    suspend fun pollToken(
        form: Map<String, String>,
        interval: Int,
    ): TokenResponse {
        var wait = maxOf(interval, 1)
        while (true) {
            delay(wait * 1000L)
            val response = postForm(tokenUrl(null), form)
            if (response.statusCode in 200..299) return parseToken(response)
            when (ServerError.parse(response.body)?.code) {
                "authorization_pending" -> continue
                "slow_down" -> wait += SLOW_DOWN_STEP
                else -> throw failure(response, "Polling ended")
            }
        }
    }

    suspend fun postForm(
        url: String,
        form: Map<String, String>,
    ): ThunderIDHttpResponse =
        http.exchange(
            ThunderIDHttpRequest(
                "POST",
                url,
                mapOf("Content-Type" to "application/x-www-form-urlencoded", "Accept" to JSON),
                encodeForm(form),
            ),
        )

    private fun parseToken(response: ThunderIDHttpResponse): TokenResponse {
        val token = runCatching { Gson().fromJson(response.body, TokenResponse::class.java) }.getOrNull()
        @Suppress("SENSELESS_COMPARISON")
        if (token?.accessToken == null) throw IAMException(ThunderIDErrorCode.SERVER_ERROR, "The server returned no access token")
        return token
    }

    companion object {
        private const val JSON = "application/json"
        private const val WELL_KNOWN_SUFFIX = "/.well-known/openid-configuration"
        private const val DEFAULT_INTERVAL = 5
        private const val DEFAULT_CIBA_EXPIRY = 120
        private const val DEFAULT_DEVICE_EXPIRY = 600
        private const val SLOW_DOWN_STEP = 5

        fun encodeForm(form: Map<String, String>): String =
            form.entries.joinToString("&") { (name, value) ->
                "${URLEncoder.encode(name, Charsets.UTF_8.name())}=${URLEncoder.encode(value, Charsets.UTF_8.name())}"
            }

        /**
         * Maps a refused OAuth request to an [IAMException] carrying the server's error. An `unauthorized_client`
         * from an endpoint acting for an organization unit means the unit does not exist or the application
         * cannot be used from it, which the server does not tell apart.
         */
        fun failure(
            response: ThunderIDHttpResponse,
            message: String,
            ouId: String? = null,
        ): IAMException {
            val serverError = ServerError.parse(response.body)
            val code =
                when {
                    ouId != null && serverError?.code == "unauthorized_client" -> {
                        ThunderIDErrorCode.ORGANIZATION_UNIT_UNAVAILABLE
                    }

                    serverError?.code == "invalid_grant" -> {
                        ThunderIDErrorCode.INVALID_GRANT
                    }

                    serverError?.code == "access_denied" || serverError?.code == "expired_token" -> {
                        ThunderIDErrorCode.AUTHENTICATION_FAILED
                    }

                    response.statusCode == 401 -> {
                        ThunderIDErrorCode.AUTHENTICATION_FAILED
                    }

                    response.statusCode in 500..599 -> {
                        ThunderIDErrorCode.SERVER_ERROR
                    }

                    response.statusCode in 400..499 -> {
                        ThunderIDErrorCode.INVALID_INPUT
                    }

                    else -> {
                        ThunderIDErrorCode.UNKNOWN_ERROR
                    }
                }
            val detail = serverError?.code?.let { ": $it" } ?: " (${response.statusCode})"
            return IAMException(code, "$message$detail", serverError = serverError)
        }

        private fun parseObject(body: String): JsonObject? =
            runCatching { JsonParser.parseString(body) }.getOrNull()?.takeIf { it.isJsonObject }?.asJsonObject

        private fun JsonObject.string(name: String): String? =
            get(name)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotEmpty() }

        private fun JsonObject.int(name: String): Int? = get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt
    }
}

/** Percent-encodes [value] as one URL path segment, so a `/` or a space in it cannot change the path. */
internal fun encodePathSegment(value: String): String = encodeUriComponent(value)

/**
 * Percent-encodes [value] for a URL path segment or query value (RFC 3986): a space becomes `%20` and a `+`
 * becomes `%2B`, so neither reads back as the other.
 */
internal fun encodeUriComponent(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

/** Builds a URL query from [params], in order, with every value percent-encoded by [encodeUriComponent]. */
internal fun encodeQuery(params: List<Pair<String, String>>): String =
    params.joinToString("&") { (name, value) -> "$name=${encodeUriComponent(value)}" }
