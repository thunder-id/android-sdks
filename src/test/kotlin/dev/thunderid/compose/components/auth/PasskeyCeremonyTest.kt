// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.auth

import android.content.Context
import android.util.Log
import dev.thunderid.android.EmbeddedFlowRequestConfig
import dev.thunderid.android.EmbeddedFlowResponse
import dev.thunderid.android.EmbeddedSignInPayload
import dev.thunderid.android.FlowAction
import dev.thunderid.android.FlowError
import dev.thunderid.android.FlowErrorText
import dev.thunderid.android.FlowStatus
import dev.thunderid.android.FlowStepData
import dev.thunderid.android.FlowType
import dev.thunderid.android.IAMException
import dev.thunderid.android.ThunderIDClient
import dev.thunderid.android.ThunderIDErrorCode
import dev.thunderid.android.api.auth.PasskeyClient
import dev.thunderid.compose.ThunderIDState
import dev.thunderid.compose.i18n.ThunderIDI18n
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PasskeyCeremonyTest {
    private val client = mockk<ThunderIDClient>()
    private val passkeyClient = mockk<PasskeyClient>()
    private val context = mockk<Context>()
    private val request = EmbeddedFlowRequestConfig("app-id", FlowType.AUTHENTICATION)

    private val passkeyStep =
        EmbeddedFlowResponse(
            flowId = "flow-1",
            flowStatus = FlowStatus.INCOMPLETE,
            challengeToken = "token-1",
            data = FlowStepData(additionalData = mapOf("passkeyChallenge" to "{\"challenge\":\"Y2hhbGxlbmdl\"}")),
        )

    private val retryStep =
        EmbeddedFlowResponse(
            flowId = "flow-1",
            flowStatus = FlowStatus.INCOMPLETE,
            challengeToken = "token-2",
            data = FlowStepData(actions = listOf(FlowAction(ref = "action_passkey"))),
            error =
                FlowError(
                    code = "FET-1019",
                    message = FlowErrorText(key = "passkey_auth_failed", defaultValue = "Passkey authentication failed"),
                ),
        )

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.e(any(), any()) } returns 0
    }

    @After
    fun tearDown() = unmockkAll()

    private suspend fun TestScope.handle(
        signInState: SignInState,
        onError: ((String) -> Unit)? = null,
    ) {
        handleSignInResponse(
            response = passkeyStep,
            actionId = "action_passkey",
            signInState = signInState,
            thunderState = ThunderIDState(client, ThunderIDI18n(), this),
            request = request,
            context = context,
            passkeyClient = passkeyClient,
            onComplete = null,
            onError = onError,
        )
    }

    @Test
    fun `submits the credential inputs when the ceremony succeeds`() =
        runTest {
            val credential = mapOf("credentialId" to "cred-1", "signature" to "sig")
            val payload = slot<EmbeddedSignInPayload>()
            coEvery { passkeyClient.authenticate(context, any()) } returns credential
            coEvery { client.signIn(capture(payload), request) } returns retryStep.copy(error = null)

            handle(SignInState())

            assertEquals(
                EmbeddedSignInPayload(flowId = "flow-1", inputs = credential, challengeToken = "token-1"),
                payload.captured,
            )
        }

    @Test
    fun `reports a failed ceremony to the flow and shows the step and error it returns`() =
        runTest {
            val payload = slot<EmbeddedSignInPayload>()
            coEvery { passkeyClient.authenticate(context, any()) } throws
                IAMException(ThunderIDErrorCode.PASSKEY_CANCELLED, "Passkey ceremony was cancelled by the user")
            coEvery { client.signIn(capture(payload), request) } returns retryStep
            val signInState = SignInState()
            var reported: String? = null

            handle(signInState) { reported = it }

            assertEquals(
                EmbeddedSignInPayload(
                    flowId = "flow-1",
                    inputs = mapOf("passkeyError" to "PASSKEY_CANCELLED"),
                    challengeToken = "token-1",
                ),
                payload.captured,
            )
            assertEquals(listOf("action_passkey"), signInState.actions.map { it.ref })
            assertEquals("token-2", signInState.challengeToken)
            assertEquals("Passkey authentication failed", signInState.error)
            assertNull(reported)
        }

    @Test
    fun `reports a failure without an SDK error code as a failed passkey`() =
        runTest {
            val payload = slot<EmbeddedSignInPayload>()
            coEvery { passkeyClient.authenticate(context, any()) } throws IllegalStateException("bad response")
            coEvery { client.signIn(capture(payload), request) } returns retryStep

            handle(SignInState())

            assertEquals(mapOf("passkeyError" to "PASSKEY_FAILED"), payload.captured.inputs)
        }

    @Test
    fun `surfaces the ceremony error when the failure report itself fails`() =
        runTest {
            coEvery { passkeyClient.authenticate(context, any()) } throws
                IAMException(ThunderIDErrorCode.PASSKEY_NO_CREDENTIAL, "No passkey credential is available")
            coEvery { client.signIn(any(), request) } throws
                IAMException(ThunderIDErrorCode.NETWORK_ERROR, "offline")
            val signInState = SignInState()
            var reported: String? = null

            handle(signInState) { reported = it }

            assertEquals("[PASSKEY_NO_CREDENTIAL] No passkey credential is available", signInState.error)
            assertEquals(signInState.error, reported)
        }

    @Test
    fun `lets coroutine cancellation propagate without reporting a failure`() =
        runTest {
            coEvery { passkeyClient.authenticate(context, any()) } throws CancellationException("left composition")

            val thrown = runCatching { handle(SignInState()) }.exceptionOrNull()

            assertTrue(thrown is CancellationException)
            coVerify(exactly = 0) { client.signIn(any(), any()) }
        }
}
