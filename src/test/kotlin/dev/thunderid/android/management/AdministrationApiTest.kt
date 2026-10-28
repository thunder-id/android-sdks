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
import dev.thunderid.android.http.HttpClient
import dev.thunderid.android.token.TokenStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

private const val BASE_URL = "https://localhost:8090"

private data class Flow(
    val id: String,
    val handle: String,
    val flowType: String = ADMINISTRATION_FLOW_TYPE,
)

/**
 * Answers like the server: the flow configuration, the flow listing, flow execution, and [resource] for
 * every other request.
 */
private class ServerFetcher(
    val config: Map<String, String> = emptyMap(),
    val flows: List<Flow> = emptyList(),
    val execution: String = """{"flowStatus":"COMPLETE","data":{"additionalData":{}}}""",
    val resource: String = "{}",
) : ThunderIDFetcher {
    val requests = mutableListOf<ThunderIDHttpRequest>()

    override suspend fun fetch(request: ThunderIDHttpRequest): ThunderIDHttpResponse {
        requests += request
        val body =
            when {
                request.url == "$BASE_URL/server-config/flow" -> {
                    val merged = config.mapValues { mapOf("defaultHandle" to it.value) }
                    Gson().toJson(mapOf("merged" to merged))
                }

                request.url.startsWith("$BASE_URL/flows?") -> {
                    Gson().toJson(mapOf("flows" to flows))
                }

                request.url == "$BASE_URL/flow/execute" -> {
                    execution
                }

                else -> {
                    resource
                }
            }
        return ThunderIDHttpResponse(200, body)
    }

    fun calls() = requests.map { "${it.method} ${it.url}" }

    fun executions(): List<JsonElement> = requests.filter { it.url == "$BASE_URL/flow/execute" }.map { json(it.body) }

    fun put(): JsonElement? = requests.firstOrNull { it.method == "PUT" }?.let { json(it.body) }
}

private fun json(text: String?): JsonElement = JsonParser.parseString(text)

private fun execution(
    flowId: String,
    vararg inputs: Pair<String, String>,
): JsonElement = json(Gson().toJson(mapOf("flowId" to flowId, "inputs" to mapOf(*inputs))))

class AdministrationApiTest {
    private suspend fun client(
        fetcher: ThunderIDFetcher,
        endpoints: ThunderIDEndpoints = ThunderIDEndpoints(),
    ): ThunderIDClient {
        val storage = InMemoryStorageAdapter()
        TokenStore(storage).save(TokenResponse(accessToken = "admin-token", tokenType = "Bearer", expiresIn = 3600))
        val config = ThunderIDConfig(baseUrl = BASE_URL, clientId = "client", endpoints = endpoints, http = ThunderIDHttpConfig(fetcher))
        return ThunderIDClient().apply { initialize(config, storage) }
    }

    private val deletionFlow =
        ServerFetcher(
            config = mapOf(AdministrationFlowConfigKey.USER_DELETION to "delete-user"),
            flows = listOf(Flow("flow-1", "delete-user")),
        )

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

    // MARK: - New operations

