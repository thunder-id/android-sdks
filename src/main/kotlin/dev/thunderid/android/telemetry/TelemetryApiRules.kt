// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.telemetry

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.thunderid.android.TelemetryParentActions
import java.net.URI

/** A call that is reported: its name before the prefix, its path template and its codes. */
internal data class TelemetryApiCall(
    val name: String,
    val route: String,
    val data: Map<String, String>,
)

/** A create, an update or a delete. */
private enum class Change(
    val value: String,
) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
}

/**
 * Names an API call from its method and path alone, the same way for any REST API, with no knowledge of
 * a particular API:
 *
 * - `POST /<collection>` is `<type>.create`; `PUT` or `PATCH /<collection>/{id}` is `<type>.update`;
 *   `DELETE /<collection>/{id}` is `<type>.delete`. Words in front of the first collection, such as
 *   `design` in `/design/themes`, are skipped.
 * - The word right after a top-level collection listed in [kinds], as in `/connections/google/{id}`, is
 *   reported as `kind`. After any other top-level collection it is an item value, `{id}` in the route.
 * - A label and its value in front of a sub-collection, such as `ns/{namespace}` in
 *   `/languages/{id}/translations/ns/{namespace}/keys`, keep the label and hide the value.
 * - A sub-resource is named for itself and carries `parent_type`: `POST /resource-servers/{id}/actions`
 *   is `action.create`, unless [parentActions] reports it as an action on the parent.
 * - A change word, or a word listed in [actions], ending a POST to a sub-collection is an action on the
 *   parent: `POST /groups/{id}/members/add` is `group.member-add`, with `sub_type: member`. Any other word
 *   there is an item value: `POST /groups/{id}/members/alice` is `member.create`. Words after an id are an
 *   action on the item:
 *   `POST /applications/{id}/client-secret/regenerate` is `application.client-secret-regenerate`.
 * - Words after a collection's address word listed in [addressWords], such as `tree` in
 *   `/organization-units/tree/a/b`, are an address: route `/organization-units/tree/{path}`. Words after an
 *   item that is itself a word are part of its address too, and never part of a name.
 * - A path of words alone is named from them: `POST /import` is `api.import`.
 * - Any other change is `api.call`, with its method and a path template that keeps only the first
 *   segment, so nothing is lost.
 *
 * Only `POST`, `PUT`, `PATCH` and `DELETE` are named. Which calls to skip, and any name an API needs
 * beyond these rules, come from configuration.
 *
 * @param bases The base URLs of the APIs that are watched.
 * @param parentActions Sub-collections reported as an action on their parent, by change.
 * @param kinds Top-level collections whose next path word is a kind, such as `connections`.
 * @param addressWords Words that, right after a collection, start an address, such as `tree`.
 * @param actions Words that, ending a POST to a sub-collection, are an action on the parent, such as
 * `suspend`.
 */
