// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Typed error codes for all ThunderID SDK error conditions (spec §10.2).
 */
enum class ThunderIDErrorCode(
    val value: String,
) {
    // Configuration
    SDK_NOT_INITIALIZED("SDK_NOT_INITIALIZED"),
    ALREADY_INITIALIZED("ALREADY_INITIALIZED"),
    INVALID_CONFIGURATION("INVALID_CONFIGURATION"),
    INVALID_REDIRECT_URI("INVALID_REDIRECT_URI"),
    UNSUPPORTED_OPERATION("UNSUPPORTED_OPERATION"),

    // Authentication
    AUTHENTICATION_FAILED("AUTHENTICATION_FAILED"),
    USER_ACCOUNT_LOCKED("USER_ACCOUNT_LOCKED"),
    USER_ACCOUNT_DISABLED("USER_ACCOUNT_DISABLED"),
    SESSION_EXPIRED("SESSION_EXPIRED"),
    MFA_REQUIRED("MFA_REQUIRED"),
    MFA_FAILED("MFA_FAILED"),
    INVALID_GRANT("INVALID_GRANT"),
    CONSENT_REQUIRED("CONSENT_REQUIRED"),

    /**
     * The operation needs a more recent sign-in (a `401` with `error="insufficient_user_authentication"`, or the
     * server code `AUTH-4011`). [IAMException.authenticationChallenge] carries `maxAge`. Sign in again with
     * [ThunderIDClient.reauthenticate] and retry.
     */
    STEP_UP_REQUIRED("STEP_UP_REQUIRED"),

    /** The embedded flow execution expired, or already ended, so the server refused the step. The flow has to start again. */
    FLOW_EXPIRED("FLOW_EXPIRED"),

    /**
     * The organization unit a sign-in or switch named does not exist, or the application cannot be used
     * from it. The server does not tell the two apart (`unauthorized_client`, or `FES-1023` for an embedded
     * flow).
     */
    ORGANIZATION_UNIT_UNAVAILABLE("ORGANIZATION_UNIT_UNAVAILABLE"),

    // Registration
    USER_ALREADY_EXISTS("USER_ALREADY_EXISTS"),
    INVALID_INPUT("INVALID_INPUT"),
    INVITATION_CODE_INVALID("INVITATION_CODE_INVALID"),
    INVITATION_CODE_EXPIRED("INVITATION_CODE_EXPIRED"),
    REGISTRATION_DISABLED("REGISTRATION_DISABLED"),

    // Recovery
    RECOVERY_FAILED("RECOVERY_FAILED"),
    CONFIRMATION_CODE_INVALID("CONFIRMATION_CODE_INVALID"),
    CONFIRMATION_CODE_EXPIRED("CONFIRMATION_CODE_EXPIRED"),

    // Verification: the server refused a self user attribute verification (an `ERROR` step or an HTTP 4xx).
    VERIFICATION_FAILED("VERIFICATION_FAILED"),

    // Passkey / WebAuthn
    PASSKEY_NOT_SUPPORTED("PASSKEY_NOT_SUPPORTED"),
    PASSKEY_CANCELLED("PASSKEY_CANCELLED"),
    PASSKEY_NO_CREDENTIAL("PASSKEY_NO_CREDENTIAL"),
    PASSKEY_FAILED("PASSKEY_FAILED"),

    // Management
    FORBIDDEN("FORBIDDEN"),
    NOT_FOUND("NOT_FOUND"),

    /** An administration flow did not complete. [dev.thunderid.android.api.management.AdministrationFlowError] carries why. */
    ADMINISTRATION_FLOW_FAILED("ADMINISTRATION_FLOW_FAILED"),

    // Network & Server
    NETWORK_ERROR("NETWORK_ERROR"),
    REQUEST_TIMEOUT("REQUEST_TIMEOUT"),
    SERVER_ERROR("SERVER_ERROR"),
    UNKNOWN_ERROR("UNKNOWN_ERROR"),
    ;

    companion object {
        fun fromValue(value: String): ThunderIDErrorCode = entries.firstOrNull { it.value == value } ?: UNKNOWN_ERROR
    }
}