    @Test
    fun `new operations call the right endpoints`() =
        runTest {
            val fetcher = ServerFetcher()
            val client = client(fetcher)
            val schema = mapOf("email" to PropertyDefinition(type = "email", required = true))

            client.users.getUsages("u-1")
            client.users.updateCredentials("u-1", UpdateUserCredentialsRequest(mapOf("password" to "p")))
            client.userTypes.list(limit = 5)
            client.userTypes.get("t-1")
            client.userTypes.create(CreateUserTypeRequest(handle = "staff", displayName = "Staff", ouId = "ou-1", schema = emptyMap()))
            client.userTypes.update("t-1", UpdateUserTypeRequest(displayName = "Staff", ouId = "ou-1", schema = schema))
            client.userTypes.delete("t-1")
            client.agentTypes.list(offset = 10)
            client.agentTypes.get("a-1")
            client.agentTypes.update("a-1", UpdateAgentTypeRequest(displayName = "Bot", ouId = "ou-1", schema = emptyMap()))
            client.applications.previewCimdDocument("https://client.example/cimd.json")

            assertEquals(
                listOf(
                    "GET $BASE_URL/users/u-1/usages",
                    "POST $BASE_URL/users/u-1/update-credentials",
                    "GET $BASE_URL/user-types?limit=5&include=display",
                    "GET $BASE_URL/user-types/t-1?include=display",
                    "POST $BASE_URL/user-types",
                    "PUT $BASE_URL/user-types/t-1",
                    "DELETE $BASE_URL/user-types/t-1",
                    "GET $BASE_URL/agent-types?offset=10&include=display",
                    "GET $BASE_URL/agent-types/a-1?include=display",
                    "PUT $BASE_URL/agent-types/a-1",
                    "POST $BASE_URL/cimd/preview",
                ),
                fetcher.calls(),
            )
            val bodies = fetcher.requests.map { it.body?.let(::json) }
            assertNull(bodies[0])
            assertEquals(json("""{"credentials":{"password":"p"}}"""), bodies[1])
            assertEquals(json("""{"handle":"staff","displayName":"Staff","ouId":"ou-1","schema":{}}"""), bodies[4])
            assertEquals(
                json("""{"displayName":"Staff","ouId":"ou-1","schema":{"email":{"type":"email","required":true}}}"""),
                bodies[5],
            )
            assertEquals(json("""{"displayName":"Bot","ouId":"ou-1","schema":{}}"""), bodies[9])
            assertEquals(json("""{"clientId":"https://client.example/cimd.json"}"""), bodies[10])
        }

    @Test
    fun `CIMD preview targets the server whatever the applications override`() =
        runTest {
            val fetcher = ServerFetcher()
            val client = client(fetcher, ThunderIDEndpoints(applications = "https://rs.example.com/v2/applications"))

            client.applications.previewCimdDocument("https://client.example/cimd.json")

            assertEquals(listOf("POST $BASE_URL/cimd/preview"), fetcher.calls())
        }

    @Test
    fun `user usages and type details parse`() =
        runTest {
            val usages =
                client(
                    ServerFetcher(
                        resource =
                            """{"count":1,"totalResults":null,"summary":{"agent":1},""" +
                                """"usages":[{"id":"a-1","resourceType":"agent","displayName":"Bot","behaviorOnDelete":"cascade"}]}""",
                    ),
                ).users.getUsages("u-1")
            val type =
                client(
                    ServerFetcher(
                        resource =
                            """{"id":"t-1","handle":"staff","displayName":"Staff","ouId":"ou-1",""" +
                                """"schema":{"tags":{"type":"array","items":{"type":"string","enum":["a"]}}},""" +
                                """"systemAttributes":{"display":"email"}}""",
                    ),
                ).userTypes.get("t-1")

            assertEquals(UserUsageBehavior.CASCADE, usages.usages.single().behaviorOnDelete)
            assertNull(usages.totalResults)
            assertEquals(mapOf("agent" to 1), usages.summary)
            assertEquals(listOf("a"), type.schema["tags"]?.items?.enum)
            assertEquals("email", type.systemAttributes?.display)
        }

    // MARK: - Administration flows

    @Test
    fun `calls the API directly by default, without reading the flow configuration`() =
        runTest {
            client(deletionFlow).users.delete("u-1")

            assertEquals(listOf("DELETE $BASE_URL/users/u-1"), deletionFlow.calls())
        }

    @Test
    fun `runs the configured flow in flow mode instead of calling the API`() =
        runTest {
            client(deletionFlow).users.delete("u-1", administrationMode = AdministrationMode.FLOW)

            assertEquals(
                listOf(
                    "GET $BASE_URL/server-config/flow",
                    "GET $BASE_URL/flows?flowType=ADMINISTRATION&limit=100&offset=0",
                    "POST $BASE_URL/flow/execute",
                ),
                deletionFlow.calls(),
            )
            assertEquals(listOf(execution("flow-1", "subject" to "u-1")), deletionFlow.executions())
        }

