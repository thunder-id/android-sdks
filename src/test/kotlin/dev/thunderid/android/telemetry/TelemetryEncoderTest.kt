// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.telemetry

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.thunderid.android.TelemetryBatch
import dev.thunderid.android.TelemetryFormat
import dev.thunderid.android.TelemetryMapping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

internal val SAMPLE_EVENT =
    TelemetryEvent(
        id = "e1",
        kind = TelemetryEventKind.API,
        name = "acme.application.create",
        timestamp = "2026-01-02T03:04:05.000Z",
        route = "/applications",
        data = mapOf("resource_type" to "application"),
        identity = TelemetryIdentity(userId = "not-a-uuid", organizationId = "B3A1C2D4-0000-4000-8000-000000000001"),
        os = TelemetryOs("Android", "14"),
        context = mapOf("platform" to "Acme"),
    )

class TelemetryEncoderTest {
    private fun JsonArray.attribute(key: String): String? =
        map { it.asJsonObject }
            .firstOrNull { it["key"].asString == key }
            ?.getAsJsonObject("value")
            ?.get("stringValue")
            ?.asString

    @Test
    fun `encodes OpenTelemetry log records`() {
        val body = TelemetryEncoder(TelemetryFormat.OTLP).encode(listOf(SAMPLE_EVENT)).single()
        val resourceLogs =
            JsonParser
                .parseString(body)
                .asJsonObject
                .getAsJsonArray("resourceLogs")[0]
                .asJsonObject
        val scopeLogs = resourceLogs.getAsJsonArray("scopeLogs")[0].asJsonObject
        val record = scopeLogs.getAsJsonArray("logRecords")[0].asJsonObject
        val attributes = record.getAsJsonArray("attributes")

        assertEquals("acme.application.create", record["eventName"].asString)
        assertEquals("acme.application.create", record.getAsJsonObject("body")["stringValue"].asString)
        assertEquals("${Instant.parse("2026-01-02T03:04:05.000Z").toEpochMilli()}000000", record["timeUnixNano"].asString)
        assertEquals("telemetry", scopeLogs.getAsJsonObject("scope")["name"].asString)
        assertEquals("acme.application.create", attributes.attribute("event.name"))
        assertEquals("e1", attributes.attribute("event.id"))
        assertEquals("api", attributes.attribute("event.kind"))
        assertEquals("/applications", attributes.attribute("url.template"))
        assertEquals("not-a-uuid", attributes.attribute("user.id"))
        assertEquals("B3A1C2D4-0000-4000-8000-000000000001", attributes.attribute("organization.id"))
        assertEquals("application", attributes.attribute("event.data.resource_type"))
        assertEquals("Android", attributes.attribute("os.name"))
        assertEquals("14", attributes.attribute("os.version"))
        val resource = resourceLogs.getAsJsonObject("resource").getAsJsonArray("attributes")
        assertEquals("unknown_service", resource.attribute("service.name"))
        assertEquals("Acme", resource.attribute("platform"))
    }

    @Test
    fun `lets the context name the service`() {
        val event = SAMPLE_EVENT.copy(context = mapOf("service.name" to "console"))
        val body = TelemetryEncoder(TelemetryFormat.OTLP).encode(listOf(event)).single()
        val resource =
            JsonParser
                .parseString(body)
                .asJsonObject
                .getAsJsonArray("resourceLogs")[0]
                .asJsonObject
                .getAsJsonObject("resource")
                .getAsJsonArray("attributes")
        assertEquals("console", resource.attribute("service.name"))
        assertEquals(1, resource.size())
    }

