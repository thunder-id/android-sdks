// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.account

import com.google.gson.JsonParser
import dev.thunderid.android.EmbeddedFlowRequestConfig
import dev.thunderid.android.EmbeddedSignInPayload
import dev.thunderid.android.FlowType
import dev.thunderid.android.IAMException
import dev.thunderid.android.InMemoryStorageAdapter
import dev.thunderid.android.SignInOptions
import dev.thunderid.android.ThunderIDClient
import dev.thunderid.android.ThunderIDConfig
import dev.thunderid.android.ThunderIDErrorCode
import dev.thunderid.android.TokenResponse
import dev.thunderid.android.api.auth.FlowExecutionClient
import dev.thunderid.android.api.auth.OrganizationUnitSwitchResult
import dev.thunderid.android.api.auth.SwitchOrganizationUnitOptions
import dev.thunderid.android.backend.BACKEND
import dev.thunderid.android.backend.FakeBackend
import dev.thunderid.android.backend.HANDLE_KEY
import dev.thunderid.android.backend.REDIRECT_URI
import dev.thunderid.android.backend.backendConfig
import dev.thunderid.android.backend.queryOf
import dev.thunderid.android.backend.response
import dev.thunderid.android.backend.signIn
import dev.thunderid.android.http.HttpClient
import dev.thunderid.android.token.TokenStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Base64

internal const val BASE = "https://id.example.com"

/** An unsigned JWT carrying [claims], enough for code that only reads the payload. */
internal fun jwt(vararg claims: Pair<String, Any>): String {
    val payload =
        com.google.gson
            .Gson()
            .toJson(claims.toMap())
    return "e30." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray()) + ".sig"
}

internal fun form(body: String?): Map<String, String> =
    body.orEmpty().split('&').filter { it.isNotEmpty() }.associate {
        java.net.URLDecoder.decode(it.substringBefore('='), "UTF-8") to java.net.URLDecoder.decode(it.substringAfter('='), "UTF-8")
    }

class OrganizationUnitApiTest {
    private val fake = FakeBackend()
    private val storage = InMemoryStorageAdapter()
    private val client = ThunderIDClient(fake)
    private val store = TokenStore(storage)
    private val config =
        ThunderIDConfig(baseUrl = BASE, clientId = "app", afterSignInUrl = REDIRECT_URI, applicationId = "app-id")

