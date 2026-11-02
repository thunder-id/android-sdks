// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.admin

import dev.thunderid.android.management.AccountLifecycleAction
import dev.thunderid.android.management.Agent
import dev.thunderid.android.management.GroupMember
import dev.thunderid.android.management.GroupMemberInput
import dev.thunderid.android.management.GroupMemberType
import dev.thunderid.android.management.ManagedUser
import dev.thunderid.android.management.MembershipGroup
import dev.thunderid.android.management.RoleAssigneeType
import dev.thunderid.android.management.RoleAssignment
import dev.thunderid.android.management.RoleAssignmentInput
import dev.thunderid.android.management.RoleResourcePermissions

/** A search an admin list applies on the server: the attribute to match and the value it must equal. */
data class AdminSearch(
    val attribute: String,
    val value: String,
)

/**
 * What an admin list hands its `content` lambda, so the rows can be shown with any UI.
 *
 * @property totalResults The total on the server, or the number of rows given when it is unknown.
 * @property onPageChange Shows the page that starts at the given offset. `null` when the list is not paginated.
 * @property onSelect Chooses a row. `null` when rows are not selectable.
 * @property search The search currently applied, if any.
 * @property searchAttributes The attributes that can be searched, with translated labels.
 * @property onSearch Applies a search on the server, or clears it with `null`. `null` when the list cannot be searched.
 */
data class AdminListState<T>(
    val rows: List<T>,
    val isLoading: Boolean,
    val error: String?,
    val limit: Int,
    val offset: Int,
    val totalResults: Int,
    val selectedId: String?,
    val onPageChange: ((Int) -> Unit)?,
    val onSelect: ((T) -> Unit)?,
    val search: AdminSearch? = null,
    val searchAttributes: List<AdminOption> = emptyList(),
    val onSearch: ((AdminSearch?) -> Unit)? = null,
)

/**
 * What [BaseGroupCreate] and [BaseRoleCreate] hand their `content` lambda.
 *
 * @property submit Submits the current values. Does nothing while they are not valid or a submission is in flight.
 */
data class NamedResourceFormState(
    val name: String,
    val onNameChange: (String) -> Unit,
    val description: String,
    val onDescriptionChange: (String) -> Unit,
    val isValid: Boolean,
    val isSubmitting: Boolean,
    val error: String?,
    val submit: () -> Unit,
    val onCancel: (() -> Unit)?,
)

/**
 * What [BaseUserCreate] hands its `content` lambda.
 *
 * @property type The chosen user type. The first one is chosen until another is set.
 * @property values The attribute values, keyed by attribute name.
 * @property onValueChange Sets one attribute's value.
 * @property isValid Whether a type is chosen and every required attribute has a value.
 * @property submit Submits the chosen type and the non-blank attributes. Does nothing while they are not valid.
 */
data class UserCreateFormState(
    val userTypes: List<AdminOption>,
    val fields: List<UserCreateField>,
    val type: String,
    val onTypeChange: (String) -> Unit,
    val values: Map<String, String>,
    val onValueChange: (name: String, value: String) -> Unit,
    val isValid: Boolean,
    val isSubmitting: Boolean,
    val error: String?,
    val submit: () -> Unit,
    val onCancel: (() -> Unit)?,
)

/**
 * What [BaseAgentCreate] hands its `content` lambda.
 *
 * @property type The chosen agent type. The first one is chosen until another is set.
 * @property submit Submits the current values. Does nothing while they are not valid or a submission is in flight.
 */
data class AgentCreateFormState(
    val agentTypes: List<AdminOption>,
    val name: String,
    val onNameChange: (String) -> Unit,
    val type: String,
    val onTypeChange: (String) -> Unit,
    val description: String,
    val onDescriptionChange: (String) -> Unit,
    val isValid: Boolean,
    val isSubmitting: Boolean,
    val error: String?,
    val submit: () -> Unit,
    val onCancel: (() -> Unit)?,
)

