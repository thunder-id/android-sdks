// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.token

import dev.thunderid.android.InMemoryStorageAdapter
import dev.thunderid.android.ThunderIDClient
import dev.thunderid.android.ThunderIDConfig
import dev.thunderid.android.TokenExchangeRequestConfig
import dev.thunderid.android.TokenResponse
import dev.thunderid.android.api.account.BASE
import dev.thunderid.android.api.account.form
import dev.thunderid.android.api.account.jwt
import dev.thunderid.android.backend.FakeBackend
import dev.thunderid.android.backend.REDIRECT_URI
import dev.thunderid.android.backend.response
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The token and revocation endpoints read only form-encoded bodies (RFC 6749 §4.1.3, RFC 7009 §2.1), so every
 * request to them must be sent that way, whether or not the sign-in was bounded to an organization unit.
 */
class TokenRequestEncodingTest {
    private val fake = FakeBackend()
    private val storage = InMemoryStorageAdapter()
    private val client = ThunderIDClient(fake)
    private val store = TokenStore(storage)
    private val config = ThunderIDConfig(baseUrl = BASE, clientId = "app", afterSignInUrl = REDIRECT_URI)
    private val formType = "application/x-www-form-urlencoded"
    private val tokenBody = """{"access_token":"new","token_type":"Bearer","expires_in":3600,"refresh_token":"r2"}"""

    @Test
    fun `an authorization code is redeemed form-encoded at the token endpoint`() =
        runTest {
            client.initialize(config, storage)
            fake.on("POST", "$BASE/oauth2/token", response(200, tokenBody))
            fake.on("GET", "$BASE/oauth2/userinfo", response(200, """{"sub":"u1"}"""))

            client.buildSignInUrl()
            client.handleRedirectCallback("$REDIRECT_URI?code=the-code&state=s")

            val request = fake.to("$BASE/oauth2/token").single()
            assertEquals(formType, request.headers["Content-Type"])
            assertEquals("authorization_code", form(request.body)["grant_type"])
            assertEquals("the-code", form(request.body)["code"])
            assertEquals("new", store.accessToken())
        }

    @Test
    fun `an expired access token is refreshed form-encoded`() =
        runTest {
            client.initialize(config, storage)
            store.save(TokenResponse(accessToken = jwt("sub" to "u1"), tokenType = "Bearer", expiresIn = 0, refreshToken = "r1"))
            fake.on("POST", "$BASE/oauth2/token", response(200, tokenBody))

            assertEquals("new", client.getAccessToken())

            val request = fake.to("$BASE/oauth2/token").single()
            assertEquals(formType, request.headers["Content-Type"])
            assertEquals("refresh_token", form(request.body)["grant_type"])
            assertEquals("r1", form(request.body)["refresh_token"])
        }

    @Test
    fun `a token exchange is sent form-encoded`() =
        runTest {
            client.initialize(config, storage)
            fake.on("POST", "$BASE/oauth2/token", response(200, tokenBody))

            client.exchangeToken(TokenExchangeRequestConfig(subjectToken = "s", subjectTokenType = "urn:x"))

            val request = fake.to("$BASE/oauth2/token").single()
            assertEquals(formType, request.headers["Content-Type"])
            assertEquals("urn:ietf:params:oauth:grant-type:token-exchange", form(request.body)["grant_type"])
            assertEquals("s", form(request.body)["subject_token"])
        }

    @Test
    fun `sign-out revokes the refresh token form-encoded`() =
        runTest {
            client.initialize(config, storage)
            store.save(TokenResponse(accessToken = jwt("sub" to "u1"), tokenType = "Bearer", expiresIn = 3600, refreshToken = "r1"))
            fake.on("POST", "$BASE/oauth2/revoke", response(200, ""))

            client.signOut()

            val request = fake.to("$BASE/oauth2/revoke").single()
            assertEquals(formType, request.headers["Content-Type"])
            assertEquals("r1", form(request.body)["token"])
            assertEquals("app", form(request.body)["client_id"])
        }
}
