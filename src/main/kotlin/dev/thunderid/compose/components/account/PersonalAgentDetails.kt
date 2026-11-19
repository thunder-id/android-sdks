// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.thunderid.android.api.account.MeAgentSuspensionAction
import dev.thunderid.android.api.account.PersonalAgentFormValues
import dev.thunderid.android.api.account.UpdateMeAgentRequest
import dev.thunderid.android.api.account.oauthConfig
import dev.thunderid.android.api.management.Agent
import dev.thunderid.android.api.management.OAuth2Config
import dev.thunderid.android.api.management.resolveAgentName
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.components.management.AdminConfirmDialog
import dev.thunderid.compose.components.management.AdminDetailList
import dev.thunderid.compose.components.management.AdminErrorBanner
import dev.thunderid.compose.components.management.AdminLoading
import dev.thunderid.compose.components.management.AdminMuted
import dev.thunderid.compose.components.management.AdminPanel
import dev.thunderid.compose.components.management.AdminSection
import dev.thunderid.compose.components.management.AdminSuccessNotice
import dev.thunderid.compose.components.management.adminTag
import dev.thunderid.compose.components.management.toManagementErrorMessage
import dev.thunderid.compose.components.management.translate
import dev.thunderid.compose.management.rememberDeleteMeAgent
import dev.thunderid.compose.management.rememberGetMeAgent
import dev.thunderid.compose.management.rememberMeAgentSuspension
import dev.thunderid.compose.management.rememberUpdateMeAgent
import kotlinx.coroutines.launch

/**
 * What [BasePersonalAgentDetails] hands its `content` lambda.
 *
 * @property oauth The agent's OAuth settings. `null` when it has no OAuth client.
 * @property error A translated message, or `null` when there is nothing to report.
 * @property onUpdate Replaces the editable part of the agent, and returns whether it was saved.
 *   [PersonalAgentFormValues] builds the request.
 * @property onDelete Deletes the agent. Ask for confirmation first.
 * @property onSuspend Places the owner's suspension on the agent. Offered unless the owner already holds it
 *   suspended. Ask for confirmation first.
 * @property onUnsuspend Lifts the owner's suspension of the agent. Offered only while the owner holds it suspended.
 */
data class PersonalAgentDetailsState(
    val agent: Agent?,
    val oauth: OAuth2Config?,
    val isLoading: Boolean,
    val error: String?,
    val isUpdating: Boolean,
    val isDeleting: Boolean,
    val isChangingSuspension: Boolean,
    val onUpdate: (suspend (UpdateMeAgentRequest) -> Boolean)?,
    val onDelete: (() -> Unit)?,
    val onSuspend: (() -> Unit)?,
    val onUnsuspend: (() -> Unit)?,
)

/**
 * One of the signed-in user's personal agents with the actions its owner can take, rendered from the data it is
 * given. Editing, deleting and the owner's suspension are each offered only when their handler is set; editing
 * and deleting never for a read-only agent. Suspending and deleting ask for confirmation first.
 * [PersonalAgentDetails] loads the data for you.
 *
 * @param error A translated message to show.
 * @param onUpdate Saves the edited agent and returns whether it was saved. The edit form stays open with the
 *   changes until it returns `true`.
 * @param actions Controls rendered next to the agent's name.
 * @param content Renders custom UI in place of the default view, from the agent, its OAuth settings, the loading
 *   and error state, and the actions the app has turned on that apply to it.
 */
