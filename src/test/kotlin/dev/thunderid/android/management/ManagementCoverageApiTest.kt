// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.management

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import dev.thunderid.android.IAMException
import dev.thunderid.android.InMemoryStorageAdapter
import dev.thunderid.android.ThunderIDClient
import dev.thunderid.android.ThunderIDConfig
import dev.thunderid.android.ThunderIDEndpoints
import dev.thunderid.android.ThunderIDErrorCode
import dev.thunderid.android.ThunderIDFetcher
import dev.thunderid.android.ThunderIDHttpConfig
import dev.thunderid.android.ThunderIDHttpRequest
import dev.thunderid.android.ThunderIDHttpResponse
import dev.thunderid.android.TokenResponse
import dev.thunderid.android.token.TokenStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

private const val BASE_URL = "https://localhost:8090"

/**
 * Answers like the server: the flow configuration, the administration flow listing, flow execution, and
 * [resource] for every other request. Records every request.
 */
private class CoverageFetcher(
    val config: Map<String, String> = emptyMap(),
    val flows: Map<String, String> = emptyMap(),
    val execution: String = """{"flowStatus":"COMPLETE","data":{"additionalData":{}}}""",
    val resource: String = "{}",
) : ThunderIDFetcher {
    val requests = mutableListOf<ThunderIDHttpRequest>()

    override suspend fun fetch(request: ThunderIDHttpRequest): ThunderIDHttpResponse {
        requests += request
        val body =
            when {
                request.url == "$BASE_URL/server-config/flow" -> {
                    Gson().toJson(mapOf("merged" to config.mapValues { mapOf("defaultHandle" to it.value) }))
                }

                request.url.startsWith("$BASE_URL/flows?flowType=ADMINISTRATION") -> {
                    val items = flows.map { (handle, id) -> mapOf("id" to id, "handle" to handle, "flowType" to "ADMINISTRATION") }
                    Gson().toJson(mapOf("flows" to items))
                }

                request.url == "$BASE_URL/flow/execute" -> {
                    execution
                }

                else -> {
                    resource
                }
            }
        return ThunderIDHttpResponse(if (request.method == "DELETE") 204 else 200, body)
    }

    fun calls() = requests.map { "${it.method} ${it.url}" }

    fun bodies(): List<JsonElement?> = requests.map { it.body?.let(::json) }

    fun executions(): List<JsonElement> = requests.filter { it.url == "$BASE_URL/flow/execute" }.map { json(it.body) }
}

private fun json(text: String?): JsonElement = JsonParser.parseString(text)

private fun execution(
    flowId: String,
    vararg inputs: Pair<String, String>,
): JsonElement = json(Gson().toJson(mapOf("flowId" to flowId, "inputs" to mapOf(*inputs))))

class ManagementCoverageApiTest {
    private suspend fun client(
        fetcher: ThunderIDFetcher,
        endpoints: ThunderIDEndpoints = ThunderIDEndpoints(),
    ): ThunderIDClient {
        val storage = InMemoryStorageAdapter()
        TokenStore(storage).save(TokenResponse(accessToken = "admin-token", tokenType = "Bearer", expiresIn = 3600))
        val config = ThunderIDConfig(baseUrl = BASE_URL, clientId = "client", endpoints = endpoints, http = ThunderIDHttpConfig(fetcher))
        return ThunderIDClient().apply { initialize(config, storage) }
    }

    private suspend inline fun <reified E : Throwable> expectError(block: () -> Unit): E {
        try {
            block()
        } catch (e: Throwable) {
            if (e is E) return e
            throw e
        }
        fail("Expected ${E::class.simpleName}")
        throw IllegalStateException()
    }

    // MARK: - Organization units