    @Test
    fun `flow requests target the server whatever the collection override`() =
        runTest {
            val client = client(deletionFlow, ThunderIDEndpoints(users = "https://rs.example.com/v2/users"))

            client.users.delete("u-1", administrationMode = AdministrationMode.FLOW)

            assertTrue(deletionFlow.requests.all { it.url.startsWith("$BASE_URL/") })
            assertEquals(1, deletionFlow.executions().size)
        }

    @Test
    fun `calls the API directly in flow mode when no flow is configured`() =
        runTest {
            val fetcher = ServerFetcher()

            client(fetcher).groups.delete("g-1", administrationMode = AdministrationMode.FLOW)

            assertTrue(fetcher.executions().isEmpty())
            assertEquals("DELETE $BASE_URL/groups/g-1", fetcher.calls().last())
        }

    @Test
    fun `refuses when the configured flow no longer exists, rather than skipping the revocation`() =
        runTest {
            val fetcher = ServerFetcher(config = mapOf(AdministrationFlowConfigKey.ROLE_DELETION to "gone"))

            val error =
                expectError<AdministrationFlowError> {
                    client(fetcher).roles.delete("r-1", administrationMode = AdministrationMode.FLOW)
                }

            assertEquals("resolveAdministrationFlowId-NotFoundError-001", error.errorCode)
            assertEquals(ThunderIDErrorCode.ADMINISTRATION_FLOW_FAILED, error.code)
            assertFalse(fetcher.requests.any { it.method == "DELETE" })
        }

    @Test
    fun `reports a refused step with the step's own code`() =
        runTest {
            val fetcher =
                ServerFetcher(
                    config = mapOf(AdministrationFlowConfigKey.APPLICATION_DELETION to "delete-app"),
                    flows = listOf(Flow("flow-2", "delete-app")),
                    execution = """{"flowStatus":"ERROR","error":{"code":"FLW-4001","message":{"defaultValue":"Not allowed"}}}""",
                )

            val error =
                expectError<AdministrationFlowError> {
                    client(fetcher).applications.delete("app-1", administrationMode = AdministrationMode.FLOW)
                }

            assertEquals("FLW-4001", error.errorCode)
            assertEquals("FLW-4001", error.flowError?.code)
            assertTrue(error.message.orEmpty().contains("Not allowed"))
            assertEquals(listOf(execution("flow-2", "targetApplicationId" to "app-1")), fetcher.executions())
            assertFalse(fetcher.requests.any { it.method == "DELETE" })
        }

    @Test
    fun `refuses a flow that asks for more input`() =
        runTest {
            val fetcher =
                ServerFetcher(
                    config = deletionFlow.config,
                    flows = deletionFlow.flows,
                    execution = """{"flowStatus":"INCOMPLETE"}""",
                )

            val error =
                expectError<AdministrationFlowError> {
                    client(fetcher).users.delete("u-1", administrationMode = AdministrationMode.FLOW)
                }

            assertEquals("executeAdministrationFlow-IncompleteError-001", error.errorCode)
        }

    @Test
    fun `needs the server URL to run a flow`() =
        runTest {
            val fetcher = ServerFetcher()
            val http = HttpClient(BASE_URL, defaultTransport = fetcher).apply { setAccessTokenProvider { "token" } }
            val users = UsersClient(ManagementTransport(http, "$BASE_URL/users", null), ManagementTransport(http, "", null))

            val error =
                expectError<IAMException> {
                    users.delete("u-1", administrationMode = AdministrationMode.FLOW)
                }

            assertEquals(ThunderIDErrorCode.INVALID_CONFIGURATION, error.code)
            assertTrue(fetcher.requests.isEmpty())
        }

