// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.telemetry

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.thunderid.android.TelemetryApiEventsConfig
import dev.thunderid.android.TelemetryBatch
import dev.thunderid.android.TelemetryConfig
import dev.thunderid.android.TelemetryFormat
import dev.thunderid.android.TelemetryKindConfig
import dev.thunderid.android.TelemetryMapping
import dev.thunderid.android.TelemetryParentActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

private const val USER = "11111111-1111-4111-8111-111111111111"
private const val ORG = "22222222-2222-4222-8222-222222222222"

/** A telemetry that sends `json` to a list, for reading back what each batch held. */
internal class Recorder(
    config: TelemetryConfig,
    scope: CoroutineScope,
    identity: () -> TelemetryIdentity = { TelemetryIdentity(USER, ORG) },
    sender: TelemetrySender? = null,
) {
    val batches = CopyOnWriteArrayList<JsonArray>()
    val tokens = CopyOnWriteArrayList<String?>()
    val telemetry =
        Telemetry(
            config,
            TelemetryDependencies(
                apiBases = listOf("https://api.example.com"),
                identity = identity,
                token = { "app-token" },
                sender =
                    sender ?: TelemetrySender { body, token ->
                        batches += JsonParser.parseString(body).asJsonArray
                        tokens += token
                    },
                os = TelemetryOs("Android", "14"),
                scope = scope,
            ),
        )

    fun names(batch: Int = 0): List<String> = batches[batch].map { it.asJsonObject["name"].asString }

    fun event(
        batch: Int,
        index: Int,
    ): JsonObject = batches[batch][index].asJsonObject
}

@OptIn(ExperimentalCoroutinesApi::class)
class TelemetryTest {
    private fun TestScope.recorder(config: TelemetryConfig) = Recorder(config, backgroundScope)

    private fun json(
        prefix: String? = null,
        context: Map<String, String> = emptyMap(),
    ) = TelemetryConfig(endpoint = "https://t.example.com", format = TelemetryFormat.JSON, prefix = prefix, context = context)

    @Test
    fun `reports every kind with the prefix, the ids and the context, batched after the interval`() =
        runTest {
            val recorder = recorder(json(prefix = "acme", context = mapOf("platform" to "Acme")))
            with(recorder.telemetry) {
                page("/applications")
                api("POST", "https://api.example.com/applications", "{}", 201, null)
                api("POST", "https://api.example.com/groups", "{}", 409, """{"code":"GRP-1004","message":"Group x exists"}""")
                auth(TelemetryAuthAction.SIGN_IN)
                link("https://docs.example.com/guides/start?ref=x#top")
                track("application.template-pick", mapOf("type" to "spa"))
            }
            advanceTimeBy(Telemetry.FLUSH_INTERVAL_MS - 1)
            runCurrent()
            assertTrue(recorder.batches.isEmpty())
            advanceTimeBy(2)
            runCurrent()

            assertEquals(1, recorder.batches.size)
            assertEquals(
                listOf(
                    "acme.application.list-view",
                    "acme.application.create",
                    "acme.group.create-failed",
                    "acme.auth.sign-in",
                    "acme.link.open",
                    "acme.application.template-pick",
                ),
                recorder.names(),
            )
            val failed = recorder.event(0, 2).getAsJsonObject("attributes")
            assertEquals(
                JsonParser.parseString("""{"resource_type":"group","status":"409","error_code":"GRP-1004"}"""),
                failed["data"],
            )
            assertEquals("/groups", failed["route"].asString)
            val link = recorder.event(0, 4).getAsJsonObject("attributes")
            assertEquals(JsonParser.parseString("""{"host":"docs.example.com","path":"/guides/{id}"}"""), link["data"])
            val page = recorder.event(0, 0)
            assertEquals(USER, page.getAsJsonObject("attributes").getAsJsonObject("user")["id"].asString)
            assertEquals(ORG, page.getAsJsonObject("attributes").getAsJsonObject("organization")["id"].asString)
            assertEquals("Acme", page.getAsJsonObject("context")["platform"].asString)
            assertEquals("page", page["kind"].asString)
            assertEquals("app-token", recorder.tokens.single())
        }

