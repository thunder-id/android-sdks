// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.thunderid.android.api.account.PersonalAgentFormValues
import dev.thunderid.android.api.account.PersonalAgentGrantTypes
import dev.thunderid.android.api.account.grantTypesOf
import dev.thunderid.android.api.account.oauthConfig
import dev.thunderid.android.api.management.Agent
import dev.thunderid.android.api.management.SuspensionAppliedBy
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.components.management.AdminDetailItem
import dev.thunderid.compose.components.management.AdminFormActions
import dev.thunderid.compose.components.management.AdminPanel
import dev.thunderid.compose.components.management.AdminTextField
import dev.thunderid.compose.components.management.accessStatusLabel
import dev.thunderid.compose.components.management.adminTag
import dev.thunderid.compose.components.management.stringifyAttribute
import dev.thunderid.compose.i18n.ThunderIDI18n

internal const val PERSONAL_AGENT_DETAILS = "personal-agent-details"

/** An action of [BasePersonalAgentDetails] waiting for the user to confirm it. */
internal enum class PersonalAgentConfirmation(
    val action: String,
    val confirmKey: String,
) {
    SUSPEND("suspend", "user.agents.suspend.confirm"),
    DELETE("delete", "user.agents.delete.confirm"),
}

/** An open edit form, and the agent it was opened for. */
internal data class PersonalAgentDraft(
    val agentId: String,
    val values: PersonalAgentFormValues,
)

/** The parties whose suspension the details explain, in the order they are shown. */
private val EXPLAINED_PARTIES =
    listOf(SuspensionAppliedBy.OWNER, SuspensionAppliedBy.ORGANIZATION, SuspensionAppliedBy.CASCADE)

/** One translated notice per suspension in force, saying who placed it. */
internal fun personalAgentSuspensionNotices(
    i18n: ThunderIDI18n,
    agent: Agent,
): List<String> {
    val parties = agent.status?.suspendedBy.orEmpty()
    return EXPLAINED_PARTIES.filter { it in parties }.map { i18n.resolve("user.agents.suspension.$it") }
}

/** The translated label of a grant type, or the grant type itself when it has none. */
internal fun personalAgentGrantTypeLabel(
    i18n: ThunderIDI18n,
    grantType: String,
): String = PersonalAgentGrantTypes.LABEL_KEYS[grantType]?.let(i18n::resolve) ?: grantType

/** The overview rows of a personal agent: its status, client, grant types and redirect URIs. */
internal fun personalAgentOverview(
    i18n: ThunderIDI18n,
    agent: Agent,
): List<AdminDetailItem> {
    val none = i18n.resolve("user.agents.details.none")
    val oauth = agent.oauthConfig
    val grantTypes = grantTypesOf(oauth).map { personalAgentGrantTypeLabel(i18n, it) }
    val redirectUris = oauth?.redirectUris.orEmpty()
    return listOf(
        AdminDetailItem(i18n.resolve("user.agents.list.columns.status"), accessStatusLabel(i18n, agent.status?.value) ?: none),
        AdminDetailItem(i18n.resolve("user.agents.details.client_id"), agent.clientId ?: none, copyable = agent.clientId != null),
        AdminDetailItem(i18n.resolve("user.agents.details.grant_types"), grantTypes.ifEmpty { listOf(none) }.joinToString(", ")),
        AdminDetailItem(i18n.resolve("user.agents.details.redirect_uris"), redirectUris.ifEmpty { listOf(none) }.joinToString("\n")),
    )
}

/** The agent's attributes as rows, sorted by name, with any value that is not text shown as JSON. */
internal fun personalAgentAttributes(agent: Agent): List<AdminDetailItem> =
    agent.attributes
        .orEmpty()
        .toSortedMap()
        .map { (name, value) -> AdminDetailItem(name, stringifyAttribute(value)) }

/**
 * The edit form of [BasePersonalAgentDetails]: the name, the agent's other text attributes, the description, the
 * redirect URIs and the grant types.
 */
