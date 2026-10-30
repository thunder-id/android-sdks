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

    /** An administration flow did not complete. [dev.thunderid.android.management.AdministrationFlowError] carries why. */
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
 *   parameters. Use it to show a message specific to the server's code.
 */
open class IAMException(
    val code: ThunderIDErrorCode,
    message: String,
    cause: Throwable? = null,
    val serverError: ServerError? = null,
) : Exception("[$code] $message", cause)

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
 * @property code The server's error code, such as `USR-1027`.
 */
data class ServerError(
    val code: String,
    val message: ServerErrorMessage? = null,
    val description: ServerErrorMessage? = null,
) {
    companion object {
        /**
         * Reads the server's error envelope from a raw response body, or returns `null` when the body is
         * not one: not JSON, not an object, or without a string `code`. A message or description sent as
         * a plain string becomes its [ServerErrorMessage.defaultValue].
         */
        fun parse(body: String?): ServerError? {
            if (body.isNullOrBlank()) return null
            val json =
                runCatching { JsonParser.parseString(body) }
                    .getOrNull()
                    ?.takeIf { it.isJsonObject }
                    ?.asJsonObject
                    ?: return null
            val code = json.get("code")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString } ?: return null
            return ServerError(
                code = code.asString,
                message = message(json.get("message")),
                description = message(json.get("description")),
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
