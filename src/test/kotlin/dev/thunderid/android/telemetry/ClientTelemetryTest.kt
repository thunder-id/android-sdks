// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.telemetry

import com.google.gson.JsonArray
import com.google.gson.JsonParser
import dev.thunderid.android.EmbeddedFlowRequestConfig
import dev.thunderid.android.EmbeddedSignInPayload
import dev.thunderid.android.FlowType
import dev.thunderid.android.IAMException
import dev.thunderid.android.InMemoryStorageAdapter
import dev.thunderid.android.TelemetryConfig
import dev.thunderid.android.TelemetryFormat
import dev.thunderid.android.TelemetryToken
import dev.thunderid.android.ThunderIDClient
import dev.thunderid.android.ThunderIDConfig
import dev.thunderid.android.ThunderIDErrorCode
import dev.thunderid.android.ThunderIDHttpRequest
import dev.thunderid.android.ThunderIDHttpResponse
import dev.thunderid.android.TokenResponse
import dev.thunderid.android.api.account.jwt
import dev.thunderid.android.api.management.CreateGroupRequest
import dev.thunderid.android.backend.API
import dev.thunderid.android.backend.FakeBackend
import dev.thunderid.android.backend.PROXY
import dev.thunderid.android.backend.backendConfig
import dev.thunderid.android.backend.response
import dev.thunderid.android.backend.signIn
import dev.thunderid.android.token.TokenStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

private const val BASE = "https://id.example.com"
private const val SAME_ORIGIN_ENDPOINT = "$BASE/telemetry"
private const val OTHER_ENDPOINT = "https://collector.example.com/v1/logs"

@OptIn(ExperimentalCoroutinesApi::class)
class ClientTelemetryTest {
    private val fake = FakeBackend()
    private val storage = InMemoryStorageAdapter()
    private val store = TokenStore(storage)

    private fun config(
        endpoint: String = SAME_ORIGIN_ENDPOINT,
        token: TelemetryToken = TelemetryToken.App,
        vendor: String = ThunderIDConfig.DEFAULT_VENDOR,
    ) = ThunderIDConfig(
        baseUrl = BASE,
        clientId = "app",
        applicationId = "app-id",
        vendor = vendor,
        telemetry = TelemetryConfig(endpoint = endpoint, format = TelemetryFormat.JSON, token = token),
    )

    private suspend fun TestScope.client(config: ThunderIDConfig = config()): ThunderIDClient =
        ThunderIDClient(fake, backgroundScope).apply { initialize(config, storage) }

    private fun TestScope.sent(endpoint: String = SAME_ORIGIN_ENDPOINT): List<ThunderIDHttpRequest> {
        runCurrent()
        return fake.to(endpoint)
    }

    private fun ThunderIDHttpRequest.events(): JsonArray = JsonParser.parseString(body).asJsonArray

    private fun ThunderIDHttpRequest.names(): List<String> = events().map { it.asJsonObject["name"].asString }

    private fun signedIn(
        sub: String = "u1",
        authTime: Long = 1_700_000_000,
    ) {
        val idToken = jwt("sub" to sub, "auth_time" to authTime, "ouId" to "ou-1")
        store.save(TokenResponse(accessToken = "access-1", tokenType = "Bearer", idToken = idToken, expiresIn = 3600))
    }

    private fun completeFlow(assertion: String) =
        fake.on("POST", "$BASE/flow/execute", response(200, """{"flowStatus":"COMPLETE","assertion":"$assertion"}"""))

    // Token rules

    @Test
    fun `an endpoint on the origin of baseUrl gets the app's access token`() =
        runTest {
            val client = client()
            signedIn()
            client.track("thing.happen")
            client.flushTelemetry()
            val request = sent().single()
            assertEquals("Bearer access-1", request.headers["Authorization"])
            assertEquals("POST", request.method)
            assertEquals(listOf("thing.happen"), request.names())
            val attributes = request.events()[0].asJsonObject.getAsJsonObject("attributes")
            assertEquals("u1", attributes.getAsJsonObject("user")["id"].asString)
            assertEquals("ou-1", attributes.getAsJsonObject("organization")["id"].asString)
        }

