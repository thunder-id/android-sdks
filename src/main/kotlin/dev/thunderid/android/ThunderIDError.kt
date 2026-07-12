/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package dev.thunderid.android

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

class IAMException(
    val code: ThunderIDErrorCode,
    message: String,
    cause: Throwable? = null,
) : Exception("[$code] $message", cause)