    @Test
    fun `organization unit operations call the right endpoints`() =
        runTest {
            val fetcher = CoverageFetcher()
            val ous = client(fetcher).organizationUnits
            val request = OrganizationUnitRequest(handle = "eng", name = "Engineering", parent = null)
            val byPath = OrganizationUnitByPathRequest(handle = "fe", name = "Frontend")

            ous.list(limit = 10, offset = 20, filter = "handle sw \"eng\"")
            ous.get("ou-1")
            ous.getByPath("engineering/frontend")
            ous.create(request)
            ous.create(request.copy(parent = "ou-1"))
            ous.update("ou-2", request)
            ous.updateByPath("engineering/frontend", byPath)
            ous.delete("ou-2")
            ous.deleteByPath("engineering/frontend")
            ous.listChildren("ou-1", limit = 5)
            ous.listChildrenByPath("engineering", offset = 5)
            ous.listUsers("ou-1")
            ous.listUsersByPath("engineering", limit = 2)
            ous.listGroups("ou-1")
            ous.listGroupsByPath("engineering")
            ous.listRoles("ou-1", offset = 1)
            ous.listRolesByPath("engineering")

            assertEquals(
                listOf(
                    "GET $BASE_URL/organization-units?limit=10&offset=20&filter=handle%20sw%20%22eng%22",
                    "GET $BASE_URL/organization-units/ou-1",
                    "GET $BASE_URL/organization-units/tree/engineering/frontend",
                    "POST $BASE_URL/organization-units",
                    "POST $BASE_URL/organization-units",
                    "PUT $BASE_URL/organization-units/ou-2",
                    "PUT $BASE_URL/organization-units/tree/engineering/frontend",
                    "DELETE $BASE_URL/organization-units/ou-2",
                    "DELETE $BASE_URL/organization-units/tree/engineering/frontend",
                    "GET $BASE_URL/organization-units/ou-1/ous?limit=5",
                    "GET $BASE_URL/organization-units/tree/engineering/ous?offset=5",
                    "GET $BASE_URL/organization-units/ou-1/users?include=display",
                    "GET $BASE_URL/organization-units/tree/engineering/users?limit=2&include=display",
                    "GET $BASE_URL/organization-units/ou-1/groups",
                    "GET $BASE_URL/organization-units/tree/engineering/groups",
                    "GET $BASE_URL/organization-units/ou-1/roles?offset=1",
                    "GET $BASE_URL/organization-units/tree/engineering/roles",
                ),
                fetcher.calls(),
            )
            val bodies = fetcher.bodies()
            // A root organization unit states its parent as null, which the server requires.
            assertEquals(json("""{"handle":"eng","name":"Engineering","parent":null}"""), bodies[3])
            assertEquals(json("""{"handle":"eng","name":"Engineering","parent":"ou-1"}"""), bodies[4])
            assertEquals(json("""{"handle":"eng","name":"Engineering","parent":null}"""), bodies[5])
            assertEquals(json("""{"handle":"fe","name":"Frontend"}"""), bodies[6])
            assertNull(bodies[8])
        }

    @Test
    fun `encodes each handle of a tree path and keeps the slashes`() =
        runTest {
            val fetcher = CoverageFetcher()
            val client = client(fetcher)

            client.organizationUnits.getByPath("/r&d/front end/")
            client.users.listByPath("a b/c?d")

            assertEquals(
                listOf(
                    "GET $BASE_URL/organization-units/tree/r%26d/front%20end",
                    "GET $BASE_URL/users/tree/a%20b/c%3Fd?include=display",
                ),
                fetcher.calls(),
            )
        }

    @Test
    fun `refuses an empty tree path or an empty handle in it without a request`() =
        runTest {
            val fetcher = CoverageFetcher()
            val ous = client(fetcher).organizationUnits

            listOf("", "/", "a//b").forEach { path ->
                val error = expectError<IAMException> { ous.getByPath(path) }
                assertEquals(ThunderIDErrorCode.INVALID_INPUT, error.code)
            }
            assertEquals(ThunderIDErrorCode.INVALID_INPUT, expectError<IAMException> { ous.listUsers(" ") }.code)
            assertTrue(fetcher.requests.isEmpty())
        }

