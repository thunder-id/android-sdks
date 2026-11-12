// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.management

import dev.thunderid.android.IAMException
import dev.thunderid.android.ThunderIDErrorCode
import dev.thunderid.android.api.management.ConnectionCategory
import dev.thunderid.android.api.management.ConnectionInstance
import dev.thunderid.android.api.management.FlowVariableDefinition
import dev.thunderid.android.api.management.FlowVariableOption
import dev.thunderid.android.api.management.FlowVariableType
import dev.thunderid.android.api.management.FlowVariableValidationError
import dev.thunderid.android.api.management.FlowVariableValidationErrorCode
import dev.thunderid.android.api.management.ManagedFlow
import dev.thunderid.android.api.management.OverlayRule
import dev.thunderid.android.api.management.ResolvedOverlay
import dev.thunderid.compose.i18n.ThunderIDI18n
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowVariablesComponentsTest {
    private val i18n = ThunderIDI18n()

    private val email = FlowVariableDefinition("supportEmail", FlowVariableType.EMAIL_INPUT, defaultValue = "help@acme.example")
    private val otp = FlowVariableDefinition("otp", FlowVariableType.NUMBER_INPUT, defaultValue = 120.0, min = 30.0)
    private val showLink = FlowVariableDefinition("showLink", FlowVariableType.BOOLEAN_INPUT, defaultValue = false)
    private val idp = FlowVariableDefinition("idp", FlowVariableType.CONNECTION_SELECT, category = "IDENTITY_PROVIDER")
    private val variables = listOf(email, otp, showLink, idp)

    // The unit may set the email and the identity provider; the owner pinned the OTP lifetime and the link.
    private val overlay =
        ResolvedOverlay(
            isOwnedByOU = false,
            rules =
                mapOf(
                    "variables.supportEmail" to OverlayRule(editable = true),
                    "variables.otp" to OverlayRule(editable = false, value = listOf("90")),
                    "variables.showLink" to OverlayRule(editable = false),
                    "variables.idp" to OverlayRule(editable = true, excludedValues = listOf("c-2")),
                ),
        )

    // MARK: - Form values

    @Test
    fun `the form starts from the unit's own values where it may set them, otherwise the owner's`() {
        val starting =
            flowVariableStartingValues(variables, overlay, mapOf("supportEmail" to "ops@unit.example", "otp" to 300.0))

        assertEquals(mapOf("supportEmail" to "ops@unit.example", "otp" to 90.0, "showLink" to false), starting)
        assertEquals(
            mapOf("supportEmail" to "help@acme.example", "otp" to 90.0, "showLink" to false),
            flowVariableDefaults(variables, overlay),
        )
    }

    @Test
    fun `only editable values that differ from their default are saved, numbers typed as text as numbers`() {
        val values = mapOf("supportEmail" to "help@acme.example", "otp" to "45", "showLink" to true, "idp" to "c-1")

        assertEquals(mapOf("idp" to "c-1"), flowVariableOverrides(variables, overlay, values))
        assertEquals(
            mapOf("otp" to 45.0, "showLink" to true, "idp" to "c-1"),
            flowVariableOverrides(variables, null, values),
        )
        assertTrue(flowVariableOverrides(variables, null, mapOf("otp" to 120L)).isEmpty())
    }

    @Test
    fun `only variables the unit may set are checked`() {
        val problems = flowVariableProblems(variables, overlay, mapOf("supportEmail" to "nope", "otp" to "x"))

        assertEquals(
            mapOf(
                "supportEmail" to FlowVariableValidationError(FlowVariableValidationErrorCode.INVALID),
                "idp" to FlowVariableValidationError(FlowVariableValidationErrorCode.REQUIRED),
            ),
            problems,
        )
        assertTrue(flowVariableProblems(variables, overlay, mapOf("supportEmail" to "a@b.co", "idp" to "c-1")).isEmpty())
    }

    @Test
    fun `refused values read in the active language, or with the variable's own message`() {
        assertEquals(
            "Enter a valid value.",
            flowVariableErrorMessage(i18n, FlowVariableValidationError(FlowVariableValidationErrorCode.INVALID)),
        )
        assertEquals(
            "Saisissez une valeur.",
            flowVariableErrorMessage(
                ThunderIDI18n(language = "fr-FR"),
                FlowVariableValidationError(FlowVariableValidationErrorCode.REQUIRED),
            ),
        )
        assertEquals(
            "Use lowercase",
            flowVariableErrorMessage(i18n, FlowVariableValidationError(FlowVariableValidationErrorCode.PATTERN, "Use lowercase")),
        )
        FlowVariableValidationErrorCode.values().forEach {
            val key = "admin.flowVariables.errors.${it.value}"
            assertTrue(key, i18n.resolve(key) != key)
        }
    }

    @Test
    fun `an editable choice is narrowed by the owner's rule, a pinned one is not`() {
        val connections =
            listOf(
                ConnectionInstance("c-1", "Google", categories = listOf(ConnectionCategory.IDENTITY_PROVIDER)),
                ConnectionInstance("c-2", "GitHub", categories = listOf(ConnectionCategory.IDENTITY_PROVIDER)),
            )
        val pinned = ResolvedOverlay(isOwnedByOU = false, rules = mapOf("variables.idp" to OverlayRule(excludedValues = listOf("c-2"))))

        assertEquals(listOf(FlowVariableOption("Google", "c-1")), flowVariableChoices(idp, overlay, connections))
        assertEquals(listOf("c-1", "c-2"), flowVariableChoices(idp, pinned, connections).map { it.value })
    }

    // MARK: - Field

    @Test
    fun `a number being typed stays text until it reads back the same`() {
        assertEquals(12.0, parseFlowVariableInput(otp, "12"))
        assertEquals(1.5, parseFlowVariableInput(otp, "1.5"))
        assertEquals("1.", parseFlowVariableInput(otp, "1."))
        assertEquals("1.50", parseFlowVariableInput(otp, "1.50"))
        assertEquals("", parseFlowVariableInput(otp, ""))
        assertEquals("12", parseFlowVariableInput(email, "12"))
    }

    @Test
    fun `a value reads as on or off, by its choice's label, or as text`() {
        val options = listOf(FlowVariableOption("Google", "c-1"))

        assertEquals("On", displayFlowVariableValue(i18n, true, options))
        assertEquals("Désactivé", displayFlowVariableValue(ThunderIDI18n(language = "fr-FR"), false, options))
        assertEquals("Google", displayFlowVariableValue(i18n, "c-1", options))
        assertEquals("120", displayFlowVariableValue(i18n, 120.0, options))
        assertEquals("", displayFlowVariableValue(i18n, null, options))
    }

    // MARK: - Shared flow list

    private val flows =
        listOf(
            ManagedFlow("f-1", "own-1", "AUTHENTICATION", isOwnedByOU = true),
            ManagedFlow("f-2", "shared-1", "AUTHENTICATION", isOwnedByOU = false),
            ManagedFlow("f-3", "shared-2", "REGISTRATION", isOwnedByOU = false),
            ManagedFlow("f-4", "unknown", "AUTHENTICATION"),
        )

    @Test
    fun `a narrowed list keeps the flows of that ownership and pages them on the device`() {
        val shared = sharedFlowPage(flows, 4, SharedFlowOwnership.SHARED, offset = 0, limit = 1)
        val secondPage = sharedFlowPage(flows, 4, SharedFlowOwnership.SHARED, offset = 1, limit = 1)
        val owned = sharedFlowPage(flows, 4, SharedFlowOwnership.OWNED, offset = 0, limit = 20)

        assertEquals(listOf("f-2"), shared.flows.map { it.id })
        assertEquals(2, shared.totalResults)
        assertEquals(listOf("f-3"), secondPage.flows.map { it.id })
        assertEquals(listOf("f-1"), owned.flows.map { it.id })
        assertEquals(1, owned.totalResults)
    }

    @Test
    fun `the whole list is the server's page as it is`() {
        val all = sharedFlowPage(flows, 40, SharedFlowOwnership.ALL, offset = 20, limit = 20)

        assertEquals(flows, all.flows)
        assertEquals(40, all.totalResults)
    }

    @Test
    fun `list failures use the flow list messages`() {
        assertEquals(
            "Could not load flows. Please try again.",
            toManagementErrorMessage(i18n, IllegalStateException("server text"), "admin.flows.list.error"),
        )
        assertEquals(
            "You do not have permission to do this.",
            toManagementErrorMessage(i18n, IAMException(ThunderIDErrorCode.FORBIDDEN, "Forbidden"), "admin.flows.list.error"),
        )
    }
}
