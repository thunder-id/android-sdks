// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.management

import com.google.gson.Gson
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

private class QueueFetcher : ThunderIDFetcher {
    val requests = mutableListOf<ThunderIDHttpRequest>()
    private val responses = ArrayDeque<ThunderIDHttpResponse>()

    fun respond(
        body: String,
        status: Int = 200,
    ) {
        responses.addLast(ThunderIDHttpResponse(status, body))
    }

    override suspend fun fetch(request: ThunderIDHttpRequest): ThunderIDHttpResponse {
        requests += request
        return responses.removeFirstOrNull() ?: ThunderIDHttpResponse(200, "{}")
    }
}

class GroupsAndRolesApiTest {
    private val baseUrl = "https://localhost:8090"

    private suspend fun client(
        fetcher: ThunderIDFetcher,
        endpoints: ThunderIDEndpoints = ThunderIDEndpoints(),
    ): ThunderIDClient {
        val storage = InMemoryStorageAdapter()
        TokenStore(storage).save(TokenResponse(accessToken = "admin-token", tokenType = "Bearer", expiresIn = 3600))
        val config =
            ThunderIDConfig(
                baseUrl = baseUrl,
                clientId = "client",
                endpoints = endpoints,
                http = ThunderIDHttpConfig(fetcher),
            )
        return ThunderIDClient().apply { initialize(config, storage) }
    }

    private fun body(request: ThunderIDHttpRequest) = JsonParser.parseString(request.body).asJsonObject

    private val groupJson = """{"id":"g-1","name":"Admins","ouId":"ou-1"}"""
    private val roleJson =
        """{"id":"r-1","name":"Viewer","ouId":"ou-1","permissions":[{"resourceServerId":"rs","permissions":["read"]}]}"""

    // MARK: - Groups

    @Test
    fun `group CRUD hits the groups collection`() =
        runTest {
            val fetcher = QueueFetcher()
            val client = client(fetcher)
            fetcher.respond("""{"totalResults":1,"startIndex":1,"count":1,"groups":[$groupJson]}""")
            fetcher.respond(groupJson)
            fetcher.respond(groupJson)
            fetcher.respond(groupJson)
            fetcher.respond("", 204)

            val page = client.groups.list(limit = 10, offset = 20)
            client.groups.get("g-1")
            client.groups.create(CreateGroupRequest(name = "Admins", ouId = "ou-1"))
            client.groups.update("g-1", UpdateGroupRequest(name = "Admins", ouId = "ou-1", description = "All"))
            client.groups.delete("g-1")

            assertEquals("Admins", page.groups.single().name)
            assertEquals(listOf("GET", "GET", "POST", "PUT", "DELETE"), fetcher.requests.map { it.method })
            assertEquals(
                listOf(
                    "$baseUrl/groups?limit=10&offset=20&include=display",
                    "$baseUrl/groups/g-1?include=display",
                    "$baseUrl/groups",
                    "$baseUrl/groups/g-1",
                    "$baseUrl/groups/g-1",
                ),
                fetcher.requests.map { it.url },
            )
            fetcher.requests.forEach { assertEquals("Bearer admin-token", it.headers["Authorization"]) }
            val created = body(fetcher.requests[2])
            assertEquals("Admins", created["name"].asString)
            assertTrue("description" !in created.keySet())
            assertEquals("All", body(fetcher.requests[3])["description"].asString)
            assertNull(fetcher.requests[4].body)
        }

    @Test
    fun `group members are listed, added and removed through sub-resources`() =
        runTest {
            val fetcher = QueueFetcher()
            val client = client(fetcher)
            fetcher.respond(
                """{"totalResults":1,"startIndex":1,"count":1,"members":[{"id":"u-1","type":"user","display":"Alice"}]}""",
            )
            fetcher.respond(groupJson)
            fetcher.respond(groupJson)

            val members = client.groups.listMembers("g-1", limit = 5)
            client.groups.addMembers("g-1", listOf(GroupMemberInput("u-2", GroupMemberType.USER)))
            client.groups.removeMembers("g-1", listOf(GroupMemberInput("ag-1", GroupMemberType.AGENT)))

            assertEquals(GroupMemberType.USER, members.members.single().type)
            assertEquals("Alice", members.members.single().display)
            assertEquals(
                listOf(
                    "GET $baseUrl/groups/g-1/members?include=display&limit=5",
                    "POST $baseUrl/groups/g-1/members/add",
                    "POST $baseUrl/groups/g-1/members/remove",
                ),
                fetcher.requests.map { "${it.method} ${it.url}" },
            )
            assertEquals("""{"members":[{"id":"u-2","type":"user"}]}""", fetcher.requests[1].body)
            assertEquals("""{"members":[{"id":"ag-1","type":"agent"}]}""", fetcher.requests[2].body)
        }

