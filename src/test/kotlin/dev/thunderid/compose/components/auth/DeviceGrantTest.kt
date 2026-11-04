// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.auth

import dev.thunderid.android.EmbeddedFlowResponse
import dev.thunderid.android.EmbeddedSignInPayload
import dev.thunderid.android.FlowAction
import dev.thunderid.android.FlowError
import dev.thunderid.android.FlowInput
import dev.thunderid.android.FlowStatus
import dev.thunderid.android.FlowStepData
import dev.thunderid.android.IAMException
import dev.thunderid.android.ThunderIDErrorCode
import dev.thunderid.compose.i18n.DefaultStrings
import dev.thunderid.compose.i18n.ThunderIDI18n
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceGrantTest {
    private val i18n = ThunderIDI18n()
    private val sent = mutableListOf<EmbeddedSignInPayload>()

    private val codePrompt =
        EmbeddedFlowResponse(
            flowId = "exec-1",
            flowStatus = FlowStatus.INCOMPLETE,
            type = "VIEW",
            challengeToken = "ct-1",
            data =
                FlowStepData(
                    inputs = listOf(FlowInput(name = "user_code", type = "TEXT_INPUT", required = true)),
                    actions = listOf(FlowAction(id = "verify", label = "Continue")),
                ),
        )

    private fun controller(vararg responses: EmbeddedFlowResponse): DeviceGrantController {
        val queue = ArrayDeque(responses.toList())
        return DeviceGrantController(i18n) { payload ->
            sent += payload
            queue.removeFirst()
        }
    }

    @Test
    fun `starts the flow with no inputs and renders the code prompt`() =
        runTest {
            val grant = controller(codePrompt)

            grant.start()

            assertEquals(EmbeddedSignInPayload(), sent.single())
            assertEquals(codePrompt, grant.step)
            assertNull(grant.result)
            assertEquals("exec-1", grant.flowId)
        }

    @Test
    fun `submits the entered code with the flow, action and challenge token, and approves the device`() =
        runTest {
            val approved = EmbeddedFlowResponse(flowStatus = FlowStatus.COMPLETE, assertion = "a")
            val grant = controller(codePrompt, approved)
            grant.start()

            grant.submit("verify", mapOf("user_code" to "WDJB-MJHT"))

            assertEquals(EmbeddedSignInPayload("exec-1", "verify", mapOf("user_code" to "WDJB-MJHT"), "ct-1"), sent.last())
            assertEquals(DeviceGrantResult.APPROVED, grant.result)
            assertNull(grant.step)
            assertNull(grant.flowId)
        }

    @Test
    fun `a denial and an expired code end the flow with their own outcome`() {
        assertEquals(
            DeviceGrantResult.DENIED,
            deviceGrantResult(EmbeddedFlowResponse(flowStatus = FlowStatus.ERROR, failureReason = "User denied the request")),
        )
        assertEquals(
            DeviceGrantResult.EXPIRED,
            deviceGrantResult(EmbeddedFlowResponse(flowStatus = FlowStatus.ERROR, error = FlowError(code = "DEVICE-EXPIRED_TOKEN"))),
        )
        assertNull(deviceGrantResult(codePrompt))
    }

    @Test
    fun `a step past its time allowance expires`() =
        runTest {
            val grant = controller(codePrompt)
            grant.start()

            grant.expire()

            assertEquals(DeviceGrantResult.EXPIRED, grant.result)
            assertNull(grant.step)
            assertNull(grant.submit("verify", emptyMap()))
        }

    @Test
    fun `request failures show translated messages and never the server text`() =
        runTest {
            val failing = DeviceGrantController(i18n) { throw IAMException(ThunderIDErrorCode.SERVER_ERROR, "pq: boom") }

            failing.start()

            assertEquals("Could not start device verification. Please try again.", failing.error)
            assertTrue(!failing.isLoading)
        }

    @Test
    fun `outcomes use the app's text first, then the translations`() {
        assertEquals(
            "Device authorized" to "You can return to your device.",
            deviceGrantResultText(i18n, DeviceGrantResult.APPROVED),
        )
        assertEquals(
            "Denied" to "Try again",
            deviceGrantResultText(i18n, DeviceGrantResult.DENIED, failedMessage = "Denied", failedDescription = "Try again"),
        )
        assertEquals("Code expired", deviceGrantResultText(i18n, DeviceGrantResult.EXPIRED).first)
    }

    @Test
    fun `the step state renders the server's fields and submits through the callback`() {
        val state = DeviceGrantState()
        val submitted = mutableListOf<Pair<String, Map<String, String>>>()
        state.update(codePrompt)
        state.onSubmit = { actionId -> submitted += actionId to state.fields() }

        state.setField("user_code", "ABCD")
        state.submit("verify")

        assertEquals(listOf("verify" to mapOf("user_code" to "ABCD")), submitted)
        assertEquals("deviceGrant", state.i18nPrefix)
    }

    @Test
    fun `ships the device grant strings with the JavaScript SDK's keys`() {
        listOf(
            "deviceGrant.errors.start",
            "deviceGrant.errors.continue",
            "deviceGrant.messages.complete",
            "deviceGrant.messages.completeDescription",
            "deviceGrant.messages.failed",
            "deviceGrant.messages.failedDescription",
            "deviceGrant.submit",
            "deviceGrant.federatedError",
        ).forEach { assertTrue(it, DefaultStrings.all.containsKey(it)) }
    }
}
