// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.admin

import dev.thunderid.android.IAMException
import dev.thunderid.android.ThunderIDErrorCode
import dev.thunderid.android.management.RoleResourcePermissions
import dev.thunderid.compose.i18n.DefaultStrings
import dev.thunderid.compose.i18n.ThunderIDI18n
import dev.thunderid.compose.management.GrantedPermissionsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminComponentsTest {
    private val i18n = ThunderIDI18n()

    // ── Error messages ─────────────────────────────────────────────────────

    @Test
    fun `forbidden and not found get their own translated messages`() {
        assertEquals(
            "You do not have permission to do this.",
            toManagementErrorMessage(i18n, IAMException(ThunderIDErrorCode.FORBIDDEN, "Forbidden"), "admin.users.list.error"),
        )
        assertEquals(
            "This item could not be found.",
            toManagementErrorMessage(i18n, IAMException(ThunderIDErrorCode.NOT_FOUND, "Not found"), "admin.users.list.error"),
        )
    }

    @Test
    fun `other failures use the fallback and never the server text`() {
        val serverText = "pq: duplicate key value violates unique constraint"
        val message =
            toManagementErrorMessage(i18n, IAMException(ThunderIDErrorCode.INVALID_INPUT, serverText), "admin.groups.create.error")

        assertEquals("Could not create the group. Please try again.", message)
        assertEquals(
            "Could not load roles. Please try again.",
            toManagementErrorMessage(i18n, IllegalStateException(serverText), "admin.roles.list.error"),
        )
        assertNull(toManagementErrorMessage(i18n, null, "admin.roles.list.error"))
    }

    @Test
    fun `an application bundle overrides the admin strings`() {
        val custom = ThunderIDI18n(bundles = mapOf("en-US" to mapOf("admin.common.errors.forbidden" to "Not allowed")))

        assertEquals("Not allowed", toManagementErrorMessage(custom, IAMException(ThunderIDErrorCode.FORBIDDEN, "x"), "k"))
        assertEquals("Users", custom.resolve("admin.users.list.title"))
    }

    // ── Translations ───────────────────────────────────────────────────────

    @Test
    fun `ships every admin string the components use`() {
        val keys = DefaultStrings.all.keys.filter { it.startsWith("admin.") }
        assertEquals(160, keys.size)
        listOf("user", "group", "agent", "app").forEach {
            assertTrue(DefaultStrings.all.containsKey("admin.common.identity_types.$it"))
        }
    }

    @Test
    fun `translate substitutes placeholders`() {
        assertEquals("Delete Alice?", i18n.translate("admin.users.details.delete.confirm", "name" to "Alice"))
        assertEquals(
            "21 to 40 of 95",
            formatTemplate("{start} to {end} of {total}", mapOf("start" to "21", "end" to "40", "total" to "95")),
        )
    }

    // ── Pagination ─────────────────────────────────────────────────────────

    @Test
    fun `page range covers the rows shown`() {
        assertEquals(PageRange(1, 20, 95), pageRange(offset = 0, rowCount = 20, total = 95))
        assertEquals(PageRange(81, 95, 95), pageRange(offset = 80, rowCount = 15, total = 95))
        assertEquals(PageRange(0, 0, 0), pageRange(offset = 0, rowCount = 0, total = 0))
    }

    // ── Forms and pickers ──────────────────────────────────────────────────

    @Test
    fun `user create requires a type and the required fields`() {
        val values = mapOf("username" to "ann", "email" to " ")

        assertFalse(isUserCreateComplete("customer", DEFAULT_USER_CREATE_FIELDS, values))
        assertFalse(isUserCreateComplete("", DEFAULT_USER_CREATE_FIELDS, values + ("email" to "a@b.c")))
        assertTrue(isUserCreateComplete("customer", DEFAULT_USER_CREATE_FIELDS, values + ("email" to "a@b.c")))
    }

    @Test
    fun `user create submits only the attributes with a value`() {
        val submission = userCreateSubmission("customer", mapOf("username" to "ann", "given_name" to "", "family_name" to "  "))

        assertEquals(UserCreateSubmission("customer", mapOf("username" to "ann")), submission)
    }

    @Test
    fun `identity picker leaves out identities already present`() {
        val candidates = listOf(IdentityCandidate("u-1", "Alice"), IdentityCandidate("u-2", "Bob"))

        assertEquals(listOf(IdentityCandidate("u-2", "Bob")), availableCandidates(candidates, listOf("u-1")))
        assertTrue(availableCandidates(null, emptyList()).isEmpty())
    }

    // ── Details ────────────────────────────────────────────────────────────

    @Test
    fun `user details hides secrets and edits only text attributes`() {
        val visible =
            visibleAttributes(mapOf("password" to "x", "email" to "a@b.c", "age" to 30, "tags" to listOf("a")), listOf("password"))

        assertEquals(listOf("email", "age", "tags"), visible.map { it.first })
        assertEquals(mapOf("email" to "a@b.c"), editableAttributes(visible))
        assertEquals("30", stringifyAttribute(30))
        assertEquals("[\"a\"]", stringifyAttribute(listOf("a")))
        assertEquals("", stringifyAttribute(null))
    }

    @Test
    fun `role permissions show only resource servers that grant something`() {
        val permissions = listOf(RoleResourcePermissions("rs-1", emptyList()), RoleResourcePermissions("rs-2", listOf("read")))

        assertEquals(listOf("rs-2"), grantedPermissions(permissions).map { it.resourceServerId })
    }

    @Test
    fun `role assignments list candidates from the given organization unit`() {
        assertEquals("ouId eq \"ou-1\"", roleAssignmentCandidateFilter("ou-1"))
        assertNull(roleAssignmentCandidateFilter(null))
        assertNull(roleAssignmentCandidateFilter(" "))
    }

    @Test
    fun `group members list candidates from the given organization unit`() {
        assertEquals("ouId eq \"ou-1\"", groupMemberCandidateFilter("ou-1"))
        assertNull(groupMemberCandidateFilter(null))
        assertNull(groupMemberCandidateFilter(" "))
    }

    @Test
    fun `admin test tags are stable`() {
        assertEquals("thunderid-admin-user-details-delete-confirm", adminTag("user-details", "delete", "confirm"))
        assertEquals("thunderid-admin-group-list", adminTag("group-list"))
    }

    // ── Permissions state ──────────────────────────────────────────────────

    @Test
    fun `granted permissions state tests with the hierarchy`() {
        val state = GrantedPermissionsState().apply { permissions = listOf("system:user") }

        assertTrue(state.can("system:user:view"))
        assertFalse(state.can("system:group:view"))
    }

    // ── Render state ───────────────────────────────────────────────────────

    @Test
    fun `role permissions state names resource servers and falls back to the identifier`() {
        val state =
            RolePermissionsState(
                permissions = listOf(RoleResourcePermissions("rs-1", listOf("read"))),
                isLoading = false,
                error = null,
                resourceServerNames = mapOf("rs-1" to "Orders API"),
            )

        assertEquals("Orders API", state.resourceServerName("rs-1"))
        assertEquals("rs-9", state.resourceServerName("rs-9"))
    }

    @Test
    fun `list state hands pagination back to the caller`() {
        var requested: Int? = null
        val state =
            AdminListState(
                rows = listOf("a", "b"),
                isLoading = false,
                error = null,
                limit = 20,
                offset = 20,
                totalResults = 45,
                selectedId = null,
                onPageChange = { requested = it },
                onSelect = null,
            )

        state.onPageChange?.invoke(40)

        assertEquals(40, requested)
        assertNull(state.onSelect)
    }

    @Test
    fun `list state carries the search for custom UIs`() {
        var applied: AdminSearch? = AdminSearch("username", "ada")
        val state =
            AdminListState(
                rows = emptyList<String>(),
                isLoading = false,
                error = null,
                limit = 20,
                offset = 0,
                totalResults = 0,
                selectedId = null,
                onPageChange = null,
                onSelect = null,
                search = applied,
                searchAttributes = listOf(AdminOption("Username", "username")),
                onSearch = { applied = it },
            )

        state.onSearch?.invoke(null)

        assertNull(applied)
        assertEquals(listOf("username"), state.searchAttributes.map { it.value })
    }
}
