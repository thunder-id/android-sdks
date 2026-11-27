// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.telemetry

import dev.thunderid.android.TelemetryConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

/** The auth events the SDK reports. */
internal enum class TelemetryAuthAction(
    val value: String,
) {
    SIGN_IN("sign-in"),
    SIGN_IN_FAILED("sign-in-failed"),
    SIGN_OUT("sign-out"),
    SESSION_EXPIRED("session-expired"),
}

/** Sends one encoded body to the endpoint. A failure is dropped by the caller. */
internal fun interface TelemetrySender {
    suspend fun send(
        body: String,
        token: String?,
    )
}

/**
 * What the platform gives the telemetry.
 *
 * @property apiBases The base URLs of the SDK's own API, watched for API events besides `apiEvents.apis`.
 * @property identity Reads the current user and organization. Must be quick: it runs as each event is added.
 * @property token The token for the endpoint, read when a batch is taken for sending.
 * @property sender Sends the encoded batches.
 * @property os The operating system and its version.
 * @property scope Where batches are sent and the flush timer runs.
 */
internal class TelemetryDependencies(
    val apiBases: List<String>,
    val identity: () -> TelemetryIdentity,
    val token: () -> String?,
    val sender: TelemetrySender,
    val os: TelemetryOs?,
    val scope: CoroutineScope,
    val clock: () -> Long = System::currentTimeMillis,
)

/**
 * Reports what people do in an app: page views, API events, auth events, external links and the app's own
 * `track()` calls. Events are batched and sent in the background; a failure is dropped and never reaches
 * the app. Built only when the configuration has a usable endpoint.
 */
