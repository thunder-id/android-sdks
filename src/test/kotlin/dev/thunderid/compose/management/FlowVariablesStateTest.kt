// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.management

import dev.thunderid.android.api.management.ConnectionListResponse
import dev.thunderid.android.api.management.FlowDefinition
import dev.thunderid.android.api.management.FlowVariableDefinition
import dev.thunderid.android.api.management.FlowVariableQueryKeys
import dev.thunderid.android.api.management.FlowVariableType
import dev.thunderid.android.api.management.FlowVariables
import dev.thunderid.android.api.management.ResolvedOverlay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowVariablesStateTest {
    private class Refused : Exception()

    private val definitions = listOf(FlowVariableDefinition("supportEmail", FlowVariableType.EMAIL_INPUT))
    private val flow =
        FlowDefinition(
            id = "f-1",
            name = "Login",
            flowType = "AUTHENTICATION",
            handle = "login",
            activeVersion = 1,
            isReadOnly = true,
            nodes = emptyList(),
            variables = definitions,
        )

    private fun state(
        values: () -> FlowVariables = { FlowVariables(mapOf("supportEmail" to "ops@unit.example")) },
        save: (Map<String, Any>) -> FlowVariables = { FlowVariables(it) },
        invalidator: ResourceInvalidator = ResourceInvalidator(),
        onSaved: ((Map<String, Any>) -> Unit)? = null,
    ): Triple<FlowVariablesState, List<ResourceQueryState<*>>, ResourceInvalidator> {
        val flowQuery = ResourceQueryState(listOf(FlowVariableQueryKeys.DEFINITIONS)) { flow }
        val valuesQuery = ResourceQueryState(listOf(FlowVariableQueryKeys.VALUES)) { values() }
        val overlayQuery = ResourceQueryState(listOf(FlowVariableQueryKeys.DEFINITIONS, "overlay")) { ResolvedOverlay(isOwnedByOU = false) }
        val connectionsQuery =
            ResourceQueryState(listOf(FlowVariableQueryKeys.DEFINITIONS, "connections")) {
                ConnectionListResponse(0, 1, 0, emptyList())
            }
        val mutation =
            ResourceMutationState<Map<String, Any>, FlowVariables>(invalidator, { listOf(listOf(FlowVariableQueryKeys.VALUES)) }) {
                save(it)
            }
        val state = FlowVariablesState(flowQuery, valuesQuery, overlayQuery, connectionsQuery, mutation, definitions) { onSaved }
        return Triple(state, listOf(flowQuery, valuesQuery, overlayQuery), invalidator)
    }

    @Test
    fun `is loaded once the flow, the unit's values and its rules have all arrived`() =
        runTest {
            val (state, queries) = state()
            assertFalse(state.isLoaded)

            queries.forEach { it.refetch() }

            assertTrue(state.isLoaded)
            assertNull(state.error)
            assertEquals(flow, state.flow)
            assertEquals(definitions, state.definitions)
            assertEquals(mapOf("supportEmail" to "ops@unit.example"), state.overrides)
            assertTrue(state.connections.isEmpty())
        }

    @Test
    fun `a failed load is reported and the state never counts as loaded`() =
        runTest {
            val (state, queries) = state(values = { throw Refused() })

            queries.forEach { it.refetch() }

            assertFalse(state.isLoaded)
            assertTrue(state.error is Refused)
            assertTrue(state.overrides.isEmpty())
        }

    @Test
    fun `saving reports the stored values and refreshes the unit's values`() =
        runTest {
            val saved = mutableListOf<Map<String, Any>>()
            val invalidated = mutableListOf<String>()
            val invalidator = ResourceInvalidator()
            invalidator.subscribe(listOf(FlowVariableQueryKeys.VALUES, "f-1", "ou-1")) { invalidated += "values" }
            val (state) = state(invalidator = invalidator, onSaved = { saved += it })

            val result = state.save(mapOf("supportEmail" to "new@unit.example"))

            assertEquals(mapOf("supportEmail" to "new@unit.example"), result?.values)
            assertEquals(listOf(mapOf<String, Any>("supportEmail" to "new@unit.example")), saved)
            assertEquals(listOf("values"), invalidated)
            assertNull(state.saveError)
            assertFalse(state.isSaving)
        }

    @Test
    fun `a refused save is reported without calling onSaved`() =
        runTest {
            val saved = mutableListOf<Map<String, Any>>()
            val (state) = state(save = { throw Refused() }, onSaved = { saved += it })

            assertNull(state.save(mapOf("supportEmail" to "bad")))
            assertTrue(state.saveError is Refused)
            assertTrue(saved.isEmpty())
        }
}
