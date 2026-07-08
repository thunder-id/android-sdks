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
 * Configuration for the ThunderID Android SDK (spec §5.2).
 */
data class ThunderIDConfig(
    // Core
    val baseUrl: String,
    val clientId: String? = null,
    // Redirect URIs
    val afterSignInUrl: String? = null,
    val afterSignOutUrl: String? = null,
    val signInUrl: String? = null,
    val signUpUrl: String? = null,
    // OAuth2 / OIDC
    val scopes: List<String> = listOf("openid"),
    val clientSecret: String? = null,
    val signInOptions: Map<String, Any> = emptyMap(),
    val signOutOptions: Map<String, Any> = emptyMap(),
    val signUpOptions: Map<String, Any> = emptyMap(),
    // Application Identity
    val applicationId: String? = null,
    val organizationHandle: String? = null,
    // Token Validation
    val tokenValidation: TokenValidationConfig = TokenValidationConfig(),
    // Storage & Platform
    val storage: StorageAdapter? = null,
    val instanceId: Int? = null,
    // Development only — bypasses TLS certificate verification. Never use in production.
    val allowInsecureConnections: Boolean = false,
)

data class TokenValidationConfig(
    val validate: Boolean = true,
    val validateIssuer: Boolean = true,
    val clockTolerance: Int = 0,
)