    @Test
    fun `sends a full batch at once`() =
        runTest {
            val recorder = recorder(json())
            repeat(Telemetry.BATCH_SIZE) { recorder.telemetry.track("thing.happen") }
            runCurrent()
            assertEquals(1, recorder.batches.size)
            assertEquals(Telemetry.BATCH_SIZE, recorder.batches[0].size())
        }

    @Test
    fun `leaves out excluded and disabled kinds, and renames`() =
        runTest {
            val recorder =
                recorder(
                    json().copy(
                        apiEvents = TelemetryApiEventsConfig(exclude = listOf("role.*", "https://api.example.com/users/**")),
                        authEvents = TelemetryKindConfig(exclude = listOf("session-expired"), names = mapOf("sign-out" to "session.end")),
                        links = TelemetryKindConfig(names = mapOf("https://example.com/pricing" to "plan.upgrade-click")),
                        pages =
                            TelemetryKindConfig(
                                exclude = listOf("/settings/**"),
                                names = mapOf("/home" to "organization.overview-view"),
                            ),
                    ),
                )
            with(recorder.telemetry) {
                page("/settings/general/advanced")
                page("/home")
                api("DELETE", "https://api.example.com/roles/r1", null, 204, null)
                api("POST", "https://api.example.com/users/u1/credentials?x=1", "{}", 201, null)
                api("POST", "https://api.example.com/groups", "{}", 201, null)
                auth(TelemetryAuthAction.SESSION_EXPIRED)
                auth(TelemetryAuthAction.SIGN_OUT)
                link("https://example.com/pricing")
                link("mailto:someone@example.com")
                link("intent://scan/#Intent;scheme=zxing;end")
            }
            recorder.telemetry.flush()
            runCurrent()
            assertEquals(
                listOf("organization.overview-view", "group.create", "session.end", "plan.upgrade-click"),
                recorder.names(),
            )

            val off =
                recorder(json().copy(pages = TelemetryKindConfig(enabled = false), apiEvents = TelemetryApiEventsConfig(enabled = false)))
            off.telemetry.page("/home")
            off.telemetry.api("POST", "https://api.example.com/groups", "{}", 201, null)
            assertNull(off.telemetry.flush())
            runCurrent()
            assertTrue(off.batches.isEmpty())
        }

    @Test
    fun `reports no read and no call outside the watched APIs`() =
        runTest {
            val recorder = recorder(json().copy(apiEvents = TelemetryApiEventsConfig(apis = listOf("https://env.example.com/v1"))))
            with(recorder.telemetry) {
                api("GET", "https://api.example.com/applications", null, 200, null)
                api("POST", "https://other.example.com/applications", "{}", 201, null)
                api("POST", "https://env.example.com/v1/promotions", "{}", null, null)
            }
            recorder.telemetry.flush()
            runCurrent()
            assertEquals(listOf("promotion.create-failed"), recorder.names())
            assertEquals(
                JsonParser.parseString("""{"resource_type":"promotion","status":"network"}"""),
                recorder.event(0, 0).getAsJsonObject("attributes")["data"],
            )
        }

    @Test
    fun `names an API event from config first, then from the API, then by the built-in rules`() =
        runTest {
            val names = mapOf("POST /import/*" to "configuration.import", "role.delete" to "role.remove")
            val recorder = recorder(json().copy(apiEvents = TelemetryApiEventsConfig(names = names)))
            with(recorder.telemetry) {
                api("POST", "https://api.example.com/import/managed", "{}", 200, null, "import.run")
                api("POST", "https://api.example.com/applications/a1/invitations", "{}", 201, null, "application.invite")
                api("POST", "https://api.example.com/applications/a1/invitations", "{}", 201, null, "Not A Name")
                api("DELETE", "https://api.example.com/roles/r1", null, 204, null)
                api("POST", "https://api.example.com/export", "{}", 200, null)
                api("GET", "https://api.example.com/applications", null, 200, null, "application.list")
            }
            recorder.telemetry.flush()
            runCurrent()
            assertEquals(
                listOf(
                    listOf("configuration.import", "/import/{id}"),
                    listOf("application.invite", "/applications/{id}/invitations"),
                    listOf("invitation.create", "/applications/{id}/invitations"),
                    listOf("role.remove", "/roles/{id}"),
                    listOf("api.export", "/export"),
                ),
                recorder.batches[0].map {
                    val event = it.asJsonObject
                    listOf(event["name"].asString, event.getAsJsonObject("attributes")["route"].asString)
                },
            )
        }

