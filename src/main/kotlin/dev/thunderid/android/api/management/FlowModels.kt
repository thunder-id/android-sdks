// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.management

import com.google.gson.annotations.SerializedName

/**
 * What happens to a resource that references another one when that one is deleted. [value] is the wire
 * value. [RESTRICT] means the reference blocks the deletion.
 */
enum class ResourceUsageBehavior(
    val value: String,
) {
    @SerializedName("fallback")
    FALLBACK("fallback"),

    @SerializedName("cascade")
    CASCADE("cascade"),

    @SerializedName("restrict")
    RESTRICT("restrict"),
}

/** A resource that references another one, such as an application that uses a flow, theme or layout. */
data class ResourceUsage(
    val id: String,
    val resourceType: String,
    val displayName: String,
    val behaviorOnDelete: ResourceUsageBehavior,
)

/**
 * The resources that reference a flow, a theme, a layout or a connection. [totalResults] and [summary] are
 * `null` when the server could not tell; `0` means there are none. [summary] counts the usages of each
 * resource type, such as `application`.
 */
data class ResourceUsagesResponse(
    val count: Int,
    val usages: List<ResourceUsage>,
    val totalResults: Int? = null,
    val summary: Map<String, Int>? = null,
)

/** A page of flow definitions. Each item carries the flow's summary, without its graph. */
data class FlowListResponse(
    val totalResults: Int,
    val startIndex: Int,
    val count: Int,
    val flows: List<ManagedFlow>,
    val links: List<ApiPaginationLink>? = null,
)

/**
 * A flow definition with its graph.
 *
 * @property flowType `AUTHENTICATION` or `REGISTRATION` (administration flows report `ADMINISTRATION`).
 * @property nodes The flow graph: each node as the server describes it (`id`, `type`, `layout`, `meta`,
 *   `prompts`, `executor`, `onSuccess`, …), kept as JSON objects so no field is lost on a round trip. A flow
 *   the unit owns declares its variables on its `VARIABLES` node.
 * @property ouId The organization unit the flow was requested for.
 * @property isOwnedByOU Whether the organization unit the flow was requested for owns it.
 * @property variables Set on a flow shared with the requested organization unit, which comes back as its
 *   variables only, without its graph. Read the variables of either kind with [getFlowVariableDefinitions].
 */
data class FlowDefinition(
    val id: String,
    val name: String,
    val flowType: String,
    val handle: String,
    val activeVersion: Int,
    val isReadOnly: Boolean,
    val nodes: List<Map<String, Any?>>,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val ouId: String? = null,
    val isOwnedByOU: Boolean? = null,
    val variables: List<FlowVariableDefinition>? = null,
)

/** The payload used to create or update a flow definition. A flow's [handle] cannot change once set. */
data class FlowDefinitionRequest(
    val name: String,
    val handle: String,
    val flowType: String,
    val nodes: List<Map<String, Any?>>,
)

/** One version in a flow's history. */
data class FlowVersionInfo(
    val version: Int,
    val createdAt: String,
    val isActive: Boolean,
)

/** A flow's version history, most recent first. */
data class FlowVersionListResponse(
    val totalVersions: Int,
    val versions: List<FlowVersionInfo>,
)

/** A snapshot of a flow definition at one version. */
data class FlowVersion(
    val id: String,
    val name: String,
    val flowType: String,
    val handle: String,
    val version: Int,
    val isActive: Boolean,
    val nodes: List<Map<String, Any?>>,
    val createdAt: String,
)

/**
 * The values an organization unit has set for a flow's variables: its own overrides, keyed by variable
 * identifier, each in the variable's own type (a string, a number or a boolean). A variable the unit has not set
 * is absent. JSON numbers arrive as [Double].
 */
data class FlowVariables(
    val values: Map<String, Any> = emptyMap(),
)

/**
 * The payload used to replace an organization unit's flow variable values. [values] replaces everything the
 * unit held, so a variable left out returns to its default. A value is a string, a number or a boolean, never
 * `null`.
 */
data class UpdateFlowVariablesRequest(
    val ouId: String,
    val values: Map<String, Any>,
)

/** Cache keys for flow lists. */
object FlowQueryKeys {
    const val FLOWS = "flows"
}

/**
 * Cache keys for flow variables: what a flow declares for an organization unit, and the values the unit has
 * set. Saving the values invalidates [VALUES].
 */
object FlowVariableQueryKeys {
    const val DEFINITIONS = "flow-variable-definitions"
    const val VALUES = "flow-variable-values"
}
