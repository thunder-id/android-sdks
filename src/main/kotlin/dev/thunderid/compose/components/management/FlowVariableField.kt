// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.management

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.thunderid.android.api.management.FlowVariableDefinition
import dev.thunderid.android.api.management.FlowVariableOption
import dev.thunderid.android.api.management.FlowVariableType
import dev.thunderid.android.api.management.flowVariableText
import dev.thunderid.android.api.management.sameFlowVariableValue
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.i18n.ThunderIDI18n

private const val FIELD_BLOCK = "flow-variables"

/**
 * The value a text control's [raw] input stands for. A `NUMBER_INPUT` becomes a number when the text reads back
 * the same, so a value being typed, such as `1.`, stays as it is until it is complete.
 */
internal fun parseFlowVariableInput(
    definition: FlowVariableDefinition,
    raw: String,
): Any =
    if (definition.type == FlowVariableType.NUMBER_INPUT) {
        raw.toDoubleOrNull()?.takeIf { flowVariableText(it) == raw } ?: raw
    } else {
        raw
    }

/** How a flow variable value reads: a boolean as on or off, a choice by its label, anything else as text. */
internal fun displayFlowVariableValue(
    i18n: ThunderIDI18n,
    value: Any?,
    options: List<FlowVariableOption>,
): String =
    when (value) {
        null -> ""
        is Boolean -> i18n.resolve(if (value) "admin.flowVariables.on" else "admin.flowVariables.off")
        else -> flowVariableText(value).let { text -> options.firstOrNull { it.value == text }?.label ?: text }
    }

private fun keyboardType(type: String): KeyboardType =
    when (type) {
        FlowVariableType.NUMBER_INPUT -> KeyboardType.Number
        FlowVariableType.EMAIL_INPUT -> KeyboardType.Email
        FlowVariableType.PHONE_INPUT -> KeyboardType.Phone
        else -> KeyboardType.Text
    }

/**
 * One flow variable: its label and description, and the control its type calls for. A variable the organization
 * unit cannot set shows the value the owner set instead of a control. Carries the test tag
 * `thunderid-admin-flow-variables-field-<identifier>`, and its control `…-input-<identifier>`.
 *
 * @param value The current value.
 * @param defaultValue The value the variable falls back to. The field is marked customized while [value] differs.
 * @param editable Whether the organization unit may set the value.
 * @param error A translated message to show under the control.
 * @param options The choices of a `SELECT` or `CONNECTION_SELECT` variable.
 * @param ownerName Who set a value the unit cannot change.
 * @param hint Extra guidance under the control.
 * @param action Controls rendered next to the label.
 */
@Composable
fun FlowVariableField(
    definition: FlowVariableDefinition,
    value: Any?,
    onChange: (Any) -> Unit,
    modifier: Modifier = Modifier,
    defaultValue: Any? = null,
    editable: Boolean = true,
    enabled: Boolean = true,
    error: String? = null,
    options: List<FlowVariableOption> = emptyList(),
    ownerName: String? = null,
    hint: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    val id = definition.identifier
    val label = definition.label ?: id
    val isDefault = sameFlowVariableValue(value, defaultValue)
    val shownDefault = displayFlowVariableValue(i18n, defaultValue, options)
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (editable) 0.4f else 0.2f),
        modifier = modifier.fillMaxWidth().testTag(adminTag(FIELD_BLOCK, "field", id)),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    definition.description?.let { AdminMuted(it) }
                }
                if (editable && !isDefault) {
                    Text(
                        i18n.resolve("admin.flowVariables.status.customized"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag(adminTag(FIELD_BLOCK, "status", id)),
                    )
                }
                action?.invoke()
            }
            if (editable) {
                FlowVariableControl(definition, value, onChange, defaultValue, enabled, error, options, label, shownDefault)
                hint?.invoke()
                if (!isDefault && defaultValue != null) {
                    AdminMuted(i18n.translate("admin.flowVariables.default_hint", "default" to shownDefault))
                }
            } else {
                val owner = ownerName ?: i18n.resolve("admin.flowVariables.owner_fallback")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AdminMuted(i18n.translate("admin.flowVariables.locked", "owner" to owner))
                    Text(
                        displayFlowVariableValue(i18n, value, options),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f).testTag(adminTag(FIELD_BLOCK, "value", id)),
                    )
                    if (definition.type == FlowVariableType.BOOLEAN_INPUT) {
                        Switch(
                            checked = value == true,
                            onCheckedChange = null,
                            enabled = false,
                            modifier = Modifier.semantics { contentDescription = label },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FlowVariableControl(
    definition: FlowVariableDefinition,
    value: Any?,
    onChange: (Any) -> Unit,
    defaultValue: Any?,
    enabled: Boolean,
    error: String?,
    options: List<FlowVariableOption>,
    label: String,
    shownDefault: String,
) {
    val i18n = LocalThunderID.current.i18n
    val inputTag = adminTag(FIELD_BLOCK, "input", definition.identifier)
    val hasDefault = defaultValue != null && defaultValue != ""
    when (definition.type) {
        FlowVariableType.BOOLEAN_INPUT -> {
            Switch(
                checked = value == true,
                onCheckedChange = onChange,
                enabled = enabled,
                modifier = Modifier.testTag(inputTag).semantics { contentDescription = label },
            )
        }

        FlowVariableType.SELECT, FlowVariableType.CONNECTION_SELECT -> {
            // The first entry, the default, clears the choice.
            val useDefault = AdminOption(i18n.translate("admin.flowVariables.use_default", "default" to shownDefault), "")
            val choices = listOfNotNull(useDefault.takeIf { hasDefault }) + options.map { AdminOption(it.label, it.value) }
            AdminSelect(
                label = label,
                options = choices,
                value = if (value == null || sameFlowVariableValue(value, defaultValue)) "" else flowVariableText(value),
                onChange = { chosen ->
                    if (chosen == "" && defaultValue != null) {
                        onChange(defaultValue)
                    } else {
                        onChange(definition.options.orEmpty().firstOrNull { flowVariableText(it) == chosen } ?: chosen)
                    }
                },
                testTag = inputTag,
                placeholder = i18n.resolve("admin.flowVariables.select_placeholder"),
                emptyText = i18n.resolve("admin.flowVariables.no_options"),
                enabled = enabled,
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }

        else -> {
            OutlinedTextField(
                value = flowVariableText(value),
                onValueChange = { onChange(parseFlowVariableInput(definition, it)) },
                placeholder = { Text(if (hasDefault) shownDefault else i18n.resolve("admin.flowVariables.enter_value")) },
                isError = error != null,
                supportingText = if (error != null) ({ Text(error) }) else null,
                singleLine = true,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType(definition.type)),
                modifier = Modifier.fillMaxWidth().testTag(inputTag).semantics { contentDescription = label },
            )
        }
    }
}
