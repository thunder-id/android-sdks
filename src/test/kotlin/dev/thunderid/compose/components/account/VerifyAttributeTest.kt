// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.account

import com.google.gson.Gson
import com.google.gson.JsonParser
import dev.thunderid.android.AttributeSchema
import dev.thunderid.android.EmbeddedFlowResponse
import dev.thunderid.android.EmbeddedSignInPayload
import dev.thunderid.android.FlowAction
import dev.thunderid.android.FlowComponent
import dev.thunderid.android.FlowError
import dev.thunderid.android.FlowErrorText
import dev.thunderid.android.FlowMeta
import dev.thunderid.android.FlowStatus
import dev.thunderid.android.FlowStepData
import dev.thunderid.android.IAMException
import dev.thunderid.android.InMemoryStorageAdapter
import dev.thunderid.android.ThunderIDClient
import dev.thunderid.android.ThunderIDConfig
import dev.thunderid.android.ThunderIDErrorCode
import dev.thunderid.android.ThunderIDFetcher
import dev.thunderid.android.ThunderIDHttpResponse
import dev.thunderid.android.TokenResponse
import dev.thunderid.android.User
import dev.thunderid.android.UserProfile
import dev.thunderid.android.VerifiedAttribute
import dev.thunderid.android.token.TokenStore
import dev.thunderid.compose.ThunderIDState
import dev.thunderid.compose.i18n.DefaultStrings
import dev.thunderid.compose.i18n.ThunderIDI18n
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.URI

@OptIn(ExperimentalCoroutinesApi::class)
class VerifyAttributeTest {
    private lateinit var client: ThunderIDClient
    private val api = mockk<ThunderIDClient>()
    private val reported = mutableListOf<String>()
    private val profileReads = mutableListOf<UserProfile?>()
    private var completions = 0
    private var dones = 0

    private val verifiedProfile =
        UserProfile(
            id = "u1",
            attributes = mapOf("email" to "jane@example.com"),
            verifiedAttributes = mapOf("email" to VerifiedAttribute("2026-10-20T10:00:00Z")),
        )

    private val codeComponents =
        listOf(
            FlowComponent(id = "h", type = "TEXT", variant = "HEADING_1", label = "{{ t(verification:self.email.title) }}"),
            FlowComponent(id = "s", type = "TEXT", variant = "HEADING_6", label = "{{ t(verification:self.email.message) }}"),
            FlowComponent(
                id = "block",
                type = "BLOCK",
                components =
                    listOf(
                        FlowComponent(id = "input_email_otp", ref = "otp", type = "OTP_INPUT"),
                        FlowComponent(id = "action_email_verify", type = "ACTION", label = "Verify"),
                        FlowComponent(id = "action_email_resend", type = "RESEND", label = "Resend"),
                    ),
            ),
        )

    private fun codeStep(
        token: String = "ct-1",
        destination: String? = "j***@example.com",
        error: FlowError? = null,
    ) = EmbeddedFlowResponse(
        flowId = "exec-1",
        flowStatus = FlowStatus.INCOMPLETE,
        challengeToken = token,
        error = error,
        failureReason = error?.message?.defaultValue,
        data =
            FlowStepData(
                actions = listOf(FlowAction(ref = "action_email_verify"), FlowAction(ref = "action_email_resend")),
                meta = FlowMeta(components = codeComponents),
                additionalData = destination?.let { mapOf("maskedDestination" to it, "otpLength" to "6") },
            ),
    )

    private val linkStep =
        EmbeddedFlowResponse(
            flowId = "exec-1",
            flowStatus = FlowStatus.INCOMPLETE,
            challengeToken = "ct-1",
            data =
                FlowStepData(
                    meta = FlowMeta(components = listOf(FlowComponent(id = "t", type = "TEXT", label = "Check your inbox"))),
                    additionalData = mapOf("maskedDestination" to "j***@example.com"),
                ),
        )

    private val translations =
        mapOf(
            "i18n" to
                mapOf(
                    "translations" to
                        mapOf(
                            "verification" to
                                mapOf(
                                    "self.email.title" to "Verify your email address",
                                    "self.email.message" to "Enter the verification code sent to your email address.",
                                    "self.code.invalid" to "That code is not right.",
                                ),
                        ),
                ),
        )

