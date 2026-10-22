// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.management

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.thunderid.android.ThunderIDFetcher
import dev.thunderid.android.management.CreateGroupRequest
import dev.thunderid.android.management.CreateRoleRequest
import dev.thunderid.android.management.Group
import dev.thunderid.android.management.GroupListResponse
import dev.thunderid.android.management.GroupMemberInput
import dev.thunderid.android.management.GroupMemberListResponse
import dev.thunderid.android.management.GroupQueryKeys
import dev.thunderid.android.management.Role
import dev.thunderid.android.management.RoleAssigneeType
import dev.thunderid.android.management.RoleAssignmentInput
import dev.thunderid.android.management.RoleAssignmentListResponse
import dev.thunderid.android.management.RoleListResponse
import dev.thunderid.android.management.RoleQueryKeys
import dev.thunderid.android.management.UpdateGroupRequest
import dev.thunderid.android.management.UpdateRoleRequest
import dev.thunderid.android.management.hasPermission
import dev.thunderid.compose.LocalThunderID

/** The input of a group member mutation: the group, and the members to add or remove. */
data class GroupMembersInput(
    val groupId: String,
    val members: List<GroupMemberInput>,
)

/** The input of a role assignment mutation: the role, and the assignees to add or remove. */
data class RoleAssignmentsInput(
    val roleId: String,
    val assignments: List<RoleAssignmentInput>,
)

private fun pageKey(
    limit: Int?,
    offset: Int?,
): List<String> = listOf("limit=${limit ?: ""}", "offset=${offset ?: ""}")

// MARK: - Groups

@Composable
fun rememberGetGroups(
    limit: Int? = null,
    offset: Int? = null,
    fetcher: ThunderIDFetcher? = null,
    enabled: Boolean = true,
): ResourceQueryState<GroupListResponse> =
    rememberResourceQuery(listOf(GroupQueryKeys.GROUPS) + pageKey(limit, offset), enabled) {
        it.groups.list(limit, offset, fetcher)
    }

@Composable
fun rememberGetGroup(
    id: String?,
    fetcher: ThunderIDFetcher? = null,
): ResourceQueryState<Group> =
    rememberResourceQuery(listOf(GroupQueryKeys.GROUP, id.orEmpty()), enabled = id != null) {
        it.groups.get(id!!, fetcher)
    }

@Composable
fun rememberGetGroupMembers(
    id: String?,
    limit: Int? = null,
    offset: Int? = null,
    fetcher: ThunderIDFetcher? = null,
): ResourceQueryState<GroupMemberListResponse> =
    rememberResourceQuery(listOf(GroupQueryKeys.GROUP_MEMBERS, id.orEmpty()) + pageKey(limit, offset), id != null) {
        it.groups.listMembers(id!!, limit, offset, fetcher)
    }

@Composable
fun rememberCreateGroup(fetcher: ThunderIDFetcher? = null): ResourceMutationState<CreateGroupRequest, Group> =
    rememberResourceMutation({ listOf(listOf(GroupQueryKeys.GROUPS), listOf(GroupQueryKeys.MEMBERSHIPS)) }) { client, request ->
        client.groups.create(request, fetcher)
    }

@Composable
fun rememberUpdateGroup(fetcher: ThunderIDFetcher? = null): ResourceMutationState<Pair<String, UpdateGroupRequest>, Group> =
    rememberResourceMutation({ (id, _) ->
        listOf(listOf(GroupQueryKeys.GROUP, id), listOf(GroupQueryKeys.GROUPS), listOf(GroupQueryKeys.MEMBERSHIPS))
    }) { client, (id, request) -> client.groups.update(id, request, fetcher) }

@Composable
fun rememberDeleteGroup(fetcher: ThunderIDFetcher? = null): ResourceMutationState<String, Unit> =
    rememberResourceMutation({
        listOf(listOf(GroupQueryKeys.GROUPS), listOf(GroupQueryKeys.MEMBERSHIPS), listOf(RoleQueryKeys.ROLE_ASSIGNMENTS))
    }) { client, id -> client.groups.delete(id, fetcher) }

internal fun groupMembershipKeys(groupId: String): List<List<String>> =
    listOf(
        listOf(GroupQueryKeys.GROUP, groupId),
        listOf(GroupQueryKeys.GROUP_MEMBERS, groupId),
        listOf(GroupQueryKeys.MEMBERSHIPS),
        listOf(RoleQueryKeys.ROLE_ASSIGNMENTS),
    )

@Composable
fun rememberAddGroupMembers(fetcher: ThunderIDFetcher? = null): ResourceMutationState<GroupMembersInput, Group> =
    rememberResourceMutation({ groupMembershipKeys(it.groupId) }) { client, input ->
        client.groups.addMembers(input.groupId, input.members, fetcher)
    }