    @Test
    fun `an endpoint on any other origin gets no token`() =
        runTest {
            val client = client(config(endpoint = OTHER_ENDPOINT))
            signedIn()
            client.track("thing.happen")
            client.flushTelemetry()
            assertNull(sent(OTHER_ENDPOINT).single().headers["Authorization"])
        }

    @Test
    fun `a configured token is the only token sent, and none sends none`() =
        runTest {
            val client = client(config(token = TelemetryToken.Value("ingest-key")))
            signedIn()
            client.track("thing.happen")
            client.flushTelemetry()
            assertEquals("Bearer ingest-key", sent().single().headers["Authorization"])
            assertFalse(TelemetryToken.Value("ingest-key").toString().contains("ingest-key"))

            val none = ThunderIDClient(fake, backgroundScope)
            none.initialize(config(token = TelemetryToken.None), InMemoryStorageAdapter())
            none.track("thing.again")
            none.flushTelemetry()
            assertNull(sent().last().headers["Authorization"])
        }

    @Test
    fun `an app whose sign-in runs on a backend sends no token`() =
        runTest {
            val config = backendConfig().copy(telemetry = TelemetryConfig(endpoint = "$API/telemetry", format = TelemetryFormat.JSON))
            val client = ThunderIDClient(fake, backgroundScope)
            signIn(client, fake, storage, config)
            client.track("thing.happen")
            client.flushTelemetry()
            assertNull(sent("$API/telemetry").single().headers["Authorization"])
        }

    // Off

    @Test
    fun `without an HTTPS endpoint telemetry is off and initialization still succeeds`() =
        runTest {
            listOf("http://collector.example.com/v1/logs", "/telemetry", " ").forEach { endpoint ->
                val client = ThunderIDClient(fake, backgroundScope)
                assertTrue(client.initialize(config(endpoint = endpoint), InMemoryStorageAdapter()))
                client.track("thing.happen")
                client.trackPageView("/home")
                client.trackLink("https://docs.example.com")
                client.flushTelemetry()
            }
            runCurrent()
            assertTrue(fake.requests.isEmpty())
        }

    @Test
    fun `without telemetry the operations do nothing, even before initialization`() =
        runTest {
            val client = ThunderIDClient(fake, backgroundScope)
            client.track("thing.happen")
            client.flushTelemetry()
            client.initialize(ThunderIDConfig(baseUrl = BASE, clientId = "app"), storage)
            client.track("thing.happen")
            client.trackPageView("/home")
            client.flushTelemetry()
            runCurrent()
            assertTrue(fake.requests.isEmpty())
        }

    // API events through the SDK's HTTP client

    @Test
    fun `a management call through the SDK's HTTP client is reported, on success and on failure`() =
        runTest {
            val client = client()
            signedIn()
            fake.on("POST", "$BASE/groups", response(201, """{"id":"g1","name":"Admins","ouId":"ou-1"}"""))
            client.groups.create(CreateGroupRequest(name = "Admins", ouId = "ou-1"))
            fake.on("POST", "$BASE/groups", response(409, """{"code":"GRP-1004","message":"A group named Admins exists"}"""))
            try {
                client.groups.create(CreateGroupRequest(name = "Admins", ouId = "ou-1"))
                fail("Expected IAMException")
            } catch (_: IAMException) {
            }
            fake.on("GET", "$BASE/groups?limit=30&offset=0", response(200, """{"groups":[]}"""))
            runCatching { client.groups.list() }
            client.flushTelemetry()

            val request = sent().single()
            assertEquals(listOf("group.create", "group.create-failed"), request.names())
            val failed = request.events()[1].asJsonObject.getAsJsonObject("attributes")
            assertEquals(
                JsonParser.parseString("""{"resource_type":"group","status":"409","error_code":"GRP-1004"}"""),
                failed["data"],
            )
            assertEquals("/groups", failed["route"].asString)
            assertFalse(request.body!!.contains("Admins"))
        }