    // MARK: - Roles

    @Test
    fun `role CRUD hits the roles collection`() =
        runTest {
            val fetcher = QueueFetcher()
            val client = client(fetcher)
            fetcher.respond("""{"totalResults":1,"startIndex":1,"count":1,"roles":[{"id":"r-1","name":"Viewer","ouId":"ou-1"}]}""")
            fetcher.respond(roleJson)
            fetcher.respond(roleJson)
            fetcher.respond(roleJson)
            fetcher.respond("", 204)

            client.roles.list(limit = 2)
            val role = client.roles.get("r-1")
            client.roles.create(CreateRoleRequest(name = "Viewer", ouId = "ou-1"))
            client.roles.update(
                "r-1",
                UpdateRoleRequest(name = "Viewer", ouId = "ou-1", permissions = listOf(RoleResourcePermissions("rs", listOf("read")))),
            )
            client.roles.delete("r-1")

            assertEquals(listOf("read"), role.permissions.single().permissions)
            assertEquals(
                listOf(
                    "GET $baseUrl/roles?limit=2",
                    "GET $baseUrl/roles/r-1",
                    "POST $baseUrl/roles",
                    "PUT $baseUrl/roles/r-1",
                    "DELETE $baseUrl/roles/r-1",
                ),
                fetcher.requests.map { "${it.method} ${it.url}" },
            )
            val updated = body(fetcher.requests[3])
            assertEquals("rs", updated["permissions"].asJsonArray[0].asJsonObject["resourceServerId"].asString)
        }

    @Test
    fun `role assignments are listed, added and removed through sub-resources`() =
        runTest {
            val fetcher = QueueFetcher()
            val client = client(fetcher)
            fetcher.respond(
                """{"totalResults":1,"startIndex":1,"count":1,"assignments":[{"id":"g-1","type":"group","display":"Admins"}]}""",
            )
            fetcher.respond("", 204)
            fetcher.respond("", 204)

            val page = client.roles.listAssignments("r-1", type = RoleAssigneeType.GROUP)
            client.roles.addAssignments("r-1", listOf(RoleAssignmentInput("app-1", RoleAssigneeType.APP)))
            client.roles.removeAssignments("r-1", listOf(RoleAssignmentInput("u-1", RoleAssigneeType.USER)))

            assertEquals(RoleAssigneeType.GROUP, page.assignments.single().type)
            assertEquals(
                listOf(
                    "GET $baseUrl/roles/r-1/assignments?include=display&type=group",
                    "POST $baseUrl/roles/r-1/assignments/add",
                    "POST $baseUrl/roles/r-1/assignments/remove",
                ),
                fetcher.requests.map { "${it.method} ${it.url}" },
            )
            assertEquals("""{"assignments":[{"id":"app-1","type":"app"}]}""", fetcher.requests[1].body)
            assertEquals("""{"assignments":[{"id":"u-1","type":"user"}]}""", fetcher.requests[2].body)
        }

    // MARK: - User and agent relations

    @Test
    fun `user groups and types`() =
        runTest {
            val fetcher = QueueFetcher()
            val client = client(fetcher)
            fetcher.respond("""{"totalResults":1,"startIndex":1,"count":1,"groups":[{"id":"g-1","name":"Admins","ouId":"ou"}]}""")
            fetcher.respond("""{"totalResults":1,"startIndex":1,"count":1,"types":[{"id":"t-1","handle":"customer"}]}""")

            val groups = client.users.listGroups("u-1", limit = 100)
            val types = client.users.listTypes(offset = 0)

            assertEquals("Admins", groups.groups.single().name)
            assertEquals("customer", types.types.single().handle)
            assertEquals(
                listOf("$baseUrl/users/u-1/groups?limit=100", "$baseUrl/user-types?offset=0"),
                fetcher.requests.map { it.url },
            )
        }

