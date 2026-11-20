// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.account

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.thunderid.android.api.management.AgentKind
import dev.thunderid.android.api.management.BasicAgent
import dev.thunderid.android.api.management.resolveAgentName
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.components.management.AdminAccessStatus
import dev.thunderid.compose.components.management.AdminColumn
import dev.thunderid.compose.components.management.AdminConfirmButton
import dev.thunderid.compose.components.management.AdminErrorBanner
import dev.thunderid.compose.components.management.AdminPagination
import dev.thunderid.compose.components.management.AdminSection
import dev.thunderid.compose.components.management.AdminSuccessNotice
import dev.thunderid.compose.components.management.AdminTable
import dev.thunderid.compose.components.management.adminTag
import dev.thunderid.compose.components.management.toManagementErrorMessage
import dev.thunderid.compose.components.management.translate
import dev.thunderid.compose.management.rememberGetMeAgents
import dev.thunderid.compose.management.rememberSuspendAllMeAgents
import kotlinx.coroutines.launch

internal const val PERSONAL_AGENT_LIST = "personal-agent-list"

/**
 * What [BasePersonalAgentList] hands its `content` lambda.
 *
 * @property error A translated message, or `null` when there is nothing to report.
 * @property totalResults The total on the server, or the number of agents given when it is unknown.
 * @property onPageChange Shows the page that starts at the given offset. `null` when the list is not paginated.
 * @property onSelect Chooses an agent. `null` when agents are not selectable.
 * @property actionError Why the last suspend-all request failed, already translated.
 * @property onSuspendAll Suspends every personal agent of the signed-in user. Ask for confirmation first. `null`
 *   when not allowed, or when there are none.
 * @property suspendedCount How many agents the last suspend-all request suspended, until another page is shown.
 */
data class PersonalAgentListState(
    val agents: List<BasicAgent>,
    val isLoading: Boolean,
    val error: String?,
    val limit: Int,
    val offset: Int,
    val totalResults: Int,
    val selectedId: String?,
    val onPageChange: ((Int) -> Unit)?,
    val onSelect: ((BasicAgent) -> Unit)?,
    val actionError: String?,
    val isSuspendingAll: Boolean,
    val onSuspendAll: (() -> Unit)?,
    val suspendedCount: Int?,
)

/**
 * The signed-in user's personal agents with their client and status, rendered from the data it is given.
 * Suspending them all is offered only when [onSuspendAll] is set and there are agents, and asks for confirmation
 * first. [PersonalAgentList] loads the data for you.
 *
 * @param error A translated message to show in place of the rows.
 * @param actionError A translated message about the last suspend-all request.
 * @param suspendedCount How many agents the last suspend-all request suspended, shown as a notice.
 * @param actions Controls rendered next to the title.
 * @param content Renders custom UI in place of the default list, from the agents, the loading and error state,
 *   the pagination and selection handlers, and the suspend-all action when it is allowed.
 */