    @Before
    fun setUp() = runTest { setUpClient() }

    private suspend fun setUpClient(applicationId: String? = "app-1") {
        val config = ThunderIDConfig(baseUrl = "https://localhost:8090", applicationId = applicationId)
        val storage = InMemoryStorageAdapter()
        TokenStore(storage).save(TokenResponse(accessToken = "user-token", tokenType = "Bearer", expiresIn = 3600))
        // Keep the real configuration getter; its JVM name overlaps the configuration resource getter.
        client =
            ThunderIDClient(
                ThunderIDFetcher { request ->
                    val uri = URI(request.url)
                    val response: Any =
                        when (uri.path) {
                            "/users/me/meta" -> {
                                mapOf("schema" to api.getUserSchema())
                            }

                            "/users/me" -> {
                                api.getUserProfile()
                            }

                            "/flow/meta" -> {
                                val query =
                                    uri.query.split("&").associate { it.substringBefore("=") to it.substringAfter("=") }
                                api.getFlowMeta(query.getValue("id"), query.getValue("language"))
                            }

                            "/flow/execute" -> {
                                val body = JsonParser.parseString(request.body).asJsonObject
                                if (body.has("executionId")) {
                                    val inputs =
                                        body.getAsJsonObject("inputs")?.entrySet()?.associate { it.key to it.value.asString }
                                    api.verifyAttribute(
                                        EmbeddedSignInPayload(
                                            flowId = body["executionId"].asString,
                                            actionId = body["action"]?.asString,
                                            inputs = inputs.orEmpty(),
                                            challengeToken = body["challengeToken"]?.asString,
                                        ),
                                    )
                                } else {
                                    api.verifyAttribute(
                                        body.getAsJsonObject("inputs")["attribute"].asString,
                                        body["handle"].asString,
                                    )
                                }
                            }

                            else -> {
                                error("Unexpected request: ${uri.path}")
                            }
                        }
                    ThunderIDHttpResponse(200, Gson().toJson(response))
                },
            ).apply { initialize(config, storage) }
        coEvery { api.getUserSchema() } returns mapOf("email" to AttributeSchema(type = "email", displayName = "Email"))
        coEvery { api.getFlowMeta(any(), any()) } returns translations
        coEvery { api.getUserProfile() } returns verifiedProfile
    }

    private fun TestScope.controller(
        attribute: String = "email",
        flowHandle: String = "default-self-attribute-verification-flow",
        i18n: ThunderIDI18n = ThunderIDI18n(),
    ) = VerifyAttributeController(
        state = VerifyAttributeState(claimLabel(attribute)),
        thunderState = ThunderIDState(client, i18n, this),
        attribute = attribute,
        flowHandle = flowHandle,
        hasDisplayName = false,
    ).apply {
        onError = { reported += it }
        onComplete = { completions++ }
        onDone = { dones++ }
        onProfileRead = { profileReads += it }
    }

    // ── Starting ────────────────────────────────────────────────────────────

    @Test
    fun `starts once per showing, however often start runs`() =
        runTest {
            coEvery { api.verifyAttribute("email", any()) } returns codeStep()
            val controller = controller()

            controller.start()
            controller.start()

            coVerify(exactly = 1) { api.verifyAttribute("email", "default-self-attribute-verification-flow") }
            assertEquals(VerifyAttributePhase.ACTIVE, controller.state.phase)
        }

    @Test
    fun `starts the flow the app names`() =
        runTest {
            coEvery { api.verifyAttribute("email", any()) } returns codeStep()

            controller(flowHandle = "custom-verification").start()

            coVerify(exactly = 1) { api.verifyAttribute("email", "custom-verification") }
        }

