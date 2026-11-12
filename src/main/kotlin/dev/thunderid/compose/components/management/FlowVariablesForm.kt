// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.management

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.thunderid.android.api.management.ConnectionInstance
import dev.thunderid.android.api.management.FlowVariableDefinition
import dev.thunderid.android.api.management.FlowVariableOption
import dev.thunderid.android.api.management.FlowVariableOptionRestriction
import dev.thunderid.android.api.management.FlowVariableType
import dev.thunderid.android.api.management.FlowVariableValidationError
import dev.thunderid.android.api.management.ResolvedFlowVariableRule
import dev.thunderid.android.api.management.ResolvedOverlay
import dev.thunderid.android.api.management.getFlowVariableOptions
import dev.thunderid.android.api.management.resolveFlowVariableRule
import dev.thunderid.android.api.management.sameFlowVariableValue
import dev.thunderid.android.api.management.validateFlowVariableValue
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.i18n.ThunderIDI18n
import dev.thunderid.compose.management.rememberFlowVariables
import kotlinx.coroutines.launch

private const val BLOCK = "flow-variables"

/**
 * What [BaseFlowVariables] hands its `content` lambda.
 *
 * @property values The current value of each variable, keyed by identifier.
 * @property errors A translated message for each variable whose value is refused, once it was changed or a
 *   submission was attempted.
 * @property isValid Whether every variable the organization unit may set has a value it accepts.
 * @property onValueChange Sets one variable's value, by identifier.
 * @property submit Hands the values and the overrides to `onSubmit`. Shows every error instead while a value is
 *   refused.
 * @property resetToDefaults Puts every variable back to its default.
 * @property getOptions The choices a variable offers the organization unit.
 * @property getRule What the organization unit may do with a variable.
 */
data class FlowVariablesFormState(
    val variables: List<FlowVariableDefinition>,
    val values: Map<String, Any>,
    val errors: Map<String, String>,
    val isValid: Boolean,
    val isSubmitting: Boolean,
    val error: String?,
    val onValueChange: (String, Any) -> Unit,
    val submit: () -> Unit,
    val resetToDefaults: () -> Unit,
    val onCancel: (() -> Unit)?,
    val getOptions: (FlowVariableDefinition) -> List<FlowVariableOption>,
    val getRule: (FlowVariableDefinition) -> ResolvedFlowVariableRule,
)

/**
 * The value each variable starts from: the unit's own value where it may set one, otherwise the default, or the
 * value the owner pinned.
 */
internal fun flowVariableStartingValues(
    variables: List<FlowVariableDefinition>,
    overlay: ResolvedOverlay?,
    initialValues: Map<String, Any>,
): Map<String, Any> =
    variables
        .mapNotNull { definition ->
            val rule = resolveFlowVariableRule(definition, overlay)
            val chosen = if (rule.editable) initialValues[definition.identifier] ?: rule.defaultValue else rule.defaultValue
            chosen?.let { definition.identifier to it }
        }.toMap()

/** The default of each variable that has one. */
internal fun flowVariableDefaults(
    variables: List<FlowVariableDefinition>,
    overlay: ResolvedOverlay?,
): Map<String, Any> =
    variables
        .mapNotNull { definition -> resolveFlowVariableRule(definition, overlay).defaultValue?.let { definition.identifier to it } }
        .toMap()

/** Why each variable the unit may set refuses its value. A variable it cannot set is not checked. */
internal fun flowVariableProblems(
    variables: List<FlowVariableDefinition>,
    overlay: ResolvedOverlay?,
    values: Map<String, Any>,
): Map<String, FlowVariableValidationError> =
    variables
        .filter { resolveFlowVariableRule(it, overlay).editable }
        .mapNotNull { definition ->
            validateFlowVariableValue(definition, values[definition.identifier])?.let { definition.identifier to it }
        }.toMap()

/**
 * The values to save: only those the unit may set that differ from the default, so the rest keep following it. A
 * number typed as text is sent as a number.
 */
internal fun flowVariableOverrides(
    variables: List<FlowVariableDefinition>,
    overlay: ResolvedOverlay?,
    values: Map<String, Any>,
): Map<String, Any> =
    variables
        .mapNotNull { definition ->
            val rule = resolveFlowVariableRule(definition, overlay)
            val value = values[definition.identifier]
            if (!rule.editable || value == null || sameFlowVariableValue(value, rule.defaultValue)) {
                null
            } else if (definition.type == FlowVariableType.NUMBER_INPUT && value is String) {
                definition.identifier to (value.trim().toDoubleOrNull() ?: value)
            } else {
                definition.identifier to value
            }
        }.toMap()

/**
 * The choices a variable offers. A variable the unit cannot set shows its pinned value, which a restriction might
 * hide from the choices, so only an editable one is narrowed.
 */
internal fun flowVariableChoices(
    definition: FlowVariableDefinition,
    overlay: ResolvedOverlay?,
    connections: List<ConnectionInstance>,
): List<FlowVariableOption> {
    val rule = resolveFlowVariableRule(definition, overlay)
    val restriction =
        if (rule.editable) FlowVariableOptionRestriction(rule.allowedValues, rule.excludedValues) else FlowVariableOptionRestriction()
    return getFlowVariableOptions(definition, connections, restriction)
}