internal class Telemetry(
    private val config: TelemetryConfig,
    private val dependencies: TelemetryDependencies,
) {
    private val names = TelemetryNames(config.prefix)
    private val encoder = TelemetryEncoder(config.format, config.mapping)
    private val apiRules =
        TelemetryApiRules(
            dependencies.apiBases + config.apiEvents.apis,
            config.apiEvents.parentActions,
            config.apiEvents.kinds,
            config.apiEvents.addressWords,
            config.apiEvents.actions,
        )
    private val lock = Any()

    /** The batch being sent, so a second one never goes while it is in flight. */
    private var inFlight: Job? = null
    private var queue = ArrayList<TelemetryEvent>()
    private var timer: Job? = null

    /** Set by [stop]: nothing more is queued or scheduled. */
    private var stopped = false

    /** Reports a page view from its route pattern, such as `/applications/:id`. */
    fun page(pattern: String) {
        val pages = config.pages
        if (!pages.enabled || TelemetryNames.matchesAny(pattern, pages.exclude)) return
        add(TelemetryEventKind.PAGE, pages.names[pattern] ?: TelemetryNames.pageName(pattern), pattern, emptyMap())
    }

    /**
     * Reports an API call made through the SDK's HTTP client, when it is a change to a watched API.
     *
     * @param body The request body, read only for `skipWhen` and the `?field` conditions of `names`; never
     * sent.
     * @param status The response status, or `null` when there was no response.
     * @param responseBody The response body of a failed call, read only for the API's error code.
     * @param event The name the API gave the call in its [TELEMETRY_EVENT_HEADER] response header, if any;
     * `none` means the call is not reported.
     */
    fun api(
        method: String?,
        url: String?,
        body: String?,
        status: Int?,
        responseBody: String?,
        event: String? = null,
    ) {
        val apiEvents = config.apiEvents
        if (!apiEvents.enabled || url.isNullOrEmpty()) return
        if (apiEvents.skipWhen.any { TelemetryApiRules.sets(body, it) }) return
        val call = apiRules.classify(method, url) ?: return
        val path = apiRules.path(url) ?: return
        if (event == NOT_REPORTED) return
        val (name, route) = apiName(call, path, (method ?: "").uppercase(), body, event)
        // Checked before `-failed` is added, so excluding a name also leaves out its failures.
        val exclude = apiEvents.exclude
        val excluded =
            listOfNotNull(withoutQuery(url), apiRules.literalPath(url), path, call.name, name)
                .any { TelemetryNames.matchesAny(it, exclude) }
        if (excluded) return
        if (status != null && status in 200..299) {
            add(TelemetryEventKind.API, name, route, call.data)
        } else {
            add(TelemetryEventKind.API, "$name-failed", route, call.data + TelemetryApiRules.failureData(status, responseBody))
        }
    }

    /**
     * The name of an API event and its route, from the first that applies: a configured name for its method
     * and path, optionally with a body field it must set (`POST /promotions ?origin`); a configured rename of
     * the rules' name; the name the API gave in its response header, which a configured rename of that name
     * may still change; or the rules' name. Configuration always comes before the header. A configured path
     * names the route too, with each `*` as `{id}`.
     */
    private fun apiName(
        call: TelemetryApiCall,
        path: String,
        method: String,
        body: String?,
        event: String?,
    ): Pair<String, String> {
        val names = config.apiEvents.names
        // A key with a body condition is more specific, so it is tried first.
        val keys = names.entries.sortedByDescending { it.key.contains(" ?") }
        for ((key, value) in keys) {
            val parts = key.split(" ")
            val pattern = parts.getOrNull(1) ?: continue
            val field = parts.getOrNull(2)?.takeIf { it.startsWith("?") }?.substring(1)
            if (parts[0].uppercase() == method &&
                TelemetryNames.matches(path, pattern) &&
                (field == null || TelemetryApiRules.sets(body, field))
            ) {
                return value to WILDCARD.replace(pattern, "{id}")
            }
        }
        names[call.name]?.let { return it to call.route }
        val fromApi = event?.takeIf { EVENT_NAME.matches(it) }
        if (fromApi != null) return (names[fromApi] ?: fromApi) to call.route
        return call.name to call.route
    }

    /** Reports an auth event, with codes about it such as an error code for a failed sign-in. */
    fun auth(
        action: TelemetryAuthAction,
        data: Map<String, String> = emptyMap(),
    ) {
        val authEvents = config.authEvents
        if (!authEvents.enabled || action.value in authEvents.exclude) return
        add(TelemetryEventKind.AUTH, authEvents.names[action.value] ?: "auth.${action.value}", null, data)
    }

    /** Reports a link that leaves the app, with its host and path only. Only `http` and `https` links count. */
    fun link(href: String) {
        val links = config.links
        if (!links.enabled) return
        val target = TelemetryApiRules.parseAbsolute(href) ?: return
        val scheme = target.scheme.lowercase()
        if (scheme != "https" && scheme != "http") return
        val host = target.host.lowercase() + if (target.port != -1 && !isDefaultPort(scheme, target.port)) ":${target.port}" else ""
        val rawPath = target.rawPath?.takeIf { it.isNotEmpty() } ?: "/"
        val where = "$scheme://$host$rawPath"
        if (TelemetryNames.matchesAny(where, links.exclude)) return
        val renamed =
            links.names.entries
                .firstOrNull { (pattern, _) -> TelemetryNames.matches(where, pattern) }
                ?.value
        add(TelemetryEventKind.LINK, renamed ?: "link.open", null, mapOf("host" to host, "path" to linkPath(rawPath)))
    }

    /**
     * Reports an event the app names. [data] holds codes only, never a name or anything a user typed. An
     * event whose name is not `<type>.<action>` in lower case is dropped, and so is a value that is long or
     * looks like an address, a URL or free text.
     */
    fun track(
        name: String,
        data: Map<String, String> = emptyMap(),
    ) {
        if (!EVENT_NAME.matches(name)) return
        add(TelemetryEventKind.CUSTOM, name, null, data.filterValues { isCode(it) })
    }

    /**
     * Takes what is waiting and sends it in the background. The endpoint's token is read now, so a flush
     * just before sign-out still carries it. At most one batch is in flight: while one is, the events stay
     * queued and the timer tries again. Consent is checked again before each body is sent: when it is
     * withdrawn, the bodies not yet started go back to the queue, and a send already started goes on. A
     * send that has not completed within [SEND_TIMEOUT_MS] is abandoned. Returns the send, or `null` when
     * nothing is sent.
     */
    fun flush(): Job? {
        val reservation = Job()
        // The app's consent function is called outside the lock, so a slow one never holds up adding events.
        // It is called only when something waits, and an answer an instant old is good enough.
        val allowed = synchronized(lock) { queue.isNotEmpty() } && consented()
        val batch =
            synchronized(lock) {
                timer?.cancel()
                timer = null
                if (queue.isEmpty()) return null
                if (!allowed || inFlight?.isActive == true) {
                    schedule()
                    return null
                }
                // Reserved here, with the batch taken, so a flush in between sees a send in flight.
                inFlight = reservation
                queue.also { queue = ArrayList() }
            }
        val token = runCatching { dependencies.token() }.getOrNull()
        val send =
            dependencies.scope.launch {
                val bodies = runCatching { encoder.encodeWithEvents(batch) }.getOrDefault(emptyList())
                for ((index, part) in bodies.withIndex()) {
                    if (!consented()) {
                        requeue(bodies.drop(index).flatMap { it.first })
                        break
                    }
                    try {
                        dependencies.sender.send(part.second, token)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Throwable) {
                        // A failure is dropped: telemetry never affects the app.
                    }
                }
            }
        // Waits for the send, cancellably, so a send stuck in blocking I/O is abandoned on time.
        val watch =
            dependencies.scope.launch {
                withTimeoutOrNull(SEND_TIMEOUT_MS) { send.join() }
                send.cancel()
            }
        // The reservation holds until the watch ends, whether it completes, times out or is cancelled.
        watch.invokeOnCompletion { reservation.complete() }
        return watch
    }

    /**
     * Stops this telemetry for good, such as when a new configuration replaces it: cancels the flush timer
     * and drops the events waiting. A send already started goes on; nothing is queued or sent after it.
     */
    fun stop() {
        synchronized(lock) {
            stopped = true
            timer?.cancel()
            timer = null
            queue = ArrayList()
        }
    }

    /** Puts the events of bodies not sent back in front of the queue, keeping at most [MAX_WAITING]. */
    private fun requeue(events: List<TelemetryEvent>) {
        synchronized(lock) {
            if (stopped) return
            queue.addAll(0, events)
            trim()
            schedule()
        }
    }

    /** Drops the oldest events beyond [MAX_WAITING]. Called holding [lock]. */
    private fun trim() {
        if (queue.size > MAX_WAITING) queue.subList(0, queue.size - MAX_WAITING).clear()
    }

    private fun add(
        kind: TelemetryEventKind,
        name: String,
        route: String?,
        data: Map<String, String>,
    ) {
        val identity = runCatching { dependencies.identity() }.getOrDefault(TelemetryIdentity())
        val event =
            TelemetryEvent(
                id = UUID.randomUUID().toString(),
                kind = kind,
                name = names.full(name),
                timestamp = TIMESTAMP.format(Instant.ofEpochMilli(dependencies.clock())),
                route = route,
                data = data,
                identity = identity,
                os = dependencies.os,
                context = config.context,
            )
        val full =
            synchronized(lock) {
                if (stopped) return
                queue.add(event)
                trim()
                queue.size >= BATCH_SIZE
            }
        if (full && consented()) flush() else synchronized(lock) { schedule() }
    }

    /** Starts the flush timer unless it runs. Called holding [lock]. */
    private fun schedule() {
        if (stopped || timer?.isActive == true) return
        timer =
            dependencies.scope.launch {
                delay(FLUSH_INTERVAL_MS)
                flush()
            }
    }

    private fun consented(): Boolean = runCatching { config.consent?.invoke() ?: true }.getOrDefault(false)

    private fun withoutQuery(url: String): String {
        val parsed = TelemetryApiRules.parseAbsolute(url) ?: return url
        val port = if (parsed.port != -1) ":${parsed.port}" else ""
        return "${parsed.scheme}://${parsed.host}$port${parsed.rawPath.orEmpty()}"
    }

    private fun isDefaultPort(
        scheme: String,
        port: Int,
    ): Boolean = (scheme == "https" && port == 443) || (scheme == "http" && port == 80)

    companion object {
        /** A batch is sent when it reaches this many events, or after [FLUSH_INTERVAL_MS]. */
        const val BATCH_SIZE = 20
        const val FLUSH_INTERVAL_MS = 5_000L

        /** At most this many events wait for consent; the oldest are dropped first. */
        const val MAX_WAITING = 200

        /** A send not completed within this is abandoned. */
        const val SEND_TIMEOUT_MS = 10_000L

        /**
         * The response header in which an API names the event for a call, such as `application.share`.
         * Matched without regard to case.
         */
        const val TELEMETRY_EVENT_HEADER = "x-telemetry-event"

        /** The [TELEMETRY_EVENT_HEADER] value by which an API asks for a call not to be reported. */
        private const val NOT_REPORTED = "none"

        /** A `*` or `**` in a configured path, reported as `{id}`. */
        private val WILDCARD = Regex("\\*\\*?")

        /**
         * What `track` and an API's [TELEMETRY_EVENT_HEADER] must give as a name: `<type>.<action>`, lower
         * case, words joined by hyphens.
         */
        private val EVENT_NAME = Regex("^[a-z0-9]+(?:-[a-z0-9]+)*(?:\\.[a-z0-9]+(?:-[a-z0-9]+)*)+$")

        /** A value that is not a code: an address, a URL, a query, a fragment or text with spaces. */
        private val NOT_A_CODE = Regex("[@?#\\s]|://")
        private const val MAX_CODE_LENGTH = 64

        /** A link path segment kept as it is; any other is replaced by `{id}`. */
        private val PLAIN_SEGMENT = Regex("^[A-Za-z_-]{1,40}$")

        /** Whether [value] can go in a `track` event's data. */
        fun isCode(value: String): Boolean = value.length <= MAX_CODE_LENGTH && !NOT_A_CODE.containsMatchIn(value)

        /**
         * A link's path with only its first segment kept, when it is a plain word; every later segment is
         * `{id}`, since it can be a name or a token. Empty segments stay.
         */
        fun linkPath(path: String): String {
            var first = true
            return path.split("/").joinToString("/") { segment ->
                when {
                    segment.isEmpty() -> segment
                    first && PLAIN_SEGMENT.matches(segment) -> segment.also { first = false }
                    else -> "{id}".also { first = false }
                }
            }
        }

        private val TIMESTAMP: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)
    }
}
