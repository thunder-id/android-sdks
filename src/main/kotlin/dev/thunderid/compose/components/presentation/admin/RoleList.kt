// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.presentation.admin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.thunderid.android.management.RoleSummary
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.management.rememberGetRoles

/**
 * Presents a page of roles. Holds no data of its own: pass the [roles] and their loading state.
 *
 * @param error A translated message to show in place of the rows.
 * @param onPageChange Receives the offset of the page to show. Pagination is hidden when `null`.
 * @param onSelect Receives the chosen role. Rows are selectable only when set.
 * @param title Heading of the list. Pass an empty string to hide it.
 * @param actions Controls rendered next to the title, such as a button that opens [RoleCreate].
 * @param content Renders custom UI in place of the default list, from the roles, the loading and error state,
 *   and the pagination and selection handlers.
 */
@Composable
fun BaseRoleList(
    roles: List<RoleSummary>,
    modifier: Modifier = Modifier,
    totalResults: Int? = null,
    isLoading: Boolean = false,
    error: String? = null,
    limit: Int = 20,
    offset: Int = 0,
    onPageChange: ((Int) -> Unit)? = null,
    onSelect: ((RoleSummary) -> Unit)? = null,
    selectedRoleId: String? = null,
    title: String? = null,
    description: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: (@Composable (AdminListState<RoleSummary>) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    if (content != null) {
        Box(modifier = modifier) {
            content(
                AdminListState(
                    rows = roles,
                    isLoading = isLoading,
                    error = error,
                    limit = limit,
                    offset = offset,
                    totalResults = totalResults ?: roles.size,
                    selectedId = selectedRoleId,
                    onPageChange = onPageChange,
                    onSelect = onSelect,
                ),
            )
        }
        return
    }
    AdminSection(
        block = "role-list",
        modifier = modifier,
        title = title ?: i18n.resolve("admin.roles.list.title"),
        description = description,
        actions = actions,
    ) {
        AdminTable(
            block = "role-list",
            columns =
                listOf(
                    AdminColumn(i18n.resolve("admin.roles.list.columns.name")) { Text(it.name) },
                    AdminColumn(i18n.resolve("admin.roles.list.columns.description"), 2f) { Text(it.description.orEmpty()) },
                    AdminColumn(i18n.resolve("admin.common.columns.organization_unit")) { Text(it.ouHandle ?: it.ouId) },
                ),
            rows = roles,
            rowId = { it.id },
            emptyText = i18n.resolve("admin.roles.list.empty"),
            isLoading = isLoading,
            error = error,
            onRowClick = onSelect,
            selectedRowId = selectedRoleId,
            pagination = onPageChange?.let { AdminPagination(limit, offset, totalResults ?: roles.size, it) },
        )
    }
}

/**
 * Lists roles, a page at a time, through the role management operations.
 *
 * @param content Renders custom UI in place of the default list. See [BaseRoleList].
 */
@Composable
fun RoleList(
    modifier: Modifier = Modifier,
    limit: Int = 20,
    onSelect: ((RoleSummary) -> Unit)? = null,
    selectedRoleId: String? = null,
    title: String? = null,
    description: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: (@Composable (AdminListState<RoleSummary>) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    var offset by remember(limit) { mutableStateOf(0) }
    val roles = rememberGetRoles(limit = limit, offset = offset)
    BaseRoleList(
        roles = roles.data?.roles.orEmpty(),
        modifier = modifier,
        totalResults = roles.data?.totalResults,
        isLoading = roles.isLoading,
        error = toManagementErrorMessage(i18n, roles.error, "admin.roles.list.error"),
        limit = limit,
        offset = offset,
        onPageChange = { offset = it },
        onSelect = onSelect,
        selectedRoleId = selectedRoleId,
        title = title,
        description = description,
        actions = actions,
        content = content,
    )
}