/** The variable's own message for a refused value, or the translated one for its code. */
internal fun flowVariableErrorMessage(
    i18n: ThunderIDI18n,
    problem: FlowVariableValidationError,
): String = problem.message ?: i18n.resolve("admin.flowVariables.errors.${problem.code.value}")

/**
 * Presents a form over the variables a flow declares for an organization unit, and hands the values to [onSubmit].
 * Holds no data of its own: pass the [variables], the unit's [initialValues], the [overlay] rules that say which
 * variables it may set, and the [connections] a `CONNECTION_SELECT` variable can take. The form resets when they
 * change.
 *
 * Each variable is checked as the server will check it. A variable the unit cannot set shows the owner's value
 * and is never submitted.
 *
 * @param error A translated message to show above the form.
 * @param onSubmit Receives every value and the overrides: the values that differ from their default. The save
 *   controls are shown only when it is set.
 * @param onChange Receives the values after each change.
 * @param onCancel Shows a cancel button that calls it when set.
 * @param onCreateConnection Offers to create a connection under a `CONNECTION_SELECT` variable when set.
 * @param ouName Names the organization unit in the notices. Falls back to a generic name.
 * @param ownerName Names who set a value the unit cannot change. Falls back to a generic name.
 * @param readOnly Shows the values without letting them change.
 * @param showScopeNotice Explains that the values apply to this organization unit only.
 * @param title Heading of the form. Pass an empty string to hide it.
 * @param renderField Wraps or replaces each field. Receives the built-in field, the variable and its position.
 * @param fieldAction Controls rendered next to a variable's label.
 * @param content Renders custom UI in place of the default form, from the variables, the values with their
 *   setter and errors, the submit and reset actions, and the submission state.
 */
@Composable
fun BaseFlowVariables(
    variables: List<FlowVariableDefinition>,
    modifier: Modifier = Modifier,
    connections: List<ConnectionInstance> = emptyList(),
    initialValues: Map<String, Any> = emptyMap(),
    overlay: ResolvedOverlay? = null,
    error: String? = null,
    isSubmitting: Boolean = false,
    onSubmit: ((values: Map<String, Any>, overrides: Map<String, Any>) -> Unit)? = null,
    onChange: ((Map<String, Any>) -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    onCreateConnection: (() -> Unit)? = null,
    ouName: String? = null,
    ownerName: String? = null,
    readOnly: Boolean = false,
    showScopeNotice: Boolean = false,
    title: String? = null,
    description: String? = null,
    renderField: (@Composable (field: @Composable () -> Unit, definition: FlowVariableDefinition, index: Int) -> Unit)? = null,
    fieldAction: (@Composable (definition: FlowVariableDefinition, index: Int) -> Unit)? = null,
    content: (@Composable (FlowVariablesFormState) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    // The form holds the starting values; only a change to what they come from replaces what was typed.
    val startingValues = remember(variables, overlay, initialValues) { flowVariableStartingValues(variables, overlay, initialValues) }
    var values by remember(startingValues) { mutableStateOf(startingValues) }
    var touched by remember(startingValues) { mutableStateOf(emptySet<String>()) }
    var submitted by remember(startingValues) { mutableStateOf(false) }

    val problems = flowVariableProblems(variables, overlay, values)
    val isValid = problems.isEmpty()
    val errors =
        problems
            .filterKeys { submitted || it in touched }
            .mapValues { (_, problem) -> flowVariableErrorMessage(i18n, problem) }
    val getRule = { definition: FlowVariableDefinition -> resolveFlowVariableRule(definition, overlay) }
    val getOptions = { definition: FlowVariableDefinition -> flowVariableChoices(definition, overlay, connections) }
    val onValueChange: (String, Any) -> Unit = { identifier, value ->
        values = values + (identifier to value)
        touched = touched + identifier
        onChange?.invoke(values)
    }
    val resetToDefaults: () -> Unit = {
        values = flowVariableDefaults(variables, overlay)
        touched = emptySet()
        submitted = false
        onChange?.invoke(values)
    }
    val submit: () -> Unit = {
        submitted = true
        if (isValid) onSubmit?.invoke(values, flowVariableOverrides(variables, overlay, values))
    }

    if (content != null) {
        Box(modifier = modifier) {
            content(
                FlowVariablesFormState(
                    variables = variables,
                    values = values,
                    errors = errors,
                    isValid = isValid,
                    isSubmitting = isSubmitting,
                    error = error,
                    onValueChange = onValueChange,
                    submit = submit,
                    resetToDefaults = resetToDefaults,
                    onCancel = onCancel,
                    getOptions = getOptions,
                    getRule = getRule,
                ),
            )
        }
        return
    }

    val ou = ouName ?: i18n.resolve("admin.flowVariables.ou_fallback")
    AdminSection(
        block = BLOCK,
        modifier = modifier,
        title = title ?: i18n.resolve("admin.flowVariables.title"),
        description = description,
    ) {
        if (showScopeNotice) FlowVariablesScopeNotice(i18n, ou)
        error?.let { AdminErrorBanner(it) }
        if (variables.isEmpty()) AdminMuted(i18n.resolve("admin.flowVariables.empty"))
        variables.forEachIndexed { index, definition ->
            val rule = getRule(definition)
            val field: @Composable () -> Unit = {
                FlowVariableField(
                    definition = definition,
                    value = values[definition.identifier],
                    onChange = { onValueChange(definition.identifier, it) },
                    defaultValue = rule.defaultValue,
                    editable = rule.editable,
                    enabled = !readOnly && !isSubmitting,
                    error = errors[definition.identifier],
                    options = getOptions(definition),
                    ownerName = ownerName,
                    hint =
                        if (definition.type == FlowVariableType.CONNECTION_SELECT && rule.editable) {
                            { ConnectionHint(definition.identifier, ou, onCreateConnection) }
                        } else {
                            null
                        },
                    action = if (fieldAction != null) ({ fieldAction(definition, index) }) else null,
                )
            }
            if (renderField != null) renderField(field, definition, index) else field()
        }
        if (onSubmit != null && !readOnly && variables.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = resetToDefaults, enabled = !isSubmitting, modifier = Modifier.testTag(adminTag(BLOCK, "reset"))) {
                    Text(i18n.resolve("admin.flowVariables.reset_defaults"))
                }
                Spacer(modifier = Modifier.weight(1f))
                onCancel?.let {
                    TextButton(onClick = it, enabled = !isSubmitting, modifier = Modifier.testTag(adminTag(BLOCK, "cancel"))) {
                        Text(i18n.resolve("admin.common.actions.cancel"))
                    }
                }
                Button(onClick = submit, enabled = !isSubmitting, modifier = Modifier.testTag(adminTag(BLOCK, "submit"))) {
                    Text(i18n.resolve("admin.flowVariables.submit"))
                }
            }
        }
    }
}

