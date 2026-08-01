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

package dev.thunderid.android.auth

import android.content.Context
import androidx.credentials.CreateCredentialResponse
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.gson.JsonParser
import dev.thunderid.android.IAMException
import dev.thunderid.android.ThunderIDErrorCode
import kotlinx.coroutines.CancellationException

/**
 * Drives native WebAuthn/passkey ceremonies via Jetpack Credential Manager, translating the
 * flow-execution API's `passkeyChallenge`/`passkeyCreationOptions` JSON strings into the flat
 * `credentialId`/`clientDataJSON`/`authenticatorData`/`signature`/`userHandle` (assertion) or
 * `credentialId`/`clientDataJSON`/`attestationObject` (attestation) input maps the flow expects
 * on the next `/flow/execute` submit.
 */
internal class PasskeyClient(
    private val credentialManager: (Context) -> CredentialManager = { context -> CredentialManager.create(context) },
) {
    /** Performs a WebAuthn assertion ceremony (sign-in with an existing passkey). */
    suspend fun authenticate(
        context: Context,
        requestOptionsJson: String,
    ): Map<String, String> {
        val response =
            runCatching {
                credentialManager(context).getCredential(
                    context = context,
                    request = GetCredentialRequest(listOf(GetPublicKeyCredentialOption(requestOptionsJson))),
                )
            }.getOrElse {
                if (it is CancellationException) throw it
                throw it.toIAMException()
            }
        return extractAssertionInputs(response)
    }

    /** Performs a WebAuthn attestation ceremony (registers a new passkey). */
    suspend fun register(
        context: Context,
        creationOptionsJson: String,
    ): Map<String, String> {
        val response =
            runCatching {
                credentialManager(context).createCredential(
                    context = context,
                    request = CreatePublicKeyCredentialRequest(creationOptionsJson),
                )
            }.getOrElse {
                if (it is CancellationException) throw it
                throw it.toIAMException()
            }
        return extractAttestationInputs(response)
    }

    internal fun extractAssertionInputs(response: GetCredentialResponse): Map<String, String> {
        val credential =
            response.credential as? PublicKeyCredential
                ?: throw IAMException(ThunderIDErrorCode.PASSKEY_FAILED, "No public key credential returned")
        return parseAssertionResponseJson(credential.authenticationResponseJson)
    }

    internal fun extractAttestationInputs(response: CreateCredentialResponse): Map<String, String> {
        val credentialResponse =
            response as? CreatePublicKeyCredentialResponse
                ?: throw IAMException(ThunderIDErrorCode.PASSKEY_FAILED, "No public key credential returned")
        return parseAttestationResponseJson(credentialResponse.registrationResponseJson)
    }
}

/**
 * Parses a Credential Manager `authenticationResponseJson` (the standard WebAuthn
 * `PublicKeyCredential.toJSON()` shape) into the flat map the flow-execution API expects.
 */
internal fun parseAssertionResponseJson(json: String): Map<String, String> {
    val root = JsonParser.parseString(json).asJsonObject
    val response = root.getAsJsonObject("response")
    val inputs =
        mutableMapOf(
            "credentialId" to root.get("id").asString,
            "clientDataJSON" to response.get("clientDataJSON").asString,
            "authenticatorData" to response.get("authenticatorData").asString,
            "signature" to response.get("signature").asString,
        )
    response.get("userHandle")?.takeIf { !it.isJsonNull }?.let { inputs["userHandle"] = it.asString }
    return inputs
}

/**
 * Parses a Credential Manager `registrationResponseJson` (the standard WebAuthn
 * `PublicKeyCredential.toJSON()` shape) into the flat map the flow-execution API expects.
 */
internal fun parseAttestationResponseJson(json: String): Map<String, String> {
    val root = JsonParser.parseString(json).asJsonObject
    val response = root.getAsJsonObject("response")
    return mapOf(
        "credentialId" to root.get("id").asString,
        "clientDataJSON" to response.get("clientDataJSON").asString,
        "attestationObject" to response.get("attestationObject").asString,
    )
}

private fun Throwable.toIAMException(): IAMException =
    when (this) {
        is GetCredentialCancellationException, is CreateCredentialCancellationException -> {
            IAMException(ThunderIDErrorCode.PASSKEY_CANCELLED, "Passkey ceremony was cancelled by the user", this)
        }

        is NoCredentialException -> {
            IAMException(ThunderIDErrorCode.PASSKEY_NO_CREDENTIAL, "No passkey credential is available", this)
        }

        is GetCredentialException -> {
            IAMException(ThunderIDErrorCode.PASSKEY_FAILED, "Passkey authentication failed: ${this.type}", this)
        }

        is CreateCredentialException -> {
            IAMException(ThunderIDErrorCode.PASSKEY_FAILED, "Passkey registration failed: ${this.type}", this)
        }

        is IAMException -> {
            this
        }

        else -> {
            IAMException(ThunderIDErrorCode.PASSKEY_FAILED, "Passkey ceremony failed", this)
        }
    }
