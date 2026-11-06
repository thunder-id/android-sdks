// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.management

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.thunderid.android.api.management.Project
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.management.rememberGetProjects

/**
 * Presents a page of projects. Holds no data of its own: pass the [projects] and their loading state.
 *
 * @param error A translated message to show in place of the rows.
 * @param onPageChange Receives the offset of the page to show. Pagination is hidden when `null`.
 * @param onSelect Receives the chosen project. Rows are selectable only when set.
 * @param title Heading of the list. Pass an empty string to hide it.
 * @param actions Controls rendered next to the title, such as a button that opens [ProjectCreate].
 * @param content Renders custom UI in place of the default list, from the projects, the loading and error state,
 *   and the pagination and selection handlers.
 */
@Composable
fun BaseProjectList(
    projects: List<Project>,
    modifier: Modifier = Modifier,
    totalResults: Int? = null,
    isLoading: Boolean = false,
    error: String? = null,
    limit: Int = 20,
    offset: Int = 0,
    onPageChange: ((Int) -> Unit)? = null,
    onSelect: ((Project) -> Unit)? = null,
    selectedProjectId: String? = null,
    title: String? = null,
    description: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: (@Composable (AdminListState<Project>) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    if (content != null) {
        Box(modifier = modifier) {
            content(
                AdminListState(
                    rows = projects,
                    isLoading = isLoading,
                    error = error,
                    limit = limit,
                    offset = offset,
                    totalResults = totalResults ?: projects.size,
                    selectedId = selectedProjectId,
                    onPageChange = onPageChange,
                    onSelect = onSelect,
                ),
            )
        }
        return
    }
    AdminSection(
        block = "project-list",
        modifier = modifier,
        title = title ?: i18n.resolve("admin.projects.list.title"),
        description = description,
        actions = actions,
    ) {
        AdminTable(
            block = "project-list",
            columns =
                listOf(
                    AdminColumn(i18n.resolve("admin.projects.list.columns.name")) { Text(it.displayName) },
                    AdminColumn(i18n.resolve("admin.projects.list.columns.handle")) { Text(it.handle) },
                    AdminColumn(i18n.resolve("admin.projects.list.columns.description"), 2f) { Text(it.description.orEmpty()) },
                ),
            rows = projects,
            rowId = { it.id },
            emptyText = i18n.resolve("admin.projects.list.empty"),
            isLoading = isLoading,
            error = error,
            onRowClick = onSelect,
            selectedRowId = selectedProjectId,
            pagination = onPageChange?.let { AdminPagination(limit, offset, totalResults ?: projects.size, it) },
        )
    }
}

/**
 * Lists projects, a page at a time, through the project management operations. Only the Control Plane serves
 * projects.
 *
 * @param content Renders custom UI in place of the default list. See [BaseProjectList].
 */
@Composable
fun ProjectList(
    modifier: Modifier = Modifier,
    limit: Int = 20,
    onSelect: ((Project) -> Unit)? = null,
    selectedProjectId: String? = null,
    title: String? = null,
    description: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: (@Composable (AdminListState<Project>) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    var offset by remember(limit) { mutableStateOf(0) }
    val projects = rememberGetProjects(limit = limit, offset = offset)
    BaseProjectList(
        projects = projects.data?.projects.orEmpty(),
        modifier = modifier,
        totalResults = projects.data?.totalResults,
        isLoading = projects.isLoading,
        error = toManagementErrorMessage(i18n, projects.error, "admin.projects.list.error"),
        limit = limit,
        offset = offset,
        onPageChange = { offset = it },
        onSelect = onSelect,
        selectedProjectId = selectedProjectId,
        title = title,
        description = description,
        actions = actions,
        content = content,
    )
}
