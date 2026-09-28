// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.management

/** The flow loader built into `AuthFlowSelectField`. */
internal typealias FetchAuthFlows = FetchSelectPage<ManagedFlow>

/** The user type loader built into `UserTypeSelectField`. */
internal typealias FetchUserTypes = FetchSelectPage<ManagedUserType>

private fun String?.nonEmpty(): String? = this?.trim()?.ifEmpty { null }

/**
 * Converts one flow into an option: shown by its name (then its handle, then its ID) and submitting its ID.
 * A name or handle that only repeats the ID is treated as unset.
 */
fun toAuthFlowSelectOption(flow: ManagedFlow): SelectOption {
    val label = listOfNotNull(flow.name.nonEmpty(), flow.handle.nonEmpty()).firstOrNull { it != flow.id }
    return SelectOption(label = label ?: flow.id, value = flow.id)
}

/**
 * Converts one user type into an option shown and submitted by its handle, or `null` when it has none.
 * Display names are not unique, so they are not used.
 */
fun toUserTypeSelectOption(type: ManagedUserType): SelectOption? {
    val handle = type.handle.nonEmpty() ?: return null
    return SelectOption(label = handle, value = handle)
}

/** Converts a `client.flows.list` response into a loader page. The next offset comes from the backend's own `count`. */
fun toAuthFlowSelectPage(
    response: ManagedFlowListResponse,
    requestOffset: Int,
): SelectPage<ManagedFlow> =
    toListSelectPage(
        options = response.flows.map(::toAuthFlowSelectOption),
        count = response.count,
        totalResults = response.totalResults,
        requestOffset = requestOffset,
    )

/**
 * Converts a `client.userTypes.list` response into a loader page. The next offset comes from the
 * backend's own `count`, so user types without a handle never shift the paging.
 */
fun toUserTypeSelectPage(
    response: ManagedUserTypeListResponse,
    requestOffset: Int,
): SelectPage<ManagedUserType> =
    toListSelectPage(
        options = response.types.mapNotNull(::toUserTypeSelectOption),
        count = response.count,
        totalResults = response.totalResults,
        requestOffset = requestOffset,
    )