    @Test
    fun `runs the removal flow once for each member, and for each assignee`() =
        runTest {
            val fetcher =
                ServerFetcher(
                    config =
                        mapOf(
                            AdministrationFlowConfigKey.GROUP_MEMBERSHIP_REMOVAL to "remove-member",
                            AdministrationFlowConfigKey.ROLE_ASSIGNMENT_REMOVAL to "remove-assignment",
                        ),
                    flows = listOf(Flow("flow-m", "remove-member"), Flow("flow-a", "remove-assignment")),
                    resource = """{"id":"g-1","name":"Admins","ouId":"ou-1"}""",
                )
            val client = client(fetcher)

            val group =
                client.groups.removeMembers(
                    "g-1",
                    listOf(GroupMemberInput("u-1", GroupMemberType.USER), GroupMemberInput("u-2", GroupMemberType.USER)),
                    administrationMode = AdministrationMode.FLOW,
                )
            client.roles.removeAssignments(
                "r-1",
                listOf(RoleAssignmentInput("g-1", RoleAssigneeType.GROUP)),
                administrationMode = AdministrationMode.FLOW,
            )

            assertEquals("Admins", group.name)
            assertTrue("GET $BASE_URL/groups/g-1?include=display" in fetcher.calls())
            assertFalse(fetcher.calls().any { it.endsWith("/remove") })
            assertEquals(
                listOf(
                    execution("flow-m", "targetGroupId" to "g-1", "targetMemberId" to "u-1"),
                    execution("flow-m", "targetGroupId" to "g-1", "targetMemberId" to "u-2"),
                    execution("flow-a", "targetAssigneeId" to "g-1", "targetRoleId" to "r-1"),
                ),
                fetcher.executions(),
            )
        }

    @Test
    fun `walks the flow listing page by page until the handle is found`() =
        runTest {
            val firstPage = (0 until 100).map { Flow("id-$it", "flow-$it") }
            val requests = mutableListOf<String>()
            val fetcher =
                object : ThunderIDFetcher {
                    override suspend fun fetch(request: ThunderIDHttpRequest): ThunderIDHttpResponse {
                        requests += request.url
                        val flows = if ("offset=0" in request.url) firstPage else listOf(Flow("id-late", "late"))
                        return ThunderIDHttpResponse(200, Gson().toJson(mapOf("flows" to flows)))
                    }
                }

            val found = client(fetcher).administration.findFlows(listOf("late"))

            assertEquals("id-late", found["late"]?.id)
            assertEquals(
                listOf(
                    "$BASE_URL/flows?flowType=ADMINISTRATION&limit=100&offset=0",
                    "$BASE_URL/flows?flowType=ADMINISTRATION&limit=100&offset=100",
                ),
                requests,
            )
        }

    @Test
    fun `resolves several operations in one configuration read and one listing walk`() =
        runTest {
            val fetcher =
                ServerFetcher(
                    config =
                        mapOf(
                            AdministrationFlowConfigKey.USER_DELETION to "delete-user",
                            AdministrationFlowConfigKey.GROUP_DELETION to "missing",
                        ),
                    flows = listOf(Flow("flow-1", "delete-user"), Flow("other", "delete-group", flowType = "AUTHENTICATION")),
                )

            val resolved =
                client(fetcher).administration.resolveFlows(
                    listOf(
                        AdministrationFlowConfigKey.USER_DELETION,
                        AdministrationFlowConfigKey.GROUP_DELETION,
                        AdministrationFlowConfigKey.ROLE_DELETION,
                    ),
                )

            assertEquals(mapOf(AdministrationFlowConfigKey.USER_DELETION to "flow-1"), resolved)
            assertEquals(2, fetcher.requests.size)
        }

    // MARK: - updateRole in flow mode

    private val role =
        Role(
            id = "r-1",
            name = "Viewer",
            ouId = "ou-1",
            permissions = listOf(RoleResourcePermissions("rs-1", listOf("read", "write"))),
        )

    private fun roleFlowServer() =
        ServerFetcher(
            config = mapOf(AdministrationFlowConfigKey.ROLE_PERMISSION_REMOVAL to "remove-permission"),
            flows = listOf(Flow("flow-p", "remove-permission")),
            resource = Gson().toJson(role),
        )

