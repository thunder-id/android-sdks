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
import dev.thunderid.android.api.management.BasicApplication
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.management.rememberGetApplications

/** What the type column of an application list shows: its template, then its type, then nothing. */
internal fun applicationKind(application: BasicApplication): String = application.template ?: application.type ?: ""

/**
 * Presents a page of applications. Holds no data of its own: pass the [applications] and their loading state.
 * The applications API has no filter, so there is no search.
 *
 * @param error A translated message to show in place of the rows.
 * @param onPageChange Receives the offset of the page to show. Pagination is hidden when `null`.
 * @param onSelect Receives the chosen application. Rows are selectable only when set.
 * @param title Heading of the list. Pass an empty string to hide it.
 * @param actions Controls rendered next to the title, such as a button that opens [ApplicationCreate].
 * @param content Renders custom UI in place of the default list, from the applications, the loading and error
 *   state, and the pagination and selection handlers.
 */
@Composable
fun BaseApplicationList(
    applications: List<BasicApplication>,
    modifier: Modifier = Modifier,
    totalResults: Int? = null,
    isLoading: Boolean = false,
    error: String? = null,
    limit: Int = 20,
    offset: Int = 0,
    onPageChange: ((Int) -> Unit)? = null,
    onSelect: ((BasicApplication) -> Unit)? = null,
    selectedApplicationId: String? = null,
    title: String? = null,
    description: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: (@Composable (AdminListState<BasicApplication>) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    if (content != null) {
        Box(modifier = modifier) {
            content(
                AdminListState(
                    rows = applications,
                    isLoading = isLoading,
                    error = error,
                    limit = limit,
                    offset = offset,
                    totalResults = totalResults ?: applications.size,
                    selectedId = selectedApplicationId,
                    onPageChange = onPageChange,
                    onSelect = onSelect,
                ),
            )
        }
        return
    }
    AdminSection(
        block = "application-list",
        modifier = modifier,
        title = title ?: i18n.resolve("admin.applications.list.title"),
        description = description,
        actions = actions,
    ) {
        AdminTable(
            block = "application-list",
            columns =
                listOf(
                    AdminColumn(i18n.resolve("admin.applications.list.columns.name")) { Text(it.name) },
                    AdminColumn(i18n.resolve("admin.applications.list.columns.client_id")) { Text(it.clientId.orEmpty()) },
                    AdminColumn(i18n.resolve("admin.applications.list.columns.type")) { Text(applicationKind(it)) },
                ),
            rows = applications,
            rowId = { it.id },
            emptyText = i18n.resolve("admin.applications.list.empty"),
            isLoading = isLoading,
            error = error,
            onRowClick = onSelect,
            selectedRowId = selectedApplicationId,
            pagination = onPageChange?.let { AdminPagination(limit, offset, totalResults ?: applications.size, it) },
        )
    }
}

/**
 * Lists applications, a page at a time, through the application management operations.
 *
 * @param projectId Lists the applications of this project: the ones its root organization unit owns, followed by
 *   the ones shared into it. Defaults to the current project ([dev.thunderid.compose.ThunderIDState.currentProject])
 *   when one is chosen; otherwise every application is listed.
 * @param content Renders custom UI in place of the default list. See [BaseApplicationList].
 */
@Composable
fun ApplicationList(
    modifier: Modifier = Modifier,
    limit: Int = 20,
    projectId: String? = null,
    onSelect: ((BasicApplication) -> Unit)? = null,
    selectedApplicationId: String? = null,
    title: String? = null,
    description: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: (@Composable (AdminListState<BasicApplication>) -> Unit)? = null,
) {
    val state = LocalThunderID.current
    val i18n = state.i18n
    val resolvedProjectId = projectId ?: state.currentProject?.id
    var offset by remember(limit, resolvedProjectId) { mutableStateOf(0) }
    val applications = rememberGetApplications(limit = limit, offset = offset, projectId = resolvedProjectId)
    BaseApplicationList(
        applications = applications.data?.applications.orEmpty(),
        modifier = modifier,
        totalResults = applications.data?.totalResults,
        isLoading = applications.isLoading,
        error = toManagementErrorMessage(i18n, applications.error, "admin.applications.list.error"),
        limit = limit,
        offset = offset,
        onPageChange = { offset = it },
        onSelect = onSelect,
        selectedApplicationId = selectedApplicationId,
        title = title,
        description = description,
        actions = actions,
        content = content,
    )
}