    @Test
    fun `refuses an attribute the schema does not declare as email or mobile, without a request`() =
        runTest {
            coEvery { api.getUserSchema() } returns mapOf("given_name" to AttributeSchema(type = "string"))
            val controller = controller(attribute = "given_name")

            controller.start()

            coVerify(exactly = 0) { api.verifyAttribute(any<String>(), any()) }
            assertEquals(VerifyAttributePhase.FAILED, controller.state.phase)
            assertEquals(DefaultStrings.all["verifyAttribute.error"], controller.state.error)
            assertEquals(1, reported.size)
        }

    @Test
    fun `keeps the step's own heading and subtitle and adds where the code went`() =
        runTest {
            coEvery { api.verifyAttribute("email", any()) } returns codeStep()
            val controller = controller()

            controller.start()

            coVerify { api.getFlowMeta("app-1", "en-US") }
            assertEquals("Verify your email address", controller.state.title)
            assertEquals("Enter the verification code sent to your email address.", controller.state.subtitle)
            assertEquals("Sent to j***@example.com.", controller.state.destinationMessage)
            assertEquals(listOf("block"), controller.state.bodyComponents.map { it.id })
            assertEquals("Email", controller.state.attributeDisplayName)
            assertFalse(controller.state.isAwaitingLink)
        }

    @Test
    fun `still starts when the translations cannot be read, without showing an unresolved label`() =
        runTest {
            coEvery { api.getFlowMeta(any(), any()) } throws IAMException(ThunderIDErrorCode.SERVER_ERROR, "down")
            coEvery { api.verifyAttribute("email", any()) } returns codeStep(destination = null)
            val controller = controller()

            controller.start()

            assertEquals("Verify Email", controller.state.title)
            assertEquals(DefaultStrings.all["verifyAttribute.subheading.generic"], controller.state.subtitle)
            assertNull(controller.state.destinationMessage)
        }

    @Test
    fun `without an application ID it reads no flow metadata, as sign-up, and still starts`() =
        runTest {
            setUpClient(applicationId = null)
            coEvery { api.verifyAttribute("email", any()) } returns codeStep()
            val controller = controller()

            controller.start()

            coVerify(exactly = 0) { api.getFlowMeta(any(), any()) }
            assertEquals(VerifyAttributePhase.ACTIVE, controller.state.phase)
        }

    // ── Submitting ──────────────────────────────────────────────────────────

    @Test
    fun `submits the code with the step's challenge token`() =
        runTest {
            coEvery { api.verifyAttribute("email", any()) } returns codeStep()
            coEvery { api.verifyAttribute(any<EmbeddedSignInPayload>()) } returns codeStep(token = "ct-2")
            val controller = controller()
            controller.start()
            controller.state.setField("otp", "123456")

            controller.submit("action_email_verify", withInputs = true)

            coVerify {
                api.verifyAttribute(EmbeddedSignInPayload("exec-1", "action_email_verify", mapOf("otp" to "123456"), "ct-1"))
            }
        }

    @Test
    fun `a resend submits its own action with no inputs, even with a code partly typed`() =
        runTest {
            coEvery { api.verifyAttribute("email", any()) } returns codeStep()
            coEvery { api.verifyAttribute(any<EmbeddedSignInPayload>()) } returns codeStep(token = "ct-2")
            val controller = controller()
            controller.start()
            controller.state.setField("otp", "12")

            controller.submit("action_email_resend", withInputs = false)

            coVerify {
                api.verifyAttribute(EmbeddedSignInPayload("exec-1", "action_email_resend", emptyMap(), "ct-1"))
            }
        }

    @Test
    fun `only the destination, channel and code length carry to a later step`() =
        runTest {
            val sent =
                codeStep().let {
                    it.copy(
                        data =
                            it.data?.copy(
                                additionalData =
                                    mapOf(
                                        "maskedDestination" to "j***@example.com",
                                        "channel" to "EMAIL",
                                        "otpLength" to "6",
                                        "emailSent" to "true",
                                    ),
                            ),
                    )
                }
            val next =
                codeStep(token = "ct-2", destination = null).let {
                    it.copy(data = it.data?.copy(additionalData = mapOf("stepNote" to "now", "otpLength" to "8")))
                }
            coEvery { api.verifyAttribute("email", any()) } returns sent
            coEvery { api.verifyAttribute(any<EmbeddedSignInPayload>()) } returns next
            val controller = controller()
            controller.start()
            controller.state.setField("otp", "000000")

            controller.submit("action_email_verify", withInputs = true)

            assertEquals(
                mapOf("maskedDestination" to "j***@example.com", "channel" to "EMAIL", "otpLength" to "8", "stepNote" to "now"),
                controller.state.additionalData,
            )
        }

