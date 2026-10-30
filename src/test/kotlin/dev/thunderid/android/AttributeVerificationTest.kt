// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.thunderid.android.token.TokenStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

private class VerificationTransport : ThunderIDFetcher {
    val requests = mutableListOf<ThunderIDHttpRequest>()
    var answer: () -> ThunderIDHttpResponse = { ThunderIDHttpResponse(200, STEP) }

    override suspend fun fetch(request: ThunderIDHttpRequest): ThunderIDHttpResponse {
        requests += request
        return answer()
    }
}

private const val BASE_URL = "https://localhost:8090"
private const val STEP =
    """{"executionId":"exec-1","flowStatus":"INCOMPLETE","challengeToken":"ct-1",""" +
        """"data":{"additionalData":{"maskedDestination":"j***@example.com"}}}"""

class AttributeVerificationTest {
    private val transport = VerificationTransport()

    private suspend fun client(
        endpoints: ThunderIDEndpoints = ThunderIDEndpoints(),
        http: ThunderIDHttpConfig = ThunderIDHttpConfig(),
    ): ThunderIDClient {
        val storage = InMemoryStorageAdapter()
        TokenStore(storage).save(TokenResponse(accessToken = "user-token", tokenType = "Bearer", expiresIn = 3600))
        val config =
            ThunderIDConfig(
                baseUrl = BASE_URL,
                clientId = "client",
                applicationId = "app-1",
                endpoints = endpoints,
                http = http,
            )
        return ThunderIDClient(transport).apply { initialize(config, storage) }
    }

    private fun body(request: ThunderIDHttpRequest): JsonObject = JsonParser.parseString(request.body).asJsonObject

    private suspend fun expectError(
        code: ThunderIDErrorCode,
        block: suspend () -> Unit,
    ): IAMException {
        try {
            block()
        } catch (e: IAMException) {
            assertEquals(code, e.code)
            return e
        }
        fail("Expected IAMException $code")
        throw AssertionError()
    }

    // ── Starting a verification ─────────────────────────────────────────────

    @Test
    fun `start sends a SELF_SERVICE request with the default handle and the attribute`() =
        runTest {
            val step = client().verifyAttribute("email")

            val request = transport.requests.single()
            assertEquals("POST", request.method)
            assertEquals("$BASE_URL/flow/execute", request.url)
            val json = body(request)
            assertEquals("SELF_SERVICE", json["flowType"].asString)
            assertEquals("default-self-attribute-verification-flow", json["handle"].asString)
            assertEquals("email", json["inputs"].asJsonObject["attribute"].asString)
            assertTrue(json["verbose"].asBoolean)
            assertEquals("exec-1", step.flowId)
            assertEquals("j***@example.com", step.data?.additionalData?.get("maskedDestination"))
        }

    @Test
    fun `start sends the access token and no user or application ID`() =
        runTest {
            client().verifyAttribute("email")

            val request = transport.requests.single()
            assertEquals("Bearer user-token", request.headers["Authorization"])
            val keys = body(request).keySet()
            assertEquals(setOf("flowType", "handle", "inputs", "verbose"), keys)
        }

    @Test
    fun `start sends the handle the app names`() =
        runTest {
            client().verifyAttribute("mobile", handle = "custom-verification")

            assertEquals("custom-verification", body(transport.requests.single())["handle"].asString)
        }

    @Test
    fun `start without a handle is rejected before any request`() =
        runTest {
            val client = client()

            expectError(ThunderIDErrorCode.INVALID_INPUT) { client.verifyAttribute("email", handle = " ") }

            assertTrue(transport.requests.isEmpty())
        }

    @Test
    fun `start without an attribute is rejected before any request`() =
        runTest {
            val client = client()

            expectError(ThunderIDErrorCode.INVALID_INPUT) { client.verifyAttribute("") }

            assertTrue(transport.requests.isEmpty())
        }

