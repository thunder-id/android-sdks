// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.management

import dev.thunderid.android.FlowType
import dev.thunderid.android.ThunderIDFetcher

/**
 * Application management operations, reached through `ThunderIDClient.applications`.
 * Each call accepts an optional [ThunderIDFetcher] that takes precedence over `ThunderIDConfig.http.fetcher`.
 */
class ApplicationsClient internal constructor(
    private val transport: ManagementTransport,
) {
    /** Returns a page of applications. */
    suspend fun list(
        limit: Int? = null,
        offset: Int? = null,
        fetcher: ThunderIDFetcher? = null,
    ): ApplicationListResponse = transport.list(mapOf("limit" to limit?.toString(), "offset" to offset?.toString()), fetcher)

    /** Returns a single application. */
    suspend fun get(
        id: String,
        fetcher: ThunderIDFetcher? = null,
    ): Application = transport.get(id, fetcher = fetcher)

    /** Creates an application and returns it as the server stored it. */
    suspend fun create(
        application: ApplicationRequest,
        fetcher: ThunderIDFetcher? = null,
    ): Application = transport.create(application, fetcher)

    /** Replaces an application's mutable fields and returns the updated application. */
    suspend fun update(
        id: String,
        application: ApplicationRequest,
        fetcher: ThunderIDFetcher? = null,
    ): Application = transport.update(id, application, fetcher)

    /** Deletes an application. */
    suspend fun delete(
        id: String,
        fetcher: ThunderIDFetcher? = null,
    ) = transport.delete(id, fetcher)
}

/**
 * User management operations, reached through `ThunderIDClient.users`.
 * Each call accepts an optional [ThunderIDFetcher] that takes precedence over `ThunderIDConfig.http.fetcher`.
 */
class UsersClient internal constructor(
    private val transport: ManagementTransport,
) {
    /** Returns a page of users, each with its resolved `display` value. */
    suspend fun list(
        limit: Int? = null,
        offset: Int? = null,
        filter: String? = null,
        fetcher: ThunderIDFetcher? = null,
    ): ManagedUserListResponse =
        transport.list(
            mapOf(
                "limit" to limit?.toString(),
                "offset" to offset?.toString(),
                "filter" to filter,
                "include" to "display",
            ),
            fetcher,
        )

    /** Returns a single user, with its resolved `display` value. */
    suspend fun get(
        id: String,
        fetcher: ThunderIDFetcher? = null,
    ): ManagedUser = transport.get(id, mapOf("include" to "display"), fetcher)

    /** Creates a user and returns it as the server stored it. */
    suspend fun create(
        user: CreateManagedUserRequest,
        fetcher: ThunderIDFetcher? = null,
    ): ManagedUser = transport.create(user, fetcher)

    /** Updates a user and returns the updated user. */
    suspend fun update(
        id: String,
        user: UpdateManagedUserRequest,
        fetcher: ThunderIDFetcher? = null,
    ): ManagedUser = transport.update(id, user, fetcher)

    /** Deletes a user. */
    suspend fun delete(
        id: String,
        fetcher: ThunderIDFetcher? = null,
    ) = transport.delete(id, fetcher)
}

/**
 * Flow listing, reached through `ThunderIDClient.flows`.
 * Each call accepts an optional [ThunderIDFetcher] that takes precedence over `ThunderIDConfig.http.fetcher`.
 */
class FlowsClient internal constructor(
    private val transport: ManagementTransport,
) {
    /** Returns a page of flows, optionally restricted to one [flowType]. */
    suspend fun list(
        limit: Int? = null,
        offset: Int? = null,
        flowType: FlowType? = null,
        fetcher: ThunderIDFetcher? = null,
    ): ManagedFlowListResponse =
        transport.list(
            mapOf("flowType" to flowType?.value, "limit" to limit?.toString(), "offset" to offset?.toString()),
            fetcher,
        )
}

/**
 * User type listing, reached through `ThunderIDClient.userTypes`.
 * Each call accepts an optional [ThunderIDFetcher] that takes precedence over `ThunderIDConfig.http.fetcher`.
 */
class UserTypesClient internal constructor(
    private val transport: ManagementTransport,
) {
    /** Returns a page of user types. */
    suspend fun list(
        limit: Int? = null,
        offset: Int? = null,
        fetcher: ThunderIDFetcher? = null,
    ): ManagedUserTypeListResponse = transport.list(mapOf("limit" to limit?.toString(), "offset" to offset?.toString()), fetcher)
}

/**
 * Agent management operations, reached through `ThunderIDClient.agents`.
 * Each call accepts an optional [ThunderIDFetcher] that takes precedence over `ThunderIDConfig.http.fetcher`.
 */
class AgentsClient internal constructor(
    private val transport: ManagementTransport,
) {
    /** Returns a page of agents. */
    suspend fun list(
        limit: Int? = null,
        offset: Int? = null,
        fetcher: ThunderIDFetcher? = null,
    ): AgentListResponse =
        transport.list(
            mapOf("limit" to limit?.toString(), "offset" to offset?.toString(), "include" to "display"),
            fetcher,
        )

    /** Returns a single agent. */
    suspend fun get(
        id: String,
        fetcher: ThunderIDFetcher? = null,
    ): Agent = transport.get(id, mapOf("include" to "display"), fetcher)

    /** Creates an agent and returns it as the server stored it. */
    suspend fun create(
        agent: CreateAgentRequest,
        fetcher: ThunderIDFetcher? = null,
    ): Agent = transport.create(agent, fetcher)

    /** Updates an agent and returns the updated agent. */
    suspend fun update(
        id: String,
        agent: UpdateAgentRequest,
        fetcher: ThunderIDFetcher? = null,
    ): Agent = transport.update(id, agent, fetcher)

    /** Deletes an agent. */
    suspend fun delete(
        id: String,
        fetcher: ThunderIDFetcher? = null,
    ) = transport.delete(id, fetcher)
}
