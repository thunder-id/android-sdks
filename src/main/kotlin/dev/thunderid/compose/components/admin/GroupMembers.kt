// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.admin

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.thunderid.android.management.GroupMember
import dev.thunderid.android.management.GroupMemberInput
import dev.thunderid.android.management.GroupMemberType
import dev.thunderid.android.management.ManagementFilter
import dev.thunderid.android.management.resolveManagedIdentityName
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.management.GroupMembersInput
import dev.thunderid.compose.management.rememberAddGroupMembers
import dev.thunderid.compose.management.rememberGetAgents
import dev.thunderid.compose.management.rememberGetGroupMembers
import dev.thunderid.compose.management.rememberGetUsers
import dev.thunderid.compose.management.rememberRemoveGroupMembers
import kotlinx.coroutines.launch

private val GROUP_MEMBER_TYPE_ORDER = listOf(GroupMemberType.USER, GroupMemberType.AGENT, GroupMemberType.GROUP)

/**
 * Presents the members of a group. Holds no data of its own.
 *
 * Adding is offered only when both [onAdd] and [candidates] are set, for the member types [candidates]
 * holds. Removing is offered only when [onRemove] is set.
 *
 * @param error A translated message to show in place of the members.
 * @param content Renders custom UI in place of the default table and picker, from the members, the candidates,
 *   the loading and error state, and the add and remove actions.
 */
@Composable
fun BaseGroupMembers(
    members: List<GroupMember>,
    modifier: Modifier = Modifier,
    candidates: Map<GroupMemberType, List<IdentityCandidate>>? = null,
    error: String? = null,
    isLoading: Boolean = false,
    isMutating: Boolean = false,
    onAdd: ((GroupMemberInput) -> Unit)? = null,
    onRemove: ((GroupMember) -> Unit)? = null,
    title: String? = null,
    description: String? = null,
    content: (@Composable (GroupMembersState) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    if (content != null) {
        Box(modifier = modifier) {
            content(
                GroupMembersState(
                    members = members,
                    candidates = candidates.takeIf { onAdd != null },
                    isLoading = isLoading,
                    error = error,
                    isMutating = isMutating,
                    onAdd = onAdd.takeIf { candidates != null },
                    onRemove = onRemove,
                ),
            )
        }
        return
    }
    val columns =
        mutableListOf<AdminColumn<GroupMember>>(
            AdminColumn(i18n.resolve("admin.groups.members.columns.member"), 2f) { Text(it.display ?: it.id) },
            AdminColumn(i18n.resolve("admin.groups.members.columns.type")) {
                Text(i18n.resolve("admin.common.identity_types.${it.type.value}"))
            },
        )
    if (onRemove != null) {
        columns +=
            AdminColumn(i18n.resolve("admin.common.columns.actions")) { member ->
                val label = i18n.translate("admin.groups.members.remove_label", "name" to (member.display ?: member.id))
                TextButton(
                    onClick = { onRemove(member) },
                    enabled = !isMutating,
                    modifier =
                        Modifier
                            .semantics { contentDescription = label }
                            .testTag(adminTag("group-members", "remove", member.id)),
                ) { Text(i18n.resolve("admin.common.actions.remove"), color = MaterialTheme.colorScheme.error) }
            }
    }
    AdminSection(
        block = "group-members",
        modifier = modifier,
        title = title ?: i18n.resolve("admin.groups.members.title"),
        description = description,
    ) {
        if (onAdd != null && candidates != null) {
            AdminIdentityPicker(
                block = "group-members",
                addLabel = i18n.resolve("admin.groups.members.add"),
                types =
                    GROUP_MEMBER_TYPE_ORDER
                        .filter { it in candidates }
                        .map { i18n.resolve("admin.common.identity_types.${it.value}") to it },
                candidates = candidates,
                excludedIds = members.map { it.id },
                isSubmitting = isMutating,
                onAdd = { id, type -> onAdd(GroupMemberInput(id, type)) },
            )
        }
        AdminTable(
            block = "group-members",
            columns = columns,
            rows = members,
            rowId = { "${it.type.value}:${it.id}" },
            emptyText = i18n.resolve("admin.groups.members.empty"),
            isLoading = isLoading,
            error = error,
        )
    }
}

/**
 * Shows the members of a group through the group management operations.
 *
 * @param editable Lets users and agents be added to the group and members be removed. Off by default.
 * @param ouId Organization unit the users and agents offered for adding are listed from.
 * @param content Renders custom UI in place of the default table and picker. See [BaseGroupMembers].
 */
@Composable
fun GroupMembers(
    groupId: String,
    modifier: Modifier = Modifier,
    editable: Boolean = false,
    title: String? = null,
    description: String? = null,
    ouId: String? = null,
    content: (@Composable (GroupMembersState) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    val scope = rememberCoroutineScope()
    val members = rememberGetGroupMembers(groupId, limit = 100)
    // A new filter is a new query key, which starts with no data, so no candidate from the previous unit is offered.
    val filter = groupMemberCandidateFilter(ouId)
    val users = rememberGetUsers(limit = 100, filter = filter, enabled = editable)
    val agents = rememberGetAgents(limit = 100, filter = filter, enabled = editable)
    val add = rememberAddGroupMembers()
    val remove = rememberRemoveGroupMembers()
    BaseGroupMembers(
        members = members.data?.members.orEmpty(),
        modifier = modifier,
        candidates =
            if (editable) {
                mapOf(
                    GroupMemberType.USER to
                        users.data
                            ?.users
                            .orEmpty()
                            .map { IdentityCandidate(it.id, resolveManagedIdentityName(it)) },
                    GroupMemberType.AGENT to
                        agents.data
                            ?.agents
                            .orEmpty()
                            .map { IdentityCandidate(it.id, it.name) },
                )
            } else {
                null
            },
        error =
            toManagementErrorMessage(i18n, members.error, "admin.groups.members.error")
                ?: toManagementErrorMessage(i18n, add.error ?: remove.error, "admin.groups.members.update_error"),
        isLoading = members.isLoading,
        isMutating = add.isLoading || remove.isLoading,
        onAdd =
            if (editable) {
                { member -> scope.launch { add.mutate(GroupMembersInput(groupId, listOf(member))) } }
            } else {
                null
            },
        onRemove =
            if (editable) {
                { member ->
                    scope.launch { remove.mutate(GroupMembersInput(groupId, listOf(GroupMemberInput(member.id, member.type)))) }
                }
            } else {
                null
            },
        title = title,
        description = description,
        content = content,
    )
}

/** The filter [GroupMembers] lists its candidates with, `null` to list them from every organization unit. */
internal fun groupMemberCandidateFilter(ouId: String?): String? = ouId?.let { ManagementFilter.equality("ouId", it) }
