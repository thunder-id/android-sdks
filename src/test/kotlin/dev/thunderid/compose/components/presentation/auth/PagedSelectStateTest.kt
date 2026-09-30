// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.presentation.auth

import dev.thunderid.android.IAMException
import dev.thunderid.android.ThunderIDErrorCode
import dev.thunderid.android.management.FetchPagedOptions
import dev.thunderid.android.management.PagedSelectOption
import dev.thunderid.android.management.PagedSelectPage
import dev.thunderid.android.management.PagedSelectRequest
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

class PagedSelectStateTest {
    private fun page(
        start: Int,
        count: Int,
        total: Int,
    ): PagedSelectPage<String> =
        PagedSelectPage(
            items = (0 until count).map { "user-${start + it + 1}" },
            nextOffset = if (start + count < total) start + count else null,
            totalResults = total,
        )

    private fun toOptions(items: List<String>): List<PagedSelectOption> = items.map { PagedSelectOption(it, it) }

    private fun state(
        pageSize: Int = 30,
        fetch: suspend (PagedSelectRequest) -> PagedSelectPage<String>,
    ): PagedSelectState<String> = PagedSelectState(pageSize, filter = null, fetch = FetchPagedOptions { fetch(it) }, convert = ::toOptions)

    @Test
    fun `makes no request until loadInitial`() =
        runTest {
            var calls = 0
            val state =
                state {
                    calls++
                    page(0, 0, 0)
                }

            assertEquals(0, calls)
            assertFalse(state.hasLoaded)
        }

    @Test
    fun `loadInitial requests page zero`() =
        runTest {
            val requests = mutableListOf<PagedSelectRequest>()
            val state =
                state {
                    requests += it
                    page(0, 30, 75)
                }

            state.loadInitial()

            assertEquals(1, requests.size)
            assertEquals(0, requests.first().offset)
            assertEquals(30, requests.first().limit)
            assertEquals(30, state.options.size)
            assertTrue(state.hasMore)
        }

    @Test
    fun `loadInitial twice only fetches once`() =
        runTest {
            var calls = 0
            val state =
                state {
                    calls++
                    page(0, 30, 30)
                }

            state.loadInitial()
            state.loadInitial()

            assertEquals(1, calls)
        }

    @Test
    fun `loadMore appends and stops at the last page`() =
        runTest {
            val offsets = mutableListOf<Int>()
            val state =
                state {
                    offsets += it.offset
                    page(it.offset, minOf(30, 75 - it.offset), 75)
                }

            state.loadInitial()
            state.loadMore()
            state.loadMore()

            assertEquals(listOf(0, 30, 60), offsets)
            assertEquals(75, state.options.size)
            assertEquals(75, state.options.distinctBy { it.value }.size)
            assertFalse(state.hasMore)
        }

    @Test
    fun `loadMore does nothing once exhausted`() =
        runTest {
            var calls = 0
            val state =
                state {
                    calls++
                    page(0, 10, 10)
                }

            state.loadInitial()
            assertFalse(state.hasMore)
            state.loadMore()

            assertEquals(1, calls)
        }

    @Test
    fun `first page failure shows a message and retry recovers`() =
        runTest {
            var attempt = 0
            val state =
                state {
                    attempt++
                    if (attempt == 1) throw IAMException(ThunderIDErrorCode.SERVER_ERROR, "Upstream exploded")
                    page(0, 5, 5)
                }

            state.loadInitial()
            assertEquals("Upstream exploded", state.failure?.message)
            assertFalse(state.hasLoaded)

            state.retry()

            assertNull(state.failure)
            assertEquals(5, state.options.size)
        }

    @Test
    fun `next page failure keeps already-loaded options`() =
        runTest {
            var attempt = 0
            val state =
                state {
                    attempt++
                    if (attempt == 2) throw IAMException(ThunderIDErrorCode.NETWORK_ERROR, "Offline")
                    page(it.offset, 30, 60)
                }

            state.loadInitial()
            state.loadMore()

            assertEquals(30, state.options.size)
            assertEquals("Offline", state.failure?.message)

            state.retry()

            assertNull(state.failure)
            assertEquals(60, state.options.size)
        }

    @Test
    fun `a plain exception shows no message`() =
        runTest {
            val state = state { throw RuntimeException("boom") }

            state.loadInitial()

            assertNull(state.failure?.message)
        }

    @Test
    fun `reset drops loaded state so the next loadInitial starts over`() =
        runTest {
            var calls = 0
            val state =
                state {
                    calls++
                    page(0, 5, 5)
                }

            state.loadInitial()
            state.reset()

            assertFalse(state.hasLoaded)
            assertTrue(state.options.isEmpty())

            state.loadInitial()

            assertEquals(2, calls)
            assertEquals(5, state.options.size)
        }

    @Test
    fun `dedupes an item repeated across a page boundary`() =
        runTest {
            val state =
                state(pageSize = 2) {
                    if (it.offset == 0) {
                        PagedSelectPage(items = listOf("a", "b"), nextOffset = 2, totalResults = 3)
                    } else {
                        PagedSelectPage(items = listOf("b", "c"), nextOffset = null, totalResults = 3)
                    }
                }

            state.loadInitial()
            state.loadMore()

            assertEquals(listOf("a", "b", "c"), state.options.map { it.value })
        }

