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
import androidx.compose.ui.text.font.FontFamily
import dev.thunderid.android.api.management.ManagedFlow
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.management.rememberGetFlows

/** The most flows [SharedFlowList] reads to narrow them by ownership on the device. */
private const val MAX_FETCH = 100

/** Which flows of an organization unit [SharedFlowList] shows. */
enum class SharedFlowOwnership {
    /** Every flow the unit owns or was shared. */
    ALL,

    /** Only the flows the unit owns. */
    OWNED,

    /** Only the flows shared with the unit. */
    SHARED,
}

/** One page of [SharedFlowList]: the flows to show and the total they are a page of. */
internal data class SharedFlowPage(
    val flows: List<ManagedFlow>,
    val totalResults: Int?,
)

/**
 * The page to show. The server cannot narrow by ownership, so a narrowed list is read whole and paged here; a
 * flow whose ownership the server did not report is left out of it. [ALL][SharedFlowOwnership.ALL] is paged by
 * the server, so its [flows] are the page already.
 */
internal fun sharedFlowPage(
    flows: List<ManagedFlow>,
    totalResults: Int?,
    ownership: SharedFlowOwnership,
    offset: Int,
    limit: Int,
): SharedFlowPage {
    if (ownership == SharedFlowOwnership.ALL) return SharedFlowPage(flows, totalResults)
    val owned = ownership == SharedFlowOwnership.OWNED
    val matching = flows.filter { it.isOwnedByOU == owned }
    return SharedFlowPage(matching.drop(offset).take(limit), matching.size)
}

/**
 * Presents a page of flows. Holds no data of its own: pass the [flows] and their loading state.
 *
 * @param error A translated message to show in place of the rows.
 * @param onPageChange Receives the offset of the page to show. Pagination is hidden when `null`.
 * @param onSelect Receives the chosen flow, for example to open its [FlowVariablesForm]. Rows are selectable only
 *   when set.
 * @param title Heading of the list. Pass an empty string to hide it.
 * @param actions Controls rendered next to the title.
 * @param content Renders custom UI in place of the default list, from the flows, the loading and error state, and
 *   the pagination and selection handlers.
 */
@Composable
fun BaseSharedFlowList(
    flows: List<ManagedFlow>,
    modifier: Modifier = Modifier,
    totalResults: Int? = null,
    isLoading: Boolean = false,
    error: String? = null,
    limit: Int = 20,
    offset: Int = 0,
    onPageChange: ((Int) -> Unit)? = null,
    onSelect: ((ManagedFlow) -> Unit)? = null,
    selectedFlowId: String? = null,
    title: String? = null,
    description: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: (@Composable (AdminListState<ManagedFlow>) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    if (content != null) {
        Box(modifier = modifier) {
            content(
                AdminListState(
                    rows = flows,
                    isLoading = isLoading,
                    error = error,
                    limit = limit,
                    offset = offset,
                    totalResults = totalResults ?: flows.size,
                    selectedId = selectedFlowId,
                    onPageChange = onPageChange,
                    onSelect = onSelect,
                ),
            )
        }
        return
    }
    AdminSection(
        block = "shared-flow-list",
        modifier = modifier,
        title = title ?: i18n.resolve("admin.flows.list.title"),
        description = description,
        actions = actions,
    ) {
        AdminTable(
            block = "shared-flow-list",
            columns =
                listOf(
                    AdminColumn(i18n.resolve("admin.flows.list.columns.name")) { Text(it.name ?: it.handle) },
                    AdminColumn(i18n.resolve("admin.flows.list.columns.handle")) { Text(it.handle, fontFamily = FontFamily.Monospace) },
                    AdminColumn(i18n.resolve("admin.flows.list.columns.type")) { Text(it.flowType) },
                ),
            rows = flows,
            rowId = { it.id },
            emptyText = i18n.resolve("admin.flows.list.empty"),
            isLoading = isLoading,
            error = error,
            onRowClick = onSelect,
            selectedRowId = selectedFlowId,
            pagination = onPageChange?.let { AdminPagination(limit, offset, totalResults ?: flows.size, it) },
        )
    }
}

/**
 * Lists the flows of the organization unit [ouId] through the flow management operations: by default the ones
 * shared with it, which it can configure through [FlowVariablesForm].
 *
 * @param ownership Which flows to show. [SharedFlowOwnership.ALL] is paged by the server; the others read up to
 *   100 flows and page them on the device.
 * @param flowType Only flows of this type, such as `AUTHENTICATION`.
 * @param onSelect Receives the chosen flow.
 * @param content Renders custom UI in place of the default list. See [BaseSharedFlowList].
 */
@Composable
fun SharedFlowList(
    ouId: String,
    modifier: Modifier = Modifier,
    ownership: SharedFlowOwnership = SharedFlowOwnership.SHARED,
    flowType: String? = null,
    limit: Int = 20,
    onSelect: ((ManagedFlow) -> Unit)? = null,
    selectedFlowId: String? = null,
    title: String? = null,
    description: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: (@Composable (AdminListState<ManagedFlow>) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    var offset by remember(limit, ouId, ownership, flowType) { mutableStateOf(0) }
    val narrowed = ownership != SharedFlowOwnership.ALL
    val flows =
        rememberGetFlows(
            ouId = ouId,
            flowType = flowType,
            limit = if (narrowed) MAX_FETCH else limit,
            offset = if (narrowed) 0 else offset,
        )
    val page = sharedFlowPage(flows.data?.flows.orEmpty(), flows.data?.totalResults, ownership, offset, limit)
    BaseSharedFlowList(
        flows = page.flows,
        modifier = modifier,
        totalResults = page.totalResults,
        isLoading = flows.isLoading,
        error = toManagementErrorMessage(i18n, flows.error, "admin.flows.list.error"),
        limit = limit,
        offset = offset,
        onPageChange = { offset = it },
        onSelect = onSelect,
        selectedFlowId = selectedFlowId,
        title = title,
        description = description,
        actions = actions,
        content = content,
    )
}