    @Test
    fun `verifyAttribute before initialize fails with SDK_NOT_INITIALIZED`() =
        runTest {
            expectError(ThunderIDErrorCode.SDK_NOT_INITIALIZED) { ThunderIDClient(transport).verifyAttribute("email") }
            assertTrue(transport.requests.isEmpty())
        }

    // ── Continuing a verification ───────────────────────────────────────────

    @Test
    fun `continuation sends the step, the access token and no user or application ID`() =
        runTest {
            transport.answer = { ThunderIDHttpResponse(200, """{"executionId":"exec-1","flowStatus":"COMPLETE"}""") }

            val step =
                client().verifyAttribute(
                    EmbeddedSignInPayload(
                        flowId = "exec-1",
                        actionId = "action_email_verify",
                        inputs = mapOf("otp" to "123456"),
                        challengeToken = "ct-1",
                    ),
                )

            assertEquals(FlowStatus.COMPLETE, step.flowStatus)
            val request = transport.requests.single()
            assertEquals("$BASE_URL/flow/execute", request.url)
            assertEquals("Bearer user-token", request.headers["Authorization"])
            val json = body(request)
            assertEquals("exec-1", json["executionId"].asString)
            assertEquals("action_email_verify", json["action"].asString)
            assertEquals("ct-1", json["challengeToken"].asString)
            assertEquals("123456", json["inputs"].asJsonObject["otp"].asString)
            assertEquals(setOf("executionId", "action", "challengeToken", "inputs", "verbose"), json.keySet())
        }

    @Test
    fun `continuation without an execution ID is rejected before any request`() =
        runTest {
            val client = client()

            expectError(ThunderIDErrorCode.INVALID_INPUT) { client.verifyAttribute(EmbeddedSignInPayload(actionId = "verify")) }

            assertTrue(transport.requests.isEmpty())
        }

    @Test
    fun `a resend continuation sends no inputs`() =
        runTest {
            client().verifyAttribute(EmbeddedSignInPayload(flowId = "exec-1", actionId = "action_email_resend"))

            assertFalse(body(transport.requests.single()).has("inputs"))
        }

    // ── Request URL ─────────────────────────────────────────────────────────

    @Test
    fun `verification posts to the base URL, whatever the management overrides`() =
        runTest {
            val client = client(endpoints = ThunderIDEndpoints(users = "https://users.example.com/users"))

            client.verifyAttribute("email")
            client.verifyAttribute(EmbeddedSignInPayload(flowId = "exec-1"))

            assertEquals(listOf("$BASE_URL/flow/execute", "$BASE_URL/flow/execute"), transport.requests.map { it.url })
        }

    @Test
    fun `verification ignores the management fetcher`() =
        runTest {
            val management = VerificationTransport()

            client(http = ThunderIDHttpConfig(fetcher = management)).verifyAttribute("email")

            assertTrue(management.requests.isEmpty())
            assertEquals(1, transport.requests.size)
        }

    // ── Errors ──────────────────────────────────────────────────────────────

    @Test
    fun `an ERROR step is a VERIFICATION_FAILED refusal without the server's reason`() =
        runTest {
            transport.answer = {
                ThunderIDHttpResponse(
                    200,
                    """{"executionId":"exec-1","flowStatus":"ERROR","error":{"code":"FET-1112",""" +
                        """"message":{"key":"refused","defaultValue":"secret server detail"}}}""",
                )
            }
            val client = client()

            val error = expectError(ThunderIDErrorCode.VERIFICATION_FAILED) { client.verifyAttribute("email") }

            assertEquals("[VERIFICATION_FAILED] The server refused the verification", error.message)
            assertNull(error.cause)
        }

    @Test
    fun `a 4xx is a VERIFICATION_FAILED refusal without the response body`() =
        runTest {
            val client = client()
            for (status in listOf(400, 401, 403, 404, 409)) {
                transport.answer = { ThunderIDHttpResponse(status, """{"code":"FES-1005","message":"secret server detail"}""") }

                val start = expectError(ThunderIDErrorCode.VERIFICATION_FAILED) { client.verifyAttribute("email") }
                val next =
                    expectError(ThunderIDErrorCode.VERIFICATION_FAILED) {
                        client.verifyAttribute(EmbeddedSignInPayload(flowId = "exec-1"))
                    }

                for (error in listOf(start, next)) {
                    assertEquals("[VERIFICATION_FAILED] The server refused the verification", error.message)
                    assertNull(error.cause)
                }
            }
        }

