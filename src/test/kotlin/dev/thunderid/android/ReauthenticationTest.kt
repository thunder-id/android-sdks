// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android

import com.google.gson.JsonParser
import dev.thunderid.android.api.account.BASE
import dev.thunderid.android.api.account.form
import dev.thunderid.android.api.account.jwt
import dev.thunderid.android.backend.FakeBackend
import dev.thunderid.android.backend.REDIRECT_URI
import dev.thunderid.android.backend.backendConfig
import dev.thunderid.android.backend.challengeOf
import dev.thunderid.android.backend.queryOf
import dev.thunderid.android.backend.response
import dev.thunderid.android.token.TokenStore
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.util.Base64

/** Signing the signed-in user in again after a step-up refusal. */
class ReauthenticationTest {
    private val fake = FakeBackend()
    private val storage = InMemoryStorageAdapter()
    private val store = TokenStore(storage)
    private val client = ThunderIDClient(fake)
    private val keys: KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val config =
        ThunderIDConfig(baseUrl = BASE, clientId = "app", afterSignInUrl = REDIRECT_URI, scopes = listOf("profile", "openid"))
    private val authorizeUrls = mutableListOf<String>()

    @Before
    fun decodeBase64OnTheJvm() {
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.decode(any<String>(), any()) } answers { Base64.getDecoder().decode(firstArg<String>()) }
    }

    @After
    fun restoreBase64() {
        unmockkStatic(android.util.Base64::class)
    }

    private fun idToken(
        sub: String,
        nonce: String? = null,
    ): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"RS256","kid":"k1","typ":"JWT"}""".toByteArray())
        val exp = System.currentTimeMillis() / 1000 + 600
        val nonceClaim = nonce?.let { ",\"nonce\":\"$it\"" } ?: ""
        val payload = encoder.encodeToString("""{"iss":"$BASE","aud":"app","sub":"$sub","exp":$exp$nonceClaim}""".toByteArray())
        val signature =
            Signature.getInstance("SHA256withRSA").run {
                initSign(keys.private)
                update("$header.$payload".toByteArray(Charsets.US_ASCII))
                sign()
            }
        return "$header.$payload.${encoder.encodeToString(signature)}"
    }

    private val oldIdToken by lazy { idToken("u1") }

    private suspend fun signedIn() {
        client.initialize(config, storage)
        store.save(
            TokenResponse(
                accessToken = "old-access",
                tokenType = "Bearer",
                expiresIn = 3600,
                refreshToken = "old-refresh",
                idToken = oldIdToken,
            ),
        )
        val key = keys.public as RSAPublicKey
        val n = b64(key.modulus.toByteArray())
        val e = b64(key.publicExponent.toByteArray())
        val jwk = """{"kty":"RSA","kid":"k1","alg":"RS256","n":"$n","e":"$e"}"""
        fake.on("GET", "$BASE/oauth2/jwks", response(200, """{"keys":[$jwk]}"""))
        fake.on("GET", "$BASE/oauth2/userinfo", response(200, """{"sub":"u1"}"""))
    }

    private fun b64(bytes: ByteArray): String {
        val unsigned = if (bytes[0] == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes
        return Base64.getUrlEncoder().withoutPadding().encodeToString(unsigned)
    }

    /** A browser that signs [sub] in and returns to the app; [state] and [nonce] replace the values it returns. */
    private fun browser(
        sub: String = "u1",
        state: (String) -> String = { it },
        nonce: (String) -> String = { it },
    ): suspend (String) -> String =
        { url ->
            authorizeUrls += url
            val query = queryOf(url)
            val token = idToken(sub, nonce(query.getValue("nonce")))
            fake.on(
                "POST",
                "$BASE/oauth2/token",
                response(
                    200,
                    """
                    {"access_token":"new-access","token_type":"Bearer","expires_in":3600,
                     "refresh_token":"new-refresh","id_token":"$token"}
                    """.trimIndent(),
                ),
            )
            "$REDIRECT_URI?code=the-code&state=${state(query.getValue("state"))}"
        }

    private fun assertSessionKept() {
        assertEquals("old-access", store.accessToken())
        assertEquals("old-refresh", store.refreshToken())
        assertEquals(oldIdToken, store.idToken())
    }

    private fun refreshRequests() = fake.requests.filter { form(it.body)["grant_type"] == "refresh_token" }

    private suspend fun refusal(block: suspend () -> Unit): IAMException {
        try {
            block()
        } catch (e: IAMException) {
            return e
        }
        throw AssertionError("Expected IAMException")
    }

    @Test
    fun `signs in again with max_age and id_token_hint, then replaces the session`() =
        runTest {
            signedIn()

            client.reauthenticate(300, browser())

            val query = queryOf(authorizeUrls.single())
            assertTrue(authorizeUrls.single().startsWith("$BASE/oauth2/authorize?"))
            assertEquals("300", query["max_age"])
            assertNull(query["prompt"])
            assertEquals(oldIdToken, query["id_token_hint"])
            assertEquals("openid profile", query["scope"])
            assertEquals("S256", query["code_challenge_method"])
            assertFalse(query["state"].isNullOrEmpty())
            assertFalse(query["nonce"].isNullOrEmpty())
            val redeem = form(fake.to("$BASE/oauth2/token").single().body)
            assertEquals("authorization_code", redeem["grant_type"])
            assertEquals(query["code_challenge"], challengeOf(redeem.getValue("code_verifier")))
            assertEquals("new-access", store.accessToken())
            assertEquals("new-refresh", store.refreshToken())
            assertTrue(refreshRequests().isEmpty())
        }

    @Test
    fun `asks for a fresh sign-in with prompt=login when the max age is unknown`() =
        runTest {
            signedIn()

            client.reauthenticate(null, browser())

            val query = queryOf(authorizeUrls.single())
            assertEquals("login", query["prompt"])
            assertNull(query["max_age"])
        }

    @Test
    fun `another user signing in fails and keeps the session as it was`() =
        runTest {
            signedIn()

            val error = refusal { client.reauthenticate(300, browser(sub = "u2")) }

            assertEquals(ThunderIDErrorCode.AUTHENTICATION_FAILED, error.code)
            assertSessionKept()
            assertTrue(refreshRequests().isEmpty())
        }

    @Test
    fun `a state or nonce that does not match fails and keeps the session as it was`() =
        runTest {
            signedIn()

            val badState = refusal { client.reauthenticate(300, browser(state = { "other" })) }
            val badNonce = refusal { client.reauthenticate(300, browser(nonce = { "other" })) }

            assertEquals(ThunderIDErrorCode.AUTHENTICATION_FAILED, badState.code)
            assertEquals(ThunderIDErrorCode.AUTHENTICATION_FAILED, badNonce.code)
            assertSessionKept()
            // A different state is refused before the code is redeemed.
            assertEquals(1, fake.to("$BASE/oauth2/token").size)
        }

    @Test
    fun `a sign-in the server refuses keeps the session as it was`() =
        runTest {
            signedIn()

            val error =
                refusal {
                    client.reauthenticate(300) { url -> "$REDIRECT_URI?error=access_denied&state=${queryOf(url)["state"]}" }
                }

            assertEquals(ThunderIDErrorCode.AUTHENTICATION_FAILED, error.code)
            assertEquals("access_denied", error.serverError?.code)
            assertSessionKept()
        }

    @Test
    fun `is not available when sign-in runs on a backend`() =
        runTest {
            client.initialize(backendConfig(), storage)

            val error = refusal { client.reauthenticate(300) { error("no browser expected") } }

            assertEquals(ThunderIDErrorCode.UNSUPPORTED_OPERATION, error.code)
            assertEquals(ReauthenticationMethod.UNAVAILABLE, client.reauthenticationMethod())
        }

    @Test
    fun `a redirect session signs in again in the browser and an app-native one in its flow`() =
        runTest {
            signedIn()
            assertEquals(ReauthenticationMethod.BROWSER, client.reauthenticationMethod())

            val appNative = ThunderIDClient(fake)
            appNative.initialize(ThunderIDConfig(baseUrl = BASE, applicationId = "app-1"), InMemoryStorageAdapter())
            assertEquals(ReauthenticationMethod.FLOW, appNative.reauthenticationMethod())
        }

    // ── App-native ──────────────────────────────────────────────────────────

    private val issued = jwt("sub" to "u1", "client_id" to "app", "auth_time" to 2)

    /** Signs u1 in with the embedded flow, so the session is one from an app-native sign-in. */
    private suspend fun appNativeSession(afterSignInUrl: String? = null) {
        client.initialize(
            ThunderIDConfig(baseUrl = BASE, clientId = "app", applicationId = "app-1", afterSignInUrl = afterSignInUrl),
            storage,
        )
        fake.on(
            "POST",
            "$BASE/flow/execute",
            response(
                200,
                """{"flowStatus":"COMPLETE","assertion":"${jwt("sub" to "u1", "auth_time" to 1)}",""" +
                    """"sessionData":[{"flowIdentifier":"f-0","sessionHandle":"h-0"}]}""",
            ),
        )
        fake.on("POST", "$BASE/oauth2/token", response(200, """{"access_token":"old-at","token_type":"Bearer"}"""))
        client.signIn(EmbeddedSignInPayload(), EmbeddedFlowRequestConfig("app-1"))
        fake.requests.clear()
        fake.on("POST", "$BASE/oauth2/token", response(200, """{"access_token":"$issued","token_type":"Bearer"}"""))
    }

    private fun completed(sub: String) = response(200, """{"flowStatus":"COMPLETE","assertion":"${jwt("sub" to sub, "auth_time" to 2)}"}""")

    private val step = response(200, """{"executionId":"e-1","flowStatus":"INCOMPLETE"}""")

    private fun flowBody(index: Int) = JsonParser.parseString(fake.to("$BASE/flow/execute")[index].body).asJsonObject

    @Test
    fun `a session from an app-native sign-in signs in again with the flow`() =
        runTest {
            appNativeSession(afterSignInUrl = REDIRECT_URI)

            assertEquals(ReauthenticationMethod.FLOW, client.reauthenticationMethod())
        }

    @Test
    fun `the re-sign-in starts with maxAge and the kept session handles`() =
        runTest {
            appNativeSession()
            fake.on("POST", "$BASE/flow/execute", step)

            client.signIn(EmbeddedSignInPayload(), EmbeddedFlowRequestConfig("app-1", reauthentication = true, maxAge = 30))
            client.signIn(EmbeddedSignInPayload(), EmbeddedFlowRequestConfig("app-1", reauthentication = true))
            client.signIn(EmbeddedSignInPayload(), EmbeddedFlowRequestConfig("app-1"))

            assertEquals(30, flowBody(0)["maxAge"].asInt)
            assertEquals("h-0", flowBody(0)["sessionData"].asJsonArray[0].asJsonObject["sessionHandle"].asString)
            assertEquals(0, flowBody(1)["maxAge"].asInt)
            assertFalse(flowBody(2).has("maxAge"))
        }

    @Test
    fun `the same user's re-sign-in replaces the session with the exchanged token`() =
        runTest {
            appNativeSession()
            fake.on("POST", "$BASE/flow/execute", completed("u1"))

            client.signIn(EmbeddedSignInPayload(flowId = "e-1"), EmbeddedFlowRequestConfig("app-1", reauthentication = true))

            assertEquals(issued, store.accessToken())
            assertEquals(ReauthenticationMethod.FLOW, client.reauthenticationMethod())
            assertTrue(refreshRequests().isEmpty())
        }

    @Test
    fun `another user's re-sign-in is refused before the exchange and the session stays`() =
        runTest {
            appNativeSession()
            fake.on("POST", "$BASE/flow/execute", completed("u2"))

            val error =
                refusal {
                    client.signIn(EmbeddedSignInPayload(flowId = "e-1"), EmbeddedFlowRequestConfig("app-1", reauthentication = true))
                }

            assertTrue(error.isReauthenticationByAnotherAccount)
            assertTrue(fake.to("$BASE/oauth2/token").isEmpty())
            assertEquals("old-at", store.accessToken())
        }

    @Test
    fun `a step that completes a started re-sign-in is checked without the flag`() =
        runTest {
            appNativeSession()
            fake.on("POST", "$BASE/flow/execute", step, completed("u2"))
            client.signIn(EmbeddedSignInPayload(), EmbeddedFlowRequestConfig("app-1", reauthentication = true))

            val error = refusal { client.signIn(EmbeddedSignInPayload(flowId = "e-1"), EmbeddedFlowRequestConfig("app-1")) }

            assertTrue(error.isReauthenticationByAnotherAccount)
            assertTrue(fake.to("$BASE/oauth2/token").isEmpty())
            assertEquals("old-at", store.accessToken())
        }

    @Test
    fun `tokens from the token endpoint end the app-native mark`() =
        runTest {
            appNativeSession(afterSignInUrl = REDIRECT_URI)

            store.save(TokenResponse(accessToken = "redirect-at", tokenType = "Bearer", idToken = jwt("sub" to "u1")))

            assertEquals(ReauthenticationMethod.BROWSER, client.reauthenticationMethod())
        }

    @Test
    fun `a re-sign-in whose start returns an error step is still checked when it completes`() =
        runTest {
            appNativeSession()
            fake.on(
                "POST",
                "$BASE/flow/execute",
                response(200, """{"executionId":"e-1","flowStatus":"ERROR"}"""),
                completed("u2"),
            )
            client.signIn(EmbeddedSignInPayload(), EmbeddedFlowRequestConfig("app-1", reauthentication = true))

            val error = refusal { client.signIn(EmbeddedSignInPayload(flowId = "e-1"), EmbeddedFlowRequestConfig("app-1")) }

            assertTrue(error.isReauthenticationByAnotherAccount)
            assertTrue(fake.to("$BASE/oauth2/token").isEmpty())
        }

    @Test
    fun `sign-up steps on a re-sign-in flow are checked too`() =
        runTest {
            appNativeSession()
            fake.on("POST", "$BASE/flow/execute", step, completed("u2"))
            client.signIn(EmbeddedSignInPayload(), EmbeddedFlowRequestConfig("app-1", reauthentication = true))

            val error = refusal { client.signUp(EmbeddedSignInPayload(flowId = "e-1")) }

            assertTrue(error.isReauthenticationByAnotherAccount)
            assertEquals("old-at", store.accessToken())
        }

    @Test
    fun `signing out forgets the re-sign-in flows in progress`() =
        runTest {
            appNativeSession()
            fake.on("POST", "$BASE/flow/execute", step)
            client.signIn(EmbeddedSignInPayload(), EmbeddedFlowRequestConfig("app-1", reauthentication = true))
            client.signOut()
            fake.on("POST", "$BASE/flow/execute", completed("u2"))
            fake.on("POST", "$BASE/oauth2/token", response(200, """{"access_token":"u2-at","token_type":"Bearer"}"""))

            client.signIn(EmbeddedSignInPayload(flowId = "e-1"), EmbeddedFlowRequestConfig("app-1"))

            assertEquals("u2-at", store.accessToken())
        }

    @Test
    fun `a re-sign-in with nobody signed in does not start`() =
        runTest {
            client.initialize(ThunderIDConfig(baseUrl = BASE, clientId = "app", applicationId = "app-1"), storage)

            val error =
                refusal { client.signIn(EmbeddedSignInPayload(), EmbeddedFlowRequestConfig("app-1", reauthentication = true)) }

            assertEquals(ThunderIDErrorCode.AUTHENTICATION_FAILED, error.code)
            assertFalse(error.isReauthenticationByAnotherAccount)
            assertTrue(fake.to("$BASE/flow/execute").isEmpty())
        }

    @Test
    fun `a refresh keeps the embedded sign-in mark`() {
        val refreshed = TokenResponse(accessToken = "new", tokenType = "Bearer", expiresIn = 3600)
        store.markEmbeddedSignIn()

        store.save(refreshed, refreshed = true)
        assertTrue(store.isEmbeddedSignIn())

        store.save(refreshed)
        assertFalse(store.isEmbeddedSignIn())
    }
}
