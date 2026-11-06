// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.management

import com.google.gson.annotations.SerializedName

/** A theme as listed, with the default color scheme's name and primary color for a preview. */
data class DesignThemeListItem(
    val id: String,
    val handle: String,
    val displayName: String,
    val description: String? = null,
    val defaultColorScheme: String? = null,
    val primaryColor: String? = null,
    val isReadOnly: Boolean? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

/** A page of themes. */
data class DesignThemeListResponse(
    val totalResults: Int,
    val startIndex: Int,
    val count: Int,
    val themes: List<DesignThemeListItem>,
    val links: List<ApiPaginationLink>? = null,
)

/** A theme: color palettes and typography, as a JSON object in [theme]. */
data class DesignTheme(
    val id: String,
    val handle: String,
    val displayName: String,
    val description: String? = null,
    val theme: Map<String, Any?>? = null,
    val isReadOnly: Boolean? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

/** The payload used to create or update a theme. [handle] is a unique kebab-case identifier. */
data class DesignThemeRequest(
    val handle: String,
    val displayName: String,
    val theme: Map<String, Any?>,
    val description: String? = null,
)

/** A layout as listed. */
data class DesignLayoutListItem(
    val id: String,
    val handle: String,
    val displayName: String,
    val description: String? = null,
    val isReadOnly: Boolean? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

/** A page of layouts. */
data class DesignLayoutListResponse(
    val totalResults: Int,
    val startIndex: Int,
    val count: Int,
    val layouts: List<DesignLayoutListItem>,
    val links: List<ApiPaginationLink>? = null,
)

/** A layout: the structure of the authentication screens, as a JSON object in [layout]. */
data class DesignLayout(
    val id: String,
    val handle: String,
    val displayName: String,
    val description: String? = null,
    val layout: Map<String, Any?>? = null,
    val isReadOnly: Boolean? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

/** The payload used to create or update a layout. [handle] is a unique kebab-case identifier. */
data class DesignLayoutRequest(
    val handle: String,
    val displayName: String,
    val layout: Map<String, Any?>,
    val description: String? = null,
)

/** The kind of entity a design is resolved for. [value] is the wire value. */
enum class DesignResolveType(
    val value: String,
) {
    @SerializedName("APP")
    APP("APP"),

    @SerializedName("OU")
    OU("OU"),
}

/** The design that applies to an application or organization unit: its theme and layout merged. */
data class DesignResolveResponse(
    val theme: Map<String, Any?>? = null,
    val layout: Map<String, Any?>? = null,
)
