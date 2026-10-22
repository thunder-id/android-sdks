// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.management

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
    private val typesTransport: ManagementTransport,
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

    /** Returns a page of the groups the user belongs to. */
    suspend fun listGroups(
        id: String,
        limit: Int? = null,
        offset: Int? = null,
        fetcher: ThunderIDFetcher? = null,
    ): MembershipGroupListResponse = transport.getSubResource(id, "groups", page(limit, offset), fetcher)

    /** Returns a page of user types, from the `userTypes` collection. */
    suspend fun listTypes(
        limit: Int? = null,
        offset: Int? = null,
        fetcher: ThunderIDFetcher? = null,
    ): ManagedUserTypeListResponse = typesTransport.list(page(limit, offset), fetcher)
}

/**
 * Agent management operations, reached through `ThunderIDClient.agents`.
 * Each call accepts an optional [ThunderIDFetcher] that takes precedence over `ThunderIDConfig.http.fetcher`.
 */
class AgentsClient internal constructor(
    private val transport: ManagementTransport,
    private val typesTransport: ManagementTransport,
) {
    /**
     * Returns a page of agents.
     *
     * @param filter A server-side filter on a custom agent attribute, such as `name eq "Calendar Agent"`.
     *   [ManagementFilter.equality] builds one.
     */
    suspend fun list(
        limit: Int? = null,
        offset: Int? = null,
        filter: String? = null,
        fetcher: ThunderIDFetcher? = null,
    ): AgentListResponse =
        transport.list(
            mapOf(
                "limit" to limit?.toString(),
                "offset" to offset?.toString(),
                "filter" to filter,
                "include" to "display",
            ),
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

    /** Returns a page of the groups the agent belongs to. */
    suspend fun listGroups(
        id: String,
        limit: Int? = null,
        offset: Int? = null,
        fetcher: ThunderIDFetcher? = null,
    ): MembershipGroupListResponse = transport.getSubResource(id, "groups", page(limit, offset), fetcher)

    /** Returns a page of the names of the roles assigned to the agent. */
    suspend fun listRoles(
        id: String,
        limit: Int? = null,
        offset: Int? = null,
        fetcher: ThunderIDFetcher? = null,
    ): AgentRoleListResponse = transport.getSubResource(id, "roles", page(limit, offset), fetcher)

    /** Returns a page of agent types, from the `agentTypes` collection. */
    suspend fun listTypes(
        limit: Int? = null,
        offset: Int? = null,
        fetcher: ThunderIDFetcher? = null,
    ): AgentTypeListResponse = typesTransport.list(page(limit, offset), fetcher)
}

/**
 * Group management operations, reached through `ThunderIDClient.groups`.
 * Each call accepts an optional [ThunderIDFetcher] that takes precedence over `ThunderIDConfig.http.fetcher`.
 */
class GroupsClient internal constructor(
    private val transport: ManagementTransport,
) {
    /** Returns a page of groups. */
    suspend fun list(
        limit: Int? = null,
        offset: Int? = null,
        fetcher: ThunderIDFetcher? = null,
    ): GroupListResponse = transport.list(page(limit, offset) + ("include" to "display"), fetcher)

    /** Returns a single group. */
    suspend fun get(
        id: String,
        fetcher: ThunderIDFetcher? = null,
    ): Group = transport.get(id, mapOf("include" to "display"), fetcher)

    /** Creates a group and returns it as the server stored it. */
    suspend fun create(
        group: CreateGroupRequest,
        fetcher: ThunderIDFetcher? = null,
    ): Group = transport.create(group, fetcher)

    /** Replaces a group's name and description and returns the updated group. Members are left unchanged. */
    suspend fun update(
        id: String,
        group: UpdateGroupRequest,
        fetcher: ThunderIDFetcher? = null,
    ): Group = transport.update(id, group, fetcher)

    /** Deletes a group. */
    suspend fun delete(
        id: String,
        fetcher: ThunderIDFetcher? = null,
    ) = transport.delete(id, fetcher)

    /** Returns a page of the group's members, each with its resolved `display` value. */
    suspend fun listMembers(
        id: String,
        limit: Int? = null,
        offset: Int? = null,
        fetcher: ThunderIDFetcher? = null,
    ): GroupMemberListResponse = transport.getSubResource(id, "members", mapOf("include" to "display") + page(limit, offset), fetcher)

    /** Adds [members] to the group and returns the updated group. */
    suspend fun addMembers(
        id: String,
        members: List<GroupMemberInput>,
        fetcher: ThunderIDFetcher? = null,
    ): Group = transport.postSubResource(id, "members/add", mapOf("members" to members), fetcher)

    /** Removes [members] from the group and returns the updated group. */
    suspend fun removeMembers(
        id: String,
        members: List<GroupMemberInput>,
        fetcher: ThunderIDFetcher? = null,
    ): Group = transport.postSubResource(id, "members/remove", mapOf("members" to members), fetcher)
}

/**
 * Role management operations, reached through `ThunderIDClient.roles`.
 * Each call accepts an optional [ThunderIDFetcher] that takes precedence over `ThunderIDConfig.http.fetcher`.
 */
class RolesClient internal constructor(
    private val transport: ManagementTransport,
) {
    /** Returns a page of roles. */
    suspend fun list(
        limit: Int? = null,
        offset: Int? = null,
        fetcher: ThunderIDFetcher? = null,
    ): RoleListResponse = transport.list(page(limit, offset), fetcher)

    /** Returns a single role, with the permissions it grants. */
    suspend fun get(
        id: String,
        fetcher: ThunderIDFetcher? = null,
    ): Role = transport.get(id, fetcher = fetcher)

    /** Creates a role and returns it as the server stored it. */
    suspend fun create(
        role: CreateRoleRequest,
        fetcher: ThunderIDFetcher? = null,
    ): Role = transport.create(role, fetcher)

    /** Replaces a role's name, description and permissions and returns the updated role. */
    suspend fun update(
        id: String,
        role: UpdateRoleRequest,
        fetcher: ThunderIDFetcher? = null,
    ): Role = transport.update(id, role, fetcher)

    /** Deletes a role. */
    suspend fun delete(
        id: String,
        fetcher: ThunderIDFetcher? = null,
    ) = transport.delete(id, fetcher)

    /**
     * Returns a page of the role's assignees, each with its resolved `display` value. Set [type] to list
     * only assignees of that kind.
     */
    suspend fun listAssignments(
        id: String,
        limit: Int? = null,
        offset: Int? = null,
        type: RoleAssigneeType? = null,
        fetcher: ThunderIDFetcher? = null,
    ): RoleAssignmentListResponse =
        transport.getSubResource(
            id,
            "assignments",
            mapOf("include" to "display") + page(limit, offset) + ("type" to type?.value),
            fetcher,
        )

    /** Assigns the role to [assignments]. */
    suspend fun addAssignments(
        id: String,
        assignments: List<RoleAssignmentInput>,
        fetcher: ThunderIDFetcher? = null,
    ) {
        transport.postSubResource<Unit>(id, "assignments/add", mapOf("assignments" to assignments), fetcher)
    }

    /** Removes the role from [assignments]. */
    suspend fun removeAssignments(
        id: String,
        assignments: List<RoleAssignmentInput>,
        fetcher: ThunderIDFetcher? = null,
    ) {
        transport.postSubResource<Unit>(id, "assignments/remove", mapOf("assignments" to assignments), fetcher)
    }
}

private fun page(
    limit: Int?,
    offset: Int?,
): Map<String, String?> = mapOf("limit" to limit?.toString(), "offset" to offset?.toString())
