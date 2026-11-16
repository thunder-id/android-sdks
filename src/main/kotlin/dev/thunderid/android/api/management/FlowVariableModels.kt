// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.management

/**
 * The kinds of flow variable, as the server names them in [FlowVariableDefinition.type]. A kind the SDK does not
 * know is treated as text.
 */
object FlowVariableType {
    const val TEXT_INPUT = "TEXT_INPUT"
    const val EMAIL_INPUT = "EMAIL_INPUT"
    const val PHONE_INPUT = "PHONE_INPUT"
    const val NUMBER_INPUT = "NUMBER_INPUT"
    const val DATE_INPUT = "DATE_INPUT"
    const val BOOLEAN_INPUT = "BOOLEAN_INPUT"
    const val SELECT = "SELECT"
    const val CONNECTION_SELECT = "CONNECTION_SELECT"
}

/**
 * A rule a text variable's value must meet.
 *
 * @property type `regex`, `minLength` or `maxLength`.
 * @property value The pattern, or the length, as a string or a number.
 * @property message Shown in place of the SDK's own message when the rule fails.
 */
data class FlowVariableValidationRule(
    val type: String,
    val value: Any? = null,
    val message: String? = null,
)

/**
 * A variable a flow declares, which a `{{var(name)}}` placeholder in the flow reads.
 *
 * @property identifier Name of the variable.
 * @property type One of [FlowVariableType].
 * @property defaultValue The value the variable has until another is set: a string, a number or a boolean.
 * @property options For a `SELECT`: the values it can take.
 * @property min For a `NUMBER_INPUT`: the lowest value.
 * @property max For a `NUMBER_INPUT`: the highest value.
 * @property category For a `CONNECTION_SELECT`: the kind of connection it takes, such as `IDENTITY_PROVIDER`.
 * @property connectionTypes For a `CONNECTION_SELECT`: limits it to these vendors, such as `google`.
 */
data class FlowVariableDefinition(
    val identifier: String,
    val type: String,
    val label: String? = null,
    val description: String? = null,
    val defaultValue: Any? = null,
    val options: List<Any>? = null,
    val min: Double? = null,
    val max: Double? = null,
    val category: String? = null,
    val connectionTypes: List<String>? = null,
    val validation: List<FlowVariableValidationRule>? = null,
)

/** A choice a `SELECT` or `CONNECTION_SELECT` variable offers: the [label] shown and the [value] stored. */
data class FlowVariableOption(
    val label: String,
    val value: String,
)

/**
 * Narrows the choices of a variable. [allowedValues] keeps only those values when set; [excludedValues] takes
 * values out.
 */
data class FlowVariableOptionRestriction(
    val allowedValues: List<String>? = null,
    val excludedValues: List<String>? = null,
)

/**
 * What an organization unit may do with one flow variable, from the overlay rules that reached it.
 *
 * @property defaultValue The value the variable has until the unit sets its own, or the value it is pinned to
 *   when it cannot set one.
 * @property editable Whether the unit may set its own value.
 * @property allowedValues The values the unit may choose from. `null` means anything the variable accepts.
 * @property excludedValues Values taken out of what the unit may choose.
 */
data class ResolvedFlowVariableRule(
    val defaultValue: Any?,
    val editable: Boolean,
    val allowedValues: List<String>? = null,
    val excludedValues: List<String>? = null,
)

/** Why a flow variable value was refused. [value] is the code, which the translation keys use. */
enum class FlowVariableValidationErrorCode(
    val value: String,
) {
    INVALID("invalid"),
    MAX("max"),
    MAX_LENGTH("max_length"),
    MIN("min"),
    MIN_LENGTH("min_length"),
    NOT_OPTION("not_option"),
    PATTERN("pattern"),
    REQUIRED("required"),
}

/** A refused flow variable value. [message] is the variable's own message for the rule that failed, if it has one. */
data class FlowVariableValidationError(
    val code: FlowVariableValidationErrorCode,
    val message: String? = null,
)
