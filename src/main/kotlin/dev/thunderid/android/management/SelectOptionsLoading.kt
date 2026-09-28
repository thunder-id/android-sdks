// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.management

/**
 * One batch of options from a loader. A plain list ends the data when it is empty or adds nothing
 * new; a page ends it when [Page.nextCursor] is `null`.
 */
sealed interface SelectOptionsBatch<out C> {
    data class OptionList(
        val options: List<SelectOption>,
    ) : SelectOptionsBatch<Nothing>

    data class Page<C>(
        val options: List<SelectOption>,
        val nextCursor: C?,
    ) : SelectOptionsBatch<C>
}

/** What a loader receives when its select asks for more options. */
data class SelectOptionsLoadContext<C>(
    /** `null` on the first call, then the `nextCursor` of the previous batch. */
    val cursor: C?,
    /** The options loaded so far. */
    val options: List<SelectOption>,
)

/** Something about a batch that the caller may want to log. The merge itself carried on. */
enum class SelectOptionsBatchIssue {
    /** The batch added nothing and its cursor did not move: a loader that ignores its cursor. */
    CURSOR_STUCK,
}

/** The options and cursor after a batch was added, and whether the data has ended. */
data class SelectOptionsMergeResult<C>(
    val cursor: C?,
    /** No more batches should be requested. */
    val finished: Boolean,
    val issue: SelectOptionsBatchIssue?,
    val options: List<SelectOption>,
)

/**
 * Adds one loaded batch to the options loaded so far and decides whether the data has ended.
 * Options that are already present are replaced in place by their newer copy. A page ends when its
 * cursor is missing, or when it adds nothing and the cursor did not move. A plain list ends when it
 * adds nothing new. A missing batch counts as an empty list.
 *
 * Shared by the SDK modules; it is not part of the supported API and can change in any release.
 */
fun <C> mergeSelectOptionsBatch(
    cursor: C?,
    options: List<SelectOption>,
    batch: SelectOptionsBatch<C>?,
): SelectOptionsMergeResult<C> {
    val incoming =
        when (batch) {
            is SelectOptionsBatch.OptionList -> batch.options
            is SelectOptionsBatch.Page -> batch.options
            null -> emptyList()
        }
    val merged = SelectPaging.dedupeSelectOptions(options, incoming)
    val addedNothing = merged.size == options.size

    if (batch !is SelectOptionsBatch.Page) {
        return SelectOptionsMergeResult(cursor, finished = addedNothing, issue = null, options = merged)
    }

    val cursorStuck = addedNothing && batch.nextCursor != null && batch.nextCursor == cursor
    return SelectOptionsMergeResult(
        cursor = batch.nextCursor,
        finished = batch.nextCursor == null || cursorStuck,
        issue = if (cursorStuck) SelectOptionsBatchIssue.CURSOR_STUCK else null,
        options = merged,
    )
}

/**
 * Loads batches until one adds something new, the data ends, or a few empty batches in a row have
 * been skipped.
 *
 * A batch that adds nothing new while more remain would leave the list empty or stuck, so a request
 * for more options would appear to do nothing. The helper keeps going instead, a few batches at most.
 *
 * - [loadBatch] loads the next batch.
 * - [applyBatch] adds a batch to the list and returns `true` when it added nothing while more remain.
 * - [isCurrent] returns `false` once the list was reset or the owner went away, which stops the loop.
 *
 * Shared by the SDK modules; it is not part of the supported API and can change in any release.
 */
suspend fun <B> loadUntilOptionsGrow(
    loadBatch: suspend () -> B,
    applyBatch: (B) -> Boolean,
    isCurrent: () -> Boolean,
) {
    var emptyBatches = 0
    while (emptyBatches <= SelectConstants.MAX_EMPTY_CONTINUATIONS) {
        val batch = loadBatch()
        if (!isCurrent() || !applyBatch(batch)) return
        emptyBatches += 1
    }
}