@Composable
fun BasePersonalAgentList(
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
    actionError: String? = null,
    isSuspendingAll: Boolean = false,
    onSuspendAll: (() -> Unit)? = null,
    suspendedCount: Int? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: (@Composable (PersonalAgentListState) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    val suspendAll = onSuspendAll.takeIf { agents.isNotEmpty() }
    if (content != null) {
        Box(modifier = modifier) {
            content(
                PersonalAgentListState(
                    agents = agents,
                    isLoading = isLoading,
                    error = error,
                    limit = limit,
                    offset = offset,
                    totalResults = totalResults ?: agents.size,
                    selectedId = selectedAgentId,
                    onPageChange = onPageChange,
                    onSelect = onSelect,
                    actionError = actionError,
                    isSuspendingAll = isSuspendingAll,
                    onSuspendAll = suspendAll,
                    suspendedCount = suspendedCount,
                ),
            )
        }
        return
    }
    AdminSection(
        block = PERSONAL_AGENT_LIST,
        modifier = modifier,
        title = title ?: i18n.resolve("user.agents.title"),
        description = description ?: i18n.resolve("user.agents.description"),
        actions = {
            actions?.invoke(this)
            if (suspendAll != null) {
                AdminConfirmButton(
                    block = PERSONAL_AGENT_LIST,
                    label = i18n.resolve("user.agents.suspend_all.action"),
                    confirmText = i18n.resolve("user.agents.suspend_all.confirm"),
                    onConfirm = suspendAll,
                    isLoading = isSuspendingAll,
                    action = "suspend-all",
                )
            }
        },
    ) {
        actionError?.let { AdminErrorBanner(it) }
        suspendedCount?.let {
            AdminSuccessNotice(
                i18n.translate("user.agents.suspend_all.done", "count" to it.toString()),
                modifier = Modifier.testTag(adminTag(PERSONAL_AGENT_LIST, "suspended")),
            )
        }
        AdminTable(
            block = PERSONAL_AGENT_LIST,
            columns =
                listOf(
                    AdminColumn(i18n.resolve("user.agents.list.columns.name"), weight = 2f) { agent ->
                        Column {
                            Text(resolveAgentName(agent))
                            agent.clientId?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    AdminColumn(i18n.resolve("user.agents.list.columns.status")) { agent ->
                        AdminAccessStatus(block = "$PERSONAL_AGENT_LIST-${agent.id}", status = agent.status?.value)
                    },
                ),
            rows = agents,
            rowId = { it.id },
            emptyText = i18n.resolve("user.agents.list.empty"),
            isLoading = isLoading,
            error = error,
            onRowClick = onSelect,
            selectedRowId = selectedAgentId,
            pagination = onPageChange?.let { AdminPagination(limit, offset, totalResults ?: agents.size, it) },
        )
    }
}

/**
 * The signed-in user's personal agents, a page at a time, through `/users/me/agents`. Pair it with
 * [PersonalAgentDetails] to show the agent the user chooses.
 *
 * @param suspendable Lets the user suspend all their personal agents at once. Off by default, and asks for
 *   confirmation first.
 * @param content Renders custom UI in place of the default list. See [BasePersonalAgentList].
 */
@Composable
fun PersonalAgentList(
    modifier: Modifier = Modifier,
    limit: Int = 20,
    onSelect: ((BasicAgent) -> Unit)? = null,
    selectedAgentId: String? = null,
    title: String? = null,
    description: String? = null,
    suspendable: Boolean = false,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: (@Composable (PersonalAgentListState) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    val scope = rememberCoroutineScope()
    var offset by remember(limit) { mutableStateOf(0) }
    // The count belongs to the page it was reported on, so showing another page clears it.
    var suspendedCount by remember(limit, offset) { mutableStateOf<Int?>(null) }
    val agents = rememberGetMeAgents(kind = AgentKind.PERSONAL, limit = limit, offset = offset)
    val suspendAll = rememberSuspendAllMeAgents()
    BasePersonalAgentList(
        agents = agents.data?.agents.orEmpty(),
        modifier = modifier,
        totalResults = agents.data?.totalResults,
        isLoading = agents.isLoading,
        error = toManagementErrorMessage(i18n, agents.error, "user.agents.list.error"),
        limit = limit,
        offset = offset,
        onPageChange = { offset = it },
        onSelect = onSelect,
        selectedAgentId = selectedAgentId,
        title = title,
        description = description,
        actionError = toManagementErrorMessage(i18n, suspendAll.error, "user.agents.action.error"),
        isSuspendingAll = suspendAll.isLoading,
        onSuspendAll =
            if (suspendable) {
                { scope.launch { suspendAll.mutate(Unit)?.let { suspendedCount = it.suspendedCount } } }
            } else {
                null
            },
        suspendedCount = suspendedCount,
        actions = actions,
        content = content,
    )
}