    @Test
    fun `organization unit responses parse`() =
        runTest {
            val list =
                client(
                    CoverageFetcher(
                        resource =
                            """{"totalResults":3,"startIndex":1,"count":1,"organizationUnits":[{"id":"ou-1","handle":"eng",""" +
                                """"name":"Engineering","isReadOnly":false}],"links":[{"href":"organization-units?offset=1","rel":"next"}]}""",
                    ),
                ).organizationUnits.list()
            val users =
                client(
                    CoverageFetcher(
                        resource =
                            """{"totalResults":1,"count":1,"users":""" +
                                """[{"id":"u-1","type":"employee","display":"Maya","isOwned":false}]}""",
                    ),
                ).organizationUnits.listUsers("ou-1")

            assertEquals("eng", list.organizationUnits.single().handle)
            assertEquals("next", list.links?.single()?.rel)
            assertEquals(false, users.users.single().isOwned)
            assertEquals("Maya", users.users.single().display)
        }

    @Test
    fun `uses the organization units override`() =
        runTest {
            val fetcher = CoverageFetcher()
            val client = client(fetcher, ThunderIDEndpoints(organizationUnits = "https://rs.example.com/v2/ous/"))

            client.organizationUnits.getByPath("a/b")
            client.organizationUnits.listRoles("ou-1")

            assertEquals(
                listOf("GET https://rs.example.com/v2/ous/tree/a/b", "GET https://rs.example.com/v2/ous/ou-1/roles"),
                fetcher.calls(),
            )
        }

    // MARK: - Resource servers

    @Test
    fun `resource server, resource and action operations call the right endpoints`() =
        runTest {
            val fetcher = CoverageFetcher()
            val rs = client(fetcher).resourceServers

            rs.list(limit = 10)
            rs.get("rs-1")
            rs.create(CreateResourceServerRequest(name = "Booking", identifier = "https://api.example.com", ouId = "ou-1"))
            rs.update("rs-1", UpdateResourceServerRequest(name = "Booking", ouId = "ou-1"))
            rs.delete("rs-1")
            rs.listResources("rs-1", parentId = "res-0", limit = 5)
            rs.getResource("rs-1", "res-1")
            rs.createResource("rs-1", CreateResourceRequest(name = "Reservations", handle = "reservations"))
            rs.updateResource("rs-1", "res-1", UpdateResourceRequest(name = "Bookings"))
            rs.deleteResource("rs-1", "res-1")
            rs.listActions("rs-1", kind = ActionKind.TOOL)
            rs.listActions("rs-1", resourceId = "res-1", offset = 3)
            rs.getAction("rs-1", "act-1")
            rs.getAction("rs-1", "act-1", resourceId = "res-1")
            rs.createAction("rs-1", CreateActionRequest(name = "Create", handle = "create", kind = ActionKind.RESOURCE))
            rs.createAction("rs-1", CreateActionRequest(name = "Read", handle = "read"), resourceId = "res-1")
            rs.updateAction("rs-1", "act-1", UpdateActionRequest(description = "d"))
            rs.updateAction("rs-1", "act-1", UpdateActionRequest(name = "n"), resourceId = "res-1")

            assertEquals(
                listOf(
                    "GET $BASE_URL/resource-servers?limit=10",
                    "GET $BASE_URL/resource-servers/rs-1",
                    "POST $BASE_URL/resource-servers",
                    "PUT $BASE_URL/resource-servers/rs-1",
                    "DELETE $BASE_URL/resource-servers/rs-1",
                    "GET $BASE_URL/resource-servers/rs-1/resources?parentId=res-0&limit=5",
                    "GET $BASE_URL/resource-servers/rs-1/resources/res-1",
                    "POST $BASE_URL/resource-servers/rs-1/resources",
                    "PUT $BASE_URL/resource-servers/rs-1/resources/res-1",
                    "DELETE $BASE_URL/resource-servers/rs-1/resources/res-1",
                    "GET $BASE_URL/resource-servers/rs-1/actions?kind=tool",
                    "GET $BASE_URL/resource-servers/rs-1/resources/res-1/actions?offset=3",
                    "GET $BASE_URL/resource-servers/rs-1/actions/act-1",
                    "GET $BASE_URL/resource-servers/rs-1/resources/res-1/actions/act-1",
                    "POST $BASE_URL/resource-servers/rs-1/actions",
                    "POST $BASE_URL/resource-servers/rs-1/resources/res-1/actions",
                    "PUT $BASE_URL/resource-servers/rs-1/actions/act-1",
                    "PUT $BASE_URL/resource-servers/rs-1/resources/res-1/actions/act-1",
                ),
                fetcher.calls(),
            )
            val bodies = fetcher.bodies()
            assertEquals(json("""{"name":"Booking","identifier":"https://api.example.com","ouId":"ou-1"}"""), bodies[2])
            assertEquals(json("""{"name":"Reservations","handle":"reservations"}"""), bodies[7])
            assertEquals(json("""{"name":"Bookings"}"""), bodies[8])
            assertEquals(json("""{"name":"Create","handle":"create","kind":"resource"}"""), bodies[14])
            assertEquals(json("""{"description":"d"}"""), bodies[16])
        }

