// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.admin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import dev.thunderid.android.management.AccountLifecycleAction
import dev.thunderid.android.management.AccountLifecycleSubjectKind
import dev.thunderid.android.management.Agent
import dev.thunderid.android.management.AgentQueryKeys
import dev.thunderid.android.management.MembershipGroup
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.management.rememberAccountLifecycle
import dev.thunderid.compose.management.rememberDeleteAgent
import dev.thunderid.compose.management.rememberGetAgent
import dev.thunderid.compose.management.rememberGetAgentGroups
import dev.thunderid.compose.management.rememberGetAgentRoles
import kotlinx.coroutines.launch

/**
 * Presents one agent: its overview, the groups it belongs to and the roles it holds. Holds no data of its own.
 *
 * Delete is shown only when [onDelete] is set, and never for a read-only agent. It asks for confirmation first.
 * The agent's access status is shown as a translated label.
 *
 * @param error A translated message to show.
 * @param lifecycleActions The account lifecycle actions to offer (suspend, unsuspend, unlock), shown only with
 *   [onLifecycleAction]. They change only runtime state, so they apply to read-only agents too.
 * @param onLifecycleAction Runs an account lifecycle action. Suspending and unsuspending ask for confirmation
 *   first, and a suspension takes an optional operator note.
 * @param actions Controls rendered next to the agent's name.
 * @param content Renders custom UI in place of the default view, from the agent, its groups and roles, the
 *   loading and error state, and the delete and lifecycle actions.
 */
