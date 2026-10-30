// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.auth

import com.google.gson.Gson
import com.google.gson.JsonParseException
import dev.thunderid.android.EmbeddedFlowResponse
import dev.thunderid.android.FlowStatus
import dev.thunderid.android.FlowType
import dev.thunderid.android.IAMException
import dev.thunderid.android.ThunderIDErrorCode
import dev.thunderid.android.http.HttpClient
import java.io.IOException

/**
 * Drives the ThunderID Flow Execution API for app-native sign-in, sign-up, and recovery (spec §6.1–6.3).
 */
internal class FlowExecutionClient(
    private val httpClient: HttpClient,
) {
    suspend fun initiate(
        applicationId: String,
        flowType: FlowType,
        attestationToken: String? = null,
    ): EmbeddedFlowResponse {
        val body =
            mapOf(
                "applicationId" to applicationId,
                "flowType" to flowType.value,
                "verbose" to true,
            )
        return httpClient
            .post<EmbeddedFlowResponse>("/flow/execute", body, requiresAuth = false, headers = attestationTokenHeaders(attestationToken))
            .withErrorAsFailureReason()
    }

    suspend fun submit(
        flowId: String,
        actionId: String?,
        inputs: Map<String, String>,
        challengeToken: String?,
    ): EmbeddedFlowResponse {
        val body = submitBody(flowId, actionId, challengeToken).toMutableMap()
        body["verbose"] = true
        if (inputs.isNotEmpty()) body["inputs"] = inputs
        return httpClient.post<EmbeddedFlowResponse>("/flow/execute", body, requiresAuth = false).withErrorAsFailureReason()
    }

    /**
     * Starts a `SELF_SERVICE` flow named by [handle] at [url], the flow execution endpoint on `baseUrl`. Sent
     * with the signed-in user's access token, which is how the server knows the user, so the body carries
     * no user or application ID.
     */
    suspend fun startSelfService(
        url: String,
        handle: String,
        inputs: Map<String, String>,
    ): EmbeddedFlowResponse {
        val body =
            mutableMapOf<String, Any>(
                "flowType" to FlowType.SELF_SERVICE.value,
                "handle" to handle,
                "verbose" to true,
            )
        if (inputs.isNotEmpty()) body["inputs"] = inputs
        return sendSelfService(url, body)
    }

    /** Continues a `SELF_SERVICE` flow, also with the signed-in user's access token. */
    suspend fun continueSelfService(
        url: String,
        flowId: String,
        actionId: String?,
        inputs: Map<String, String>,
        challengeToken: String?,
    ): EmbeddedFlowResponse {
        val body = submitBody(flowId, actionId, challengeToken).toMutableMap()
        body["verbose"] = true
        if (inputs.isNotEmpty()) body["inputs"] = inputs
        return sendSelfService(url, body)
    }

    // An ERROR step or a 4xx is a refusal, reported with a fixed message and without the response body.
    private suspend fun sendSelfService(
        url: String,
        body: Map<String, Any>,
    ): EmbeddedFlowResponse {
        val response =
            try {
                httpClient.send<EmbeddedFlowResponse>("POST", url, Gson().toJson(body))
            } catch (e: IAMException) {
                if (e.code !in REFUSAL_CODES) throw e
                throw selfServiceRefused()
            } catch (e: IOException) {
                throw IAMException(ThunderIDErrorCode.NETWORK_ERROR, "The self-service flow request failed", e)
            } catch (e: JsonParseException) {
                throw IAMException(ThunderIDErrorCode.SERVER_ERROR, "The self-service flow response could not be read")
            }
        if (response.flowStatus == FlowStatus.ERROR) throw selfServiceRefused()
        return response.withErrorAsFailureReason()
    }

    private fun selfServiceRefused() = IAMException(ThunderIDErrorCode.VERIFICATION_FAILED, "The server refused the verification")

    private fun attestationTokenHeaders(token: String?): Map<String, String> = token?.let { mapOf("Attestation-Token" to it) } ?: emptyMap()

    internal fun submitBody(
        flowId: String,
        actionId: String?,
        challengeToken: String?,
    ): Map<String, Any> {
        val body = mutableMapOf<String, Any>("executionId" to flowId)
        if (actionId != null) body["action"] = actionId
        if (challengeToken != null) body["challengeToken"] = challengeToken
        return body
    }
}

// The codes HttpClient maps a 4xx status to.
private val REFUSAL_CODES =
    setOf(
        ThunderIDErrorCode.INVALID_INPUT,
        ThunderIDErrorCode.AUTHENTICATION_FAILED,
        ThunderIDErrorCode.FORBIDDEN,
        ThunderIDErrorCode.NOT_FOUND,
        ThunderIDErrorCode.USER_ALREADY_EXISTS,
    )

/** Surfaces the `error` object's text through `failureReason`, which the UI already shows. */
internal fun EmbeddedFlowResponse.withErrorAsFailureReason(): EmbeddedFlowResponse =
    copy(failureReason = error?.message?.defaultValue ?: error?.description?.defaultValue ?: failureReason)
