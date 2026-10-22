// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.presentation.admin

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.thunderid.android.management.RoleResourcePermissions
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.management.rememberGetRole

/** The entries that grant at least one permission. */
internal fun grantedPermissions(permissions: List<RoleResourcePermissions>): List<RoleResourcePermissions> =
    permissions.filter { it.permissions.isNotEmpty() }

/**
 * Presents the permissions a role grants, grouped by resource server. Holds no data of its own.
 *
 * @param error A translated message to show in place of the permissions.
 * @param resourceServerNames Names to show for resource server identifiers. The identifier is shown otherwise.
 * @param content Renders custom UI in place of the default view, from the granted permissions grouped by
 *   resource server, the loading and error state, and a resource server name lookup.
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
    content: (@Composable (RolePermissionsState) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    val granted = grantedPermissions(permissions)
    if (content != null) {
        Box(modifier = modifier) {
            content(
                RolePermissionsState(
                    permissions = granted,
                    isLoading = isLoading,
                    error = error,
                    resourceServerNames = resourceServerNames,
                ),
            )
        }
        return
    }
    AdminSection(
        block = "role-permissions",
        modifier = modifier,
        title = title ?: i18n.resolve("admin.roles.permissions.title"),
        description = description,
    ) {
        when {
            isLoading -> {
                AdminLoading()
            }

            error != null -> {
                AdminErrorBanner(error)
            }

            granted.isEmpty() -> {
                AdminMuted(i18n.resolve("admin.roles.permissions.empty"))
            }

            else -> {
                granted.forEach { entry ->
                    val name = resourceServerNames[entry.resourceServerId] ?: entry.resourceServerId
                    AdminPanel(title = name) {
                        AdminChipList(
                            label = name,
                            emptyText = i18n.resolve("admin.roles.permissions.empty"),
                            items = entry.permissions.map { AdminChip(it, it) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Shows the permissions a role grants, loaded through the role management operations. Read-only.
 *
 * @param content Renders custom UI in place of the default view. See [BaseRolePermissions].
 */
@Composable
fun RolePermissions(
    roleId: String,
    modifier: Modifier = Modifier,
    resourceServerNames: Map<String, String> = emptyMap(),
    title: String? = null,
    description: String? = null,
    content: (@Composable (RolePermissionsState) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    val role = rememberGetRole(roleId)
    BaseRolePermissions(
        permissions = role.data?.permissions.orEmpty(),
        modifier = modifier,
        error = toManagementErrorMessage(i18n, role.error, "admin.roles.permissions.error"),
        isLoading = role.isLoading,
        resourceServerNames = resourceServerNames,
        title = title,
        description = description,
        content = content,
    )
}