    @Test
    fun `resource server responses parse`() =
        runTest {
            val server =
                client(
                    CoverageFetcher(
                        resource =
                            """{"id":"rs-1","name":"MCP","identifier":"urn:mcp","ouId":"ou-1","type":"MCP","delimiter":":",""" +
                                """"authorizationEngine":{"type":"authzen_pdp","properties":{"pdpConnectionId":"c-1"}}}""",
                    ),
                ).resourceServers.get("rs-1")
            val actions =
                client(
                    CoverageFetcher(
                        resource =
                            """{"totalResults":1,"count":1,"actions":[{"id":"a-1","name":"Run","handle":"run",""" +
                                """"permission":"tools:run","kind":"tool"}]}""",
                    ),
                ).resourceServers.listActions("rs-1")

            assertEquals(ResourceServerType.MCP, server.type)
            assertEquals("c-1", server.authorizationEngine?.properties?.pdpConnectionId)
            assertEquals(ActionKind.TOOL, actions.actions.single().kind)
            assertEquals("tools:run", actions.actions.single().permission)
        }

    @Test
    fun `deletes an action natively by default`() =
        runTest {
            val fetcher = CoverageFetcher(config = mapOf(AdministrationFlowConfigKey.ACTION_DELETION to "delete-action"))
            val rs = client(fetcher).resourceServers

            rs.deleteAction("rs-1", "act-1")
            rs.deleteAction("rs-1", "act-2", resourceId = "res-1")

            assertEquals(
                listOf(
                    "DELETE $BASE_URL/resource-servers/rs-1/actions/act-1",
                    "DELETE $BASE_URL/resource-servers/rs-1/resources/res-1/actions/act-2",
                ),
                fetcher.calls(),
            )
        }

    @Test
    fun `deletes an action through its flow in flow mode`() =
        runTest {
            val fetcher =
                CoverageFetcher(
                    config = mapOf(AdministrationFlowConfigKey.ACTION_DELETION to "delete-action"),
                    flows = mapOf("delete-action" to "flow-act"),
                )
            val rs = client(fetcher, ThunderIDEndpoints(resourceServers = "https://rs.example.com/v2/resource-servers")).resourceServers

            rs.deleteAction("rs-1", "act-1", administrationMode = AdministrationMode.FLOW)
            rs.deleteAction("rs-1", "act-2", resourceId = "res-1", administrationMode = AdministrationMode.FLOW)

            assertFalse(fetcher.requests.any { it.method == "DELETE" })
            assertTrue(fetcher.requests.all { it.url.startsWith("$BASE_URL/") })
            assertEquals(
                listOf(
                    execution("flow-act", "targetActionId" to "act-1", "targetResourceServerId" to "rs-1"),
                    execution(
                        "flow-act",
                        "targetActionId" to "act-2",
                        "targetResourceServerId" to "rs-1",
                        "targetResourceId" to "res-1",
                    ),
                ),
                fetcher.executions(),
            )
        }

