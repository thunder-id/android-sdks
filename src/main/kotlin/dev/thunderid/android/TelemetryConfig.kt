// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android

/**
 * Usage telemetry: what people do in the app, reported to an endpoint the application configures. Off
 * until [endpoint] is set. Only codes are sent: route patterns, path templates, ids and error codes, never
 * a URL with its query, a name or anything a user typed.
 *
 * The SDK reports by itself the API events of every call through its own HTTP client (management
 * operations included), its own sign-in and sign-out, and links its components open. Screen views come
 * from [ThunderIDClient.trackPageView] or the Compose helpers, and anything else from
 * [ThunderIDClient.track].
 *
 * @property endpoint Where events are sent: an absolute `https` URL. Telemetry stays off when it is
 * blank or does not use HTTPS.
 * @property format The encoding. Defaults to [TelemetryFormat.OTLP].
 * @property mapping The template for [TelemetryFormat.MAPPING].
 * @property prefix A product prefix added to every name, such as `acme` for `acme.application.create`.
 * @property context Codes added to every event, such as the product, the plan tier or the region. In the
 * OTLP format they are resource attributes, so `service.name` here names the service.
 * @property consent When set, nothing is sent until it returns `true`. Events wait in memory until then,
 * at most 200 of them, the oldest dropped first.
 * @property identity The ID token claims the user's and organization's ids are read from.
 * @property token The token the endpoint gets. See [TelemetryToken].
 * @property pages Screen views.
 * @property apiEvents Creates, updates, deletes, deploys, promotions and rollbacks, on success and on failure.
 * @property authEvents `auth.sign-in`, `auth.sign-in-failed`, `auth.sign-out` and `auth.session-expired`.
 * @property links Links that leave the app, reported as `link.open`.
 */
data class TelemetryConfig(
    val endpoint: String,
    val format: TelemetryFormat = TelemetryFormat.OTLP,
    val mapping: TelemetryMapping? = null,
    val prefix: String? = null,
    val context: Map<String, String> = emptyMap(),
    val consent: (() -> Boolean)? = null,
    val identity: TelemetryIdentityConfig = TelemetryIdentityConfig(),
    val token: TelemetryToken = TelemetryToken.App,
    val pages: TelemetryKindConfig = TelemetryKindConfig(),
    val apiEvents: TelemetryApiEventsConfig = TelemetryApiEventsConfig(),
    val authEvents: TelemetryKindConfig = TelemetryKindConfig(),
    val links: TelemetryKindConfig = TelemetryKindConfig(),
)

/** How events are encoded for the endpoint. */
enum class TelemetryFormat {
    /** OpenTelemetry log records over OTLP/HTTP with JSON encoding, for an OpenTelemetry Collector. */
    OTLP,

    /** The events as they are, in a JSON array. */
    JSON,

    /** Each event reshaped by [TelemetryConfig.mapping], for any other API. */
    MAPPING,
}

/** How a [TelemetryMapping] sends a batch. */
enum class TelemetryBatch {
    /** Every event of the batch in one JSON array. */
    ARRAY,

    /** One request per event. */
    EACH,
}

/**
 * A template that reshapes each event for an API that does not take OpenTelemetry.
 *
 * @property template The shape of one event. String values may hold `{{path}}` expressions read from the
 * event, with filters: `uuid` (the value when it is a UUID, lower-cased), `lookup: <table>` (the value's
 * entry in [tables]) and `date` (an ISO 8601 time as milliseconds since the epoch). A string that is a
 * single expression keeps the value's type. A key starting with `...` spreads an object value into its
 * parent. An expression that reads nothing leaves its key out.
 * @property tables Lookup tables for the `lookup` filter.
 * @property batch Whether a batch is one array or one request per event.
 */
data class TelemetryMapping(
    val template: Map<String, Any?>,
    val tables: Map<String, Map<String, Any?>> = emptyMap(),
    val batch: TelemetryBatch = TelemetryBatch.ARRAY,
)

/**
 * The ID token claims an event's ids come from.
 *
 * @property userClaim The claim holding the user's id.
 * @property organizationClaim The claim holding the organization's id.
 */
data class TelemetryIdentityConfig(
    val userClaim: String = "sub",
    val organizationClaim: String = "ouId",
)