    @Test
    fun `takes every API-specific rule from configuration`() =
        runTest {
            val apiEvents =
                TelemetryApiEventsConfig(
                    exclude = listOf("/oauth2/**", "/**/.search"),
                    names =
                        mapOf(
                            "POST /gateways/*/apply" to "deployment.deploy",
                            "POST /gateways/*/apply ?origin" to "deployment.promote",
                            "POST /promotions" to "deployment.promote",
                        ),
                    parentActions =
                        mapOf(
                            "sharing-policies" to TelemetryParentActions(create = "share", update = "share-update", delete = "unshare"),
                        ),
                    skipWhen = listOf("dryRun", "preview"),
                )
            val recorder = recorder(json().copy(apiEvents = apiEvents))
            val base = "https://api.example.com"
            with(recorder.telemetry) {
                api("POST", "$base/gateways/g1/apply", """{"origin":"g0"}""", 200, null)
                api("POST", "$base/gateways/g1/apply", "{}", 200, null)
                api("POST", "$base/gateways/g1/apply", """{"dryRun":true}""", 200, null)
                api("POST", "$base/promotions", """{"preview":true}""", 200, null)
                api("POST", "$base/promotions", "{}", 200, null)
                api("POST", "$base/applications/a1/sharing-policies", "{}", 200, null)
                api("POST", "$base/oauth2/token", "{}", 200, null)
                api("POST", "$base/scim/v2/Users/.search", "{}", 200, null)
                api("POST", "$base/users/search", "{}", 200, null, "none")
            }
            recorder.telemetry.flush()
            runCurrent()
            assertEquals(
                listOf(
                    listOf("deployment.promote", "/gateways/{id}/apply"),
                    listOf("deployment.deploy", "/gateways/{id}/apply"),
                    listOf("deployment.promote", "/promotions"),
                    listOf("application.share", "/applications/{id}/sharing-policies"),
                ),
                recorder.batches[0].map {
                    val event = it.asJsonObject
                    listOf(event["name"].asString, event.getAsJsonObject("attributes")["route"].asString)
                },
            )
        }

    @Test
    fun `puts configuration before the header, and lets a rename of the header's name apply`() =
        runTest {
            val names = mapOf("role.delete" to "role.remove", "application.invite" to "application.invite-send")
            val recorder = recorder(json().copy(apiEvents = TelemetryApiEventsConfig(names = names)))
            with(recorder.telemetry) {
                api("DELETE", "https://api.example.com/roles/r1", null, 204, null, "role.erase")
                api("POST", "https://api.example.com/applications/a1/invitations", "{}", 201, null, "application.invite")
                api("POST", "https://api.example.com/groups", "{}", 201, null, "none")
            }
            recorder.telemetry.flush()
            runCurrent()
            assertEquals(listOf("role.remove", "application.invite-send"), recorder.names())
        }

    @Test
    fun `excludes by URL, literal path, masked path, rules name or final name, failures included`() =
        runTest {
            val exclude = listOf("group.create", "/roles/r1", "application.invite", "/users/{id}")
            val recorder = recorder(json().copy(apiEvents = TelemetryApiEventsConfig(exclude = exclude)))
            with(recorder.telemetry) {
                api("POST", "https://api.example.com/groups", "{}", 201, null)
                api("POST", "https://api.example.com/groups", "{}", 409, """{"code":"GRP-1004"}""")
                api("DELETE", "https://api.example.com/roles/r1", null, 204, null)
                api("DELETE", "https://api.example.com/roles/r2", null, 204, null)
                api("POST", "https://api.example.com/applications/a1/invitations", "{}", 201, null, "application.invite")
                api("DELETE", "https://api.example.com/users/019a2b3c-4d5e", null, 204, null)
            }
            recorder.telemetry.flush()
            runCurrent()
            assertEquals(listOf("role.delete"), recorder.names())
        }