    @Test
    fun `deletes an action natively in flow mode when no flow is configured`() =
        runTest {
            val fetcher = CoverageFetcher()

            client(fetcher).resourceServers.deleteAction("rs-1", "act-1", administrationMode = AdministrationMode.FLOW)

            assertTrue(fetcher.executions().isEmpty())
            assertEquals("DELETE $BASE_URL/resource-servers/rs-1/actions/act-1", fetcher.calls().last())
        }

    // MARK: - Users, applications and agent types

    @Test
    fun `user tree and sharing operations call the right endpoints`() =
        runTest {
            val fetcher = CoverageFetcher(resource = """{"id":"x","totalResults":0,"count":0,"policies":[],"users":[]}""")
            val users = client(fetcher).users
            val policy =
                SharingPolicyRequest(
                    childUnits =
                        SharingSection(
                            mode = SharingPolicyMode.SELECTED,
                            units = listOf(SharingUnit("ou-a", "subtree")),
                        ),
                )

            users.listByPath("engineering/frontend", limit = 10, offset = 0)
            users.createByPath("engineering", CreateUserByPathRequest(type = "employee", attributes = mapOf("username" to "maya")))
            users.listSharingPolicies("u-1", limit = 5)
            users.createSharingPolicy("u-1", policy)
            users.getSharingPolicy("u-1", "p-1")
            users.updateSharingPolicy(
                "u-1",
                "p-1",
                SharingPolicyUpdateRequest(version = 2, allUnits = SharingAllUnits(listOf("ou-x"))),
            )
            users.deleteSharingPolicy("u-1", "p-1")
            users.getOverlayRules("u-1", ouId = "ou-a")

            assertEquals(
                listOf(
                    "GET $BASE_URL/users/tree/engineering/frontend?limit=10&offset=0&include=display",
                    "POST $BASE_URL/users/tree/engineering",
                    "GET $BASE_URL/users/u-1/sharing-policies?limit=5",
                    "POST $BASE_URL/users/u-1/sharing-policies",
                    "GET $BASE_URL/users/u-1/sharing-policies/p-1",
                    "PUT $BASE_URL/users/u-1/sharing-policies/p-1",
                    "DELETE $BASE_URL/users/u-1/sharing-policies/p-1",
                    "GET $BASE_URL/users/u-1/overlay-rules?ouId=ou-a",
                ),
                fetcher.calls(),
            )
            val bodies = fetcher.bodies()
            assertEquals(json("""{"type":"employee","attributes":{"username":"maya"}}"""), bodies[1])
            assertEquals(json("""{"childUnits":{"mode":"selected","units":[{"ouId":"ou-a","reach":"subtree"}]}}"""), bodies[3])
            assertEquals(json("""{"version":2,"allUnits":{"excludedOuIds":["ou-x"]}}"""), bodies[5])
        }

    @Test
    fun `application sharing operations call the right endpoints`() =
        runTest {
            val fetcher = CoverageFetcher(resource = """{"id":"p-1","totalResults":0,"count":0,"policies":[]}""")
            val apps = client(fetcher, ThunderIDEndpoints(applications = "https://rs.example.com/apps")).applications

            apps.listSharingPolicies("app-1")
            apps.createSharingPolicy("app-1", SharingPolicyRequest(childUnits = SharingSection(SharingPolicyMode.ALL)))
            apps.getSharingPolicy("app-1", "p-1")
            apps.updateSharingPolicy("app-1", "p-1", SharingPolicyUpdateRequest(version = 1))
            apps.deleteSharingPolicy("app-1", "p-1")
            apps.getOverlayRules("app-1", "ou-a")

            assertEquals(
                listOf(
                    "GET https://rs.example.com/apps/app-1/sharing-policies",
                    "POST https://rs.example.com/apps/app-1/sharing-policies",
                    "GET https://rs.example.com/apps/app-1/sharing-policies/p-1",
                    "PUT https://rs.example.com/apps/app-1/sharing-policies/p-1",
                    "DELETE https://rs.example.com/apps/app-1/sharing-policies/p-1",
                    "GET https://rs.example.com/apps/app-1/overlay-rules?ouId=ou-a",
                ),
                fetcher.calls(),
            )
            assertEquals(json("""{"childUnits":{"mode":"all"}}"""), fetcher.bodies()[1])
        }

