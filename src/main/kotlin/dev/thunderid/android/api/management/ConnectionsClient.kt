// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.management

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import dev.thunderid.android.IAMException
import dev.thunderid.android.ThunderIDErrorCode
import dev.thunderid.android.ThunderIDFetcher

/**
 * Connection management operations, reached through `ThunderIDClient.connections`, against the
 * `connections` collection: the identity providers, message senders, authorization PDPs and HTTP endpoints
 * the server connects to. Payloads can hold credentials, so the SDK never logs them. Each call accepts an
 * optional [ThunderIDFetcher] that takes precedence over `ThunderIDConfig.http.fetcher`.
 */
class ConnectionsClient internal constructor(
    private val transport: ManagementTransport,
) {
    /**
     * Returns the configured connections.
     *
     * Without [type] it returns a page of every connection (`GET /connections`), optionally narrowed to a
     * [category]. With [type] it returns every connection of that vendor (`GET /connections/{type}`), which
     * the server does not page or filter, so [category], [limit], [offset] and [ouId] are not sent.
     *
     * @param ouId Lists the connections this organization unit owns, followed by the ones shared into it,
     *   instead of every connection the caller can see.
     */
    suspend fun list(
        type: ConnectionType? = null,
        category: ConnectionCategory? = null,
        limit: Int? = null,
        offset: Int? = null,
        fetcher: ThunderIDFetcher? = null,
        ouId: String? = null,
    ): ConnectionListResponse {
        if (type == null) {
            val filter = ouId?.let { ManagementFilter.equality("ouId", it) }
            return transport.list(mapOf("category" to category?.value, "filter" to filter) + page(limit, offset), fetcher)
        }
        val items: Array<ConnectionInstance>? = transport.request("GET", listOf(type.value), fetcher = fetcher)
        val connections = items.orEmpty().map { it.copy(type = type, categories = it.categories.orEmpty()) }
        return ConnectionListResponse(
            totalResults = connections.size,
            startIndex = 1,
            count = connections.size,
            connections = connections,
        )
    }

    /** Returns a single connection, with its secrets masked. */
    suspend fun get(
        type: ConnectionType,
        id: String,
        fetcher: ThunderIDFetcher? = null,
    ): Connection = transport.request<JsonObject>("GET", listOf(type.value, id), fetcher = fetcher).toConnection()

    /** Creates a connection of [type] and returns it as the server stored it. */
    suspend fun create(
        type: ConnectionType,
        connection: ConnectionRequest,
        fetcher: ThunderIDFetcher? = null,
    ): Connection =
        transport
            .request<JsonObject>("POST", listOf(type.value), body = connection.toBody(), fetcher = fetcher)
            .toConnection()

    /** Replaces a connection and returns the updated connection. */
    suspend fun update(
        type: ConnectionType,
        id: String,
        connection: ConnectionRequest,
        fetcher: ThunderIDFetcher? = null,
    ): Connection =
        transport
            .request<JsonObject>("PUT", listOf(type.value, id), body = connection.toBody(), fetcher = fetcher)
            .toConnection()

    /** Deletes a connection. The server refuses while a resource that restricts the deletion uses it. */
    suspend fun delete(
        type: ConnectionType,
        id: String,
        fetcher: ThunderIDFetcher? = null,
    ) {
        transport.request<Unit>("DELETE", listOf(type.value, id), fetcher = fetcher)
    }

    /**
     * Returns the resources that reference a connection. HTTP connections have no usages endpoint, so
     * [ConnectionType.HTTP] is refused with [ThunderIDErrorCode.UNSUPPORTED_OPERATION] without a request.
     */
    suspend fun getUsages(
        type: ConnectionType,
        id: String,
        fetcher: ThunderIDFetcher? = null,
    ): ResourceUsagesResponse {
        if (type == ConnectionType.HTTP) {
            throw IAMException(ThunderIDErrorCode.UNSUPPORTED_OPERATION, "HTTP connections do not report usages")
        }
        return transport.request("GET", listOf(type.value, id, "usages"), fetcher = fetcher)
    }

    /** Describes what each connection vendor can be configured with, or only [vendor] when given. */
    suspend fun getMeta(
        vendor: ConnectionType? = null,
        fetcher: ThunderIDFetcher? = null,
    ): ConnectionMetaResponse {
        val query = mapOf("vendor" to vendor?.value)
        return transport.request("GET", listOf("meta"), query, fetcher = fetcher)
    }
}

private val COMMON_FIELDS = setOf("id", "name", "description", "type")

private val propertiesType = object : TypeToken<Map<String, Any?>>() {}.type

// The vendor's fields go at the top level of the body, next to the name and description.
private fun ConnectionRequest.toBody(): Map<String, Any?> =
    (properties - COMMON_FIELDS + mapOf("name" to name, "description" to description)).filterValues { it != null }

private fun JsonObject?.toConnection(): Connection {
    val all: Map<String, Any?> = this?.let { Gson().fromJson<Map<String, Any?>>(it, propertiesType) }.orEmpty()
    return Connection(
        id = all["id"] as? String ?: "",
        name = all["name"] as? String ?: "",
        description = all["description"] as? String,
        type = all["type"] as? String,
        properties = all - COMMON_FIELDS,
    )
}
