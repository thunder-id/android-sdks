// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.admin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.thunderid.android.management.BasicAgent
import dev.thunderid.android.management.ManagementFilter
import dev.thunderid.android.management.resolveAgentName
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.management.rememberGetAgents

/**
 * Presents a page of agents. Holds no data of its own: pass the [agents] and their loading state.
 *
 * @param error A translated message to show in place of the rows.
 * @param onPageChange Receives the offset of the page to show. Pagination is hidden when `null`.
 * @param onSelect Receives the chosen agent. Rows are selectable only when set.
 * @param title Heading of the list. Pass an empty string to hide it.
 * @param search The search currently applied, if any. While set, an empty page says nothing matched.
 * @param searchAttributes The attributes that can be searched, with translated labels.
 * @param onSearch Applies a search on the server, or clears it with `null`. The search form shows only when this
 *   and [searchAttributes] are set.
 * @param actions Controls rendered next to the title, such as a button that opens [AgentCreate].
 * @param content Renders custom UI in place of the default list, from the agents, the loading and error state,
 *   and the pagination and selection handlers.
 */
@Composable
fun BaseAgentList(
    agents: List<BasicAgent>,
    modifier: Modifier = Modifier,
    totalResults: Int? = null,
    isLoading: Boolean = false,
    error: String? = null,
    limit: Int = 20,
    offset: Int = 0,
    onPageChange: ((Int) -> Unit)? = null,
    onSelect: ((BasicAgent) -> Unit)? = null,
    selectedAgentId: String? = null,
    title: String? = null,
    description: String? = null,
    search: AdminSearch? = null,
    searchAttributes: List<AdminOption> = emptyList(),
    onSearch: ((AdminSearch?) -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: (@Composable (AdminListState<BasicAgent>) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    if (content != null) {
        Box(modifier = modifier) {
            content(
                AdminListState(
                    rows = agents,
                    isLoading = isLoading,
                    error = error,
                    limit = limit,
                    offset = offset,
                    totalResults = totalResults ?: agents.size,
                    selectedId = selectedAgentId,
                    onPageChange = onPageChange,
                    onSelect = onSelect,
                    search = search,
                    searchAttributes = searchAttributes,
                    onSearch = onSearch,
                ),
            )
        }
        return
    }
    AdminSection(
        block = "agent-list",
        modifier = modifier,
        title = title ?: i18n.resolve("admin.agents.list.title"),
        description = description,
        actions = actions,
    ) {
        if (onSearch != null && searchAttributes.isNotEmpty()) {
            AdminSearchBar(block = "agent-list", attributes = searchAttributes, search = search, onSearch = onSearch)
        }
        AdminTable(
            block = "agent-list",
            columns =
                listOf(
                    AdminColumn(i18n.resolve("admin.agents.list.columns.name")) { Text(resolveAgentName(it)) },
                    AdminColumn(i18n.resolve("admin.agents.list.columns.type")) { Text(it.type) },
                    AdminColumn(i18n.resolve("admin.agents.list.columns.client_id")) { Text(it.clientId.orEmpty()) },
                    AdminColumn(i18n.resolve("admin.common.columns.organization_unit")) { Text(it.ouHandle ?: it.ouId) },
                ),
            rows = agents,
            rowId = { it.id },
            emptyText =
                search?.let { i18n.translate("admin.common.search.no_results", "value" to it.value) }
                    ?: i18n.resolve("admin.agents.list.empty"),
            isLoading = isLoading,
            error = error,
            onRowClick = onSelect,
            selectedRowId = selectedAgentId,
            pagination = onPageChange?.let { AdminPagination(limit, offset, totalResults ?: agents.size, it) },
        )
    }
}

/**
 * Lists agents, a page at a time, through the agent management operations.
 *
 * @param content Renders custom UI in place of the default list. See [BaseAgentList].
 * @param searchable Shows a search form. The server matches the whole value of the chosen attribute. On by
 *   default.
 * @param searchAttributes The attributes to offer in the search form. Defaults to name.
 */
@Composable
fun AgentList(
    modifier: Modifier = Modifier,
    limit: Int = 20,
    onSelect: ((BasicAgent) -> Unit)? = null,
    selectedAgentId: String? = null,
    title: String? = null,
    description: String? = null,
    searchable: Boolean = true,
    searchAttributes: List<AdminOption>? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: (@Composable (AdminListState<BasicAgent>) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    var offset by remember(limit) { mutableStateOf(0) }
    var search by remember { mutableStateOf<AdminSearch?>(null) }
    val activeFilter = search?.let { ManagementFilter.equality(it.attribute, it.value) }
    val agents = rememberGetAgents(limit = limit, offset = offset, filter = activeFilter)
    BaseAgentList(
        agents = agents.data?.agents.orEmpty(),
        modifier = modifier,
        totalResults = agents.data?.totalResults,
        isLoading = agents.isLoading,
        error = toManagementErrorMessage(i18n, agents.error, "admin.agents.list.error"),
        limit = limit,
        offset = offset,
        onPageChange = { offset = it },
        onSelect = onSelect,
        selectedAgentId = selectedAgentId,
        title = title,
        description = description,
        search = search,
        searchAttributes = searchAttributes ?: listOf(AdminOption(i18n.resolve("admin.agents.list.search.name"), "name")),
        onSearch =
            if (searchable) {
                { next ->
                    search = next
                    offset = 0
                }
            } else {
                null
            },
        actions = actions,
        content = content,
    )
}
