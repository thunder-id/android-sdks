// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.management

import dev.thunderid.android.IAMException
import dev.thunderid.android.ServerError
import dev.thunderid.android.ServerErrorMessage
import dev.thunderid.android.ThunderIDClient
import dev.thunderid.android.ThunderIDErrorCode
import dev.thunderid.android.api.management.OrganizationUnit
import dev.thunderid.android.api.management.Project
import dev.thunderid.android.api.management.UpdateProjectRequest
import dev.thunderid.compose.ThunderIDState
import dev.thunderid.compose.i18n.DefaultStrings
import dev.thunderid.compose.i18n.ThunderIDI18n
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectAndOrganizationUnitComponentsTest {
    private val i18n = ThunderIDI18n()
    private val shop =
        Project(id = "p-1", handle = "shop", displayName = "Online shop", description = "Storefront", createdAt = "2026-01-02")
    private val unit =
        OrganizationUnit(
            id = "ou-2",
            handle = "eng",
            name = "Engineering",
            description = "Builders",
            parent = "ou-root",
            themeId = "theme-1",
            authFlowId = "flow-1",
            tosUri = "https://example.com/tos",
        )

    // ── Current project ────────────────────────────────────────────────────

    @Test
    fun `the current project is kept on the SDK state and can be cleared`() {
        val state = ThunderIDState(mockk<ThunderIDClient>(), i18n, TestScope())
        assertNull(state.currentProject)

        state.setCurrentProject(shop)
        assertEquals("p-1", state.currentProject?.id)

        state.setCurrentProject(null)
        assertNull(state.currentProject)
    }

    @Test
    fun `create forms default to the app's unit, then the signed-in user's, never to a project`() {
        assertEquals("ou-app", defaultOuId("ou-app", "ou-claims", "ou-profile"))
        assertEquals("ou-claims", defaultOuId(null, "ou-claims", "ou-profile"))
        assertEquals("ou-profile", defaultOuId(null, null, "ou-profile"))
        assertNull(defaultOuId(null, null, null))
    }

    @Test
    fun `application and agent creates send the current project only when the app passes no unit`() {
        assertEquals(CreatePlacement(ouId = "ou-app", projectId = null), createPlacement("ou-app", "p-1", "ou-user"))
        assertEquals(CreatePlacement(ouId = null, projectId = "p-1"), createPlacement(null, "p-1", "ou-user"))
        assertEquals(CreatePlacement(ouId = "ou-user", projectId = null), createPlacement(null, null, "ou-user"))
        assertFalse(createPlacement(null, null, null).isKnown)
    }

    // ── Projects ───────────────────────────────────────────────────────────

    @Test
    fun `project handles follow the server's rule`() {
        listOf("shop", "a", "online-shop_2", "9lives").forEach { assertTrue(it, isValidProjectHandle(it)) }
        listOf("", "Shop", "-shop", "shop-", "shop space", "shöp").forEach { assertFalse(it, isValidProjectHandle(it)) }
    }

    @Test
    fun `project overview shows handle, name, description and creation time`() {
        assertEquals(
            listOf(
                AdminDetailItem("Handle", "shop", copyable = true),
                AdminDetailItem("Name", "Online shop"),
                AdminDetailItem("Description", "Storefront"),
                AdminDetailItem("Created", "2026-01-02"),
            ),
            projectOverview(i18n, shop),
        )
        assertEquals(2, projectOverview(i18n, shop.copy(description = null, createdAt = null)).size)
    }

    private fun serverError(code: String) =
        IAMException(
            ThunderIDErrorCode.INVALID_INPUT,
            "pq: server text",
            serverError = ServerError(code, ServerErrorMessage(key = "error.server", defaultValue = "server text")),
        )

    @Test
    fun `a failed project deletion has no refusal of its own and never shows the server text`() {
        assertEquals(
            "Could not delete the project. Please try again.",
            toManagementErrorMessage(i18n, serverError("PRJ-1013"), "admin.projects.details.delete.error"),
        )
        assertEquals(
            "You do not have permission to do this.",
            toManagementErrorMessage(i18n, IAMException(ThunderIDErrorCode.FORBIDDEN, "x"), "admin.projects.details.delete.error"),
        )
    }

    @Test
    fun `project conflicts are translated by the server's code`() {
        mapOf(
            "PRJ-1006" to "A project with this ID already exists.",
            "PRJ-1012" to "A project with this handle already exists.",
            "PRJ-1007" to "A project's handle cannot be changed.",
        ).forEach { (code, message) ->
            assertEquals(code, message, toProjectOrUnitErrorMessage(i18n, serverError(code), "admin.projects.create.error"))
        }
        assertEquals(
            "Could not create the project. Please try again.",
            toProjectOrUnitErrorMessage(i18n, serverError("PRJ-1003"), "admin.projects.create.error"),
        )
        assertEquals(
            "Could not create the project. Please try again.",
            toProjectOrUnitErrorMessage(i18n, IllegalStateException("boom"), "admin.projects.create.error"),
        )
        assertNull(toProjectOrUnitErrorMessage(i18n, null, "admin.projects.create.error"))
    }

    @Test
    fun `a custom project details render gets the record and only the allowed actions`() {
        val updates = mutableListOf<UpdateProjectRequest>()
        val state =
            ProjectDetailsState(
                project = shop,
                isLoading = false,
                error = null,
                isDeleting = false,
                isUpdating = false,
                onUpdate = { updates += it },
                onDelete = null,
            )

        state.onUpdate?.invoke(UpdateProjectRequest("Shop", null, "shop"))
        assertEquals("Shop", updates.single().displayName)
        assertNull(state.onDelete)
    }

    @Test
    fun `a custom project switcher render picks a project`() {
        val picked = mutableListOf<String>()
        val state = ProjectSwitcherState(listOf(shop), "p-1", false, null, 10, 0, 1, {}, { picked += it.id })

        state.onSelect(shop)
        assertEquals(listOf("p-1"), picked)
        assertEquals("p-1", state.current)
    }

    // ── Organization units ─────────────────────────────────────────────────

    @Test
    fun `organization unit overview names the parent, or says it is a top level unit`() {
        assertEquals(
            listOf(
                AdminDetailItem("Handle", "eng", copyable = true),
                AdminDetailItem("Description", "Builders"),
                AdminDetailItem("Parent", "ou-root"),
            ),
            organizationUnitOverview(i18n, unit),
        )
        assertEquals("None (top level)", organizationUnitOverview(i18n, unit.copy(parent = null)).last().value)
    }

    @Test
    fun `an organization unit edit keeps every field it does not change`() {
        val request = unit.toRequest(OrganizationUnitEdit("Platform", "platform", null))

        assertEquals("Platform", request.name)
        assertEquals("platform", request.handle)
        assertNull(request.description)
        assertEquals("ou-root", request.parent)
        assertEquals("theme-1", request.themeId)
        assertEquals("flow-1", request.authFlowId)
        assertEquals("https://example.com/tos", request.tosUri)
    }

    @Test
    fun `organization unit refusals and conflicts are translated by the server's code`() {
        val fallback = "admin.organization_units.details.delete.error"
        assertEquals(
            "This organization unit still has child units, users, groups or other resources. Remove them first.",
            toProjectOrUnitErrorMessage(i18n, serverError("OU-1006"), fallback),
        )
        assertEquals(
            "This organization unit is defined in declarative configuration and cannot be changed.",
            toProjectOrUnitErrorMessage(i18n, serverError("OU-1012"), fallback),
        )
        assertEquals(
            "An organization unit with this name already exists here.",
            toProjectOrUnitErrorMessage(i18n, serverError("OU-1004"), "admin.organization_units.create.error"),
        )
        assertEquals(
            "An organization unit with this handle already exists here.",
            toProjectOrUnitErrorMessage(i18n, serverError("OU-1008"), "admin.organization_units.create.error"),
        )
        // A plain bad request is not taken to mean the unit still holds resources.
        assertEquals(
            "Could not delete the organization unit. Please try again.",
            toProjectOrUnitErrorMessage(i18n, IAMException(ThunderIDErrorCode.INVALID_INPUT, "x"), fallback),
        )
        assertEquals(
            "Could not delete the organization unit. Please try again.",
            toProjectOrUnitErrorMessage(i18n, RuntimeException("x"), fallback),
        )
    }

    @Test
    fun `a custom organization unit details render gets members read only`() {
        val state =
            OrganizationUnitDetailsState(
                organizationUnit = unit,
                children = emptyList(),
                users = emptyList(),
                groups = emptyList(),
                roles = emptyList(),
                isLoading = false,
                error = null,
                isDeleting = false,
                isUpdating = false,
                onSelectChild = null,
                onUpdate = null,
                onDelete = null,
            )

        assertEquals("Engineering", state.organizationUnit?.name)
        assertNull(state.onUpdate)
        assertNull(state.onDelete)
    }

    @Test
    fun `ships every project and organization unit string`() {
        listOf(
            "admin.projects.list.title",
            "admin.projects.create.fields.display_name",
            "admin.projects.errors.handle_conflict",
            "admin.projects.switcher.current",
            "admin.organization_units.details.children",
            "admin.organization_units.details.delete.has_children_error",
            "admin.organization_units.errors.declarative",
        ).forEach { assertTrue(it, DefaultStrings.all.containsKey(it)) }
        assertEquals(
            "Delete Online shop?",
            i18n.translate(
                "admin.projects.details.delete.confirm",
                "name" to shop.displayName,
            ),
        )
    }

    @Test
    fun `agent search is offered only while no project scopes the list`() {
        assertTrue(agentListSearchable(searchable = true, projectId = null))
        assertFalse(agentListSearchable(searchable = true, projectId = "p-2"))
        assertFalse(agentListSearchable(searchable = false, projectId = null))
    }
}