    @Test
    fun `a rejected code keeps the destination and code length of the step that sent the code`() =
        runTest {
            val rejection = FlowError(code = "FET-1010", message = FlowErrorText(defaultValue = "The code is incorrect"))
            coEvery { api.verifyAttribute("email", any()) } returns codeStep()
            coEvery { api.verifyAttribute(any<EmbeddedSignInPayload>()) } returns
                codeStep(token = "ct-2", destination = null, error = rejection)
            val controller = controller()
            controller.start()
            controller.state.setField("otp", "000000")

            controller.submit("action_email_verify", withInputs = true)

            val state = controller.state
            assertEquals(VerifyAttributePhase.ACTIVE, state.phase)
            assertEquals("j***@example.com", state.maskedDestination)
            assertEquals("6", state.additionalData["otpLength"])
            assertEquals("Enter the verification code sent to your email address.", state.subtitle)
            assertEquals("Sent to j***@example.com.", state.destinationMessage)
            assertEquals(DefaultStrings.all["verifyAttribute.error"], state.error)
            assertEquals("", state.fieldValue("otp"))
            assertEquals("ct-2", state.challengeToken)
        }

    @Test
    fun `an ERROR step is a VERIFICATION_FAILED refusal, reported without the server's reason`() =
        runTest {
            coEvery { api.verifyAttribute("email", any()) } returns
                EmbeddedFlowResponse(flowId = "exec-1", flowStatus = FlowStatus.ERROR, failureReason = "Suspended accounts cannot verify")
            val controller = controller()

            controller.start()

            assertEquals(VerifyAttributePhase.FAILED, controller.state.phase)
            assertEquals(DefaultStrings.all["verifyAttribute.error"], controller.state.error)
            assertEquals(listOf("[VERIFICATION_FAILED] The server refused the verification"), reported)
        }

    @Test
    fun `a failed request shows a generic message and is not retried`() =
        runTest {
            coEvery { api.verifyAttribute("email", any()) } throws
                IAMException(ThunderIDErrorCode.VERIFICATION_FAILED, "The server refused the verification")
            val controller = controller()

            controller.start()
            controller.start()

            assertEquals(VerifyAttributePhase.FAILED, controller.state.phase)
            assertEquals(DefaultStrings.all["verifyAttribute.error"], controller.state.error)
            assertEquals(1, reported.size)
            coVerify(exactly = 1) { api.verifyAttribute(any<String>(), any()) }
        }

    @Test
    fun `ignores a second submission while one is in flight`() =
        runTest {
            coEvery { api.verifyAttribute("email", any()) } returns codeStep()
            val controller = controller()
            controller.start()
            controller.state.isLoading = true

            controller.submit("action_email_verify", withInputs = true)

            coVerify(exactly = 0) { api.verifyAttribute(any<EmbeddedSignInPayload>()) }
        }

    @Test
    fun `custom submit and resend ignore overlapping requests and keep the winning challenge token`() =
        runTest {
            for (firstIsSubmit in listOf(true, false)) {
                for (secondIsSubmit in listOf(true, false)) {
                    val response = CompletableDeferred<EmbeddedFlowResponse>()
                    var requests = 0
                    coEvery { api.verifyAttribute("email", any()) } returns codeStep()
                    coEvery { api.verifyAttribute(any<EmbeddedSignInPayload>()) } coAnswers {
                        requests++
                        response.await()
                    }
                    val state = customRender().apply { start() }.state

                    fun act(submit: Boolean) {
                        if (submit) state.submit("action_email_verify") else state.resend("action_email_resend")
                    }

                    act(firstIsSubmit)
                    runCurrent()
                    assertTrue(state.isLoading)
                    act(secondIsSubmit)
                    runCurrent()
                    assertEquals(1, requests)
                    assertEquals("ct-1", state.challengeToken)

                    response.complete(codeStep(token = "ct-2"))
                    advanceUntilIdle()
                    assertEquals("ct-2", state.challengeToken)
                    assertFalse(state.isLoading)
                    assertNull(state.loadingActionId)
                    act(secondIsSubmit)
                    advanceUntilIdle()
                    assertEquals(2, requests)
                }
            }
        }