@Composable
private fun FlowVariablesScopeNotice(
    i18n: ThunderIDI18n,
    ou: String,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().testTag(adminTag(BLOCK, "notice")),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                i18n.translate("admin.flowVariables.notice_title", "ou" to ou),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(i18n.translate("admin.flowVariables.notice_body", "ou" to ou), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ConnectionHint(
    identifier: String,
    ou: String,
    onCreateConnection: (() -> Unit)?,
) {
    val i18n = LocalThunderID.current.i18n
    Column {
        AdminMuted(i18n.translate("admin.flowVariables.connections_hint", "ou" to ou))
        onCreateConnection?.let {
            TextButton(onClick = it, modifier = Modifier.testTag(adminTag(BLOCK, "create-connection", identifier))) {
                Text(i18n.resolve("admin.flowVariables.create_connection"))
            }
        }
    }
}

/**
 * Edits the variables the flow [flowId] shares with the organization unit [ouId]: loads what the flow declares
 * for the unit, the values the unit has set and what it may change, and saves the unit's overrides. Only the
 * values that differ from their default are saved, so the rest keep following it.
 *
 * Use [rememberFlowVariables] to build the form with any UI instead.
 *
 * @param onSaved Receives the overrides as the server stored them.
 * @param content Renders custom UI in place of the default form. See [BaseFlowVariables].
 */
@Composable
fun FlowVariablesForm(
    flowId: String,
    ouId: String,
    modifier: Modifier = Modifier,
    onSaved: ((Map<String, Any>) -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    onCreateConnection: (() -> Unit)? = null,
    ouName: String? = null,
    ownerName: String? = null,
    readOnly: Boolean = false,
    showScopeNotice: Boolean = false,
    title: String? = null,
    description: String? = null,
    renderField: (@Composable (field: @Composable () -> Unit, definition: FlowVariableDefinition, index: Int) -> Unit)? = null,
    fieldAction: (@Composable (definition: FlowVariableDefinition, index: Int) -> Unit)? = null,
    content: (@Composable (FlowVariablesFormState) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    val scope = rememberCoroutineScope()
    val state = rememberFlowVariables(flowId, ouId, onSaved = onSaved)
    val loadError = state.error
    if (!state.isLoaded && loadError == null) {
        AdminLoading(modifier = modifier)
        return
    }
    BaseFlowVariables(
        variables = state.definitions,
        modifier = modifier,
        connections = state.connections,
        initialValues = state.overrides,
        overlay = state.overlay,
        error =
            if (loadError != null) {
                toManagementErrorMessage(i18n, loadError, "admin.flowVariables.load_error")
            } else {
                toManagementErrorMessage(i18n, state.saveError, "admin.flowVariables.error")
            },
        isSubmitting = state.isSaving,
        onSubmit = { _, overrides -> scope.launch { state.save(overrides) } },
        onCancel = onCancel,
        onCreateConnection = onCreateConnection,
        ouName = ouName,
        ownerName = ownerName,
        readOnly = readOnly,
        showScopeNotice = showScopeNotice,
        title = title,
        description = description,
        renderField = renderField,
        fieldAction = fieldAction,
        content = content,
    )
}
