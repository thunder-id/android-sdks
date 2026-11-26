// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.account

import com.google.gson.Gson
import com.google.gson.JsonParser
import dev.thunderid.android.IAMException
import dev.thunderid.android.InMemoryStorageAdapter
import dev.thunderid.android.ThunderIDClient
import dev.thunderid.android.ThunderIDConfig
import dev.thunderid.android.ThunderIDErrorCode
import dev.thunderid.android.ThunderIDFetcher
import dev.thunderid.android.ThunderIDHttpConfig
import dev.thunderid.android.ThunderIDHttpRequest
import dev.thunderid.android.ThunderIDHttpResponse
import dev.thunderid.android.TokenResponse
import dev.thunderid.android.api.management.AccessStatus
import dev.thunderid.android.api.management.AccessStatusDetails
import dev.thunderid.android.api.management.AccessStatusValue
import dev.thunderid.android.api.management.AccountLifecycleAction
import dev.thunderid.android.api.management.AccountLifecycleSubjectKind
import dev.thunderid.android.api.management.AdministrationFlowConfigKey
import dev.thunderid.android.api.management.Agent
import dev.thunderid.android.api.management.AgentKind
import dev.thunderid.android.api.management.AgentListResponse
import dev.thunderid.android.api.management.SuspensionAppliedBy
import dev.thunderid.android.api.management.accountLifecycleActions
import dev.thunderid.android.backend.FakeBackend
import dev.thunderid.android.backend.response
import dev.thunderid.android.token.TokenStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PersonalAgentsApiTest {
    private val fake = FakeBackend()
    private val storage = InMemoryStorageAdapter()
    private val client = ThunderIDClient(fake)

    /** Records any request that reaches the management `http.fetcher`, which `/users/me/agents` must never use. */
    private val managementFetcher =
        object : ThunderIDFetcher {
            val requests = mutableListOf<ThunderIDHttpRequest>()

            override suspend fun fetch(request: ThunderIDHttpRequest): ThunderIDHttpResponse {
                requests += request
                return ThunderIDHttpResponse(500, "")
            }
        }

    private suspend fun signedIn(): ThunderIDClient {
        client.initialize(
            ThunderIDConfig(baseUrl = BASE, clientId = "app", http = ThunderIDHttpConfig(managementFetcher)),
            storage,
        )
        TokenStore(storage).save(TokenResponse(accessToken = "user-token", tokenType = "Bearer", expiresIn = 3600))
        return client
    }

    private suspend inline fun assertFails(
        code: ThunderIDErrorCode,
        block: () -> Unit,
    ) {
        try {
            block()
        } catch (e: IAMException) {
            assertEquals(code, e.code)
            return
        }
        fail("Expected IAMException $code")
    }

    @Test
    fun `lists the user's agents of a kind with the user's token, through the SDK's own transport`() =
        runTest {
            fake.on(
                "GET",
                "$BASE/users/me/agents?kind=personal-assistant&limit=5&offset=10",
                response(
                    200,
                    """{"totalResults":11,"startIndex":11,"count":1,"agents":[{"id":"pa-1","ouId":"ou-1",
                    "type":"assistant","kind":"personal-assistant","status":{"value":"ACTIVE"}}]}""",
                ),
            )

            val page: AgentListResponse = signedIn().getMeAgents(AgentKind.PERSONAL, limit = 5, offset = 10)

            assertEquals(11, page.totalResults)
            assertTrue(page.agents.single().isPersonal)
            assertEquals("Bearer user-token", fake.requests.single().headers["Authorization"])
            assertTrue(managementFetcher.requests.isEmpty())
        }

    @Test
    fun `lists every kind without a query`() =
        runTest {
            fake.on("GET", "$BASE/users/me/agents", response(200, """{"totalResults":0,"startIndex":1,"count":0,"agents":[]}"""))

            assertTrue(signedIn().getMeAgents().agents.isEmpty())
        }

    @Test
    fun `reads one agent with its display name, encoding the identifier`() =
        runTest {
            fake.on(
                "GET",
                "$BASE/users/me/agents/pa%2F1?include=display",
                response(
                    200,
                    """{"id":"pa/1","ouId":"ou-1","type":"assistant","kind":"personal-assistant","display":"Spending",
                    "owner":{"id":"usr-1","idType":"uuid","isLocal":true},"loginConsent":{"alwaysPrompt":true},
                    "status":{"value":"SUSPENDED","details":{"reason":"OWNER_SUSPENSION","appliedBy":["owner","cascade"]}}}""",
                ),
            )

            val agent = signedIn().getMeAgent("pa/1")

            assertEquals("usr-1", agent.owner?.id)
            assertEquals(true, agent.owner?.isLocal)
            assertEquals(true, agent.loginConsent?.alwaysPrompt)
            assertTrue(agent.isPersonal)
            assertEquals(listOf(SuspensionAppliedBy.OWNER, SuspensionAppliedBy.CASCADE), agent.status?.suspendedBy)
        }

    @Test
    fun `update sends the owner-editable fields whole, with the lists even when empty`() =
        runTest {
            fake.on("PUT", "$BASE/users/me/agents/pa-1", response(200, """{"id":"pa-1","ouId":"ou-1","type":"assistant"}"""))

            signedIn().updateMeAgent(
                "pa-1",
                UpdateMeAgentRequest(
                    attributes = mapOf("name" to "Spending"),
                    description = "",
                    grantTypes = emptyList(),
                    redirectUris = emptyList(),
                ),
            )

            val request = fake.requests.single()
            val body = JsonParser.parseString(request.body).asJsonObject
            assertEquals("PUT", request.method)
            assertEquals("Spending", body.getAsJsonObject("attributes")["name"].asString)
            assertEquals(0, body.getAsJsonArray("grantTypes").size())
            assertEquals(0, body.getAsJsonArray("redirectUris").size())
            assertEquals("", body["description"].asString)
        }

    @Test
    fun `deletes, suspends and unsuspends one agent, and suspends them all`() =
        runTest {
            fake.on("DELETE", "$BASE/users/me/agents/pa-1", response(204))
            fake.on("POST", "$BASE/users/me/agents/pa-1/suspend", response(204))
            fake.on("POST", "$BASE/users/me/agents/pa-1/unsuspend", response(204))
            fake.on("POST", "$BASE/users/me/agents/suspend", response(200, """{"suspendedCount":3}"""))
            val client = signedIn()

            client.deleteMeAgent("pa-1")
            client.suspendMeAgent("pa-1")
            client.unsuspendMeAgent("pa-1")
            val all = client.suspendAllMeAgents()

            assertEquals(3, all.suspendedCount)
            assertEquals(
                listOf(
                    "DELETE $BASE/users/me/agents/pa-1",
                    "POST $BASE/users/me/agents/pa-1/suspend",
                    "POST $BASE/users/me/agents/pa-1/unsuspend",
                    "POST $BASE/users/me/agents/suspend",
                ),
                fake.requests.map { "${it.method} ${it.url}" },
            )
            assertTrue(managementFetcher.requests.isEmpty())
        }

    @Test
    fun `refuses a blank agent identifier before any request`() =
        runTest {
            val client = signedIn()

            assertFails(ThunderIDErrorCode.INVALID_INPUT) { client.getMeAgent(" ") }
            assertFails(ThunderIDErrorCode.INVALID_INPUT) { client.suspendMeAgent("") }
            assertTrue(fake.requests.isEmpty())
        }

    @Test
    fun `tells a missing permission from a missing agent`() =
        runTest {
            fake.on("GET", "$BASE/users/me/agents/pa-1?include=display", response(403, "{}"))
            fake.on("GET", "$BASE/users/me/agents/pa-2?include=display", response(404, "{}"))
            val client = signedIn()

            assertFails(ThunderIDErrorCode.FORBIDDEN) { client.getMeAgent("pa-1") }
            assertFails(ThunderIDErrorCode.NOT_FOUND) { client.getMeAgent("pa-2") }
        }

    @Test
    fun `fails before initialization`() =
        runTest {
            assertFails(ThunderIDErrorCode.SDK_NOT_INITIALIZED) { client.getMeAgents() }
            assertFails(ThunderIDErrorCode.SDK_NOT_INITIALIZED) { client.suspendAllMeAgents() }
        }

    @Test
    fun `the owner unsuspends only their own suspension`() {
        fun suspended(vararg parties: String) = AccessStatus(AccessStatusValue.SUSPENDED, AccessStatusDetails(appliedBy = parties.toList()))

        assertEquals(MeAgentSuspensionAction.SUSPEND, MeAgentSuspensionAction.available(null))
        assertEquals(MeAgentSuspensionAction.SUSPEND, MeAgentSuspensionAction.available(AccessStatus(AccessStatusValue.ACTIVE)))
        assertEquals(MeAgentSuspensionAction.UNSUSPEND, MeAgentSuspensionAction.available(suspended("owner")))
        assertEquals(MeAgentSuspensionAction.UNSUSPEND, MeAgentSuspensionAction.available(suspended("organization", "owner")))
        assertEquals(MeAgentSuspensionAction.SUSPEND, MeAgentSuspensionAction.available(suspended("organization")))
        assertEquals(MeAgentSuspensionAction.SUSPEND, MeAgentSuspensionAction.available(suspended("cascade")))
        // A party is read only while the status is suspended.
        val stale = AccessStatus(AccessStatusValue.ACTIVE, AccessStatusDetails(appliedBy = listOf("owner")))
        assertTrue(stale.suspendedBy.isEmpty())
    }

    @Test
    fun `the organization is offered its own suspension when only others hold the agent suspended`() {
        val flows =
            mapOf(
                AdministrationFlowConfigKey.AGENT_SUSPEND to "f-suspend",
                AdministrationFlowConfigKey.AGENT_UNSUSPEND to "f-unsuspend",
            )

        fun actions(appliedBy: List<String>?) =
            accountLifecycleActions(AccountLifecycleSubjectKind.AGENT, AccessStatusValue.SUSPENDED, flows, appliedBy)

        assertEquals(listOf(AccountLifecycleAction.SUSPEND), actions(listOf("owner")))
        assertEquals(listOf(AccountLifecycleAction.SUSPEND), actions(listOf("owner", "cascade")))
        assertEquals(listOf(AccountLifecycleAction.UNSUSPEND), actions(listOf("organization", "owner")))
        assertEquals(listOf(AccountLifecycleAction.UNSUSPEND), actions(null))
        assertEquals(listOf(AccountLifecycleAction.UNSUSPEND), actions(emptyList()))
    }

    @Test
    fun `the edit form starts from the agent and sends back what it does not edit`() {
        val agent =
            Gson().fromJson(
                """{"id":"pa-1","ouId":"ou-1","type":"assistant","description":" Pays bills ",
                "attributes":{"name":"Spending","model":"planner-v1","limits":{"monthly":200}},
                "inboundAuthConfig":[{"type":"oauth2","config":{"grantTypes":["authorization_code"],
                "redirectUris":["https://a.example/cb","https://b.example/cb"]}}]}""",
                Agent::class.java,
            )

        val values = PersonalAgentFormValues.from(agent)

        assertEquals("Spending", values.name)
        assertEquals(mapOf("model" to "planner-v1"), values.attributes)
        assertEquals(listOf(PersonalAgentGrantTypes.AUTHORIZATION_CODE), values.grantTypes)
        assertEquals("https://a.example/cb\nhttps://b.example/cb", values.redirectUris)
        assertTrue(values.isValid)

        val request = values.copy(name = "  Bills  ", attributes = mapOf("model" to "planner-v2")).updateRequest(agent)

        assertEquals("Bills", request.attributes["name"])
        assertEquals("planner-v2", request.attributes["model"])
        assertEquals(mapOf("monthly" to 200.0), request.attributes["limits"])
        assertEquals("Pays bills", request.description)
        assertEquals(listOf("https://a.example/cb", "https://b.example/cb"), request.redirectUris)
    }

    @Test
    fun `the edit form needs a name, and a redirect URI for the authorization code grant`() {
        val values = PersonalAgentFormValues("Spending", "", emptyMap(), emptyList(), "")

        assertTrue(values.isValid)
        assertFalse(values.copy(name = "  ").isValid)
        assertFalse(values.copy(grantTypes = listOf(PersonalAgentGrantTypes.AUTHORIZATION_CODE)).isValid)
        assertEquals(
            listOf("https://a.example/cb?pair=a,b", "https://b.example/cb"),
            values.copy(redirectUris = " https://a.example/cb?pair=a,b \n\nhttps://b.example/cb ").redirectUriList,
        )
    }

    @Test
    fun `the edit form keeps a redirect URI that holds a comma`() {
        val agent =
            Gson().fromJson(
                """{"id":"pa-1","ouId":"ou-1","type":"assistant","inboundAuthConfig":[{"type":"oauth2","config":""" +
                    """{"grantTypes":["authorization_code"],"redirectUris":["https://a.example/cb?pair=a,b","https://b.example/cb"]}}]}""",
                Agent::class.java,
            )

        assertEquals(
            listOf("https://a.example/cb?pair=a,b", "https://b.example/cb"),
            PersonalAgentFormValues.from(agent).updateRequest(agent).redirectUris,
        )
    }

    @Test
    fun `an agent whose grants were all removed reads as having none`() {
        val agent =
            Gson().fromJson(
                """{"id":"pa-1","ouId":"ou-1","type":"assistant","inboundAuthConfig":[{"type":"oauth2","config":{}}]}""",
                Agent::class.java,
            )

        assertTrue(PersonalAgentFormValues.from(agent).grantTypes.isEmpty())
        assertNull(agent.kind)
        assertFalse(agent.isPersonal)
    }
}
