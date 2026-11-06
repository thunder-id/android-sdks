// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.management

import dev.thunderid.android.ThunderIDClient
import dev.thunderid.android.api.management.OverlayRule
import dev.thunderid.android.api.management.ResolvedOverlay
import dev.thunderid.android.api.management.SharingAllUnits
import dev.thunderid.android.api.management.SharingPolicy
import dev.thunderid.android.api.management.SharingPolicyListResponse
import dev.thunderid.android.api.management.SharingPolicyMode
import dev.thunderid.android.api.management.SharingSection
import dev.thunderid.android.api.management.SharingUnit
import dev.thunderid.compose.i18n.ThunderIDI18n
import dev.thunderid.compose.management.SharingResourceType
import dev.thunderid.compose.management.SharingTarget
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SharingPoliciesTest {
    private val i18n = ThunderIDI18n()

    private val selected =
        SharingPolicy(
            id = "sp-1",
            stage = "share",
            version = 3,
            allUnits = SharingAllUnits(excludedOuIds = listOf("ou-x")),
            childUnits =
                SharingSection(
                    mode = SharingPolicyMode.SELECTED,
                    units = listOf(SharingUnit("ou-1", "unit"), SharingUnit("ou-2", "unit")),
                ),
            otherTreeRoots = SharingSection(mode = SharingPolicyMode.ALL),
        )

    // ── Drafts ─────────────────────────────────────────────────────────────

    @Test
    fun `a draft for chosen child units sends each unit with its reach`() {
        val request = SharingPolicyDraft(SharingScope.SELECTED_CHILDREN, listOf("ou-1", "ou-2"), SharingReach.UNIT).toRequest()

        assertNull(request.otherTreeRoots)
        assertEquals(SharingPolicyMode.SELECTED, request.childUnits?.mode)
        assertEquals(listOf(SharingUnit("ou-1", "unit"), SharingUnit("ou-2", "unit")), request.childUnits?.units)
    }

    @Test
    fun `a draft for every other tree sends its exclusions and no reach`() {
        val request = SharingPolicyDraft(SharingScope.ALL_OTHER_ROOTS, listOf("ou-9")).toRequest()

        assertNull(request.childUnits)
        assertEquals(SharingSection(mode = SharingPolicyMode.ALL, excludedOuIds = listOf("ou-9")), request.otherTreeRoots)
        assertEquals(
            listOf(SharingUnit("ou-3")),
            SharingPolicyDraft(SharingScope.SELECTED_OTHER_ROOTS, listOf("ou-3")).toRequest().otherTreeRoots?.units,
        )
    }

    @Test
    fun `a chosen-units draft needs at least one unit`() {
        assertFalse(SharingPolicyDraft(SharingScope.SELECTED_CHILDREN).isValid)
        assertTrue(SharingPolicyDraft(SharingScope.ALL_CHILDREN).isValid)
    }

    @Test
    fun `editing a policy starts from its child units section and keeps its other sections and version`() {
        val draft = SharingPolicyDraft.from(selected)!!
        assertEquals(SharingPolicyDraft(SharingScope.SELECTED_CHILDREN, listOf("ou-1", "ou-2"), SharingReach.UNIT), draft)

        val update = draft.copy(ouIds = listOf("ou-1")).toUpdate(selected)

        assertEquals(3, update.version)
        assertEquals(listOf("ou-1"), update.childUnits?.units?.map { it.ouId })
        assertEquals(selected.otherTreeRoots, update.otherTreeRoots)
        assertEquals(selected.allUnits, update.allUnits)
        assertNull(SharingPolicyDraft.from(SharingPolicy(id = "sp-2", allUnits = SharingAllUnits())))
    }

    // ── Presentation ───────────────────────────────────────────────────────

    @Test
    fun `the scope shows every section as the server states it`() {
        assertEquals(
            "All organization units, except ou-x; Selected child units: ou-1, ou-2; All other trees",
            sharingScopeSummary(i18n, selected),
        )
        assertEquals("-", sharingScopeSummary(i18n, SharingPolicy(id = "sp-3")))
        assertEquals("Shared by the owner", sharingStageLabel(i18n, selected))
        assertEquals("Reshared", sharingStageLabel(i18n, selected.copy(stage = "reshare")))
    }

    @Test
    fun `read-only policies are never offered for change`() {
        assertTrue(isSharingPolicyEditable(selected, allowed = true))
        assertFalse(isSharingPolicyEditable(selected, allowed = false))
        assertFalse(isSharingPolicyEditable(selected.copy(isReadOnly = true), allowed = true))
    }

    @Test
    fun `overlay rules show ownership, visibility and each field's rule`() {
        val overlay =
            ResolvedOverlay(
                ouId = "ou-1",
                isOwned = false,
                visible = true,
                rules = mapOf("assignments" to OverlayRule(editable = true, value = listOf("viewer"))),
            )

        assertEquals(
            listOf(
                AdminDetailItem("Owned", "No"),
                AdminDetailItem("Visible", "Yes"),
                AdminDetailItem("assignments", "viewer (editable)"),
            ),
            overlayItems(i18n, overlay),
        )
        assertEquals("Rules for ou-1", i18n.translate("admin.sharing.overlay.title", "unit" to "ou-1"))
    }

    @Test
    fun `a custom render gets the actions only when editable`() {
        val readOnly =
            SharingPoliciesState(listOf(selected), false, null, 20, 0, 1, null, null, false, null, null, null)

        assertNull(readOnly.onCreate)
        assertNull(readOnly.onUpdate)
        assertNull(readOnly.onDelete)
    }

    // ── Server operations ──────────────────────────────────────────────────

    @Test
    fun `each resource type runs its own client's sharing operations`() =
        runTest {
            val client = mockk<ThunderIDClient>(relaxed = true)
            val page = SharingPolicyListResponse(totalResults = 1, count = 1, policies = listOf(selected))
            coEvery { client.roles.listSharingPolicies("r-1", 5, 0, null) } returns page
            coEvery { client.flows.getOverlayRules("f-1", "ou-1", null) } returns ResolvedOverlay(ouId = "ou-1")

            assertEquals(page, SharingTarget(client, SharingResourceType.ROLE, "r-1", null).list(5, 0))
            assertEquals("ou-1", SharingTarget(client, SharingResourceType.FLOW, "f-1", null).overlayRules("ou-1").ouId)
            SharingTarget(client, SharingResourceType.USER, "u-1", null).delete("sp-1")
            SharingTarget(client, SharingResourceType.APPLICATION, "a-1", null).create(SharingPolicyDraft().toRequest())

            coVerify { client.users.deleteSharingPolicy("u-1", "sp-1", null) }
            coVerify { client.applications.createSharingPolicy("a-1", any(), null) }
        }

    @Test
    fun `failures use the sharing messages`() {
        val failure = IllegalStateException("pq: server text")
        assertEquals(
            "Could not load the sharing policies. Please try again.",
            toManagementErrorMessage(i18n, failure, "admin.sharing.error"),
        )
        assertEquals(
            "Could not delete the sharing policy. Please try again.",
            toManagementErrorMessage(i18n, failure, "admin.sharing.delete.error"),
        )
    }
}
