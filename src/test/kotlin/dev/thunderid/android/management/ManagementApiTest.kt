// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.management

import com.google.gson.Gson
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
    fun `agents can be filtered on the server`() =
        runTest {
            val recorder = RecordingFetcher()
            val client = client(recorder)
            recorder.respond("""{"totalResults":0,"startIndex":1,"count":0,"agents":[]}""")
            client.agents.list(limit = 5, filter = ManagementFilter.equality("name", "Calendar Agent"))

            val url = recorder.requests.single().url
            assertEquals(
                "name eq \"Calendar Agent\"",
                java.net
                    .URI(url)
                    .query
                    .split("&")
                    .first { it.startsWith("filter=") }
                    .removePrefix("filter="),
            )
        }

    @Test
    fun `equality filters trim the value and reject invalid input`() {
        assertEquals("username eq \"ada\"", ManagementFilter.equality("username", "  ada "))
        assertEquals("name eq \"Say hi\"", ManagementFilter.equality("name", "Say \"hi\""))
        assertEquals(null, ManagementFilter.equality("username", "   "))
        assertEquals(null, ManagementFilter.equality("user name", "ada"))
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
}
