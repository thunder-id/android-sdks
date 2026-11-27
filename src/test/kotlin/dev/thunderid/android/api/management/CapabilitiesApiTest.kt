// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.management

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

private class CapabilitiesFetcher(
    val status: Int = 200,
    val body: String = """{"scope":{"type":"ou","ouId":"ou-1"},"resources":{"app":{"view":true,"create":false}}}""",
) : ThunderIDFetcher {
    val urls = mutableListOf<String>()

    override suspend fun fetch(request: ThunderIDHttpRequest): ThunderIDHttpResponse {
        urls += "${request.method} ${request.url}"
        return ThunderIDHttpResponse(status, body)
    }
}

class CapabilitiesApiTest {
    private suspend fun client(
        fetcher: ThunderIDFetcher,
        endpoints: ThunderIDEndpoints = ThunderIDEndpoints(),
    ): ThunderIDClient {
        val storage = InMemoryStorageAdapter()
        TokenStore(storage).save(TokenResponse(accessToken = "admin-token", tokenType = "Bearer", expiresIn = 3600))
        val config =
            ThunderIDConfig(
                baseUrl = "https://localhost:8090",
                clientId = "client",
                endpoints = endpoints,
                http = ThunderIDHttpConfig(fetcher),
            )
        return ThunderIDClient().apply { initialize(config, storage) }
    }

    @Test
    fun `reads the capabilities collection under baseUrl`() =
        runTest {
            val fetcher = CapabilitiesFetcher()

            val capabilities = client(fetcher).getCapabilities()

            assertEquals(listOf("GET https://localhost:8090/capabilities"), fetcher.urls)
            assertEquals(CapabilityScope("ou", "ou-1"), capabilities.scope)
            assertTrue(capabilities.can("app"))
            assertFalse(capabilities.can("app", "create"))
        }

    @Test
    fun `the endpoint override replaces the collection URL`() =
        runTest {
            val fetcher = CapabilitiesFetcher()

            client(fetcher, ThunderIDEndpoints(capabilities = "https://api.example.com/v2/capabilities")).getCapabilities()

            assertEquals(listOf("GET https://api.example.com/v2/capabilities"), fetcher.urls)
        }

    @Test
    fun `a fetcher passed to the call takes precedence over the configured one`() =
        runTest {
            val configured = CapabilitiesFetcher()
            val perCall = CapabilitiesFetcher()

            client(configured).getCapabilities(perCall)

            assertTrue(configured.urls.isEmpty())
            assertEquals(1, perCall.urls.size)
        }

    @Test
    fun `a 403 surfaces as forbidden and other failures as their own codes`() =
        runTest {
            suspend fun codeFor(status: Int): ThunderIDErrorCode =
                try {
                    client(CapabilitiesFetcher(status, "{}")).getCapabilities()
                    fail("expected the read to fail")
                    error("unreachable")
                } catch (e: IAMException) {
                    e.code
                }

            assertEquals(ThunderIDErrorCode.FORBIDDEN, codeFor(403))
            assertEquals(ThunderIDErrorCode.SERVER_ERROR, codeFor(500))
        }

    @Test
    fun `odd JSON is tolerated and an unknown scope type is kept`() {
        val parsed =
            parseCapabilities(
                JsonParser.parseString(
                    """{"scope":{"type":"tenant","ouId":7},"resources":{"app":{"view":"yes","update":true},"x":3}}""",
                ),
            )

        assertEquals(CapabilityScope("tenant", null), parsed.scope)
        assertEquals(mapOf("app" to mapOf("update" to true)), parsed.resources)
        assertFalse(parsed.can("app"))

        val empty = parseCapabilities(null)
        assertEquals("", empty.scope.type)
        assertNull(empty.scope.ouId)
        assertTrue(empty.resources.isEmpty())
    }

    @Test
    fun `record access follows the locks and the update and delete capabilities`() {
        // isReadOnly, isOwnedByOU, update, delete -> readOnly, canDelete, canChangeState
        val rows =
            listOf(
                listOf(null, null, true, true) to RecordAccess(false, true, true),
                listOf(null, null, false, true) to RecordAccess(true, true, false),
                listOf(null, null, true, false) to RecordAccess(false, false, true),
                listOf(null, null, false, false) to RecordAccess(true, false, false),
                listOf(true, null, true, true) to RecordAccess(true, false, true),
                listOf(false, true, true, true) to RecordAccess(false, true, true),
                listOf(null, false, true, true) to RecordAccess(true, false, false),
                listOf(true, false, true, true) to RecordAccess(true, false, false),
            )
        rows.forEach { (input, expected) ->
            val (isReadOnly, isOwnedByOU, update, delete) = input
            val can = { resource: String, action: String ->
                resource == "user" && ((action == "update" && update == true) || (action == "delete" && delete == true))
            }
            assertEquals(input.toString(), expected, RecordAccess("user", isReadOnly, isOwnedByOU, can))
        }
        // Another resource's capabilities never open this one.
        assertEquals(RecordAccess(true, false, false), RecordAccess("app", null, null) { r, _ -> r == "user" })
    }
}
