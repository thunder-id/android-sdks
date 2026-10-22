// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.management

import com.google.gson.Gson
import dev.thunderid.android.IAMException
import dev.thunderid.android.ThunderIDErrorCode
import dev.thunderid.android.ThunderIDFetcher
import dev.thunderid.android.http.HttpClient
import java.net.URLEncoder

/**
 * Sends management API requests for one resource collection. The collection URL comes from the
 * `endpoints` override when set, otherwise `{baseUrl}/{collection}`.
 */
internal class ManagementTransport(
    @PublishedApi internal val httpClient: HttpClient,
    collectionUrl: String,
    @PublishedApi internal val fetcher: ThunderIDFetcher?,
) {
    private val collectionUrl = collectionUrl.trimEnd('/')

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
        val queryString =
            query.entries
                .filter { it.value != null }
                .joinToString("&") { "${encode(it.key)}=${encode(it.value!!)}" }
        return if (queryString.isEmpty()) resourceUrl else "$resourceUrl?$queryString"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}