    @Test
    fun `waits for the schema before starting or refusing`() =
        runTest {
            val schema = CompletableDeferred<Map<String, AttributeSchema>>()
            coEvery { api.getUserSchema() } coAnswers { schema.await() }
            coEvery { api.verifyAttribute("email", any()) } returns codeStep()
            val controller = controller()
            launch { controller.start() }
            runCurrent()

            assertEquals(VerifyAttributePhase.LOADING, controller.state.phase)
            assertTrue(reported.isEmpty())
            coVerify(exactly = 0) { api.verifyAttribute(any<String>(), any()) }
            schema.complete(mapOf("email" to AttributeSchema(type = "email")))
            advanceUntilIdle()

            assertEquals(VerifyAttributePhase.ACTIVE, controller.state.phase)
            coVerify(exactly = 1) { api.verifyAttribute("email", any()) }
        }

    @Test
    fun `a rejected code can be corrected and submitted again through custom content`() =
        runTest {
            val submitted = mutableListOf<EmbeddedSignInPayload>()
            coEvery { api.verifyAttribute("email", any()) } returns codeStep()
            coEvery { api.verifyAttribute(any<EmbeddedSignInPayload>()) } coAnswers {
                submitted += firstArg<EmbeddedSignInPayload>()
                if (submitted.size == 1) {
                    codeStep(error = FlowError(message = FlowErrorText(defaultValue = "Invalid code")))
                } else {
                    EmbeddedFlowResponse(flowId = "exec-1", flowStatus = FlowStatus.COMPLETE)
                }
            }
            val state = customRender().apply { start() }.state
            state.setField("otp", "bad")
            state.submit("action_email_verify")
            advanceUntilIdle()
            assertEquals(VerifyAttributePhase.ACTIVE, state.phase)
            assertFalse(state.isLoading)

            state.setField("otp", "123456")
            state.submit("action_email_verify")
            advanceUntilIdle()

            assertEquals(listOf("bad", "123456"), submitted.map { it.inputs["otp"] })
            assertEquals(VerifyAttributePhase.COMPLETE, state.phase)
            assertNull(state.error)
        }

    @Test
    fun `an untranslatable step error shows the provider's generic translation instead of server text`() =
        runTest {
            val i18n = ThunderIDI18n(bundles = mapOf("en-US" to mapOf("verifyAttribute.error" to "Try again later.")))
            val raw = "POST /flow/execute: private server detail"
            coEvery { api.verifyAttribute("email", any()) } returns
                codeStep(error = FlowError(message = FlowErrorText(key = "unknown.error", defaultValue = raw)))
            val controller = controller(i18n = i18n)

            controller.start()

            assertEquals("Try again later.", controller.state.error)
            assertEquals(VerifyAttributePhase.ACTIVE, controller.state.phase)
        }

    // ── Completing ──────────────────────────────────────────────────────────

    @Test
    fun `the standalone component re-reads the profile after COMPLETE, with no host to do it`() =
        runTest {
            coEvery { api.verifyAttribute("email", any()) } returns codeStep()
            coEvery { api.verifyAttribute(any<EmbeddedSignInPayload>()) } returns
                EmbeddedFlowResponse(flowId = "exec-1", flowStatus = FlowStatus.COMPLETE)
            val controller = controller()
            controller.start()

            controller.submit("action_email_verify", withInputs = true)

            assertEquals(VerifyAttributePhase.COMPLETE, controller.state.phase)
            coVerify(exactly = 1) { api.getUserProfile() }
            assertEquals(listOf(verifiedProfile), profileReads)
            assertEquals(1, completions)
        }

