// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.auth

import android.content.Context
import android.net.Uri
import android.util.Log
import dev.thunderid.android.EmbeddedFlowResponse
import dev.thunderid.android.EmbeddedSignInPayload
import dev.thunderid.android.FlowComponent
import dev.thunderid.android.FlowMeta
import dev.thunderid.android.FlowStatus
import dev.thunderid.android.FlowStepData
import dev.thunderid.android.ThunderIDClient
import dev.thunderid.android.api.auth.FederatedAuthSession
import dev.thunderid.compose.ThunderIDState
import dev.thunderid.compose.i18n.ThunderIDI18n
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
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

class SignUpTest {
    private val client = mockk<ThunderIDClient>()
    private val context = mockk<Context>()
    private val redirectUrl = "https://accounts.google.com/o/oauth2/v2/auth?state=abc"

    private fun redirection(
        token: String,
        url: String? = redirectUrl,
    ) = EmbeddedFlowResponse(
        flowId = "flow-1",
        flowStatus = FlowStatus.INCOMPLETE,
        type = "REDIRECTION",
        challengeToken = token,
        data = FlowStepData(redirectURL = url),
    )

    private val linkingPrompt =
        EmbeddedFlowResponse(
            flowId = "flow-1",
            flowStatus = FlowStatus.INCOMPLETE,
            type = "VIEW",
            data =
                FlowStepData(
                    additionalData = mapOf("linkingPromptDetails" to "[]"),
                    meta =
                        FlowMeta(
                            components =
                                listOf(
                                    FlowComponent(id = "kv", type = "KEY_VALUE_LIST", source = "linkingPromptDetails"),
                                ),
                        ),
                ),
        )

    @Before
    fun setUp() {
        mockkObject(FederatedAuthSession)
        mockkStatic(Log::class)
        every { Log.e(any(), any()) } returns 0
        mockkStatic(Uri::class)
        every { Uri.parse(redirectUrl) } returns callback(code = null)
    }

    @After
    fun tearDown() = unmockkAll()

    private fun callback(
        code: String?,
        state: String? = "abc",
    ): Uri =
        mockk {
            every { getQueryParameter("code") } returns code
            every { getQueryParameter("state") } returns state
        }

    private suspend fun TestScope.handle(
        response: EmbeddedFlowResponse,
        state: SignUpState,
        onError: ((String) -> Unit)? = null,
    ) = handleSignUpResponse(
        response,
        "action_google",
        state,
        ThunderIDState(client, ThunderIDI18n(), this),
        context,
        null,
        onError,
    )

    @Test
    fun `follows a federated sign-up redirect into the account-linking prompt`() =
        runTest {
            coEvery { FederatedAuthSession.launch(context, redirectUrl) } returns callback("provider-code")
            coEvery { client.signUp(any(), null) } returns linkingPrompt
            val state = SignUpState()

            handle(redirection("rotated-token"), state)

            coVerify {
                client.signUp(
                    EmbeddedSignInPayload(
                        flowId = "flow-1",
                        actionId = "action_google",
                        inputs = mapOf("code" to "provider-code", "state" to "abc"),
                        challengeToken = "rotated-token",
                    ),
                    null,
                )
            }
            assertEquals("KEY_VALUE_LIST", state.components.single().type)
            assertEquals("[]", state.additionalData["linkingPromptDetails"])
            assertNull(state.error)
        }

    @Test
    fun `follows a redirect that resumes straight into another one`() =
        runTest {
            coEvery { FederatedAuthSession.launch(context, redirectUrl) } returns callback("provider-code")
            coEvery { client.signUp(match { it.challengeToken == "token-1" }, null) } returns redirection("token-2")
            coEvery { client.signUp(match { it.challengeToken == "token-2" }, null) } returns linkingPrompt
            val state = SignUpState()

            handle(redirection("token-1"), state)

            coVerify(exactly = 2) { FederatedAuthSession.launch(context, redirectUrl) }
            assertEquals("KEY_VALUE_LIST", state.components.single().type)
        }

    @Test
    fun `keeps the step without an error when the user dismisses the browser`() =
        runTest {
            coEvery { FederatedAuthSession.launch(any(), any()) } throws CancellationException("dismissed")
            val state = SignUpState()

            handle(redirection("rotated-token"), state)

            assertNull(state.error)
            assertEquals("rotated-token", state.challengeToken)
            coVerify(exactly = 0) { client.signUp(any(), any()) }
        }

    @Test
    fun `rejects a callback whose state is not the one the sign-up was started with`() =
        runTest {
            coEvery { FederatedAuthSession.launch(any(), any()) } returns callback("injected-code", state = "other")
            val state = SignUpState()
            var reported: String? = null

            handle(redirection("rotated-token"), state) { reported = it }

            assertTrue(state.error!!.contains("Callback state does not match"))
            assertEquals(state.error, reported)
            coVerify(exactly = 0) { client.signUp(any(), any()) }
        }

    @Test
    fun `reports the sign-up error when a REDIRECTION step carries no redirect URL`() =
        runTest {
            val state = SignUpState()

            handle(redirection("rotated-token", url = null), state)

            assertEquals("Could not start federated sign-up", state.error)
            coVerify(exactly = 0) { FederatedAuthSession.launch(any(), any()) }
        }

    @Test
    fun `reports the failure reason of an ERROR step`() =
        runTest {
            val state = SignUpState()
            var reported: String? = null

            handle(
                EmbeddedFlowResponse(flowStatus = FlowStatus.ERROR, failureReason = "Account already linked"),
                state,
            ) { reported = it }

            assertEquals("Account already linked", state.error)
            assertEquals("Account already linked", reported)
        }
}