    @Test
    fun `a 5xx is reported as SERVER_ERROR`() =
        runTest {
            transport.answer = { ThunderIDHttpResponse(503, "unavailable") }
            val client = client()

            expectError(ThunderIDErrorCode.SERVER_ERROR) { client.verifyAttribute("email") }
        }

    @Test
    fun `a network failure is reported as NETWORK_ERROR`() =
        runTest {
            transport.answer = { throw IOException("connection reset") }
            val client = client()

            expectError(ThunderIDErrorCode.NETWORK_ERROR) { client.verifyAttribute("email") }
        }

    @Test
    fun `an unreadable response is reported as SERVER_ERROR`() =
        runTest {
            transport.answer = { ThunderIDHttpResponse(200, "<html>") }
            val client = client()

            expectError(ThunderIDErrorCode.SERVER_ERROR) { client.verifyAttribute("email") }
        }

    // ── Profile and flow metadata ───────────────────────────────────────────

    @Test
    fun `the profile reports verified attributes, none, or unknown`() =
        runTest {
            val client = client()

            transport.answer = {
                ThunderIDHttpResponse(
                    200,
                    """{"id":"u1","attributes":{"email":"a@example.com"},""" +
                        """"verifiedAttributes":{"email":{"verifiedAt":"2026-10-20T10:00:00Z"}}}""",
                )
            }
            assertEquals(
                "2026-10-20T10:00:00Z",
                client
                    .getUserProfile()
                    .verifiedAttributes
                    ?.get("email")
                    ?.verifiedAt,
            )

            transport.answer = { ThunderIDHttpResponse(200, """{"id":"u1","attributes":{},"verifiedAttributes":{}}""") }
            assertEquals(emptyMap<String, VerifiedAttribute>(), client.getUserProfile().verifiedAttributes)

            transport.answer = { ThunderIDHttpResponse(200, """{"id":"u1","attributes":{}}""") }
            assertNull(client.getUserProfile().verifiedAttributes)
        }

    // ── Eligibility ─────────────────────────────────────────────────────────

    @Test
    fun `email and mobile properties are verifiable, whatever their case`() {
        val schema =
            mapOf(
                "email" to AttributeSchema(type = "email"),
                "mobile_number" to AttributeSchema(type = "MOBILE"),
            )

        assertTrue(AttributeVerification.isVerifiableAttribute("email", schema))
        assertTrue(AttributeVerification.isVerifiableAttribute("mobile_number", schema))
    }

    @Test
    fun `other types, multi-valued properties and unknown attributes are not verifiable`() {
        val schema =
            mapOf(
                "given_name" to AttributeSchema(type = "string"),
                "emails" to AttributeSchema(type = "array"),
                "untyped" to AttributeSchema(),
            )

        assertFalse(AttributeVerification.isVerifiableAttribute("given_name", schema))
        assertFalse(AttributeVerification.isVerifiableAttribute("emails", schema))
        assertFalse(AttributeVerification.isVerifiableAttribute("untyped", schema))
        assertFalse(AttributeVerification.isVerifiableAttribute("email", schema))
        assertFalse(AttributeVerification.isVerifiableAttribute("email", null))
    }

    @Test
    fun `status shows for email and mobile, and only email opens after a save`() {
        assertTrue(AttributeVerification.isVerifiableType("email"))
        assertTrue(AttributeVerification.isVerifiableType("mobile"))
        assertFalse(AttributeVerification.isVerifiableType("string"))
        assertFalse(AttributeVerification.isVerifiableType(null))

        assertTrue(AttributeVerification.opensAfterSave("email"))
        assertFalse(AttributeVerification.opensAfterSave("mobile"))
    }
}