    @Test
    fun `sends the events as they are in the json format, leaving out what is not known`() {
        val event = SAMPLE_EVENT.copy(identity = TelemetryIdentity(), route = null)
        val body = TelemetryEncoder(TelemetryFormat.JSON).encode(listOf(event)).single()
        val parsed = JsonParser.parseString(body).asJsonArray[0].asJsonObject
        assertEquals("acme.application.create", parsed["name"].asString)
        assertEquals("api", parsed["kind"].asString)
        assertEquals("2026-01-02T03:04:05.000Z", parsed["timestamp"].asString)
        val attributes = parsed.getAsJsonObject("attributes")
        assertEquals("application", attributes.getAsJsonObject("data")["resource_type"].asString)
        assertEquals("Android", attributes.getAsJsonObject("os")["name"].asString)
        assertFalse(attributes.has("user"))
        assertFalse(attributes.has("route"))
        assertEquals("Acme", parsed.getAsJsonObject("context")["platform"].asString)
    }

    @Test
    fun `reshapes events with a mapping, its filters and spreads`() {
        val encoder =
            TelemetryEncoder(
                TelemetryFormat.MAPPING,
                TelemetryMapping(
                    tables = mapOf("features" to mapOf("acme.application.create" to mapOf("category" to "application", "tier" to "core"))),
                    template =
                        mapOf(
                            "action_name" to "{{name}}",
                            "company_id" to "{{attributes.organization.id | uuid}}",
                            "metadata" to
                                mapOf(
                                    "..." to "{{attributes.data}}",
                                    "...context" to "{{context}}",
                                    "...feature" to "{{name | lookup: features}}",
                                    "missing" to "x-{{attributes.nothing}}",
                                ),
                            "request" to mapOf("time" to "{{timestamp}}", "when" to "{{timestamp | date}}"),
                            "user_id" to "{{attributes.user.id | uuid}}",
                            "label" to "event {{name}} at {{attributes.route}}",
                            "tags" to listOf("{{kind}}", "{{attributes.nothing}}"),
                            "fixed" to 1,
                        ),
                ),
            )
        val parsed = JsonParser.parseString(encoder.encode(listOf(SAMPLE_EVENT)).single()).asJsonArray
        assertEquals(1, parsed.size())
        val mapped = parsed[0].asJsonObject
        assertEquals("acme.application.create", mapped["action_name"].asString)
        assertEquals("b3a1c2d4-0000-4000-8000-000000000001", mapped["company_id"].asString)
        assertFalse(mapped.has("user_id"))
        val metadata = mapped.getAsJsonObject("metadata")
        assertEquals(
            JsonParser.parseString(
                """{"resource_type":"application","platform":"Acme","category":"application","tier":"core"}""",
            ),
            metadata,
        )
        val request = mapped.getAsJsonObject("request")
        assertEquals("2026-01-02T03:04:05.000Z", request["time"].asString)
        assertEquals(Instant.parse("2026-01-02T03:04:05.000Z").toEpochMilli(), request["when"].asLong)
        assertEquals("event acme.application.create at /applications", mapped["label"].asString)
        assertEquals(JsonParser.parseString("""["api"]"""), mapped["tags"])
        assertEquals(1, mapped["fixed"].asInt)
    }

    @Test
    fun `sends each event on its own when the mapping asks, and nothing for no events`() {
        val encoder =
            TelemetryEncoder(TelemetryFormat.MAPPING, TelemetryMapping(batch = TelemetryBatch.EACH, template = mapOf("n" to "{{name}}")))
        assertEquals(
            listOf("""{"n":"acme.application.create"}""", """{"n":"acme.application.create"}"""),
            encoder.encode(listOf(SAMPLE_EVENT, SAMPLE_EVENT)),
        )
        assertTrue(encoder.encode(emptyList()).isEmpty())
        assertTrue(TelemetryEncoder(TelemetryFormat.OTLP).encode(emptyList()).isEmpty())
    }

    @Test
    fun `a mapping without a template sends empty objects`() {
        val body = TelemetryEncoder(TelemetryFormat.MAPPING).encode(listOf(SAMPLE_EVENT)).single()
        assertEquals(JsonObject(), JsonParser.parseString(body).asJsonArray[0])
    }
}