    @Test
    fun `updateRole saves directly when no permission is removed`() =
        runTest {
            val fetcher = roleFlowServer()

            client(fetcher).roles.update(
                "r-1",
                UpdateRoleRequest(
                    name = "Viewer",
                    ouId = "ou-1",
                    permissions = listOf(RoleResourcePermissions("rs-1", listOf("read", "write", "admin"))),
                ),
                administrationMode = AdministrationMode.FLOW,
                current = role,
            )

            assertTrue(fetcher.executions().isEmpty())
            assertEquals(listOf("PUT $BASE_URL/roles/r-1"), fetcher.calls())
        }

    @Test
    fun `updateRole runs the flow for a removed permission, after saving the other fields with the old permissions`() =
        runTest {
            val fetcher = roleFlowServer()
            val permissions = listOf(RoleResourcePermissions("rs-1", listOf("read")))

            val updated =
                client(fetcher).roles.update(
                    "r-1",
                    UpdateRoleRequest(name = "Reader", ouId = "ou-1", permissions = permissions),
                    administrationMode = AdministrationMode.FLOW,
                )

            assertEquals("GET $BASE_URL/roles/r-1?include=display", fetcher.calls().first())
            assertEquals(json("""{"name":"Reader","ouId":"ou-1","permissions":${Gson().toJson(role.permissions)}}"""), fetcher.put())
            assertEquals(
                listOf(execution("flow-p", "targetPermissions" to Gson().toJson(permissions), "targetRoleId" to "r-1")),
                fetcher.executions(),
            )
            assertEquals(permissions, updated.permissions)
        }

    @Test
    fun `updateRole skips the save when only permissions change`() =
        runTest {
            val fetcher = roleFlowServer()

            client(fetcher).roles.update(
                "r-1",
                UpdateRoleRequest(name = "Viewer", ouId = "ou-1", permissions = emptyList()),
                administrationMode = AdministrationMode.FLOW,
                current = role,
            )

            assertNull(fetcher.put())
            assertEquals(1, fetcher.executions().size)
        }

    // MARK: - Secret regeneration

    private val application =
        """{"id":"app-1","createdAt":"yesterday","name":"Shop",""" +
            """"inboundAuthConfig":[{"type":"oauth2","config":{"clientId":"client","clientSecret":"old"}}]}"""

    private val secretFlow =
        mapOf(AdministrationFlowConfigKey.SECRET_REGENERATION to "rotate") to listOf(Flow("flow-s", "rotate"))

    @Test
    fun `returns the secret a regeneration flow produced`() =
        runTest {
            val fetcher =
                ServerFetcher(
                    config = secretFlow.first,
                    flows = secretFlow.second,
                    execution = """{"flowStatus":"COMPLETE","data":{"additionalData":{"clientSecret":"from-flow"}}}""",
                )

            val result = client(fetcher).applications.regenerateClientSecret("app-1", administrationMode = AdministrationMode.FLOW)

            assertEquals(RegenerateClientSecretResult("from-flow"), result)
            assertEquals(listOf(execution("flow-s", "targetApplicationId" to "app-1")), fetcher.executions())
        }

    @Test
    fun `refuses a regeneration flow that returns no secret`() =
        runTest {
            val fetcher = ServerFetcher(config = secretFlow.first, flows = secretFlow.second)

            val error =
                expectError<AdministrationFlowError> {
                    client(fetcher).applications.regenerateClientSecret("app-1", administrationMode = AdministrationMode.FLOW)
                }

            assertEquals("regenerateClientSecret-FlowError-001", error.errorCode)
        }

    @Test
    fun `otherwise saves a newly generated client secret on the application`() =
        runTest {
            val fetcher = ServerFetcher(resource = application)

            val result = client(fetcher).applications.regenerateClientSecret("app-1")

            assertTrue(result.clientSecret, Regex("^[A-Za-z0-9_-]{43}$").matches(result.clientSecret))
            assertEquals(listOf("GET $BASE_URL/applications/app-1", "PUT $BASE_URL/applications/app-1"), fetcher.calls())
            assertEquals(
                json(
                    """{"name":"Shop","inboundAuthConfig":[{"type":"oauth2",""" +
                        """"config":{"clientId":"client","clientSecret":"${result.clientSecret}"}}]}""",
                ),
                fetcher.put(),
            )
        }