@Composable
internal fun PersonalAgentEditForm(
    i18n: ThunderIDI18n,
    values: PersonalAgentFormValues,
    onValuesChange: (PersonalAgentFormValues) -> Unit,
    isUpdating: Boolean,
    onCancel: () -> Unit,
    onSave: (PersonalAgentFormValues) -> Unit,
) {
    AdminPanel(
        title = i18n.resolve("user.agents.edit.title"),
        modifier = Modifier.testTag(adminTag(PERSONAL_AGENT_DETAILS, "edit-form")),
    ) {
        AdminTextField(
            block = PERSONAL_AGENT_DETAILS,
            name = "name",
            label = i18n.resolve("user.agents.fields.name"),
            value = values.name,
            onValueChange = { onValuesChange(values.copy(name = it)) },
            required = true,
        )
        values.attributes.keys.sorted().forEach { attribute ->
            AdminTextField(
                block = PERSONAL_AGENT_DETAILS,
                name = "attribute-$attribute",
                label = attribute,
                value = values.attributes[attribute].orEmpty(),
                onValueChange = { onValuesChange(values.copy(attributes = values.attributes + (attribute to it))) },
            )
        }
        AdminTextField(
            block = PERSONAL_AGENT_DETAILS,
            name = "description",
            label = i18n.resolve("user.agents.fields.description"),
            value = values.description,
            onValueChange = { onValuesChange(values.copy(description = it)) },
        )
        OutlinedTextField(
            value = values.redirectUris,
            onValueChange = { onValuesChange(values.copy(redirectUris = it)) },
            label = { Text(i18n.resolve("user.agents.details.redirect_uris")) },
            supportingText = { Text(i18n.resolve("user.agents.fields.redirect_uris_hint")) },
            maxLines = 4,
            modifier = Modifier.fillMaxWidth().testTag(adminTag(PERSONAL_AGENT_DETAILS, "field", "redirectUris")),
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                i18n.resolve("user.agents.details.grant_types"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { heading() },
            )
            PersonalAgentGrantTypes.ALL.forEach { grantType ->
                val checked = grantType in values.grantTypes
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .toggleable(value = checked, enabled = !isUpdating, role = Role.Checkbox) { isOn ->
                                val others = values.grantTypes - grantType
                                onValuesChange(values.copy(grantTypes = if (isOn) others + grantType else others))
                            }.testTag(adminTag(PERSONAL_AGENT_DETAILS, "grant", grantType)),
                ) {
                    Checkbox(checked = checked, onCheckedChange = null, enabled = !isUpdating)
                    Text(personalAgentGrantTypeLabel(i18n, grantType))
                }
            }
        }
        AdminFormActions(
            block = PERSONAL_AGENT_DETAILS,
            submitLabel = i18n.resolve("user.agents.edit.save"),
            canSubmit = values.isValid,
            isSubmitting = isUpdating,
            onCancel = onCancel,
        ) { if (values.isValid) onSave(values) }
    }
}

/**
 * The overflow menu of [BasePersonalAgentDetails]: the owner's suspension, then delete. A handler that is `null`
 * leaves its item out. The menu shows progress and is disabled while [isBusy].
 */
@Composable
internal fun PersonalAgentActionMenu(
    isBusy: Boolean,
    onUnsuspend: (() -> Unit)?,
    onSuspend: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    val i18n = LocalThunderID.current.i18n
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            enabled = !isBusy,
            modifier = Modifier.testTag(adminTag(PERSONAL_AGENT_DETAILS, "menu")),
        ) {
            if (isBusy) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = i18n.resolve("user.agents.actions.menu"),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            val choose = { action: () -> Unit ->
                expanded = false
                action()
            }
            onUnsuspend?.let {
                DropdownMenuItem(
                    text = { Text(i18n.resolve("user.agents.unsuspend.action")) },
                    onClick = { choose(it) },
                    modifier = Modifier.testTag(adminTag(PERSONAL_AGENT_DETAILS, "unsuspend")),
                )
            }
            onSuspend?.let {
                DropdownMenuItem(
                    text = { Text(i18n.resolve("user.agents.suspend.action")) },
                    onClick = { choose(it) },
                    modifier = Modifier.testTag(adminTag(PERSONAL_AGENT_DETAILS, "suspend")),
                )
            }
            onDelete?.let {
                if (onUnsuspend != null || onSuspend != null) HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(i18n.resolve("user.agents.delete.action"), color = MaterialTheme.colorScheme.error) },
                    onClick = { choose(it) },
                    modifier = Modifier.testTag(adminTag(PERSONAL_AGENT_DETAILS, "delete")),
                )
            }
        }
    }
}
