// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.management

/**
 * A user record managed through the ThunderID management API.
 *
 * This is a server record, distinct from `dev.thunderid.android.User`, which describes the signed-in user.
 */
data class ManagedUser(
    val id: String,
    val ouId: String,
    val type: String,
    val ouHandle: String? = null,
    val attributes: Map<String, Any?>? = null,
    val display: String? = null,
    val isReadOnly: Boolean? = null,
)

/** A pagination link returned alongside a list response. */
data class ApiPaginationLink(
    val href: String,
    val rel: String,
)

/** A page of users. */
data class ManagedUserListResponse(
    val totalResults: Int,
    val startIndex: Int,
    val count: Int,
    val users: List<ManagedUser>,
    val links: List<ApiPaginationLink>? = null,
)

/** The payload used to create a user. */
data class CreateManagedUserRequest(
    val ouId: String,
    val type: String,
    val groups: List<String>? = null,
    val attributes: Map<String, Any?>? = null,
)

/** The payload used to update a user. */
data class UpdateManagedUserRequest(
    val ouId: String? = null,
    val type: String? = null,
    val groups: List<String>? = null,
    val attributes: Map<String, Any?>? = null,
)

/** Inbound authentication configuration of an agent. [type] is currently always `oauth2`. */
data class AgentInboundAuthConfig(
    val type: String = "oauth2",
    val config: OAuth2Config? = null,
)

/** Login consent configuration of an agent. */
data class AgentLoginConsentConfig(
    val validityPeriod: Int? = null,
)

/**
 * An agent registered in ThunderID. [authFlowId], [registrationFlowId] and [isRegistrationFlowEnabled]
 * are populated only when the agent has an inbound client.
 */
data class Agent(
    val id: String,
    val ouId: String,
    val type: String,
    val name: String,
    val ouHandle: String? = null,
    val description: String? = null,
    val logoUrl: String? = null,
    val owner: String? = null,
    val clientId: String? = null,
    val attributes: Map<String, Any?>? = null,
    val allowedUserTypes: List<String>? = null,
    val allowedAgentTypes: List<String>? = null,
    val inboundAuthConfig: List<AgentInboundAuthConfig>? = null,
    val authFlowId: String? = null,
    val registrationFlowId: String? = null,
    val isRegistrationFlowEnabled: Boolean? = null,
    val assertion: TokenConfig? = null,
    val loginConsent: AgentLoginConsentConfig? = null,
    val isReadOnly: Boolean? = null,
)

/** The summary of an agent returned in list responses. */
data class BasicAgent(
    val id: String,
    val ouId: String,
    val type: String,
    val name: String,
    val ouHandle: String? = null,
    val description: String? = null,
    val logoUrl: String? = null,
    val clientId: String? = null,
    val isReadOnly: Boolean? = null,
)

/** A page of agents. */
data class AgentListResponse(
    val totalResults: Int,
    val startIndex: Int,
    val count: Int,
    val agents: List<BasicAgent>,
)

/** The payload used to create an agent. */
data class CreateAgentRequest(
    val ouId: String,
    val type: String,
    val name: String,
    val description: String? = null,
    val logoUrl: String? = null,
    val owner: String? = null,
    val attributes: Map<String, Any?>? = null,
    val inboundAuthConfig: List<AgentInboundAuthConfig>? = null,
)

/** The payload used to update an agent. */
data class UpdateAgentRequest(
    val ouId: String? = null,
    val type: String? = null,
    val name: String? = null,
    val description: String? = null,
    val logoUrl: String? = null,
    val owner: String? = null,
    val attributes: Map<String, Any?>? = null,
    val allowedUserTypes: List<String>? = null,
    val allowedAgentTypes: List<String>? = null,
    val inboundAuthConfig: List<AgentInboundAuthConfig>? = null,
    val authFlowId: String? = null,
    val registrationFlowId: String? = null,
    val isRegistrationFlowEnabled: Boolean? = null,
)

/**
 * Cache keys for management resources. The Compose wrappers use them to refetch after a mutation,
 * and an application can reuse them as keys in its own cache.
 */
object ApplicationQueryKeys {
    const val APPLICATION = "application"
    const val APPLICATIONS = "applications"
}

/** Cache keys for user resources. */
object UserQueryKeys {
    const val USER = "user"
    const val USERS = "users"
    const val USER_TYPES = "user-types"
}

/** Cache keys for agent resources. */
object AgentQueryKeys {
    const val AGENT = "agent"
    const val AGENTS = "agents"
    const val AGENT_TYPES = "agent-types"
}
