// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.presentation.auth

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.thunderid.android.management.SelectOption
import dev.thunderid.android.management.SelectOptionsBatch
import dev.thunderid.android.management.SelectOptionsBatchIssue
import dev.thunderid.android.management.SelectOptionsLoadContext
import dev.thunderid.android.management.loadUntilOptionsGrow
import dev.thunderid.android.management.mergeSelectOptionsBatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Keeps the option list of a [SelectField] that loads more options when the user asks for them.
 *
 * It owns the cursor, appends each batch without duplicates, and stops at the end of the data.
 * Nothing is drawn, so it works with any list composable. A failed load never changes the options:
 * it sets [error], calls `onError`, and leaves the cursor where it was, so asking again repeats the
 * same request. Loads run in [scope], so they are cancelled when the composition that owns the scope
 * leaves, and when [reset] is called.
 */
@Stable
class InfiniteOptionsState<C : Any> internal constructor(
    private val scope: CoroutineScope,
    private val initialOptions: List<SelectOption>,
    private var load: suspend (SelectOptionsLoadContext<C>) -> SelectOptionsBatch<C>?,
    private var onError: ((Throwable) -> Unit)?,
    private val warn: (String) -> Unit,
) {
    /** The options loaded so far. */
    var options by mutableStateOf(initialOptions)
        private set

    /** Whether a batch is being fetched. */
    var loading by mutableStateOf(false)
        private set

    /** Whether more batches may be available. */
    var hasMore by mutableStateOf(true)
        private set

    /** The error of the last load, cleared by the next successful one. */
    var error by mutableStateOf<Throwable?>(null)
        private set

    private var cursor: C? = null
    private var finished = false
    private var requested = false
    private var generation = 0
    private var job: Job? = null

    /**
     * Replaces the loader and the error callback. A composable calls this on each composition so a
     * request made later uses the current values; it does not restart the list.
     */
    fun update(
        load: suspend (SelectOptionsLoadContext<C>) -> SelectOptionsBatch<C>?,
        onError: ((Throwable) -> Unit)? = null,
    ) {
        this.load = load
        this.onError = onError
    }

    /** Loads the next batch. Does nothing while a load is running or once the data has ended. */
    fun loadMore() {
        if (loading || finished) return
        requested = true
        startLoad()
    }

    /**
     * Goes back to the initial options and the first batch. Aborts a running load, and loads again
     * right away if loading had already been requested.
     */
    fun reset() {
        val shouldReload = requested
        generation += 1
        job?.cancel()
        job = null
        cursor = null
        finished = false
        loading = false
        options = initialOptions
        hasMore = true
        error = null
        if (shouldReload) startLoad()
    }

    private fun startLoad() {
        val thisGeneration = generation
        loading = true
        job =
            scope.launch {
                try {
                    loadUntilOptionsGrow(
                        loadBatch = { load(SelectOptionsLoadContext(cursor, options)) },
                        applyBatch = { batch -> addBatch(batch) },
                        isCurrent = { thisGeneration == generation },
                    )
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (failure: Exception) {
                    if (thisGeneration == generation) {
                        error = failure
                        reportError(failure)
                    }
                } finally {
                    if (thisGeneration == generation) {
                        job = null
                        loading = false
                    }
                }
            }
    }

    /** Adds a batch to the list. Returns `true` when it added nothing while more remain. */
    private fun addBatch(batch: SelectOptionsBatch<C>?): Boolean {
        val merged = mergeSelectOptionsBatch(cursor, options, batch)
        if (merged.issue == SelectOptionsBatchIssue.CURSOR_STUCK) {
            warn("A load returned no new options and the same cursor; the list was treated as finished.")
        }
        val addedNothing = merged.options.size == options.size
        cursor = merged.cursor
        options = merged.options
        finished = merged.finished
        hasMore = !merged.finished
        error = null
        return addedNothing && !merged.finished
    }

    private fun reportError(failure: Throwable) {
        try {
            onError?.invoke(failure)
        } catch (callbackFailure: Exception) {
            warn("The onError callback threw ${callbackFailure::class.simpleName}.")
        }
    }
}
