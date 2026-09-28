// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.management

/** Limits and values shared by the select pickers that list management resources. */
object SelectConstants {
    /** How many options a picker asks for at a time when it is not told otherwise. */
    const val DEFAULT_PAGE_SIZE = 30

    /**
     * The largest page the management API serves. The flow list clamps a larger request and the user
     * type list rejects it, so loaders cap their request at this size.
     */
    const val MAX_PAGE_SIZE = 100

    /**
     * Batches in a row that add nothing new before the loading helper stops continuing by itself and
     * waits for the next request.
     */
    const val MAX_EMPTY_CONTINUATIONS = 3

    /** The pause before the single retry of a failed page request, in milliseconds. */
    const val RETRY_DELAY_MILLIS = 500L
}

/**
 * The page size a picker asks for: the default when none is given, otherwise the given size clamped
 * to at least 1 and at most what the management API serves.
 */
fun resolvePageSize(pageSize: Int = SelectConstants.DEFAULT_PAGE_SIZE): Int = pageSize.coerceIn(1, SelectConstants.MAX_PAGE_SIZE)

/**
 * Pure (Android-framework-free) paging math backing the select pickers, kept separate so it can be
 * unit tested without a device/Robolectric.
 */
object SelectPaging {
    /**
     * Whether a loader's reported next offset is safe to follow: an integer past the request
     * offset. Anything else would loop or go backwards, so the list ends instead.
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
        if (requestOffset < 0 || count <= 0) return null
        val next = requestOffset + count
        return if (totalResults != null && next >= totalResults) null else next
    }

    /**
     * Appends [incoming] to [existing], keeping each value at its first position and taking the
     * newest data when a value repeats (offset paging can return the same item on two pages).
     */
    fun dedupeSelectOptions(
        existing: List<SelectOption>,
        incoming: List<SelectOption>,
    ): List<SelectOption> {
        val byValue = LinkedHashMap<String, SelectOption>()
        for (option in existing + incoming) {
            byValue[option.value] = option
        }
        return byValue.values.toList()
    }
}

/**
 * Builds a loader page from one page of a management list response. The next offset comes from the
 * backend's own [count], so items dropped by a mapping never shift the paging.
 */
internal fun <T> toListSelectPage(
    options: List<SelectOption>,
    count: Int,
    totalResults: Int,
    requestOffset: Int,
): SelectPage<T> =
    SelectPage(
        options = options,
        nextOffset = SelectPaging.computeNextPageOffset(requestOffset, count, totalResults),
        totalResults = totalResults,
    )

/**
 * Converts one raw item into an option using [mapping]: the mapped value is submitted and the
 * mapped label is shown, falling back to the value. `null` when the mapping has no value for the item.
 */
fun <T> toSelectOption(
    item: T,
    mapping: SelectOptionMapping<T>,
): SelectOption? {
    val value = mapping.value(item) ?: return null
    return SelectOption(label = mapping.label(item) ?: value, value = value)
}

/** Converts a page of raw items into options, dropping any item the mapping has no value for. */
fun <T> toSelectOptions(
    items: List<T>,
    mapping: SelectOptionMapping<T>,
): List<SelectOption> = items.mapNotNull { toSelectOption(it, mapping) }
