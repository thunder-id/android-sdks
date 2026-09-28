// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.management

import com.google.gson.Gson
import dev.thunderid.android.FlowType
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

private class RecordingFetcher : ThunderIDFetcher {
    val requests = mutableListOf<ThunderIDHttpRequest>()
    var response = ThunderIDHttpResponse(200, "{}")

    override suspend fun fetch(request: ThunderIDHttpRequest): ThunderIDHttpResponse {
        requests += request
        return response
    }

    fun respond(
        body: String,
        status: Int = 200,
    ) {
        response = ThunderIDHttpResponse(status, body)
    }
}

class ManagementApiTest {
    private val baseUrl = "https://localhost:8090"

    private suspend fun client(
        fetcher: ThunderIDFetcher? = null,
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

    @Test
    fun `management requires initialization`() {
        try {
            ThunderIDClient().applications
            fail("Expected IAMException")
        } catch (e: IAMException) {
            assertEquals(ThunderIDErrorCode.SDK_NOT_INITIALIZED, e.code)
        }
    }

    @Test
    fun `list sends query and access token`() =
        runTest {
            val recorder = RecordingFetcher()
            recorder.respond("""{"totalResults":1,"count":1,"applications":[{"id":"app-1","name":"App"}]}""")

            val page = client(recorder).applications.list(limit = 5, offset = 10)

            assertEquals("App", page.applications.first().name)
            val request = recorder.requests.single()
            assertEquals("GET", request.method)
            assertEquals("$baseUrl/applications?limit=5&offset=10", request.url)
            assertEquals("Bearer admin-token", request.headers["Authorization"])
        }

    @Test
    fun `users and agents request display`() =
        runTest {
            val recorder = RecordingFetcher()
            val client = client(recorder)
            recorder.respond("""{"id":"u-1","ouId":"ou","type":"customer","display":"Alice"}""")
            val user = client.users.get("u-1")
            recorder.respond("""{"totalResults":0,"startIndex":1,"count":0,"agents":[]}""")
            client.agents.list()

            assertEquals("Alice", user.display)
            assertEquals("$baseUrl/users/u-1?include=display", recorder.requests[0].url)
            assertEquals("$baseUrl/agents?include=display", recorder.requests[1].url)
        }

    @Test
    fun `create sends the payload without null fields`() =
        runTest {
            val recorder = RecordingFetcher()
            recorder.respond("""{"id":"new","name":"My SPA","url":"https://app.example.com"}""")

            val application =
                client(recorder).applications.create(ApplicationRequest(name = "My SPA", url = "https://app.example.com"))

            assertEquals("new", application.id)
            assertEquals("My SPA", application.toRequest().name)
            val request = recorder.requests.single()
            assertEquals("POST", request.method)
            @Suppress("UNCHECKED_CAST")
            val body = Gson().fromJson(request.body, Map::class.java) as Map<String, Any?>
            assertEquals("My SPA", body["name"])
            assertTrue("description" !in body)
        }

    @Test
    fun `update and delete target the resource`() =
        runTest {
            val recorder = RecordingFetcher()
            val client = client(recorder)
            recorder.respond("""{"id":"ag-1","ouId":"ou","type":"default","name":"Renamed"}""")
            client.agents.update("ag-1", UpdateAgentRequest(name = "Renamed"))
            recorder.respond("", 204)
            client.agents.delete("ag-1")

            assertEquals(listOf("PUT", "DELETE"), recorder.requests.map { it.method })
            assertEquals(listOf("$baseUrl/agents/ag-1", "$baseUrl/agents/ag-1"), recorder.requests.map { it.url })
            assertNull(recorder.requests[1].body)
        }

    @Test
    fun `endpoints override targets the resource server`() =
        runTest {
            val recorder = RecordingFetcher()
            recorder.respond("", 204)

            client(recorder, ThunderIDEndpoints(users = "https://rs.example.com/users/")).users.delete("u 1")

            assertEquals("https://rs.example.com/users/u%201", recorder.requests.single().url)
        }

    @Test
    fun `per call fetcher takes precedence`() =
        runTest {
            val clientFetcher = RecordingFetcher()
            val callFetcher = RecordingFetcher().apply { respond("", 204) }

            client(clientFetcher).applications.delete("app-1", callFetcher)

            assertEquals(1, callFetcher.requests.size)
            assertTrue(clientFetcher.requests.isEmpty())
        }

    @Test
    fun `forbidden and not found have distinct codes`() =
        runTest {
            val recorder = RecordingFetcher()
            val client = client(recorder)

            for ((status, code) in listOf(403 to ThunderIDErrorCode.FORBIDDEN, 404 to ThunderIDErrorCode.NOT_FOUND)) {
                recorder.respond("{}", status)
                try {
                    client.users.get("u-1")
                    fail("Expected $code")
                } catch (e: IAMException) {
                    assertEquals(code, e.code)
                }
            }
        }

    @Test
    fun `empty identifier fails before any request`() =
        runTest {
            val recorder = RecordingFetcher()
            try {
                client(recorder).applications.get(" ")
                fail("Expected IAMException")
            } catch (e: IAMException) {
                assertEquals(ThunderIDErrorCode.INVALID_INPUT, e.code)
            }
            assertTrue(recorder.requests.isEmpty())
        }

    @Test
    fun `flows list sends the flow type and paging`() =
        runTest {
            val recorder = RecordingFetcher()
            recorder.respond(
                """{"totalResults":75,"startIndex":31,"count":1,"flows":[{"id":"flow-1","handle":"password",""" +
                    """"name":"Password login","flowType":"AUTHENTICATION","activeVersion":2,"isReadOnly":false,""" +
                    """"createdAt":"2026-01-01","updatedAt":"2026-01-02"}]}""",
            )

            val page = client(recorder).flows.list(limit = 30, offset = 30, flowType = FlowType.AUTHENTICATION)

            assertEquals(75, page.totalResults)
            assertEquals("flow-1", page.flows.first().id)
            assertEquals("Password login", page.flows.first().name)
            val request = recorder.requests.single()
            assertEquals("GET", request.method)
            assertEquals("$baseUrl/flows?flowType=AUTHENTICATION&limit=30&offset=30", request.url)
            assertEquals("Bearer admin-token", request.headers["Authorization"])
        }

    @Test
    fun `flows list without a flow type sends no filter and tolerates missing optional fields`() =
        runTest {
            val recorder = RecordingFetcher()
            recorder.respond(
                """{"totalResults":1,"startIndex":1,"count":1,"flows":""" +
                    """[{"id":"f","handle":"h","name":"N","flowType":"REGISTRATION"}]}""",
            )

            val page = client(recorder).flows.list()

            assertNull(page.flows.first().activeVersion)
            assertEquals("$baseUrl/flows", recorder.requests.single().url)
        }

    @Test
    fun `user types list sends paging and decodes handles`() =
        runTest {
            val recorder = RecordingFetcher()
            recorder.respond(
                """{"totalResults":2,"startIndex":1,"count":2,"types":[""" +
                    """{"id":"type-1","handle":"employee","displayName":"Staff","ouId":"ou-1",""" +
                    """"allowSelfRegistration":false,"isReadOnly":false},""" +
                    """{"id":"type-2","handle":"contractor","displayName":"Staff"}]}""",
            )

            val page = client(recorder).userTypes.list(limit = 30, offset = 0)

            assertEquals(listOf("employee", "contractor"), page.types.map { it.handle })
            assertEquals("$baseUrl/user-types?limit=30&offset=0", recorder.requests.single().url)
        }

    @Test
    fun `flow and user type endpoint overrides target the resource server`() =
        runTest {
            val recorder = RecordingFetcher()
            recorder.respond("""{"totalResults":0,"startIndex":1,"count":0,"flows":[],"types":[]}""")
            val endpoints =
                ThunderIDEndpoints(flows = "https://rs.example.com/flows/", userTypes = "https://rs.example.com/user-types")
            val client = client(recorder, endpoints)

            client.flows.list(flowType = FlowType.AUTHENTICATION)
            client.userTypes.list(limit = 10)

            assertEquals(
                listOf("https://rs.example.com/flows?flowType=AUTHENTICATION", "https://rs.example.com/user-types?limit=10"),
                recorder.requests.map { it.url },
            )
        }

    @Test
    fun `flow and user type lists require initialization`() {
        for (access in listOf({ ThunderIDClient().flows }, { ThunderIDClient().userTypes })) {
            try {
                access()
                fail("Expected IAMException")
            } catch (e: IAMException) {
                assertEquals(ThunderIDErrorCode.SDK_NOT_INITIALIZED, e.code)
            }
        }
    }

    @Test
    fun `forbidden on the flow and user type lists has its own code`() =
        runTest {
            val recorder = RecordingFetcher()
            recorder.respond("{}", 403)
            val client = client(recorder)

            for (list in listOf<suspend () -> Any>({ client.flows.list() }, { client.userTypes.list() })) {
                try {
                    list()
                    fail("Expected FORBIDDEN")
                } catch (e: IAMException) {
                    assertEquals(ThunderIDErrorCode.FORBIDDEN, e.code)
                }
            }
        }
}
