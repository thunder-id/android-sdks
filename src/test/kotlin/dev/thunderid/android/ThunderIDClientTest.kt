// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android

import com.google.gson.JsonParser
import dev.thunderid.android.api.account.BASE
import dev.thunderid.android.api.account.form
import dev.thunderid.android.api.account.jwt
import dev.thunderid.android.api.auth.FlowExecutionClient
import dev.thunderid.android.backend.FakeBackend
import dev.thunderid.android.backend.response
import dev.thunderid.android.http.HttpClient
import dev.thunderid.android.token.TokenStore
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ThunderIDClientTest {
    private lateinit var client: ThunderIDClient
    private lateinit var storage: InMemoryStorageAdapter

    @Before
    fun setUp() {
        client = ThunderIDClient()
        storage = InMemoryStorageAdapter()
    }

    // Initialization

    @Test
    fun `initialize succeeds with valid https config`() =
        runTest {
            val config = ThunderIDConfig(baseUrl = "https://localhost:8090", clientId = "test")
            assertTrue(client.initialize(config, storage))
        }

    @Test(expected = IAMException::class)
    fun `initialize rejects http baseUrl`() =
        runTest {
            val config = ThunderIDConfig(baseUrl = "http://localhost:8090", clientId = "test")
            client.initialize(config, storage)
        }

    @Test(expected = IAMException::class)
    fun `initialize throws when called twice`() =
        runTest {
            val config = ThunderIDConfig(baseUrl = "https://localhost:8090", clientId = "test")
            client.initialize(config, storage)
            client.initialize(config, storage) // should throw ALREADY_INITIALIZED
        }

    @Test
    fun `operations before init throw SDK_NOT_INITIALIZED`() =
        runTest {
            try {
                client.isSignedIn()
                fail("Expected IAMException")
            } catch (e: IAMException) {
                assertEquals(ThunderIDErrorCode.SDK_NOT_INITIALIZED, e.code)
            }
        }

    @Test
    fun `getConfiguration returns config after init`() =
        runTest {
            val config =
                ThunderIDConfig(
                    baseUrl = "https://localhost:8090",
                    clientId = "my-client",
                    scopes = listOf("openid", "profile"),
                )
            client.initialize(config, storage)
            val retrieved = client.getConfiguration()
            assertEquals("https://localhost:8090", retrieved.baseUrl)
            assertEquals("my-client", retrieved.clientId)
            assertEquals(listOf("openid", "profile"), retrieved.scopes)
        }

    @Test(expected = IAMException::class)
    fun `initialize rejects attestationEnabled without a token provider`() =
        runTest {
            val config = ThunderIDConfig(baseUrl = "https://localhost:8090", attestationEnabled = true)
            client.initialize(config, storage)
        }

    @Test
    fun `initialize accepts attestationEnabled with a token provider`() =
        runTest {
            val config =
                ThunderIDConfig(
                    baseUrl = "https://localhost:8090",
                    attestationEnabled = true,
                    attestationTokenProvider = { "test-token" },
                )
            assertTrue(client.initialize(config, storage))
        }

    // PKCE

    @Test
    fun `PKCEManager generates S256 challenge`() {
        val manager =
            dev.thunderid.android.api.auth
                .PKCEManager()
        val (verifier, challenge) = manager.generate()
        assertTrue(verifier.isNotEmpty())
        assertTrue(challenge.isNotEmpty())
        assertNotEquals(verifier, challenge)
        assertTrue(verifier.length >= 43)
        assertFalse(challenge.contains("+"))
        assertFalse(challenge.contains("/"))
        assertFalse(challenge.contains("="))
    }

    @Test
    fun `PKCEManager clears verifier`() {
        val manager =
            dev.thunderid.android.api.auth
                .PKCEManager()
        manager.generate()
        assertNotNull(manager.codeVerifier)
        manager.clearVerifier()
        assertNull(manager.codeVerifier)
    }

    // Token Store

    @Test
    fun `TokenStore saves and retrieves tokens`() {
        val store =
            dev.thunderid.android.token
                .TokenStore(storage)
        val response =
            TokenResponse(
                accessToken = "access123",
                tokenType = "Bearer",
                expiresIn = 3600,
                refreshToken = "refresh456",
                idToken = "id789",
            )
        store.save(response)
        assertEquals("access123", store.accessToken())
        assertEquals("refresh456", store.refreshToken())
        assertEquals("id789", store.idToken())
    }

    @Test
    fun `TokenStore isNearExpiry when expires in 30s`() {
        val store =
            dev.thunderid.android.token
                .TokenStore(storage)
        store.save(TokenResponse(accessToken = "tok", tokenType = "Bearer", expiresIn = 30))
        assertTrue(store.isNearExpiry())
    }

    @Test
    fun `TokenStore not near expiry when expires in 3600s`() {
        val store =
            dev.thunderid.android.token
                .TokenStore(storage)
        store.save(TokenResponse(accessToken = "tok", tokenType = "Bearer", expiresIn = 3600))
        assertFalse(store.isNearExpiry())
    }

    @Test
    fun `TokenStore clear removes all tokens`() {
        val store =
            dev.thunderid.android.token
                .TokenStore(storage)
        store.save(TokenResponse(accessToken = "tok", tokenType = "Bearer"))
        store.clear()
        assertNull(store.accessToken())
    }

    @Test
    fun `TokenStore replace keeps the device secret and flow sessions, and clear removes them`() {
        val store =
            dev.thunderid.android.token
                .TokenStore(storage)
        store.save(TokenResponse(accessToken = "tok", tokenType = "Bearer", refreshToken = "r1", deviceSecret = "ds-1"))
        store.updateFlowSessions(listOf(FlowSessionData("f-1", "h-1")), null)

        store.replace(TokenResponse(accessToken = "tok2", tokenType = "Bearer"))

        assertNull(store.refreshToken())
        assertEquals("ds-1", store.deviceSecret())
        assertEquals(listOf(FlowSessionData("f-1", "h-1")), store.flowSessions())

        store.clear()

        assertNull(store.deviceSecret())
        assertTrue(store.flowSessions().isEmpty())
    }

    @Test
    fun `TokenStore merges flow sessions by flow, drops cleared ones and skips expired ones`() {
        val store =
            dev.thunderid.android.token
                .TokenStore(storage)
        store.updateFlowSessions(listOf(FlowSessionData("f-1", "h-1", 60L), FlowSessionData("f-2", "h-2")), null, now = 1_000L)
        // A step that names neither leaves the kept sessions as they are.
        store.updateFlowSessions(null, null, now = 1_000L)
        store.updateFlowSessions(emptyList(), emptyList(), now = 1_000L)
        assertEquals(listOf(FlowSessionData("f-1", "h-1"), FlowSessionData("f-2", "h-2")), store.flowSessions(now = 1_000L))

        store.updateFlowSessions(listOf(FlowSessionData("f-1", "h-1b", 60L)), listOf("f-2"), now = 2_000L)

        assertEquals(listOf(FlowSessionData("f-1", "h-1b")), store.flowSessions(now = 61_999L))
        assertTrue(store.flowSessions(now = 62_000L).isEmpty())

        store.updateFlowSessions(null, listOf("f-1"))
        assertNull(storage.retrieve("thunder.flow_sessions"))
    }

    @Test
    fun `TokenStore ignores unreadable flow sessions and incomplete ones`() {
        val store =
            dev.thunderid.android.token
                .TokenStore(storage)
        storage.store("thunder.flow_sessions", "not json")
        assertTrue(store.flowSessions().isEmpty())

        val sessions = listOf(FlowSessionData("f-1", null), FlowSessionData(null, "h-2"), FlowSessionData("f-3", "h-3"))
        store.updateFlowSessions(sessions, null)
        assertEquals(listOf(FlowSessionData("f-3", "h-3")), store.flowSessions())
    }

    // isLoading

    @Test
    fun `isLoading defaults false after init`() =
        runTest {
            client.initialize(ThunderIDConfig(baseUrl = "https://localhost:8090", clientId = "test"), storage)
            assertFalse(client.isLoading())
        }

    // clearSession

    @Test
    fun `clearSession means isSignedIn returns false`() =
        runTest {
            client.initialize(ThunderIDConfig(baseUrl = "https://localhost:8090", clientId = "test"), storage)
            client.clearSession()
            assertFalse(client.isSignedIn())
        }

    // Error codes

    @Test
    fun `ThunderIDErrorCode fromValue round-trips`() {
        val code = ThunderIDErrorCode.fromValue("AUTHENTICATION_FAILED")
        assertEquals(ThunderIDErrorCode.AUTHENTICATION_FAILED, code)
    }

    @Test
    fun `ThunderIDErrorCode fromValue returns UNKNOWN_ERROR for unknown`() {
        assertEquals(ThunderIDErrorCode.UNKNOWN_ERROR, ThunderIDErrorCode.fromValue("NOT_A_REAL_CODE"))
    }

    @Test
    fun `IAMException message includes code`() {
        val ex = IAMException(ThunderIDErrorCode.NETWORK_ERROR, "connection refused")
        assertTrue(ex.message!!.contains("NETWORK_ERROR"))
    }

    // Self-service credential change

    private class RecordingFetcher(
        var response: ThunderIDHttpResponse = ThunderIDHttpResponse(204, ""),
    ) : ThunderIDFetcher {
        val requests = mutableListOf<ThunderIDHttpRequest>()

        override suspend fun fetch(request: ThunderIDHttpRequest): ThunderIDHttpResponse {
            requests += request
            return response
        }
    }

    private suspend fun signedInClient(fetcher: ThunderIDFetcher): ThunderIDClient {
        dev.thunderid.android.token
            .TokenStore(storage)
            .save(TokenResponse(accessToken = "user-token", tokenType = "Bearer", expiresIn = 3600))
        return ThunderIDClient(fetcher).apply {
            initialize(ThunderIDConfig(baseUrl = "https://localhost:8090", clientId = "client"), storage)
        }
    }

    private fun bodyOf(request: ThunderIDHttpRequest): JSONObject = JSONObject(request.body!!)

    @Test
    fun `updateUserCredentials sends the name, current value and new value`() =
        runTest {
            val fetcher = RecordingFetcher()
            signedInClient(fetcher).updateUserCredentials(attribute = "pin", newValue = "2468", currentValue = "1357")

            val request = fetcher.requests.single()
            assertEquals("POST", request.method)
            assertEquals("https://localhost:8090/users/me/update-credentials", request.url)
            val body = bodyOf(request)
            assertEquals(setOf("name", "currentValue", "newValue"), body.keySet())
            assertEquals("pin", body.getString("name"))
            assertEquals("1357", body.getString("currentValue"))
            assertEquals("2468", body.getString("newValue"))
        }

    @Test
    fun `updateUserCredentials leaves out an empty current value`() =
        runTest {
            val fetcher = RecordingFetcher()
            val client = signedInClient(fetcher)

            client.updateUserCredentials(newValue = "n3wValue!")
            client.updateUserCredentials(newValue = "n3wValue!", currentValue = "")

            fetcher.requests.forEach { request ->
                val body = bodyOf(request)
                assertEquals(setOf("name", "newValue"), body.keySet())
                assertEquals("password", body.getString("name"))
                assertFalse(body.has("attributes"))
            }
        }

    @Test
    fun `updateUserCredentials refuses a blank name or new value without sending anything`() =
        runTest {
            val fetcher = RecordingFetcher()
            val client = signedInClient(fetcher)

            for ((attribute, newValue) in listOf(" " to "n3wValue!", "password" to "")) {
                try {
                    client.updateUserCredentials(attribute = attribute, newValue = newValue, currentValue = "0ldValue!")
                    fail("Expected IAMException")
                } catch (e: IAMException) {
                    assertEquals(ThunderIDErrorCode.INVALID_INPUT, e.code)
                }
            }
            assertTrue(fetcher.requests.isEmpty())
        }

    @Test
    fun `updateUserCredentials carries the server's code and message`() =
        runTest {
            val body =
                """
                {"code":"USR-1048",
                 "message":{"key":"error.userservice.invalid_current_credential","defaultValue":"Invalid current credential"},
                 "description":{"defaultValue":"The current value does not match the credential"}}
                """.trimIndent()
            val client = signedInClient(RecordingFetcher(ThunderIDHttpResponse(403, body)))

            try {
                client.updateUserCredentials(newValue = "n3wValue!", currentValue = "wrong")
                fail("Expected IAMException")
            } catch (e: IAMException) {
                assertEquals(ThunderIDErrorCode.FORBIDDEN, e.code)
                assertEquals("USR-1048", e.serverError?.code)
                assertEquals("[FORBIDDEN] Invalid current credential", e.message)
                assertNull(e.authenticationChallenge)
            }
        }

    private val stepUpBody =
        """
        {"code":"AUTH-4011",
         "message":{"defaultValue":"Recent authentication required"},
         "description":{"defaultValue":"Authenticate again","params":{"maxAge":"300"}}}
        """.trimIndent()

    private val stepUpHeader =
        "Bearer error=\"insufficient_user_authentication\", " +
            "error_description=\"A more recent authentication is required\", max_age=\"120\""

    private suspend fun stepUpError(call: suspend () -> Unit): IAMException {
        try {
            call()
        } catch (e: IAMException) {
            return e
        }
        throw AssertionError("Expected IAMException")
    }

    @Test
    fun `a credential or profile change refused for an old sign-in is STEP_UP_REQUIRED with the header's max age`() =
        runTest {
            val fetcher = RecordingFetcher(ThunderIDHttpResponse(401, stepUpBody, mapOf("www-authenticate" to stepUpHeader)))
            val client = signedInClient(fetcher)
            val calls =
                listOf<suspend () -> Unit>(
                    { client.updateUserCredentials(newValue = "n3wValue!") },
                    { client.updateUserProfile(mapOf("email" to "new@example.com")) },
                )

            for (call in calls) {
                val error = stepUpError(call)
                assertEquals(ThunderIDErrorCode.STEP_UP_REQUIRED, error.code)
                assertEquals("[STEP_UP_REQUIRED] Recent authentication required", error.message)
                assertEquals(
                    AuthenticationChallenge(
                        error = "insufficient_user_authentication",
                        errorDescription = "A more recent authentication is required",
                        maxAge = 120,
                    ),
                    error.authenticationChallenge,
                )
            }
            // One request each: the call does not sign in again, refresh or retry.
            assertEquals(listOf("POST", "PUT"), fetcher.requests.map { it.method })
        }

    @Test
    fun `without the header the body's AUTH-4011 makes it STEP_UP_REQUIRED with the body's max age`() =
        runTest {
            val fetcher = RecordingFetcher(ThunderIDHttpResponse(401, stepUpBody))
            val client = signedInClient(fetcher)
            val calls =
                listOf<suspend () -> Unit>(
                    { client.updateUserCredentials(newValue = "n3wValue!") },
                    { client.updateUserProfile(mapOf("mobile" to "+94771111111")) },
                )

            for (call in calls) {
                val error = stepUpError(call)
                assertEquals(ThunderIDErrorCode.STEP_UP_REQUIRED, error.code)
                assertEquals(
                    AuthenticationChallenge(error = "insufficient_user_authentication", maxAge = 300),
                    error.authenticationChallenge,
                )
            }
            assertEquals(2, fetcher.requests.size)
        }

    @Test
    fun `a 401 that asks for no recent sign-in stays AUTHENTICATION_FAILED`() =
        runTest {
            val body = """{"code":"AUTH-4010","message":{"defaultValue":"Unauthorized"}}"""
            val header = "Bearer error=\"invalid_token\""
            val client = signedInClient(RecordingFetcher(ThunderIDHttpResponse(401, body, mapOf("WWW-Authenticate" to header))))

            val error = stepUpError { client.updateUserProfile(mapOf("email" to "new@example.com")) }

            assertEquals(ThunderIDErrorCode.AUTHENTICATION_FAILED, error.code)
            assertNull(error.authenticationChallenge)
        }

    @Test
    fun `sign-in options send max_age and extra parameters without overriding the SDK's own`() =
        runTest {
            client.initialize(
                ThunderIDConfig(baseUrl = "https://localhost:8090", clientId = "app", afterSignInUrl = "com.example.app://callback"),
                storage,
            )

            val url =
                client.buildSignInUrl(
                    SignInOptions(maxAge = 300, extra = mapOf("id_token_hint" to "hint", "client_id" to "other", "ui_locales" to "fr")),
                )

            val query =
                url.substringAfter('?').split('&').associate {
                    it.substringBefore('=') to java.net.URLDecoder.decode(it.substringAfter('='), "UTF-8")
                }
            assertEquals("300", query["max_age"])
            assertEquals("hint", query["id_token_hint"])
            assertEquals("fr", query["ui_locales"])
            assertEquals("app", query["client_id"])
        }

    @Test
    fun `flow submit body uses action field`() {
        val flowClient = FlowExecutionClient(HttpClient(baseUrl = "https://localhost:8090"))

        val body = flowClient.submitBody("flow-123", "basic_auth", null)

        assertEquals("flow-123", body["executionId"])
        assertEquals("basic_auth", body["action"])
        assertFalse(body.containsKey("actionId"))
    }

    // Embedded sign-in session

    private val fake = FakeBackend()
    private val embedded = ThunderIDClient(fake)
    private val embeddedConfig = ThunderIDConfig(baseUrl = BASE, clientId = "app", applicationId = "app-id")
    private val issued = jwt("sub" to "u1", "client_id" to "app")
    private val assertion = jwt("sub" to "u1", "username" to "alice")

    private fun completeFlow(sessionData: String = "") {
        fake.on(
            "POST",
            "$BASE/flow/execute",
            response(200, """{"flowStatus":"COMPLETE","assertion":"$assertion"$sessionData}"""),
        )
    }

    private fun issueToken() {
        fake.on("POST", "$BASE/oauth2/token", response(200, """{"access_token":"$issued","token_type":"Bearer","expires_in":3600}"""))
    }

    private suspend fun assertRefused(
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

    @Test
    fun `a completed embedded sign-in keeps the token its assertion is exchanged for`() =
        runTest {
            val storage = InMemoryStorageAdapter()
            val store = TokenStore(storage)
            embedded.initialize(embeddedConfig, storage)
            store.save(TokenResponse(accessToken = "old", tokenType = "Bearer", refreshToken = "old-refresh"))
            completeFlow()
            issueToken()

            embedded.signIn(EmbeddedSignInPayload(), EmbeddedFlowRequestConfig("app-id"))

            val exchange = form(fake.to("$BASE/oauth2/token").single().body)
            assertEquals(
                mapOf(
                    "grant_type" to "urn:ietf:params:oauth:grant-type:token-exchange",
                    "client_id" to "app",
                    "subject_token" to assertion,
                    "subject_token_type" to "urn:ietf:params:oauth:token-type:jwt",
                ),
                exchange,
            )
            assertEquals(issued, store.accessToken())
            assertEquals(assertion, store.idToken())
            assertNull(store.refreshToken())
            assertFalse(store.isNearExpiry())
        }

    @Test
    fun `a completed embedded sign-up keeps the exchanged token too`() =
        runTest {
            val storage = InMemoryStorageAdapter()
            embedded.initialize(embeddedConfig, storage)
            completeFlow()
            issueToken()

            embedded.signUp()

            assertEquals(issued, TokenStore(storage).accessToken())
        }

    @Test
    fun `an application without the token exchange grant gets no session and the step's handles are dropped`() =
        runTest {
            val storage = InMemoryStorageAdapter()
            val store = TokenStore(storage)
            embedded.initialize(embeddedConfig, storage)
            store.save(TokenResponse(accessToken = "old", tokenType = "Bearer"))
            store.updateFlowSessions(listOf(FlowSessionData("f-0", "h-0")), null)
            completeFlow(""","sessionData":[{"flowIdentifier":"f-1","sessionHandle":"h-1"}],"clearedSessions":["f-0"]""")
            fake.on("POST", "$BASE/oauth2/token", response(400, """{"error":"unauthorized_client"}"""))

            val error =
                assertRefused(ThunderIDErrorCode.INVALID_CONFIGURATION) {
                    embedded.signIn(EmbeddedSignInPayload(), EmbeddedFlowRequestConfig("app-id"))
                }

            assertEquals("unauthorized_client", error.serverError?.code)
            assertTrue(error.message!!.contains("urn:ietf:params:oauth:grant-type:token-exchange"))
            assertEquals("old", store.accessToken())
            assertTrue(store.flowSessions().isEmpty())
        }

    @Test
    fun `another refusal of the exchange is reported as the server's error`() =
        runTest {
            val storage = InMemoryStorageAdapter()
            embedded.initialize(embeddedConfig, storage)
            completeFlow()
            fake.on("POST", "$BASE/oauth2/token", response(400, """{"error":"invalid_request"}"""))

            val error =
                assertRefused(ThunderIDErrorCode.INVALID_INPUT) {
                    embedded.signIn(EmbeddedSignInPayload(), EmbeddedFlowRequestConfig("app-id"))
                }

            assertEquals("invalid_request", error.serverError?.code)
            assertNull(TokenStore(storage).accessToken())
        }

    @Test
    fun `an embedded flow does not start without a clientId`() =
        runTest {
            embedded.initialize(embeddedConfig.copy(clientId = null), InMemoryStorageAdapter())

            assertRefused(ThunderIDErrorCode.INVALID_CONFIGURATION) {
                embedded.signIn(EmbeddedSignInPayload(), EmbeddedFlowRequestConfig("app-id"))
            }
            assertRefused(ThunderIDErrorCode.INVALID_CONFIGURATION) { embedded.signUp() }
            assertTrue(fake.requests.isEmpty())
        }

    @Test
    fun `an embedded flow step is not submitted without a clientId`() =
        runTest {
            embedded.initialize(embeddedConfig.copy(clientId = null), InMemoryStorageAdapter())
            val step = EmbeddedSignInPayload(flowId = "e-1", inputs = mapOf("username" to "alice"))

            assertRefused(ThunderIDErrorCode.INVALID_CONFIGURATION) {
                embedded.signIn(step, EmbeddedFlowRequestConfig("app-id"))
            }
            assertRefused(ThunderIDErrorCode.INVALID_CONFIGURATION) { embedded.signUp(step) }
            assertTrue(fake.requests.isEmpty())
        }

    @Test
    fun `a flow step that does not complete stores no token`() =
        runTest {
            val storage = InMemoryStorageAdapter()
            embedded.initialize(embeddedConfig, storage)
            fake.on("POST", "$BASE/flow/execute", response(200, """{"executionId":"e-1","flowStatus":"INCOMPLETE"}"""))

            embedded.signIn(EmbeddedSignInPayload(), EmbeddedFlowRequestConfig("app-id"))

            assertTrue(fake.to("$BASE/oauth2/token").isEmpty())
            assertNull(TokenStore(storage).accessToken())
            assertFalse(JsonParser.parseString(fake.requests.single().body).asJsonObject.has("ouId"))
        }

    @Test
    fun `a completed sign-in keeps the step's session handles and the device secret`() =
        runTest {
            val storage = InMemoryStorageAdapter()
            val store = TokenStore(storage)
            embedded.initialize(embeddedConfig, storage)
            store.save(TokenResponse(accessToken = "old", tokenType = "Bearer", deviceSecret = "ds-1"))
            completeFlow(""","sessionData":[{"flowIdentifier":"f-1","sessionHandle":"h-1"}]""")
            issueToken()

            embedded.signIn(EmbeddedSignInPayload(), EmbeddedFlowRequestConfig("app-id"))

            assertEquals(listOf(FlowSessionData("f-1", "h-1")), store.flowSessions())
            assertEquals("ds-1", store.deviceSecret())
        }

    @Test
    fun `a flow that completes without an assertion is not exchanged`() =
        runTest {
            val storage = InMemoryStorageAdapter()
            embedded.initialize(embeddedConfig, storage)
            fake.on("POST", "$BASE/flow/execute", response(200, """{"flowStatus":"COMPLETE"}"""))

            embedded.signUp()

            assertTrue(fake.to("$BASE/oauth2/token").isEmpty())
            assertNull(TokenStore(storage).accessToken())
        }
}
