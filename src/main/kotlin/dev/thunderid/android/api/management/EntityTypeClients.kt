// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.management

import dev.thunderid.android.ThunderIDFetcher

/**
 * User type management operations, reached through `ThunderIDClient.userTypes`, against the `userTypes`
 * collection. Each call accepts an optional [ThunderIDFetcher] that takes precedence over
 * `ThunderIDConfig.http.fetcher`.
 */
class UserTypesClient internal constructor(
    private val transport: ManagementTransport,
) {
    /** Returns a page of user types, each with its resolved display data. */
    suspend fun list(
        limit: Int? = null,
        offset: Int? = null,
        fetcher: ThunderIDFetcher? = null,
    ): ManagedUserTypeListResponse = transport.list(page(limit, offset) + ("include" to "display"), fetcher)

    /** Returns a single user type with its attribute schema. */
    suspend fun get(
        id: String,
        fetcher: ThunderIDFetcher? = null,
    ): ManagedUserTypeDetails = transport.get(id, mapOf("include" to "display"), fetcher)

    /** Creates a user type and returns it as the server stored it. */
    suspend fun create(
        userType: CreateUserTypeRequest,
        fetcher: ThunderIDFetcher? = null,
    ): ManagedUserTypeDetails = transport.create(userType, fetcher)

    /** Replaces a user type's mutable fields and returns the updated user type. */
    suspend fun update(
        id: String,
        userType: UpdateUserTypeRequest,
        fetcher: ThunderIDFetcher? = null,
    ): ManagedUserTypeDetails = transport.update(id, userType, fetcher)

    /** Deletes a user type. */
    suspend fun delete(
        id: String,
        fetcher: ThunderIDFetcher? = null,
    ) = transport.delete(id, fetcher)
}

/**
 * Agent type management operations, reached through `ThunderIDClient.agentTypes`, against the `agentTypes`
 * collection. Each call accepts an optional [ThunderIDFetcher] that takes precedence over
 * `ThunderIDConfig.http.fetcher`.
 */
class AgentTypesClient internal constructor(
    private val transport: ManagementTransport,
) {
    /** Returns a page of agent types, each with its resolved display data. */
    suspend fun list(
        limit: Int? = null,
        offset: Int? = null,
        fetcher: ThunderIDFetcher? = null,
    ): AgentTypeListResponse = transport.list(page(limit, offset) + ("include" to "display"), fetcher)

    /** Returns a single agent type with its attribute schema. */
    suspend fun get(
        id: String,
        fetcher: ThunderIDFetcher? = null,
    ): AgentTypeDetails = transport.get(id, mapOf("include" to "display"), fetcher)

    /**
     * Creates the agent type and returns it as the server stored it. The server keeps a single agent type
     * whose handle must be `default`, so any other handle is refused.
     */
    suspend fun create(
        agentType: CreateAgentTypeRequest,
        fetcher: ThunderIDFetcher? = null,
    ): AgentTypeDetails = transport.create(agentType, fetcher)

    /** Replaces an agent type's mutable fields and returns the updated agent type. */
    suspend fun update(
        id: String,
        agentType: UpdateAgentTypeRequest,
        fetcher: ThunderIDFetcher? = null,
    ): AgentTypeDetails = transport.update(id, agentType, fetcher)

    /**
     * Deletes an agent type. The server does not support deleting the `default` agent type, which agent
     * creation depends on, and refuses the request; the call is here so an application reaches the server's
     * own answer.
     */
    suspend fun delete(
        id: String,
        fetcher: ThunderIDFetcher? = null,
    ) = transport.delete(id, fetcher)
}