    @Test
    fun `two concurrent loadInitial calls fetch once`() =
        runTest {
            var calls = 0
            val state =
                state {
                    calls++
                    delay(10)
                    page(0, 5, 5)
                }

            coroutineScope {
                launch { state.loadInitial() }
                launch { state.loadInitial() }
            }

            assertEquals(1, calls)
        }

    @Test
    fun `concurrent loadMore calls fetch the page once`() =
        runTest {
            val offsets = mutableListOf<Int>()
            val state =
                state {
                    offsets += it.offset
                    delay(10)
                    page(it.offset, minOf(30, 75 - it.offset), 75)
                }
            state.loadInitial()

            coroutineScope {
                launch { state.loadMore() }
                launch { state.loadMore() }
            }

            assertEquals(listOf(0, 30), offsets)
        }

    @Test
    fun `retry does nothing without a failure`() =
        runTest {
            var calls = 0
            val state =
                state {
                    calls++
                    page(0, 5, 5)
                }

            state.loadInitial()
            state.retry()

            assertEquals(1, calls)
        }

    @Test
    fun `a page mapped to no options still leaves later pages reachable`() =
        runTest {
            val offsets = mutableListOf<Int>()
            val state =
                PagedSelectState<String>(
                    pageSize = 30,
                    filter = null,
                    fetch =
                        FetchPagedOptions {
                            offsets += it.offset
                            if (it.offset == 0) {
                                PagedSelectPage(items = listOf("hidden"), nextOffset = 30, totalResults = 31)
                            } else {
                                PagedSelectPage(items = listOf("user-31"), nextOffset = null, totalResults = 31)
                            }
                        },
                    convert = { items -> toOptions(items.filter { it != "hidden" }) },
                )

            state.loadInitial()

            assertTrue(state.options.isEmpty())
            assertTrue(state.hasMore)

            state.loadMore()

            assertEquals(listOf(0, 30), offsets)
            assertEquals(listOf("user-31"), state.options.map { it.value })
            assertFalse(state.hasMore)
        }

    @Test
    fun `the selected label survives reset and only matches its own value`() =
        runTest {
            val state = state { page(0, 3, 3) }
            state.loadInitial()

            state.select(PagedSelectOption(label = "Jane Smith", value = "user-2"))
            state.reset()

            assertTrue(state.options.isEmpty())
            assertEquals("Jane Smith", state.labelFor("user-2"))
            assertNull(state.labelFor("user-9"))
        }

    @Test
    fun `a loaded option labels a value that was never selected here`() =
        runTest {
            val state =
                PagedSelectState<String>(
                    pageSize = 30,
                    filter = null,
                    fetch =
                        FetchPagedOptions {
                            PagedSelectPage(
                                options = listOf(PagedSelectOption("Jane Smith", "user-2")),
                                nextOffset = null,
                            )
                        },
                    convert = { emptyList() },
                )

            state.loadInitial()

            assertEquals("Jane Smith", state.labelFor("user-2"))
        }

    @Test
    fun `changing the filter resets and the next load uses it`() =
        runTest {
            val filters = mutableListOf<String?>()
            val fetch =
                FetchPagedOptions<String> {
                    filters += it.filter
                    page(0, 2, 2)
                }
            val state = PagedSelectState(30, "department eq \"A\"", fetch, ::toOptions)
            state.loadInitial()

            val didReset = state.configure(30, "department eq \"B\"", fetch, ::toOptions)

            assertTrue(didReset)
            assertFalse(state.hasLoaded)
            assertTrue(state.options.isEmpty())

            state.loadInitial()

            assertEquals(listOf<String?>("department eq \"A\"", "department eq \"B\""), filters)
        }

    @Test
    fun `an unchanged filter keeps the pages but adopts the new loader`() =
        runTest {
            var source = "first"
            val state =
                state {
                    source = "first"
                    page(0, 30, 60)
                }
            state.loadInitial()

            val didReset =
                state.configure(
                    30,
                    null,
                    FetchPagedOptions {
                        source = "second"
                        page(it.offset, 30, 60)
                    },
                    ::toOptions,
                )
            state.loadMore()

            assertFalse(didReset)
            assertEquals("second", source)
            assertEquals(60, state.options.size)
        }

    @Test
    fun `a result from the old filter is dropped after a change`() =
        runTest {
            val slow =
                FetchPagedOptions<String> {
                    delay(30)
                    page(0, 5, 5)
                }
            val state = PagedSelectState(30, "old", slow, ::toOptions)

            coroutineScope {
                launch { state.loadInitial() }
                delay(5)
                state.configure(30, "new", FetchPagedOptions { page(0, 1, 1) }, ::toOptions)
            }

            assertFalse(state.hasLoaded)
            assertTrue(state.options.isEmpty())
        }

    @Test
    fun `a cancelled load is rethrown and leaves no failure`() =
        runTest {
            val state = state { throw CancellationException("cancelled") }

            try {
                state.loadInitial()
                fail("expected the cancellation to propagate")
            } catch (e: CancellationException) {
                assertEquals("cancelled", e.message)
            }

            assertNull(state.failure)
            assertFalse(state.isLoading)
        }
}
