// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.management

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.thunderid.android.ThunderIDClient
import dev.thunderid.android.api.management.AdministrationMode
import dev.thunderid.android.api.management.Role
import dev.thunderid.android.api.management.RoleQueryKeys
import dev.thunderid.android.api.management.RoleResourcePermissions
import dev.thunderid.android.api.management.UpdateRoleRequest
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.management.rememberGetRole
import dev.thunderid.compose.management.rememberResourceMutation
import dev.thunderid.compose.management.rememberResourceQuery
import kotlinx.coroutines.launch

private const val BLOCK = "role-permissions"

/** How many resource servers, and actions of each, [RolePermissions] offers for granting. */
private const val CANDIDATE_LIMIT = 100

/**
 * A resource server whose permissions a role can be granted, with the permissions its actions define.
 *
 * @property label The resource server's display name.
 */
data class PermissionCandidate(
    val resourceServerId: String,
    val label: String,
    val permissions: List<String>,
)

/** The entries that grant at least one permission. */
internal fun grantedPermissions(permissions: List<RoleResourcePermissions>): List<RoleResourcePermissions> =
    permissions.filter { it.permissions.isNotEmpty() }

/** [permissions] with [permission] of [resourceServerId] granted, once. */
internal fun withPermission(
    permissions: List<RoleResourcePermissions>,
    resourceServerId: String,
    permission: String,
): List<RoleResourcePermissions> {
    if (permissions.none { it.resourceServerId == resourceServerId }) {
        return permissions + RoleResourcePermissions(resourceServerId, listOf(permission))
    }
    return permissions.map { entry ->
        if (entry.resourceServerId != resourceServerId || permission in entry.permissions) {
            entry
        } else {
            entry.copy(permissions = entry.permissions + permission)
        }
    }
}

/** [permissions] without [permission] of [resourceServerId], leaving out a resource server it emptied. */
internal fun withoutPermission(
    permissions: List<RoleResourcePermissions>,
    resourceServerId: String,
    permission: String,
): List<RoleResourcePermissions> =
    permissions
        .map { entry ->
            if (entry.resourceServerId == resourceServerId) entry.copy(permissions = entry.permissions - permission) else entry
        }.filter { it.permissions.isNotEmpty() }

/** The permissions of [resourceServerId] in [candidates] that [granted] does not already grant. */
internal fun addablePermissions(
    candidates: List<PermissionCandidate>,
    granted: List<RoleResourcePermissions>,
    resourceServerId: String,
): List<String> {
    val held = granted.filter { it.resourceServerId == resourceServerId }.flatMap { it.permissions }.toSet()
    return candidates
        .filter { it.resourceServerId == resourceServerId }
        .flatMap { it.permissions }
        .distinct()
        .filter { it !in held }
}

/**
 * What [BaseRolePermissions] hands a custom render. Adding is offered only when both [onAdd] and [candidates] are
 * set, and the candidates only along with it; removing only when [onRemove] is set.
 */
internal fun rolePermissionsState(
    permissions: List<RoleResourcePermissions>,
    isLoading: Boolean,
    error: String?,
    resourceServerNames: Map<String, String>,
    candidates: List<PermissionCandidate>?,
    isMutating: Boolean,
    onAdd: ((String, String) -> Unit)?,
    onRemove: ((String, String) -> Unit)?,
): RolePermissionsState =
    RolePermissionsState(
        permissions = grantedPermissions(permissions),
        isLoading = isLoading,
        error = error,
        resourceServerNames = resourceServerNames,
        candidates = candidates.takeIf { onAdd != null },
        isMutating = isMutating,
        onAdd = onAdd.takeIf { candidates != null },
        onRemove = onRemove,
    )

/** Every resource server with the permissions of its own actions, for [RolePermissions] to offer. */
internal suspend fun loadPermissionCandidates(client: ThunderIDClient): List<PermissionCandidate> =
    client.resourceServers.list(limit = CANDIDATE_LIMIT).resourceServers.map { server ->
        val actions = client.resourceServers.listActions(server.id, limit = CANDIDATE_LIMIT).actions
        PermissionCandidate(server.id, server.name, actions.map { it.permission })
    }

