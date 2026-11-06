// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.management

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import dev.thunderid.android.api.management.AdministrationMode
import dev.thunderid.android.api.management.Group
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.i18n.ThunderIDI18n
import dev.thunderid.compose.management.rememberDeleteGroup
import dev.thunderid.compose.management.rememberGetGroup
import kotlinx.coroutines.launch

/** The overview [BaseGroupDetails] shows for a group: its organization unit, by handle when it is known. */
internal fun groupOverview(
    i18n: ThunderIDI18n,
    group: Group,
): List<AdminDetailItem> = listOf(AdminDetailItem(i18n.resolve("admin.common.columns.organization_unit"), group.ouHandle ?: group.ouId))

/**
 * Presents one group: its name, description and overview, with its members underneath. Holds no data of its own.
 *
 * Delete is shown only when [onDelete] is set. It asks for confirmation first.
 *
 * @param error A translated message to show.
 * @param actions Controls rendered next to the group's name.
 * @param members Renders the group's members under the overview, such as a [GroupMembers] or a [BaseGroupMembers].
 *   Shown only once the group is loaded.
 * @param content Renders custom UI in place of the default view, from the group, the loading and error state, and
 *   the delete action.
 */
@Composable
fun BaseGroupDetails(
    group: Group?,
    modifier: Modifier = Modifier,
    error: String? = null,
    isLoading: Boolean = false,
    isDeleting: Boolean = false,
    onDelete: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    members: (@Composable () -> Unit)? = null,
    content: (@Composable (GroupDetailsState) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    if (content != null) {
        Box(modifier = modifier) {
            content(
                GroupDetailsState(
                    group = group,
                    isLoading = isLoading,
                    error = error,
                    isDeleting = isDeleting,
                    onDelete = onDelete.takeIf { group != null },
                ),
            )
        }
        return
    }
    if (group == null) {
        AdminSection(block = "group-details", modifier = modifier) {
            if (isLoading) AdminLoading() else AdminErrorBanner(error ?: i18n.resolve("admin.common.errors.not_found"))
        }
        return
    }

    AdminSection(
        block = "group-details",
        modifier = modifier,
        title = group.name,
        description = group.description,
        actions = {
            actions?.invoke(this)
            if (onDelete != null) {
                AdminConfirmButton(
                    block = "group-details",
                    label = i18n.resolve("admin.groups.details.delete.action"),
                    confirmText = i18n.translate("admin.groups.details.delete.confirm", "name" to group.name),
                    onConfirm = onDelete,
                    isLoading = isDeleting,
                )
            }
        },
    ) {
        error?.let { AdminErrorBanner(it) }
        AdminPanel(title = i18n.resolve("admin.groups.details.overview")) { AdminDetailList(groupOverview(i18n, group)) }
        members?.invoke()
    }
}

/**
 * Shows one group, loaded through the group management operations, with its members.
 *
 * @param editable Lets users and agents be added to the group and members be removed, once the group is loaded.
 *   Off by default.
 * @param deletable Shows a delete action, which asks for confirmation. Off by default.
 * @param administrationMode How the delete and member removals run: [AdministrationMode.FLOW] runs the group
 *   deletion and membership removal flows the server configures, and calls the API directly when none is.
 *   Defaults to [AdministrationMode.NATIVE], which calls the API directly.
 * @param onDeleted Called once the group is deleted, for example to navigate away.
 * @param content Renders custom UI in place of the default view, members included. See [BaseGroupDetails].
 */
@Composable
fun GroupDetails(
    groupId: String,
    modifier: Modifier = Modifier,
    administrationMode: AdministrationMode = AdministrationMode.NATIVE,
    editable: Boolean = false,
    deletable: Boolean = false,
    onDeleted: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: (@Composable (GroupDetailsState) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    val scope = rememberCoroutineScope()
    val group = rememberGetGroup(groupId)
    val deletion = rememberDeleteGroup(administrationMode = administrationMode)
    val loaded = group.data
    BaseGroupDetails(
        group = loaded,
        modifier = modifier,
        error =
            toManagementErrorMessage(i18n, group.error, "admin.groups.details.error")
                ?: toManagementErrorMessage(i18n, deletion.error, "admin.groups.details.delete.error"),
        isLoading = group.isLoading,
        isDeleting = deletion.isLoading,
        onDelete =
            if (deletable) {
                { scope.launch { deletion.mutate(groupId)?.let { onDeleted?.invoke() } } }
            } else {
                null
            },
        actions = actions,
        members = {
            GroupMembers(
                groupId = groupId,
                editable = editable && loaded != null,
                administrationMode = administrationMode,
                ouId = loaded?.ouId,
            )
        },
        content = content,
    )
}
