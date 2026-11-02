// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.management

import dev.thunderid.android.FlowError
import dev.thunderid.android.ThunderIDFetcher

/**
 * Administration flows, reached through `ThunderIDClient.administration`: the flows the server configures
 * to carry out an operation that changes state, such as deleting a user or regenerating a client secret.
 *
 * The resource clients run these for an operation called with [AdministrationMode.FLOW]; call them
 * directly to drive another operation through its flow. Every call targets the server at `baseUrl`
 * (`/server-config/flow`, `/flows`, `/flow/execute`), whatever `endpoints` override a resource collection
 * has. Each call accepts an optional [ThunderIDFetcher] that takes precedence over `ThunderIDConfig.http.fetcher`.
 */
class AdministrationClient internal constructor(
    private val transport: ManagementTransport,
) {
    /**
     * Reads the handle configured for each of [configKeys] (see [AdministrationFlowConfigKey]) in one
     * request. A key with no handle is absent from the result.
     *
     * A read failure is thrown: treating it as "no flow configured" would perform the operation through the
     * API directly, which revokes nothing, and report it as a success.
     */
    suspend fun resolveFlowHandles(
        configKeys: List<String>,
        fetcher: ThunderIDFetcher? = null,
    ): Map<String, String> {
        // The server returns the declarative, writable and merged layers; only the merged one is applied.
        val layers: AdministrationFlowConfigLayers? =
            transport.server("server-config").get<AdministrationFlowConfigLayers>("flow", fetcher = fetcher)
        val section = layers?.merged.orEmpty()
        return configKeys
            .mapNotNull { key -> section[key]?.defaultHandle?.takeIf { it.isNotEmpty() }?.let { key to it } }
            .toMap()
    }

    /**
     * Finds the administration flows carrying [handles], in one walk of the flow listing that stops once
     * every handle is found. A handle with no flow is absent from the result.
     *
     * The listing is paged and newest first, which puts the bootstrap flows last, so pages of
     * [FLOW_PAGE_SIZE] are walked until the handles are found or the listing ends.
     */
    suspend fun findFlows(
        handles: Collection<String>,
        fetcher: ThunderIDFetcher? = null,
    ): Map<String, ManagedFlow> {
        val wanted = handles.toMutableSet()
        val found = linkedMapOf<String, ManagedFlow>()
        if (wanted.isEmpty()) return found
        val flows = transport.server("flows")
        var offset = 0
        while (wanted.isNotEmpty()) {
            // Sequential: a page is requested only when the previous one did not complete the set.
            val page: ManagedFlowListResponse? =
                flows.list<ManagedFlowListResponse>(
                    mapOf(
                        "flowType" to ADMINISTRATION_FLOW_TYPE,
                        "limit" to FLOW_PAGE_SIZE.toString(),
                        "offset" to offset.toString(),
                    ),
                    fetcher,
                )
            val items = page?.flows.orEmpty()
            items.forEach { flow ->
                if (flow.handle in wanted && flow.flowType == ADMINISTRATION_FLOW_TYPE) {
                    found[flow.handle] = flow
                    wanted -= flow.handle
                }
            }
            // A short page is the last one, so the handles still wanted do not exist.
            if (items.size < FLOW_PAGE_SIZE) break
            offset += FLOW_PAGE_SIZE
        }
        return found
    }

    /**
     * Resolves each of [configKeys] whose configured handle names an existing administration flow to that
     * flow's identifier, in one configuration read and one listing walk. A key with no flow is absent.
     */
    suspend fun resolveFlows(
        configKeys: List<String>,
        fetcher: ThunderIDFetcher? = null,
    ): Map<String, String> {
        val handles = resolveFlowHandles(configKeys, fetcher)
        val flows = findFlows(handles.values, fetcher)
        return handles.mapNotNull { (key, handle) -> flows[handle]?.let { key to it.id } }.toMap()
    }

    /**
     * Finds the administration flow the server configures for [configKey], or returns `null` when none is
     * configured so the caller calls the API directly.
     *
     * Only an unset handle yields `null`: that is the deployment asking for the direct call. A handle that
     * names a flow that no longer exists throws [AdministrationFlowError] instead, since falling back would
     * make the change without the revocation the configuration asks for.
     *
     * @param action What the flow does, such as `user deletion`, for the error message.
     */
    suspend fun resolveFlowId(
        configKey: String,
        action: String,
        fetcher: ThunderIDFetcher? = null,
    ): String? {
        val handle = resolveFlowHandles(listOf(configKey), fetcher)[configKey] ?: return null
        val flow =
            findFlows(listOf(handle), fetcher)[handle]
                ?: throw AdministrationFlowError(
                    "resolveAdministrationFlowId-NotFoundError-001",
                    "The configured $action flow \"$handle\" no longer exists, so the action was not performed. " +
                        "Restore the flow or clear the handle from the server configuration.",
                )
        return flow.id
    }

    /**
     * Runs the administration flow [flowId] to completion in one call and returns the values it produced
     * (see [AdministrationFlowData]).
     *
     * Every input the flow needs is given up front in [inputs] (see [AdministrationFlowInput]), so it should
     * finish without pausing. A flow that asks for more input, or a step that refuses, throws
     * [AdministrationFlowError].
     */
    suspend fun executeFlow(
        flowId: String,
        inputs: Map<String, String>,
        action: String,
        fetcher: ThunderIDFetcher? = null,
    ): Map<String, String> {
        val result: AdministrationFlowExecutionResponse? =
            transport
                .server(
                    "flow/execute",
                ).create<AdministrationFlowExecutionResponse>(mapOf("flowId" to flowId, "inputs" to inputs), fetcher)
        when (result?.flowStatus) {
            "COMPLETE" -> {
                return result.data
                    ?.additionalData
                    .orEmpty()
                    .mapNotNull { (key, value) -> value?.let { key to it.toString() } }
                    .toMap()
            }

            "INCOMPLETE" -> {
                throw AdministrationFlowError(
                    "executeAdministrationFlow-IncompleteError-001",
                    "The $action flow requires additional input and could not be completed.",
                )
            }
        }
        val flowError = result?.error ?: FlowError()
        throw AdministrationFlowError(
            flowError.code ?: "executeAdministrationFlow-FlowError-001",
            flowError.message?.defaultValue ?: flowError.description?.defaultValue ?: "The $action flow did not complete.",
            flowError,
        )
    }

    /**
     * Runs the administration flow the server configures for [configKey] and returns the values it
     * produced, or returns `null` when none is configured so the caller calls the API directly. See
     * [resolveFlowId].
     */
    suspend fun runFlow(
        configKey: String,
        inputs: Map<String, String>,
        action: String,
        fetcher: ThunderIDFetcher? = null,
    ): Map<String, String>? {
        val flowId = resolveFlowId(configKey, action, fetcher) ?: return null
        return executeFlow(flowId, inputs, action, fetcher)
    }

    /**
     * Runs an account lifecycle [action] (suspend, unsuspend, unlock) on the [kind] of identity [subject],
     * through the same flow as the matching resource operation, such as `UsersClient.suspend`. A missing flow
     * fails with that operation's `{operation}-NotConfiguredError-001`, and an action that does not apply to
     * [kind], such as unlocking an application, fails with `INVALID_INPUT` without a request.
     *
     * Use it with [accountLifecycleActions] to drive the actions of any kind of identity from one place.
     */
    suspend fun runAccountLifecycleAction(
        kind: AccountLifecycleSubjectKind,
        action: AccountLifecycleAction,
        subject: String,
        operatorNote: String? = null,
        fetcher: ThunderIDFetcher? = null,
    ): AccountLifecycleResult = runAccountLifecycle(kind, action, subject, operatorNote, fetcher)

    companion object {
        /**
         * Page size used when walking the flow listing to find a handle. It matches the server's maximum
         * page size, so the usual handful of administration flows resolves in one request.
         */
        const val FLOW_PAGE_SIZE = 100
    }
}
