// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.management

import com.google.gson.JsonParser
import dev.thunderid.android.AttributeVerification
import dev.thunderid.android.FlowType
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

private const val BASE_URL = "https://localhost:8090"

private const val LINK_SENT =
    """{"executionId":"exec-1","flowStatus":"INCOMPLETE","type":"VIEW","data":{"additionalData":""" +
        """{"channel":"email","emailSent":"true","maskedDestination":"t•••@example.com"}}}"""

/** Answers every request with [status] and [body], and records it. */
private class FlowFetcher(
    val body: String = LINK_SENT,
    val status: Int = 200,
    val configuredHandle: String? = "default-admin-attribute-verification-flow",
) : ThunderIDFetcher {
    val requests = mutableListOf<ThunderIDHttpRequest>()

    override suspend fun fetch(request: ThunderIDHttpRequest): ThunderIDHttpResponse {
        requests += request
        if (request.url == "$BASE_URL/server-config/flow") {
            val merged = configuredHandle?.let { """{"adminAttributeVerificationFlow":{"defaultHandle":"$it"}}""" } ?: "{}"
            return ThunderIDHttpResponse(200, """{"merged":$merged}""")
        }
        return ThunderIDHttpResponse(status, body)
    }

    val flowRequests get() = requests.filter { it.url == "$BASE_URL/flow/execute" }
}

private fun sent(additionalData: String) = """{"flowStatus":"INCOMPLETE","data":{"additionalData":$additionalData}}"""

class UserAttributeVerificationTest {
    private suspend fun client(fetcher: ThunderIDFetcher): ThunderIDClient {
        val storage = InMemoryStorageAdapter()
        TokenStore(storage).save(TokenResponse(accessToken = "admin-token", tokenType = "Bearer", expiresIn = 3600))
        val config = ThunderIDConfig(baseUrl = BASE_URL, clientId = "client", http = ThunderIDHttpConfig(fetcher))
        return ThunderIDClient().apply { initialize(config, storage) }
    }

    private suspend fun send(answer: String) = client(FlowFetcher(answer)).users.sendAttributeVerification("u-1", "email")

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

    @Test
    fun `starts the administrator-initiated flow with the administrator's token and returns the sent link`() =
        runTest {
            val fetcher = FlowFetcher()

            val result = client(fetcher).users.sendAttributeVerification("u-1", "workEmail")

            assertEquals(UserAttributeVerificationResult("email", emailSent = true, maskedDestination = "t•••@example.com"), result)
            assertEquals("GET", fetcher.requests.first().method)
            assertEquals("$BASE_URL/server-config/flow", fetcher.requests.first().url)
            val request = fetcher.flowRequests.single()
            assertEquals("POST", request.method)
            assertEquals("$BASE_URL/flow/execute", request.url)
            assertEquals("Bearer admin-token", request.headers["Authorization"])
            assertEquals(
                JsonParser.parseString(
                    """{"flowType":"ADMIN_INITIATED","handle":"default-admin-attribute-verification-flow",""" +
                        """"inputs":{"subject":"u-1","attribute":"workEmail"},"verbose":true}""",
                ),
                JsonParser.parseString(request.body),
            )
        }

    @Test
    fun `runs the flow the app names`() =
        runTest {
            val fetcher = FlowFetcher()

            client(fetcher).users.sendAttributeVerification("u-1", "email", handle = "custom-verification")

            assertEquals("custom-verification", JsonParser.parseString(fetcher.requests.single().body).asJsonObject["handle"].asString)
        }

    @Test
    fun `runs the handle the server configures`() =
        runTest {
            val fetcher = FlowFetcher(configuredHandle = "tenant-verification")

            client(fetcher).users.sendAttributeVerification("u-1", "email")

            assertEquals("tenant-verification", JsonParser.parseString(fetcher.flowRequests.single().body).asJsonObject["handle"].asString)
        }

    @Test
    fun `fails as not configured, with no flow request, when the server configures no handle`() =
        runTest {
            val fetcher = FlowFetcher(configuredHandle = null)

            val error = expectError<AdministrationFlowError> { client(fetcher).users.sendAttributeVerification("u-1", "email") }

            assertEquals("sendUserAttributeVerification-NotConfiguredError-001", error.errorCode)
            assertTrue(fetcher.flowRequests.isEmpty())
        }