@Composable
fun BasePersonalAgentDetails(
    agent: Agent?,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    error: String? = null,
    isUpdating: Boolean = false,
    isDeleting: Boolean = false,
    isChangingSuspension: Boolean = false,
    onUpdate: (suspend (UpdateMeAgentRequest) -> Boolean)? = null,
    onDelete: (() -> Unit)? = null,
    onSuspend: (() -> Unit)? = null,
    onUnsuspend: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: (@Composable (PersonalAgentDetailsState) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    val isWritable = agent != null && agent.isReadOnly != true
    val suspension = agent?.let { MeAgentSuspensionAction.available(it.status) }
    val update = onUpdate.takeIf { isWritable }
    val delete = onDelete.takeIf { isWritable }
    val suspend = onSuspend.takeIf { suspension == MeAgentSuspensionAction.SUSPEND }
    val unsuspend = onUnsuspend.takeIf { suspension == MeAgentSuspensionAction.UNSUSPEND }
    val scope = rememberCoroutineScope()
    // A change of agent discards the draft, so one agent's values are never saved onto another, and coming back
    // to an agent starts from its stored values.
    var draft by remember(agent?.id) { mutableStateOf<PersonalAgentDraft?>(null) }
    var confirmation by remember(agent?.id) { mutableStateOf<PersonalAgentConfirmation?>(null) }
    if (content != null) {
        Box(modifier = modifier) {
            content(
                PersonalAgentDetailsState(
                    agent = agent,
                    oauth = agent?.oauthConfig,
                    isLoading = isLoading,
                    error = error,
                    isUpdating = isUpdating,
                    isDeleting = isDeleting,
                    isChangingSuspension = isChangingSuspension,
                    onUpdate = update,
                    onDelete = delete,
                    onSuspend = suspend,
                    onUnsuspend = unsuspend,
                ),
            )
        }
        return
    }
    if (agent == null) {
        AdminSection(block = PERSONAL_AGENT_DETAILS, modifier = modifier) {
            if (isLoading) AdminLoading() else AdminErrorBanner(error ?: i18n.resolve("admin.common.errors.not_found"))
        }
        return
    }
    val name = resolveAgentName(agent)
    AdminSection(
        block = PERSONAL_AGENT_DETAILS,
        modifier = modifier,
        title = name,
        description = agent.description,
        actions = {
            actions?.invoke(this)
            val isEditing = draft?.agentId == agent.id
            val hasMenu = suspend != null || unsuspend != null || delete != null
            // Edit and the menu sit together, closer than the header's other actions.
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (update != null && !isEditing) {
                    // Sized to its label rather than a text button's minimum width, so it sits next to the menu.
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .heightIn(min = 48.dp)
                                .clip(MaterialTheme.shapes.small)
                                .clickable(role = Role.Button) {
                                    draft = PersonalAgentDraft(agent.id, PersonalAgentFormValues.from(agent))
                                }.padding(horizontal = 8.dp)
                                .testTag(adminTag(PERSONAL_AGENT_DETAILS, "edit")),
                    ) {
                        Text(
                            text = i18n.resolve("user.agents.edit.action"),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (hasMenu) {
                    PersonalAgentActionMenu(
                        isBusy = isChangingSuspension || isDeleting,
                        onUnsuspend = unsuspend,
                        onSuspend = suspend?.let { { confirmation = PersonalAgentConfirmation.SUSPEND } },
                        onDelete = delete?.let { { confirmation = PersonalAgentConfirmation.DELETE } },
                    )
                }
            }
        },
    ) {
        error?.let { AdminErrorBanner(it) }
        personalAgentSuspensionNotices(i18n, agent).forEach { AdminSuccessNotice(it) }
        val current = draft?.takeIf { it.agentId == agent.id }
        if (current != null && update != null) {
            PersonalAgentEditForm(
                i18n = i18n,
                values = current.values,
                onValuesChange = { draft = current.copy(values = it) },
                isUpdating = isUpdating,
                onCancel = { draft = null },
                onSave = { values ->
                    val request = values.updateRequest(agent)
                    scope.launch {
                        // Keeps the changes until the update succeeds, so a failed save can be corrected and retried.
                        if (update(request) && draft?.agentId == agent.id) draft = null
                    }
                },
            )
        } else {
            AdminPanel(title = i18n.resolve("user.agents.details.overview")) {
                AdminDetailList(personalAgentOverview(i18n, agent))
            }
            AdminPanel(title = i18n.resolve("user.agents.details.attributes")) {
                val attributes = personalAgentAttributes(agent)
                if (attributes.isEmpty()) AdminMuted(i18n.resolve("user.agents.details.none")) else AdminDetailList(attributes)
            }
        }
    }
    confirmation?.let { pending ->
        AdminConfirmDialog(
            block = PERSONAL_AGENT_DETAILS,
            action = pending.action,
            text = i18n.translate(pending.confirmKey, "name" to name),
            onConfirm = {
                confirmation = null
                if (pending == PersonalAgentConfirmation.SUSPEND) suspend?.invoke() else delete?.invoke()
            },
            onDismiss = { confirmation = null },
        )
    }
}

/**
 * One of the signed-in user's personal agents, loaded through `/users/me/agents`.
 *
 * @param editable Lets the user edit the agent's name, attributes, description, redirect URIs and grant types.
 * @param deletable Lets the user delete the agent. Asks for confirmation first.
 * @param suspendable Lets the user place or lift their own suspension of the agent. Suspending asks for
 *   confirmation first.
 * @param onDeleted Called once the agent is deleted, for example to navigate away.
 * @param content Renders custom UI in place of the default view. See [BasePersonalAgentDetails].
 *
 * Editing, deleting and suspending are each off by default.
 */
@Composable
fun PersonalAgentDetails(
    agentId: String,
    modifier: Modifier = Modifier,
    editable: Boolean = false,
    deletable: Boolean = false,
    suspendable: Boolean = false,
    onDeleted: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: (@Composable (PersonalAgentDetailsState) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    val scope = rememberCoroutineScope()
    val currentAgentId by rememberUpdatedState(agentId)
    val agent = rememberGetMeAgent(agentId)
    val update = rememberUpdateMeAgent()
    val deletion = rememberDeleteMeAgent()
    val suspension = rememberMeAgentSuspension()
    // The agent this composable deleted, shown as gone from then on rather than as the last record read.
    var deletedId by remember { mutableStateOf<String?>(null) }
    val isDeleted = deletedId == agentId
    val changeSuspension = { action: MeAgentSuspensionAction ->
        scope.launch { suspension.mutate(agentId to action) }
        Unit
    }
    BasePersonalAgentDetails(
        agent = if (isDeleted) null else agent.data,
        modifier = modifier,
        // A new agent's query has neither data nor an error until its first request starts, which reads as loading.
        isLoading = !isDeleted && (agent.isLoading || (agent.data == null && agent.error == null)),
        error =
            toManagementErrorMessage(i18n, agent.error, "user.agents.details.error")
                ?: toManagementErrorMessage(i18n, update.error ?: deletion.error ?: suspension.error, "user.agents.action.error"),
        isUpdating = update.isLoading,
        isDeleting = deletion.isLoading,
        isChangingSuspension = suspension.isLoading,
        onUpdate =
            if (editable) {
                { request -> update.mutate(agentId to request) != null }
            } else {
                null
            },
        onDelete =
            if (deletable) {
                {
                    val deleting = agentId
                    scope.launch {
                        deletion.mutate(deleting)?.let {
                            deletedId = deleting
                            // A delete that completes after another agent was selected must not close that agent.
                            if (currentAgentId == deleting) onDeleted?.invoke()
                        }
                    }
                }
            } else {
                null
            },
        onSuspend = if (suspendable) ({ changeSuspension(MeAgentSuspensionAction.SUSPEND) }) else null,
        onUnsuspend = if (suspendable) ({ changeSuspension(MeAgentSuspensionAction.UNSUSPEND) }) else null,
        actions = actions,
        content = content,
    )
}