/** The token the telemetry endpoint gets. */
sealed class TelemetryToken {
    /**
     * The app's access token, only for an endpoint on the origin of `baseUrl`, and never when sign-in
     * runs on a backend. Every other endpoint gets no token.
     */
    object App : TelemetryToken() {
        override fun toString(): String = "App"
    }

    /** No token. */
    object None : TelemetryToken() {
        override fun toString(): String = "None"
    }

    /** This token, and never the app's own. */
    class Value(
        val token: String,
    ) : TelemetryToken() {
        override fun equals(other: Any?): Boolean = other is Value && other.token == token

        override fun hashCode(): Int = token.hashCode()

        // Never print the token.
        override fun toString(): String = "Value(***)"
    }
}

/**
 * Settings every automatic kind shares.
 *
 * @property enabled Whether this kind is reported.
 * @property exclude What is never reported: route patterns for screens and URLs for links, where `*`
 * matches the rest of one path segment and `**` the rest of the value, and, for auth events, action names
 * matched exactly, such as `session-expired`.
 * @property names Renames, from a route pattern, a URL or an automatic name to the name to report instead.
 * The prefix is still added.
 */
data class TelemetryKindConfig(
    val enabled: Boolean = true,
    val exclude: List<String> = emptyList(),
    val names: Map<String, String> = emptyMap(),
)

/**
 * API events.
 *
 * @property enabled Whether API events are reported.
 * @property exclude What is never reported: URLs (without their query), paths below the API as called or
 * with values as `{id}` (such as every path under `/oauth2`, with a trailing `**`), the rules' name or the
 * final name, with the same patterns as [TelemetryKindConfig.exclude]. Excluding a name also leaves out its
 * `-failed` form.
 * @property names Renames API events. A key is either an automatic name, such as `group.create` or
 * `api.call`, or a method and a path, such as `POST /import`, where `*` matches one segment and `**` the
 * rest, which names any call that matches, including one the built-in rules do not name. A path key may
 * end with `?<field>` to match only when the request body sets that field, such as
 * `POST /promotions ?origin`. The body is read for that alone and never sent. These come first, before a
 * name the API gives in its `X-Telemetry-Event` response header and before the built-in rules.
 * @property apis More APIs to watch with the same rules as the SDK's own: base URLs whose calls through
 * the SDK's HTTP client are reported.
 * @property parentActions Sub-collections reported as an action on their parent, by change:
 * `mapOf("sharing-policies" to TelemetryParentActions(create = "share", update = "share-update",
 * delete = "unshare"))` reports `POST /applications/{id}/sharing-policies` as `application.share`.
 * @property skipWhen Request body fields that mean a call changes nothing, such as `dryRun` or `preview`:
 * a call whose body sets one is not reported.
 * @property kinds Top-level collections, by their path word such as `connections`, whose next path word is a
 * kind: `POST /connections/google` is `connection.create` with `kind: google`. After any other collection
 * that word is an item value, reported as `{id}` and never in a name or data.
 * @property addressWords Words that, right after a collection, start an address, such as `tree`: everything
 * after one is the item's address, so `PUT /organization-units/tree/engineering/operations` is
 * `organization-unit.update` with route `/organization-units/tree/{path}`.
 * @property actions Words that, ending a POST to a sub-collection, are an action on the parent, such as
 * `suspend`, besides the change words `add`, `create`, `delete`, `remove` and `update`:
 * `POST /groups/{id}/members/add` is `group.member-add`. Any other word there is an item value.
 */
data class TelemetryApiEventsConfig(
    val enabled: Boolean = true,
    val exclude: List<String> = emptyList(),
    val names: Map<String, String> = emptyMap(),
    val apis: List<String> = emptyList(),
    val parentActions: Map<String, TelemetryParentActions> = emptyMap(),
    val skipWhen: List<String> = emptyList(),
    val kinds: List<String> = emptyList(),
    val addressWords: List<String> = emptyList(),
    val actions: List<String> = emptyList(),
)

/**
 * The names of a sub-collection's changes when they are reported as an action on its parent. A change
 * left `null` keeps its own name.
 *
 * @property create The action for a create, such as `share`.
 * @property update The action for an update, such as `share-update`.
 * @property delete The action for a delete, such as `unshare`.
 */
data class TelemetryParentActions(
    val create: String? = null,
    val update: String? = null,
    val delete: String? = null,
)