    @Test
    fun `matches auth exclusions exactly, by action name`() =
        runTest {
            val recorder = recorder(json().copy(authEvents = TelemetryKindConfig(exclude = listOf("sign-*", "sign-out"))))
            with(recorder.telemetry) {
                auth(TelemetryAuthAction.SIGN_IN)
                auth(TelemetryAuthAction.SIGN_OUT)
            }
            recorder.telemetry.flush()
            runCurrent()
            assertEquals(listOf("auth.sign-in"), recorder.names())
        }

    @Test
    fun `accepts no trailing line break in a name or an exact pattern`() =
        runTest {
            assertFalse(TelemetryNames.matches("role.delete\n", "role.delete"))
            val recorder = recorder(json())
            recorder.telemetry.track("item.open\n")
            recorder.telemetry.track("item.open")
            recorder.telemetry.flush()
            runCurrent()
            assertEquals(listOf("item.open"), recorder.names())
        }

    @Test
    fun `reads an API's path below the longest watched base`() =
        runTest {
            val recorder =
                recorder(json().copy(apiEvents = TelemetryApiEventsConfig(apis = listOf("https://api.example.com/admin"))))
            recorder.telemetry.api("POST", "https://api.example.com/admin/widgets", "{}", 201, null)
            recorder.telemetry.flush()
            runCurrent()
            assertEquals(listOf("widget.create"), recorder.names())
            assertEquals("/widgets", recorder.event(0, 0).getAsJsonObject("attributes")["route"].asString)
        }

    @Test
    fun `holds events until consent, then sends them`() =
        runTest {
            var allowed = false
            val recorder = recorder(json().copy(consent = { allowed }))
            recorder.telemetry.page("/home")
            advanceTimeBy(Telemetry.FLUSH_INTERVAL_MS + 1)
            runCurrent()
            assertTrue(recorder.batches.isEmpty())
            allowed = true
            advanceTimeBy(Telemetry.FLUSH_INTERVAL_MS + 1)
            runCurrent()
            assertEquals(listOf("app.home-view"), recorder.names())
        }

    @Test
    fun `keeps at most the newest events while waiting for consent`() =
        runTest {
            var allowed = false
            val recorder = recorder(json().copy(consent = { allowed }))
            repeat(Telemetry.MAX_WAITING + 50) { recorder.telemetry.track("thing.n$it") }
            runCurrent()
            assertTrue(recorder.batches.isEmpty())
            allowed = true
            recorder.telemetry.flush()
            runCurrent()
            assertEquals(Telemetry.MAX_WAITING, recorder.batches.single().size())
            assertEquals("thing.n50", recorder.names().first())
            assertEquals("thing.n${Telemetry.MAX_WAITING + 49}", recorder.names().last())
        }

    @Test
    fun `checks consent before each body, and keeps the bodies not started when it is withdrawn`() =
        runTest {
            var allowed = true
            val started = kotlinx.coroutines.CompletableDeferred<Unit>()
            val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
            val sent = CopyOnWriteArrayList<String>()
            val config =
                TelemetryConfig(
                    endpoint = "https://t.example.com",
                    format = TelemetryFormat.MAPPING,
                    mapping = TelemetryMapping(mapOf("name" to "{{name}}"), batch = TelemetryBatch.EACH),
                    consent = { allowed },
                )
            val recorder =
                Recorder(config, backgroundScope, sender = { body, _ ->
                    sent += JsonParser.parseString(body).asJsonObject["name"].asString
                    if (!started.isCompleted) {
                        started.complete(Unit)
                        gate.await()
                    }
                })
            recorder.telemetry.track("thing.one")
            recorder.telemetry.track("thing.two")
            recorder.telemetry.flush()
            runCurrent()
            assertTrue(started.isCompleted)

            // Withdrawn while the first body is being sent: that send goes on, the second is not started.
            allowed = false
            gate.complete(Unit)
            runCurrent()
            assertEquals(listOf("thing.one"), sent)

            // The second is kept, and goes once consent is given again.
            advanceTimeBy(Telemetry.FLUSH_INTERVAL_MS * 3)
            runCurrent()
            assertEquals(listOf("thing.one"), sent)
            allowed = true
            advanceTimeBy(Telemetry.FLUSH_INTERVAL_MS + 1)
            runCurrent()
            assertEquals(listOf("thing.one", "thing.two"), sent)
        }

