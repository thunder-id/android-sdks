// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.auth

import dev.thunderid.android.IAMException
import dev.thunderid.android.InMemoryStorageAdapter
import dev.thunderid.android.ThunderIDClient
import dev.thunderid.android.ThunderIDConfig
import dev.thunderid.android.ThunderIDErrorCode
import dev.thunderid.android.api.account.BASE
import dev.thunderid.android.api.account.form
import dev.thunderid.android.backend.FakeBackend
import dev.thunderid.android.backend.REDIRECT_URI
import dev.thunderid.android.backend.queryOf
import dev.thunderid.android.backend.response
import dev.thunderid.android.token.TokenStore
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.util.Base64

/** Redirect sign-in against an OpenID provider whose endpoints come from its discovery document. */
class ProviderDiscoveryTest {
    private val fake = FakeBackend()
    private val storage = InMemoryStorageAdapter()
    private val client = ThunderIDClient(fake)
    private val store = TokenStore(storage)
    private val keys: KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    private fun config(baseUrl: String) =
        ThunderIDConfig(baseUrl = baseUrl, clientId = "app", afterSignInUrl = REDIRECT_URI, scopes = listOf("openid"))

    @Before
    fun decodeBase64OnTheJvm() {
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.decode(any<String>(), any()) } answers { Base64.getDecoder().decode(firstArg<String>()) }
    }

    @After
    fun restoreBase64() {
        unmockkStatic(android.util.Base64::class)
    }

    private fun discovery(
        baseUrl: String,
        issuer: String,
        endpoints: String = ORG,
    ) = discoveryAt("$baseUrl/.well-known/openid-configuration", issuer, endpoints)

    private fun discoveryAt(
        url: String,
        issuer: String,
        endpoints: String = ORG,
    ) = fake.on(
        "GET",
        url,
        response(
            200,
            """
            {"issuer":"$issuer","authorization_endpoint":"$endpoints/authorize","token_endpoint":"$endpoints/token",
            "jwks_uri":"$endpoints/jwks","userinfo_endpoint":"$endpoints/userinfo","revocation_endpoint":"$endpoints/revoke"}
            """.trimIndent(),
        ),
    )

    private fun jwks(url: String) {
        val key = keys.public as RSAPublicKey
        val jwk = """{"kty":"RSA","kid":"k1","use":"sig","alg":"RS256","n":"${b64(key.modulus)}","e":"${b64(key.publicExponent)}"}"""
        fake.on("GET", url, response(200, """{"keys":[$jwk]}"""))
    }

    private fun tokens(
        url: String,
        issuer: String,
    ) = fake.on(
        "POST",
        url,
        response(
            200,
            """{"access_token":"opaque","token_type":"Bearer","expires_in":3600,"refresh_token":"r","id_token":"${idToken(issuer)}"}""",
        ),
    )

    private fun idToken(issuer: String): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"RS256","kid":"k1","typ":"JWT"}""".toByteArray())
        val exp = System.currentTimeMillis() / 1000 + 600
        val payload = encoder.encodeToString("""{"iss":"$issuer","aud":"app","sub":"u1","exp":$exp}""".toByteArray())
        val signature =
            Signature.getInstance("SHA256withRSA").run {
                initSign(keys.private)
                update("$header.$payload".toByteArray(Charsets.US_ASCII))
                sign()
            }
        return "$header.$payload.${encoder.encodeToString(signature)}"
    }

    private fun b64(value: BigInteger): String {
        val bytes = value.toByteArray().let { if (it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private suspend fun signInWithCode() {
        val url = client.signIn()
        assertEquals("app", queryOf(url)["client_id"])
        client.handleRedirectCallback("$REDIRECT_URI?code=the-code&state=s")
    }

    @Test
    fun `an issuer with a path takes every redirect endpoint from discovery`() =
        runTest {
            client.initialize(config(ISSUER), storage)
            discovery(ISSUER, ISSUER)
            jwks("$ORG/jwks")
            tokens("$ORG/token", ISSUER)
            fake.on("GET", "$ORG/userinfo", response(200, """{"sub":"u1","email":"ann@example.com"}"""))
            fake.on("POST", "$ORG/revoke", response(200))

            val url = client.signIn()
            assertTrue(url, url.startsWith("$ORG/authorize?response_type=code&client_id=app"))
            assertTrue(client.buildSignInUrl().startsWith("$ORG/authorize?"))
            val user = client.handleRedirectCallback("$REDIRECT_URI?code=the-code&state=s")

            val request = fake.to("$ORG/token").single()
            assertEquals("application/x-www-form-urlencoded", request.headers["Content-Type"])
            val body = form(request.body)
            assertEquals("authorization_code", body["grant_type"])
            assertEquals("the-code", body["code"])
            assertEquals(REDIRECT_URI, body["redirect_uri"])
            assertTrue(body["code_verifier"]!!.isNotEmpty())
            assertEquals(1, fake.to("$ORG/jwks").size)
            assertEquals("ann@example.com", user.claims["email"])
            assertEquals("opaque", store.accessToken())

            client.signOut()
            assertEquals("r", form(fake.to("$ORG/revoke").single().body)["token"])
            assertTrue(fake.requests.none { it.url.startsWith("$ISSUER/oauth2/") })
        }

    @Test
    fun `a refresh goes to the discovered token endpoint`() =
        runTest {
            client.initialize(config(ISSUER), storage)
            discovery(ISSUER, ISSUER)
            fake.on("POST", "$ORG/token", response(200, """{"access_token":"fresh","token_type":"Bearer","expires_in":3600}"""))
            store.save(dev.thunderid.android.TokenResponse(accessToken = "old", tokenType = "Bearer", expiresIn = 0, refreshToken = "r"))

            assertEquals("fresh", client.getAccessToken())
            assertEquals("refresh_token", form(fake.to("$ORG/token").single().body)["grant_type"])
        }

    @Test
    fun `without discovery the endpoints fall back to the ThunderID paths under baseUrl`() =
        runTest {
            client.initialize(config(BASE), storage)
            jwks("$BASE/oauth2/jwks")
            tokens("$BASE/oauth2/token", BASE)
            fake.on("GET", "$BASE/oauth2/userinfo", response(200, """{"sub":"u1"}"""))
            fake.on("POST", "$BASE/oauth2/revoke", response(200))

            assertTrue(client.signIn().startsWith("$BASE/oauth2/authorize?response_type=code"))
            client.handleRedirectCallback("$REDIRECT_URI?code=the-code&state=s")
            client.signOut()

            assertEquals(1, fake.to("$BASE/oauth2/token").size)
            assertEquals(1, fake.to("$BASE/oauth2/jwks").size)
            assertEquals(1, fake.to("$BASE/oauth2/userinfo").size)
            assertEquals(1, fake.to("$BASE/oauth2/revoke").size)
        }

    @Test
    fun `a ThunderID server's discovery resolves to the same URLs as the fallbacks`() =
        runTest {
            client.initialize(config(BASE), storage)
            discovery(BASE, BASE, endpoints = "$BASE/oauth2")
            jwks("$BASE/oauth2/jwks")
            tokens("$BASE/oauth2/token", BASE)
            fake.on("GET", "$BASE/oauth2/userinfo", response(200, """{"sub":"u1"}"""))

            assertTrue(client.signIn().startsWith("$BASE/oauth2/authorize?response_type=code"))
            client.handleRedirectCallback("$REDIRECT_URI?code=the-code&state=s")

            assertEquals(1, fake.to("$BASE/oauth2/token").size)
        }

    @Test
    fun `a discovered issuer with a trailing slash is the issuer the ID token must carry`() =
        runTest {
            client.initialize(config(ISSUER), storage)
            discovery(ISSUER, "$ISSUER/")
            jwks("$ORG/jwks")
            fake.on("GET", "$ORG/userinfo", response(200, """{"sub":"u1"}"""))
            tokens("$ORG/token", "$ISSUER/")

            signInWithCode()

            assertEquals("opaque", store.accessToken())
        }

    @Test
    fun `an ID token carrying baseUrl is refused when the discovered issuer differs from it by a trailing slash`() =
        runTest {
            client.initialize(config(ISSUER), storage)
            discovery(ISSUER, "$ISSUER/")
            jwks("$ORG/jwks")
            tokens("$ORG/token", ISSUER)

            assertRefusedIssuer()
        }

    @Test
    fun `a discovered issuer other than baseUrl is ignored, so the ID token must carry baseUrl`() =
        runTest {
            client.initialize(config(ISSUER), storage)
            discovery(ISSUER, "https://elsewhere.example.com")
            jwks("$ORG/jwks")
            tokens("$ORG/token", "https://elsewhere.example.com")

            assertRefusedIssuer()
        }

    private suspend fun assertRefusedIssuer() {
        try {
            signInWithCode()
            fail("Expected the ID token to be refused")
        } catch (e: IAMException) {
            assertEquals(ThunderIDErrorCode.AUTHENTICATION_FAILED, e.code)
            assertTrue(e.message, e.message!!.endsWith("ID token iss mismatch"))
        }
        assertNull(store.accessToken())
    }

    @Test
    fun `buildSignInUrl uses the discovered authorization endpoint once discovery has been read`() =
        runTest {
            client.initialize(config(ISSUER), storage)
            discovery(ISSUER, ISSUER)

            assertTrue(client.buildSignInUrl().startsWith("$ISSUER/oauth2/authorize?"))
            client.signIn()
            assertTrue(client.buildSignInUrl().startsWith("$ORG/authorize?"))
        }

    @Test
    fun `the authorization URL percent-encodes every query value`() =
        runTest {
            val redirect = "https://app.example.com/callback?from=sign in"
            val config =
                ThunderIDConfig(
                    baseUrl = BASE,
                    clientId = "my app",
                    afterSignInUrl = redirect,
                    scopes = listOf("openid", "profile", "email"),
                )
            client.initialize(config, storage)

            val options =
                dev.thunderid.android.SignInOptions(
                    prompt = "login consent",
                    loginHint = "ann+test@example.com",
                    fidp = "Google&Co",
                )
            val url = client.buildSignInUrl(options)

            val query = url.substringAfter('?')
            assertEquals(
                "response_type=code&client_id=my%20app&redirect_uri=https%3A%2F%2Fapp.example.com%2Fcallback%3Ffrom%3Dsign%20in" +
                    "&scope=openid%20profile%20email",
                query.substringBefore("&code_challenge="),
            )
            assertTrue(query, query.contains("&code_challenge_method=S256&prompt=login%20consent"))
            assertTrue(query, query.endsWith("&login_hint=ann%2Btest%40example.com&fidp=Google%26Co"))
            assertEquals("ann+test@example.com", queryOf(url)["login_hint"])
            assertEquals(redirect, queryOf(url)["redirect_uri"])
        }

    @Test
    fun `a pushed authorization request redirects with its request_uri percent-encoded`() =
        runTest {
            client.initialize(config(BASE).copy(pushedAuthorizationRequests = true), storage)
            val pushed = """{"request_uri":"urn:ietf:params:oauth:request_uri:a b+c","expires_in":60}"""
            fake.on("POST", "$BASE/oauth2/par", response(201, pushed))

            val url = client.signIn()

            val requestUri = "urn%3Aietf%3Aparams%3Aoauth%3Arequest_uri%3Aa%20b%2Bc"
            assertEquals("$BASE/oauth2/authorize?client_id=app&request_uri=$requestUri", url)
        }

    // ── endpoints.wellKnown ────────────────────────────────────────────────

    private fun rootConfig(wellKnown: String) =
        config(ROOT).copy(endpoints = dev.thunderid.android.ThunderIDEndpoints(wellKnown = wellKnown))

    @Test
    fun `a tenant root with wellKnown takes every redirect endpoint and the issuer from that document`() =
        runTest {
            client.initialize(rootConfig(WELL_KNOWN), storage)
            discoveryAt(WELL_KNOWN, ISSUER)
            jwks("$ORG/jwks")
            tokens("$ORG/token", ISSUER)
            fake.on("GET", "$ORG/userinfo", response(200, """{"sub":"u1","email":"ann@example.com"}"""))

            val url = client.signIn()
            assertTrue(url, url.startsWith("$ORG/authorize?response_type=code&client_id=app"))
            val user = client.handleRedirectCallback("$REDIRECT_URI?code=the-code&state=s")

            assertEquals("authorization_code", form(fake.to("$ORG/token").single().body)["grant_type"])
            assertEquals(1, fake.to("$ORG/jwks").size)
            assertEquals("ann@example.com", user.claims["email"])
            assertEquals("opaque", store.accessToken())
            // Asgardeo's endpoints sit under the root, so the URLs themselves show nothing fell back.
            assertEquals(setOf(WELL_KNOWN, "$ORG/token", "$ORG/jwks", "$ORG/userinfo"), fake.requests.map { it.url }.toSet())
        }

    @Test
    fun `with wellKnown an ID token carrying the tenant root is refused`() =
        runTest {
            client.initialize(rootConfig(WELL_KNOWN), storage)
            discoveryAt(WELL_KNOWN, ISSUER)
            jwks("$ORG/jwks")
            tokens("$ORG/token", ROOT)

            assertRefusedIssuer()
        }

    @Test
    fun `with wellKnown a discovered issuer other than the derived one is ignored`() =
        runTest {
            client.initialize(rootConfig(WELL_KNOWN), storage)
            discoveryAt(WELL_KNOWN, "https://elsewhere.example.com")
            jwks("$ORG/jwks")
            fake.on("GET", "$ORG/userinfo", response(200, """{"sub":"u1"}"""))
            tokens("$ORG/token", ISSUER)

            signInWithCode()

            assertEquals("opaque", store.accessToken())
        }

    @Test
    fun `a wellKnown without the standard suffix makes the discovered issuer the one the ID token must carry`() =
        runTest {
            val document = "https://idp.example.com/discovery/acme"
            val issuer = "https://issuer.example.com/acme"
            client.initialize(rootConfig(document), storage)
            discoveryAt(document, issuer)
            jwks("$ORG/jwks")
            fake.on("GET", "$ORG/userinfo", response(200, """{"sub":"u1"}"""))
            tokens("$ORG/token", issuer)

            signInWithCode()

            assertEquals("opaque", store.accessToken())
        }

    @Test
    fun `a wellKnown without the standard suffix that cannot be read refuses the ID token`() =
        runTest {
            client.initialize(rootConfig("https://idp.example.com/discovery/acme"), storage)
            jwks("$ROOT/oauth2/jwks")
            tokens("$ROOT/oauth2/token", ROOT)

            try {
                signInWithCode()
                fail("Expected the ID token to be refused")
            } catch (e: IAMException) {
                assertEquals(ThunderIDErrorCode.AUTHENTICATION_FAILED, e.code)
                assertTrue(e.message, e.message!!.contains("ID token issuer unknown"))
            }
            assertNull(store.accessToken())
        }

    @Test
    fun `a wellKnown that is not an HTTPS URL is refused at initialize`() =
        runTest {
            for (wellKnown in listOf("http://idp.example.com/.well-known/openid-configuration", "/.well-known/openid-configuration")) {
                try {
                    ThunderIDClient(fake).initialize(rootConfig(wellKnown), InMemoryStorageAdapter())
                    fail("Expected $wellKnown to be refused")
                } catch (e: IAMException) {
                    assertEquals(ThunderIDErrorCode.INVALID_CONFIGURATION, e.code)
                }
            }
        }

    private companion object {
        const val ROOT = "https://idp.example.com/t/acme"
        const val ORG = "$ROOT/oauth2"
        const val ISSUER = "$ORG/token"
        const val WELL_KNOWN = "$ISSUER/.well-known/openid-configuration"
    }
}
