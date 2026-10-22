// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.presentation.admin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.thunderid.android.management.Agent
import dev.thunderid.android.management.CreateAgentRequest
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.management.rememberCreateAgent
import dev.thunderid.compose.management.rememberGetAgentTypes
import kotlinx.coroutines.launch

/** What the agent create form submits. A blank description is `null`. */
data class AgentCreateSubmission(
    val name: String,
    val type: String,
    val description: String? = null,
)

/**
 * Presents a form that collects a new agent's name, type and description and hands them to [onSubmit].
 *
 * @param agentTypes The agent types to choose from. The first is chosen by default.
 * @param error A translated message to show above the form.
 * @param onCancel Shows a cancel button that calls it when set.
 * @param content Renders custom UI in place of the default form, from the agent types, the name, type and
 *   description with their setters, the submit action and the submission state.
 */
@Composable
fun BaseAgentCreate(
    agentTypes: List<AdminOption>,
    onSubmit: (AgentCreateSubmission) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    isSubmitting: Boolean = false,
    onCancel: (() -> Unit)? = null,
    title: String? = null,
    description: String? = null,
    content: (@Composable (AgentCreateFormState) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    var name by remember { mutableStateOf("") }
    var chosenType by remember { mutableStateOf("") }
    val type = chosenType.ifEmpty { agentTypes.firstOrNull()?.value.orEmpty() }
    var agentDescription by remember { mutableStateOf("") }
    val isValid = name.isNotBlank() && type.isNotEmpty()
    val submit = {
        if (isValid && !isSubmitting) onSubmit(AgentCreateSubmission(name.trim(), type, agentDescription.trim().ifEmpty { null }))
    }
    if (content != null) {
        Box(modifier = modifier) {
            content(
                AgentCreateFormState(
                    agentTypes = agentTypes,
                    name = name,
                    onNameChange = { name = it },
                    type = type,
                    onTypeChange = { chosenType = it },
                    description = agentDescription,
                    onDescriptionChange = { agentDescription = it },
                    isValid = isValid,
                    isSubmitting = isSubmitting,
                    error = error,
                    submit = submit,
                    onCancel = onCancel,
                ),
            )
        }
        return
    }
    AdminSection(
        block = "agent-create",
        modifier = modifier,
        title = title ?: i18n.resolve("admin.agents.create.title"),
        description = description,
    ) {
        error?.let { AdminErrorBanner(it) }
        AdminTextField("agent-create", "name", i18n.resolve("admin.agents.create.fields.name"), name, { name = it }, true)
        AdminSelect(
            label = i18n.resolve("admin.agents.create.fields.type"),
            options = agentTypes,
            value = type,
            onChange = { chosenType = it },
            testTag = adminTag("agent-create", "field", "type"),
            modifier = Modifier.fillMaxWidth(),
        )
        AdminTextField(
            "agent-create",
            "description",
            i18n.resolve("admin.agents.create.fields.description"),
            agentDescription,
            { agentDescription = it },
        )
        AdminFormActions(
            block = "agent-create",
            submitLabel = i18n.resolve("admin.agents.create.submit"),
            canSubmit = isValid,
            isSubmitting = isSubmitting,
            onCancel = onCancel,
            onSubmit = submit,
        )
    }
}

/**
 * Registers an agent through the agent management operations, in [ouId] or, when it is `null`, the
 * signed-in user's organization unit. Offers the agent types the server lists.
 *
 * @param owner The identifier of the agent's owner, if any.
 * @param onCreated Receives the agent as the server stored it.
 * @param content Renders custom UI in place of the default form. See [BaseAgentCreate].
 */
@Composable
fun AgentCreate(
    modifier: Modifier = Modifier,
    ouId: String? = null,
    owner: String? = null,
    onCreated: ((Agent) -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    title: String? = null,
    description: String? = null,
    content: (@Composable (AgentCreateFormState) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    val scope = rememberCoroutineScope()
    val resolvedOuId = rememberDefaultOuId(ouId)
    val agentTypes = rememberGetAgentTypes(limit = 100)
    val create = rememberCreateAgent()
    BaseAgentCreate(
        agentTypes =
            agentTypes.data
                ?.types
                .orEmpty()
                .map { AdminOption(it.displayName, it.handle) },
        onSubmit = { submission ->
            val targetOuId = resolvedOuId ?: return@BaseAgentCreate
            scope.launch {
                create
                    .mutate(
                        CreateAgentRequest(
                            ouId = targetOuId,
                            type = submission.type,
                            name = submission.name,
                            description = submission.description,
                            owner = owner,
                        ),
                    )?.let { onCreated?.invoke(it) }
            }
        },
        modifier = modifier,
        error =
            if (resolvedOuId == null) {
                i18n.resolve("admin.common.errors.no_ou")
            } else {
                toManagementErrorMessage(i18n, create.error, "admin.agents.create.error")
            },
        isSubmitting = create.isLoading,
        onCancel = onCancel,
        title = title,
        description = description,
        content = content,
    )
}
