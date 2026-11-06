// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.management

import androidx.compose.runtime.Composable
import dev.thunderid.android.ThunderIDClient
import dev.thunderid.android.ThunderIDFetcher
import dev.thunderid.android.api.management.ResolvedOverlay
import dev.thunderid.android.api.management.SharingPolicy
import dev.thunderid.android.api.management.SharingPolicyListResponse
import dev.thunderid.android.api.management.SharingPolicyRequest
import dev.thunderid.android.api.management.SharingPolicyUpdateRequest
import dev.thunderid.android.api.management.SharingQueryKeys

/** The kinds of resource that carry sharing policies, each through its own management client. */
enum class SharingResourceType(
    val value: String,
) {
    USER("user"),
    APPLICATION("application"),
    ROLE("role"),
    FLOW("flow"),
}

/** The input of a sharing policy update: the policy and the sections and version to write. */
data class SharingPolicyUpdateInput(
    val policyId: String,
    val policy: SharingPolicyUpdateRequest,
)

/**
 * The sharing operations of one resource, routed to `users`, `applications`, `roles` or `flows` by [type].
 * Each runs the existing operation of that client; nothing here talks to the server on its own.
 */
internal class SharingTarget(
    private val client: ThunderIDClient,
    private val type: SharingResourceType,
    private val id: String,
    private val fetcher: ThunderIDFetcher?,
) {
    suspend fun list(
        limit: Int?,
        offset: Int?,
    ): SharingPolicyListResponse =
        when (type) {
            SharingResourceType.USER -> client.users.listSharingPolicies(id, limit, offset, fetcher)
            SharingResourceType.APPLICATION -> client.applications.listSharingPolicies(id, limit, offset, fetcher)
            SharingResourceType.ROLE -> client.roles.listSharingPolicies(id, limit, offset, fetcher)
            SharingResourceType.FLOW -> client.flows.listSharingPolicies(id, limit, offset, fetcher)
        }

    suspend fun create(policy: SharingPolicyRequest): SharingPolicy =
        when (type) {
            SharingResourceType.USER -> client.users.createSharingPolicy(id, policy, fetcher)
            SharingResourceType.APPLICATION -> client.applications.createSharingPolicy(id, policy, fetcher)
            SharingResourceType.ROLE -> client.roles.createSharingPolicy(id, policy, fetcher)
            SharingResourceType.FLOW -> client.flows.createSharingPolicy(id, policy, fetcher)
        }

    suspend fun update(input: SharingPolicyUpdateInput): SharingPolicy =
        when (type) {
            SharingResourceType.USER -> client.users.updateSharingPolicy(id, input.policyId, input.policy, fetcher)
            SharingResourceType.APPLICATION -> client.applications.updateSharingPolicy(id, input.policyId, input.policy, fetcher)
            SharingResourceType.ROLE -> client.roles.updateSharingPolicy(id, input.policyId, input.policy, fetcher)
            SharingResourceType.FLOW -> client.flows.updateSharingPolicy(id, input.policyId, input.policy, fetcher)
        }

    suspend fun delete(policyId: String) {
        when (type) {
            SharingResourceType.USER -> client.users.deleteSharingPolicy(id, policyId, fetcher)
            SharingResourceType.APPLICATION -> client.applications.deleteSharingPolicy(id, policyId, fetcher)
            SharingResourceType.ROLE -> client.roles.deleteSharingPolicy(id, policyId, fetcher)
            SharingResourceType.FLOW -> client.flows.deleteSharingPolicy(id, policyId, fetcher)
        }
    }

    suspend fun overlayRules(ouId: String): ResolvedOverlay =
        when (type) {
            SharingResourceType.USER -> client.users.getOverlayRules(id, ouId, fetcher)
            SharingResourceType.APPLICATION -> client.applications.getOverlayRules(id, ouId, fetcher)
            SharingResourceType.ROLE -> client.roles.getOverlayRules(id, ouId, fetcher)
            SharingResourceType.FLOW -> client.flows.getOverlayRules(id, ouId, fetcher)
        }
}

private fun sharingKeys(
    type: SharingResourceType,
    id: String,
): List<List<String>> =
    listOf(
        listOf(SharingQueryKeys.SHARING_POLICIES, type.value, id),
        listOf(SharingQueryKeys.OVERLAY_RULES, type.value, id),
    )

/** Loads a page of the sharing policies of the [type] resource [id]. */
@Composable
fun rememberGetSharingPolicies(
    type: SharingResourceType,
    id: String?,
    limit: Int? = null,
    offset: Int? = null,
    fetcher: ThunderIDFetcher? = null,
): ResourceQueryState<SharingPolicyListResponse> =
    rememberResourceQuery(
        listOf(SharingQueryKeys.SHARING_POLICIES, type.value, id.orEmpty(), "limit=${limit ?: ""}", "offset=${offset ?: ""}"),
        enabled = id != null,
    ) { SharingTarget(it, type, id!!, fetcher).list(limit, offset) }

/** Resolves what the organization unit [ouId] may do with the [type] resource [id]. */
@Composable
fun rememberGetOverlayRules(
    type: SharingResourceType,
    id: String?,
    ouId: String?,
    fetcher: ThunderIDFetcher? = null,
): ResourceQueryState<ResolvedOverlay> =
    rememberResourceQuery(
        listOf(SharingQueryKeys.OVERLAY_RULES, type.value, id.orEmpty(), ouId.orEmpty()),
        enabled = id != null && !ouId.isNullOrBlank(),
    ) { SharingTarget(it, type, id!!, fetcher).overlayRules(ouId!!) }

@Composable
fun rememberCreateSharingPolicy(
    type: SharingResourceType,
    id: String,
    fetcher: ThunderIDFetcher? = null,
): ResourceMutationState<SharingPolicyRequest, SharingPolicy> =
    rememberResourceMutation({ sharingKeys(type, id) }) { client, policy -> SharingTarget(client, type, id, fetcher).create(policy) }

@Composable
fun rememberUpdateSharingPolicy(
    type: SharingResourceType,
    id: String,
    fetcher: ThunderIDFetcher? = null,
): ResourceMutationState<SharingPolicyUpdateInput, SharingPolicy> =
    rememberResourceMutation({ sharingKeys(type, id) }) { client, input -> SharingTarget(client, type, id, fetcher).update(input) }

@Composable
fun rememberDeleteSharingPolicy(
    type: SharingResourceType,
    id: String,
    fetcher: ThunderIDFetcher? = null,
): ResourceMutationState<String, Unit> =
    rememberResourceMutation({ sharingKeys(type, id) }) { client, policyId -> SharingTarget(client, type, id, fetcher).delete(policyId) }