internal class TelemetryApiRules(
    private val bases: List<String>,
    private val parentActions: Map<String, TelemetryParentActions> = emptyMap(),
    kinds: List<String> = emptyList(),
    addressWords: List<String> = emptyList(),
    actions: List<String> = emptyList(),
) {
    private val kinds = kinds.map { it.lowercase() }.toSet()
    private val addressWords = addressWords.map { it.lowercase() }.toSet()
    private val actions = actions.map { it.lowercase() }.toSet()

    /**
     * Names a call, or returns `null` when it is not reported.
     *
     * @param method The HTTP method.
     * @param url The absolute URL called.
     */
    fun classify(
        method: String?,
        url: String?,
    ): TelemetryApiCall? {
        val verb = (method ?: "GET").uppercase()
        if (change(verb) == null || url.isNullOrEmpty()) return null
        val path = pathBelowBase(url)
        if (path.isNullOrEmpty()) return null
        return name(verb, path) ?: TelemetryApiCall(API_CALL, route(path), mapOf("method" to verb))
    }

    /**
     * The path template of a URL below a watched base, or `null` when the URL is not under one. Only the
     * first segment is kept, when it is a word; every later segment, which can be an id, a handle or a name
     * someone chose, is `{id}`.
     */
    fun route(url: String): String? = pathBelowBase(url)?.let { route(it) }

    /**
     * The path of a URL below a watched base, in lower case, with only what cannot be a word replaced by
     * `{id}`, or `null` when the URL is not under one. Used only to match configured patterns; never sent.
     */
    fun path(url: String): String? = pathBelowBase(url)?.let { shape(it) }

    /**
     * The path of a URL below a watched base, in lower case and as called, with no value replaced, or
     * `null` when the URL is not under one. Used only to match configured exclusions; never sent.
     */
    fun literalPath(url: String): String? = pathBelowBase(url)?.let { path -> join(path.map { it.lowercase() }) }

    /** Names a change from its method and path segments, or returns `null` when no rule fits. */
    private fun name(
        verb: String,
        path: List<String>,
    ): TelemetryApiCall? {
        val change = change(verb) ?: return null
        val route = ArrayList<String>()
        var index = 0

        // Words in front of the first collection, such as `design` in `/design/themes`.
        while (index < path.size && !isCollection(path[index])) {
            if (!TEMPLATE_WORD.matches(path[index])) break
            route.add(path[index].lowercase())
            index += 1
        }
        if (index == path.size || !isCollection(path[index])) {
            // No collection: `PUT /server-config/{name}` is a change to `server-config`.
            if (path.size == 2 && change != Change.CREATE && WORD.matches(path[0])) {
                val type = path[0].lowercase()
                return TelemetryApiCall("$type.${change.value}", "/$type/$ID", mapOf(RESOURCE_TYPE to type))
            }
            // No resource type, only words: `POST /import` is `api.import`, `POST /import/managed` is
            // `api.import-managed`, and `PUT /maintenance` is `api.maintenance-update`. Only letter words and
            // versions such as `v2` make a name, so an id with digits, such as a client id, never does.
            if (index == path.size && path.all { WORD.matches(it) || VERSION.matches(it.lowercase()) }) {
                val suffix = if (verb == "POST") "" else "-${change.value}"
                return TelemetryApiCall("api.${route.joinToString("-")}$suffix", join(route), emptyMap())
            }
            return null
        }

        var parent: String? = null
        while (true) {
            val collection = path[index].lowercase()
            val type = TelemetryNames.singular(collection)
            route.add(collection)
            index += 1
            val data: Map<String, String> = if (parent != null) mapOf(PARENT_TYPE to parent) else emptyMap()

            // The collection itself: `POST /groups`, or `PUT /flows/{id}/variables` for all of them.
            if (index == path.size) return changeOf(change, type, collection, parent, data, route)

            // A configured address word: everything after it is an address, as in
            // `/organization-units/tree/engineering/operations`, and never part of a name.
            if (path[index].lowercase() in addressWords) {
                route.add(path[index].lowercase())
                route.add("{path}")
                return changeOf(change, type, collection, parent, data, route)
            }

            // A change word, or a configured action, that ends a POST to a sub-collection is an action on the
            // parent: `POST /groups/{id}/members/add` is `group.member-add`. Any other word there is an item
            // value, so `POST /groups/{id}/members/alice` is `member.create`.
            val last = path[index].lowercase()
            if (parent != null &&
                verb == "POST" &&
                index == path.size - 1 &&
                WORD.matches(path[index]) &&
                (last in CHANGE_WORDS || last in actions)
            ) {
                val action = last
                route.add(action)
                return TelemetryApiCall(
                    "$parent.$type-$action",
                    join(route),
                    mapOf(RESOURCE_TYPE to parent, SUB_TYPE to type),
                )
            }

            // A kind after a top-level collection listed in [kinds]: `/connections/google/{id}`, or
            // `POST /connections/google` for a new one of that kind. After any other top-level collection the
            // word is an item value.
            val segment = path[index]
            val isKind = parent == null && collection in kinds && WORD.matches(segment)
            val itemData = if (isKind) data + ("kind" to segment.lowercase()) else data

            // A label and its value in front of a sub-collection, such as `ns/{namespace}` in
            // `/languages/{id}/translations/ns/{namespace}/keys`: the label is kept and the value is not.
            val afterValue = path.getOrNull(index + 2)
            if (parent != null &&
                !isKind &&
                WORD.matches(segment) &&
                !isChangeWord(segment) &&
                afterValue != null &&
                isCollection(afterValue)
            ) {
                route.add(segment.lowercase())
                route.add(ID)
                index += 2
                parent = type
                continue
            }

            if (isKind) {
                route.add(segment.lowercase())
                index += 1
                if (index == path.size) return changeOf(change, type, collection, parent, itemData, route)
            }

            // The item: an id, or a word that a collection or an action follows, such as `me` or `en`.
            val itemIsWord = WORD.matches(path[index])
            route.add(ID)
            index += 1
            // `POST /<collection>/{id}` creates one at an id the caller chose.
            if (index == path.size) return changeOf(change, type, collection, parent, itemData, route)

            val rest = path.subList(index, path.size).map { it.lowercase() }

            // A sub-resource of the item.
            if (isCollection(path[index])) {
                parent = type
                continue
            }

            // Words after an item that is itself a word, as in `/organization-units/tree/eng/backend`, can be
            // values someone chose, such as handles in a path, so they are part of the item's address and
            // never part of a name. One segment that starts with a change word, such as
            // `/users/me/update-credentials`, is still an action.
            if (itemIsWord && !(rest.size == 1 && isChangeWord(rest[0]))) {
                route.add("{path}")
                return changeOf(change, type, collection, parent, itemData, route)
            }

            // An action on the item: `client-secret/regenerate`, `restore`, `update-credentials`, or a
            // setting such as `PUT /projects/{id}/auto-sync`.
            if (!rest.all { WORD.matches(it) }) return null
            route.addAll(rest)
            val suffix = if (verb == "POST") "" else "-${change.value}"
            return TelemetryApiCall(
                "$type.${rest.joinToString("-")}$suffix",
                join(route),
                itemData + (RESOURCE_TYPE to type),
            )
        }
    }

    /**
     * A create, update or delete of a collection's resource, or the parent's action for a sub-resource
     * such as a sharing policy.
     */
    private fun changeOf(
        change: Change,
        type: String,
        collection: String,
        parent: String?,
        data: Map<String, String>,
        route: List<String>,
    ): TelemetryApiCall {
        val joined = join(route)
        val actions = if (parent != null) parentActions[collection] else null
        val parentAction =
            when (change) {
                Change.CREATE -> actions?.create
                Change.UPDATE -> actions?.update
                Change.DELETE -> actions?.delete
            }
        if (parent != null && parentAction != null) {
            return TelemetryApiCall("$parent.$parentAction", joined, data - PARENT_TYPE + (RESOURCE_TYPE to parent))
        }
        return TelemetryApiCall("$type.${change.value}", joined, data + (RESOURCE_TYPE to type))
    }

    /** The path segments of [url] below the longest watched base it falls under, or `null`. */
    private fun pathBelowBase(url: String): List<String>? {
        val called = parseAbsolute(url) ?: return null
        val calledPath = called.rawPath.orEmpty()
        val prefix =
            bases
                .mapNotNull { base ->
                    val root = parseAbsolute(base) ?: return@mapNotNull null
                    val prefix = root.rawPath.orEmpty().trimEnd('/')
                    val under = prefix.isEmpty() || calledPath == prefix || calledPath.startsWith("$prefix/")
                    prefix.takeIf { origin(called) == origin(root) && under }
                }.maxByOrNull { it.length } ?: return null
        return calledPath.substring(prefix.length).split("/").filter { it.isNotEmpty() }
    }

    companion object {
        private const val RESOURCE_TYPE = "resource_type"
        private const val PARENT_TYPE = "parent_type"
        private const val SUB_TYPE = "sub_type"
        private const val API_CALL = "api.call"
        private const val ID = "{id}"

        /** A plain word in a path: letters and hyphens. Anything else, such as a UUID or a name, is a value. */
        private val WORD = Regex("^[A-Za-z][A-Za-z-]{0,39}$")

        /** A path segment kept in a template: a word that may hold a version digit, such as `v2` or `oauth2`. */
        private val TEMPLATE_WORD = Regex("^\\.?[A-Za-z][A-Za-z0-9-]{0,39}$")

        /** An API version in a path, such as `v2`. */
        private val VERSION = Regex("^v\\d{1,2}$")

        /**
         * The words of the naming scheme itself. A hyphenated segment that starts with one, such as
         * `update-credentials`, is an action and not a collection, even though it ends in `s`.
         */
        private val CHANGE_WORDS = setOf("add", "create", "delete", "remove", "update")

        private val ERROR_CODE = Regex("^[A-Z][A-Z0-9_]*-\\d+$")

        private fun change(verb: String): Change? =
            when (verb) {
                "POST" -> Change.CREATE
                "PUT", "PATCH" -> Change.UPDATE
                "DELETE" -> Change.DELETE
                else -> null
            }

        private fun join(route: List<String>): String = "/" + route.joinToString("/")

        /** The path template of [path]: see [route]. */
        private fun route(path: List<String>): String =
            join(path.mapIndexed { index, segment -> if (index == 0 && TEMPLATE_WORD.matches(segment)) segment.lowercase() else ID })

        /** The path below a watched base: see [path]. */
        private fun shape(path: List<String>): String = join(path.map { if (TEMPLATE_WORD.matches(it)) it.lowercase() else ID })

        /** Whether a segment starts with a word of the naming scheme, such as `update-credentials`. */
        private fun isChangeWord(segment: String): Boolean =
            WORD.matches(segment) && segment.lowercase().substringBefore('-') in CHANGE_WORDS

        /** Whether a segment names a collection, such as `applications`, and is not an action. */
        private fun isCollection(segment: String): Boolean =
            segment.length > 2 &&
                WORD.matches(segment) &&
                !isChangeWord(segment) &&
                TelemetryNames.isCollection(segment.lowercase())

        /** Whether [code] has the server's error code format, such as `APP-1023`. */
        fun isErrorCode(code: String?): Boolean = code != null && ERROR_CODE.matches(code)

        /**
         * What a failed call is reported with: the HTTP status (or `network` when there was no response)
         * and the API's own error code, such as `APP-1023`, when it gave one. Never the message, which can
         * quote what the user typed.
         */
        fun failureData(
            status: Int?,
            responseBody: String?,
        ): Map<String, String> {
            val data = linkedMapOf("status" to (status?.toString() ?: "network"))
            val code = parseObject(responseBody)?.get("code")?.stringOrNull()
            if (isErrorCode(code)) data["error_code"] = code!!
            return data
        }

        /**
         * Whether a JSON request body sets [field] to a value other than `false`, `null` or an empty
         * string. Only read here, never sent.
         */
        fun sets(
            body: String?,
            field: String,
        ): Boolean {
            val value = parseObject(body)?.get(field) ?: return false
            if (value.isJsonNull) return false
            if (!value.isJsonPrimitive) return true
            val primitive = value.asJsonPrimitive
            return when {
                primitive.isBoolean -> primitive.asBoolean
                primitive.isString -> primitive.asString.isNotEmpty()
                else -> true
            }
        }

        private fun JsonElement.stringOrNull(): String? = if (isJsonPrimitive && asJsonPrimitive.isString) asString else null

        private fun parseObject(body: String?): JsonObject? {
            if (body.isNullOrEmpty()) return null
            return runCatching { JsonParser.parseString(body) }.getOrNull()?.takeIf { it.isJsonObject }?.asJsonObject
        }

        /** [value] as an absolute URI with a scheme and a host, or `null`. */
        fun parseAbsolute(value: String): URI? =
            runCatching { URI(value) }.getOrNull()?.takeIf { it.isAbsolute && !it.host.isNullOrEmpty() }

        /** The origin of an absolute URI: its scheme, host and port, with the scheme's default port filled in. */
        fun origin(uri: URI): String {
            val scheme = uri.scheme.lowercase()
            val port =
                when {
                    uri.port != -1 -> uri.port
                    scheme == "https" -> 443
                    scheme == "http" -> 80
                    else -> -1
                }
            return "$scheme://${uri.host.lowercase()}:$port"
        }
    }
}
