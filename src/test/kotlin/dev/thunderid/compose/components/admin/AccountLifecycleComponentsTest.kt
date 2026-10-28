// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.admin

import dev.thunderid.android.management.AccessStatus
import dev.thunderid.android.management.AccessStatusValue
import dev.thunderid.android.management.AccountLifecycleAction
import dev.thunderid.android.management.ManagedUser
import dev.thunderid.compose.i18n.DefaultStrings
import dev.thunderid.compose.i18n.ThunderIDI18n
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountLifecycleComponentsTest {
    private val i18n = ThunderIDI18n()

    @Test
    fun `shows the translated access status, the raw value for an unknown one, and nothing without one`() {
        assertEquals("Active", accessStatusLabel(i18n, AccessStatusValue.ACTIVE))
        assertEquals("Locked", accessStatusLabel(i18n, AccessStatusValue.LOCKED))
        assertEquals("Suspended", accessStatusLabel(i18n, AccessStatusValue.SUSPENDED))
        assertEquals("ARCHIVED", accessStatusLabel(i18n, "ARCHIVED"))
        assertNull(accessStatusLabel(i18n, null))
    }

    @Test
    fun `an application bundle translates the status and the actions`() {
        val custom =
            ThunderIDI18n(
                bundles =
                    mapOf(
                        "fr-FR" to
                            mapOf(
                                "admin.common.status.locked" to "Verrouillé",
                                "admin.common.lifecycle.unlock" to "Déverrouiller",
                            ),
                    ),
                language = "fr-FR",
            )

        assertEquals("Verrouillé", accessStatusLabel(custom, AccessStatusValue.LOCKED))
        assertEquals("Déverrouiller", accountLifecycleLabel(custom, AccountLifecycleAction.UNLOCK))
        assertEquals("Suspend", accountLifecycleLabel(custom, AccountLifecycleAction.SUSPEND))
    }

    @Test
    fun `offers no lifecycle actions without a handler or a record`() {
        val actions = listOf(AccountLifecycleAction.UNLOCK, AccountLifecycleAction.SUSPEND)
        val handler: AccountLifecycleHandler = { _, _ -> }

        assertEquals(actions, offeredLifecycleActions(actions, handler, hasRecord = true))
        assertTrue(offeredLifecycleActions(actions, null, hasRecord = true).isEmpty())
        assertTrue(offeredLifecycleActions(actions, handler, hasRecord = false).isEmpty())
    }

    @Test
    fun `an operator note is trimmed, capped at the server's limit, and left out when blank`() {
        assertEquals("Ticket 4521", normalizeOperatorNote("  Ticket 4521 "))
        assertNull(normalizeOperatorNote("   "))
        assertNull(normalizeOperatorNote(null))
        assertEquals(OPERATOR_NOTE_MAX_LENGTH, normalizeOperatorNote("x".repeat(150))?.length)
    }

    @Test
    fun `asks for confirmation in the identity's name`() {
        assertEquals(
            "Suspend Alice? It can no longer sign in, and its sessions and tokens are revoked.",
            i18n.translate("admin.common.lifecycle.suspend_confirm", "name" to "Alice"),
        )
        assertEquals(
            "Unsuspend Alice? Its access is restored, unless it is also locked.",
            i18n.translate("admin.common.lifecycle.unsuspend_confirm", "name" to "Alice"),
        )
    }

    @Test
    fun `hands a custom render the lifecycle actions, for a read-only user too`() {
        var ran: Pair<AccountLifecycleAction, String?>? = null
        val handler: AccountLifecycleHandler = { action, note -> ran = action to note }
        val user =
            ManagedUser(
                id = "u-1",
                ouId = "ou-1",
                type = "customer",
                isReadOnly = true,
                status = AccessStatus(AccessStatusValue.LOCKED),
            )
        val state =
            UserDetailsState(
                user = user,
                displayName = "Alice",
                attributes = emptyMap(),
                groups = emptyList(),
                isLoading = false,
                error = null,
                isDeleting = false,
                isUpdating = false,
                onDelete = null,
                onUpdate = null,
                lifecycleActions = offeredLifecycleActions(listOf(AccountLifecycleAction.UNLOCK), handler, hasRecord = true),
                onLifecycleAction = handler,
            )

        state.onLifecycleAction?.invoke(AccountLifecycleAction.UNLOCK, null)

        assertEquals(listOf(AccountLifecycleAction.UNLOCK), state.lifecycleActions)
        assertSame(handler, state.onLifecycleAction)
        assertEquals(AccountLifecycleAction.UNLOCK to null, ran)
    }

    @Test
    fun `a custom render built without lifecycle actions gets none`() {
        val state = AgentDetailsState(null, emptyList(), emptyList(), false, null, false, null)

        assertTrue(state.lifecycleActions.isEmpty())
        assertNull(state.onLifecycleAction)
    }

    @Test
    fun `ships every lifecycle string`() {
        val keys =
            listOf("active", "locked", "suspended").map { "admin.common.status.$it" } +
                listOf("suspend", "unsuspend", "unlock", "suspend_confirm", "unsuspend_confirm", "operator_note", "error", "load_error")
                    .map { "admin.common.lifecycle.$it" } +
                "admin.common.columns.status"

        keys.forEach { assertTrue(it, DefaultStrings.all.containsKey(it)) }
    }
}
