// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.management

import dev.thunderid.android.IAMException
import dev.thunderid.android.ThunderIDErrorCode
import dev.thunderid.android.api.management.Group
import dev.thunderid.android.api.management.Role
import dev.thunderid.compose.i18n.DefaultStrings
import dev.thunderid.compose.i18n.ThunderIDI18n
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupRoleDetailsTest {
    private val i18n = ThunderIDI18n()

    private val group = Group(id = "g-1", name = "Engineering", ouId = "ou-1", description = "Product engineering")
    private val role = Role(id = "r-1", name = "Viewer", ouId = "ou-1", permissions = emptyList())

    // ── Overview ───────────────────────────────────────────────────────────

    @Test
    fun `group overview shows the organization unit handle, or its id when the handle is unknown`() {
        assertEquals(
            listOf(AdminDetailItem("Organization unit", "ou-1")),
            groupOverview(i18n, group),
        )
        assertEquals("engineering", groupOverview(i18n, group.copy(ouHandle = "engineering")).single().value)
    }

    @Test
    fun `role overview shows the organization unit handle, or its id when the handle is unknown`() {
        assertEquals(listOf(AdminDetailItem("Organization unit", "ou-1")), roleOverview(i18n, role))
        assertEquals("root", roleOverview(i18n, role.copy(ouHandle = "root")).single().value)
    }

    // ── Delete ─────────────────────────────────────────────────────────────

    @Test
    fun `a read-only or missing role is never offered for deletion`() {
        assertTrue(isRoleDeletable(role))
        assertTrue(isRoleDeletable(role.copy(isReadOnly = false)))
        assertFalse(isRoleDeletable(role.copy(isReadOnly = true)))
        assertFalse(isRoleDeletable(null))
    }

    @Test
    fun `delete confirmation names the group and the role`() {
        assertEquals("Delete Engineering?", i18n.translate("admin.groups.details.delete.confirm", "name" to group.name))
        assertEquals("Delete Viewer?", i18n.translate("admin.roles.details.delete.confirm", "name" to role.name))
    }

    // ── Custom render state ────────────────────────────────────────────────

    @Test
    fun `a custom render gets the loaded group and the delete action`() {
        var deleted = false
        val state = GroupDetailsState(group, isLoading = false, error = null, isDeleting = false, onDelete = { deleted = true })

        assertEquals("Engineering", state.group?.name)
        state.onDelete?.invoke()
        assertTrue(deleted)
    }

    @Test
    fun `a custom render built while loading has no record`() {
        val groupState = GroupDetailsState(null, isLoading = true, error = null, isDeleting = false, onDelete = null)
        val roleState = RoleDetailsState(null, isLoading = true, error = null, isDeleting = false, onDelete = null)

        assertNull(groupState.group)
        assertNull(groupState.onDelete)
        assertNull(roleState.role)
        assertNull(roleState.onDelete)
    }

    // ── Errors and translations ────────────────────────────────────────────

    @Test
    fun `load and delete failures use the details messages and never the server text`() {
        val failure = IllegalStateException("pq: relation does not exist")

        assertEquals(
            "Could not load this group. Please try again.",
            toManagementErrorMessage(i18n, failure, "admin.groups.details.error"),
        )
        assertEquals(
            "Could not delete the group. Please try again.",
            toManagementErrorMessage(i18n, failure, "admin.groups.details.delete.error"),
        )
        assertEquals(
            "Could not load this role. Please try again.",
            toManagementErrorMessage(i18n, failure, "admin.roles.details.error"),
        )
        assertEquals(
            "Could not delete the role. Please try again.",
            toManagementErrorMessage(i18n, failure, "admin.roles.details.delete.error"),
        )
        assertEquals(
            "This item could not be found.",
            toManagementErrorMessage(i18n, IAMException(ThunderIDErrorCode.NOT_FOUND, "x"), "admin.roles.details.error"),
        )
    }

    @Test
    fun `ships every group and role details string`() {
        val keys =
            listOf("groups", "roles").flatMap { resource ->
                listOf("overview", "error", "delete.action", "delete.confirm", "delete.error")
                    .map { "admin.$resource.details.$it" }
            } + "admin.common.columns.organization_unit" + "admin.common.errors.not_found"

        keys.forEach { assertTrue(it, DefaultStrings.all.containsKey(it)) }
        assertEquals("Delete group", i18n.resolve("admin.groups.details.delete.action"))
        assertEquals("Delete role", i18n.resolve("admin.roles.details.delete.action"))
        assertEquals("Overview", i18n.resolve("admin.roles.details.overview"))
    }
}
