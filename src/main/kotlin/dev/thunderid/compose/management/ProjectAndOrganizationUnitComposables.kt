// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.management

import androidx.compose.runtime.Composable
import dev.thunderid.android.ThunderIDFetcher
import dev.thunderid.android.api.management.CreateProjectRequest
import dev.thunderid.android.api.management.OrganizationUnit
import dev.thunderid.android.api.management.OrganizationUnitGroupListResponse
import dev.thunderid.android.api.management.OrganizationUnitListResponse
import dev.thunderid.android.api.management.OrganizationUnitQueryKeys
import dev.thunderid.android.api.management.OrganizationUnitRequest
import dev.thunderid.android.api.management.OrganizationUnitRoleListResponse
import dev.thunderid.android.api.management.OrganizationUnitUserListResponse
import dev.thunderid.android.api.management.Project
import dev.thunderid.android.api.management.ProjectListResponse
import dev.thunderid.android.api.management.ProjectQueryKeys
import dev.thunderid.android.api.management.UpdateProjectRequest

private fun page(
    limit: Int?,
    offset: Int?,
): List<String> = listOf("limit=${limit ?: ""}", "offset=${offset ?: ""}")

/** A project has a root organization unit of its own, so a change to one reaches the organization unit queries too. */
internal val projectAndOrganizationUnitKeys: List<List<String>> =
    listOf(
        listOf(ProjectQueryKeys.PROJECTS),
        listOf(ProjectQueryKeys.PROJECT),
        listOf(OrganizationUnitQueryKeys.ORGANIZATION_UNITS),
        listOf(OrganizationUnitQueryKeys.ORGANIZATION_UNIT),
    )

// MARK: - Projects

@Composable
fun rememberGetProjects(
    limit: Int? = null,
    offset: Int? = null,
    fetcher: ThunderIDFetcher? = null,
    enabled: Boolean = true,
): ResourceQueryState<ProjectListResponse> =
    rememberResourceQuery(listOf(ProjectQueryKeys.PROJECTS) + page(limit, offset), enabled) {
        it.projects.list(limit, offset, fetcher)
    }

@Composable
fun rememberGetProject(
    id: String?,
    fetcher: ThunderIDFetcher? = null,
): ResourceQueryState<Project> =
    rememberResourceQuery(listOf(ProjectQueryKeys.PROJECT, id.orEmpty()), enabled = id != null) {
        it.projects.get(id!!, fetcher)
    }

@Composable
fun rememberCreateProject(fetcher: ThunderIDFetcher? = null): ResourceMutationState<CreateProjectRequest, Project> =
    rememberResourceMutation({ projectAndOrganizationUnitKeys }) { client, request -> client.projects.create(request, fetcher) }

@Composable
fun rememberUpdateProject(fetcher: ThunderIDFetcher? = null): ResourceMutationState<Pair<String, UpdateProjectRequest>, Project> =
    rememberResourceMutation({ projectAndOrganizationUnitKeys }) { client, (id, request) -> client.projects.update(id, request, fetcher) }

@Composable
fun rememberDeleteProject(fetcher: ThunderIDFetcher? = null): ResourceMutationState<String, Unit> =
    rememberResourceMutation({ projectAndOrganizationUnitKeys }) { client, id -> client.projects.delete(id, fetcher) }

// MARK: - Organization units

/** Loads a page of the root organization units, or of the children of [parentId] when it is set. */
@Composable
fun rememberGetOrganizationUnits(
    parentId: String? = null,
    limit: Int? = null,
    offset: Int? = null,
    fetcher: ThunderIDFetcher? = null,
    enabled: Boolean = true,
): ResourceQueryState<OrganizationUnitListResponse> =
    rememberResourceQuery(
        listOf(OrganizationUnitQueryKeys.ORGANIZATION_UNITS, "parent=${parentId.orEmpty()}") + page(limit, offset),
        enabled,
    ) {
        if (parentId ==
            null
        ) {
            it.organizationUnits.list(limit, offset, fetcher = fetcher)
        } else {
            it.organizationUnits.listChildren(parentId, limit, offset, fetcher = fetcher)
        }
    }

@Composable
fun rememberGetOrganizationUnit(
    id: String?,
    fetcher: ThunderIDFetcher? = null,
): ResourceQueryState<OrganizationUnit> =
    rememberResourceQuery(listOf(OrganizationUnitQueryKeys.ORGANIZATION_UNIT, id.orEmpty()), enabled = id != null) {
        it.organizationUnits.get(id!!, fetcher)
    }

@Composable
fun rememberGetOrganizationUnitUsers(
    id: String?,
    limit: Int? = null,
    offset: Int? = null,
    fetcher: ThunderIDFetcher? = null,
): ResourceQueryState<OrganizationUnitUserListResponse> =
    rememberResourceQuery(
        listOf(OrganizationUnitQueryKeys.ORGANIZATION_UNIT_MEMBERS, id.orEmpty(), "users") + page(limit, offset),
        enabled = id != null,
    ) { it.organizationUnits.listUsers(id!!, limit, offset, fetcher) }

@Composable
fun rememberGetOrganizationUnitGroups(
    id: String?,
    limit: Int? = null,
    offset: Int? = null,
    fetcher: ThunderIDFetcher? = null,
): ResourceQueryState<OrganizationUnitGroupListResponse> =
    rememberResourceQuery(
        listOf(OrganizationUnitQueryKeys.ORGANIZATION_UNIT_MEMBERS, id.orEmpty(), "groups") + page(limit, offset),
        enabled = id != null,
    ) { it.organizationUnits.listGroups(id!!, limit, offset, fetcher) }

@Composable
fun rememberGetOrganizationUnitRoles(
    id: String?,
    limit: Int? = null,
    offset: Int? = null,
    fetcher: ThunderIDFetcher? = null,
): ResourceQueryState<OrganizationUnitRoleListResponse> =
    rememberResourceQuery(
        listOf(OrganizationUnitQueryKeys.ORGANIZATION_UNIT_MEMBERS, id.orEmpty(), "roles") + page(limit, offset),
        enabled = id != null,
    ) { it.organizationUnits.listRoles(id!!, limit, offset, fetcher) }

@Composable
fun rememberCreateOrganizationUnit(fetcher: ThunderIDFetcher? = null): ResourceMutationState<OrganizationUnitRequest, OrganizationUnit> =
    rememberResourceMutation({ projectAndOrganizationUnitKeys }) { client, request -> client.organizationUnits.create(request, fetcher) }

@Composable
fun rememberUpdateOrganizationUnit(
    fetcher: ThunderIDFetcher? = null,
): ResourceMutationState<Pair<String, OrganizationUnitRequest>, OrganizationUnit> =
    rememberResourceMutation({ projectAndOrganizationUnitKeys }) { client, (id, request) ->
        client.organizationUnits.update(id, request, fetcher)
    }

@Composable
fun rememberDeleteOrganizationUnit(fetcher: ThunderIDFetcher? = null): ResourceMutationState<String, Unit> =
    rememberResourceMutation({ projectAndOrganizationUnitKeys }) { client, id -> client.organizationUnits.delete(id, fetcher) }
