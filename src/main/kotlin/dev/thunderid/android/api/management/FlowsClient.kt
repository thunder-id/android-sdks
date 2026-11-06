// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.management

import dev.thunderid.android.IAMException
import dev.thunderid.android.ThunderIDErrorCode
import dev.thunderid.android.ThunderIDFetcher

/**
 * Flow definition management operations, reached through `ThunderIDClient.flows`, against the `flows`
 * collection. These manage the flow graphs; running a flow is the sign-in, sign-up and administration
 * operations' job. Each call accepts an optional [ThunderIDFetcher] that takes precedence over
 * `ThunderIDConfig.http.fetcher`.
 */
class FlowsClient internal constructor(
    private val transport: ManagementTransport,
) {
    private val sharing = SharingOperations(transport)

    /**
     * Returns a page of flow definitions.
     *
     * @param flowType Only flows of this type, such as `AUTHENTICATION` or `REGISTRATION`.
     */
    suspend fun list(
        flowType: String? = null,
        limit: Int? = null,
        offset: Int? = null,
        fetcher: ThunderIDFetcher? = null,
    ): FlowListResponse = transport.list(mapOf("flowType" to flowType) + page(limit, offset), fetcher)

    /** Returns a single flow definition with its graph. */
    suspend fun get(
        id: String,
        fetcher: ThunderIDFetcher? = null,
    ): FlowDefinition = transport.get(id, fetcher = fetcher)

    /** Creates a flow definition and returns it as the server stored it. */
    suspend fun create(
        flow: FlowDefinitionRequest,
        fetcher: ThunderIDFetcher? = null,
    ): FlowDefinition = transport.create(flow, fetcher)

    /** Replaces a flow definition, which creates a new version, and returns the updated flow. */
    suspend fun update(
        id: String,
        flow: FlowDefinitionRequest,
        fetcher: ThunderIDFetcher? = null,
    ): FlowDefinition = transport.update(id, flow, fetcher)

    /** Deletes a flow definition. A declarative (read-only) flow cannot be deleted. */
    suspend fun delete(
        id: String,
        fetcher: ThunderIDFetcher? = null,
    ) = transport.delete(id, fetcher)

    /** Returns the version history of a flow, most recent first. */
    suspend fun listVersions(
        id: String,
        fetcher: ThunderIDFetcher? = null,
    ): FlowVersionListResponse = transport.request("GET", listOf(id, "versions"), fetcher = fetcher)

    /** Returns a flow definition as it was at [version]. */
    suspend fun getVersion(
        id: String,
        version: Int,
        fetcher: ThunderIDFetcher? = null,
    ): FlowVersion = transport.request("GET", listOf(id, "versions", version.toString()), fetcher = fetcher)

    /**
     * Restores a flow to [version], which creates a new version with that version's content, and returns
     * the updated flow.
     */
    suspend fun restoreVersion(
        id: String,
        version: Int,
        fetcher: ThunderIDFetcher? = null,
    ): FlowDefinition {
        val body = mapOf("version" to version)
        return transport.request("POST", listOf(id, "restore"), body = body, fetcher = fetcher)
    }

    /**
     * Returns the resources that reference a flow, such as applications that sign in with it. Informational:
     * the server does not refuse to delete a flow that is in use.
     */
    suspend fun getUsages(
        id: String,
        fetcher: ThunderIDFetcher? = null,
    ): ResourceUsagesResponse = transport.getSubResource(id, "usages", emptyMap(), fetcher)

    /** Returns a page of the flow's sharing policies. */
    suspend fun listSharingPolicies(
        id: String,
        limit: Int? = null,
        offset: Int? = null,
        fetcher: ThunderIDFetcher? = null,
    ): SharingPolicyListResponse = sharing.list(id, limit, offset, fetcher)

    /** Records a sharing policy for the flow and returns it. One policy exists per initiating organization unit. */
    suspend fun createSharingPolicy(
        id: String,
        policy: SharingPolicyRequest,
        fetcher: ThunderIDFetcher? = null,
    ): SharingPolicy = sharing.create(id, policy, fetcher)

    /** Returns one of the flow's sharing policies. */
    suspend fun getSharingPolicy(
        id: String,
        policyId: String,
        fetcher: ThunderIDFetcher? = null,
    ): SharingPolicy = sharing.get(id, policyId, fetcher)

    /** Replaces a sharing policy's sections. An edit against a stale [SharingPolicyUpdateRequest.version] is refused. */
    suspend fun updateSharingPolicy(
        id: String,
        policyId: String,
        policy: SharingPolicyUpdateRequest,
        fetcher: ThunderIDFetcher? = null,
    ): SharingPolicy = sharing.update(id, policyId, policy, fetcher)

    /** Deletes a sharing policy and the policies that depended on it. */
    suspend fun deleteSharingPolicy(
        id: String,
        policyId: String,
        fetcher: ThunderIDFetcher? = null,
    ) = sharing.delete(id, policyId, fetcher)

    /**
     * Resolves what the organization unit [ouId] may do with the flow: whether it owns it, whether it can see
     * it, the policies that reached it, and the effective rule for each variable, keyed `variables.<identifier>`. The server requires [ouId] and answers
     * `404` for a unit no policy reaches.
     */
    suspend fun getOverlayRules(
        id: String,
        ouId: String,
        fetcher: ThunderIDFetcher? = null,
    ): ResolvedOverlay = sharing.overlayRules(id, ouId, fetcher)

    /**
     * Returns the values the organization unit [ouId] has set for the flow's variables, keyed by identifier. A
     * variable the unit has not set is absent, and the flow's `defaultValue` applies to it.
     */
    suspend fun getVariables(
        id: String,
        ouId: String,
        fetcher: ThunderIDFetcher? = null,
    ): FlowVariables {
        requireOuId(ouId)
        return transport.request("GET", listOf(id, "variables"), mapOf("ouId" to ouId), fetcher = fetcher)
    }

    /**
     * Replaces the values the organization unit named in [variables] holds for the flow's variables, and returns
     * them as the server stored them. A variable left out returns to its default. Every value is validated before
     * any is written.
     */
    suspend fun updateVariables(
        id: String,
        variables: UpdateFlowVariablesRequest,
        fetcher: ThunderIDFetcher? = null,
    ): FlowVariables {
        requireOuId(variables.ouId)
        return transport.request("PUT", listOf(id, "variables"), body = variables, fetcher = fetcher)
    }

    private fun requireOuId(ouId: String) {
        if (ouId.isBlank()) {
            throw IAMException(ThunderIDErrorCode.INVALID_INPUT, "An organization unit identifier is required for flow variables")
        }
    }
}
