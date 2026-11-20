// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.management

import dev.thunderid.android.api.management.AgentQueryKeys
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PersonalAgentStateTest {
    @Test
    fun `a change to a personal agent refreshes its details and the lists, and no other agent`() =
        runTest {
            val invalidator = ResourceInvalidator()
            val refreshed = mutableListOf<String>()
            mapOf(
                "details" to listOf(AgentQueryKeys.ME_AGENT, "pa-1"),
                "list" to listOf(AgentQueryKeys.ME_AGENTS, "kind=", "limit=", "offset="),
                "other agent" to listOf(AgentQueryKeys.ME_AGENT, "pa-2"),
            ).forEach { (name, key) -> invalidator.subscribe(key) { refreshed += name } }
            val delete = ResourceMutationState<String, Unit>(invalidator, ::meAgentKeys) { }

            delete.mutate("pa-1")

            assertEquals(listOf("details", "list"), refreshed)
        }
}
