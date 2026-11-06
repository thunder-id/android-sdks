// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.auth

import dev.thunderid.android.FlowAction
import dev.thunderid.android.FlowComponent
import dev.thunderid.android.FlowInput
import dev.thunderid.compose.i18n.DefaultStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignInTest {
    @Test
    fun `enrichActions matches a flat action to its component by component id equals action ref`() {
        // The server never shares a field name between a flat action and its ACTION component:
        // the flat action only carries `ref`, the component only carries `id`. The pairing is
        // component.id == action.ref.
        val actions = listOf(FlowAction(ref = "action_001"))
        val components =
            listOf(
                FlowComponent(id = "action_001", type = "ACTION", label = "Sign In", variant = "PRIMARY"),
            )

        val enriched = enrichActions(actions, components)

        assertEquals(1, enriched.size)
        assertEquals("Sign In", enriched[0].label)
        assertEquals("PRIMARY", enriched[0].variant)
    }

    @Test
    fun `enrichActions leaves an action unchanged when no component matches`() {
        val actions = listOf(FlowAction(ref = "action_001"))
        val components = listOf(FlowComponent(id = "some_other_action", type = "ACTION"))

        val enriched = enrichActions(actions, components)

        assertEquals(1, enriched.size)
        assertNull(enriched[0].label)
    }

    @Test
    fun `isOutlinedVariant outlines secondary and outlined actions`() {
        assertTrue(isOutlinedVariant("SECONDARY"))
        assertTrue(isOutlinedVariant("secondary"))
        assertTrue(isOutlinedVariant("OUTLINED"))
    }

    @Test
    fun `isOutlinedVariant keeps primary and missing variants filled`() {
        assertFalse(isOutlinedVariant("PRIMARY"))
        assertFalse(isOutlinedVariant(null))
        assertFalse(isOutlinedVariant(""))
    }

    @Test
    fun `a Resolve OU step offering units is a pick, and one before sign-in is a typed field`() {
        // After sign-in the server offers the eligible units as a SELECT with their handle paths.
        assertEquals(FlowFieldKind.CHOICE, flowFieldKind("SELECT", listOf("acme/eng", "acme/sales")))
        // Before sign-in no unit is ever listed: the OU_SELECT input has no options.
        assertEquals(FlowFieldKind.ORGANIZATION_UNIT, flowFieldKind("OU_SELECT", null))
        assertEquals(FlowFieldKind.CHOICE, flowFieldKind("OU_SELECT", listOf("acme")))
        assertEquals(FlowFieldKind.PASSWORD, flowFieldKind("PASSWORD_INPUT", null))
        assertEquals(FlowFieldKind.TEXT, flowFieldKind("TEXT_INPUT", emptyList()))
        assertEquals(FlowFieldKind.TEXT, flowFieldKind(null, null))
    }

    @Test
    fun `a Resolve OU step's options are read from the server's inputs`() {
        val input =
            com.google.gson.Gson().fromJson(
                """{"identifier":"ouId","type":"SELECT","required":true,"options":["acme/eng","acme/sales"]}""",
                FlowInput::class.java,
            )
        assertEquals("ouId", input.name)
        assertEquals(listOf("acme/eng", "acme/sales"), input.options)
    }

    @Test
    fun `ships the Resolve OU strings`() {
        listOf(
            "signIn.select.label",
            "signIn.organizationUnit.label",
            "signIn.organizationUnit.placeholder",
            "signIn.organizationUnit.select",
        ).forEach { assertTrue(it, DefaultStrings.all.containsKey(it)) }
    }
}