    @Test
    fun `a call is named from the API's X-Telemetry-Event header, read without regard to case`() =
        runTest {
            val client = client()
            signedIn()
            val created = """{"id":"g1","name":"Admins","ouId":"ou-1"}"""
            fake.on("POST", "$BASE/groups", ThunderIDHttpResponse(201, created, mapOf("x-TELEMETRY-event" to "group.provision")))
            client.groups.create(CreateGroupRequest(name = "Admins", ouId = "ou-1"))
            fake.on("POST", "$BASE/groups", ThunderIDHttpResponse(201, created, mapOf("X-Telemetry-Event" to "Not A Name")))
            client.groups.create(CreateGroupRequest(name = "Admins", ouId = "ou-1"))
            client.flushTelemetry()

            assertEquals(listOf("group.provision", "group.create"), sent().single().names())
        }

    @Test
    fun `an observer that fails never changes the call`() =
        runTest {
            val client = client(config().copy(telemetry = config().telemetry!!.copy(context = mapOf("a" to "b"))))
            signedIn()
            fake.on("POST", "$BASE/groups", response(201, """{"id":"g1","name":"Admins","ouId":"ou-1"}"""))
            assertEquals("g1", client.groups.create(CreateGroupRequest(name = "Admins", ouId = "ou-1")).id)
        }

    @Test
    fun `a backend sign-in and a call through the backend's proxy are reported once, at its API URL`() =
        runTest {
            val config = backendConfig().copy(telemetry = TelemetryConfig(endpoint = "$API/telemetry", format = TelemetryFormat.JSON))
            val client = ThunderIDClient(fake, backgroundScope)
            signIn(client, fake, storage, config)
            fake.on("POST", "$PROXY/groups", response(201, """{"id":"g1","name":"Admins","ouId":"ou-1"}"""))
            client.groups.create(CreateGroupRequest(name = "Admins", ouId = "ou-1"))
            fake.on("DELETE", "$PROXY/roles/r1", response(204))
            client.httpRequest(ThunderIDHttpRequest("DELETE", "$API/roles/r1", emptyMap(), null))
            client.flushTelemetry()
            assertEquals(listOf("auth.sign-in", "group.create", "role.delete"), sent("$API/telemetry").single().names())
        }

    // Auth events

    @Test
    fun `a sign-in is reported once per sign-in session, and not for an organization switch`() =
        runTest {
            val vendor = "acme"
            val client = client(config(vendor = vendor))
            val first = jwt("sub" to "u1", "auth_time" to 1_700_000_000, "ouId" to "ou-1")
            completeFlow(first)
            val request = EmbeddedFlowRequestConfig("app-id", FlowType.AUTHENTICATION)
            client.signIn(EmbeddedSignInPayload(), request)
            assertEquals("u1:1700000000", storage.retrieve("$vendor.telemetry_sign_in"))
            assertNull(storage.retrieve("${ThunderIDConfig.DEFAULT_VENDOR}.telemetry_sign_in"))

            // The same session again, as a repeated completion would be.
            client.signIn(EmbeddedSignInPayload(), request)
            // A switch of organization signs in to the unit, which is not a new sign-in.
            fake.on("GET", "$BASE/oauth2/userinfo", response(200, """{"sub":"u1"}"""))
            completeFlow(jwt("sub" to "u1", "auth_time" to 1_700_000_500, "ouId" to "ou-3"))
            client.switchOrganizationUnit("ou-3")
            client.flushTelemetry()

            assertEquals(listOf("auth.sign-in"), sent().single().names())
        }

