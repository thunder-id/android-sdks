// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.management

/**
 * Pure (Android-framework-free) paging math backing the paged-select pickers, kept separate so it
 * can be unit tested without a device/Robolectric.
 */
object PagedSelectPaging {
    /**
     * Whether a loader's reported next offset is safe to follow: `null` (exhausted) or an integer
     * past the request offset. Anything else would loop or go backwards.
     */
    fun isAdvancingPageOffset(
        requestOffset: Int,
        nextOffset: Int?,
    ): Boolean = nextOffset != null && nextOffset > requestOffset

    /**
     * Offset of the next page: the request offset plus the number of items the backend returned,
     * or `null` once the list is exhausted.
     */
    fun computeNextPageOffset(
        requestOffset: Int,
        count: Int,
        totalResults: Int?,
    ): Int? {
        if (count <= 0) return null
        val next = requestOffset + count
        return if (totalResults != null && next >= totalResults) null else next
    }

    /**
     * Appends [incoming] to [existing], keeping each value at its first position and taking the
     * newest data when a value repeats (offset paging can return the same item on two pages).
     */
    fun dedupePagedSelectOptions(
        existing: List<PagedSelectOption>,
        incoming: List<PagedSelectOption>,
    ): List<PagedSelectOption> {
        val byValue = LinkedHashMap<String, PagedSelectOption>()
        for (option in existing + incoming) {
            byValue[option.value] = option
        }
        return byValue.values.toList()
    }
}

/**
 * Converts one raw item into an option using [mapping]: the mapped value is submitted and the
 * mapped label is shown, falling back to the value. `null` when the mapping has no value for the item.
 */
fun <T> toPagedSelectOption(
    item: T,
    mapping: PagedSelectOptionMapping<T>,
): PagedSelectOption? {
    val value = mapping.value(item) ?: return null
    return PagedSelectOption(label = mapping.label(item) ?: value, value = value)
}

/** Converts a page of raw items into options, dropping any item the mapping has no value for. */
fun <T> toPagedSelectOptions(
    items: List<T>,
    mapping: PagedSelectOptionMapping<T>,
): List<PagedSelectOption> = items.mapNotNull { toPagedSelectOption(it, mapping) }