    @Test
    fun `agent groups, roles and types`() =
        runTest {
            val fetcher = QueueFetcher()
            val client = client(fetcher)
            fetcher.respond("""{"totalResults":0,"startIndex":1,"count":0,"groups":[]}""")
            fetcher.respond("""{"totalResults":1,"startIndex":1,"count":1,"roles":["Viewer"]}""")
            fetcher.respond(
                """{"totalResults":1,"startIndex":1,"count":1,"types":[{"id":"t","handle":"bot","displayName":"Bot","ouId":"ou"}]}""",
            )

            client.agents.listGroups("ag-1")
            val roles = client.agents.listRoles("ag-1", limit = 10, offset = 0)
            val types = client.agents.listTypes(limit = 100)

            assertEquals(listOf("Viewer"), roles.roles)
            assertEquals("Bot", types.types.single().displayName)
            assertEquals(
                listOf(
                    "$baseUrl/agents/ag-1/groups",
                    "$baseUrl/agents/ag-1/roles?limit=10&offset=0",
                    "$baseUrl/agent-types?limit=100",
                ),
                fetcher.requests.map { it.url },
            )
        }

    // MARK: - Endpoints, fetcher and errors

    @Test
    fun `endpoint overrides apply to every collection and its sub-resources`() =
        runTest {
            val fetcher = QueueFetcher()
            val client =
                client(
                    fetcher,
                    ThunderIDEndpoints(
                        groups = "https://rs.example.com/v2/groups/",
                        roles = "https://rs.example.com/v2/roles",
                        agents = "https://rs.example.com/v2/agents",
                        agentTypes = "https://rs.example.com/v2/agent-kinds",
                        userTypes = "https://rs.example.com/v2/user-kinds",
                    ),
                )
            fetcher.respond(groupJson)
            fetcher.respond("", 204)
            fetcher.respond("""{"totalResults":0,"startIndex":1,"count":0,"roles":[]}""")
            repeat(2) { fetcher.respond("""{"totalResults":0,"startIndex":1,"count":0,"types":[]}""") }

            client.groups.removeMembers("g 1", emptyList())
            client.roles.addAssignments("r-1", emptyList())
            client.agents.listRoles("ag-1")
            client.agents.listTypes()
            client.users.listTypes()

            assertEquals(
                listOf(
                    "https://rs.example.com/v2/groups/g%201/members/remove",
                    "https://rs.example.com/v2/roles/r-1/assignments/add",
                    "https://rs.example.com/v2/agents/ag-1/roles",
                    "https://rs.example.com/v2/agent-kinds",
                    "https://rs.example.com/v2/user-kinds",
                ),
                fetcher.requests.map { it.url },
            )
        }

    @Test
    fun `per call fetcher takes precedence for sub-resources`() =
        runTest {
            val clientFetcher = QueueFetcher()
            val callFetcher = QueueFetcher().apply { respond("", 204) }

            client(clientFetcher).roles.removeAssignments("r-1", emptyList(), callFetcher)

            assertEquals(1, callFetcher.requests.size)
            assertTrue(clientFetcher.requests.isEmpty())
        }

    @Test
    fun `forbidden and not found have distinct codes for groups and roles`() =
        runTest {
            val fetcher = QueueFetcher()
            val client = client(fetcher)
            val calls: List<suspend () -> Unit> =
                listOf(
                    { client.groups.listMembers("g-1") },
                    { client.roles.addAssignments("r-1", emptyList()) },
                    { client.agents.listTypes() },
                )

            for (call in calls) {
                for ((status, code) in listOf(403 to ThunderIDErrorCode.FORBIDDEN, 404 to ThunderIDErrorCode.NOT_FOUND)) {
                    fetcher.respond("""{"message":"server detail"}""", status)
                    try {
                        call()
                        fail("Expected $code")
                    } catch (e: IAMException) {
                        assertEquals(code, e.code)
                    }
                }
            }
        }

    @Test
    fun `empty identifier fails before any request`() =
        runTest {
            val fetcher = QueueFetcher()
            val client = client(fetcher)
            for (call in listOf<suspend () -> Unit>({ client.groups.addMembers("", emptyList()) }, { client.users.listGroups(" ") })) {
                try {
                    call()
                    fail("Expected IAMException")
                } catch (e: IAMException) {
                    assertEquals(ThunderIDErrorCode.INVALID_INPUT, e.code)
                }
            }
            assertTrue(fetcher.requests.isEmpty())
        }

    @Test
    fun `member and assignee types serialize to their wire values`() {
        assertEquals("""{"id":"x","type":"group"}""", Gson().toJson(GroupMemberInput("x", GroupMemberType.GROUP)))
        assertEquals(RoleAssigneeType.AGENT, Gson().fromJson("""{"id":"x","type":"agent"}""", RoleAssignment::class.java).type)
    }
}
