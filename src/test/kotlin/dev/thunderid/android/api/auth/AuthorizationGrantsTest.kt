// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.auth

import com.google.gson.JsonParser
import dev.thunderid.android.EmbeddedSignInPayload
import dev.thunderid.android.FlowStatus
import dev.thunderid.android.IAMException
import dev.thunderid.android.InMemoryStorageAdapter
import dev.thunderid.android.SignInOptions
import dev.thunderid.android.ThunderIDClient
import dev.thunderid.android.ThunderIDConfig
import dev.thunderid.android.ThunderIDErrorCode
import dev.thunderid.android.api.account.BASE
import dev.thunderid.android.api.account.form
import dev.thunderid.android.backend.FakeBackend
import dev.thunderid.android.backend.REDIRECT_URI
import dev.thunderid.android.backend.backendConfig
import dev.thunderid.android.backend.queryOf
import dev.thunderid.android.backend.response
import dev.thunderid.android.token.TokenStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthorizationGrantsTest {
    private val fake = FakeBackend()
    private val storage = InMemoryStorageAdapter()
    private val client = ThunderIDClient(fake)
    private val store = TokenStore(storage)
    private val config =
        ThunderIDConfig(
            baseUrl = BASE,
            clientId = "tv-app",
            afterSignInUrl = REDIRECT_URI,
            applicationId = "app-id",
            scopes = listOf("openid", "profile"),
        )
    private val discovery = "$BASE/.well-known/openid-configuration"
    private val token = "$BASE/oauth2/token"

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

    private fun metadata(extra: String) = response(200, """{"issuer":"$BASE",$extra}""")

    // ── CIBA ───────────────────────────────────────────────────────────────

    @Test
    fun `initiateCIBA posts a public client's form to the discovered endpoint`() =
        runTest {
            client.initialize(config, storage)
            fake.on("GET", discovery, metadata(""""backchannel_authentication_endpoint":"$BASE/oauth2/bc-authorize""""))
            fake.on("POST", "$BASE/oauth2/bc-authorize", response(200, """{"auth_req_id":"req-1","expires_in":90,"interval":3}"""))

            val started =
                client.initiateCIBA(
                    CIBAInitiateOptions(
                        loginHint = "ann@example.com",
                        bindingMessage = "A1",
                        requestedExpiry = 90,
                        authorizationDetails = listOf(mapOf("type" to "payment")),
                    ),
                )

            assertEquals(CIBAInitiateResponse("req-1", 90, 3), started)
            assertFalse(started.toString().contains("req-1"))
            val request = fake.to("$BASE/oauth2/bc-authorize").single()
            assertEquals("application/x-www-form-urlencoded", request.headers["Content-Type"])
            assertNull(request.headers["Authorization"])
            val body = form(request.body)
            assertEquals("tv-app", body["client_id"])
            assertEquals("openid profile", body["scope"])
            assertEquals("ann@example.com", body["login_hint"])
            assertEquals("A1", body["binding_message"])
            assertEquals("90", body["requested_expiry"])
            assertEquals("""[{"type":"payment"}]""", body["authorization_details"])
            assertFalse(body.containsKey("client_secret"))
        }

    @Test
    fun `initiateCIBA needs exactly one hint`() =
        runTest {
            client.initialize(config, storage)
            assertFails(ThunderIDErrorCode.INVALID_INPUT) { client.initiateCIBA(CIBAInitiateOptions()) }
            assertFails(ThunderIDErrorCode.INVALID_INPUT) {
                client.initiateCIBA(CIBAInitiateOptions(loginHint = "a", idTokenHint = "b"))
            }
            assertTrue(fake.requests.isEmpty())
        }

    @Test
    fun `initiateCIBA fails with a documented error when discovery names no endpoint`() =
        runTest {
            client.initialize(config, storage)
            fake.on("GET", discovery, metadata(""""token_endpoint":"$token""""))

            assertFails(ThunderIDErrorCode.UNSUPPORTED_OPERATION) { client.initiateCIBA(CIBAInitiateOptions(loginHint = "a")) }
        }

    @Test
    fun `a refused CIBA request carries the OAuth error`() =
        runTest {
            client.initialize(config, storage)
            fake.on("GET", discovery, metadata(""""backchannel_authentication_endpoint":"$BASE/oauth2/bc-authorize""""))
            fake.on("POST", "$BASE/oauth2/bc-authorize", response(400, """{"error":"unknown_user_id"}"""))

            val error = assertFails(ThunderIDErrorCode.INVALID_INPUT) { client.initiateCIBA(CIBAInitiateOptions(loginHint = "a")) }
            assertEquals("unknown_user_id", error.serverError?.code)
        }

    @Test
    fun `pollCIBA waits the interval, slows down when asked, and stores the tokens`() =
        runTest {
            client.initialize(config, storage)
            fake.on(
                "POST",
                token,
                response(400, """{"error":"authorization_pending"}"""),
                response(400, """{"error":"slow_down"}"""),
                response(200, """{"access_token":"ciba-token","token_type":"Bearer","refresh_token":"r"}"""),
            )

            val tokens = client.pollCIBA("req-1", 2)

            assertEquals("ciba-token", tokens.accessToken)
            assertEquals("ciba-token", store.accessToken())
            // 2s, 2s, then 7s after the slow_down.
            assertEquals(11_000L, currentTime)
            val body = form(fake.to(token).first().body)
            assertEquals("urn:openid:params:grant-type:ciba", body["grant_type"])
            assertEquals("req-1", body["auth_req_id"])
            assertEquals("tv-app", body["client_id"])
        }

    @Test
    fun `pollCIBA ends when the user denies the request`() =
        runTest {
            client.initialize(config, storage)
            fake.on("POST", token, response(400, """{"error":"access_denied"}"""))

            val error = assertFails(ThunderIDErrorCode.AUTHENTICATION_FAILED) { client.pollCIBA("req-1", 1) }
            assertEquals("access_denied", error.serverError?.code)
            assertNull(store.accessToken())
        }

    @Test
    fun `CIBA and device authorization are refused when sign-in runs on a backend`() =
        runTest {
            client.initialize(backendConfig(), storage)
            assertFails(ThunderIDErrorCode.UNSUPPORTED_OPERATION) { client.initiateCIBA(CIBAInitiateOptions(loginHint = "a")) }
            assertFails(ThunderIDErrorCode.UNSUPPORTED_OPERATION) { client.startDeviceAuthorization() }
            assertFails(ThunderIDErrorCode.UNSUPPORTED_OPERATION) { client.verifyDeviceCode("ABCD") }
        }

    // ── Device side ────────────────────────────────────────────────────────

    @Test
    fun `startDeviceAuthorization posts to device-authorize and never prints the device code`() =
        runTest {
            client.initialize(config, storage)
            fake.on(
                "POST",
                "$BASE/oauth2/device-authorize",
                response(
                    200,
                    """{"device_code":"secret-device-code","user_code":"BCDF-GHJK","verification_uri":"$BASE/gate/device",
                    "verification_uri_complete":"$BASE/gate/device?user_code=BCDF-GHJK","expires_in":600,"interval":5}""",
                ),
            )

            val authorization = client.startDeviceAuthorization()

            assertEquals("secret-device-code", authorization.deviceCode)
            assertEquals("BCDF-GHJK", authorization.userCode)
            assertEquals("$BASE/gate/device?user_code=BCDF-GHJK", authorization.verificationUriComplete)
            assertEquals(600, authorization.expiresIn)
            assertFalse(authorization.toString().contains("secret-device-code"))
            assertTrue(authorization.toString().contains("BCDF-GHJK"))
            val body = form(fake.to("$BASE/oauth2/device-authorize").single().body)
            assertEquals(mapOf("client_id" to "tv-app", "scope" to "openid profile"), body)
        }

    @Test
    fun `startDeviceAuthorization uses the endpoint the metadata names`() =
        runTest {
            client.initialize(config, storage)
            fake.on("GET", discovery, metadata(""""device_authorization_endpoint":"$BASE/custom/device""""))
            fake.on(
                "POST",
                "$BASE/custom/device",
                response(200, """{"device_code":"d","user_code":"U","verification_uri":"$BASE/v"}"""),
            )

            val authorization = client.startDeviceAuthorization()

            assertEquals(5, authorization.interval)
        }

    @Test
    fun `pollDeviceAuthorization polls the device code grant and stores the tokens`() =
        runTest {
            client.initialize(config, storage)
            fake.on(
                "POST",
                token,
                response(400, """{"error":"authorization_pending"}"""),
                response(200, """{"access_token":"tv-token","token_type":"Bearer"}"""),
            )

            client.pollDeviceAuthorization(DeviceAuthorization("dc", "U", "$BASE/v", null, 600, 5))

            assertEquals("tv-token", store.accessToken())
            assertEquals(10_000L, currentTime)
            val body = form(fake.to(token).first().body)
            assertEquals("urn:ietf:params:oauth:grant-type:device_code", body["grant_type"])
            assertEquals("dc", body["device_code"])
        }

    @Test
    fun `pollDeviceAuthorization ends when the code expires`() =
        runTest {
            client.initialize(config, storage)
            fake.on("POST", token, response(400, """{"error":"expired_token"}"""))

            val error = assertFails(ThunderIDErrorCode.AUTHENTICATION_FAILED) { client.pollDeviceAuthorization("dc", 5) }
            assertEquals("expired_token", error.serverError?.code)
        }

    // ── User side ──────────────────────────────────────────────────────────

    @Test
    fun `verifyDeviceCode starts the device grant flow with the user code`() =
        runTest {
            client.initialize(config, storage)
            fake.on(
                "POST",
                "$BASE/flow/execute",
                response(200, """{"executionId":"e-1","flowStatus":"INCOMPLETE","data":{"inputs":[{"identifier":"username"}]}}"""),
            )

            val step = client.verifyDeviceCode("BCDF-GHJK")

            assertEquals("e-1", step.flowId)
            val body = JsonParser.parseString(fake.requests.single().body).asJsonObject
            assertEquals("DEVICE_GRANT", body["flowType"].asString)
            assertEquals("app-id", body["applicationId"].asString)
            assertEquals("BCDF-GHJK", body["inputs"].asJsonObject["user_code"].asString)
            assertFalse(body.has("ouId"))
        }

    @Test
    fun `a completed device grant flow relays its assertion and never signs the app in`() =
        runTest {
            client.initialize(config, storage)
            fake.on(
                "POST",
                "$BASE/flow/execute",
                response(
                    200,
                    """{"flowStatus":"COMPLETE","assertion":"device-assertion",
                    "data":{"additionalData":{"authId":"auth-1","callbackType":"device_code"}}}""",
                ),
            )
            fake.on("POST", "$BASE/oauth2/auth/callback", response(200, """{"status":"OK"}"""))

            val done = client.verifyDeviceCode(EmbeddedSignInPayload(flowId = "e-1", actionId = "approve"))

            assertEquals(FlowStatus.COMPLETE, done.flowStatus)
            val submit = JsonParser.parseString(fake.to("$BASE/flow/execute").single().body).asJsonObject
            assertEquals("e-1", submit["executionId"].asString)
            assertEquals("approve", submit["action"].asString)
            val relay = JsonParser.parseString(fake.to("$BASE/oauth2/auth/callback").single().body).asJsonObject
            assertEquals("device-assertion", relay["assertion"].asString)
            assertEquals("auth-1", relay["authId"].asString)
            assertEquals("device_code", relay["type"].asString)
            assertNull(store.accessToken())
        }

    @Test
    fun `a denied device grant flow relays the error assertion and accepts access_denied`() =
        runTest {
            client.initialize(config, storage)
            fake.on(
                "POST",
                "$BASE/flow/execute",
                response(
                    200,
                    """{"flowStatus":"ERROR","errorAssertion":"denied","error":{"message":{"defaultValue":"Denied"}},
                    "data":{"additionalData":{"authId":"auth-1","callbackType":"device_code"}}}""",
                ),
            )
            fake.on("POST", "$BASE/oauth2/auth/callback", response(400, """{"error":"access_denied"}"""))

            val done = client.verifyDeviceCode(EmbeddedSignInPayload(flowId = "e-1"))

            assertEquals(FlowStatus.ERROR, done.flowStatus)
            assertEquals("Denied", done.failureReason)
            assertEquals(
                "denied",
                JsonParser.parseString(fake.to("$BASE/oauth2/auth/callback").single().body).asJsonObject["assertion"].asString,
            )
        }

    @Test
    fun `a device grant flow refused with an error assertion is relayed and returned as an error step`() =
        runTest {
            client.initialize(config, storage)
            fake.on(
                "POST",
                "$BASE/flow/execute",
                response(400, """{"errorAssertion":"expired","data":{"additionalData":{"authId":"a","callbackType":"device_code"}}}"""),
            )
            fake.on("POST", "$BASE/oauth2/auth/callback", response(200, """{"status":"OK"}"""))

            assertEquals(FlowStatus.ERROR, client.verifyDeviceCode(EmbeddedSignInPayload(flowId = "e-1")).flowStatus)

            fake.on("POST", "$BASE/flow/execute", response(400, """{"code":"FES-1001"}"""))
            val error = assertFails(ThunderIDErrorCode.INVALID_INPUT) { client.verifyDeviceCode(EmbeddedSignInPayload(flowId = "e-1")) }
            assertEquals("FES-1001", error.serverError?.code)
        }

    @Test
    fun `a finished device grant flow without callback data is an error`() =
        runTest {
            client.initialize(config, storage)
            fake.on("POST", "$BASE/flow/execute", response(200, """{"flowStatus":"COMPLETE","assertion":"x"}"""))

            assertFails(ThunderIDErrorCode.SERVER_ERROR) { client.verifyDeviceCode(EmbeddedSignInPayload(flowId = "e-1")) }
        }

    // ── Pushed authorization requests ──────────────────────────────────────

    @Test
    fun `signIn pushes the request when the app asks and redirects with the request_uri`() =
        runTest {
            client.initialize(config.copy(pushedAuthorizationRequests = true), storage)
            fake.on(
                "POST",
                "$BASE/oauth2/par",
                response(201, """{"request_uri":"urn:ietf:params:oauth:request_uri:abc","expires_in":60}"""),
            )

            val url = client.signIn(SignInOptions(prompt = "login"))

            assertTrue(url.startsWith("$BASE/oauth2/authorize?"))
            assertEquals(mapOf("client_id" to "tv-app", "request_uri" to "urn:ietf:params:oauth:request_uri:abc"), queryOf(url))
            val pushed = form(fake.to("$BASE/oauth2/par").single().body)
            assertEquals("code", pushed["response_type"])
            assertEquals("tv-app", pushed["client_id"])
            assertEquals(REDIRECT_URI, pushed["redirect_uri"])
            assertEquals("openid profile", pushed["scope"])
            assertEquals("S256", pushed["code_challenge_method"])
            assertEquals("login", pushed["prompt"])
        }

    @Test
    fun `signIn pushes when the server requires it, to the endpoint it names, and bounds it to a unit`() =
        runTest {
            client.initialize(config, storage)
            fake.on(
                "GET",
                discovery,
                metadata(""""require_pushed_authorization_requests":true,"pushed_authorization_request_endpoint":"$BASE/par2""""),
            )
            fake.on("POST", "$BASE/par2", response(201, """{"request_uri":"urn:x"}"""))

            val url = client.signIn(SignInOptions(organizationUnitId = "ou-1"))

            assertTrue(url, url.startsWith("$BASE/ou/ou-1/oauth2/authorize?client_id=tv-app&request_uri=urn%3Ax"))
        }

    @Test
    fun `signIn does not push when neither the app nor the server asks`() =
        runTest {
            client.initialize(config, storage)
            fake.on("GET", discovery, metadata(""""require_pushed_authorization_requests":false"""))

            val url = client.signIn()

            assertTrue(url.startsWith("$BASE/oauth2/authorize?response_type=code&client_id=tv-app"))
            assertTrue(fake.to("$BASE/oauth2/par").isEmpty())
        }

    @Test
    fun `a refused push fails sign-in, and buildSignInUrl cannot push`() =
        runTest {
            client.initialize(config.copy(pushedAuthorizationRequests = true), storage)
            fake.on("POST", "$BASE/oauth2/par", response(400, """{"error":"invalid_request"}"""))

            assertFails(ThunderIDErrorCode.INVALID_INPUT) { client.signIn() }
            assertFails(ThunderIDErrorCode.UNSUPPORTED_OPERATION) { client.buildSignInUrl() }
        }
}
