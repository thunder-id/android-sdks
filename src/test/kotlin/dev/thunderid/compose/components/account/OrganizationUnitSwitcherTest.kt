// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.account

import dev.thunderid.android.EmbeddedFlowResponse
import dev.thunderid.android.FlowStatus
import dev.thunderid.android.IAMException
import dev.thunderid.android.ThunderIDErrorCode
import dev.thunderid.android.User
import dev.thunderid.android.api.account.AccessibleOrganizationUnit
import dev.thunderid.android.api.account.AccessibleOrganizationUnits
import dev.thunderid.android.api.account.AccessibleOrganizationUnitsOptions
import dev.thunderid.android.api.account.CurrentOrganizationUnit
import dev.thunderid.android.api.account.OrganizationUnitScope
import dev.thunderid.android.api.auth.OrganizationUnitSwitchResult
import dev.thunderid.compose.i18n.DefaultStrings
import dev.thunderid.compose.i18n.ThunderIDI18n
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrganizationUnitSwitcherTest {
    private val i18n = ThunderIDI18n()
    private val acme = AccessibleOrganizationUnit("ou-1", "acme", "Acme")
    private val beta = AccessibleOrganizationUnit("ou-2", "beta", "Beta")

    private var current = "ou-1"
    private val loads = mutableListOf<AccessibleOrganizationUnitsOptions>()
    private val switches = mutableListOf<String>()

    private fun controller(
        scope: OrganizationUnitScope? = null,
        limit: Int = 10,
        load: suspend (AccessibleOrganizationUnitsOptions) -> AccessibleOrganizationUnits = { options ->
            loads += options
            AccessibleOrganizationUnits(current, listOf(acme, beta), totalResults = 12, count = 2)
        },
        switch: suspend (AccessibleOrganizationUnit) -> OrganizationUnitSwitchResult? = { unit ->
            switches += unit.id
            current = unit.id
            OrganizationUnitSwitchResult.Switched(User(mapOf("sub" to "u1", "ouId" to unit.id)))
        },
        session: suspend () -> CurrentOrganizationUnit? = { null },
    ) = OrganizationUnitSwitcherController(i18n, scope, limit, load, switch, session)

    private fun OrganizationUnitSwitcherController.render() = state(onPageChange = {}, onSwitch = {})

    @Test
    fun `loads a page of the accessible units with the scope and marks the current one`() =
        runTest {
            val switcher = controller(scope = OrganizationUnitScope.SIBLINGS, limit = 2)

            switcher.reload(4)

            assertEquals(AccessibleOrganizationUnitsOptions(OrganizationUnitScope.SIBLINGS, 2, 4), loads.single())
            val state = switcher.render()
            assertEquals(listOf(acme, beta), state.organizationUnits)
            assertEquals("ou-1", state.current)
            assertEquals(4, state.offset)
            assertEquals(2, state.limit)
            assertEquals(12, state.totalResults)
            assertNull(state.error)
        }

    @Test
    fun `switching calls switchOrganizationUnit and reloads so the current unit moves`() =
        runTest {
            val switcher = controller()
            switcher.reload()

            val result = switcher.switchTo(beta)

            assertTrue(result is OrganizationUnitSwitchResult.Switched)
            assertEquals(listOf("ou-2"), switches)
            assertEquals("ou-2", switcher.render().current)
            assertNull(switcher.render().switchingId)
        }

    @Test
    fun `switching to the current unit does nothing`() =
        runTest {
            val switcher = controller()
            switcher.reload()

            assertNull(switcher.switchTo(acme))
            assertTrue(switches.isEmpty())
        }

    @Test
    fun `an app's onSelect takes the selection over`() =
        runTest {
            val picked = mutableListOf<String>()
            val switcher =
                controller(switch = { unit ->
                    picked += unit.id
                    null
                })
            switcher.reload()

            assertNull(switcher.switchTo(beta))
            assertEquals(listOf("ou-2"), picked)
            assertEquals(1, loads.size)
        }

    @Test
    fun `a failed switch shows a translated message, never the server's text`() =
        runTest {
            val notAccessible =
                controller(switch = { throw IAMException(ThunderIDErrorCode.ORGANIZATION_UNIT_UNAVAILABLE, "unauthorized_client") })
            notAccessible.reload()
            notAccessible.switchTo(beta)
            assertEquals("This organization unit is not available to this application.", notAccessible.render().error)

            val other = controller(switch = { throw IllegalStateException("pq: relation does not exist") })
            other.reload()
            other.switchTo(beta)
            assertEquals("Could not switch to this organization unit. Please try again.", other.render().error)
        }

    @Test
    fun `an embedded switch that needs the user asks the user to sign in again`() =
        runTest {
            val flow = EmbeddedFlowResponse(flowId = "e-1", flowStatus = FlowStatus.INCOMPLETE)
            val switcher = controller(switch = { OrganizationUnitSwitchResult.SignInRequired(flow) })
            switcher.reload()

            val result = switcher.switchTo(beta)

            assertEquals(flow, (result as OrganizationUnitSwitchResult.SignInRequired).response)
            assertEquals("Sign in again to switch to this organization unit.", switcher.render().error)
        }

    @Test
    fun `a failed load shows a translated message`() =
        runTest {
            val switcher = controller(load = { throw IAMException(ThunderIDErrorCode.SERVER_ERROR, "boom") })

            switcher.reload()

            assertEquals("Could not load your organization units. Please try again.", switcher.render().error)
            assertTrue(switcher.render().organizationUnits.isEmpty())
        }

    @Test
    fun `a custom render gets the units, paging and the switch action`() =
        runTest {
            val switcher = controller()
            switcher.reload()
            var switched: AccessibleOrganizationUnit? = null
            var page: Int? = null

            val state = switcher.state(onPageChange = { page = it }, onSwitch = { switched = it })
            state.onSwitch(beta)
            state.onPageChange(10)

            assertEquals(beta, switched)
            assertEquals(10, page)
        }

    @Test
    fun `a unit is named by its name, then its handle, then a placeholder`() {
        assertEquals("Acme", organizationUnitLabel(i18n, acme))
        assertEquals("acme", organizationUnitLabel(i18n, acme.copy(name = " ")))
        assertEquals("Unnamed organization unit", organizationUnitLabel(i18n, AccessibleOrganizationUnit("ou-3")))
    }

    @Test
    fun `names the current unit from the page, else from the session when it is the same unit`() =
        runTest {
            val listed = controller()
            listed.reload()
            assertEquals("Acme", listed.render().currentName)

            val offPage =
                controller(
                    load = { AccessibleOrganizationUnits(current, listOf(beta), totalResults = 12, count = 1) },
                    session = { CurrentOrganizationUnit("ou-1", "acme", "Acme Inc") },
                )
            offPage.reload()
            assertEquals("ou-1", offPage.render().current)
            assertEquals("Acme Inc", offPage.render().currentName)

            val otherUnit =
                controller(
                    load = { AccessibleOrganizationUnits(current, listOf(beta)) },
                    session = { CurrentOrganizationUnit("ou-9", name = "Nine") },
                )
            otherUnit.reload()
            assertNull(otherUnit.render().currentName)
        }

    @Test
    fun `takes the current unit from the session when the page names none`() =
        runTest {
            val switcher =
                controller(
                    load = { AccessibleOrganizationUnits(null, listOf(acme, beta)) },
                    session = { CurrentOrganizationUnit("ou-2", "beta") },
                )

            switcher.reload()

            assertEquals("ou-2", switcher.render().current)
            assertEquals("Beta", switcher.render().currentName)
            assertNull(switcher.switchTo(beta))
            assertTrue(switches.isEmpty())
        }

    @Test
    fun `a session that cannot be read leaves the current unit to the page`() =
        runTest {
            val switcher = controller(session = { throw IAMException(ThunderIDErrorCode.SERVER_ERROR, "boom") })

            switcher.reload()

            assertEquals("Acme", switcher.render().currentName)
            assertNull(switcher.render().error)
        }

    private fun fieldState(
        currentName: String? = null,
        isLoading: Boolean = false,
        switchingId: String? = null,
    ) = OrganizationUnitSwitcherState(
        organizationUnits = listOf(acme, beta),
        current = "ou-1",
        isLoading = isLoading,
        error = null,
        limit = 10,
        offset = 0,
        totalResults = 2,
        onPageChange = {},
        switchingId = switchingId,
        onSwitch = {},
        currentName = currentName,
    )

    @Test
    fun `the field shows the unit being switched to, the current unit, the loading text or the placeholder`() {
        assertEquals("Switching to Beta…", organizationUnitFieldText(i18n, fieldState(currentName = "Acme", switchingId = "ou-2")))
        assertEquals("Switching to ou-7…", organizationUnitFieldText(i18n, fieldState(switchingId = "ou-7")))
        assertEquals("Acme", organizationUnitFieldText(i18n, fieldState(currentName = "Acme", isLoading = true)))
        assertEquals("Loading organization units…", organizationUnitFieldText(i18n, fieldState(isLoading = true)))
        assertEquals("Select an organization", organizationUnitFieldText(i18n, fieldState()))
        assertEquals("Passage à Beta…", organizationUnitFieldText(ThunderIDI18n(language = "fr-FR"), fieldState(switchingId = "ou-2")))
    }

    @Test
    fun `ships every switcher string`() {
        listOf(
            "label",
            "placeholder",
            "switching",
            "loading",
            "empty",
            "unnamed",
            "previous",
            "next",
            "page",
            "errors.load",
            "errors.switch",
            "errors.notAccessible",
            "errors.signInRequired",
        ).forEach { assertTrue(it, DefaultStrings.all.containsKey("organizationUnitSwitcher.$it")) }
    }
}
