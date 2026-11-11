// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.auth

import com.google.gson.Gson
import dev.thunderid.android.EmbeddedFlowRequestConfig
import dev.thunderid.android.EmbeddedFlowResponse
import dev.thunderid.android.EmbeddedSignInPayload
import dev.thunderid.android.IAMException
import dev.thunderid.android.InMemoryStorageAdapter
import dev.thunderid.android.ThunderIDClient
import dev.thunderid.android.ThunderIDConfig
import dev.thunderid.android.ThunderIDErrorCode
import dev.thunderid.android.backend.FakeBackend
import dev.thunderid.android.backend.response
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

private const val SERVER = "https://id.example.com"
private const val EXPIRED_BODY = """{"code":"FES-1004","message":"Invalid request","description":"Invalid flow execution ID"}"""

/** Covers the expiry of an app-native flow execution: the step's `expiresIn`, and a step refused since it expired. */
class FlowExpiryTest {
    private val fake = FakeBackend()
    private val client = ThunderIDClient(fake)
    private val config = ThunderIDConfig(baseUrl = SERVER, applicationId = "app-1")

    private suspend inline fun assertFails(
        code: ThunderIDErrorCode,
        block: () -> Unit,
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

    @Test
    fun `a step carries its expiry`() {
        val step = Gson().fromJson("""{"executionId":"f-1","flowStatus":"INCOMPLETE","expiresIn":3540}""", EmbeddedFlowResponse::class.java)
        assertEquals(3540L, step.expiresIn)
    }

    @Test
    fun `a step without an expiry has none`() {
        val step = Gson().fromJson("""{"executionId":"f-1","flowStatus":"COMPLETE"}""", EmbeddedFlowResponse::class.java)
        assertNull(step.expiresIn)
    }

    @Test
    fun `a sign-in step for an expired execution fails as flow expired`() =
        runTest {
            client.initialize(config, InMemoryStorageAdapter())
            fake.on("POST", "$SERVER/flow/execute", response(400, EXPIRED_BODY))

            val error =
                assertFails(ThunderIDErrorCode.FLOW_EXPIRED) {
                    client.signIn(EmbeddedSignInPayload(flowId = "f-1", actionId = "submit"), EmbeddedFlowRequestConfig("app-1"))
                }

            assertEquals("FES-1004", error.serverError?.code)
        }

    @Test
    fun `a sign-up step for an expired execution fails as flow expired`() =
        runTest {
            client.initialize(config, InMemoryStorageAdapter())
            fake.on("POST", "$SERVER/flow/execute", response(400, EXPIRED_BODY))

            assertFails(ThunderIDErrorCode.FLOW_EXPIRED) {
                client.signUp(EmbeddedSignInPayload(flowId = "f-1", actionId = "submit"))
            }
        }

    @Test
    fun `other refused steps keep their code`() =
        runTest {
            client.initialize(config, InMemoryStorageAdapter())
            fake.on("POST", "$SERVER/flow/execute", response(400, """{"code":"FES-1006","message":"Invalid request"}"""))

            val error =
                assertFails(ThunderIDErrorCode.INVALID_INPUT) {
                    client.signIn(EmbeddedSignInPayload(flowId = "f-1", actionId = "submit"), EmbeddedFlowRequestConfig("app-1"))
                }

            assertEquals("FES-1006", error.serverError?.code)
        }
}
