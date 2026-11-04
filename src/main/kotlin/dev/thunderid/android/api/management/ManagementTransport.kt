// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.management

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonNull
import dev.thunderid.android.IAMException
import dev.thunderid.android.ThunderIDErrorCode
import dev.thunderid.android.ThunderIDFetcher
import dev.thunderid.android.http.HttpClient
import java.net.URLEncoder

/**
 * Sends management API requests for one resource collection. The collection URL comes from the
 * `endpoints` override when set, otherwise `{baseUrl}/{collection}`.
 *
 * [serverUrl] is the server's `baseUrl`. Server-level calls, such as the administration flows, go there
 * whatever collection URL the resource was given.
 */
internal class ManagementTransport(
    @PublishedApi internal val httpClient: HttpClient,
    collectionUrl: String,
    @PublishedApi internal val fetcher: ThunderIDFetcher?,
    private val serverUrl: String? = null,
) {
    private val collectionUrl = collectionUrl.trimEnd('/')

    /**
     * A transport for the server-level collection at `{serverUrl}/{path}`, sharing this transport's
     * HTTP client and fetcher. Throws when no server URL is known, since a collection URL alone does not
     * say where the server is.
     */
    fun server(
        path: String,
        purpose: String = "run an administration flow",
    ): ManagementTransport {
        val base =
            serverUrl?.trimEnd('/')?.takeIf { it.isNotBlank() }
                ?: throw IAMException(
                    ThunderIDErrorCode.INVALID_CONFIGURATION,
                    "A baseUrl is required to $purpose: it needs the server URL, not only a collection URL",
                )
        return ManagementTransport(httpClient, "$base/$path", fetcher, serverUrl)
    }

    suspend inline fun <reified T : Any> list(
        query: Map<String, String?>,
        fetcher: ThunderIDFetcher?,
    ): T = httpClient.send("GET", url(null, query), null, fetcher ?: this.fetcher)

    suspend inline fun <reified T : Any> get(
        id: String,
        query: Map<String, String?> = emptyMap(),
        fetcher: ThunderIDFetcher?,
    ): T = httpClient.send("GET", url(id, query), null, fetcher ?: this.fetcher)

    suspend inline fun <reified T : Any> create(
        body: Any,
        fetcher: ThunderIDFetcher?,
    ): T = httpClient.send("POST", url(null, emptyMap()), Gson().toJson(body), fetcher ?: this.fetcher)

    suspend inline fun <reified T : Any> update(
        id: String,
        body: Any,
        fetcher: ThunderIDFetcher?,
    ): T = httpClient.send("PUT", url(id, emptyMap()), Gson().toJson(body), fetcher ?: this.fetcher)

    suspend fun delete(
        id: String,
        fetcher: ThunderIDFetcher?,
    ) {
        httpClient.send<Unit>("DELETE", url(id, emptyMap()), null, fetcher ?: this.fetcher)
    }

    /** Reads a sub-resource of one resource, such as `{collection}/{id}/members`. */
    suspend inline fun <reified T : Any> getSubResource(
        id: String,
        subPath: String,
        query: Map<String, String?>,
        fetcher: ThunderIDFetcher?,
    ): T = httpClient.send("GET", url(id, query, subPath), null, fetcher ?: this.fetcher)

    /** Posts to a sub-resource of one resource, such as `{collection}/{id}/members/add`. */
    suspend inline fun <reified T : Any> postSubResource(
        id: String,
        subPath: String,
        body: Any,
        fetcher: ThunderIDFetcher?,
    ): T = httpClient.send("POST", url(id, emptyMap(), subPath), Gson().toJson(body), fetcher ?: this.fetcher)

    /**
     * Sends [method] to `{collection}/{segments…}`, each segment percent-encoded on its own, with [body]
     * as JSON when given. A blank segment is refused with [ThunderIDErrorCode.INVALID_INPUT] before any
     * request is made. Use [treePath] to turn an organization unit handle path into segments.
     */
    suspend inline fun <reified T : Any> request(
        method: String,
        segments: List<String> = emptyList(),
        query: Map<String, String?> = emptyMap(),
        body: Any? = null,
        fetcher: ThunderIDFetcher?,
    ): T = httpClient.send(method, path(segments, query), encodeBody(body), fetcher ?: this.fetcher)

    /** [body] as JSON. A [JsonBody] is sent as it is, so it can carry an explicit `null` member. */
    @PublishedApi
    internal fun encodeBody(body: Any?): String? =
        when (body) {
            null -> null
            is JsonBody -> body.json
            else -> Gson().toJson(body)
        }

    @PublishedApi
    internal fun path(
        segments: List<String>,
        query: Map<String, String?>,
    ): String {
        if (segments.any { it.isBlank() }) {
            throw IAMException(ThunderIDErrorCode.INVALID_INPUT, "A resource identifier is required")
        }
        val resourceUrl = (listOf(collectionUrl) + segments.map(::encode)).joinToString("/")
        val queryString = queryString(query)
        return if (queryString.isEmpty()) resourceUrl else "$resourceUrl?$queryString"
    }

    @PublishedApi
    internal fun url(
        id: String?,
        query: Map<String, String?>,
        subPath: String? = null,
    ): String {
        if (id != null && id.isBlank()) {
            throw IAMException(ThunderIDErrorCode.INVALID_INPUT, "A resource identifier is required")
        }
        val itemUrl = if (id == null) collectionUrl else "$collectionUrl/${encode(id)}"
        val resourceUrl = if (id != null && subPath != null) "$itemUrl/$subPath" else itemUrl
        val queryString = queryString(query)
        return if (queryString.isEmpty()) resourceUrl else "$resourceUrl?$queryString"
    }

    private fun queryString(query: Map<String, String?>): String =
        query.entries
            .filter { it.value != null }
            .joinToString("&") { "${encode(it.key)}=${encode(it.value!!)}" }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}

/**
 * The segments of an organization unit handle path such as `engineering/platform`, for addressing
 * `{collection}/tree/{path}`. Each segment is encoded on its own and the slashes are kept. Leading and
 * trailing slashes are ignored; an empty path, or an empty segment inside it, is refused with
 * [ThunderIDErrorCode.INVALID_INPUT].
 */
internal fun treePath(path: String): List<String> {
    val segments = path.trim('/').split('/')
    if (segments.any { it.isBlank() }) {
        throw IAMException(ThunderIDErrorCode.INVALID_INPUT, "An organization unit path is required, such as \"a/b/c\"")
    }
    return segments
}

/** A request body already encoded as JSON, for a payload that must send an explicit `null`. */
internal class JsonBody(
    val json: String,
)

/**
 * [body] as JSON with [nullMembers] present as `null` when the object leaves them out, for a field the
 * server requires but accepts as `null`, such as an organization unit's `parent`.
 */
internal fun jsonWithNulls(
    body: Any,
    vararg nullMembers: String,
): JsonBody {
    val tree = Gson().toJsonTree(body).asJsonObject
    nullMembers.filterNot(tree::has).forEach { tree.add(it, JsonNull.INSTANCE) }
    return JsonBody(GsonBuilder().serializeNulls().create().toJson(tree))
}
