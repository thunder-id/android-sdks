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
import dev.thunderid.android.management.Group
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.management.rememberGetGroups

/**
 * Presents a page of groups. Holds no data of its own: pass the [groups] and their loading state.
 *
 * @param error A translated message to show in place of the rows.
 * @param onPageChange Receives the offset of the page to show. Pagination is hidden when `null`.
 * @param onSelect Receives the chosen group. Rows are selectable only when set.
 * @param title Heading of the list. Pass an empty string to hide it.
 * @param actions Controls rendered next to the title, such as a button that opens [GroupCreate].
 * @param content Renders custom UI in place of the default list, from the groups, the loading and error state,
 *   and the pagination and selection handlers.
 */
@Composable
fun BaseGroupList(
    groups: List<Group>,
    modifier: Modifier = Modifier,
    totalResults: Int? = null,
    isLoading: Boolean = false,
    error: String? = null,
    limit: Int = 20,
    offset: Int = 0,
    onPageChange: ((Int) -> Unit)? = null,
    onSelect: ((Group) -> Unit)? = null,
    selectedGroupId: String? = null,
    title: String? = null,
    description: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: (@Composable (AdminListState<Group>) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    if (content != null) {
        Box(modifier = modifier) {
            content(
                AdminListState(
                    rows = groups,
                    isLoading = isLoading,
                    error = error,
                    limit = limit,
                    offset = offset,
                    totalResults = totalResults ?: groups.size,
                    selectedId = selectedGroupId,
                    onPageChange = onPageChange,
                    onSelect = onSelect,
                ),
            )
        }
        return
    }
    AdminSection(
        block = "group-list",
        modifier = modifier,
        title = title ?: i18n.resolve("admin.groups.list.title"),
        description = description,
        actions = actions,
    ) {
        AdminTable(
            block = "group-list",
            columns =
                listOf(
                    AdminColumn(i18n.resolve("admin.groups.list.columns.name")) { Text(it.name) },
                    AdminColumn(i18n.resolve("admin.groups.list.columns.description"), 2f) { Text(it.description.orEmpty()) },
                    AdminColumn(i18n.resolve("admin.common.columns.organization_unit")) { Text(it.ouHandle ?: it.ouId) },
                ),
            rows = groups,
            rowId = { it.id },
            emptyText = i18n.resolve("admin.groups.list.empty"),
            isLoading = isLoading,
            error = error,
            onRowClick = onSelect,
            selectedRowId = selectedGroupId,
            pagination = onPageChange?.let { AdminPagination(limit, offset, totalResults ?: groups.size, it) },
        )
    }
}

/**
 * Lists groups, a page at a time, through the group management operations.
 *
 * @param content Renders custom UI in place of the default list. See [BaseGroupList].
 */
@Composable
fun GroupList(
    modifier: Modifier = Modifier,
    limit: Int = 20,
    onSelect: ((Group) -> Unit)? = null,
    selectedGroupId: String? = null,
    title: String? = null,
    description: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: (@Composable (AdminListState<Group>) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    var offset by remember(limit) { mutableStateOf(0) }
    val groups = rememberGetGroups(limit = limit, offset = offset)
    BaseGroupList(
        groups = groups.data?.groups.orEmpty(),
        modifier = modifier,
        totalResults = groups.data?.totalResults,
        isLoading = groups.isLoading,
        error = toManagementErrorMessage(i18n, groups.error, "admin.groups.list.error"),
        limit = limit,
        offset = offset,
        onPageChange = { offset = it },
        onSelect = onSelect,
        selectedGroupId = selectedGroupId,
        title = title,
        description = description,
        actions = actions,
        content = content,
    )
}
