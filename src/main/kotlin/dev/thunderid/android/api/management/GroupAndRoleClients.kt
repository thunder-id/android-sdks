// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.management

import com.google.gson.Gson
import dev.thunderid.android.ThunderIDFetcher

/**
 * Group management operations, reached through `ThunderIDClient.groups`.
 * Each call accepts an optional [ThunderIDFetcher] that takes precedence over `ThunderIDConfig.http.fetcher`.
 */
class GroupsClient internal constructor(
    private val transport: ManagementTransport,
) {
    private val administration = AdministrationClient(transport)

    /** Returns a page of groups. [filter] narrows it on the server, for example `ouId eq "<id>"`. */
    suspend fun list(
        limit: Int? = null,
        offset: Int? = null,
        filter: String? = null,
        fetcher: ThunderIDFetcher? = null,
    ): GroupListResponse = transport.list(page(limit, offset) + ("filter" to filter) + ("include" to "display"), fetcher)

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

    /**
     * Deletes a group. With [AdministrationMode.FLOW] it runs the server's
     * [AdministrationFlowConfigKey.GROUP_DELETION] flow instead, when one is configured.
     */
    suspend fun delete(
        id: String,
        fetcher: ThunderIDFetcher? = null,
        administrationMode: AdministrationMode = AdministrationMode.NATIVE,
    ) {
        if (administrationMode == AdministrationMode.FLOW) {
            val inputs = mapOf(AdministrationFlowInput.GROUP to id)
            administration.runFlow(AdministrationFlowConfigKey.GROUP_DELETION, inputs, "group deletion", fetcher)?.let { return }
        }
        transport.delete(id, fetcher)
    }

    /**
     * Returns a page of the groups of the organization unit at the handle [path], such as
     * `engineering/frontend`, each with its organization unit handle. Each handle is percent-encoded and the
     * slashes between them kept.
     */
    suspend fun listByPath(
        path: String,
        limit: Int? = null,
        offset: Int? = null,
        fetcher: ThunderIDFetcher? = null,
    ): GroupListResponse =
        transport.request("GET", listOf("tree") + treePath(path), page(limit, offset) + ("include" to "display"), fetcher = fetcher)

    /**
     * Creates a group in the organization unit at the handle [path] and returns it as the server stored it.
     * Every initial member must be owned by, or shared with, that organization unit.
     */
    suspend fun createByPath(
        path: String,
        group: CreateGroupByPathRequest,
        fetcher: ThunderIDFetcher? = null,
    ): Group = transport.request("POST", listOf("tree") + treePath(path), body = group, fetcher = fetcher)

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

    /**
     * Removes [members] from the group and returns the updated group. With [AdministrationMode.FLOW] it runs
     * the server's [AdministrationFlowConfigKey.GROUP_MEMBERSHIP_REMOVAL] flow once for each member when one
     * is configured, then reads the group back.
     */
    suspend fun removeMembers(
        id: String,
        members: List<GroupMemberInput>,
        fetcher: ThunderIDFetcher? = null,
        administrationMode: AdministrationMode = AdministrationMode.NATIVE,
    ): Group {
        if (administrationMode == AdministrationMode.FLOW) {
            val action = "group membership removal"
            val flowId = administration.resolveFlowId(AdministrationFlowConfigKey.GROUP_MEMBERSHIP_REMOVAL, action, fetcher)
            if (flowId != null) {
                for (member in members) {
                    val inputs = mapOf(AdministrationFlowInput.GROUP to id, AdministrationFlowInput.MEMBER to member.id)
                    administration.executeFlow(flowId, inputs, action, fetcher)
                }
                return get(id, fetcher)
            }
        }
        return transport.postSubResource(id, "members/remove", mapOf("members" to members), fetcher)
    }
}

/**
 * Role management operations, reached through `ThunderIDClient.roles`.
 * Each call accepts an optional [ThunderIDFetcher] that takes precedence over `ThunderIDConfig.http.fetcher`.
 */
