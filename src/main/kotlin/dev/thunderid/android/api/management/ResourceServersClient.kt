// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.management

import dev.thunderid.android.ThunderIDFetcher

/**
 * Resource server, resource and action management operations, reached through
 * `ThunderIDClient.resourceServers`.
 *
 * An action belongs to the resource server itself (`{collection}/{rsId}/actions`) or, when a `resourceId`
 * is given, to one of its resources (`{collection}/{rsId}/resources/{resourceId}/actions`). Each call
 * accepts an optional [ThunderIDFetcher] that takes precedence over `ThunderIDConfig.http.fetcher`.
 */
class ResourceServersClient internal constructor(
    private val transport: ManagementTransport,
) {
    private val administration = AdministrationClient(transport)

    /** Returns a page of resource servers. */
    suspend fun list(
        limit: Int? = null,
        offset: Int? = null,
        fetcher: ThunderIDFetcher? = null,
    ): ResourceServerListResponse = transport.list(page(limit, offset), fetcher)

    /** Returns a single resource server. */
    suspend fun get(
        id: String,
        fetcher: ThunderIDFetcher? = null,
    ): ResourceServer = transport.get(id, fetcher = fetcher)

    /** Creates a resource server and returns it as the server stored it. */
    suspend fun create(
        resourceServer: CreateResourceServerRequest,
        fetcher: ThunderIDFetcher? = null,
    ): ResourceServer = transport.create(resourceServer, fetcher)

    /** Updates a resource server and returns the updated resource server. */
    suspend fun update(
        id: String,
        resourceServer: UpdateResourceServerRequest,
        fetcher: ThunderIDFetcher? = null,
    ): ResourceServer = transport.update(id, resourceServer, fetcher)

    /** Deletes a resource server. */
    suspend fun delete(
        id: String,
        fetcher: ThunderIDFetcher? = null,
    ) = transport.delete(id, fetcher)

    /**
     * Returns a page of a resource server's resources: the top-level ones, or the direct children of
     * [parentId] when it is given.
     */
    suspend fun listResources(
        resourceServerId: String,
        parentId: String? = null,
        limit: Int? = null,
        offset: Int? = null,
        fetcher: ThunderIDFetcher? = null,
    ): ResourceListResponse =
        transport.request(
            "GET",
            resources(resourceServerId),
            mapOf("parentId" to parentId) + page(limit, offset),
            fetcher = fetcher,
        )

    /** Returns a single resource. */
    suspend fun getResource(
        resourceServerId: String,
        resourceId: String,
        fetcher: ThunderIDFetcher? = null,
    ): ManagedResource = transport.request("GET", resources(resourceServerId) + resourceId, fetcher = fetcher)

    /** Creates a resource and returns it as the server stored it. */
    suspend fun createResource(
        resourceServerId: String,
        resource: CreateResourceRequest,
        fetcher: ThunderIDFetcher? = null,
    ): ManagedResource = transport.request("POST", resources(resourceServerId), body = resource, fetcher = fetcher)

    /** Updates a resource's name and description and returns the updated resource. */
    suspend fun updateResource(
        resourceServerId: String,
        resourceId: String,
        resource: UpdateResourceRequest,
        fetcher: ThunderIDFetcher? = null,
    ): ManagedResource = transport.request("PUT", resources(resourceServerId) + resourceId, body = resource, fetcher = fetcher)

    /** Deletes a resource. */
    suspend fun deleteResource(
        resourceServerId: String,
        resourceId: String,
        fetcher: ThunderIDFetcher? = null,
    ) {
        transport.request<Unit>("DELETE", resources(resourceServerId) + resourceId, fetcher = fetcher)
    }

    /**
     * Returns a page of the actions of a resource server, or of its resource [resourceId] when given.
     *
     * @param kind Only the actions of this MCP primitive kind; all kinds when `null`.
     */
    suspend fun listActions(
        resourceServerId: String,
        resourceId: String? = null,
        kind: ActionKind? = null,
        limit: Int? = null,
        offset: Int? = null,
        fetcher: ThunderIDFetcher? = null,
    ): ActionListResponse =
        transport.request(
            "GET",
            actions(resourceServerId, resourceId),
            mapOf("kind" to kind?.value) + page(limit, offset),
            fetcher = fetcher,
        )

    /** Returns a single action of a resource server, or of its resource [resourceId] when given. */
    suspend fun getAction(
        resourceServerId: String,
        actionId: String,
        resourceId: String? = null,
        fetcher: ThunderIDFetcher? = null,
    ): ManagedAction = transport.request("GET", actions(resourceServerId, resourceId) + actionId, fetcher = fetcher)

    /** Creates an action on a resource server, or on its resource [resourceId] when given. */
    suspend fun createAction(
        resourceServerId: String,
        action: CreateActionRequest,
        resourceId: String? = null,
        fetcher: ThunderIDFetcher? = null,
    ): ManagedAction = transport.request("POST", actions(resourceServerId, resourceId), body = action, fetcher = fetcher)

    /** Updates an action's name and description and returns the updated action. */
    suspend fun updateAction(
        resourceServerId: String,
        actionId: String,
        action: UpdateActionRequest,
        resourceId: String? = null,
        fetcher: ThunderIDFetcher? = null,
    ): ManagedAction = transport.request("PUT", actions(resourceServerId, resourceId) + actionId, body = action, fetcher = fetcher)

    /**
     * Deletes an action of a resource server, or of its resource [resourceId] when given. With
     * [AdministrationMode.FLOW] it runs the server's [AdministrationFlowConfigKey.ACTION_DELETION] flow
     * instead, when one is configured, with the action, the resource server and the resource as inputs.
     */
    suspend fun deleteAction(
        resourceServerId: String,
        actionId: String,
        resourceId: String? = null,
        fetcher: ThunderIDFetcher? = null,
        administrationMode: AdministrationMode = AdministrationMode.NATIVE,
    ) {
        // Builds the URL first, so an empty identifier is refused before anything runs.
        val segments = actions(resourceServerId, resourceId) + actionId
        transport.path(segments, emptyMap())
        if (administrationMode == AdministrationMode.FLOW) {
            val inputs =
                buildMap {
                    put(AdministrationFlowInput.ACTION, actionId)
                    put(AdministrationFlowInput.RESOURCE_SERVER, resourceServerId)
                    resourceId?.let { put(AdministrationFlowInput.RESOURCE, it) }
                }
            administration.runFlow(AdministrationFlowConfigKey.ACTION_DELETION, inputs, "action deletion", fetcher)?.let { return }
        }
        transport.request<Unit>("DELETE", segments, fetcher = fetcher)
    }

    private fun resources(resourceServerId: String): List<String> = listOf(resourceServerId, "resources")

    private fun actions(
        resourceServerId: String,
        resourceId: String?,
    ): List<String> =
        if (resourceId == null) {
            listOf(resourceServerId, "actions")
        } else {
            resources(resourceServerId) + listOf(resourceId, "actions")
        }
}