    @Test
    fun `reads whether the email was sent and leaves out what the flow does not report`() =
        runTest {
            val notSent = send(sent("""{"channel":"email","emailSent":"false","maskedDestination":""}"""))
            val bare = send(sent("""{"channel":"email"}"""))

            assertEquals(UserAttributeVerificationResult("email", emailSent = false), notSent)
            assertEquals(UserAttributeVerificationResult("email"), bare)
        }

    @Test
    fun `a refused step raises an administration flow error with the server's code`() =
        runTest {
            val refused = """{"flowStatus":"ERROR","error":{"code":"FET-1114","message":{"defaultValue":"User is suspended"}}}"""

            val error = expectError<AdministrationFlowError> { send(refused) }

            assertEquals("FET-1114", error.errorCode)
            assertEquals(ThunderIDErrorCode.ADMINISTRATION_FLOW_FAILED, error.code)
            assertEquals("FET-1114", error.flowError?.code)
        }

    @Test
    fun `a refused step without a code gets the operation's code`() =
        runTest {
            val error = expectError<AdministrationFlowError> { send("""{"flowStatus":"ERROR"}""") }

            assertEquals("sendUserAttributeVerification-FlowError-001", error.errorCode)
        }

    @Test
    fun `any other answer is an unexpected state, never a success`() =
        runTest {
            listOf(
                """{"flowStatus":"COMPLETE","data":{"additionalData":{"channel":"email"}}}""",
                sent("""{"maskedDestination":"t•••@example.com"}"""),
                sent("""{"channel":""}"""),
                "{}",
            ).forEach { answer ->
                val error = expectError<AdministrationFlowError> { send(answer) }

                assertEquals(answer, "sendUserAttributeVerification-UnexpectedStateError-001", error.errorCode)
            }
        }

    @Test
    fun `refuses a blank user or attribute without a request`() =
        runTest {
            val fetcher = FlowFetcher()
            val client = client(fetcher)

            listOf("" to "email", "u-1" to " ").forEach { (id, attribute) ->
                val error = expectError<IAMException> { client.users.sendAttributeVerification(id, attribute) }

                assertEquals(ThunderIDErrorCode.INVALID_INPUT, error.code)
                assertFalse(error is AdministrationFlowError)
            }
            assertTrue(fetcher.requests.isEmpty())
        }

    @Test
    fun `an HTTP refusal stays the SDK's API error`() =
        runTest {
            val error =
                expectError<IAMException> {
                    client(FlowFetcher("""{"code":"FES-1017"}""", status = 403)).users.sendAttributeVerification("u-1", "email")
                }

            assertEquals(ThunderIDErrorCode.FORBIDDEN, error.code)
            assertFalse(error is AdministrationFlowError)
        }

    @Test
    fun `a managed user carries the verified attributes the server reports`() =
        runTest {
            val record =
                """{"id":"u-1","ouId":"ou-1","type":"person",""" +
                    """"verifiedAttributes":{"email":{"verifiedAt":"2026-11-03T10:00:00Z"}}}"""
            val verified = client(FlowFetcher(record)).users.get("u-1")
            val unreported = client(FlowFetcher("""{"id":"u-1","ouId":"ou-1","type":"person"}""")).users.get("u-1")

            assertEquals("2026-11-03T10:00:00Z", verified.verifiedAttributes?.get("email")?.verifiedAt)
            assertNull(unreported.verifiedAttributes)
        }

    @Test
    fun `wire names match the server contract`() {
        assertEquals("ADMIN_INITIATED", FlowType.ADMIN_INITIATED.value)
        assertEquals("adminAttributeVerificationFlow", AdministrationFlowConfigKey.ADMIN_ATTRIBUTE_VERIFICATION)
        assertEquals("subject", AdministrationFlowInput.SUBJECT)
        assertEquals("channel", AttributeVerification.CHANNEL_KEY)
        assertEquals("emailSent", AdministrationFlowData.EMAIL_SENT)
    }
}
