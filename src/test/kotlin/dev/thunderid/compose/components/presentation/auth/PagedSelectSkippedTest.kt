// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.presentation.auth

import dev.thunderid.android.FlowComponent
import org.junit.Assert.assertEquals
import org.junit.Test

class PagedSelectSkippedTest {
    @Test
    fun `every picker type is recognised`() {
        for (type in listOf("USER_SELECT", "AUTH_FLOW_SELECT", "USER_TYPE_SELECT")) {
            assertEquals("$type should be skipped", true, isPagedSelectType(type))
        }
    }

    @Test
    fun `other types and a missing type are not pickers`() {
        for (type in listOf("TEXT_INPUT", "OU_SELECT", "SELECT", "")) {
            assertEquals("$type should render", false, isPagedSelectType(type))
        }
        assertEquals(false, isPagedSelectType(null))
    }

    @Test
    fun `a picker is found at the top level and inside nested blocks`() {
        val picker = FlowComponent(id = "p", type = "AUTH_FLOW_SELECT")
        val nested = FlowComponent(id = "b", type = "BLOCK", components = listOf(FlowComponent(id = "t", type = "TEXT_INPUT"), picker))

        assertEquals(true, containsPagedSelect(listOf(picker)))
        assertEquals(true, containsPagedSelect(listOf(FlowComponent(id = "outer", type = "BLOCK", components = listOf(nested)))))
    }

    @Test
    fun `a step without a picker is not flagged`() {
        val components = listOf(FlowComponent(id = "b", type = "BLOCK", components = listOf(FlowComponent(id = "t", type = "TEXT_INPUT"))))

        assertEquals(false, containsPagedSelect(components))
        assertEquals(false, containsPagedSelect(emptyList()))
    }
}