    @Test
    fun `the re-read profile reaches the signed-in user's state without a callback`() =
        runTest {
            coEvery { api.verifyAttribute("email", any()) } returns
                EmbeddedFlowResponse(flowId = "exec-1", flowStatus = FlowStatus.COMPLETE)
            val thunderState = ThunderIDState(client, ThunderIDI18n(), this)
            thunderState.user = User(mapOf("sub" to "u1"))
            val controller = VerifyAttributeController(VerifyAttributeState("Email"), thunderState, "email", "handle", false)

            controller.start()

            coVerify(exactly = 1) { api.getUserProfile() }
            assertEquals("jane@example.com", thunderState.user?.get("email"))
        }

    @Test
    fun `Done re-reads the profile without a callback too`() =
        runTest {
            coEvery { api.verifyAttribute("email", any()) } returns linkStep
            val controller = controller().apply { onDone = null }
            controller.start()

            controller.done()

            coVerify(exactly = 1) { api.getUserProfile() }
        }

    @Test
    fun `COMPLETE still reports when the profile cannot be re-read`() =
        runTest {
            coEvery { api.getUserProfile() } throws IAMException(ThunderIDErrorCode.SERVER_ERROR, "down")
            coEvery { api.verifyAttribute("email", any()) } returns
                EmbeddedFlowResponse(flowId = "exec-1", flowStatus = FlowStatus.COMPLETE)
            val controller = controller()

            controller.start()

            assertEquals(1, completions)
            assertEquals(listOf<UserProfile?>(null), profileReads)
        }

    // ── Link ────────────────────────────────────────────────────────────────

    @Test
    fun `a step with no input or action waits for the link and says where it went`() =
        runTest {
            coEvery { api.verifyAttribute("email", any()) } returns linkStep
            val controller = controller()

            controller.start()

            assertTrue(controller.state.isAwaitingLink)
            assertEquals(
                "We sent a verification link to j***@example.com. Open it, then select Done.",
                controller.state.destinationMessage,
            )
            coVerify(exactly = 0) { api.getUserProfile() }
        }

    @Test
    fun `Done re-reads the profile once and does not poll the flow`() =
        runTest {
            coEvery { api.verifyAttribute("email", any()) } returns linkStep
            val controller = controller()
            controller.start()

            controller.done()

            coVerify(exactly = 1) { api.getUserProfile() }
            coVerify(exactly = 1) { api.verifyAttribute(any<String>(), any()) }
            coVerify(exactly = 0) { api.verifyAttribute(any<EmbeddedSignInPayload>()) }
            assertEquals(listOf(verifiedProfile), profileReads)
            assertEquals(1, dones)
        }

    @Test
    fun `Done does nothing on a step that is not waiting for a link`() =
        runTest {
            coEvery { api.verifyAttribute("email", any()) } returns codeStep()
            val controller = controller()
            controller.start()

            controller.done()

            coVerify(exactly = 0) { api.getUserProfile() }
            assertEquals(0, dones)
        }

    // ── Custom render ───────────────────────────────────────────────────────

    // Drives the verification only through the state a `content` lambda gets, on either variant.
    private fun TestScope.customRender(onCancel: (() -> Unit)? = null) = controller().apply { bind(this@customRender, onCancel) }

    @Test
    fun `a custom render gets the step's fields, where the code went, and submits and resends through the state`() =
        runTest {
            coEvery { api.verifyAttribute("email", any()) } returns codeStep()
            coEvery { api.verifyAttribute(any<EmbeddedSignInPayload>()) } returns codeStep(token = "ct-2")
            val state = customRender().apply { start() }.state

            assertEquals(VerifyAttributePhase.ACTIVE, state.phase)
            assertEquals(mapOf("otp" to ""), state.fields())
            assertEquals("Verify your email address", state.title)
            assertEquals("j***@example.com", state.maskedDestination)
            assertEquals("Sent to j***@example.com.", state.destinationMessage)
            state.setField("otp", "123456")
            assertEquals("123456", state.fieldValue("otp"))

            state.submit("action_email_verify")
            advanceUntilIdle()
            state.setField("otp", "12")
            state.resend(state.resendActionId!!)
            advanceUntilIdle()

            coVerify(exactly = 1) {
                api.verifyAttribute(EmbeddedSignInPayload("exec-1", "action_email_verify", mapOf("otp" to "123456"), "ct-1"))
            }
            coVerify(exactly = 1) {
                api.verifyAttribute(EmbeddedSignInPayload("exec-1", "action_email_resend", emptyMap(), "ct-2"))
            }
        }

