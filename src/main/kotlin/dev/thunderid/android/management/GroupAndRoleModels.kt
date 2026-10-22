// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.management

import com.google.gson.annotations.SerializedName

// MARK: - Groups

/** The kinds of identity a group can hold. [value] is the wire value. */
enum class GroupMemberType(
    val value: String,
) {
    @SerializedName("user")
    USER("user"),

    @SerializedName("group")
    GROUP("group"),

    @SerializedName("agent")
    AGENT("agent"),
}

/** A member of a group. [display] is present when the list was requested with `include=display`. */
data class GroupMember(
    val id: String,
    val type: GroupMemberType,
    val display: String? = null,
)

/** A member reference sent when adding or removing group members. */
data class GroupMemberInput(
    val id: String,
    val type: GroupMemberType,
)

/** A group of users, agents and other groups. */
data class Group(
    val id: String,
    val name: String,
    val ouId: String,
    val description: String? = null,
    val ouHandle: String? = null,
    val members: List<GroupMember>? = null,
)

/** A page of groups. */
data class GroupListResponse(
    val totalResults: Int,
    val startIndex: Int,
    val count: Int,
    val groups: List<Group>,
    val links: List<ApiPaginationLink>? = null,
)

/** A page of group members. */
data class GroupMemberListResponse(
    val totalResults: Int,
    val startIndex: Int,
    val count: Int,
    val members: List<GroupMember>,
    val links: List<ApiPaginationLink>? = null,
)

/** The payload used to create a group. */
data class CreateGroupRequest(
    val name: String,
    val ouId: String,
    val description: String? = null,
    val members: List<GroupMemberInput>? = null,
)

/** The payload used to update a group. */
data class UpdateGroupRequest(
    val name: String,
    val ouId: String,
    val description: String? = null,
)

/** A group an identity belongs to, as listed from the identity's side (`/users/{id}/groups`, `/agents/{id}/groups`). */
data class MembershipGroup(
    val id: String,
    val name: String,
    val ouId: String,
)

/** A page of the groups an identity belongs to. */
data class MembershipGroupListResponse(
    val totalResults: Int,
    val startIndex: Int,
    val count: Int,
    val groups: List<MembershipGroup>,
    val links: List<ApiPaginationLink>? = null,
)

// MARK: - Roles

/** The kinds of identity a role can be assigned to. [value] is the wire value. */
enum class RoleAssigneeType(
    val value: String,
) {
    @SerializedName("user")
    USER("user"),

    @SerializedName("group")
    GROUP("group"),

    @SerializedName("app")
    APP("app"),

    @SerializedName("agent")
    AGENT("agent"),
}

/** The permissions a role grants on one resource server. */
data class RoleResourcePermissions(
    val resourceServerId: String,
    val permissions: List<String>,
)

/** An identity a role is assigned to. [display] is present when the list was requested with `include=display`. */
data class RoleAssignment(
    val id: String,
    val type: RoleAssigneeType,
    val display: String? = null,
)

/** An assignee reference sent when adding or removing role assignments. */
data class RoleAssignmentInput(
    val id: String,
    val type: RoleAssigneeType,
)

/** The summary of a role returned in list responses. */
data class RoleSummary(
    val id: String,
    val name: String,
    val ouId: String,
    val description: String? = null,
    val ouHandle: String? = null,
    val isReadOnly: Boolean? = null,
)

/** A role with the permissions it grants. */
data class Role(
    val id: String,
    val name: String,
    val ouId: String,
    val permissions: List<RoleResourcePermissions>,
    val description: String? = null,
    val ouHandle: String? = null,
    val isReadOnly: Boolean? = null,
)

/** A page of roles. */
data class RoleListResponse(
    val totalResults: Int,
    val startIndex: Int,
    val count: Int,
    val roles: List<RoleSummary>,
    val links: List<ApiPaginationLink>? = null,
)

/** A page of role assignments. */
data class RoleAssignmentListResponse(
    val totalResults: Int,
    val startIndex: Int,
    val count: Int,
    val assignments: List<RoleAssignment>,
    val links: List<ApiPaginationLink>? = null,
)

/** The payload used to create a role. */
data class CreateRoleRequest(
    val name: String,
    val ouId: String,
    val description: String? = null,
    val permissions: List<RoleResourcePermissions>? = null,
    val assignments: List<RoleAssignmentInput>? = null,
)

/** The payload used to update a role. */
data class UpdateRoleRequest(
    val name: String,
    val ouId: String,
    val permissions: List<RoleResourcePermissions>,
    val description: String? = null,
)

// MARK: - Agent relations and types

/** A page of the role names assigned to an agent. */
data class AgentRoleListResponse(
    val totalResults: Int,
    val startIndex: Int,
    val count: Int,
    val roles: List<String>,
)

/** A kind of agent, which defines the attributes its agents carry. */
data class AgentType(
    val id: String,
    val handle: String,
    val displayName: String,
    val ouId: String,
    val ouHandle: String? = null,
    val allowSelfRegistration: Boolean? = null,
)

/** A page of agent types. */
data class AgentTypeListResponse(
    val totalResults: Int,
    val startIndex: Int,
    val count: Int,
    val types: List<AgentType>,
)

/** A kind of user, which defines the attributes its users carry. */
data class ManagedUserType(
    val id: String,
    val handle: String,
    val displayName: String? = null,
    val ouId: String? = null,
    val ouHandle: String? = null,
    val allowSelfRegistration: Boolean? = null,
    val isReadOnly: Boolean? = null,
)

/** A page of user types. */
data class ManagedUserTypeListResponse(
    val totalResults: Int,
    val startIndex: Int,
    val count: Int,
    val types: List<ManagedUserType>,
    val links: List<ApiPaginationLink>? = null,
)

// MARK: - Cache keys

/** Cache keys for group resources. [MEMBERSHIPS] covers the groups listed from a user's or agent's side. */
object GroupQueryKeys {
    const val GROUP = "group"
    const val GROUPS = "groups"
    const val GROUP_MEMBERS = "group-members"
    const val MEMBERSHIPS = "group-memberships"
}

/** Cache keys for role resources. [ROLE_ASSIGNMENTS] also covers the roles listed from an agent's side. */
object RoleQueryKeys {
    const val ROLE = "role"
    const val ROLES = "roles"
    const val ROLE_ASSIGNMENTS = "role-assignments"
}
