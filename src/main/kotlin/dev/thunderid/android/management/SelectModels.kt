// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.management

/** A selectable option: [label] is shown, [value] is submitted. */
data class SelectOption(
    val label: String,
    val value: String,
    val disabled: Boolean = false,
)

/** One page request sent to a [FetchSelectPage] loader. */
data class SelectPageRequest(
    val limit: Int,
    val offset: Int,
    val filter: String? = null,
)

/**
 * One page returned by a loader. Return ready-made [options], or raw [items] for the picker to
 * convert with its mapping. [nextOffset] is `null` on the last page, otherwise it must be greater
 * than the request's `offset`.
 */
data class SelectPage<T>(
    val items: List<T>? = null,
    val options: List<SelectOption>? = null,
    val nextOffset: Int?,
    val totalResults: Int? = null,
)

/**
 * Loads one page of options for a request. Generic over the raw item type a specialised picker
 * (users, emails, ...) fetches — [SelectPageRequest]/[SelectPage] stay the same either way.
 */
fun interface FetchSelectPage<T> {
    suspend fun fetch(request: SelectPageRequest): SelectPage<T>
}

/**
 * Chooses which field of each raw item is the label and which is the value, for a picker that
 * does not want a type's built-in default conversion.
 */
data class SelectOptionMapping<T>(
    val label: (T) -> String?,
    val value: (T) -> String?,
)
