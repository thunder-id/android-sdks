// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.management

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import dev.thunderid.android.ThunderIDFetcher
import dev.thunderid.android.api.management.ConnectionInstance
import dev.thunderid.android.api.management.ConnectionListResponse
import dev.thunderid.android.api.management.FlowDefinition
import dev.thunderid.android.api.management.FlowListResponse
import dev.thunderid.android.api.management.FlowQueryKeys
import dev.thunderid.android.api.management.FlowVariableDefinition
import dev.thunderid.android.api.management.FlowVariableQueryKeys
import dev.thunderid.android.api.management.FlowVariableType
import dev.thunderid.android.api.management.FlowVariables
import dev.thunderid.android.api.management.ResolvedOverlay
import dev.thunderid.android.api.management.UpdateFlowVariablesRequest
import dev.thunderid.android.api.management.getFlowVariableDefinitions

/** The most connections [rememberFlowVariables] offers a `CONNECTION_SELECT` variable. */
private const val CONNECTIONS_LIMIT = 100

/**
 * Loads a page of flow definitions. With [ouId], the flows that organization unit owns, followed by the ones
 * shared with it, each marked by [dev.thunderid.android.api.management.ManagedFlow.isOwnedByOU].
 */
@Composable
fun rememberGetFlows(
    ouId: String? = null,
    flowType: String? = null,
    limit: Int? = null,
    offset: Int? = null,
    fetcher: ThunderIDFetcher? = null,
    enabled: Boolean = true,
): ResourceQueryState<FlowListResponse> =
    rememberResourceQuery(
        listOf(
            FlowQueryKeys.FLOWS,
            "ouId=${ouId.orEmpty()}",
            "flowType=${flowType.orEmpty()}",
            "limit=${limit ?: ""}",
            "offset=${offset ?: ""}",
        ),
        enabled,
    ) { it.flows.list(flowType, limit, offset, fetcher, ouId) }

/**
 * The variables a flow declares for an organization unit, the values the unit has set, and what it may do with
 * each, as [rememberFlowVariables] loads them. The data behind
 * [dev.thunderid.compose.components.management.FlowVariablesForm], for building the form with any UI.
 */
@Stable
class FlowVariablesState internal constructor(
    private val flowQuery: ResourceQueryState<FlowDefinition>,
    private val valuesQuery: ResourceQueryState<FlowVariables>,
    private val overlayQuery: ResourceQueryState<ResolvedOverlay>,
    private val connectionsQuery: ResourceQueryState<ConnectionListResponse>,
    private val mutation: ResourceMutationState<Map<String, Any>, FlowVariables>,
    /** The variables the flow declares for the organization unit. */
    val definitions: List<FlowVariableDefinition>,
    private val onSaved: () -> ((Map<String, Any>) -> Unit)?,
) {
    /** The flow as the organization unit sees it. */
    val flow: FlowDefinition? get() = flowQuery.data

    /** What the organization unit may do with each variable: set its own value, or only see the owner's. */
    val overlay: ResolvedOverlay? get() = overlayQuery.data

    /** The values the organization unit has set. A variable it has not set is absent, and its default applies. */
    val overrides: Map<String, Any> get() = valuesQuery.data?.values.orEmpty()

    /**
     * The connections a `CONNECTION_SELECT` variable can take: the ones the organization unit owns and the ones
     * shared into it. Empty until loaded, and when no variable needs them.
     */
    val connections: List<ConnectionInstance> get() = connectionsQuery.data?.connections.orEmpty()

    /** Why the variables could not be loaded. */
    val error: Throwable? get() = flowQuery.error ?: valuesQuery.error ?: overlayQuery.error ?: connectionsQuery.error

    val isLoading: Boolean
        get() = flowQuery.isLoading || valuesQuery.isLoading || overlayQuery.isLoading || connectionsQuery.isLoading

    /**
     * Whether the variables, the unit's values and its rules have all arrived. A form built before then would be
     * reset when the values land.
     */
    val isLoaded: Boolean get() = flowQuery.data != null && valuesQuery.data != null && overlayQuery.data != null

    val isSaving: Boolean get() = mutation.isLoading

    /** Why the last save failed. */
    val saveError: Throwable? get() = mutation.error

    /**
     * Replaces the unit's overrides with [values]. A variable left out returns to its default. Never throws: read
     * [saveError]. Returns the values as the server stored them, or `null` when the save failed.
     */
    suspend fun save(values: Map<String, Any>): FlowVariables? = mutation.mutate(values)?.also { onSaved()?.invoke(it.values) }
}

/**
 * Loads the variables the flow [flowId] declares for the organization unit [ouId] and the values the unit has
 * set, and saves them. Nothing loads until both are known, or while [enabled] is `false`. The connections a
 * `CONNECTION_SELECT` variable can take load only when the flow has one.
 *
 * @param onSaved Called with the saved overrides.
 */
@Composable
fun rememberFlowVariables(
    flowId: String?,
    ouId: String?,
    enabled: Boolean = true,
    fetcher: ThunderIDFetcher? = null,
    onSaved: ((Map<String, Any>) -> Unit)? = null,
): FlowVariablesState {
    val ready = enabled && !flowId.isNullOrBlank() && !ouId.isNullOrBlank()
    val flowKey = flowId.orEmpty()
    val ouKey = ouId.orEmpty()
    val flowQuery =
        rememberResourceQuery(listOf(FlowVariableQueryKeys.DEFINITIONS, flowKey, ouKey), ready) {
            it.flows.get(flowId!!, fetcher, ouId)
        }
    val valuesQuery =
        rememberResourceQuery(listOf(FlowVariableQueryKeys.VALUES, flowKey, ouKey), ready) {
            it.flows.getVariables(flowId!!, ouId!!, fetcher)
        }
    val overlayQuery =
        rememberResourceQuery(listOf(FlowVariableQueryKeys.DEFINITIONS, "overlay", flowKey, ouKey), ready) {
            it.flows.getOverlayRules(flowId!!, ouId!!, fetcher)
        }
    val definitions = remember(flowQuery.data) { getFlowVariableDefinitions(flowQuery.data) }
    val needsConnections = definitions.any { it.type == FlowVariableType.CONNECTION_SELECT }
    val connectionsQuery =
        rememberResourceQuery(listOf(FlowVariableQueryKeys.DEFINITIONS, "connections", ouKey), ready && needsConnections) {
            it.connections.list(limit = CONNECTIONS_LIMIT, fetcher = fetcher, ouId = ouId)
        }
    val mutation =
        rememberResourceMutation<Map<String, Any>, FlowVariables>({ listOf(listOf(FlowVariableQueryKeys.VALUES)) }) { client, values ->
            client.flows.updateVariables(flowId.orEmpty(), UpdateFlowVariablesRequest(ouKey, values), fetcher)
        }
    val currentOnSaved by rememberUpdatedState(onSaved)
    return FlowVariablesState(flowQuery, valuesQuery, overlayQuery, connectionsQuery, mutation, definitions) { currentOnSaved }
}