/**
 * Presents the permissions a role grants, grouped by resource server. Holds no data of its own.
 *
 * Granting is offered only when both [onAdd] and [candidates] are set, picking a resource server and then one of
 * its permissions. Revoking is offered only when [onRemove] is set, and asks for confirmation first.
 *
 * @param error A translated message to show above the permissions.
 * @param resourceServerNames Names to show for resource server identifiers. The identifier is shown otherwise.
 * @param candidates The resource servers, and their permissions, that can be granted.
 * @param isMutating Whether a permission is being added or removed. The controls are disabled meanwhile.
 * @param onAdd Grants a permission: the resource server identifier, then the permission.
 * @param onRemove Revokes a permission: the resource server identifier, then the permission.
 * @param content Renders custom UI in place of the default view, from the granted permissions grouped by
 *   resource server, the loading and error state, a resource server name lookup and, when editable, the
 *   candidates and the add and remove actions.
 */
@Composable
fun BaseRolePermissions(
    permissions: List<RoleResourcePermissions>,
    modifier: Modifier = Modifier,
    error: String? = null,
    isLoading: Boolean = false,
    resourceServerNames: Map<String, String> = emptyMap(),
    title: String? = null,
    description: String? = null,
    candidates: List<PermissionCandidate>? = null,
    isMutating: Boolean = false,
    onAdd: ((resourceServerId: String, permission: String) -> Unit)? = null,
    onRemove: ((resourceServerId: String, permission: String) -> Unit)? = null,
    content: (@Composable (RolePermissionsState) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    val state =
        rolePermissionsState(permissions, isLoading, error, resourceServerNames, candidates, isMutating, onAdd, onRemove)
    if (content != null) {
        Box(modifier = modifier) { content(state) }
        return
    }
    var pendingRemoval by remember { mutableStateOf<Pair<String, String>?>(null) }
    AdminSection(
        block = BLOCK,
        modifier = modifier,
        title = title ?: i18n.resolve("admin.roles.permissions.title"),
        description = description,
    ) {
        if (isLoading) {
            AdminLoading()
        } else {
            RolePermissionEntries(state, error, isMutating) { pendingRemoval = it }
            val add = state.onAdd
            val offered = state.candidates
            if (add != null && offered != null) {
                RolePermissionPicker(offered, state.permissions, isMutating, add)
            }
        }
    }
    val removal = pendingRemoval
    val remove = state.onRemove
    if (removal != null && remove != null) {
        val (resourceServerId, permission) = removal
        AdminConfirmDialog(
            block = BLOCK,
            action = "remove",
            text =
                i18n.translate(
                    "admin.roles.permissions.remove_confirm",
                    "permission" to permission,
                    "name" to state.resourceServerName(resourceServerId),
                ),
            onConfirm = {
                pendingRemoval = null
                remove(resourceServerId, permission)
            },
            onDismiss = { pendingRemoval = null },
        )
    }
}

/** The granted permissions, one panel per resource server. A removable chip hands its permission to [onRemove]. */
@Composable
private fun RolePermissionEntries(
    state: RolePermissionsState,
    error: String?,
    isMutating: Boolean,
    onRemove: (Pair<String, String>) -> Unit,
) {
    val i18n = LocalThunderID.current.i18n
    error?.let { AdminErrorBanner(it) }
    if (state.permissions.isEmpty()) {
        if (error == null) AdminMuted(i18n.resolve("admin.roles.permissions.empty"))
        return
    }
    val removable = state.onRemove != null && !isMutating
    state.permissions.forEach { entry ->
        val name = state.resourceServerName(entry.resourceServerId)
        AdminPanel(title = name) {
            AdminChipList(
                label = name,
                emptyText = i18n.resolve("admin.roles.permissions.empty"),
                items =
                    entry.permissions.map { permission ->
                        AdminChip(
                            id = permission,
                            label = permission,
                            onRemove = if (removable) ({ onRemove(entry.resourceServerId to permission) }) else null,
                            removeLabel =
                                i18n.translate("admin.roles.permissions.remove_label", "permission" to permission, "name" to name),
                            removeTestTag = adminTag(BLOCK, "remove", entry.resourceServerId, permission),
                        )
                    },
            )
        }
    }
}

/** Picks a resource server, then one of its permissions the role does not grant yet, and hands both to [onAdd]. */
@Composable
private fun RolePermissionPicker(
    candidates: List<PermissionCandidate>,
    granted: List<RoleResourcePermissions>,
    isMutating: Boolean,
    onAdd: (String, String) -> Unit,
) {
    val i18n = LocalThunderID.current.i18n
    var resourceServerId by remember { mutableStateOf("") }
    var permission by remember { mutableStateOf("") }
    val options = addablePermissions(candidates, granted, resourceServerId).map { AdminOption(it, it) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        AdminSelect(
            label = i18n.resolve("admin.roles.permissions.fields.resource_server"),
            options = candidates.map { AdminOption(it.label, it.resourceServerId) },
            value = resourceServerId,
            onChange = {
                resourceServerId = it
                permission = ""
            },
            testTag = adminTag(BLOCK, "resource-server"),
            placeholder = i18n.resolve("admin.roles.permissions.fields.resource_server_placeholder"),
            emptyText = i18n.resolve("admin.roles.permissions.fields.resource_server_empty"),
            modifier = Modifier.fillMaxWidth(),
        )
        AdminSelect(
            label = i18n.resolve("admin.roles.permissions.fields.permission"),
            options = options,
            value = permission,
            onChange = { permission = it },
            testTag = adminTag(BLOCK, "permission"),
            placeholder = i18n.resolve("admin.roles.permissions.fields.permission_placeholder"),
            emptyText = i18n.resolve("admin.roles.permissions.fields.permission_empty"),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                onAdd(resourceServerId, permission)
                permission = ""
            },
            enabled = resourceServerId.isNotEmpty() && permission.isNotEmpty() && !isMutating,
            modifier = Modifier.align(Alignment.End).testTag(adminTag(BLOCK, "add")),
        ) { Text(i18n.resolve("admin.roles.permissions.add")) }
    }
}

