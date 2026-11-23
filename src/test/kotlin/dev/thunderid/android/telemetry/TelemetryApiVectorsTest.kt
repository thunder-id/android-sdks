// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.telemetry

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.thunderid.android.TelemetryApiEventsConfig
import dev.thunderid.android.TelemetryConfig
import dev.thunderid.android.TelemetryFormat
import dev.thunderid.android.TelemetryParentActions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Replays the API naming vectors that every SDK shares, so each one names the same calls the same way.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TelemetryApiVectorsTest {
    private val vectors: JsonObject =
        JsonParser
            .parseString(javaClass.classLoader!!.getResource(FIXTURE)!!.readText())
            .asJsonObject

    @Test
    fun `names every shared API vector as the reference does`() =
        runTest {
            val base = vectors["apiBase"].asString
            val configs = vectors.getAsJsonObject("configs").entrySet().associate { (name, value) -> name to apiEvents(value.asJsonObject) }
            val cases = vectors.getAsJsonArray("cases")
            val failures = mutableListOf<String>()
            cases.forEach { element ->
                val case = element.asJsonObject
                val config =
                    TelemetryConfig(
                        endpoint = "https://t.example.com",
                        format = TelemetryFormat.JSON,
                        apiEvents = configs.getValue(case["config"].asString),
                    )
                val recorder = Recorder(config, backgroundScope, identity = { TelemetryIdentity() })
                val outcome = case.getAsJsonObject("outcome")
                val ok = outcome["ok"].asBoolean
                val status = outcome["status"]?.asInt
                recorder.telemetry.api(
                    case["method"].asString,
                    base + case["path"].asString,
                    case["body"]?.takeUnless { it.isJsonNull }?.toString(),
                    // A failed call keeps its status; a successful one is reported as a success.
                    if (ok) (status ?: 200) else status,
                    outcome["responseBody"]?.takeUnless { it.isJsonNull }?.toString(),
                )
                recorder.telemetry.flush()
                runCurrent()
                // Every event of every batch, so an extra event is never missed.
                val actual =
                    recorder.batches.flatMap { batch -> batch.map { it.asJsonObject } }.map { event ->
                        val attributes = event.getAsJsonObject("attributes")
                        JsonObject().apply {
                            add("name", event["name"])
                            add("route", attributes["route"])
                            add("data", attributes["data"])
                        }
                    }
                val expected = case["expect"]?.takeUnless { it.isJsonNull }
                if (!same(expected, actual)) {
                    failures +=
                        "${case["config"].asString} ${case["method"].asString} ${case["path"].asString}: expected $expected, got $actual"
                }
            }
            assertTrue("${failures.size} of ${cases.size()} vectors differ:\n" + failures.joinToString("\n"), failures.isEmpty())
        }

    /** No event when [expected] is `null`; otherwise exactly one, with the same name, route and data. */
    private fun same(
        expected: JsonElement?,
        actual: List<JsonObject>,
    ): Boolean = if (expected == null) actual.isEmpty() else actual.size == 1 && expected == actual.single()

    private fun apiEvents(config: JsonObject): TelemetryApiEventsConfig {
        fun strings(key: String): List<String> = config.getAsJsonArray(key)?.map { it.asString }.orEmpty()
        return TelemetryApiEventsConfig(
            exclude = strings("exclude"),
            names =
                config
                    .getAsJsonObject("names")
                    ?.entrySet()
                    ?.associate { (key, value) -> key to value.asString }
                    .orEmpty(),
            parentActions =
                config
                    .getAsJsonObject("parentActions")
                    ?.entrySet()
                    ?.associate { (collection, value) ->
                        val actions = value.asJsonObject
                        collection to
                            TelemetryParentActions(
                                create = actions["create"]?.asString,
                                update = actions["update"]?.asString,
                                delete = actions["delete"]?.asString,
                            )
                    }.orEmpty(),
            skipWhen = strings("skipWhen"),
            kinds = strings("kinds"),
            addressWords = strings("addressWords"),
            actions = strings("actions"),
        )
    }

    private companion object {
        const val FIXTURE = "telemetry-api-vectors.json"
    }
}