    @Test
    fun `sharing policy and overlay responses parse`() =
        runTest {
            val policy =
                client(
                    CoverageFetcher(
                        resource =
                            """{"id":"p-2","initiatingOuId":"ou-a","stage":"reshare","parentPolicyId":"p-1","version":3,""" +
                                """"childUnits":{"mode":"selected","units":[{"ouId":"ou-b","reach":"unit"}]},""" +
                                """"otherTreeRoots":{"mode":"all","excludedOuIds":["partner"]}}""",
                    ),
                ).users.getSharingPolicy("u-1", "p-2")
            val overlay =
                client(
                    CoverageFetcher(
                        resource = """{"isOwned":false,"visible":true,"policyIds":["p-1"],"rules":{"email":{"editable":true}}}""",
                    ),
                ).applications.getOverlayRules("app-1", "ou-a")

            assertEquals("reshare", policy.stage)
            val unit = policy.childUnits?.units?.single()
            assertEquals("ou-b", unit?.ouId)
            assertEquals(listOf("partner"), policy.otherTreeRoots?.excludedOuIds)
            assertEquals(3, policy.toUpdateRequest().version)
            assertEquals(true, overlay.visible)
            assertEquals(true, overlay.rules?.get("email")?.editable)
        }

    @Test
    fun `overlay rules need an organization unit`() =
        runTest {
            val fetcher = CoverageFetcher()

            val error = expectError<IAMException> { client(fetcher).users.getOverlayRules("u-1", " ") }

            assertEquals(ThunderIDErrorCode.INVALID_INPUT, error.code)
            assertTrue(fetcher.requests.isEmpty())
        }

    @Test
    fun `agent type create and delete call the right endpoints`() =
        runTest {
            val fetcher = CoverageFetcher()
            val types = client(fetcher).agentTypes

            types.create(CreateAgentTypeRequest(handle = "default", displayName = "Default", ouId = "ou-1", schema = emptyMap()))
            types.delete("t-1")

            assertEquals(listOf("POST $BASE_URL/agent-types", "DELETE $BASE_URL/agent-types/t-1"), fetcher.calls())
            assertEquals(json("""{"handle":"default","displayName":"Default","ouId":"ou-1","schema":{}}"""), fetcher.bodies()[0])
        }

    @Test
    fun `reads the access status of users, agents and applications`() =
        runTest {
            val user =
                client(
                    CoverageFetcher(
                        resource =
                            """{"id":"u-1","ouId":"ou-1","type":"employee","lastLoginAt":"2026-09-09T18:22:04Z",""" +
                                """"status":{"value":"LOCKED","details":{"reason":"FAILED_ATTEMPTS",""" +
                                """"lockedMethods":[{"method":"credential","expiresAt":"2026-09-10T00:00:00Z"}]}}}""",
                    ),
                ).users.get("u-1")
            val app =
                client(
                    CoverageFetcher(resource = """{"id":"app-1","name":"App","status":{"value":"SUSPENDED"}}"""),
                ).applications.get("app-1")

            assertEquals(AccessStatusValue.LOCKED, user.status?.value)
            val lock =
                user.status
                    ?.details
                    ?.lockedMethods
                    ?.single()
            assertEquals("credential", lock?.method)
            assertEquals("2026-09-09T18:22:04Z", user.lastLoginAt)
            assertEquals(AccessStatusValue.SUSPENDED, app.status?.value)
        }

    // MARK: - Account lifecycle

    private data class Lifecycle(
        val configKey: String,
        val operation: String,
        val run: suspend (ThunderIDClient, String?) -> AccountLifecycleResult,
    )

