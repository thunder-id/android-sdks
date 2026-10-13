// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.presentation.auth

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.thunderid.android.management.FetchPagedOptions
import dev.thunderid.android.management.PagedSelectFailure
import dev.thunderid.android.management.PagedSelectOption
import dev.thunderid.android.management.PagedSelectPaging
import dev.thunderid.android.management.PagedSelectRequest
import dev.thunderid.android.management.mapPagedSelectError
import kotlin.coroutines.cancellation.CancellationException

/**
 * Loads options page by page from a loader: lazy first page, incremental paging, de-duplication,
 * and a generation guard that drops a result from a superseded load (a new field, filter, or
 * loader) — the same generation-guard shape `ResourceQueryState` uses for management resources.
 * Renders nothing itself, so any design can use it.
 */
@Stable
class PagedSelectState<T> internal constructor(
    private var pageSize: Int,
    private var filter: String?,
    private var fetch: FetchPagedOptions<T>,
    private var convert: (List<T>) -> List<PagedSelectOption>,
) {
    var options by mutableStateOf<List<PagedSelectOption>>(emptyList())
        private set
    var hasMore by mutableStateOf(true)
        private set
    var hasLoaded by mutableStateOf(false)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var isLoadingMore by mutableStateOf(false)
        private set
    var failure by mutableStateOf<PagedSelectFailure?>(null)
        private set

    /** The option chosen through [select]. Survives [reset], so its label stays available. */
    var selected by mutableStateOf<PagedSelectOption?>(null)
        private set

    private var generation = 0
    private var isInFlight = false
    private var nextOffset: Int? = 0
    private var failedOffset = 0

    /** Loads page 0, once. A later call is a no-op until [reset] starts a fresh generation. */
    suspend fun loadInitial() {
        if (hasLoaded || isInFlight) return
        loadPage(0)
    }

    /** Loads the page after the last one loaded. A no-op while a load is in flight or the list is exhausted. */
    suspend fun loadMore() {
        val offset = nextOffset ?: return
        if (!hasLoaded || isInFlight) return
        loadPage(offset)
    }

    /** Retries the page that most recently failed. */
    suspend fun retry() {
        if (failure == null || isInFlight) return
        loadPage(failedOffset)
    }

    /**
     * Discards loaded/in-flight state so the next [loadInitial] starts a fresh page 0. Call on
     * dismiss/close so reopening after a scope change (a different field or filter) does not show
     * a stale list, and a superseded in-flight result is dropped by the generation guard. The
     * [selected] option is kept.
     */
    fun reset() {
        generation++
        isInFlight = false
        nextOffset = 0
        failedOffset = 0
        options = emptyList()
        hasMore = true
        hasLoaded = false
        isLoading = false
        isLoadingMore = false
        failure = null
    }

    /** Records [option] as the chosen one. */
    fun select(option: PagedSelectOption) {
        selected = option
    }

    /** The label to show for [value]: the selected option's, else a loaded option's, else `null`. */
    fun labelFor(value: String): String? {
        selected?.takeIf { it.value == value }?.let { return it.label }
        return options.firstOrNull { it.value == value }?.label
    }

    /**
     * Applies the current inputs. The loader and conversion are used from the next request on; a
     * changed page size or filter also calls [reset], since pages already loaded belong to the old
     * query. Returns whether it reset.
     */
    internal fun configure(
        pageSize: Int,
        filter: String?,
        fetch: FetchPagedOptions<T>,
        convert: (List<T>) -> List<PagedSelectOption>,
    ): Boolean {
        this.fetch = fetch
        this.convert = convert
        if (pageSize == this.pageSize && filter == this.filter) return false
        this.pageSize = pageSize
        this.filter = filter
        reset()
        return true
    }

    private suspend fun loadPage(offset: Int) {
        val thisGeneration = generation
        val isFirstPage = offset == 0
        val request = PagedSelectRequest(pageSize, offset, filter)
        val fetch = fetch
        val convert = convert
        isInFlight = true
        if (isFirstPage) isLoading = true else isLoadingMore = true
        failure = null

        try {
            val page = fetch.fetch(request)
            if (thisGeneration != generation) return
            val incoming = page.options ?: convert(page.items ?: emptyList())
            options = PagedSelectPaging.dedupePagedSelectOptions(if (isFirstPage) emptyList() else options, incoming)
            nextOffset = page.nextOffset.takeIf { PagedSelectPaging.isAdvancingPageOffset(offset, it) }
            hasMore = nextOffset != null
            hasLoaded = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (thisGeneration != generation) return
            failedOffset = offset
            failure = mapPagedSelectError(e)
        } finally {
            if (thisGeneration == generation) {
                isInFlight = false
                isLoading = false
                isLoadingMore = false
            }
        }
    }
}
