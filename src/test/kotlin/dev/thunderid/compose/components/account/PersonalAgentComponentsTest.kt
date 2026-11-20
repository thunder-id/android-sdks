// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.account

import com.google.gson.Gson
import dev.thunderid.android.api.management.Agent
import dev.thunderid.compose.components.management.agentOverview
import dev.thunderid.compose.components.management.suspensionPartyLabel
import dev.thunderid.compose.i18n.ThunderIDI18n
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalAgentComponentsTest {
    private val i18n = ThunderIDI18n()

    private fun agent(
        status: String = """{"value":"ACTIVE"}""",
        extra: String = "",
    ): Agent =
        Gson().fromJson(
            """{"id":"pa-1","ouId":"ou-1","type":"assistant","kind":"personal-assistant","clientId":"spend",
            "owner":{"id":"usr-1"},"attributes":{"name":"Spending","enabled":true,"limits":{"monthly":200}},
            "status":$status$extra}""",
            Agent::class.java,
        )

    private fun suspendedBy(vararg parties: String) =
        """{"value":"SUSPENDED","details":{"appliedBy":[${parties.joinToString(",") { "\"$it\"" }}]}}"""

    @Test
    fun `explains each suspension in force, the owner's first, and nothing for an unknown party`() {
        assertEquals(
            listOf(
                "You suspended this agent.",
                "Your organization suspended this agent. Only an administrator can lift that suspension.",
            ),
            personalAgentSuspensionNotices(i18n, agent(suspendedBy("organization", "owner", "someone-else"))),
        )
        assertTrue(personalAgentSuspensionNotices(i18n, agent()).isEmpty())
    }

    @Test
    fun `the overview shows the status, client, grant types and redirect URIs, or none`() {
        val withOauth =
            agent(
                extra =
                    ""","inboundAuthConfig":[{"type":"oauth2","config":{"grantTypes":
                    ["urn:openid:params:grant-type:ciba","refresh_token"],"redirectUris":[]}}]""",
            )

        assertEquals(
            listOf("Active", "spend", "Backchannel authentication (CIBA), Refresh token", "None"),
            personalAgentOverview(i18n, withOauth).map { it.value },
        )
        assertEquals(listOf("Active", "spend", "None", "None"), personalAgentOverview(i18n, agent()).map { it.value })
    }

    @Test
    fun `the attributes are sorted, with any value that is not text as JSON`() {
        assertEquals(
            listOf("enabled" to "true", "limits" to "{\"monthly\":200.0}", "name" to "Spending"),
            personalAgentAttributes(agent()).map { it.label to it.value },
        )
    }

    @Test
    fun `a grant type without a label keeps its value`() {
        assertEquals("Refresh token", personalAgentGrantTypeLabel(i18n, "refresh_token"))
        assertEquals("client_credentials", personalAgentGrantTypeLabel(i18n, "client_credentials"))
    }

    @Test
    fun `the admin overview shows the owner, the kind of a personal agent, and who holds it suspended`() {
        val rows = agentOverview(i18n, agent(suspendedBy("owner", "organization"))).associate { it.label to it.value }

        assertEquals("usr-1", rows["Owner"])
        assertEquals("Personal", rows["Kind"])
        assertEquals("Owner, Organization", rows["Suspended by"])

        val ordinary =
            Gson().fromJson("""{"id":"a-1","ouId":"ou-1","type":"bot"}""", Agent::class.java)
        val ordinaryRows = agentOverview(i18n, ordinary).associate { it.label to it.value }
        assertEquals("No owner", ordinaryRows["Owner"])
        assertFalse("Kind" in ordinaryRows)
        assertFalse("Suspended by" in ordinaryRows)
    }

    @Test
    fun `the admin overview names the organization for a suspension that names no party`() {
        listOf("""{"value":"SUSPENDED"}""", suspendedBy()).forEach { status ->
            val rows = agentOverview(i18n, agent(status)).associate { it.label to it.value }
            assertEquals("Organization", rows["Suspended by"])
        }
        val active = agentOverview(i18n, agent("""{"value":"ACTIVE"}""")).associate { it.label to it.value }
        assertFalse("Suspended by" in active)
    }

    @Test
    fun `an unknown suspending party shows its raw value`() {
        assertEquals("Owner's account suspension", suspensionPartyLabel(i18n, "cascade"))
        assertEquals("auditor", suspensionPartyLabel(i18n, "auditor"))
    }

    @Test
    fun `the personal agent strings are translated`() {
        val french = ThunderIDI18n(language = "fr-FR")

        assertTrue(french.resolve("user.agents.title") != i18n.resolve("user.agents.title"))
        assertTrue(french.resolve("admin.agents.kind.personal") != "admin.agents.kind.personal")
    }
}