    private val lifecycles =
        listOf(
            Lifecycle(AdministrationFlowConfigKey.USER_SUSPEND, "suspendUser") { c, note -> c.users.suspend("id-1", note) },
            Lifecycle(AdministrationFlowConfigKey.USER_UNSUSPEND, "unsuspendUser") { c, note -> c.users.unsuspend("id-1", note) },
            Lifecycle(AdministrationFlowConfigKey.USER_UNLOCK, "unlockUser") { c, note -> c.users.unlock("id-1", note) },
            Lifecycle(AdministrationFlowConfigKey.AGENT_SUSPEND, "suspendAgent") { c, note -> c.agents.suspend("id-1", note) },
            Lifecycle(AdministrationFlowConfigKey.AGENT_UNSUSPEND, "unsuspendAgent") { c, note -> c.agents.unsuspend("id-1", note) },
            Lifecycle(AdministrationFlowConfigKey.AGENT_UNLOCK, "unlockAgent") { c, note -> c.agents.unlock("id-1", note) },
            Lifecycle(AdministrationFlowConfigKey.APPLICATION_SUSPEND, "suspendApplication") { c, note ->
                c.applications.suspend("id-1", note)
            },
            Lifecycle(AdministrationFlowConfigKey.APPLICATION_UNSUSPEND, "unsuspendApplication") { c, note ->
                c.applications.unsuspend("id-1", note)
            },
        )

    @Test
    fun `each lifecycle operation runs its flow with the subject and operator note`() =
        runTest {
            lifecycles.forEach { lifecycle ->
                val fetcher =
                    CoverageFetcher(
                        config = mapOf(lifecycle.configKey to "handle"),
                        flows = mapOf("handle" to "flow-${lifecycle.operation}"),
                    )

                val result = lifecycle.run(client(fetcher), "Fraud review")

                assertNull(lifecycle.operation, result.emailSent)
                assertEquals(
                    lifecycle.operation,
                    listOf(execution("flow-${lifecycle.operation}", "subject" to "id-1", "operatorNote" to "Fraud review")),
                    fetcher.executions(),
                )
                assertFalse(fetcher.calls().any { it.contains("/id-1") })
            }
        }

    @Test
    fun `leaves out an absent operator note and reads whether the email was sent`() =
        runTest {
            listOf("true" to true, "false" to false).forEach { (sent, expected) ->
                val fetcher =
                    CoverageFetcher(
                        config = mapOf(AdministrationFlowConfigKey.USER_SUSPEND to "suspend"),
                        flows = mapOf("suspend" to "flow-s"),
                        execution = """{"flowStatus":"COMPLETE","data":{"additionalData":{"emailSent":"$sent"}}}""",
                    )

                val result = client(fetcher).users.suspend("u-1")

                assertEquals(expected, result.emailSent)
                assertEquals(listOf(execution("flow-s", "subject" to "u-1")), fetcher.executions())
            }
        }

    @Test
    fun `a lifecycle operation with no flow configured fails rather than doing nothing`() =
        runTest {
            lifecycles.forEach { lifecycle ->
                val fetcher = CoverageFetcher()

                val error = expectError<AdministrationFlowError> { lifecycle.run(client(fetcher), null) }

                assertEquals("${lifecycle.operation}-NotConfiguredError-001", error.errorCode)
                assertEquals(ThunderIDErrorCode.ADMINISTRATION_FLOW_FAILED, error.code)
                assertEquals(listOf("GET $BASE_URL/server-config/flow"), fetcher.calls())
            }
        }

    @Test
    fun `a lifecycle operation reports a refused step`() =
        runTest {
            val fetcher =
                CoverageFetcher(
                    config = mapOf(AdministrationFlowConfigKey.AGENT_UNLOCK to "unlock"),
                    flows = mapOf("unlock" to "flow-u"),
                    execution = """{"flowStatus":"ERROR","error":{"code":"FLW-4002","message":{"defaultValue":"Refused"}}}""",
                )

            val error = expectError<AdministrationFlowError> { client(fetcher).agents.unlock("a-1") }

            assertEquals("FLW-4002", error.errorCode)
        }