    @Test
    fun `sends every body in order while consent holds`() =
        runTest {
            val sent = CopyOnWriteArrayList<String>()
            val config =
                TelemetryConfig(
                    endpoint = "https://t.example.com",
                    format = TelemetryFormat.MAPPING,
                    mapping = TelemetryMapping(mapOf("name" to "{{name}}"), batch = TelemetryBatch.EACH),
                    consent = { true },
                )
            val recorder =
                Recorder(config, backgroundScope, sender = { body, _ ->
                    sent += JsonParser.parseString(body).asJsonObject["name"].asString
                })
            listOf("thing.one", "thing.two", "thing.three").forEach { recorder.telemetry.track(it) }
            recorder.telemetry.flush()
            runCurrent()
            assertEquals(listOf("thing.one", "thing.two", "thing.three"), sent)
        }

    @Test
    fun `a stopped telemetry cancels its timer, drops what is waiting and queues nothing more`() =
        runTest {
            var checks = 0
            val recorder =
                recorder(
                    json().copy(consent = {
                        checks += 1
                        false
                    }),
                )
            recorder.telemetry.page("/home")
            advanceTimeBy(Telemetry.FLUSH_INTERVAL_MS + 1)
            runCurrent()
            assertTrue(checks > 0)
            recorder.telemetry.stop()
            val checksAtStop = checks
            recorder.telemetry.page("/home")
            advanceTimeBy(Telemetry.FLUSH_INTERVAL_MS * 10)
            runCurrent()
            assertEquals(checksAtStop, checks)
            assertNull(recorder.telemetry.flush())
            assertTrue(recorder.batches.isEmpty())
        }

    @Test
    fun `a slow consent function never blocks adding events`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val consent = {
                entered.countDown()
                release.await(10, TimeUnit.SECONDS)
                false
            }
            val recorder = Recorder(json().copy(consent = consent), scope)
            recorder.telemetry.page("/home")
            val flusher = thread { recorder.telemetry.flush() }
            assertTrue(entered.await(5, TimeUnit.SECONDS))

