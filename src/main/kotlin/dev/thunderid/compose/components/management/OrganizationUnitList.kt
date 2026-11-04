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
import dev.thunderid.android.api.management.OrganizationUnitSummary
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.management.rememberGetOrganizationUnits

/**
 * Presents a page of organization units. Holds no data of its own: pass the [organizationUnits] and their
 * loading state.
 *
 * @param error A translated message to show in place of the rows.
 * @param onPageChange Receives the offset of the page to show. Pagination is hidden when `null`.
 * @param onSelect Receives the chosen organization unit. Rows are selectable only when set.
 * @param title Heading of the list. Pass an empty string to hide it.
 * @param actions Controls rendered next to the title, such as a button that opens [OrganizationUnitCreate].
 * @param content Renders custom UI in place of the default list, from the organization units, the loading and
 *   error state, and the pagination and selection handlers.
 */
@Composable
fun BaseOrganizationUnitList(
    organizationUnits: List<OrganizationUnitSummary>,
    modifier: Modifier = Modifier,
    totalResults: Int? = null,
    isLoading: Boolean = false,
    error: String? = null,
    limit: Int = 20,
    offset: Int = 0,
    onPageChange: ((Int) -> Unit)? = null,
    onSelect: ((OrganizationUnitSummary) -> Unit)? = null,
    selectedOrganizationUnitId: String? = null,
    title: String? = null,
    description: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: (@Composable (AdminListState<OrganizationUnitSummary>) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    if (content != null) {
        Box(modifier = modifier) {
            content(
                AdminListState(
                    rows = organizationUnits,
                    isLoading = isLoading,
                    error = error,
                    limit = limit,
                    offset = offset,
                    totalResults = totalResults ?: organizationUnits.size,
                    selectedId = selectedOrganizationUnitId,
                    onPageChange = onPageChange,
                    onSelect = onSelect,
                ),
            )
        }
        return
    }
    AdminSection(
        block = "organization-unit-list",
        modifier = modifier,
        title = title ?: i18n.resolve("admin.organization_units.list.title"),
        description = description,
        actions = actions,
    ) {
        AdminTable(
            block = "organization-unit-list",
            columns =
                listOf(
                    AdminColumn(i18n.resolve("admin.organization_units.list.columns.name")) { Text(it.name) },
                    AdminColumn(i18n.resolve("admin.organization_units.list.columns.handle")) { Text(it.handle) },
                    AdminColumn(i18n.resolve("admin.organization_units.list.columns.description"), 2f) {
                        Text(it.description.orEmpty())
                    },
                ),
            rows = organizationUnits,
            rowId = { it.id },
            emptyText = i18n.resolve("admin.organization_units.list.empty"),
            isLoading = isLoading,
            error = error,
            onRowClick = onSelect,
            selectedRowId = selectedOrganizationUnitId,
            pagination = onPageChange?.let { AdminPagination(limit, offset, totalResults ?: organizationUnits.size, it) },
        )
    }
}

/**
 * Lists organization units, a page at a time, through the organization unit management operations: the root
 * units, or the children of [parentId] when it is set.
 *
 * @param content Renders custom UI in place of the default list. See [BaseOrganizationUnitList].
 */
@Composable
fun OrganizationUnitList(
    modifier: Modifier = Modifier,
    parentId: String? = null,
    limit: Int = 20,
    onSelect: ((OrganizationUnitSummary) -> Unit)? = null,
    selectedOrganizationUnitId: String? = null,
    title: String? = null,
    description: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: (@Composable (AdminListState<OrganizationUnitSummary>) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    var offset by remember(limit, parentId) { mutableStateOf(0) }
    val units = rememberGetOrganizationUnits(parentId = parentId, limit = limit, offset = offset)
    BaseOrganizationUnitList(
        organizationUnits = units.data?.organizationUnits.orEmpty(),
        modifier = modifier,
        totalResults = units.data?.totalResults,
        isLoading = units.isLoading,
        error = toManagementErrorMessage(i18n, units.error, "admin.organization_units.list.error"),
        limit = limit,
        offset = offset,
        onPageChange = { offset = it },
        onSelect = onSelect,
        selectedOrganizationUnitId = selectedOrganizationUnitId,
        title = title,
        description = description,
        actions = actions,
        content = content,
    )
}
