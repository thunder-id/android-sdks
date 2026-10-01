// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.presentation.auth

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import dev.thunderid.android.EmbeddedFlowRequestConfig
import dev.thunderid.android.EmbeddedFlowResponse
import dev.thunderid.android.EmbeddedSignInPayload
import dev.thunderid.android.FlowAction
import dev.thunderid.android.FlowStatus
import dev.thunderid.android.FlowStepData
import dev.thunderid.android.FlowType
import dev.thunderid.android.ThunderIDClient
import dev.thunderid.android.auth.FederatedAuthSession
import dev.thunderid.android.auth.PasskeyClient
import dev.thunderid.compose.ThunderIDState
import dev.thunderid.compose.i18n.ThunderIDI18n
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class FederatedRedirectTest {
    private val client = mockk<ThunderIDClient>()
    private val context = mockk<Context>()
    private val request = EmbeddedFlowRequestConfig("app-id", FlowType.AUTHENTICATION)

    // language=JSON
    private val redirection =
        Gson().fromJson(
            """
            {
                "executionId": "019fb7c3-8b38-7847-8803-3492c0cb9d9b",
                "flowStatus": "INCOMPLETE",
                "type": "REDIRECTION",
                "challengeToken": "rotated-token",
                "data": {"redirectURL": "https://accounts.google.com/o/oauth2/v2/auth?state=abc"}
            }
            """.trimIndent(),
            EmbeddedFlowResponse::class.java,
        )

    @Before
    fun setUp() {
        mockkObject(FederatedAuthSession)
        mockkStatic(Log::class)
        every { Log.e(any(), any()) } returns 0
        mockkStatic(Uri::class)
        every { Uri.parse(redirection.data!!.redirectURL) } returns callback(code = null, state = "abc")
    }

    @After
    fun tearDown() = unmockkAll()

    private suspend fun TestScope.handle(
        response: EmbeddedFlowResponse,
        signInState: SignInState,
        onError: ((String) -> Unit)? = null,
    ) {
        handleSignInResponse(
            response = response,
            actionId = "action_google",
            signInState = signInState,
            thunderState = ThunderIDState(client, ThunderIDI18n(), this),
            request = request,
            context = context,
            passkeyClient = mockk<PasskeyClient>(),
            onComplete = null,
            onError = onError,
        )
    }

    private fun callback(
        code: String?,
        state: String? = "abc",
    ): Uri =
        mockk {
            every { getQueryParameter("code") } returns code
            every { getQueryParameter("state") } returns state
        }

    @Test
    fun `decodes the redirect URL of a REDIRECTION step`() {
        assertEquals("https://accounts.google.com/o/oauth2/v2/auth?state=abc", redirection.data?.redirectURL)
    }

    @Test
    fun `resubmits the provider code and state with the pressed action and the rotated challenge token`() =
        runTest {
            val next =
                EmbeddedFlowResponse(
                    flowId = redirection.flowId,
                    flowStatus = FlowStatus.PROMPT_ONLY,
                    data = FlowStepData(actions = listOf(FlowAction(ref = "action_confirm"))),
                )
            val payload = slot<EmbeddedSignInPayload>()
            val redirectUrl = redirection.data!!.redirectURL!!
            coEvery { FederatedAuthSession.launch(context, redirectUrl) } returns callback("provider-code")
            coEvery { client.signIn(capture(payload), request) } returns next
            val signInState = SignInState()

            handle(redirection, signInState)

            assertEquals(
                EmbeddedSignInPayload(
                    flowId = redirection.flowId,
                    actionId = "action_google",
                    inputs = mapOf("code" to "provider-code", "state" to "abc"),
                    challengeToken = "rotated-token",
                ),
                payload.captured,
            )
            assertEquals(listOf("action_confirm"), signInState.actions.map { it.ref })
            assertNull(signInState.error)
        }

    @Test
    fun `reports an error and resubmits nothing when the callback carries no code`() =
        runTest {
            coEvery { FederatedAuthSession.launch(any(), any()) } returns callback(null)
            val signInState = SignInState()
            var reported: String? = null

            handle(redirection, signInState) { reported = it }

            assertEquals("[INVALID_GRANT] Authorization code missing from callback URL", signInState.error)
            assertEquals(signInState.error, reported)
            coVerify(exactly = 0) { client.signIn(any(), any()) }
        }

    @Test
    fun `rejects a callback whose state is not the one the sign-in was started with`() =
        runTest {
            coEvery { FederatedAuthSession.launch(any(), any()) } returns callback("injected-code", state = "other")
            val signInState = SignInState()

            handle(redirection, signInState)

            assertEquals("[INVALID_GRANT] Callback state does not match the sign-in request", signInState.error)
            coVerify(exactly = 0) { client.signIn(any(), any()) }
        }

    @Test
    fun `keeps the step without an error when the user dismisses the browser`() =
        runTest {
            coEvery { FederatedAuthSession.launch(any(), any()) } throws CancellationException("dismissed")
            val signInState = SignInState()

            handle(redirection, signInState)

            assertNull(signInState.error)
            assertEquals("rotated-token", signInState.challengeToken)
            coVerify(exactly = 0) { client.signIn(any(), any()) }
        }

    @Test
    fun `reports an error when a REDIRECTION step carries no redirect URL`() =
        runTest {
            val signInState = SignInState()

            handle(redirection.copy(data = FlowStepData()), signInState)

            assertEquals("Could not start federated sign-in", signInState.error)
            coVerify(exactly = 0) { FederatedAuthSession.launch(any(), any()) }
        }
}
