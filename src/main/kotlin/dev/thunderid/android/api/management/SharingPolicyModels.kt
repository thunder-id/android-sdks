// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.management

import com.google.gson.annotations.SerializedName
import dev.thunderid.android.IAMException
import dev.thunderid.android.ThunderIDErrorCode
import dev.thunderid.android.ThunderIDFetcher

/**
 * One section of a sharing policy, in the shape the server states it.
 *
 * With [mode] `all` the section reaches a whole group, less [excludedOuIds] and everything beneath them.
 * With [mode] `selected` it reaches the organization units named in [units] and takes no exclusions. See
 * [SharingPolicyMode].
 *
 * @property overlayRules The terms the units reached hold the resource on, keyed by field: `variables.<identifier>`
 *   for a flow, `assignments` for a role. Set on an `all` section; a `selected` section sets them per unit.
 */
data class SharingSection(
    val mode: String,
    val excludedOuIds: List<String>? = null,
    val units: List<SharingUnit>? = null,
    val overlayRules: Map<String, OverlayRule>? = null,
)

/** The values [SharingSection.mode] takes. */
object SharingPolicyMode {
    const val ALL = "all"
    const val SELECTED = "selected"
}

/**
 * An organization unit a `selected` section reaches. [reach] applies to child units only: `unit` for the
 * unit alone, `subtree` for it and everything below it. A root of another tree carries no reach.
 */
data class SharingUnit(
    val ouId: String,
    val reach: String? = null,
    val overlayRules: Map<String, OverlayRule>? = null,
)

/** The section that reaches every organization unit in the deployment, less [excludedOuIds]. */
data class SharingAllUnits(
    val excludedOuIds: List<String>? = null,
    val overlayRules: Map<String, OverlayRule>? = null,
)

/**
 * The payload used to create a sharing policy: at least one of [childUnits] and [otherTreeRoots].
 * [initiatingOuId] is the organization unit whose decision this is; `null` means the owner.
 */
data class SharingPolicyRequest(
    val initiatingOuId: String? = null,
    val childUnits: SharingSection? = null,
    val otherTreeRoots: SharingSection? = null,
)

/**
 * The payload used to replace a sharing policy's sections. [version] is the version being edited; an edit
 * against any other version is refused.
 */
data class SharingPolicyUpdateRequest(
    val version: Int,
    val allUnits: SharingAllUnits? = null,
    val childUnits: SharingSection? = null,
    val otherTreeRoots: SharingSection? = null,
)

/**
 * A recorded sharing policy of a user, an application, a role or a flow.
 *
 * @property stage `share` when the owner issued it, `reshare` when a sharee did.
 * @property version Bumped on every edit; send it back in [SharingPolicyUpdateRequest.version].
 */
data class SharingPolicy(
    val id: String,
    val initiatingOuId: String? = null,
    val stage: String? = null,
    val parentPolicyId: String? = null,
    val isReadOnly: Boolean? = null,
    val version: Int? = null,
    val allUnits: SharingAllUnits? = null,
    val childUnits: SharingSection? = null,
    val otherTreeRoots: SharingSection? = null,
) {
    /** This policy's sections and version, as an update starts from. */
    fun toUpdateRequest(): SharingPolicyUpdateRequest = SharingPolicyUpdateRequest(version ?: 0, allUnits, childUnits, otherTreeRoots)
}

/** A page of sharing policies. */
data class SharingPolicyListResponse(
    val totalResults: Int,
    val startIndex: Int? = null,
    val count: Int,
    val policies: List<SharingPolicy>,
    val links: List<ApiPaginationLink>? = null,
)

/**
 * What one organization unit may do with a shared user, application, role or flow: whether it owns it, whether
 * it can see it, the policies that reached it, and the effective rule per field.
 *
 * @property isOwnedByOU Whether the organization unit owns the resource. The server names this `isOwnedByOU` for every
 * resource, flows included.
 */
data class ResolvedOverlay(
    val ouId: String? = null,
    val isOwnedByOU: Boolean? = null,
    val visible: Boolean? = null,
    val policyIds: List<String>? = null,
    val rules: Map<String, OverlayRule>? = null,
)

/** What a reached organization unit may do with one field. */
data class OverlayRule(
    val editable: Boolean? = null,
    val value: List<String>? = null,
    val allowedValues: List<String>? = null,
    val excludedValues: List<String>? = null,
)

/**
 * The sharing policy and overlay operations users, applications, roles and flows share, on `{collection}/{id}/…`.
 */
internal class SharingOperations(
    private val transport: ManagementTransport,
) {
    suspend fun list(
        id: String,
        limit: Int?,
        offset: Int?,
        fetcher: ThunderIDFetcher?,
    ): SharingPolicyListResponse = transport.request("GET", listOf(id, POLICIES), page(limit, offset), fetcher = fetcher)

    suspend fun create(
        id: String,
        policy: SharingPolicyRequest,
        fetcher: ThunderIDFetcher?,
    ): SharingPolicy = transport.request("POST", listOf(id, POLICIES), body = policy, fetcher = fetcher)

    suspend fun get(
        id: String,
        policyId: String,
        fetcher: ThunderIDFetcher?,
    ): SharingPolicy = transport.request("GET", listOf(id, POLICIES, policyId), fetcher = fetcher)

    suspend fun update(
        id: String,
        policyId: String,
        policy: SharingPolicyUpdateRequest,
        fetcher: ThunderIDFetcher?,
    ): SharingPolicy = transport.request("PUT", listOf(id, POLICIES, policyId), body = policy, fetcher = fetcher)

    suspend fun delete(
        id: String,
        policyId: String,
        fetcher: ThunderIDFetcher?,
    ) {
        transport.request<Unit>("DELETE", listOf(id, POLICIES, policyId), fetcher = fetcher)
    }

    suspend fun overlayRules(
        id: String,
        ouId: String,
        fetcher: ThunderIDFetcher?,
    ): ResolvedOverlay {
        if (ouId.isBlank()) {
            throw IAMException(
                ThunderIDErrorCode.INVALID_INPUT,
                "An organization unit identifier is required to resolve overlay rules",
            )
        }
        return transport.request("GET", listOf(id, "overlay-rules"), mapOf("ouId" to ouId), fetcher = fetcher)
    }

    private companion object {
        const val POLICIES = "sharing-policies"
    }
}

/** Cache keys for sharing policies and the overlay rules they resolve to, per shared resource. */
object SharingQueryKeys {
    const val SHARING_POLICIES = "sharing-policies"
    const val OVERLAY_RULES = "overlay-rules"
}