/**
 * The error every SDK operation raises.
 *
 * @property serverError The error envelope the server returned with a failed request, when its body was
 *   one: the server's own error code, and its message and description with their translation keys and
 *   parameters. Use it to show a message specific to the server's code. The exception's message carries
 *   the envelope's [ServerError.readableMessage] when it has one.
 * @property authenticationChallenge Set on a `401` that asks for a more recent sign-in, whose code is then
 *   [ThunderIDErrorCode.STEP_UP_REQUIRED]. The operation itself does not sign in again or retry.
 */
open class IAMException(
    val code: ThunderIDErrorCode,
    message: String,
    cause: Throwable? = null,
    val serverError: ServerError? = null,
    val authenticationChallenge: AuthenticationChallenge? = null,
) : Exception("[$code] $message", cause)

/** A re-sign-in that another account completed. The session stays as it was. */
internal class AnotherAccountSignedInException : IAMException(ThunderIDErrorCode.AUTHENTICATION_FAILED, "Another user signed in")

/** Whether a re-sign-in failed because another account signed in. The session stays as it was. */
val IAMException.isReauthenticationByAnotherAccount: Boolean
    get() = this is AnotherAccountSignedInException

/**
 * A `401` that asks for a more recent sign-in (RFC 9470): the server's `WWW-Authenticate: Bearer` challenge
 * with `error="insufficient_user_authentication"`. Sign the user in again with [ThunderIDClient.reauthenticate]
 * and retry.
 *
 * @property error The challenge's `error`, `insufficient_user_authentication`.
 * @property errorDescription The challenge's `error_description`, when sent.
 * @property maxAge The challenge's `max_age`: the sign-in must be at most this many seconds old.
 */
data class AuthenticationChallenge(
    val error: String,
    val errorDescription: String? = null,
    val maxAge: Int? = null,
) {
    companion object {
        /** The `error` of a challenge that asks for a more recent sign-in. */
        const val INSUFFICIENT_USER_AUTHENTICATION = "insufficient_user_authentication"

        /** The server's code for a sign-in older than the operation allows. */
        const val RECENT_AUTHENTICATION_REQUIRED_CODE = "AUTH-4011"

        private val SCHEME = Regex("""(?i)(?:^|[\s,])Bearer(?:\s+|$)""")
        private val PARAM = Regex("""([A-Za-z0-9_!#$%&'*+.^`|~-]+)\s*=\s*(?:"((?:[^"\\]|\\.)*)"|([^\s,]*))""")

        /**
         * Reads the challenge from a `401` response: from its `WWW-Authenticate` header, else from the
         * body's `description.params.maxAge` when the server's code is `AUTH-4011`. Returns `null` when the
         * response does not ask for a more recent sign-in.
         */
        fun from(
            wwwAuthenticate: String?,
            serverError: ServerError?,
        ): AuthenticationChallenge? =
            parse(wwwAuthenticate)
                ?: serverError?.takeIf { it.code == RECENT_AUTHENTICATION_REQUIRED_CODE }?.let {
                    AuthenticationChallenge(
                        error = INSUFFICIENT_USER_AUTHENTICATION,
                        maxAge =
                            it.description
                                ?.params
                                ?.get("maxAge")
                                ?.toNonNegativeInt(),
                    )
                }

        /**
         * Reads a `WWW-Authenticate` header value, or returns `null` when it has no `Bearer` challenge with
         * `error="insufficient_user_authentication"`.
         */
        fun parse(wwwAuthenticate: String?): AuthenticationChallenge? {
            if (wwwAuthenticate.isNullOrBlank()) return null
            val start =
                SCHEME
                    .find(wwwAuthenticate)
                    ?.range
                    ?.last
                    ?.plus(1) ?: return null
            // Reads the Bearer challenge's parameters up to the next scheme.
            val rest = wwwAuthenticate.substring(start)
            val params = mutableMapOf<String, String>()
            var end = 0
            for (match in PARAM.findAll(rest)) {
                if (rest.substring(end, match.range.first).any { it != ',' && !it.isWhitespace() }) break
                val quoted = match.groups[2]?.value?.replace(Regex("""\\(.)"""), "$1")
                params.putIfAbsent(match.groupValues[1].lowercase(), quoted ?: match.groupValues[3])
                end = match.range.last + 1
            }
            val error = params["error"]?.takeIf { it == INSUFFICIENT_USER_AUTHENTICATION } ?: return null
            return AuthenticationChallenge(
                error = error,
                errorDescription = params["error_description"]?.takeIf { it.isNotEmpty() },
                maxAge = params["max_age"]?.toNonNegativeInt(),
            )
        }

        private fun String.toNonNegativeInt(): Int? = trim().toIntOrNull()?.takeIf { it >= 0 }
    }
}

