// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.management

/** The summary of an organization unit returned in list responses. */
data class OrganizationUnitSummary(
    val id: String,
    val handle: String,
    val name: String,
    val description: String? = null,
    val logoUrl: String? = null,
    val isReadOnly: Boolean? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

/** An organization unit. [parent] is `null` for a root organization unit. */
data class OrganizationUnit(
    val id: String,
    val handle: String,
    val name: String,
    val description: String? = null,
    val parent: String? = null,
    val themeId: String? = null,
    val layoutId: String? = null,
    val authFlowId: String? = null,
    val registrationFlowId: String? = null,
    val isRegistrationFlowEnabled: Boolean? = null,
    val recoveryFlowId: String? = null,
    val isRecoveryFlowEnabled: Boolean? = null,
    val signOutFlowId: String? = null,
    val logoUrl: String? = null,
    val tosUri: String? = null,
    val policyUri: String? = null,
    val cookiePolicyUri: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

/**
 * The payload used to create or update an organization unit by its identifier. [parent] is the parent
 * organization unit's identifier, or `null` for a root organization unit; it is always sent.
 */
data class OrganizationUnitRequest(
    val handle: String,
    val name: String,
    val parent: String?,
    val description: String? = null,
    val themeId: String? = null,
    val layoutId: String? = null,
    val authFlowId: String? = null,
    val registrationFlowId: String? = null,
    val isRegistrationFlowEnabled: Boolean? = null,
    val recoveryFlowId: String? = null,
    val isRecoveryFlowEnabled: Boolean? = null,
    val signOutFlowId: String? = null,
    val logoUrl: String? = null,
    val tosUri: String? = null,
    val policyUri: String? = null,
    val cookiePolicyUri: String? = null,
)

/** The payload used to update an organization unit addressed by its handle path. Its parent cannot change. */
data class OrganizationUnitByPathRequest(
    val handle: String,
    val name: String,
    val description: String? = null,
    val themeId: String? = null,
    val layoutId: String? = null,
    val authFlowId: String? = null,
    val registrationFlowId: String? = null,
    val isRegistrationFlowEnabled: Boolean? = null,
    val recoveryFlowId: String? = null,
    val isRecoveryFlowEnabled: Boolean? = null,
    val signOutFlowId: String? = null,
    val logoUrl: String? = null,
    val tosUri: String? = null,
    val policyUri: String? = null,
    val cookiePolicyUri: String? = null,
)

/** A page of organization units. */
data class OrganizationUnitListResponse(
    val totalResults: Int,
    val startIndex: Int? = null,
    val count: Int,
    val organizationUnits: List<OrganizationUnitSummary>,
    val links: List<ApiPaginationLink>? = null,
)

/**
 * A user listed in an organization unit. [isOwned] says whether the organization unit owns the user rather
 * than having it shared in; [display] is set because the SDK asks for it.
 */
data class OrganizationUnitUser(
    val id: String,
    val type: String? = null,
    val display: String? = null,
    val isOwned: Boolean? = null,
)

/** A page of the users in an organization unit: the owned users first, then the shared ones. */
data class OrganizationUnitUserListResponse(
    val totalResults: Int,
    val startIndex: Int? = null,
    val count: Int,
    val users: List<OrganizationUnitUser>,
    val links: List<ApiPaginationLink>? = null,
)

/** A group listed in an organization unit. */
data class OrganizationUnitGroup(
    val id: String,
    val name: String,
)

/** A page of the groups in an organization unit. */
data class OrganizationUnitGroupListResponse(
    val totalResults: Int,
    val startIndex: Int? = null,
    val count: Int,
    val groups: List<OrganizationUnitGroup>,
    val links: List<ApiPaginationLink>? = null,
)

/** A role listed in an organization unit. */
data class OrganizationUnitRole(
    val id: String,
    val name: String,
    val description: String? = null,
    val isReadOnly: Boolean? = null,
)

/** A page of the roles in an organization unit. */
data class OrganizationUnitRoleListResponse(
    val totalResults: Int,
    val startIndex: Int? = null,
    val count: Int,
    val roles: List<OrganizationUnitRole>,
    val links: List<ApiPaginationLink>? = null,
)

/** Cache keys for organization unit resources, including the users, groups and roles listed in one. */
object OrganizationUnitQueryKeys {
    const val ORGANIZATION_UNIT = "organization-unit"
    const val ORGANIZATION_UNITS = "organization-units"
    const val ORGANIZATION_UNIT_MEMBERS = "organization-unit-members"
}