            // While the flush waits on consent, another thread adds an event without waiting for it.
            val tracker = thread { recorder.telemetry.track("thing.one") }
            tracker.join(5_000)
            val added = !tracker.isAlive
            release.countDown()
            flusher.join(5_000)
            assertTrue(added)
        } finally {
            release.countDown()
            scope.cancel()
        }
    }

    @Test
    fun `a consent function that throws counts as no consent`() =
        runTest {
            val recorder = recorder(json().copy(consent = { error("not ready") }))
            recorder.telemetry.page("/home")
            assertNull(recorder.telemetry.flush())
        }

    @Test
    fun `never lets a failed send or a failed identity reach the app`() =
        runTest {
            val recorder =
                Recorder(
                    json(),
                    backgroundScope,
                    identity = { error("no session") },
                    sender = { _, _ -> throw java.io.IOException("down") },
                )
            recorder.telemetry.page("/home")
            recorder.telemetry.flush()?.join()
            recorder.telemetry.page("/home")
            recorder.telemetry.flush()?.join()
        }

    @Test
    fun `reports a link's path with only its first segment, when a plain word, and every later one as an id`() =
        runTest {
            assertEquals("/guides/{id}", Telemetry.linkPath("/guides/getting-started"))
            assertEquals("/users/{id}", Telemetry.linkPath("/users/alice"))
            assertEquals("/a/", Telemetry.linkPath("/a/"))
            assertEquals("/reset/{id}/{id}", Telemetry.linkPath("/reset/eyJhbGciOiJIUzI1NiJ9.abc/confirm"))
            assertEquals("/orders/{id}/", Telemetry.linkPath("/orders/12345/"))
            assertEquals("/", Telemetry.linkPath("/"))
            assertEquals("/docs/{id}/{id}", Telemetry.linkPath("/docs/getting_started/v2"))
            assertEquals("/{id}", Telemetry.linkPath("/hello%20world"))
            assertEquals("/{id}/{id}", Telemetry.linkPath("/12345/guides"))
            assertEquals("/{id}", Telemetry.linkPath("/" + "a".repeat(41)))

            val recorder = recorder(json())
            recorder.telemetry.link("https://example.com/reset/eyJhbGciOiJIUzI1NiJ9.abc/confirm?x=1")
            recorder.telemetry.flush()
            runCurrent()
            assertEquals(
                JsonParser.parseString("""{"host":"example.com","path":"/reset/{id}/{id}"}"""),
                recorder.event(0, 0).getAsJsonObject("attributes")["data"],
            )
        }

    @Test
    fun `drops a tracked event with a malformed name and data values that are not codes`() =
        runTest {
            val recorder = recorder(json(prefix = "acme"))
            with(recorder.telemetry) {
                listOf("Application.Create", "application", "application.", ".create", "app create.x", "a.b_c", "a--b.c")
                    .forEach { track(it) }
                track(
                    "plan.upgrade-click",
                    mapOf(
                        "tier" to "pro",
                        "step" to "2",
                        "email" to "bob@example.com",
                        "url" to "https://example.com",
                        "query" to "a?b",
                        "fragment" to "a#b",
                        "text" to "two words",
                        "long" to "x".repeat(65),
                        "max" to "y".repeat(64),
                    ),
                )
            }
            recorder.telemetry.flush()
            runCurrent()
            assertEquals(listOf("acme.plan.upgrade-click"), recorder.names())
            assertEquals(
                JsonParser.parseString("""{"tier":"pro","step":"2","max":"${"y".repeat(64)}"}"""),
                recorder.event(0, 0).getAsJsonObject("attributes")["data"],
            )
        }

    @Test
    fun `sends one batch at a time and abandons a send that does not finish in time`() =
        runTest {
            var calls = 0
            val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
            val recorder =
                Recorder(json(), backgroundScope, sender = { _, _ ->
                    calls += 1
                    gate.await()
                })
            recorder.telemetry.track("thing.one")
            recorder.telemetry.flush()
            runCurrent()
            recorder.telemetry.track("thing.two")
            assertNull(recorder.telemetry.flush())
            runCurrent()
            assertEquals(1, calls)

            // The stuck send is abandoned after the timeout, and the next batch goes.
            advanceTimeBy(Telemetry.SEND_TIMEOUT_MS + 1)
            runCurrent()
            assertEquals(1, calls)
            advanceTimeBy(Telemetry.FLUSH_INTERVAL_MS)
            runCurrent()
            assertEquals(2, calls)
        }

    @Test
    fun `an event has the documented json shape`() =
        runTest {
            val recorder = Recorder(json(), backgroundScope, identity = { TelemetryIdentity(userId = USER) })
            recorder.telemetry.page("/applications/:id")
            recorder.telemetry.flush()
            runCurrent()
            val event = recorder.event(0, 0)
            assertEquals(listOf("id", "name", "kind", "timestamp", "attributes", "context"), event.keySet().toList())
            assertTrue(Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z$").matches(event["timestamp"].asString))
            val attributes = event.getAsJsonObject("attributes")
            // No organization claim: no organization, and no other claim stands in for it.
            assertEquals(listOf("data", "route", "user", "os"), attributes.keySet().toList())
            assertEquals(JsonObject(), attributes["data"])
        }
}
