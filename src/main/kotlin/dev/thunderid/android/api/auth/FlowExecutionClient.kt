// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.auth

import dev.thunderid.android.EmbeddedFlowResponse
import dev.thunderid.android.FlowType
import dev.thunderid.android.http.HttpClient

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
    ): EmbeddedFlowResponse {
        val body = initiateBody(applicationId, flowType, organizationUnitId, inputs)
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

    private fun attestationTokenHeaders(token: String?): Map<String, String> = token?.let { mapOf("Attestation-Token" to it) } ?: emptyMap()

    /**
     * The request that starts a flow. `ouId` bounds an `AUTHENTICATION` flow to an organization unit; the
     * server refuses it on any other request, so it is never sent on a continuation.
     */
    internal fun initiateBody(
        applicationId: String,
        flowType: FlowType,
        organizationUnitId: String? = null,
        inputs: Map<String, String>? = null,
    ): Map<String, Any> {
        val body =
            mutableMapOf<String, Any>(
                "applicationId" to applicationId,
                "flowType" to flowType.value,
                "verbose" to true,
            )
        if (organizationUnitId != null && flowType == FlowType.AUTHENTICATION) body["ouId"] = organizationUnitId
        if (inputs != null) body["inputs"] = inputs
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

/** Surfaces the `error` object's text through `failureReason`, which the UI already shows. */
internal fun EmbeddedFlowResponse.withErrorAsFailureReason(): EmbeddedFlowResponse =
    copy(failureReason = error?.message?.defaultValue ?: error?.description?.defaultValue ?: failureReason)