/**
 * Shows the permissions a role grants, loaded through the role management operations.
 *
 * @param editable Lets permissions be granted, picked from the resource servers and their actions, and revoked,
 *   which asks for confirmation. Changes are saved with the role update operation. Never offered for a read-only
 *   role. Off by default.
 * @param administrationMode How a revocation is saved: [AdministrationMode.FLOW] runs the permission removal flow
 *   the server configures, which can revoke grants first, and saves through the API when none is. Defaults to
 *   [AdministrationMode.NATIVE], which calls the API directly.
 * @param resourceServerNames Names to show for resource server identifiers. When editable, the loaded resource
 *   servers' names are used for any identifier this leaves out.
 * @param content Renders custom UI in place of the default view. See [BaseRolePermissions].
 */
@Composable
fun RolePermissions(
    roleId: String,
    modifier: Modifier = Modifier,
    editable: Boolean = false,
    administrationMode: AdministrationMode = AdministrationMode.NATIVE,
    resourceServerNames: Map<String, String> = emptyMap(),
    title: String? = null,
    description: String? = null,
    content: (@Composable (RolePermissionsState) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    val scope = rememberCoroutineScope()
    val role = rememberGetRole(roleId)
    val loaded = role.data
    val canEdit = editable && loaded != null && loaded.isReadOnly != true
    val candidates =
        rememberResourceQuery(listOf("resource-servers", "permission-candidates"), enabled = canEdit, ::loadPermissionCandidates)
    val update =
        rememberResourceMutation<Pair<Role, List<RoleResourcePermissions>>, Role>({
            listOf(listOf(RoleQueryKeys.ROLE, roleId), listOf(RoleQueryKeys.ROLES))
        }) { client, (current, permissions) ->
            val request = UpdateRoleRequest(current.name, current.ouId, permissions, current.description)
            client.roles.update(roleId, request, administrationMode = administrationMode, current = current)
        }
    val save: (List<RoleResourcePermissions>.() -> List<RoleResourcePermissions>) -> Unit = { change ->
        role.data?.let { current -> scope.launch { update.mutate(current to current.permissions.change()) } }
    }
    val offered = candidates.data.takeIf { canEdit }
    BaseRolePermissions(
        permissions = loaded?.permissions.orEmpty(),
        modifier = modifier,
        error =
            toManagementErrorMessage(i18n, role.error, "admin.roles.permissions.error")
                ?: toManagementErrorMessage(i18n, update.error, "admin.roles.permissions.update_error")
                ?: toManagementErrorMessage(i18n, candidates.error.takeIf { canEdit }, "admin.roles.permissions.options_error"),
        isLoading = role.isLoading && loaded == null,
        resourceServerNames = offered.orEmpty().associate { it.resourceServerId to it.label } + resourceServerNames,
        title = title,
        description = description,
        candidates = offered,
        isMutating = update.isLoading,
        onAdd = if (canEdit) ({ serverId, permission -> save { withPermission(this, serverId, permission) } }) else null,
        onRemove = if (canEdit) ({ serverId, permission -> save { withoutPermission(this, serverId, permission) } }) else null,
        content = content,
    )
}