    @Test
    fun `a new sign-in session after a sign-out is reported again, and the sign-out is sent with the token first`() =
        runTest {
            val client = client()
            completeFlow(jwt("sub" to "u1", "auth_time" to 1_700_000_000))
            val request = EmbeddedFlowRequestConfig("app-id", FlowType.AUTHENTICATION)
            client.signIn(EmbeddedSignInPayload(), request)
            val token = store.accessToken()
            client.signOut()

            val signOut = sent().single()
            assertEquals(listOf("auth.sign-in", "auth.sign-out"), signOut.names())
            assertEquals("Bearer $token", signOut.headers["Authorization"])
            assertNull(storage.retrieve("${ThunderIDConfig.DEFAULT_VENDOR}.telemetry_sign_in"))

            completeFlow(jwt("sub" to "u1", "auth_time" to 1_700_000_900))
            client.signIn(EmbeddedSignInPayload(), request)
            client.flushTelemetry()
            assertEquals(listOf("auth.sign-in"), sent().last().names())
        }

    @Test
    fun `a failed sign-in carries the error code only`() =
        runTest {
            val client = client()
            fake.on(
                "POST",
                "$BASE/flow/execute",
                response(200, """{"flowStatus":"ERROR","error":{"code":"FES-1007","message":{"defaultValue":"Wrong password for bob"}}}"""),
            )
            client.signIn(EmbeddedSignInPayload(), EmbeddedFlowRequestConfig("app-id", FlowType.AUTHENTICATION))
            fake.on("POST", "$BASE/flow/execute", response(500, """{"code":"SYS-1000","message":"boom"}"""))
            runCatching { client.signIn(EmbeddedSignInPayload(), EmbeddedFlowRequestConfig("app-id", FlowType.AUTHENTICATION)) }
            client.flushTelemetry()

            val events = sent().single().events()
            assertEquals(2, events.size())
            events.forEach { assertEquals("auth.sign-in-failed", it.asJsonObject["name"].asString) }
            assertEquals(
                JsonParser.parseString("""{"error_code":"FES-1007"}"""),
                events[0].asJsonObject.getAsJsonObject("attributes")["data"],
            )
            assertEquals(
                JsonParser.parseString("""{"error_code":"SYS-1000"}"""),
                events[1].asJsonObject.getAsJsonObject("attributes")["data"],
            )
            assertFalse(sent().single().body!!.contains("bob"))
        }

    @Test
    fun `a sign-in flow that ends in an error sends its error code only when well formed`() =
        runTest {
            val client = client()
            fake.on(
                "POST",
                "$BASE/flow/execute",
                response(200, """{"flowStatus":"ERROR","error":{"code":"wrong password for bob","message":{"defaultValue":"x"}}}"""),
            )
            client.signIn(EmbeddedSignInPayload(), EmbeddedFlowRequestConfig("app-id", FlowType.AUTHENTICATION))
            client.flushTelemetry()

            val event =
                sent()
                    .single()
                    .events()
                    .single()
                    .asJsonObject
            assertEquals("auth.sign-in-failed", event["name"].asString)
            assertEquals(JsonParser.parseString("{}"), event.getAsJsonObject("attributes")["data"])
            assertFalse(sent().single().body!!.contains("bob"))
        }

    @Test
    fun `a silent sign-in that needs no login page is reported once per sign-in session`() =
        runTest {
            val client = client()
            val request = EmbeddedFlowRequestConfig("app-id", FlowType.AUTHENTICATION)
            // The flow completes on its first step, from the session the server already holds.
            completeFlow(jwt("sub" to "u1", "sid" to "s-1"))
            client.signIn(EmbeddedSignInPayload(), request)
            client.signIn(EmbeddedSignInPayload(), request)
            completeFlow(jwt("sub" to "u1", "sid" to "s-2"))
            client.signIn(EmbeddedSignInPayload(), request)
            client.flushTelemetry()

            assertEquals(listOf("auth.sign-in", "auth.sign-in"), sent().single().names())
        }

