// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.backend

import com.google.gson.JsonParser
import dev.thunderid.android.EmbeddedFlowRequestConfig
import dev.thunderid.android.EmbeddedSignInPayload
import dev.thunderid.android.IAMException
import dev.thunderid.android.InMemoryStorageAdapter
import dev.thunderid.android.ThunderIDBackendBypass
import dev.thunderid.android.ThunderIDClient
import dev.thunderid.android.ThunderIDErrorCode
import dev.thunderid.android.ThunderIDHttpRequest
import dev.thunderid.android.ThunderIDHttpResponse
import dev.thunderid.android.TokenExchangeRequestConfig
import dev.thunderid.android.token.TokenStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BackendModeTest {
    private val fake = FakeBackend()
    private val storage = InMemoryStorageAdapter()
    private val client = ThunderIDClient(fake)

    private fun get(url: String) = ThunderIDHttpRequest("GET", url, mapOf("Accept" to "application/json"), null)

    private fun storedToken(): String? = TokenStore(storage).accessToken()

    /** Collects [ThunderIDClient.sessionEnded] for the rest of the test. */
    private fun TestScope.sessionEndings(): MutableList<Unit> {
        val ended = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { client.sessionEnded.collect { ended += it } }
        return ended
    }

    private suspend inline fun assertFails(
        code: ThunderIDErrorCode,
        block: () -> Unit,
    ) {
        try {
            block()
            fail("Expected IAMException $code")
        } catch (e: IAMException) {
            assertEquals(code, e.code)
        }
    }

    // Sign-in handoff

    @Test
    fun `buildSignInUrl opens the backend sign-in endpoint with the redirect URI and an S256 challenge`() =
        runTest {
            client.initialize(backendConfig(), storage)

            val url = client.buildSignInUrl()

            assertTrue(url.startsWith("$BACKEND/api/auth/signin?"))
            val query = queryOf(url)
            assertEquals(REDIRECT_URI, query["redirect_uri"])
            assertEquals("S256", query["code_challenge_method"])
            assertTrue(query["code_challenge"]!!.isNotEmpty())
            assertFalse(query.containsKey("client_id"))
            assertTrue(fake.requests.isEmpty())
        }

    @Test
    fun `the session code is exchanged with the verifier and the handle is stored`() =
        runTest {
            fake.on("POST", SESSION, response(200, """{"handle":"h-1","isSignedIn":true,"user":{"sub":"u1"}}"""))
            client.initialize(backendConfig(), storage)
            val challenge = queryOf(client.buildSignInUrl())["code_challenge"]!!

            val user = client.handleRedirectCallback("$REDIRECT_URI?session_code=one-time")

            val exchange = fake.to(SESSION).single()
            assertEquals("POST", exchange.method)
            val body = JsonParser.parseString(exchange.body).asJsonObject
            assertEquals("one-time", body.get("session_code").asString)
            assertEquals(challenge, challengeOf(body.get("code_verifier").asString))
            assertNull(exchange.headers[SESSION_HANDLE_HEADER])
            assertEquals("h-1", storage.retrieve(HANDLE_KEY))
            assertEquals("u1", user.sub)
            assertTrue(client.isSignedIn())
            assertEquals("u1", client.getUser().sub)
            assertEquals(1, fake.requests.size)
        }

    @Test
    fun `every later backend call carries the handle and no token`() =
        runTest {
            signIn(client, fake, storage)
            fake.on("GET", "$PROXY/orders", response(200, "[]"))

            val result = client.httpRequest(get("$API/orders"))

            assertEquals(200, result.statusCode)
            val proxied = fake.to("$PROXY/orders").single()
            assertEquals("h-1", proxied.headers[SESSION_HANDLE_HEADER])
            assertNull(proxied.headers["Authorization"])
        }

    @Test
    fun `the SDK's own calls under baseUrl go through the proxy with the handle`() =
        runTest {
            signIn(client, fake, storage)
            fake.on("GET", "$PROXY/users/me", response(200, """{"id":"u1"}"""))

            assertEquals("u1", client.getUserProfile().id)
            val proxied = fake.to("$PROXY/users/me").single()
            assertEquals("h-1", proxied.headers[SESSION_HANDLE_HEADER])
            assertNull(proxied.headers["Authorization"])
        }

    @Test
    fun `a callback with an error, without a code, or for a sign-in this client did not start is refused`() =
        runTest {
            client.initialize(backendConfig(), storage)

            assertFails(ThunderIDErrorCode.INVALID_GRANT) { client.handleRedirectCallback("$REDIRECT_URI?session_code=x") }
            client.buildSignInUrl()
            assertFails(ThunderIDErrorCode.INVALID_GRANT) { client.handleRedirectCallback("$REDIRECT_URI?state=x") }
            assertFails(ThunderIDErrorCode.AUTHENTICATION_FAILED) {
                client.handleRedirectCallback("$REDIRECT_URI?error=access_denied")
            }
            assertTrue(fake.requests.isEmpty())
            assertNull(storage.retrieve(HANDLE_KEY))
        }

    @Test
    fun `isLoading stays true until the first session read settles`() =
        runTest {
            storage.store(HANDLE_KEY, "h-1")
            val gate = CompletableDeferred<ThunderIDHttpResponse>()
            fake.onSuspending("GET", SESSION) { gate.await() }

            val init = launch { client.initialize(backendConfig(), storage) }
            runCurrent()
            assertTrue(client.isLoading())

            gate.complete(response(200, """{"isSignedIn":true,"user":{"sub":"u1"}}"""))
            init.join()

            assertFalse(client.isLoading())
            assertTrue(client.isSignedIn())
            assertEquals("h-1", fake.to(SESSION).single().headers[SESSION_HANDLE_HEADER])
        }

    // 401 handling

    @Test
    fun `session_expired refreshes once and retries`() =
        runTest {
            signIn(client, fake, storage)
            fake.on("GET", "$PROXY/orders", backendError("session_expired"), response(200, "[]"))
            fake.on("POST", REFRESH, response(204))

            val result = client.httpRequest(get("$API/orders"))

            assertEquals(200, result.statusCode)
            assertEquals(2, fake.to("$PROXY/orders").size)
            assertEquals("h-1", fake.to(REFRESH).single().headers[SESSION_HANDLE_HEADER])
        }

    @Test
    fun `a failed refresh signs out and returns the 401`() =
        runTest {
            val ended = sessionEndings()
            signIn(client, fake, storage)
            fake.on("GET", "$PROXY/orders", backendError("session_expired"))
            fake.on("POST", REFRESH, backendError("login_required"))

            val result = client.httpRequest(get("$API/orders"))

            assertEquals(401, result.statusCode)
            assertEquals(1, fake.to("$PROXY/orders").size)
            assertFalse(client.isSignedIn())
            assertNull(storage.retrieve(HANDLE_KEY))
            assertEquals(1, ended.size)
        }

    @Test
    fun `a second session_expired after a refresh signs out`() =
        runTest {
            val ended = sessionEndings()
            signIn(client, fake, storage)
            fake.on("GET", "$PROXY/orders", backendError("session_expired"))
            fake.on("POST", REFRESH, response(204))

            val result = client.httpRequest(get("$API/orders"))

            assertEquals(401, result.statusCode)
            assertEquals(1, fake.to(REFRESH).size)
            assertEquals(2, fake.to("$PROXY/orders").size)
            assertFalse(client.isSignedIn())
            assertEquals(1, ended.size)
        }

    @Test
    fun `login_required signs out without a refresh and tells the app once`() =
        runTest {
            val ended = sessionEndings()
            signIn(client, fake, storage)
            fake.on("GET", "$PROXY/orders", backendError("login_required"))

            val result = client.httpRequest(get("$API/orders"))
            client.httpRequest(get("$API/orders"))

            assertEquals(401, result.statusCode)
            assertTrue(fake.to(REFRESH).isEmpty())
            assertFalse(client.isSignedIn())
            assertNull(storage.retrieve(HANDLE_KEY))
            assertEquals(1, ended.size)
        }

    @Test
    fun `any other 401 is returned untouched`() =
        runTest {
            signIn(client, fake, storage)
            val apiAnswer = response(401, """{"error":"invalid_token","detail":"from the API"}""")
            fake.on("GET", "$PROXY/orders", apiAnswer)

            val result = client.httpRequest(get("$API/orders"))

            assertEquals(apiAnswer, result)
            assertTrue(fake.to(REFRESH).isEmpty())
            assertTrue(client.isSignedIn())
            assertEquals("h-1", storage.retrieve(HANDLE_KEY))
        }

    // URL refusal

    @Test
    fun `a URL outside the API base and the bypass URLs is refused and not sent`() =
        runTest {
            signIn(client, fake, storage)
            val before = fake.requests.size

            assertFails(ThunderIDErrorCode.INVALID_INPUT) { client.httpRequest(get("https://other.example.com/orders")) }
            assertFails(ThunderIDErrorCode.INVALID_INPUT) { client.httpRequest(get("$API.attacker.test/orders")) }
            assertFails(ThunderIDErrorCode.INVALID_INPUT) { client.httpRequest(get("${DATA_PLANE}x/items")) }

            assertEquals(before, fake.requests.size)
        }

    @Test
    fun `a path relative to the API base goes through the proxy`() =
        runTest {
            signIn(client, fake, storage)
            fake.on("GET", "$PROXY/orders?page=2", response(200, "[]"))

            assertEquals(200, client.httpRequest(get("/orders?page=2")).statusCode)
        }

    // Bypass

    @Test
    fun `a bypassed request goes straight to its URL with the token from the token endpoint`() =
        runTest {
            signIn(client, fake, storage)
            fake.on("POST", TOKEN, tokenResponse("t-1"))
            fake.on("GET", "$DATA_PLANE/items", response(200, "[]"))

            val result = client.httpRequest(get("$DATA_PLANE/items"))

            assertEquals(200, result.statusCode)
            assertEquals("h-1", fake.to(TOKEN).single().headers[SESSION_HANDLE_HEADER])
            val direct = fake.to("$DATA_PLANE/items").single()
            assertEquals("Bearer t-1", direct.headers["Authorization"])
            assertNull(direct.headers[SESSION_HANDLE_HEADER])
            assertTrue(fake.requests.none { it.url.startsWith(PROXY) })
            assertEquals("t-1", storedToken())
        }

    @Test
    fun `the token is reused until 30 seconds before it expires`() =
        runTest {
            signIn(client, fake, storage)
            fake.on("POST", TOKEN, tokenResponse("t-1", expiresIn = 3600), tokenResponse("t-2", expiresIn = 3600))
            fake.on("GET", "$DATA_PLANE/items", response(200, "[]"))

            client.httpRequest(get("$DATA_PLANE/items"))
            client.httpRequest(get(DATA_PLANE))

            assertEquals(1, fake.to(TOKEN).size)
            assertEquals("Bearer t-1", fake.requests.last().headers["Authorization"])
        }

    @Test
    fun `a token within 30 seconds of expiry is requested again`() =
        runTest {
            signIn(client, fake, storage)
            fake.on("POST", TOKEN, tokenResponse("t-1", expiresIn = 20), tokenResponse("t-2", expiresIn = 3600))

            assertEquals("t-1", client.getAccessToken())
            assertEquals("t-2", client.getAccessToken())
            assertEquals("t-2", client.getAccessToken())

            assertEquals(2, fake.to(TOKEN).size)
        }

    @Test
    fun `concurrent callers share one token request`() =
        runTest {
            signIn(client, fake, storage)
            val gate = CompletableDeferred<ThunderIDHttpResponse>()
            fake.onSuspending("POST", TOKEN) { gate.await() }

            val callers = List(3) { async { client.getAccessToken() } }
            runCurrent()
            gate.complete(tokenResponse("t-1"))

            callers.forEach { assertEquals("t-1", it.await()) }
            assertEquals(1, fake.to(TOKEN).size)
        }

    @Test
    fun `a 401 from a bypassed API gets a new token and retries once`() =
        runTest {
            signIn(client, fake, storage)
            fake.on("POST", TOKEN, tokenResponse("t-1"), tokenResponse("t-2"))
            fake.on("GET", "$DATA_PLANE/items", response(401), response(200, "[]"))

            val result = client.httpRequest(get("$DATA_PLANE/items"))

            assertEquals(200, result.statusCode)
            assertEquals(
                listOf("Bearer t-1", "Bearer t-2"),
                fake.to("$DATA_PLANE/items").map { it.headers["Authorization"] },
            )
        }

    @Test
    fun `a second 401 from a bypassed API goes back to the caller`() =
        runTest {
            signIn(client, fake, storage)
            fake.on("POST", TOKEN, tokenResponse("t-1"), tokenResponse("t-2"))
            fake.on("GET", "$DATA_PLANE/items", response(401, "denied"))

            val result = client.httpRequest(get("$DATA_PLANE/items"))

            assertEquals(401, result.statusCode)
            assertEquals("denied", result.body)
            assertEquals(2, fake.to("$DATA_PLANE/items").size)
            assertEquals(2, fake.to(TOKEN).size)
            assertTrue(client.isSignedIn())
        }

    @Test
    fun `session_expired from the token endpoint refreshes the session and asks again`() =
        runTest {
            signIn(client, fake, storage)
            fake.on("POST", TOKEN, backendError("session_expired"), tokenResponse("t-1"))
            fake.on("POST", REFRESH, response(204))

            assertEquals("t-1", client.getAccessToken())
            assertEquals(1, fake.to(REFRESH).size)
            assertEquals(2, fake.to(TOKEN).size)
        }

    @Test
    fun `login_required from the token endpoint signs out`() =
        runTest {
            val ended = sessionEndings()
            signIn(client, fake, storage)
            fake.on("POST", TOKEN, backendError("login_required"))

            assertFails(ThunderIDErrorCode.SESSION_EXPIRED) { client.getAccessToken() }
            assertFalse(client.isSignedIn())
            assertEquals(1, ended.size)
        }

    @Test
    fun `with attachToken off a bypassed request carries no token and never calls the token endpoint`() =
        runTest {
            signIn(client, fake, storage, backendConfig(ThunderIDBackendBypass(listOf(DATA_PLANE), attachToken = false)))
            fake.on("GET", "$DATA_PLANE/items", response(401))

            val result =
                client.httpRequest(
                    ThunderIDHttpRequest("GET", "$DATA_PLANE/items", mapOf("Authorization" to "Bearer stale"), null),
                )

            assertEquals(401, result.statusCode)
            val direct = fake.to("$DATA_PLANE/items").single()
            assertNull(direct.headers["Authorization"])
            assertNull(direct.headers[SESSION_HANDLE_HEADER])
            assertTrue(fake.to(TOKEN).isEmpty())
        }

    // Sign-out and a session that ends elsewhere

    @Test
    fun `signOut ends the backend session, clears the handle and the token, and returns the end-session URL`() =
        runTest {
            val ended = sessionEndings()
            signIn(client, fake, storage)
            fake.on("POST", TOKEN, tokenResponse("t-1"))
            client.getAccessToken()
            fake.on("POST", SIGN_OUT, response(200, """{"redirectUrl":"https://auth.example.com/oidc/logout"}"""))

            val url = client.signOut()

            assertEquals("https://auth.example.com/oidc/logout", url)
            assertEquals("h-1", fake.to(SIGN_OUT).single().headers[SESSION_HANDLE_HEADER])
            assertNull(storage.retrieve(HANDLE_KEY))
            assertNull(storedToken())
            assertFalse(client.isSignedIn())
            assertTrue(ended.isEmpty())
        }

    @Test
    fun `signOut clears local state even when the backend fails, and reports the failure`() =
        runTest {
            signIn(client, fake, storage)
            fake.on("POST", SIGN_OUT, response(500))

            assertFails(ThunderIDErrorCode.SERVER_ERROR) { client.signOut() }
            assertNull(storage.retrieve(HANDLE_KEY))
            assertFalse(client.isSignedIn())
        }

    @Test
    fun `a session that ended elsewhere clears the handle and the token and tells the app`() =
        runTest {
            val ended = sessionEndings()
            signIn(client, fake, storage)
            fake.on("POST", TOKEN, tokenResponse("t-1"))
            client.getAccessToken()
            fake.on("GET", CHECK_SESSION, response(200, """{"isSignedIn":false}"""))

            assertFalse(client.checkSession())

            assertNull(storage.retrieve(HANDLE_KEY))
            assertNull(storedToken())
            assertFalse(client.isSignedIn())
            assertEquals(1, ended.size)
        }

    @Test
    fun `a failed check keeps the session`() =
        runTest {
            signIn(client, fake, storage)
            fake.on("GET", CHECK_SESSION, response(503))

            assertTrue(client.checkSession())
            assertTrue(client.isSignedIn())
        }

    // Client surface

    @Test
    fun `getAccessToken returns the token from the token endpoint`() =
        runTest {
            signIn(client, fake, storage)
            fake.on("POST", TOKEN, tokenResponse("t-1"))

            assertEquals("t-1", client.getAccessToken())
            assertNull(TokenStore(storage).refreshToken())
        }

    @Test
    fun `operations that need a token the app does not hold, and embedded flows, are unsupported`() =
        runTest {
            signIn(client, fake, storage)
            val before = fake.requests.size

            assertFails(ThunderIDErrorCode.UNSUPPORTED_OPERATION) {
                client.exchangeToken(TokenExchangeRequestConfig(subjectToken = "s", subjectTokenType = "t"))
            }
            assertFails(ThunderIDErrorCode.UNSUPPORTED_OPERATION) {
                client.signIn(EmbeddedSignInPayload(), EmbeddedFlowRequestConfig(applicationId = "app"))
            }
            assertFails(ThunderIDErrorCode.UNSUPPORTED_OPERATION) { client.signUp() }

            assertEquals(before, fake.requests.size)
        }

    @Test
    fun `a stored handle is read back on restart`() =
        runTest {
            storage.store(HANDLE_KEY, "h-1")
            fake.on("GET", SESSION, response(200, """{"isSignedIn":true,"user":{"sub":"u1","email":"a@example.com"}}"""))

            client.initialize(backendConfig(), storage)

            assertTrue(client.isSignedIn())
            assertEquals("a@example.com", client.getUser().email)
            assertEquals(1, fake.to(SESSION).size)
        }

    @Test
    fun `a session read that finds session_expired refreshes and reads again`() =
        runTest {
            storage.store(HANDLE_KEY, "h-1")
            fake.on("GET", SESSION, backendError("session_expired"), response(200, """{"isSignedIn":true}"""))
            fake.on("POST", REFRESH, response(204))

            client.initialize(backendConfig(), storage)

            assertTrue(client.isSignedIn())
            assertEquals(2, fake.to(SESSION).size)
        }
}
