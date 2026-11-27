// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.telemetry

import com.google.gson.Gson
import dev.thunderid.android.TelemetryBatch
import dev.thunderid.android.TelemetryFormat
import dev.thunderid.android.TelemetryMapping
import java.time.Instant

/**
 * Encodes a batch of events into request bodies for the configured format.
 *
 * @param format The format.
 * @param mapping The mapping, for [TelemetryFormat.MAPPING].
 */
internal class TelemetryEncoder(
    private val format: TelemetryFormat,
    private val mapping: TelemetryMapping? = null,
) {
    private val gson = Gson()

    /** The request bodies for a batch: one, or one per event for a mapping that sends each on its own. */
    fun encode(events: List<TelemetryEvent>): List<String> = encodeWithEvents(events).map { it.second }

    /** Each request body for a batch with the events it carries, in the order they are to be sent. */
    fun encodeWithEvents(events: List<TelemetryEvent>): List<Pair<List<TelemetryEvent>, String>> {
        if (events.isEmpty()) return emptyList()
        return when (format) {
            TelemetryFormat.JSON -> {
                listOf(events to gson.toJson(events.map { it.toMap() }))
            }

            TelemetryFormat.MAPPING -> {
                if (mapping?.batch == TelemetryBatch.EACH) {
                    events.map { listOf(it) to gson.toJson(map(it)) }
                } else {
                    listOf(events to gson.toJson(events.map { map(it) }))
                }
            }

            TelemetryFormat.OTLP -> {
                listOf(events to gson.toJson(otlp(events)))
            }
        }
    }

    /** One event reshaped by the mapping's template. */
    fun map(event: TelemetryEvent): Any? = render(mapping?.template ?: emptyMap<String, Any?>(), event.toMap())

    /**
     * OpenTelemetry log records (OTLP/HTTP JSON): one record per event, named by `event.name`, with the
     * event's codes as attributes and the context as resource attributes.
     */
    private fun otlp(events: List<TelemetryEvent>): Map<String, Any?> {
        fun attributes(values: Map<String, String?>): List<Map<String, Any>> =
            values.mapNotNull { (key, value) -> value?.let { mapOf("key" to key, "value" to mapOf("stringValue" to it)) } }

        val records =
            events.map { event ->
                val values =
                    linkedMapOf(
                        "event.id" to event.id,
                        "event.kind" to event.kind.value,
                        "event.name" to event.name,
                        "organization.id" to event.identity.organizationId,
                        "url.template" to event.route,
                        "user.id" to event.identity.userId,
                        "os.name" to event.os?.name,
                        "os.version" to event.os?.version,
                    )
                event.data.forEach { (key, value) -> values["event.data.$key"] = value }
                mapOf(
                    "attributes" to attributes(values),
                    "body" to mapOf("stringValue" to event.name),
                    "eventName" to event.name,
                    "timeUnixNano" to "${epochMillis(event.timestamp) ?: 0}000000",
                )
            }
        val resource = linkedMapOf<String, String?>("service.name" to "unknown_service").apply { putAll(events[0].context) }
        return mapOf(
            "resourceLogs" to
                listOf(
                    mapOf(
                        "resource" to mapOf("attributes" to attributes(resource)),
                        "scopeLogs" to
                            listOf(
                                mapOf(
                                    "logRecords" to records,
                                    "scope" to mapOf("name" to "telemetry"),
                                ),
                            ),
                    ),
                ),
        )
    }

    /**
     * Renders a template node: strings with expressions are filled in, maps and lists recursively, a `...`
     * key spreads its map value, and a key whose value reads nothing is left out.
     */
    private fun render(
        node: Any?,
        event: Map<String, Any?>,
    ): Any? =
        when (node) {
            is String -> {
                fill(node, event)
            }

            is List<*> -> {
                node.mapNotNull { render(it, event) }
            }

            is Map<*, *> -> {
                val result = linkedMapOf<String, Any?>()
                node.forEach { (key, value) ->
                    val name = key.toString()
                    val rendered = render(value, event)
                    if (name.startsWith("...")) {
                        if (rendered is Map<*, *>) rendered.forEach { (k, v) -> result[k.toString()] = v }
                    } else if (rendered != null) {
                        result[name] = rendered
                    }
                }
                result
            }

            else -> {
                node
            }
        }

    /**
     * A template string with its expressions filled in. A string that is a single expression keeps the
     * value's type; a string any of whose expressions reads nothing is nothing.
     */
    private fun fill(
        template: String,
        event: Map<String, Any?>,
    ): Any? {
        WHOLE_EXPRESSION.matchEntire(template)?.let { return evaluate(it.groupValues[1], event) }
        var missing = false
        val filled =
            EXPRESSION.replace(template) { match ->
                when (val value = evaluate(match.groupValues[1], event)) {
                    null -> {
                        missing = true
                        ""
                    }

                    is String -> {
                        value
                    }

                    else -> {
                        gson.toJson(value)
                    }
                }
            }
        return if (missing) null else filled
    }

    /** An expression's value: a dotted path into the event, through its filters. */
    private fun evaluate(
        expression: String,
        event: Map<String, Any?>,
    ): Any? {
        val parts = expression.split("|").map { it.trim() }
        var value: Any? =
            parts[0].split(".").fold(event as Any?) { current, key -> (current as? Map<*, *>)?.get(key) }
        parts.drop(1).forEach { value = applyFilter(it, value) }
        return value
    }

    /**
     * One filter:
     *
     * - `uuid`: the value when it is a UUID, lower-cased, else nothing;
     * - `lookup: <table>`: the value's entry in one of the mapping's tables;
     * - `date`: an ISO 8601 time as milliseconds since the epoch.
     */
    private fun applyFilter(
        filter: String,
        value: Any?,
    ): Any? {
        val name = filter.substringBefore(":").trim()
        val argument = filter.substringAfter(":", "").trim()
        return when (name) {
            "uuid" -> (value as? String)?.takeIf { UUID.matches(it) }?.lowercase()
            "lookup" -> (value as? String)?.let { mapping?.tables?.get(argument)?.get(it) }
            "date" -> (value as? String)?.let { epochMillis(it) }
            else -> value
        }
    }

    private companion object {
        val UUID = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        val EXPRESSION = Regex("\\{\\{([^}]+)\\}\\}")
        val WHOLE_EXPRESSION = Regex("^\\{\\{([^}]+)\\}\\}$")

        fun epochMillis(timestamp: String): Long? = runCatching { Instant.parse(timestamp).toEpochMilli() }.getOrNull()
    }
}
