// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.management

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import dev.thunderid.android.api.management.AdministrationMode
import dev.thunderid.android.api.management.Role
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.i18n.ThunderIDI18n
import dev.thunderid.compose.management.rememberDeleteRole
import dev.thunderid.compose.management.rememberGetRole
import kotlinx.coroutines.launch

/** The overview [BaseRoleDetails] shows for a role: its organization unit, by handle when it is known. */
internal fun roleOverview(
    i18n: ThunderIDI18n,
    role: Role,
): List<AdminDetailItem> = listOf(AdminDetailItem(i18n.resolve("admin.common.columns.organization_unit"), role.ouHandle ?: role.ouId))

/** Whether [role] may be offered for deletion: it is loaded and not read-only. */
internal fun isRoleDeletable(role: Role?): Boolean = role != null && role.isReadOnly != true

/**
 * Presents one role: its name, description and overview, with its permissions and assignments underneath. Holds
 * no data of its own.
 *
 * Delete is shown only when [onDelete] is set, and never for a read-only role. It asks for confirmation first.
 *
 * @param error A translated message to show.
 * @param actions Controls rendered next to the role's name.
 * @param permissions Renders the permissions the role grants under the overview, such as a [RolePermissions] or a
 *   [BaseRolePermissions]. Shown only once the role is loaded.
 * @param assignments Renders who the role is assigned to, under the permissions, such as a [RoleAssignments] or a
 *   [BaseRoleAssignments]. Shown only once the role is loaded.
 * @param content Renders custom UI in place of the default view, from the role, the loading and error state, and
 *   the delete action.
 */
@Composable
fun BaseRoleDetails(
    role: Role?,
    modifier: Modifier = Modifier,
    error: String? = null,
    isLoading: Boolean = false,
    isDeleting: Boolean = false,
    onDelete: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    permissions: (@Composable () -> Unit)? = null,
    assignments: (@Composable () -> Unit)? = null,
    content: (@Composable (RoleDetailsState) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    if (content != null) {
        Box(modifier = modifier) {
            content(
                RoleDetailsState(
                    role = role,
                    isLoading = isLoading,
                    error = error,
                    isDeleting = isDeleting,
                    onDelete = onDelete.takeIf { isRoleDeletable(role) },
                ),
            )
        }
        return
    }
    if (role == null) {
        AdminSection(block = "role-details", modifier = modifier) {
            if (isLoading) AdminLoading() else AdminErrorBanner(error ?: i18n.resolve("admin.common.errors.not_found"))
        }
        return
    }

    AdminSection(
        block = "role-details",
        modifier = modifier,
        title = role.name,
        description = role.description,
        actions = {
            actions?.invoke(this)
            if (onDelete != null && isRoleDeletable(role)) {
                AdminConfirmButton(
                    block = "role-details",
                    label = i18n.resolve("admin.roles.details.delete.action"),
                    confirmText = i18n.translate("admin.roles.details.delete.confirm", "name" to role.name),
                    onConfirm = onDelete,
                    isLoading = isDeleting,
                )
            }
        },
    ) {
        error?.let { AdminErrorBanner(it) }
        AdminPanel(title = i18n.resolve("admin.roles.details.overview")) { AdminDetailList(roleOverview(i18n, role)) }
        permissions?.invoke()
        assignments?.invoke()
    }
}

/**
 * Shows one role, loaded through the role management operations, with the permissions it grants and who it is
 * assigned to.
 *
 * @param editable Lets the role be assigned and unassigned, and its permissions be granted and revoked, once the
 *   role is loaded. Revoking a permission asks for confirmation. Off by default.
 * @param deletable Shows a delete action, which asks for confirmation. Never shown for a read-only role. Off by
 *   default.
 * @param administrationMode How the delete, unassignments and permission revocations run: [AdministrationMode.FLOW]
 *   runs the role deletion, assignment removal and permission removal flows the server configures, and calls the
 *   API directly when none is.
 *   Defaults to [AdministrationMode.NATIVE], which calls the API directly.
 * @param onDeleted Called once the role is deleted, for example to navigate away.
 * @param resourceServerNames Names to show for resource server identifiers in the permissions.
 * @param content Renders custom UI in place of the default view, permissions and assignments included. See
 *   [BaseRoleDetails].
 */
@Composable
fun RoleDetails(
    roleId: String,
    modifier: Modifier = Modifier,
    administrationMode: AdministrationMode = AdministrationMode.NATIVE,
    editable: Boolean = false,
    deletable: Boolean = false,
    onDeleted: (() -> Unit)? = null,
    resourceServerNames: Map<String, String> = emptyMap(),
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: (@Composable (RoleDetailsState) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    val scope = rememberCoroutineScope()
    val role = rememberGetRole(roleId)
    val deletion = rememberDeleteRole(administrationMode = administrationMode)
    val loaded = role.data
    BaseRoleDetails(
        role = loaded,
        modifier = modifier,
        error =
            toManagementErrorMessage(i18n, role.error, "admin.roles.details.error")
                ?: toManagementErrorMessage(i18n, deletion.error, "admin.roles.details.delete.error"),
        isLoading = role.isLoading,
        isDeleting = deletion.isLoading,
        onDelete =
            if (deletable) {
                { scope.launch { deletion.mutate(roleId)?.let { onDeleted?.invoke() } } }
            } else {
                null
            },
        actions = actions,
        permissions = {
            RolePermissions(
                roleId = roleId,
                editable = editable,
                administrationMode = administrationMode,
                resourceServerNames = resourceServerNames,
            )
        },
        assignments = {
            RoleAssignments(
                roleId = roleId,
                editable = editable && loaded != null,
                administrationMode = administrationMode,
                ouId = loaded?.ouId,
            )
        },
        content = content,
    )
}