    @Test
    fun `a refused refresh reports the session as expired once`() =
        runTest {
            val client = client()
            store.save(
                TokenResponse(
                    accessToken = "old",
                    refreshToken = "refresh",
                    tokenType = "Bearer",
                    expiresIn = 0,
                    idToken = jwt("sub" to "u1", "auth_time" to 1),
                ),
            )
            fake.on("POST", "$BASE/oauth2/token", response(400, """{"error":"invalid_grant"}"""))
            repeat(2) {
                try {
                    client.getAccessToken()
                    fail("Expected IAMException")
                } catch (e: IAMException) {
                    assertEquals(ThunderIDErrorCode.INVALID_GRANT, e.code)
                }
            }
            client.flushTelemetry()
            assertEquals(listOf("auth.session-expired"), sent().single().names())
        }

    // Links and pages

    @Test
    fun `a link is reported with its host and path only, and a page with its pattern`() =
        runTest {
            val client = client()
            client.trackLink("https://docs.example.com:8443/guides/start?token=secret#top")
            client.trackLink("tel:+15550100")
            client.trackPageView("/applications/{id}")
            client.flushTelemetry()

            val request = sent().single()
            assertEquals(listOf("link.open", "application.detail-view"), request.names())
            assertEquals(
                JsonParser.parseString("""{"host":"docs.example.com:8443","path":"/guides/{id}"}"""),
                request.events()[0].asJsonObject.getAsJsonObject("attributes")["data"],
            )
            assertEquals(
                "/applications/{id}",
                request
                    .events()[1]
                    .asJsonObject
                    .getAsJsonObject("attributes")["route"]
                    .asString,
            )
            assertFalse(request.body!!.contains("secret"))
        }

    @Test
    fun `a replaced telemetry stops its flush timer and drops the events it held`() =
        runTest {
            var allowed = false
            var checks = 0
            val telemetry =
                TelemetryConfig(
                    endpoint = SAME_ORIGIN_ENDPOINT,
                    format = TelemetryFormat.JSON,
                    consent = {
                        checks += 1
                        allowed
                    },
                )
            val client = client(config().copy(telemetry = telemetry, storage = storage))
            client.track("thing.before")
            advanceTimeBy(Telemetry.FLUSH_INTERVAL_MS + 1)
            runCurrent()
            assertTrue(checks > 0)

            // Initializing again replaces the telemetry. The one replaced no longer wakes up to check consent.
            assertTrue(client.reInitialize())
            val checksAtReplace = checks
            advanceTimeBy(Telemetry.FLUSH_INTERVAL_MS * 10)
            runCurrent()
            assertEquals(checksAtReplace, checks)

            // The new one starts fresh: what the old one held is never sent.
            allowed = true
            client.track("thing.after")
            client.flushTelemetry()
            assertEquals(listOf("thing.after"), sent().single().names())
        }

    @Test
    fun `requests to the telemetry endpoint are never API events`() =
        runTest {
            // POST /events would be `event.create` if the SDK's HTTP client observed it.
            val endpoint = "$BASE/events"
            val client = client(config(endpoint = endpoint))
            signedIn()
            client.track("thing.happen")
            client.flushTelemetry()
            runCurrent()
            client.track("thing.again")
            client.flushTelemetry()
            val names = sent(endpoint).flatMap { it.names() }
            assertEquals(listOf("thing.happen", "thing.again"), names)
        }

    @Test
    fun `an absent organization claim leaves the organization out`() =
        runTest {
            val client = client()
            store.save(TokenResponse(accessToken = "a", tokenType = "Bearer", idToken = jwt("sub" to "u1", "org_id" to "o1")))
            client.track("thing.happen")
            client.flushTelemetry()
            val attributes =
                sent()
                    .single()
                    .events()[0]
                    .asJsonObject
                    .getAsJsonObject("attributes")
            assertEquals("u1", attributes.getAsJsonObject("user")["id"].asString)
            assertFalse(attributes.has("organization"))
        }
}
