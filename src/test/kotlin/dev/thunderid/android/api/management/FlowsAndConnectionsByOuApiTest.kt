// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.management

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
import dev.thunderid.android.token.TokenStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

private const val HOST = "https://localhost:8090"

/** Answers every request with [body], or with [respond] when given, and [status], and records it. */
private class OuRecordingFetcher(
    val body: String = "{}",
    val status: Int = 200,
    val respond: ((ThunderIDHttpRequest) -> String)? = null,
) : ThunderIDFetcher {
    val requests = mutableListOf<ThunderIDHttpRequest>()

    override suspend fun fetch(request: ThunderIDHttpRequest): ThunderIDHttpResponse {
        requests += request
        return ThunderIDHttpResponse(status, respond?.invoke(request) ?: body)
    }

    fun calls() = requests.map { "${it.method} ${it.url}" }
}

class FlowsAndConnectionsByOuApiTest {
    private suspend fun client(fetcher: ThunderIDFetcher): ThunderIDClient {
        val storage = InMemoryStorageAdapter()
        TokenStore(storage).save(TokenResponse(accessToken = "admin-token", tokenType = "Bearer", expiresIn = 3600))
        val config = ThunderIDConfig(baseUrl = HOST, clientId = "client", http = ThunderIDHttpConfig(fetcher))
        return ThunderIDClient().apply { initialize(config, storage) }
    }

    @Test
    fun `flows and connections are scoped to an organization unit through a filter, and a flow through ouId`() =
        runTest {
            val page = """{"totalResults":0,"startIndex":1,"count":0,"flows":[],"connections":[]}"""
            // The listing of one vendor is an array.
            val fetcher = OuRecordingFetcher { if (it.url.endsWith("/google")) "[]" else page }
            val client = client(fetcher)

            client.flows.list(flowType = "AUTHENTICATION", limit = 100, offset = 0, ouId = "ou\"1")
            client.flows.list()
            client.connections.list(limit = 100, ouId = "ou-1")
            client.connections.list(type = ConnectionType.GOOGLE, ouId = "ou-1")

            assertEquals(
                listOf(
                    "GET $HOST/flows?filter=ouId%20eq%20%22ou1%22&flowType=AUTHENTICATION&limit=100&offset=0",
                    "GET $HOST/flows",
                    "GET $HOST/connections?filter=ouId%20eq%20%22ou-1%22&limit=100",
                    "GET $HOST/connections/google",
                ),
                fetcher.calls(),
            )
        }

    @Test
    fun `a flow read for an organization unit sends it, and a flow read without one does not`() =
        runTest {
            val fetcher =
                OuRecordingFetcher(
                    """{"id":"f-1","name":"Login","flowType":"AUTHENTICATION","handle":"login","activeVersion":1,""" +
                        """"isReadOnly":true,"nodes":[]}""",
                )
            val client = client(fetcher)

            client.flows.get("f-1", ouId = "ou a")
            client.flows.get("f-1")

            assertEquals(listOf("GET $HOST/flows/f-1?ouId=ou%20a", "GET $HOST/flows/f-1"), fetcher.calls())
        }

    @Test
    fun `a flow shared with the unit parses with its variables and ownership`() =
        runTest {
            val fetcher =
                OuRecordingFetcher(
                    """{"id":"f-1","name":"Login","flowType":"AUTHENTICATION","handle":"login","activeVersion":1,""" +
                        """"isReadOnly":true,"ouId":"ou-1","isOwnedByOU":false,"variables":[{"identifier":"otp",""" +
                        """"type":"NUMBER_INPUT","defaultValue":120,"min":30,"max":600}]}""",
                )

            val flow = client(fetcher).flows.get("f-1", ouId = "ou-1")

            assertEquals(false, flow.isOwnedByOU)
            assertEquals("ou-1", flow.ouId)
            val otp = getFlowVariableDefinitions(flow).single()
            assertEquals("otp", otp.identifier)
            assertEquals(120.0, otp.defaultValue)
            assertEquals(600.0, otp.max)
        }

    @Test
    fun `listed flows report whether the unit owns them, under either name`() =
        runTest {
            val fetcher =
                OuRecordingFetcher(
                    """{"totalResults":3,"startIndex":1,"count":3,"flows":[""" +
                        """{"id":"f-1","handle":"a","flowType":"AUTHENTICATION","isOwnedByOU":true},""" +
                        """{"id":"f-2","handle":"b","flowType":"AUTHENTICATION","isOwnedByOU":false},""" +
                        """{"id":"f-3","handle":"c","flowType":"AUTHENTICATION"}]}""",
                )

            val flows = client(fetcher).flows.list(ouId = "ou-1").flows

            assertEquals(true, flows[0].isOwnedByOU)
            assertEquals(false, flows[1].isOwnedByOU)
            assertNull(flows[2].isOwnedByOU)
        }

    @Test
    fun `a refused listing surfaces as a distinct error`() =
        runTest {
            val forbidden = OuRecordingFetcher("""{"code":"FLM-1000","message":"Forbidden"}""", status = 403)
            val missing = OuRecordingFetcher("""{"code":"FLM-1001","message":"Not found"}""", status = 404)

            val listError =
                try {
                    client(forbidden).flows.list(ouId = "ou-1")
                    null
                } catch (e: IAMException) {
                    e
                }
            val getError =
                try {
                    client(missing).flows.get("f-1", ouId = "ou-1")
                    null
                } catch (e: IAMException) {
                    e
                }

            assertEquals(ThunderIDErrorCode.FORBIDDEN, listError?.code)
            assertEquals(ThunderIDErrorCode.NOT_FOUND, getError?.code)
            assertFalse(forbidden.requests.isEmpty())
        }

    @Test
    fun `a flow read needs an identifier`() =
        runTest {
            val fetcher = OuRecordingFetcher()
            try {
                client(fetcher).flows.get("", ouId = "ou-1")
                fail("Expected IAMException")
            } catch (e: IAMException) {
                assertEquals(ThunderIDErrorCode.INVALID_INPUT, e.code)
            }
            assertTrue(fetcher.requests.isEmpty())
        }
}
