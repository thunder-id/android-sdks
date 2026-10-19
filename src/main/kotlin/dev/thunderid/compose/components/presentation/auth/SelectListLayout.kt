// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.presentation.auth

import dev.thunderid.android.management.SelectOption

/** What the list under the options says about its own state. */
internal enum class SelectListStatus {
    /** The first options are being fetched. */
    LOADING,

    /** There are no options, nothing is loading and there is nothing more to load. */
    EMPTY,

    /** Nothing to say: options are listed, or a "Load more" row is available. */
    NONE,
}

/** Which rows follow the options, decided from the state of the list. */
internal data class SelectListLayout(
    val status: SelectListStatus,
    /** Whether the "Load more" row is listed. While loading it reads "Loading more…" and cannot be chosen. */
    val showsLoadMore: Boolean,
) {
    companion object {
        fun of(
            optionCount: Int,
            loading: Boolean,
            canLoadMore: Boolean,
        ): SelectListLayout {
            val status =
                when {
                    loading && optionCount == 0 -> SelectListStatus.LOADING
                    optionCount == 0 && !canLoadMore && !loading -> SelectListStatus.EMPTY
                    else -> SelectListStatus.NONE
                }
            // During the first load there is nothing to load more of; after a failed first load the row
            // stays, so choosing it repeats the request.
            return SelectListLayout(status, showsLoadMore = canLoadMore && (optionCount > 0 || !loading))
        }
    }
}

/**
 * The option that labels [value]: the loaded option with that value, else the one last picked, else
 * [selectedOption]. `null` when none of them has that value.
 */
internal fun resolveSelectOption(
    value: String,
    options: List<SelectOption>,
    picked: SelectOption?,
    selectedOption: SelectOption?,
): SelectOption? {
    if (value.isEmpty()) return null
    options.firstOrNull { it.value == value }?.let { return it }
    if (picked?.value == value) return picked
    if (selectedOption?.value == value) return selectedOption
    return null
}
