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
    ): EmbeddedFlowResponse {
        val body =
            mapOf(
                "applicationId" to applicationId,
                "flowType" to flowType.value,
                "verbose" to true,
            )
        return httpClient.post("/flow/execute", body, requiresAuth = false)
    }

    suspend fun submit(
        flowId: String,
        actionId: String,
        inputs: Map<String, String>,
        challengeToken: String?,
    ): EmbeddedFlowResponse {
        val body = submitBody(flowId, actionId, challengeToken).toMutableMap()
        body["verbose"] = true
        if (inputs.isNotEmpty()) body["inputs"] = inputs
        return httpClient.post("/flow/execute", body, requiresAuth = false)
    }

    internal fun submitBody(
        flowId: String,
        actionId: String,
        challengeToken: String?,
    ): Map<String, Any> {
        val body = mutableMapOf<String, Any>("executionId" to flowId, "action" to actionId)
        if (challengeToken != null) body["challengeToken"] = challengeToken
        return body
    }
}
