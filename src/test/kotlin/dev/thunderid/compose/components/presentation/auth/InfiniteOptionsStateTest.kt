// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.presentation.auth

import dev.thunderid.android.management.SelectConstants
import dev.thunderid.android.management.SelectOption
import dev.thunderid.android.management.SelectOptionsBatch
import dev.thunderid.android.management.SelectOptionsLoadContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InfiniteOptionsStateTest {
    private class Boom(
        val id: Int,
    ) : Exception("boom $id")

    private fun options(range: IntRange) = range.map { SelectOption("User ${it + 1}", "user-${it + 1}") }

    private fun page(
        start: Int,
        count: Int,
        total: Int,
    ): SelectOptionsBatch<Int> =
        SelectOptionsBatch.Page(
            options = options(start until start + count),
            nextCursor = if (start + count < total) start + count else null,
        )

    private fun TestScope.state(
        initialOptions: List<SelectOption> = emptyList(),
        warnings: MutableList<String> = mutableListOf(),
        onError: ((Throwable) -> Unit)? = null,
        load: suspend (SelectOptionsLoadContext<Int>) -> SelectOptionsBatch<Int>?,
    ) = InfiniteOptionsState(this, initialOptions, load, onError) { warnings.add(it) }

    @Test
    fun `makes no request until loadMore`() =
        runTest {
            var calls = 0
            val state =
                state {
                    calls++
                    page(0, 0, 0)
                }

            advanceUntilIdle()

            assertEquals(0, calls)
            assertTrue(state.options.isEmpty())
            assertFalse(state.loading)
        }

    @Test
    fun `the initial options are shown before anything loads`() =
        runTest {
            val state = state(initialOptions = options(0..1)) { null }

            assertEquals(listOf("user-1", "user-2"), state.options.map { it.value })
        }

    @Test
    fun `the first load has no cursor`() =
        runTest {
            val contexts = mutableListOf<SelectOptionsLoadContext<Int>>()
            val state =
                state { context ->
                    contexts.add(context)
                    page(0, 30, 75)
                }

            state.loadMore()
            advanceUntilIdle()

            assertEquals(1, contexts.size)
            assertNull(contexts[0].cursor)
            assertEquals(30, state.options.size)
            assertTrue(state.hasMore)
        }

    @Test
    fun `two immediate calls make one request`() =
        runTest {
            var calls = 0
            val state =
                state {
                    calls++
                    page(0, 30, 75)
                }

            state.loadMore()
            state.loadMore()
            advanceUntilIdle()

            assertEquals(1, calls)
        }

    @Test
    fun `the next load continues from the stored cursor`() =
        runTest {
            val cursors = mutableListOf<Int?>()
            val state =
                state { context ->
                    cursors.add(context.cursor)
                    page(context.cursor ?: 0, 30, 75)
                }

            state.loadMore()
            advanceUntilIdle()
            state.loadMore()
            advanceUntilIdle()

            assertEquals(listOf(null, 30), cursors)
            assertEquals("user-60", state.options.last().value)
        }

    @Test
    fun `does nothing once the data has ended`() =
        runTest {
            var calls = 0
            val state =
                state {
                    calls++
                    SelectOptionsBatch.Page(options(0..2), nextCursor = null)
                }

            state.loadMore()
            advanceUntilIdle()
            state.loadMore()
            advanceUntilIdle()

            assertEquals(1, calls)
            assertFalse(state.hasMore)
        }

    @Test
    fun `a failed load keeps the options sets the error and calls onError`() =
        runTest {
            val reported = mutableListOf<Throwable>()
            var attempt = 0
            val state =
                state(onError = { reported.add(it) }) { context ->
                    attempt++
                    if (attempt == 2) throw Boom(2)
                    page(context.cursor ?: 0, 30, 75)
                }

            state.loadMore()
            advanceUntilIdle()
            state.loadMore()
            advanceUntilIdle()

            assertEquals(30, state.options.size)
            assertEquals(2, (state.error as Boom).id)
            assertEquals(1, reported.size)
            assertTrue(state.hasMore)
            assertFalse(state.loading)
        }

    @Test
    fun `asking again after a failure repeats the same request and clears the error`() =
        runTest {
            val cursors = mutableListOf<Int?>()
            var attempt = 0
            val state =
                state { context ->
                    cursors.add(context.cursor)
                    attempt++
                    if (attempt == 1) throw Boom(1)
                    page(0, 30, 75)
                }

            state.loadMore()
            advanceUntilIdle()
            state.loadMore()
            advanceUntilIdle()

            assertEquals(listOf(null, null), cursors)
            assertNull(state.error)
            assertEquals(30, state.options.size)
        }

    @Test
    fun `an onError callback that throws does not break the list`() =
        runTest {
            val warnings = mutableListOf<String>()
            val state =
                state(warnings = warnings, onError = { throw IllegalStateException("callback bug") }) { throw Boom(1) }

            state.loadMore()
            advanceUntilIdle()

            assertFalse(state.loading)
            assertTrue(state.error is Boom)
            assertEquals(1, warnings.size)
        }

    @Test
    fun `removes an option repeated across a batch boundary`() =
        runTest {
            val state =
                state { context ->
                    if (context.cursor == null) {
                        SelectOptionsBatch.Page(options(0..2), nextCursor = 3)
                    } else {
                        SelectOptionsBatch.Page(options(2..4), nextCursor = null)
                    }
                }

            state.loadMore()
            advanceUntilIdle()
            state.loadMore()
            advanceUntilIdle()

            assertEquals(listOf("user-1", "user-2", "user-3", "user-4", "user-5"), state.options.map { it.value })
        }

    @Test
    fun `a result from before a reset is dropped and does not end the new load`() =
        runTest {
            val staleGate = CompletableDeferred<Unit>()
            val freshGate = CompletableDeferred<Unit>()
            var call = 0
            val state =
                state {
                    call++
                    if (call == 1) {
                        // A loader that ignores cancellation, so only the generation guard can drop its result.
                        withContext(NonCancellable) { staleGate.await() }
                        page(0, 3, 10)
                    } else {
                        freshGate.await()
                        SelectOptionsBatch.Page(options(100..102), nextCursor = null)
                    }
                }

            state.loadMore()
            runCurrent()
            state.reset()
            runCurrent()
            staleGate.complete(Unit)
            runCurrent()

            assertTrue(state.options.isEmpty())
            assertTrue(state.loading)

            freshGate.complete(Unit)
            advanceUntilIdle()

            assertEquals(listOf("user-101", "user-102", "user-103"), state.options.map { it.value })
            assertFalse(state.loading)
            assertFalse(state.hasMore)
        }

    @Test
    fun `a failure from before a reset is not reported`() =
        runTest {
            val staleGate = CompletableDeferred<Unit>()
            var reported = 0
            var call = 0
            val state =
                state(onError = { reported++ }) {
                    call++
                    if (call == 1) {
                        withContext(NonCancellable) { staleGate.await() }
                        throw Boom(1)
                    }
                    page(0, 3, 3)
                }

            state.loadMore()
            runCurrent()
            state.reset()
            runCurrent()
            staleGate.complete(Unit)
            advanceUntilIdle()

            assertEquals(0, reported)
            assertNull(state.error)
            assertEquals(3, state.options.size)
            assertFalse(state.loading)
        }

    @Test
    fun `reset goes back to the initial options and the first cursor`() =
        runTest {
            val cursors = mutableListOf<Int?>()
            val state =
                state(initialOptions = options(0..0)) { context ->
                    cursors.add(context.cursor)
                    page(context.cursor ?: 0, 2, 4)
                }

            state.loadMore()
            advanceUntilIdle()
            state.reset()
            advanceUntilIdle()

            assertEquals(listOf(null, null), cursors)
        }

    @Test
    fun `reset loads again right away when loading had been requested`() =
        runTest {
            var calls = 0
            val state =
                state {
                    calls++
                    page(0, 3, 3)
                }

            state.loadMore()
            advanceUntilIdle()
            state.reset()
            advanceUntilIdle()

            assertEquals(2, calls)
            assertEquals(3, state.options.size)
        }

    @Test
    fun `reset does not load when nothing was requested`() =
        runTest {
            var calls = 0
            val state =
                state(initialOptions = options(0..0)) {
                    calls++
                    null
                }

            state.reset()
            advanceUntilIdle()

            assertEquals(0, calls)
            assertEquals(1, state.options.size)
        }

    @Test
    fun `a batch that adds nothing new keeps loading up to the limit then waits`() =
        runTest {
            var calls = 0
            val state =
                state(initialOptions = options(0..0)) { context ->
                    calls++
                    SelectOptionsBatch.Page(options(0..0), nextCursor = (context.cursor ?: 0) + 1)
                }

            state.loadMore()
            advanceUntilIdle()

            assertEquals(SelectConstants.MAX_EMPTY_CONTINUATIONS + 1, calls)
            assertTrue(state.hasMore)
            assertFalse(state.loading)

            state.loadMore()
            advanceUntilIdle()

            assertEquals(2 * (SelectConstants.MAX_EMPTY_CONTINUATIONS + 1), calls)
        }

    @Test
    fun `stops continuing as soon as a batch adds something`() =
        runTest {
            var calls = 0
            val state =
                state { context ->
                    calls++
                    val cursor = context.cursor ?: 0
                    if (cursor < 2) {
                        SelectOptionsBatch.Page(emptyList(), nextCursor = cursor + 1)
                    } else {
                        SelectOptionsBatch.Page(options(0..1), nextCursor = cursor + 1)
                    }
                }

            state.loadMore()
            advanceUntilIdle()

            assertEquals(3, calls)
            assertEquals(2, state.options.size)
        }

    @Test
    fun `a cursor that does not move ends the list and warns`() =
        runTest {
            val warnings = mutableListOf<String>()
            val state =
                state(initialOptions = options(0..0), warnings = warnings) {
                    SelectOptionsBatch.Page(options(0..0), nextCursor = 5)
                }

            state.loadMore()
            advanceUntilIdle()

            assertFalse(state.hasMore)
            assertEquals(1, warnings.size)
        }

    @Test
    fun `a plain list ends when it adds nothing new`() =
        runTest {
            var calls = 0
            val state =
                state {
                    calls++
                    SelectOptionsBatch.OptionList(options(0..1))
                }

            state.loadMore()
            advanceUntilIdle()
            assertTrue(state.hasMore)
            state.loadMore()
            advanceUntilIdle()

            assertEquals(2, calls)
            assertFalse(state.hasMore)
        }

    @Test
    fun `a missing batch counts as empty and ends the list`() =
        runTest {
            val state = state { null }

            state.loadMore()
            advanceUntilIdle()

            assertTrue(state.options.isEmpty())
            assertFalse(state.hasMore)
        }

    @Test
    fun `update gives the next request the new loader without restarting the list`() =
        runTest {
            val used = mutableListOf<String>()
            val state =
                state { context ->
                    used.add("old")
                    page(context.cursor ?: 0, 2, 6)
                }

            state.loadMore()
            advanceUntilIdle()
            state.update({ context ->
                used.add("new")
                page(context.cursor ?: 0, 2, 6)
            })
            state.loadMore()
            advanceUntilIdle()

            assertEquals(listOf("old", "new"), used)
            assertEquals(4, state.options.size)
        }

    @Test
    fun `two states do not share a cursor`() =
        runTest {
            val first = state { context -> page(context.cursor ?: 0, 2, 6) }
            val second = state { context -> page(context.cursor ?: 0, 2, 6) }

            first.loadMore()
            advanceUntilIdle()
            first.loadMore()
            advanceUntilIdle()
            second.loadMore()
            advanceUntilIdle()

            assertEquals(4, first.options.size)
            assertEquals(listOf("user-1", "user-2"), second.options.map { it.value })
        }
}