    @Test
    fun `generated secrets differ`() =
        runTest {
            val first = client(ServerFetcher(resource = application)).applications.regenerateClientSecret("app-1")
            val second = client(ServerFetcher(resource = application)).applications.regenerateClientSecret("app-1")

            assertFalse(first.clientSecret == second.clientSecret)
        }

    @Test
    fun `refuses to regenerate a client secret for an application without an OAuth client`() =
        runTest {
            val fetcher = ServerFetcher(resource = """{"id":"app-1","name":"Shop","inboundAuthConfig":[]}""")

            val error = expectError<IAMException> { client(fetcher).applications.regenerateClientSecret("app-1") }

            assertEquals(ThunderIDErrorCode.INVALID_INPUT, error.code)
            assertFalse(fetcher.requests.any { it.method == "PUT" })
        }

    @Test
    fun `saves a newly generated flow secret on the application`() =
        runTest {
            val fetcher = ServerFetcher(resource = application)

            val result = client(fetcher).applications.regenerateFlowSecret("app-1")

            val put = fetcher.put()!!.asJsonObject
            assertEquals(result.flowSecret, put["flowSecret"].asString)
            assertEquals("Shop", put["name"].asString)
            assertFalse(put.has("id"))
            assertFalse(put.has("createdAt"))
        }

    @Test
    fun `saves a newly generated secret on the agent's OAuth client`() =
        runTest {
            val fetcher =
                ServerFetcher(
                    resource =
                        """{"id":"a-1","clientId":"client","display":"Bot","attributes":{"name":"Bot"},""" +
                            """"ouHandle":"default","ouId":"ou-1","type":"bot",""" +
                            """"inboundAuthConfig":[{"type":"oauth2","config":{"grantTypes":["client_credentials"]}}]}""",
                )

            val result = client(fetcher).agents.regenerateSecret("a-1")

            assertEquals(listOf("GET $BASE_URL/agents/a-1?include=display", "PUT $BASE_URL/agents/a-1"), fetcher.calls())
            // The display-only organization unit handle and the server-set fields stay out of the update.
            assertEquals(
                json(
                    """{"ouId":"ou-1","type":"bot","attributes":{"name":"Bot"},"delegated":false,""" +
                        """"inboundAuthConfig":[{"type":"oauth2",""" +
                        """"config":{"grantTypes":["client_credentials"],"clientSecret":"${result.clientSecret}"}}]}""",
                ),
                fetcher.put(),
            )
        }

    // The server does not store the delegated flag, so the update restates it from the grant types.
    @Test
    fun `keeps a delegated agent held to the delegated grant rules`() =
        runTest {
            for (grant in DelegationGrantTypes) {
                val fetcher =
                    ServerFetcher(
                        resource =
                            """{"id":"a-1","attributes":{"name":"Assistant"},"ouId":"ou-1","type":"bot",""" +
                                """"inboundAuthConfig":[{"type":"oauth2","config":{"grantTypes":["$grant","refresh_token"]}}]}""",
                    )

                client(fetcher).agents.regenerateSecret("a-1")

                assertTrue(grant, fetcher.put()!!.asJsonObject["delegated"].asBoolean)
            }
        }

    @Test
    fun `refuses to regenerate the secret of an agent with no OAuth client`() =
        runTest {
            val fetcher = ServerFetcher(resource = """{"id":"a-1","attributes":{"name":"Bot"},"ouId":"ou-1","type":"bot"}""")

            val error = expectError<IAMException> { client(fetcher).agents.regenerateSecret("a-1") }

            assertEquals(ThunderIDErrorCode.INVALID_INPUT, error.code)
            assertFalse(fetcher.requests.any { it.method == "PUT" })
        }
}
