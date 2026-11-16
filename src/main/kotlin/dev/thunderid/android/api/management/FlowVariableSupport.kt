// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.management

import com.google.gson.Gson
import java.util.regex.PatternSyntaxException
import kotlin.math.abs

private const val VARIABLES_NODE_TYPE = "VARIABLES"
private const val LARGEST_EXACT_WHOLE = 1e15

private val EMAIL_PATTERN = Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""")
private val PHONE_PATTERN = Regex("""^\+?[0-9\s\-()]{6,20}$""")
private val DATE_PATTERN = Regex("""^\d{4}-\d{2}-\d{2}$""")

private val gson = Gson()

/**
 * The variables a flow declares. A flow shared with the organization unit it was requested for carries them in
 * [FlowDefinition.variables]; a flow the unit owns declares them on its `VARIABLES` node. Returns an empty list
 * for `null`, and leaves out an entry that names no identifier or type.
 */
fun getFlowVariableDefinitions(flow: FlowDefinition?): List<FlowVariableDefinition> {
    if (flow == null) return emptyList()
    val variables: List<FlowVariableDefinition?>? = flow.variables
    if (variables != null) return variables.filterNotNull().filter { isDeclared(it.identifier, it.type) }
    // A flow shared with the unit comes back without its graph.
    val nodes: List<Map<String, Any?>>? = flow.nodes
    val inputs = nodes.orEmpty().firstOrNull { it["type"] == VARIABLES_NODE_TYPE }?.get("inputs") as? List<*>
    return inputs
        .orEmpty()
        .filterIsInstance<Map<*, *>>()
        .filter { it["identifier"] is String && it["type"] is String }
        .map { gson.fromJson(gson.toJsonTree(it), FlowVariableDefinition::class.java) }
}

/**
 * The choices a `SELECT` or `CONNECTION_SELECT` variable offers, narrowed by [restriction]. A `SELECT` offers its
 * declared options. A `CONNECTION_SELECT` offers the [connections] of its category and vendors, by name, storing
 * the connection's id. Any other variable offers none.
 */
fun getFlowVariableOptions(
    definition: FlowVariableDefinition,
    connections: List<ConnectionInstance> = emptyList(),
    restriction: FlowVariableOptionRestriction = FlowVariableOptionRestriction(),
): List<FlowVariableOption> {
    val permitted = { option: FlowVariableOption ->
        (restriction.allowedValues == null || option.value in restriction.allowedValues) &&
            option.value !in restriction.excludedValues.orEmpty()
    }
    return when (definition.type) {
        FlowVariableType.SELECT -> {
            definition.options
                .orEmpty()
                .map { FlowVariableOption(flowVariableText(it), flowVariableText(it)) }
                .filter(permitted)
        }

        FlowVariableType.CONNECTION_SELECT -> {
            val category = definition.category?.let(::normalizeName)
            val vendors = definition.connectionTypes.orEmpty().map(::normalizeName)
            connections
                .filter { connection ->
                    (category == null || connection.categories.orEmpty().any { normalizeName(it.value) == category }) &&
                        (vendors.isEmpty() || connection.type?.let { normalizeName(it.value) in vendors } == true)
                }.map { FlowVariableOption(it.name, it.id) }
                .filter(permitted)
        }

        else -> {
            emptyList()
        }
    }
}

/**
 * What the organization unit [overlay] was resolved for may do with [definition]. Without an overlay, or for a
 * unit that owns the flow, the variable is editable and keeps its own default. A unit the flow was shared with
 * may set its own value only where the owner's rule allows it, and starts from the value the owner pinned.
 */
fun resolveFlowVariableRule(
    definition: FlowVariableDefinition,
    overlay: ResolvedOverlay? = null,
): ResolvedFlowVariableRule {
    if (overlay == null) return ResolvedFlowVariableRule(defaultValue = definition.defaultValue, editable = true)
    val rule = overlay.rules?.get("variables.${definition.identifier}")
    val pinned = rule?.value?.firstOrNull()
    val owned = overlay.isOwnedByOU ?: false
    return ResolvedFlowVariableRule(
        defaultValue = if (!owned && pinned != null) toVariableValue(definition, pinned) else definition.defaultValue,
        editable = owned || rule?.editable == true,
        allowedValues = if (owned) null else rule?.allowedValues,
        excludedValues = if (owned) null else rule?.excludedValues,
    )
}

/**
 * Checks [value] against what [definition] accepts, as the server will. Returns `null` when it is valid. A
 * boolean variable may be left unset; any other needs a value.
 */
fun validateFlowVariableValue(
    definition: FlowVariableDefinition,
    value: Any?,
): FlowVariableValidationError? {
    if (definition.type == FlowVariableType.BOOLEAN_INPUT) {
        return if (value == null || value is Boolean) null else refused(FlowVariableValidationErrorCode.INVALID)
    }
    if (value == null || value == "") return refused(FlowVariableValidationErrorCode.REQUIRED)
    val text = flowVariableText(value)
    return when (definition.type) {
        FlowVariableType.NUMBER_INPUT -> {
            val numeric = (value as? Number)?.toDouble() ?: text.trim().toDoubleOrNull()
            when {
                numeric == null || numeric.isNaN() -> refused(FlowVariableValidationErrorCode.INVALID)
                definition.min != null && numeric < definition.min -> refused(FlowVariableValidationErrorCode.MIN)
                definition.max != null && numeric > definition.max -> refused(FlowVariableValidationErrorCode.MAX)
                else -> null
            }
        }

        FlowVariableType.SELECT -> {
            if (definition.options.orEmpty().any { flowVariableText(it) == text }) {
                null
            } else {
                refused(FlowVariableValidationErrorCode.NOT_OPTION)
            }
        }

        FlowVariableType.EMAIL_INPUT -> {
            if (EMAIL_PATTERN.matches(text)) null else refused(FlowVariableValidationErrorCode.INVALID)
        }

        FlowVariableType.PHONE_INPUT -> {
            if (PHONE_PATTERN.matches(text)) null else refused(FlowVariableValidationErrorCode.INVALID)
        }

        FlowVariableType.DATE_INPUT -> {
            if (DATE_PATTERN.matches(text)) null else refused(FlowVariableValidationErrorCode.INVALID)
        }

        FlowVariableType.CONNECTION_SELECT -> {
            if (value is String) null else refused(FlowVariableValidationErrorCode.INVALID)
        }

        else -> {
            validateText(definition.validation.orEmpty(), text)
        }
    }
}

/**
 * A flow variable value as text: a whole number without a fractional part, since the server's numbers arrive as
 * [Double], and an empty string for `null`.
 */
internal fun flowVariableText(value: Any?): String =
    when (value) {
        null -> ""
        is Double -> if (value % 1.0 == 0.0 && abs(value) < LARGEST_EXACT_WHOLE) value.toLong().toString() else value.toString()
        is Float -> flowVariableText(value.toDouble())
        else -> value.toString()
    }

/** Whether two flow variable values are the same, comparing numbers by value whatever their type. */
internal fun sameFlowVariableValue(
    first: Any?,
    second: Any?,
): Boolean = if (first is Number && second is Number) first.toDouble() == second.toDouble() else first == second

private fun validateText(
    rules: List<FlowVariableValidationRule>,
    text: String,
): FlowVariableValidationError? {
    for (rule in rules) {
        val limit = (rule.value as? Number)?.toDouble() ?: rule.value?.toString()?.toDoubleOrNull()
        when {
            rule.type == "minLength" && limit != null && text.length < limit -> {
                return FlowVariableValidationError(FlowVariableValidationErrorCode.MIN_LENGTH, rule.message)
            }

            rule.type == "maxLength" && limit != null && text.length > limit -> {
                return FlowVariableValidationError(FlowVariableValidationErrorCode.MAX_LENGTH, rule.message)
            }

            rule.type == "regex" && !matchesPattern(rule.value?.toString().orEmpty(), text) -> {
                return FlowVariableValidationError(FlowVariableValidationErrorCode.PATTERN, rule.message)
            }
        }
    }
    return null
}

private fun matchesPattern(
    pattern: String,
    text: String,
): Boolean =
    try {
        Regex(pattern).containsMatchIn(text)
    } catch (e: PatternSyntaxException) {
        // A pattern the platform cannot compile is the server's to judge.
        true
    }

private fun refused(code: FlowVariableValidationErrorCode) = FlowVariableValidationError(code)

private fun normalizeName(value: String): String = value.lowercase().replace('_', '-')

private fun isDeclared(
    identifier: String?,
    type: String?,
): Boolean = identifier != null && type != null

private fun toVariableValue(
    definition: FlowVariableDefinition,
    raw: String,
): Any =
    when (definition.type) {
        FlowVariableType.BOOLEAN_INPUT -> raw == "true"
        FlowVariableType.NUMBER_INPUT -> raw.trim().toDoubleOrNull() ?: raw
        FlowVariableType.SELECT -> definition.options.orEmpty().firstOrNull { flowVariableText(it) == raw } ?: raw
        else -> raw
    }
