// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.management

/** A flow definition as listed by the ThunderID management API. */
data class ManagedFlow(
    val id: String,
    val handle: String,
    val name: String,
    val flowType: String,
    val activeVersion: Int? = null,
    val isReadOnly: Boolean? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

/** A page of flows. */
data class ManagedFlowListResponse(
    val totalResults: Int,
    val startIndex: Int,
    val count: Int,
    val flows: List<ManagedFlow>,
    val links: List<ApiPaginationLink>? = null,
)