    private suspend inline fun assertFails(
        code: ThunderIDErrorCode,
        block: () -> Unit,
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

    private fun signedInBefore() {
        store.save(TokenResponse(accessToken = jwt("sub" to "u1", "ouId" to "ou-old"), tokenType = "Bearer", refreshToken = "old-refresh"))
    }

    private fun answerUserInfo() {
        fake.on("GET", "$BASE/oauth2/userinfo", response(200, """{"sub":"u1"}"""))
    }

    // ── Accessible units ───────────────────────────────────────────────────

    @Test
    fun `getAccessibleOrganizationUnits reads users me ous with the user's token and the options`() =
        runTest {
            client.initialize(config, storage)
            signedInBefore()
            store.save(TokenResponse(accessToken = "token", tokenType = "Bearer", expiresIn = 3600))
            fake.on(
                "GET",
                "$BASE/users/me/ous?scope=siblings&limit=5&offset=10",
                response(
                    200,
                    """{"current":"ou-1","totalResults":12,"startIndex":11,"count":2,
                    "organizationUnits":[{"id":"ou-1","handle":"acme-1","name":"Acme 1"},{"id":"ou-2","handle":"acme-2"}],
                    "links":[]}""",
                ),
            )

            val page =
                client.getAccessibleOrganizationUnits(
                    AccessibleOrganizationUnitsOptions(OrganizationUnitScope.SIBLINGS, limit = 5, offset = 10),
                )

            assertEquals("ou-1", page.current)
            assertEquals(12, page.totalResults)
            assertEquals(11, page.startIndex)
            assertEquals(
                listOf(AccessibleOrganizationUnit("ou-1", "acme-1", "Acme 1"), AccessibleOrganizationUnit("ou-2", "acme-2")),
                page.organizationUnits,
            )
            assertEquals("Bearer token", fake.requests.single().headers["Authorization"])
        }

    @Test
    fun `getAccessibleOrganizationUnits without options sends no query and reads a sparse page`() =
        runTest {
            client.initialize(config, storage)
            store.save(TokenResponse(accessToken = "token", tokenType = "Bearer", expiresIn = 3600))
            fake.on("GET", "$BASE/users/me/ous", response(200, """{"organizationUnits":[{"id":"ou-1"},{"handle":"no-id"}]}"""))

            val page = client.getAccessibleOrganizationUnits()

            assertNull(page.current)
            assertEquals(listOf(AccessibleOrganizationUnit("ou-1")), page.organizationUnits)
            assertEquals(1, page.totalResults)
        }

    @Test
    fun `getAccessibleOrganizationUnits maps a 401 like every operation`() =
        runTest {
            client.initialize(config, storage)
            store.save(TokenResponse(accessToken = "token", tokenType = "Bearer", expiresIn = 3600))
            fake.on("GET", "$BASE/users/me/ous", response(401, """{"code":"AUTH-4010"}"""))

            val error = assertFails(ThunderIDErrorCode.AUTHENTICATION_FAILED) { client.getAccessibleOrganizationUnits() }
            assertEquals("AUTH-4010", error.serverError?.code)
        }

    // ── Current unit ───────────────────────────────────────────────────────

    @Test
    fun `getCurrentOrganizationUnit reads the session's unit claims, preferring the ID token`() =
        runTest {
            client.initialize(config, storage)
            assertNull(client.getCurrentOrganizationUnit())

            store.save(TokenResponse(accessToken = jwt("sub" to "u1", "ouId" to "ou-a"), tokenType = "Bearer"))
            assertEquals(CurrentOrganizationUnit("ou-a"), client.getCurrentOrganizationUnit())

            store.save(
                TokenResponse(
                    accessToken = jwt("sub" to "u1"),
                    tokenType = "Bearer",
                    idToken = jwt("ouId" to "ou-b", "ouHandle" to "acme", "ouName" to "Acme"),
                ),
            )
            assertEquals(CurrentOrganizationUnit("ou-b", "acme", "Acme"), client.getCurrentOrganizationUnit())
        }

    @Test
    fun `getCurrentOrganizationUnit is null when the token names no unit or is not a JWT`() =
        runTest {
            client.initialize(config, storage)
            store.save(TokenResponse(accessToken = "opaque", tokenType = "Bearer"))
            assertNull(client.getCurrentOrganizationUnit())
            store.save(TokenResponse(accessToken = jwt("sub" to "u1", "ouId" to ""), tokenType = "Bearer"))
            assertNull(client.getCurrentOrganizationUnit())
        }

    @Test
    fun `getCurrentOrganizationUnit reads the backend session's claims`() =
        runTest {
            client.initialize(backendConfig(), storage)
            fake.on(
                "POST",
                "$BACKEND/api/auth/session",
                response(200, """{"handle":"h-1","isSignedIn":true,"user":{"sub":"u1","ouId":"ou-9","ouHandle":"nine"},"expiresAt":1}"""),
            )
            client.buildSignInUrl()
            client.handleRedirectCallback("$REDIRECT_URI?session_code=one-time")

            assertEquals(CurrentOrganizationUnit("ou-9", "nine"), client.getCurrentOrganizationUnit())
        }

    // ── Bounded sign-in, redirect mode ─────────────────────────────────────

    @Test
    fun `a sign-in bounded to a unit authorizes at the unit's endpoint, with the id percent-encoded`() =
        runTest {
            client.initialize(config, storage)

            val url = client.buildSignInUrl(SignInOptions(organizationUnitId = "ou 1/x"))

            assertTrue(url, url.startsWith("$BASE/ou/ou%201%2Fx/oauth2/authorize?response_type=code&client_id=app"))
            assertTrue(client.buildSignInUrl().startsWith("$BASE/oauth2/authorize?response_type=code"))
        }

    @Test
    fun `a bounded code is redeemed at the unit's token endpoint and replaces the whole session`() =
        runTest {
            client.initialize(config, storage)
            signedInBefore()
            answerUserInfo()
            fake.on(
                "POST",
                "$BASE/ou/ou-1/oauth2/token",
                response(200, """{"access_token":"new","token_type":"Bearer","expires_in":3600}"""),
            )

            client.buildSignInUrl(SignInOptions(organizationUnitId = "ou-1"))
            client.handleRedirectCallback("$REDIRECT_URI?code=the-code&state=s")

            val request = fake.to("$BASE/ou/ou-1/oauth2/token").single()
            assertEquals("application/x-www-form-urlencoded", request.headers["Content-Type"])
            val body = form(request.body)
            assertEquals("authorization_code", body["grant_type"])
            assertEquals("the-code", body["code"])
            assertEquals("app", body["client_id"])
            assertTrue(body["code_verifier"]!!.isNotEmpty())
            assertEquals("new", store.accessToken())
            // The old refresh token is bound to the old unit, so it does not outlive the switch.
            assertNull(store.refreshToken())
        }

    // ── Switching, browser ─────────────────────────────────────────────────

    @Test
    fun `switchOrganizationUnit through the browser signs in at the unit and replaces the tokens`() =
        runTest {
            client.initialize(config, storage)
            signedInBefore()
            answerUserInfo()
            fake.on(
                "POST",
                "$BASE/ou/ou-2/oauth2/token",
                response(200, """{"access_token":"ou2-token","token_type":"Bearer","refresh_token":"ou2-refresh","expires_in":3600}"""),
            )
            var opened: String? = null

            val result =
                client.switchOrganizationUnit(
                    "ou-2",
                    SwitchOrganizationUnitOptions(browser = { url ->
                        opened = url
                        "$REDIRECT_URI?code=c2"
                    }),
                )

            assertTrue(opened!!.startsWith("$BASE/ou/ou-2/oauth2/authorize?"))
            assertTrue(result is OrganizationUnitSwitchResult.Switched)
            assertEquals("ou2-token", store.accessToken())
            assertEquals("ou2-refresh", store.refreshToken())
        }

    @Test
    fun `a unit the application cannot be used from fails with a documented error and keeps the session`() =
        runTest {
            client.initialize(config, storage)
            signedInBefore()
            val before = store.accessToken()

            val error =
                assertFails(ThunderIDErrorCode.ORGANIZATION_UNIT_UNAVAILABLE) {
                    client.switchOrganizationUnit(
                        "ou-x",
                        SwitchOrganizationUnitOptions(browser = { "$REDIRECT_URI?error=unauthorized_client&state=s" }),
                    )
                }

            assertEquals("unauthorized_client", error.serverError?.code)
            assertEquals(before, store.accessToken())
            assertEquals("old-refresh", store.refreshToken())
            assertTrue(fake.to("$BASE/ou/ou-x/oauth2/token").isEmpty())
        }

    @Test
    fun `a refused code at the unit's token endpoint keeps the session`() =
        runTest {
            client.initialize(config, storage)
            signedInBefore()
            fake.on("POST", "$BASE/ou/ou-2/oauth2/token", response(400, """{"error":"invalid_grant"}"""))

            assertFails(ThunderIDErrorCode.INVALID_GRANT) {
                client.switchOrganizationUnit("ou-2", SwitchOrganizationUnitOptions(browser = { "$REDIRECT_URI?code=c" }))
            }
            assertEquals("old-refresh", store.refreshToken())
        }

    @Test
    fun `a dismissed browser forgets the switch, so a later callback is not redeemed for the unit`() =
        runTest {
            client.initialize(config, storage)
            signedInBefore()

            try {
                client.switchOrganizationUnit("ou-2", SwitchOrganizationUnitOptions(browser = { throw IllegalStateException("closed") }))
                fail("Expected the browser failure")
            } catch (_: IllegalStateException) {
            }
            assertFails(ThunderIDErrorCode.INVALID_GRANT) { client.handleRedirectCallback("$REDIRECT_URI?code=late") }
            assertTrue(fake.to("$BASE/ou/ou-2/oauth2/token").isEmpty())
            assertEquals("old-refresh", store.refreshToken())
        }

    @Test
    fun `switchOrganizationUnit refuses a blank unit`() =
        runTest {
            client.initialize(config, storage)
            assertFails(ThunderIDErrorCode.INVALID_INPUT) { client.switchOrganizationUnit(" ") }
        }

    // ── Switching, embedded ────────────────────────────────────────────────

    @Test
    fun `an embedded switch starts the sign-in flow with ouId and replaces the session when it completes`() =
        runTest {
            client.initialize(config, storage)
            signedInBefore()
            answerUserInfo()
            val assertion = jwt("sub" to "u1", "ouId" to "ou-3")
            fake.on("POST", "$BASE/flow/execute", response(200, """{"flowStatus":"COMPLETE","assertion":"$assertion"}"""))

            val result = client.switchOrganizationUnit("ou-3")

            val body = JsonParser.parseString(fake.to("$BASE/flow/execute").single().body).asJsonObject
            assertEquals("ou-3", body["ouId"].asString)
            assertEquals("app-id", body["applicationId"].asString)
            assertEquals("AUTHENTICATION", body["flowType"].asString)
            assertTrue(result is OrganizationUnitSwitchResult.Switched)
            assertEquals(assertion, store.accessToken())
            assertNull(store.refreshToken())
            assertEquals("ou-3", client.getCurrentOrganizationUnit()?.id)
        }

    @Test
    fun `an embedded switch that needs the user returns the flow and keeps the session until it completes`() =
        runTest {
            client.initialize(config, storage)
            signedInBefore()
            fake.on(
                "POST",
                "$BASE/flow/execute",
                response(200, """{"executionId":"e-1","flowStatus":"INCOMPLETE","data":{"inputs":[{"identifier":"password"}]}}"""),
            )

            val result = client.switchOrganizationUnit("ou-3")

            assertEquals("e-1", (result as OrganizationUnitSwitchResult.SignInRequired).response.flowId)
            assertEquals("old-refresh", store.refreshToken())

            // The continuation carries no ouId, and completing it replaces the session.
            val assertion = jwt("sub" to "u1", "ouId" to "ou-3")
            fake.on("POST", "$BASE/flow/execute", response(200, """{"flowStatus":"COMPLETE","assertion":"$assertion"}"""))
            client.signIn(EmbeddedSignInPayload(flowId = "e-1", inputs = mapOf("password" to "x")), EmbeddedFlowRequestConfig("app-id"))
            assertFalse(JsonParser.parseString(fake.requests.last().body).asJsonObject.has("ouId"))
            assertNull(store.refreshToken())
        }

    @Test
    fun `an embedded switch to an inaccessible unit fails with the documented error`() =
        runTest {
            client.initialize(config, storage)
            signedInBefore()
            fake.on("POST", "$BASE/flow/execute", response(400, """{"code":"FES-1023","message":"Invalid organization unit"}"""))

            val error = assertFails(ThunderIDErrorCode.ORGANIZATION_UNIT_UNAVAILABLE) { client.switchOrganizationUnit("ou-x") }

            assertEquals("FES-1023", error.serverError?.code)
            assertEquals("old-refresh", store.refreshToken())
        }

    @Test
    fun `an embedded switch the flow refuses keeps the session`() =
        runTest {
            client.initialize(config, storage)
            signedInBefore()
            fake.on(
                "POST",
                "$BASE/flow/execute",
                response(200, """{"flowStatus":"ERROR","error":{"code":"FET-1122","message":{"defaultValue":"Not in this unit"}}}"""),
            )

            val error = assertFails(ThunderIDErrorCode.AUTHENTICATION_FAILED) { client.switchOrganizationUnit("ou-x") }

            assertEquals("FET-1122", error.serverError?.code)
            assertEquals("old-refresh", store.refreshToken())
        }

    @Test
    fun `an embedded switch needs an application id`() =
        runTest {
            client.initialize(config.copy(applicationId = null), storage)
            assertFails(ThunderIDErrorCode.INVALID_CONFIGURATION) { client.switchOrganizationUnit("ou-1") }
        }

    @Test
    fun `ouId is sent only on the request that starts an authentication flow`() {
        val flow = FlowExecutionClient(HttpClient(BASE))
        assertEquals("ou-1", flow.initiateBody("app", FlowType.AUTHENTICATION, "ou-1")["ouId"])
        assertFalse(flow.initiateBody("app", FlowType.REGISTRATION, "ou-1").containsKey("ouId"))
        assertFalse(flow.initiateBody("app", FlowType.AUTHENTICATION).containsKey("ouId"))
        assertFalse(flow.submitBody("e-1", "a", null).containsKey("ouId"))
    }

    @Test
    fun `an embedded sign-in without a unit keeps the earlier refresh token as before`() =
        runTest {
            client.initialize(config, storage)
            signedInBefore()
            fake.on("POST", "$BASE/flow/execute", response(200, """{"flowStatus":"COMPLETE","assertion":"${jwt("sub" to "u1")}"}"""))

            client.signIn(EmbeddedSignInPayload(), EmbeddedFlowRequestConfig("app-id"))

            assertFalse(JsonParser.parseString(fake.requests.single().body).asJsonObject.has("ouId"))
            assertEquals("old-refresh", store.refreshToken())
        }

    // ── Backend mode ───────────────────────────────────────────────────────

    @Test
    fun `a backend sign-in bounded to a unit passes it to the backend as ouId`() =
        runTest {
            client.initialize(backendConfig(), storage)

            val query = queryOf(client.buildSignInUrl(SignInOptions(organizationUnitId = "ou 1")))

            assertEquals("ou 1", query["ouId"])
            assertFalse(queryOf(client.buildSignInUrl()).containsKey("ouId"))
        }

    @Test
    fun `a backend switch needs a browser, and a refused one keeps the backend session`() =
        runTest {
            signIn(client, fake, storage)
            assertFails(ThunderIDErrorCode.INVALID_CONFIGURATION) { client.switchOrganizationUnit("ou-2") }

            fake.on("POST", "$BACKEND/api/auth/session", response(500))
            assertFails(ThunderIDErrorCode.AUTHENTICATION_FAILED) {
                client.switchOrganizationUnit("ou-2", SwitchOrganizationUnitOptions(browser = { "$REDIRECT_URI?session_code=c2" }))
            }
            assertEquals("h-1", storage.retrieve(HANDLE_KEY))

            val refused =
                assertFails(ThunderIDErrorCode.ORGANIZATION_UNIT_UNAVAILABLE) {
                    client.switchOrganizationUnit(
                        "ou-2",
                        SwitchOrganizationUnitOptions(browser = { "$REDIRECT_URI?error=unauthorized_client" }),
                    )
                }
            assertEquals("unauthorized_client", refused.serverError?.code)
            assertEquals("h-1", storage.retrieve(HANDLE_KEY))
        }

    @Test
    fun `a backend switch replaces the handle once the new session has started`() =
        runTest {
            signIn(client, fake, storage)
            fake.on(
                "POST",
                "$BACKEND/api/auth/session",
                response(200, """{"handle":"h-2","isSignedIn":true,"user":{"sub":"u1","ouId":"ou-2"}}"""),
            )

            val result =
                client.switchOrganizationUnit(
                    "ou-2",
                    SwitchOrganizationUnitOptions(browser = { url ->
                        assertEquals("ou-2", queryOf(url)["ouId"])
                        "$REDIRECT_URI?session_code=c2"
                    }),
                )

            assertEquals("ou-2", (result as OrganizationUnitSwitchResult.Switched).user["ouId"])
            assertEquals("h-2", storage.retrieve(HANDLE_KEY))
        }
}