    @Test
    fun `a custom render shows loading while a step is submitted and the translated error of a rejected code`() =
        runTest {
            var loadingInFlight = false
            lateinit var state: VerifyAttributeState
            coEvery { api.verifyAttribute("email", any()) } returns codeStep()
            coEvery { api.verifyAttribute(any<EmbeddedSignInPayload>()) } coAnswers {
                loadingInFlight = state.isLoading
                val rejection = FlowErrorText(key = "verification.self.code.invalid", defaultValue = "Invalid OTP")
                codeStep(destination = null, error = FlowError(code = "FET-1010", message = rejection))
            }
            state = customRender().apply { start() }.state

            state.submit("action_email_verify")
            advanceUntilIdle()

            assertTrue(loadingInFlight)
            assertFalse(state.isLoading)
            assertEquals("That code is not right.", state.error)
            assertEquals("j***@example.com", state.maskedDestination)
        }

    @Test
    fun `a custom render gets the completed state after the profile is re-read, then onComplete`() =
        runTest {
            coEvery { api.verifyAttribute("email", any()) } returns codeStep()
            coEvery { api.verifyAttribute(any<EmbeddedSignInPayload>()) } returns
                EmbeddedFlowResponse(flowId = "exec-1", flowStatus = FlowStatus.COMPLETE)
            val controller = customRender()
            var readsAtComplete = -1
            controller.onComplete = { readsAtComplete = profileReads.size }
            controller.start()
            controller.state.setField("otp", "123456")

            controller.state.submit("action_email_verify")
            advanceUntilIdle()

            assertEquals(VerifyAttributePhase.COMPLETE, controller.state.phase)
            assertEquals("Email verified.", controller.state.successMessage)
            assertEquals(1, readsAtComplete)
            assertEquals(emptyMap<String, String>(), controller.state.fields())
            assertNull(controller.state.resendActionId)
        }

    @Test
    fun `a custom render cancels through the state only when the app passes onCancel`() =
        runTest {
            coEvery { api.verifyAttribute("email", any()) } returns codeStep()
            var cancels = 0

            assertNull(customRender().state.onCancel)
            customRender(onCancel = { cancels++ }).state.onCancel?.invoke()

            assertEquals(1, cancels)
        }

    @Test
    fun `a custom render's Done re-reads the profile once for a link`() =
        runTest {
            coEvery { api.verifyAttribute("email", any()) } returns linkStep
            val state = customRender().apply { start() }.state

            assertTrue(state.isAwaitingLink)
            state.done()
            advanceUntilIdle()

            coVerify(exactly = 1) { api.getUserProfile() }
            assertEquals(1, dones)
        }

    // ── Headings ────────────────────────────────────────────────────────────

    @Test
    fun `extractHeadings takes the first two headings, nested ones too, and keeps the rest`() {
        val (heading, subheading, rest) =
            extractHeadings(
                listOf(
                    FlowComponent(id = "body", type = "TEXT", label = "Body"),
                    FlowComponent(
                        id = "block",
                        type = "BLOCK",
                        components =
                            listOf(
                                FlowComponent(id = "h1", type = "TEXT", variant = "HEADING_1"),
                                FlowComponent(id = "h2", type = "TEXT", variant = "HEADING_6"),
                                FlowComponent(id = "h3", type = "TEXT", variant = "HEADING_6"),
                            ),
                    ),
                ),
            )

        assertEquals("h1", heading?.id)
        assertEquals("h2", subheading?.id)
        assertEquals(listOf("body", "block"), rest.map { it.id })
        assertEquals(listOf("h3"), rest[1].components?.map { it.id })
    }
}
