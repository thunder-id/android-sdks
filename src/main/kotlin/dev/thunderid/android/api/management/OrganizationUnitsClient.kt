// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.management

import dev.thunderid.android.ThunderIDFetcher

/**
 * Organization unit management operations, reached through `ThunderIDClient.organizationUnits`.
 *
 * An organization unit is addressed either by its identifier or by its handle path, such as
 * `engineering/frontend`, which is sent to `{collection}/tree/{path}` with each handle encoded on its own.
 * Each call accepts an optional [ThunderIDFetcher] that takes precedence over `ThunderIDConfig.http.fetcher`.
 */
class OrganizationUnitsClient internal constructor(
    private val transport: ManagementTransport,
) {
    /**
     * Returns a page of the root organization units.
     *
     * @param filter A server-side filter on `name`, `handle`, `description`, `createdAt` or `updatedAt`,
     *   such as `handle sw "eng"`.
     */
    suspend fun list(
        limit: Int? = null,
        offset: Int? = null,
        filter: String? = null,
        fetcher: ThunderIDFetcher? = null,
    ): OrganizationUnitListResponse = transport.list(page(limit, offset) + ("filter" to filter), fetcher)

    /** Returns a single organization unit. */
    suspend fun get(
        id: String,
        fetcher: ThunderIDFetcher? = null,
    ): OrganizationUnit = transport.get(id, fetcher = fetcher)

    /** Returns the organization unit at the handle [path], such as `engineering/frontend`. */
    suspend fun getByPath(
        path: String,
        fetcher: ThunderIDFetcher? = null,
    ): OrganizationUnit = transport.request("GET", tree(path), fetcher = fetcher)

    /** Creates an organization unit and returns it as the server stored it. A `null` parent makes it a root. */
    suspend fun create(
        organizationUnit: OrganizationUnitRequest,
        fetcher: ThunderIDFetcher? = null,
    ): OrganizationUnit = transport.request("POST", body = jsonWithNulls(organizationUnit, "parent"), fetcher = fetcher)

    /** Replaces an organization unit's fields and returns the updated organization unit. */
    suspend fun update(
        id: String,
        organizationUnit: OrganizationUnitRequest,
        fetcher: ThunderIDFetcher? = null,
    ): OrganizationUnit = transport.request("PUT", listOf(id), body = jsonWithNulls(organizationUnit, "parent"), fetcher = fetcher)

    /** Replaces the fields of the organization unit at the handle [path] and returns it. */
    suspend fun updateByPath(
        path: String,
        organizationUnit: OrganizationUnitByPathRequest,
        fetcher: ThunderIDFetcher? = null,
    ): OrganizationUnit = transport.request("PUT", tree(path), body = organizationUnit, fetcher = fetcher)

    /** Deletes an organization unit. */
    suspend fun delete(
        id: String,
        fetcher: ThunderIDFetcher? = null,
    ) = transport.delete(id, fetcher)

    /** Deletes the organization unit at the handle [path]. */
    suspend fun deleteByPath(
        path: String,
        fetcher: ThunderIDFetcher? = null,
    ) {
        transport.request<Unit>("DELETE", tree(path), fetcher = fetcher)
    }

    /** Returns a page of the organization unit's child organization units. See [list] for [filter]. */
    suspend fun listChildren(
        id: String,
        limit: Int? = null,
        offset: Int? = null,
        filter: String? = null,
        fetcher: ThunderIDFetcher? = null,
    ): OrganizationUnitListResponse =
        transport.request("GET", listOf(id, "ous"), page(limit, offset) + ("filter" to filter), fetcher = fetcher)

    /** Returns a page of the child organization units of the one at the handle [path]. */
    suspend fun listChildrenByPath(
        path: String,
        limit: Int? = null,
        offset: Int? = null,
        filter: String? = null,
        fetcher: ThunderIDFetcher? = null,
    ): OrganizationUnitListResponse =
        transport.request("GET", tree(path) + "ous", page(limit, offset) + ("filter" to filter), fetcher = fetcher)

    /**
     * Returns a page of the organization unit's users, each with its resolved `display` value: the users it
     * owns first, then the users shared into it.
     */
    suspend fun listUsers(
        id: String,
        limit: Int? = null,
        offset: Int? = null,
        fetcher: ThunderIDFetcher? = null,
    ): OrganizationUnitUserListResponse =
        transport.request("GET", listOf(id, "users"), page(limit, offset) + ("include" to "display"), fetcher = fetcher)

    /** Returns a page of the users of the organization unit at the handle [path]. See [listUsers]. */
    suspend fun listUsersByPath(
        path: String,
        limit: Int? = null,
        offset: Int? = null,
        fetcher: ThunderIDFetcher? = null,
    ): OrganizationUnitUserListResponse =
        transport.request("GET", tree(path) + "users", page(limit, offset) + ("include" to "display"), fetcher = fetcher)

    /** Returns a page of the organization unit's groups. */
    suspend fun listGroups(
        id: String,
        limit: Int? = null,
        offset: Int? = null,
        fetcher: ThunderIDFetcher? = null,
    ): OrganizationUnitGroupListResponse = transport.request("GET", listOf(id, "groups"), page(limit, offset), fetcher = fetcher)

    /** Returns a page of the groups of the organization unit at the handle [path]. */
    suspend fun listGroupsByPath(
        path: String,
        limit: Int? = null,
        offset: Int? = null,
        fetcher: ThunderIDFetcher? = null,
    ): OrganizationUnitGroupListResponse = transport.request("GET", tree(path) + "groups", page(limit, offset), fetcher = fetcher)

    /** Returns a page of the organization unit's roles. */
    suspend fun listRoles(
        id: String,
        limit: Int? = null,
        offset: Int? = null,
        fetcher: ThunderIDFetcher? = null,
    ): OrganizationUnitRoleListResponse = transport.request("GET", listOf(id, "roles"), page(limit, offset), fetcher = fetcher)

    /** Returns a page of the roles of the organization unit at the handle [path]. */
    suspend fun listRolesByPath(
        path: String,
        limit: Int? = null,
        offset: Int? = null,
        fetcher: ThunderIDFetcher? = null,
    ): OrganizationUnitRoleListResponse = transport.request("GET", tree(path) + "roles", page(limit, offset), fetcher = fetcher)

    private fun tree(path: String): List<String> = listOf("tree") + treePath(path)
}