@Composable
fun rememberRemoveGroupMembers(fetcher: ThunderIDFetcher? = null): ResourceMutationState<GroupMembersInput, Group> =
    rememberResourceMutation({ groupMembershipKeys(it.groupId) }) { client, input ->
        client.groups.removeMembers(input.groupId, input.members, fetcher)
    }

// MARK: - Roles

@Composable
fun rememberGetRoles(
    limit: Int? = null,
    offset: Int? = null,
    fetcher: ThunderIDFetcher? = null,
    enabled: Boolean = true,
): ResourceQueryState<RoleListResponse> =
    rememberResourceQuery(listOf(RoleQueryKeys.ROLES) + pageKey(limit, offset), enabled) {
        it.roles.list(limit, offset, fetcher)
    }

@Composable
fun rememberGetRole(
    id: String?,
    fetcher: ThunderIDFetcher? = null,
): ResourceQueryState<Role> =
    rememberResourceQuery(listOf(RoleQueryKeys.ROLE, id.orEmpty()), enabled = id != null) {
        it.roles.get(id!!, fetcher)
    }

@Composable
fun rememberGetRoleAssignments(
    id: String?,
    limit: Int? = null,
    offset: Int? = null,
    type: RoleAssigneeType? = null,
    fetcher: ThunderIDFetcher? = null,
): ResourceQueryState<RoleAssignmentListResponse> =
    rememberResourceQuery(
        listOf(RoleQueryKeys.ROLE_ASSIGNMENTS, id.orEmpty()) + pageKey(limit, offset) + "type=${type?.value.orEmpty()}",
        enabled = id != null,
    ) { it.roles.listAssignments(id!!, limit, offset, type, fetcher) }

@Composable
fun rememberCreateRole(fetcher: ThunderIDFetcher? = null): ResourceMutationState<CreateRoleRequest, Role> =
    rememberResourceMutation({ listOf(listOf(RoleQueryKeys.ROLES), listOf(RoleQueryKeys.ROLE_ASSIGNMENTS)) }) { client, request ->
        client.roles.create(request, fetcher)
    }

@Composable
fun rememberUpdateRole(fetcher: ThunderIDFetcher? = null): ResourceMutationState<Pair<String, UpdateRoleRequest>, Role> =
    rememberResourceMutation({ (id, _) ->
        listOf(listOf(RoleQueryKeys.ROLE, id), listOf(RoleQueryKeys.ROLES))
    }) { client, (id, request) -> client.roles.update(id, request, fetcher) }

@Composable
fun rememberDeleteRole(fetcher: ThunderIDFetcher? = null): ResourceMutationState<String, Unit> =
    rememberResourceMutation({ listOf(listOf(RoleQueryKeys.ROLES), listOf(RoleQueryKeys.ROLE_ASSIGNMENTS)) }) { client, id ->
        client.roles.delete(id, fetcher)
    }

@Composable
fun rememberAddRoleAssignments(fetcher: ThunderIDFetcher? = null): ResourceMutationState<RoleAssignmentsInput, Unit> =
    rememberResourceMutation({ listOf(listOf(RoleQueryKeys.ROLE_ASSIGNMENTS)) }) { client, input ->
        client.roles.addAssignments(input.roleId, input.assignments, fetcher)
    }

@Composable
fun rememberRemoveRoleAssignments(fetcher: ThunderIDFetcher? = null): ResourceMutationState<RoleAssignmentsInput, Unit> =
    rememberResourceMutation({ listOf(listOf(RoleQueryKeys.ROLE_ASSIGNMENTS)) }) { client, input ->
        client.roles.removeAssignments(input.roleId, input.assignments, fetcher)
    }

// MARK: - Permissions

/**
 * The permissions the signed-in user's access token carries. [can] tests one with the server's hierarchy,
 * where `system` satisfies `system:user:view`. The result only decides what to show: the server still
 * decides every request.
 */
@Stable
class GrantedPermissionsState internal constructor() {
    /** The granted permissions. Empty while loading and when nobody is signed in. */
    var permissions by mutableStateOf<List<String>>(emptyList())
        internal set

    /** Whether the token is still being read. */
    var isLoading by mutableStateOf(true)
        internal set

    /** Whether the granted permissions satisfy [required]. */
    fun can(required: String): Boolean = hasPermission(permissions, required)
}

/** Reads the signed-in user's granted permissions, and reads them again when the signed-in user changes. */
@Composable
fun rememberGrantedPermissions(): GrantedPermissionsState {
    val state = LocalThunderID.current
    val granted = remember { GrantedPermissionsState() }
    val user = state.user
    LaunchedEffect(user) {
        if (user == null) {
            granted.permissions = emptyList()
            granted.isLoading = false
        } else {
            granted.isLoading = true
            granted.permissions = state.client.getGrantedPermissions()
            granted.isLoading = false
        }
    }
    return granted
}
