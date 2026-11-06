// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.account

/** Narrows [dev.thunderid.android.ThunderIDClient.getAccessibleOrganizationUnits], relative to the current unit. */
enum class OrganizationUnitScope(
    val value: String,
) {
    /** The current unit and the accessible units that share its parent. The parent itself is not listed. */
    SIBLINGS("siblings"),
}

/**
 * Options for [dev.thunderid.android.ThunderIDClient.getAccessibleOrganizationUnits].
 *
 * @property scope Narrows the list. Every accessible unit is listed when it is `null`.
 * @property limit The page size. The server's default applies when it is `null`.
 * @property offset The 0-based index of the first unit of the page.
 */
data class AccessibleOrganizationUnitsOptions(
    val scope: OrganizationUnitScope? = null,
    val limit: Int? = null,
    val offset: Int? = null,
)

/** An organization unit the signed-in user can access, as `GET /users/me/ous` describes it. */
data class AccessibleOrganizationUnit(
    val id: String,
    val handle: String? = null,
    val name: String? = null,
)

/**
 * A page of the organization units the signed-in user can access: the one the user lives in and every
 * one the user is shared into.
 *
 * @property current The id of the unit the list is relative to: the one the user's token was issued for,
 * or the user's own when the token names none. It is returned whatever the scope or page, so it may not
 * be one of [organizationUnits].
 */
data class AccessibleOrganizationUnits(
    val current: String? = null,
    val organizationUnits: List<AccessibleOrganizationUnit> = emptyList(),
    val totalResults: Int = 0,
    val startIndex: Int = 1,
    val count: Int = 0,
)

/**
 * The organization unit the signed-in session was decided for, read from its `ouId`, `ouHandle` and
 * `ouName` claims. It is for display only and never for an authorization decision: the server decides
 * every request.
 */
data class CurrentOrganizationUnit(
    val id: String,
    val handle: String? = null,
    val name: String? = null,
)

/** The wire shape of `GET /users/me/ous`, read leniently before it becomes [AccessibleOrganizationUnits]. */
internal data class AccessibleOrganizationUnitsResponse(
    val current: String? = null,
    val organizationUnits: List<AccessibleOrganizationUnitEntry?>? = null,
    val totalResults: Int? = null,
    val startIndex: Int? = null,
    val count: Int? = null,
) {
    fun toModel(): AccessibleOrganizationUnits {
        val units =
            organizationUnits.orEmpty().mapNotNull { entry ->
                entry?.id?.takeIf { it.isNotEmpty() }?.let { AccessibleOrganizationUnit(it, entry.handle, entry.name) }
            }
        return AccessibleOrganizationUnits(
            current = current?.takeIf { it.isNotEmpty() },
            organizationUnits = units,
            totalResults = totalResults ?: units.size,
            startIndex = startIndex ?: 1,
            count = count ?: units.size,
        )
    }
}

internal data class AccessibleOrganizationUnitEntry(
    val id: String? = null,
    val handle: String? = null,
    val name: String? = null,
)

/** Reads the session's organization unit from its claims, or `null` when they name none. */
internal fun currentOrganizationUnitFrom(claims: Map<String, Any?>?): CurrentOrganizationUnit? {
    val id = (claims?.get("ouId") as? String)?.takeIf { it.isNotEmpty() } ?: return null
    return CurrentOrganizationUnit(
        id = id,
        handle = (claims["ouHandle"] as? String)?.takeIf { it.isNotEmpty() },
        name = (claims["ouName"] as? String)?.takeIf { it.isNotEmpty() },
    )
}
