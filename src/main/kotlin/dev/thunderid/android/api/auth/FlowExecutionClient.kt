// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.auth

import com.google.gson.Gson
import com.google.gson.JsonParseException
import dev.thunderid.android.EmbeddedFlowResponse
import dev.thunderid.android.FlowSessionData
import dev.thunderid.android.FlowStatus
import dev.thunderid.android.FlowType
import dev.thunderid.android.IAMException
import dev.thunderid.android.ServerError
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
        organizationUnitId: String? = null,
        inputs: Map<String, String>? = null,
        sessionData: List<FlowSessionData>? = null,
    ): EmbeddedFlowResponse {
        val body = initiateBody(applicationId, flowType, organizationUnitId, inputs, sessionData)
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
        val response =
            try {
                httpClient.post<EmbeddedFlowResponse>("/flow/execute", body, requiresAuth = false)
            } catch (e: IAMException) {
                if (e.serverError?.code != EXECUTION_NOT_FOUND_CODE) throw e
                throw IAMException(ThunderIDErrorCode.FLOW_EXPIRED, "The flow execution expired", e, e.serverError)
            }
        return response.withErrorAsFailureReason()
    }

    /**
     * Starts a `SELF` flow named by [handle] at [url], the flow execution endpoint on the API base. Sent
     * with the signed-in user's access token, which is how the server knows the user, so the body carries
     * no user or application ID.
     */
    suspend fun startSelfFlow(
        url: String,
        handle: String,
        inputs: Map<String, String>,
    ): EmbeddedFlowResponse {
        val body =
            mutableMapOf<String, Any>(
                "flowType" to FlowType.SELF.value,
                "handle" to handle,
                "verbose" to true,
            )
        if (inputs.isNotEmpty()) body["inputs"] = inputs
        return sendSelfFlow(url, body)
    }

    /** Continues a `SELF` flow, also with the signed-in user's access token. */
    suspend fun continueSelfFlow(
        url: String,
        flowId: String,
        actionId: String?,
        inputs: Map<String, String>,
        challengeToken: String?,
    ): EmbeddedFlowResponse {
        val body = submitBody(flowId, actionId, challengeToken).toMutableMap()
        body["verbose"] = true
        if (inputs.isNotEmpty()) body["inputs"] = inputs
        return sendSelfFlow(url, body)
    }

    // An ERROR step or the server's own 4xx error is a refusal, reported with a fixed message and without
    // the response body. Errors raised before the request, such as a failed token refresh, and a 401 pass
    // through unchanged.
    private suspend fun sendSelfFlow(
        url: String,
        body: Map<String, Any>,
    ): EmbeddedFlowResponse {
        val response =
            try {
                httpClient.send<EmbeddedFlowResponse>("POST", url, Gson().toJson(body)) { response ->
                    if (response.statusCode in REFUSAL_STATUSES) {
                        ServerError.parse(response.body, allowOAuth = false)?.let { selfFlowRefused() }
                    } else {
                        null
                    }
                }
            } catch (e: IOException) {
                throw IAMException(ThunderIDErrorCode.NETWORK_ERROR, "The SELF flow request failed", e)
            } catch (e: JsonParseException) {
                throw IAMException(ThunderIDErrorCode.SERVER_ERROR, "The SELF flow response could not be read")
            }
        if (response.flowStatus == FlowStatus.ERROR) throw selfFlowRefused()
        return response.withErrorAsFailureReason()
    }

    private fun selfFlowRefused() = IAMException(ThunderIDErrorCode.VERIFICATION_FAILED, "The server refused the verification")

    private fun attestationTokenHeaders(token: String?): Map<String, String> = token?.let { mapOf("Attestation-Token" to it) } ?: emptyMap()

    /**
     * The request that starts a flow. `ouId` bounds an `AUTHENTICATION` flow to an organization unit; the
     * server refuses it on any other request, so it is never sent on a continuation. [sessionData] presents
     * kept SSO session handles, so the server can reuse a session without its cookie.
     */
    internal fun initiateBody(
        applicationId: String,
        flowType: FlowType,
        organizationUnitId: String? = null,
        inputs: Map<String, String>? = null,
        sessionData: List<FlowSessionData>? = null,
    ): Map<String, Any> {
        val body =
            mutableMapOf<String, Any>(
                "applicationId" to applicationId,
                "flowType" to flowType.value,
                "verbose" to true,
            )
        if (organizationUnitId != null && flowType == FlowType.AUTHENTICATION) body["ouId"] = organizationUnitId
        if (inputs != null) body["inputs"] = inputs
        val sessions =
            sessionData.orEmpty().mapNotNull { session ->
                val id = session.flowIdentifier?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val handle = session.sessionHandle?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                mapOf("flowIdentifier" to id, "sessionHandle" to handle)
            }
        if (sessions.isNotEmpty()) body["sessionData"] = sessions
        return body
    }

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

private val REFUSAL_STATUSES = setOf(400, 403, 404, 409)

/** The server's code for a step whose execution doesn't exist any more: it expired, or it already ended. */
private const val EXECUTION_NOT_FOUND_CODE = "FES-1004"

/** Surfaces the `error` object's text through `failureReason`, which the UI already shows. */
internal fun EmbeddedFlowResponse.withErrorAsFailureReason(): EmbeddedFlowResponse =
    copy(failureReason = error?.message?.defaultValue ?: error?.description?.defaultValue ?: failureReason)
