// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.management

/**
 * A user type as listed by the ThunderID management API. A user type is identified by its
 * immutable [handle]; [displayName] is not unique.
 */
data class ManagedUserType(
    val id: String,
    val handle: String,
    val displayName: String? = null,
    val ouId: String? = null,
    val ouHandle: String? = null,
    val allowSelfRegistration: Boolean? = null,
    val isReadOnly: Boolean? = null,
)

/** A page of user types. */
data class ManagedUserTypeListResponse(
    val totalResults: Int,
    val startIndex: Int,
    val count: Int,
    val types: List<ManagedUserType>,
    val links: List<ApiPaginationLink>? = null,
)