/**
 * A localizable message in a server error: its translation key, its English text, and the values its
 * placeholders take.
 */
data class ServerErrorMessage(
    val key: String? = null,
    val defaultValue: String? = null,
    val params: Map<String, String>? = null,
)

/**
 * The error envelope the ThunderID server returns with a failed request.
 *
 * @property code The server's error code, such as `USR-1027`, or the OAuth `error`, such as
 *   `invalid_client_metadata`, for an endpoint that answers in the OAuth form.
 */
data class ServerError(
    val code: String,
    val message: ServerErrorMessage? = null,
    val description: ServerErrorMessage? = null,
) {
    /** The envelope's English text: the message's `defaultValue`, else the description's. */
    val readableMessage: String?
        get() =
            message?.defaultValue?.takeIf { it.isNotBlank() }
                ?: description?.defaultValue?.takeIf { it.isNotBlank() }

    companion object {
        /**
         * Reads the server's error envelope from a raw response body, or returns `null` when the body is
         * not one: not JSON, not an object, or without a string `code`. A message or description sent as
         * a plain string becomes its [ServerErrorMessage.defaultValue]. An OAuth 2.0 error body (`error`,
         * `error_description`) is read too, with `error` as the code and `error_description` as the message.
         */
        fun parse(body: String?): ServerError? = parse(body, allowOAuth = true)

        internal fun parse(
            body: String?,
            allowOAuth: Boolean,
        ): ServerError? {
            if (body.isNullOrBlank()) return null
            val json =
                runCatching { JsonParser.parseString(body) }
                    .getOrNull()
                    ?.takeIf { it.isJsonObject }
                    ?.asJsonObject
                    ?: return null
            val code =
                json.get("code")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                    ?: return if (allowOAuth) oauthError(json) else null
            return ServerError(
                code = code.asString,
                message = message(json.get("message")),
                description = message(json.get("description")),
            )
        }

        /**
         * An OAuth 2.0 error body (`error`, `error_description`), as dynamic client registration answers:
         * `error` becomes the code and `error_description` the message.
         */
        private fun oauthError(json: JsonObject): ServerError? {
            val error = json.get("error")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString } ?: return null
            return ServerError(
                code = error.asString,
                message = json.string("error_description")?.let { ServerErrorMessage(defaultValue = it) },
            )
        }

        private fun message(element: JsonElement?): ServerErrorMessage? =
            when {
                element == null || element.isJsonNull -> {
                    null
                }

                element.isJsonPrimitive -> {
                    ServerErrorMessage(defaultValue = element.asString)
                }

                element.isJsonObject -> {
                    val obj = element.asJsonObject
                    ServerErrorMessage(
                        key = obj.string("key"),
                        defaultValue = obj.string("defaultValue"),
                        params =
                            obj
                                .get("params")
                                ?.takeIf { it.isJsonObject }
                                ?.asJsonObject
                                ?.entrySet()
                                ?.filter { it.value.isJsonPrimitive }
                                ?.associate { it.key to it.value.asString },
                    )
                }

                else -> {
                    null
                }
            }

        private fun JsonObject.string(name: String): String? = get(name)?.takeIf { it.isJsonPrimitive }?.asString
    }
}
