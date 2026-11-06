// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.management

import dev.thunderid.android.ThunderIDFetcher

/**
 * Theme and layout management operations, reached through `ThunderIDClient.design`, against the `design`
 * collection (`{design}/themes`, `{design}/layouts`, `{design}/resolve`). Each call accepts an optional
 * [ThunderIDFetcher] that takes precedence over `ThunderIDConfig.http.fetcher`.
 */
class DesignClient internal constructor(
    private val transport: ManagementTransport,
) {
    // MARK: - Themes

    /** Returns a page of themes. */
    suspend fun listThemes(
        limit: Int? = null,
        offset: Int? = null,
        fetcher: ThunderIDFetcher? = null,
    ): DesignThemeListResponse = transport.request("GET", listOf(THEMES), page(limit, offset), fetcher = fetcher)

    /** Returns a single theme. */
    suspend fun getTheme(
        id: String,
        fetcher: ThunderIDFetcher? = null,
    ): DesignTheme = transport.request("GET", listOf(THEMES, id), fetcher = fetcher)

    /** Creates a theme and returns it as the server stored it. */
    suspend fun createTheme(
        theme: DesignThemeRequest,
        fetcher: ThunderIDFetcher? = null,
    ): DesignTheme = transport.request("POST", listOf(THEMES), body = theme, fetcher = fetcher)

    /** Replaces a theme and returns the updated theme. */
    suspend fun updateTheme(
        id: String,
        theme: DesignThemeRequest,
        fetcher: ThunderIDFetcher? = null,
    ): DesignTheme = transport.request("PUT", listOf(THEMES, id), body = theme, fetcher = fetcher)

    /** Deletes a theme. Resources that still reference it fall back to the default theme. */
    suspend fun deleteTheme(
        id: String,
        fetcher: ThunderIDFetcher? = null,
    ) {
        transport.request<Unit>("DELETE", listOf(THEMES, id), fetcher = fetcher)
    }

    /** Returns a page of the resources that reference a theme. */
    suspend fun getThemeUsages(
        id: String,
        limit: Int? = null,
        offset: Int? = null,
        fetcher: ThunderIDFetcher? = null,
    ): ResourceUsagesResponse {
        val query = page(limit, offset)
        return transport.request("GET", listOf(THEMES, id, USAGES), query, fetcher = fetcher)
    }

    // MARK: - Layouts

    /** Returns a page of layouts. */
    suspend fun listLayouts(
        limit: Int? = null,
        offset: Int? = null,
        fetcher: ThunderIDFetcher? = null,
    ): DesignLayoutListResponse = transport.request("GET", listOf(LAYOUTS), page(limit, offset), fetcher = fetcher)

    /** Returns a single layout. */
    suspend fun getLayout(
        id: String,
        fetcher: ThunderIDFetcher? = null,
    ): DesignLayout = transport.request("GET", listOf(LAYOUTS, id), fetcher = fetcher)

    /** Creates a layout and returns it as the server stored it. */
    suspend fun createLayout(
        layout: DesignLayoutRequest,
        fetcher: ThunderIDFetcher? = null,
    ): DesignLayout = transport.request("POST", listOf(LAYOUTS), body = layout, fetcher = fetcher)

    /** Replaces a layout and returns the updated layout. */
    suspend fun updateLayout(
        id: String,
        layout: DesignLayoutRequest,
        fetcher: ThunderIDFetcher? = null,
    ): DesignLayout = transport.request("PUT", listOf(LAYOUTS, id), body = layout, fetcher = fetcher)

    /** Deletes a layout. Resources that still reference it fall back to the default layout. */
    suspend fun deleteLayout(
        id: String,
        fetcher: ThunderIDFetcher? = null,
    ) {
        transport.request<Unit>("DELETE", listOf(LAYOUTS, id), fetcher = fetcher)
    }

    /** Returns a page of the resources that reference a layout. */
    suspend fun getLayoutUsages(
        id: String,
        limit: Int? = null,
        offset: Int? = null,
        fetcher: ThunderIDFetcher? = null,
    ): ResourceUsagesResponse {
        val query = page(limit, offset)
        return transport.request("GET", listOf(LAYOUTS, id, USAGES), query, fetcher = fetcher)
    }

    // MARK: - Resolve

    /** Returns the design (theme and layout merged) that applies to the application or organization unit [id]. */
    suspend fun resolve(
        type: DesignResolveType,
        id: String,
        fetcher: ThunderIDFetcher? = null,
    ): DesignResolveResponse {
        val query = mapOf("type" to type.value, "id" to id)
        return transport.request("GET", listOf("resolve"), query, fetcher = fetcher)
    }

    private companion object {
        const val THEMES = "themes"
        const val LAYOUTS = "layouts"
        const val USAGES = "usages"
    }
}