    @Test
    fun `a lifecycle operation refuses an empty identifier without a request`() =
        runTest {
            val fetcher = CoverageFetcher()

            val error = expectError<IAMException> { client(fetcher).applications.suspend("") }

            assertEquals(ThunderIDErrorCode.INVALID_INPUT, error.code)
            assertTrue(fetcher.requests.isEmpty())
        }

    // MARK: - Account lifecycle, by kind and action

    @Test
    fun `runAccountLifecycleAction runs the flow configured for each kind and action`() =
        runTest {
            AccountLifecycleSubjectKind.entries.forEach { kind ->
                kind.flowConfigKeys.forEach { (action, configKey) ->
                    val fetcher = CoverageFetcher(config = mapOf(configKey to "lifecycle"), flows = mapOf("lifecycle" to "id-lifecycle"))

                    client(fetcher).administration.runAccountLifecycleAction(kind, action, "s-1")

                    assertEquals("$kind $action", listOf(execution("id-lifecycle", "subject" to "s-1")), fetcher.executions())
                }
            }
        }

    @Test
    fun `runAccountLifecycleAction passes the operator note and reports whether the notification was sent`() =
        runTest {
            val fetcher =
                CoverageFetcher(
                    config = mapOf(AdministrationFlowConfigKey.USER_SUSPEND to "suspend"),
                    flows = mapOf("suspend" to "id-suspend"),
                    execution = """{"flowStatus":"COMPLETE","data":{"additionalData":{"emailSent":"false"}}}""",
                )

            val result =
                client(fetcher).administration.runAccountLifecycleAction(
                    AccountLifecycleSubjectKind.USER,
                    AccountLifecycleAction.SUSPEND,
                    "u-1",
                    operatorNote = "Ticket 4521",
                )

            assertEquals(false, result.emailSent)
            assertEquals(listOf(execution("id-suspend", "subject" to "u-1", "operatorNote" to "Ticket 4521")), fetcher.executions())
        }

    @Test
    fun `runAccountLifecycleAction fails with the operation's error when no flow is configured`() =
        runTest {
            val error =
                expectError<AdministrationFlowError> {
                    client(CoverageFetcher()).administration.runAccountLifecycleAction(
                        AccountLifecycleSubjectKind.AGENT,
                        AccountLifecycleAction.UNLOCK,
                        "a-1",
                    )
                }

            assertEquals("unlockAgent-NotConfiguredError-001", error.errorCode)
        }

    @Test
    fun `runAccountLifecycleAction refuses to unlock an application without calling the server`() =
        runTest {
            val fetcher = CoverageFetcher()

            val error =
                expectError<IAMException> {
                    client(fetcher).administration.runAccountLifecycleAction(
                        AccountLifecycleSubjectKind.APPLICATION,
                        AccountLifecycleAction.UNLOCK,
                        "app-1",
                    )
                }

            assertEquals(ThunderIDErrorCode.INVALID_INPUT, error.code)
            assertTrue(fetcher.requests.isEmpty())
        }

    @Test
    fun `the configured lifecycle flows of a kind resolve in one read and decide the actions offered`() =
        runTest {
            val fetcher =
                CoverageFetcher(
                    config = mapOf(AdministrationFlowConfigKey.USER_UNLOCK to "unlock-user"),
                    flows = mapOf("unlock-user" to "id-unlock-user"),
                )
            val kind = AccountLifecycleSubjectKind.USER

            val flows = client(fetcher).administration.resolveFlows(kind.flowConfigKeys.values.toList())

            assertEquals(mapOf(AdministrationFlowConfigKey.USER_UNLOCK to "id-unlock-user"), flows)
            assertEquals(listOf(AccountLifecycleAction.UNLOCK), accountLifecycleActions(kind, AccessStatusValue.LOCKED, flows))
            assertTrue(accountLifecycleActions(kind, AccessStatusValue.ACTIVE, flows).isEmpty())
        }
}
