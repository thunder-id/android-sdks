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

import com.google.gson.annotations.SerializedName

data class User(
    val sub: String,
    val username: String? = null,
    val email: String? = null,
    val displayName: String? = null,
    @SerializedName("picture") val profilePicture: String? = null,
    val isNewUser: Boolean? = null,
    val claims: Map<String, Any>? = null,
)

data class UserProfile(
    val id: String,
    val claims: Map<String, Any> = emptyMap(),
)

data class TokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("expires_in") val expiresIn: Int? = null,
    @SerializedName("refresh_token") val refreshToken: String? = null,
    @SerializedName("id_token") val idToken: String? = null,
    val scope: String? = null,
)

data class SignInOptions(
    val prompt: String? = null,
    val loginHint: String? = null,
    val fidp: String? = null,
    val extra: Map<String, Any> = emptyMap(),
)

data class SignUpOptions(
    val appId: String? = null,
    val extra: Map<String, Any> = emptyMap(),
)

data class SignOutOptions(
    val idTokenHint: String? = null,
    val extra: Map<String, Any> = emptyMap(),
)

data class TokenExchangeRequestConfig(
    val subjectToken: String,
    val subjectTokenType: String,
    val requestedTokenType: String? = null,
    val audience: String? = null,
)

data class EmbeddedSignInPayload(
    val flowId: String? = null,
    val actionId: String,
    val inputs: Map<String, String> = emptyMap(),
    val challengeToken: String? = null,
)

data class EmbeddedFlowRequestConfig(
    val applicationId: String,
    val flowType: FlowType = FlowType.AUTHENTICATION,
)

enum class FlowType(val value: String) {
    AUTHENTICATION("AUTHENTICATION"),
    REGISTRATION("REGISTRATION"),
    PASSWORD_RECOVERY("PASSWORD_RECOVERY"),
    INVITED_USER_REGISTRATION("INVITED_USER_REGISTRATION"),
}

data class EmbeddedFlowResponse(
    @SerializedName("executionId") val flowId: String? = null,
    val flowStatus: FlowStatus,
    val stepId: String? = null,
    val type: String? = null,
    val data: FlowStepData? = null,
    val assertion: String? = null,
    val failureReason: String? = null,
    val challengeToken: String? = null,
)

enum class FlowStatus { PROMPT_ONLY, INCOMPLETE, COMPLETE, ERROR }

data class FlowStepData(
    val actions: List<FlowAction>? = null,
    val inputs: List<FlowInput>? = null,
    val meta: FlowMeta? = null,
)

data class FlowAction(
    val id: String? = null,
    val ref: String? = null,
    val nextNode: String? = null,
    val type: String? = null,
    val label: String? = null,
    val eventType: String? = null,
    val variant: String? = null,
    @SerializedName("image") val icon: String? = null,
)

data class FlowInput(
    @SerializedName("identifier") val name: String,
    val type: String? = null,
    val required: Boolean? = null,
)

data class FlowMeta(
    val components: List<FlowComponent>? = null,
)

data class FlowComponent(
    val id: String? = null,
    val ref: String? = null,
    val type: String? = null,
    val category: String? = null,
    val label: String? = null,
    val placeholder: String? = null,
    val variant: String? = null,
    val eventType: String? = null,
    val align: String? = null,
    @SerializedName("image") val icon: String? = null,
    val components: List<FlowComponent>? = null,
)
