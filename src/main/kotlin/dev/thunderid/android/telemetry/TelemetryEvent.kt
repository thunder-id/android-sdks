// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.telemetry

/** The kinds of event: the four the SDK reports by itself, and the app's own. */
internal enum class TelemetryEventKind(
    val value: String,
) {
    PAGE("page"),
    API("api"),
    AUTH("auth"),
    LINK("link"),
    CUSTOM("custom"),
}

/** The ids an event is attributed to. */
internal data class TelemetryIdentity(
    val userId: String? = null,
    val organizationId: String? = null,
)

/** The operating system the app runs on, which a mobile event carries instead of a user agent. */
internal data class TelemetryOs(
    val name: String,
    val version: String?,
)

/**
 * One reported event, before it is encoded.
 *
 * @property name The full name, with the prefix.
 * @property timestamp When it happened, as an ISO 8601 string in UTC with milliseconds.
 * @property route The route pattern of a page, or the path template of an API call, never the URL itself.
 * @property data Codes about the event: a resource type, an HTTP status, an error code, a link's host.
 * @property context The configured context codes.
 */
internal data class TelemetryEvent(
    val id: String,
    val kind: TelemetryEventKind,
    val name: String,
    val timestamp: String,
    val route: String?,
    val data: Map<String, String>,
    val identity: TelemetryIdentity,
    val os: TelemetryOs?,
    val context: Map<String, String>,
) {
    /**
     * The event as the `json` format sends it and the `mapping` format reads it, with the same keys as the
     * other SDKs. A value that is not known is left out.
     */
    fun toMap(): Map<String, Any?> {
        val attributes =
            linkedMapOf<String, Any?>("data" to data).apply {
                route?.let { put("route", it) }
                identity.userId?.let { put("user", mapOf("id" to it)) }
                identity.organizationId?.let { put("organization", mapOf("id" to it)) }
                os?.let { os -> put("os", listOfNotNull("name" to os.name, os.version?.let { "version" to it }).toMap()) }
            }
        return linkedMapOf(
            "id" to id,
            "name" to name,
            "kind" to kind.value,
            "timestamp" to timestamp,
            "attributes" to attributes,
            "context" to context,
        )
    }
}
