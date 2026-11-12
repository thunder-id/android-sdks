// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.management

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowVariableSupportTest {
    private fun flow(
        nodes: List<Map<String, Any?>> = emptyList(),
        variables: List<FlowVariableDefinition>? = null,
    ) = FlowDefinition(
        id = "f-1",
        name = "Login",
        flowType = "AUTHENTICATION",
        handle = "login",
        activeVersion = 1,
        isReadOnly = false,
        nodes = nodes,
        variables = variables,
    )

    private fun variable(
        type: String,
        identifier: String = "v",
        defaultValue: Any? = null,
        options: List<Any>? = null,
        min: Double? = null,
        max: Double? = null,
        category: String? = null,
        connectionTypes: List<String>? = null,
        validation: List<FlowVariableValidationRule>? = null,
    ) = FlowVariableDefinition(
        identifier = identifier,
        type = type,
        defaultValue = defaultValue,
        options = options,
        min = min,
        max = max,
        category = category,
        connectionTypes = connectionTypes,
        validation = validation,
    )

    // MARK: - getFlowVariableDefinitions

    @Test
    fun `a shared flow's variables are read as the server sent them`() {
        val shared = listOf(variable(FlowVariableType.TEXT_INPUT, "supportEmail"))

        assertEquals(shared, getFlowVariableDefinitions(flow(variables = shared)))
    }

    @Test
    fun `an owned flow's variables are read from its VARIABLES node, skipping malformed inputs`() {
        val nodes =
            listOf(
                mapOf("id" to "start", "type" to "START"),
                mapOf(
                    "id" to "vars",
                    "type" to "VARIABLES",
                    "inputs" to
                        listOf(
                            mapOf("identifier" to "otp", "type" to "NUMBER_INPUT", "defaultValue" to 120.0, "min" to 30.0),
                            mapOf("identifier" to "missingType"),
                            mapOf("type" to "TEXT_INPUT"),
                        ),
                ),
            )

        val definitions = getFlowVariableDefinitions(flow(nodes = nodes))

        assertEquals(1, definitions.size)
        assertEquals("otp", definitions.single().identifier)
        assertEquals(FlowVariableType.NUMBER_INPUT, definitions.single().type)
        assertEquals(120.0, definitions.single().defaultValue)
        assertEquals(30.0, definitions.single().min)
    }

    @Test
    fun `no flow or no VARIABLES node declares nothing`() {
        assertTrue(getFlowVariableDefinitions(null).isEmpty())
        assertTrue(getFlowVariableDefinitions(flow(nodes = listOf(mapOf("type" to "END")))).isEmpty())
    }

    // MARK: - getFlowVariableOptions

    @Test
    fun `a SELECT offers its declared options, narrowed by the restriction`() {
        val select = variable(FlowVariableType.SELECT, options = listOf("a", "b", 3.0))

        assertEquals(listOf("a", "b", "3"), getFlowVariableOptions(select).map { it.value })
        assertEquals(
            listOf("b"),
            getFlowVariableOptions(select, restriction = FlowVariableOptionRestriction(listOf("a", "b"), listOf("a"))).map { it.value },
        )
    }

    @Test
    fun `a CONNECTION_SELECT offers the connections of its category and vendors by id`() {
        val connections =
            listOf(
                ConnectionInstance(
                    "c-1",
                    "Google",
                    type = ConnectionType.GOOGLE,
                    categories = listOf(ConnectionCategory.IDENTITY_PROVIDER),
                ),
                ConnectionInstance(
                    "c-2",
                    "GitHub",
                    type = ConnectionType.GITHUB,
                    categories = listOf(ConnectionCategory.IDENTITY_PROVIDER),
                ),
                ConnectionInstance("c-3", "SMTP", type = ConnectionType.EMAIL_SMTP, categories = listOf(ConnectionCategory.EMAIL_PROVIDER)),
                ConnectionInstance("c-4", "Unknown"),
            )
        val idp = variable(FlowVariableType.CONNECTION_SELECT, category = "IDENTITY_PROVIDER")

        assertEquals(listOf("c-1", "c-2"), getFlowVariableOptions(idp, connections).map { it.value })
        assertEquals(
            listOf(FlowVariableOption("Google", "c-1")),
            getFlowVariableOptions(idp.copy(connectionTypes = listOf("GOOGLE")), connections),
        )
        assertTrue(getFlowVariableOptions(variable(FlowVariableType.TEXT_INPUT), connections).isEmpty())
    }

    // MARK: - resolveFlowVariableRule

    @Test
    fun `without an overlay, or for the owner, the variable is editable and keeps its default`() {
        val definition = variable(FlowVariableType.TEXT_INPUT, defaultValue = "help@acme.example")
        val owned =
            ResolvedOverlay(
                isOwnedByOU = true,
                rules = mapOf("variables.v" to OverlayRule(editable = false, value = listOf("pinned"), allowedValues = listOf("x"))),
            )

        assertEquals(ResolvedFlowVariableRule("help@acme.example", editable = true), resolveFlowVariableRule(definition))
        assertEquals(ResolvedFlowVariableRule("help@acme.example", editable = true), resolveFlowVariableRule(definition, owned))
    }

    @Test
    fun `a unit the flow was shared with follows the owner's rule and pinned value`() {
        val overlay =
            ResolvedOverlay(
                isOwnedByOU = false,
                rules =
                    mapOf(
                        "variables.otp" to OverlayRule(editable = false, value = listOf("90")),
                        "variables.showLink" to OverlayRule(editable = true, value = listOf("true"), excludedValues = listOf("x")),
                        "variables.mode" to OverlayRule(editable = true, value = listOf("2"), allowedValues = listOf("1", "2")),
                    ),
            )

        val otp = resolveFlowVariableRule(variable(FlowVariableType.NUMBER_INPUT, "otp", defaultValue = 120.0), overlay)
        val showLink = resolveFlowVariableRule(variable(FlowVariableType.BOOLEAN_INPUT, "showLink"), overlay)
        val mode = resolveFlowVariableRule(variable(FlowVariableType.SELECT, "mode", options = listOf(1.0, 2.0)), overlay)
        val unruled = resolveFlowVariableRule(variable(FlowVariableType.TEXT_INPUT, "other", defaultValue = "d"), overlay)

        assertEquals(ResolvedFlowVariableRule(90.0, editable = false), otp)
        assertEquals(true, showLink.defaultValue)
        assertEquals(listOf("x"), showLink.excludedValues)
        assertEquals(2.0, mode.defaultValue)
        assertEquals(listOf("1", "2"), mode.allowedValues)
        assertEquals(ResolvedFlowVariableRule("d", editable = false), unruled)
    }

    // MARK: - validateFlowVariableValue

    @Test
    fun `values each type accepts pass`() {
        assertNull(validateFlowVariableValue(variable(FlowVariableType.BOOLEAN_INPUT), null))
        assertNull(validateFlowVariableValue(variable(FlowVariableType.BOOLEAN_INPUT), false))
        assertNull(validateFlowVariableValue(variable(FlowVariableType.NUMBER_INPUT, min = 1.0, max = 10.0), "5"))
        assertNull(validateFlowVariableValue(variable(FlowVariableType.NUMBER_INPUT, min = 1.0, max = 10.0), 10.0))
        assertNull(validateFlowVariableValue(variable(FlowVariableType.SELECT, options = listOf(1.0, "b")), 1L))
        assertNull(validateFlowVariableValue(variable(FlowVariableType.EMAIL_INPUT), "a@b.co"))
        assertNull(validateFlowVariableValue(variable(FlowVariableType.PHONE_INPUT), "+94 (11) 123-4567"))
        assertNull(validateFlowVariableValue(variable(FlowVariableType.DATE_INPUT), "2026-11-10"))
        assertNull(validateFlowVariableValue(variable(FlowVariableType.CONNECTION_SELECT), "c-1"))
        assertNull(validateFlowVariableValue(variable(FlowVariableType.TEXT_INPUT), "anything"))
        assertNull(validateFlowVariableValue(variable("FUTURE_INPUT"), "treated as text"))
    }

    @Test
    fun `values a type refuses carry the matching code`() {
        fun code(
            definition: FlowVariableDefinition,
            value: Any?,
        ) = validateFlowVariableValue(definition, value)?.code

        assertEquals(FlowVariableValidationErrorCode.INVALID, code(variable(FlowVariableType.BOOLEAN_INPUT), "true"))
        assertEquals(FlowVariableValidationErrorCode.REQUIRED, code(variable(FlowVariableType.TEXT_INPUT), ""))
        assertEquals(FlowVariableValidationErrorCode.REQUIRED, code(variable(FlowVariableType.NUMBER_INPUT), null))
        assertEquals(FlowVariableValidationErrorCode.INVALID, code(variable(FlowVariableType.NUMBER_INPUT), "1.2.3"))
        assertEquals(FlowVariableValidationErrorCode.MIN, code(variable(FlowVariableType.NUMBER_INPUT, min = 5.0), 4.0))
        assertEquals(FlowVariableValidationErrorCode.MAX, code(variable(FlowVariableType.NUMBER_INPUT, max = 5.0), "6"))
        assertEquals(FlowVariableValidationErrorCode.NOT_OPTION, code(variable(FlowVariableType.SELECT, options = listOf("a")), "b"))
        assertEquals(null, code(variable(FlowVariableType.SELECT, options = listOf(true, false)), "true"))
        assertEquals(null, code(variable(FlowVariableType.SELECT, options = listOf(true, false)), false))
        assertEquals(null, code(variable(FlowVariableType.SELECT, options = listOf("a", 2.0)), "2"))
        assertEquals(
            FlowVariableValidationErrorCode.NOT_OPTION,
            code(variable(FlowVariableType.SELECT, options = listOf(true, false)), "yes"),
        )
        assertEquals(FlowVariableValidationErrorCode.INVALID, code(variable(FlowVariableType.EMAIL_INPUT), "not-an-email"))
        assertEquals(FlowVariableValidationErrorCode.INVALID, code(variable(FlowVariableType.PHONE_INPUT), "call me"))
        assertEquals(FlowVariableValidationErrorCode.INVALID, code(variable(FlowVariableType.DATE_INPUT), "10/11/2026"))
        assertEquals(FlowVariableValidationErrorCode.INVALID, code(variable(FlowVariableType.CONNECTION_SELECT), 4.0))
    }

    @Test
    fun `a text variable's rules apply in order and carry their own message`() {
        val rules =
            listOf(
                FlowVariableValidationRule("minLength", 3.0, "Too short"),
                FlowVariableValidationRule("maxLength", "5"),
                FlowVariableValidationRule("regex", "^[a-z]+$", "Lowercase only"),
            )
        val text = variable(FlowVariableType.TEXT_INPUT, validation = rules)

        assertEquals(
            FlowVariableValidationError(FlowVariableValidationErrorCode.MIN_LENGTH, "Too short"),
            validateFlowVariableValue(text, "ab"),
        )
        assertEquals(FlowVariableValidationErrorCode.MAX_LENGTH, validateFlowVariableValue(text, "abcdef")?.code)
        assertEquals(
            FlowVariableValidationError(FlowVariableValidationErrorCode.PATTERN, "Lowercase only"),
            validateFlowVariableValue(text, "ABC"),
        )
        assertNull(validateFlowVariableValue(text, "abcd"))
    }

    @Test
    fun `a pattern the platform cannot compile is left to the server`() {
        val text = variable(FlowVariableType.TEXT_INPUT, validation = listOf(FlowVariableValidationRule("regex", "(unclosed")))

        assertNull(validateFlowVariableValue(text, "anything"))
    }

    // MARK: - Values

    @Test
    fun `whole numbers read without a fraction and compare by value`() {
        assertEquals("120", flowVariableText(120.0))
        assertEquals("1.5", flowVariableText(1.5))
        assertEquals("", flowVariableText(null))
        assertTrue(sameFlowVariableValue(120.0, 120L))
        assertFalse(sameFlowVariableValue(120.0, "120"))
        assertTrue(sameFlowVariableValue(null, null))
    }
}
