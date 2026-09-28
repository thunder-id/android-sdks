// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.presentation.auth

import dev.thunderid.android.FlowComponent
import org.junit.Assert.assertEquals
import org.junit.Test

private fun component(
    id: String,
    type: String,
    vararg children: FlowComponent,
) = FlowComponent(id = id, type = type, components = children.toList())

class ResourceSelectSkippedTest {
    @Test
    fun `every picker type is recognised`() {
        for (type in listOf("USER_SELECT", "AUTH_FLOW_SELECT", "USER_TYPE_SELECT")) {
            assertEquals("$type should be skipped", true, isResourceSelectType(type))
        }
    }

    @Test
    fun `other types and a missing type are not pickers`() {
        for (type in listOf("TEXT_INPUT", "OU_SELECT", "SELECT", "")) {
            assertEquals("$type should render", false, isResourceSelectType(type))
        }
        assertEquals(false, isResourceSelectType(null))
    }

    @Test
    fun `a picker is found at the top level and inside nested blocks`() {
        val picker = component("p", "AUTH_FLOW_SELECT")
        val nested = component("b", "BLOCK", component("t", "TEXT_INPUT"), picker)

        assertEquals(true, containsResourceSelect(listOf(picker)))
        assertEquals(true, containsResourceSelect(listOf(component("outer", "BLOCK", nested))))
    }

    @Test
    fun `a step without a picker is not flagged`() {
        val components = listOf(component("b", "BLOCK", component("t", "TEXT_INPUT")))

        assertEquals(false, containsResourceSelect(components))
        assertEquals(false, containsResourceSelect(emptyList()))
    }
}