class RolesClient internal constructor(
    private val transport: ManagementTransport,
) {
    private val administration = AdministrationClient(transport)

    private val sharing = SharingOperations(transport)

    /** Returns a page of roles. */
    suspend fun list(
        limit: Int? = null,
        offset: Int? = null,
        fetcher: ThunderIDFetcher? = null,
    ): RoleListResponse = transport.list(page(limit, offset) + ("include" to "display"), fetcher)

    /** Returns a single role, with the permissions it grants. */
    suspend fun get(
        id: String,
        fetcher: ThunderIDFetcher? = null,
    ): Role = transport.get(id, mapOf("include" to "display"), fetcher)

    /** Creates a role and returns it as the server stored it. */
    suspend fun create(
        role: CreateRoleRequest,
        fetcher: ThunderIDFetcher? = null,
    ): Role = transport.create(role, fetcher)

    /**
     * Replaces a role's name, description and permissions and returns the updated role.
     *
     * With [AdministrationMode.FLOW], an update that removes a permission runs the server's
     * [AdministrationFlowConfigKey.ROLE_PERMISSION_REMOVAL] flow when one is configured: the other fields are
     * saved first with the current permissions, then the flow writes the new permissions. Pass [current], the
     * role as last read, to save reading it again.
     */
    suspend fun update(
        id: String,
        role: UpdateRoleRequest,
        fetcher: ThunderIDFetcher? = null,
        administrationMode: AdministrationMode = AdministrationMode.NATIVE,
        current: Role? = null,
    ): Role {
        if (administrationMode != AdministrationMode.FLOW) return transport.update(id, role, fetcher)
        val before = current ?: get(id, fetcher)
        if (!removesPermission(before.permissions, role.permissions)) return transport.update(id, role, fetcher)
        val action = "role permission change"
        val flowId =
            administration.resolveFlowId(AdministrationFlowConfigKey.ROLE_PERMISSION_REMOVAL, action, fetcher)
                ?: return transport.update(id, role, fetcher)
        // The flow writes only the permissions, so the other fields go first.
        val otherFieldsChanged =
            role.name != before.name || role.description.orEmpty() != before.description.orEmpty() || role.ouId != before.ouId
        val updated: Role =
            if (otherFieldsChanged) {
                transport.update(id, role.copy(permissions = before.permissions.orEmpty()), fetcher)
            } else {
                before
            }
        val inputs =
            mapOf(
                AdministrationFlowInput.PERMISSIONS to Gson().toJson(role.permissions.orEmpty()),
                AdministrationFlowInput.ROLE to id,
            )
        administration.executeFlow(flowId, inputs, action, fetcher)
        return updated.copy(permissions = role.permissions.orEmpty())
    }

    /**
     * Deletes a role. With [AdministrationMode.FLOW] it runs the server's
     * [AdministrationFlowConfigKey.ROLE_DELETION] flow instead, when one is configured.
     */
    suspend fun delete(
        id: String,
        fetcher: ThunderIDFetcher? = null,
        administrationMode: AdministrationMode = AdministrationMode.NATIVE,
    ) {
        if (administrationMode == AdministrationMode.FLOW) {
            val inputs = mapOf(AdministrationFlowInput.ROLE to id)
            administration.runFlow(AdministrationFlowConfigKey.ROLE_DELETION, inputs, "role deletion", fetcher)?.let { return }
        }
        transport.delete(id, fetcher)
    }

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

    /**
     * Removes the role from [assignments]. With [AdministrationMode.FLOW] it runs the server's
     * [AdministrationFlowConfigKey.ROLE_ASSIGNMENT_REMOVAL] flow once for each assignee when one is configured.
     */
    suspend fun removeAssignments(
        id: String,
        assignments: List<RoleAssignmentInput>,
        fetcher: ThunderIDFetcher? = null,
        administrationMode: AdministrationMode = AdministrationMode.NATIVE,
    ) {
        if (administrationMode == AdministrationMode.FLOW) {
            val action = "role assignment removal"
            val flowId = administration.resolveFlowId(AdministrationFlowConfigKey.ROLE_ASSIGNMENT_REMOVAL, action, fetcher)
            if (flowId != null) {
                for (assignment in assignments) {
                    val inputs = mapOf(AdministrationFlowInput.ASSIGNEE to assignment.id, AdministrationFlowInput.ROLE to id)
                    administration.executeFlow(flowId, inputs, action, fetcher)
                }
                return
            }
        }
        transport.postSubResource<Unit>(id, "assignments/remove", mapOf("assignments" to assignments), fetcher)
    }

    /** Returns a page of the role's sharing policies. */
    suspend fun listSharingPolicies(
        id: String,
        limit: Int? = null,
        offset: Int? = null,
        fetcher: ThunderIDFetcher? = null,
    ): SharingPolicyListResponse = sharing.list(id, limit, offset, fetcher)

    /** Records a sharing policy for the role and returns it. One policy exists per initiating organization unit. */
    suspend fun createSharingPolicy(
        id: String,
        policy: SharingPolicyRequest,
        fetcher: ThunderIDFetcher? = null,
    ): SharingPolicy = sharing.create(id, policy, fetcher)

    /** Returns one of the role's sharing policies. */
    suspend fun getSharingPolicy(
        id: String,
        policyId: String,
        fetcher: ThunderIDFetcher? = null,
    ): SharingPolicy = sharing.get(id, policyId, fetcher)

    /** Replaces a sharing policy's sections. An edit against a stale [SharingPolicyUpdateRequest.version] is refused. */
    suspend fun updateSharingPolicy(
        id: String,
        policyId: String,
        policy: SharingPolicyUpdateRequest,
        fetcher: ThunderIDFetcher? = null,
    ): SharingPolicy = sharing.update(id, policyId, policy, fetcher)

    /** Deletes a sharing policy and the policies that depended on it. */
    suspend fun deleteSharingPolicy(
        id: String,
        policyId: String,
        fetcher: ThunderIDFetcher? = null,
    ) = sharing.delete(id, policyId, fetcher)

    /**
     * Resolves what the organization unit [ouId] may do with the role: whether it owns it, whether it can see
     * it, the policies that reached it, and whether it may assign the role (the `assignments` rule). The server requires [ouId] and answers
     * `404` for a unit no policy reaches.
     */
    suspend fun getOverlayRules(
        id: String,
        ouId: String,
        fetcher: ThunderIDFetcher? = null,
    ): ResolvedOverlay = sharing.overlayRules(id, ouId, fetcher)
}

/** Whether [next] drops any permission [current] grants. */
private fun removesPermission(
    current: List<RoleResourcePermissions>?,
    next: List<RoleResourcePermissions>?,
): Boolean {
    val retained = next.orEmpty().flatMap { resource -> resource.permissions.orEmpty().map { resource.resourceServerId to it } }.toSet()
    return current.orEmpty().any { resource ->
        resource.permissions.orEmpty().any { (resource.resourceServerId to it) !in retained }
    }
}
