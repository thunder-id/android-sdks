// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.management

import dev.thunderid.android.ThunderIDFetcher

/**
 * Project management operations, reached through `ThunderIDClient.projects`, against the `projects` collection.
 * Projects are a Control Plane concept: only the Control Plane serves them. Each call accepts an optional
 * [ThunderIDFetcher] that takes precedence over `ThunderIDConfig.http.fetcher`.
 */
class ProjectsClient internal constructor(
    private val transport: ManagementTransport,
) {
    /** Returns a page of projects. */
    suspend fun list(
        limit: Int? = null,
        offset: Int? = null,
        fetcher: ThunderIDFetcher? = null,
    ): ProjectListResponse = transport.list(page(limit, offset), fetcher)

    /** Returns a single project. */
    suspend fun get(
        id: String,
        fetcher: ThunderIDFetcher? = null,
    ): Project = transport.get(id, fetcher = fetcher)

    /** Creates a project and returns it as the server stored it. */
    suspend fun create(
        project: CreateProjectRequest,
        fetcher: ThunderIDFetcher? = null,
    ): Project = transport.create(project, fetcher)

    /** Replaces a project's display name and description and returns the updated project. */
    suspend fun update(
        id: String,
        project: UpdateProjectRequest,
        fetcher: ThunderIDFetcher? = null,
    ): Project = transport.update(id, project, fetcher)

    /** Deletes a project. */
    suspend fun delete(
        id: String,
        fetcher: ThunderIDFetcher? = null,
    ) = transport.delete(id, fetcher)
}
