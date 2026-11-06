// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.management

import com.google.gson.annotations.SerializedName

/** What a resource server describes. Metadata only, and fixed once the resource server is created. */
enum class ResourceServerType(
    val value: String,
) {
    @SerializedName("API")
    API("API"),

    @SerializedName("MCP")
    MCP("MCP"),

    @SerializedName("CUSTOM")
    CUSTOM("CUSTOM"),
}

/**
 * The MCP primitive an action represents: a callable `tool` or a read-only `resource`. Fixed once the action
 * is created.
 */
enum class ActionKind(
    val value: String,
) {
    @SerializedName("tool")
    TOOL("tool"),

    @SerializedName("resource")
    RESOURCE("resource"),
}

/**
 * How a resource server makes authorization decisions. [type] is `rbac` (the default) or `authzen_pdp`;
 * [properties] names the AuthZEN PDP connection for the latter.
 */
data class AuthorizationEngineConfig(
    val type: String? = null,
    val properties: AuthorizationEngineProperties? = null,
)

/** The settings of an authorization engine. */
data class AuthorizationEngineProperties(
    val pdpConnectionId: String? = null,
)

/**
 * A resource server: an API, MCP server or other protected system whose permissions are derived from its
 * resources and actions joined by [delimiter].
 */
data class ResourceServer(
    val id: String,
    val name: String,
    val identifier: String,
    val ouId: String,
    val type: ResourceServerType? = null,
    val delimiter: String? = null,
    val description: String? = null,
    val authorizationEngine: AuthorizationEngineConfig? = null,
    val isReadOnly: Boolean? = null,
)

/** The payload used to create a resource server. [type] and [delimiter] cannot change afterwards. */
data class CreateResourceServerRequest(
    val name: String,
    val identifier: String,
    val ouId: String,
    val description: String? = null,
    val type: ResourceServerType? = null,
    val delimiter: String? = null,
)

/** The payload used to update a resource server. */
data class UpdateResourceServerRequest(
    val name: String,
    val ouId: String,
    val description: String? = null,
    val identifier: String? = null,
    val authorizationEngine: AuthorizationEngineConfig? = null,
)

/** A page of resource servers. */
data class ResourceServerListResponse(
    val totalResults: Int,
    val startIndex: Int? = null,
    val count: Int,
    val resourceServers: List<ResourceServer>,
    val links: List<ApiPaginationLink>? = null,
)

/**
 * A resource of a resource server. [permission] is derived from the handles above it; [parent] is absent for
 * a top-level resource.
 */
data class ManagedResource(
    val id: String,
    val name: String,
    val handle: String,
    val permission: String,
    val description: String? = null,
    val parent: String? = null,
)

/** The payload used to create a resource. [handle] and [parent] cannot change afterwards. */
data class CreateResourceRequest(
    val name: String,
    val handle: String,
    val description: String? = null,
    val parent: String? = null,
)

/** The payload used to update a resource: only its name and description can change. */
data class UpdateResourceRequest(
    val name: String? = null,
    val description: String? = null,
)

/** A page of resources. */
data class ResourceListResponse(
    val totalResults: Int,
    val startIndex: Int? = null,
    val count: Int,
    val resources: List<ManagedResource>,
    val links: List<ApiPaginationLink>? = null,
)

/** An action on a resource server or on one of its resources. [permission] is derived from the handles. */
data class ManagedAction(
    val id: String,
    val name: String,
    val handle: String,
    val permission: String,
    val description: String? = null,
    val kind: ActionKind? = null,
)

/** The payload used to create an action. [handle] and [kind] cannot change afterwards. */
data class CreateActionRequest(
    val name: String,
    val handle: String,
    val description: String? = null,
    val kind: ActionKind? = null,
)

/** The payload used to update an action: only its name and description can change. */
data class UpdateActionRequest(
    val name: String? = null,
    val description: String? = null,
)

/** A page of actions. */
data class ActionListResponse(
    val totalResults: Int,
    val startIndex: Int? = null,
    val count: Int,
    val actions: List<ManagedAction>,
    val links: List<ApiPaginationLink>? = null,
)