/**
 * What [BaseUserDetails] hands its `content` lambda.
 *
 * @property displayName The user's display name, or an empty string while there is no user.
 * @property attributes The user's attributes, without the hidden ones.
 * @property onDelete Deletes the user. `null` when deletion is not allowed, including for read-only users.
 * @property onUpdate Saves the given attributes. `null` when editing is not allowed, including for read-only users.
 * @property lifecycleActions The account lifecycle actions offered for the user. Empty when none is allowed.
 * @property onLifecycleAction Runs an account lifecycle action, with the operator's note on a suspension. `null`
 *   when none is allowed. Ask for confirmation before suspending or unsuspending.
 */
data class UserDetailsState(
    val user: ManagedUser?,
    val displayName: String,
    val attributes: Map<String, Any?>,
    val groups: List<MembershipGroup>,
    val isLoading: Boolean,
    val error: String?,
    val isDeleting: Boolean,
    val isUpdating: Boolean,
    val onDelete: (() -> Unit)?,
    val onUpdate: ((Map<String, Any?>) -> Unit)?,
    val lifecycleActions: List<AccountLifecycleAction> = emptyList(),
    val isRunningLifecycleAction: Boolean = false,
    val onLifecycleAction: AccountLifecycleHandler? = null,
)

/**
 * What [BaseAgentDetails] hands its `content` lambda.
 *
 * @property onDelete Deletes the agent. `null` when deletion is not allowed, including for read-only agents.
 * @property lifecycleActions The account lifecycle actions offered for the agent. Empty when none is allowed.
 * @property onLifecycleAction Runs an account lifecycle action, with the operator's note on a suspension. `null`
 *   when none is allowed. Ask for confirmation before suspending or unsuspending.
 */
data class AgentDetailsState(
    val agent: Agent?,
    val groups: List<MembershipGroup>,
    val roles: List<String>,
    val isLoading: Boolean,
    val error: String?,
    val isDeleting: Boolean,
    val onDelete: (() -> Unit)?,
    val lifecycleActions: List<AccountLifecycleAction> = emptyList(),
    val isRunningLifecycleAction: Boolean = false,
    val onLifecycleAction: AccountLifecycleHandler? = null,
)

/**
 * What [BaseGroupMembers] hands its `content` lambda.
 *
 * @property candidates The identities that can be added, by member type. `null` when adding is not allowed.
 * @property onAdd Adds a member. `null` when adding is not allowed.
 * @property onRemove Removes a member. `null` when removing is not allowed.
 */
data class GroupMembersState(
    val members: List<GroupMember>,
    val candidates: Map<GroupMemberType, List<IdentityCandidate>>?,
    val isLoading: Boolean,
    val error: String?,
    val isMutating: Boolean,
    val onAdd: ((GroupMemberInput) -> Unit)?,
    val onRemove: ((GroupMember) -> Unit)?,
)

/**
 * What [BaseRoleAssignments] hands its `content` lambda.
 *
 * @property candidates The identities the role can be assigned to, by type. `null` when assigning is not allowed.
 * @property onAssign Assigns the role. `null` when assigning is not allowed.
 * @property onUnassign Removes an assignment. `null` when unassigning is not allowed.
 * @property typeFilter The assignee type the list is narrowed to, or `null` for every type.
 * @property onTypeFilterChange Narrows the list to one assignee type on the server, or back to every type with
 *   `null`. `null` when the list cannot be filtered.
 */
data class RoleAssignmentsState(
    val assignments: List<RoleAssignment>,
    val candidates: Map<RoleAssigneeType, List<IdentityCandidate>>?,
    val isLoading: Boolean,
    val error: String?,
    val isMutating: Boolean,
    val onAssign: ((RoleAssignmentInput) -> Unit)?,
    val onUnassign: ((RoleAssignment) -> Unit)?,
    val typeFilter: RoleAssigneeType? = null,
    val onTypeFilterChange: ((RoleAssigneeType?) -> Unit)? = null,
)

/**
 * What [BaseRolePermissions] hands its `content` lambda.
 *
 * @property permissions The resource servers the role grants at least one permission on, with those permissions.
 */
data class RolePermissionsState(
    val permissions: List<RoleResourcePermissions>,
    val isLoading: Boolean,
    val error: String?,
    private val resourceServerNames: Map<String, String>,
) {
    /** A resource server's display name, or its identifier when the name is missing. */
    fun resourceServerName(resourceServerId: String): String = resourceServerNames[resourceServerId] ?: resourceServerId
}