@Composable
fun BaseAgentDetails(
    agent: Agent?,
    modifier: Modifier = Modifier,
    groups: List<MembershipGroup> = emptyList(),
    roles: List<String> = emptyList(),
    error: String? = null,
    isLoading: Boolean = false,
    isDeleting: Boolean = false,
    onDelete: (() -> Unit)? = null,
    lifecycleActions: List<AccountLifecycleAction> = emptyList(),
    isRunningLifecycleAction: Boolean = false,
    onLifecycleAction: AccountLifecycleHandler? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: (@Composable (AgentDetailsState) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    if (content != null) {
        Box(modifier = modifier) {
            content(
                AgentDetailsState(
                    agent = agent,
                    groups = groups,
                    roles = roles,
                    isLoading = isLoading,
                    error = error,
                    isDeleting = isDeleting,
                    onDelete = onDelete.takeIf { agent != null && agent.isReadOnly != true },
                    lifecycleActions = offeredLifecycleActions(lifecycleActions, onLifecycleAction, agent != null),
                    isRunningLifecycleAction = isRunningLifecycleAction,
                    onLifecycleAction = onLifecycleAction.takeIf { agent != null },
                ),
            )
        }
        return
    }
    if (agent == null) {
        AdminSection(block = "agent-details", modifier = modifier) {
            if (isLoading) AdminLoading() else AdminErrorBanner(error ?: i18n.resolve("admin.common.errors.not_found"))
        }
        return
    }

    val details =
        listOfNotNull(
            AdminDetailItem(i18n.resolve("admin.agents.details.type"), agent.type),
            AdminDetailItem(i18n.resolve("admin.agents.details.owner"), agent.owner ?: i18n.resolve("admin.agents.details.no_owner")),
            AdminDetailItem(i18n.resolve("admin.common.columns.organization_unit"), agent.ouHandle ?: agent.ouId),
            agent.clientId?.let { AdminDetailItem(i18n.resolve("admin.agents.list.columns.client_id"), it, copyable = true) },
            accessStatusLabel(i18n, agent.status?.value)?.let { AdminDetailItem(i18n.resolve("admin.common.columns.status"), it) },
        )
    AdminSection(
        block = "agent-details",
        modifier = modifier,
        title = agent.name,
        description = agent.description,
        actions = {
            actions?.invoke(this)
            if (onLifecycleAction != null) {
                AdminAccountLifecycleButtons(
                    block = "agent-details",
                    actions = lifecycleActions,
                    name = agent.name,
                    onAction = onLifecycleAction,
                    isRunning = isRunningLifecycleAction,
                    collectsOperatorNote = true,
                )
            }
            if (onDelete != null && agent.isReadOnly != true) {
                AdminConfirmButton(
                    block = "agent-details",
                    label = i18n.resolve("admin.agents.details.delete.action"),
                    confirmText = i18n.translate("admin.agents.details.delete.confirm", "name" to agent.name),
                    onConfirm = onDelete,
                    isLoading = isDeleting,
                )
            }
        },
    ) {
        error?.let { AdminErrorBanner(it) }
        AdminPanel(title = i18n.resolve("admin.agents.details.overview")) { AdminDetailList(details) }
        AdminPanel(title = i18n.resolve("admin.agents.details.groups")) {
            AdminChipList(
                label = i18n.resolve("admin.agents.details.groups"),
                emptyText = i18n.resolve("admin.agents.details.no_groups"),
                items = groups.map { AdminChip(it.id, it.name) },
            )
        }
        AdminPanel(title = i18n.resolve("admin.agents.details.roles")) {
            AdminChipList(
                label = i18n.resolve("admin.agents.details.roles"),
                emptyText = i18n.resolve("admin.agents.details.no_roles"),
                items = roles.map { AdminChip(it, it) },
            )
        }
    }
}

/**
 * Shows one agent, loaded through the agent management operations, with its groups and roles.
 *
 * @param deletable Shows a delete action, which asks for confirmation. Off by default.
 * @param lifecycleActions Lets the agent be suspended, unsuspended and unlocked from this view. Each action runs
 *   through its administration flow and is offered only when the server configures that flow and the agent's
 *   status allows it. Reading the configured flows needs a token that may read the server configuration, and
 *   happens only while this is on. Off by default.
 * @param onDeleted Called once the agent is deleted, for example to navigate away.
 * @param content Renders custom UI in place of the default view. See [BaseAgentDetails].
 */
@Composable
fun AgentDetails(
    agentId: String,
    modifier: Modifier = Modifier,
    deletable: Boolean = false,
    lifecycleActions: Boolean = false,
    onDeleted: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: (@Composable (AgentDetailsState) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    val scope = rememberCoroutineScope()
    val agent = rememberGetAgent(agentId)
    val groups = rememberGetAgentGroups(agentId, limit = 100)
    val roles = rememberGetAgentRoles(agentId, limit = 100)
    val deletion = rememberDeleteAgent()
    val lifecycle =
        rememberAccountLifecycle(
            kind = AccountLifecycleSubjectKind.AGENT,
            subject = agentId,
            status = agent.data?.status?.value,
            enabled = lifecycleActions,
            invalidatedKeys = listOf(listOf(AgentQueryKeys.AGENT, agentId), listOf(AgentQueryKeys.AGENTS)),
        )
    BaseAgentDetails(
        agent = agent.data,
        modifier = modifier,
        groups = groups.data?.groups.orEmpty(),
        roles = roles.data?.roles.orEmpty(),
        error =
            toManagementErrorMessage(i18n, agent.error, "admin.agents.details.error")
                ?: toManagementErrorMessage(i18n, deletion.error, "admin.agents.details.delete.error")
                ?: toManagementErrorMessage(i18n, lifecycle.error, "admin.common.lifecycle.error")
                ?: toManagementErrorMessage(i18n, lifecycle.loadError, "admin.common.lifecycle.load_error"),
        isLoading = agent.isLoading,
        isDeleting = deletion.isLoading,
        onDelete =
            if (deletable) {
                { scope.launch { deletion.mutate(agentId)?.let { onDeleted?.invoke() } } }
            } else {
                null
            },
        lifecycleActions = lifecycle.actions,
        isRunningLifecycleAction = lifecycle.isRunning,
        onLifecycleAction = if (lifecycleActions) lifecycle.run else null,
        actions = actions,
        content = content,
    )
}
