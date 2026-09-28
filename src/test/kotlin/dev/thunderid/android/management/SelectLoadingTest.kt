// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.management

import dev.thunderid.android.IAMException
import dev.thunderid.android.ThunderIDErrorCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun option(
    value: String,
    label: String = value,
) = SelectOption(label = label, value = value)

private fun optionList(vararg values: String) = SelectOptionsBatch.OptionList(values.map { option(it) })

private fun pageOf(
    vararg values: String,
    nextCursor: Int?,
) = SelectOptionsBatch.Page(values.map { option(it) }, nextCursor)

private fun failure(
    code: ThunderIDErrorCode,
    message: String = "failed",
) = IAMException(code, message)

class SelectOptionsMergeTest {
    @Test
    fun `a list that adds something continues`() {
        val result = mergeSelectOptionsBatch<Int>(null, listOf(option("a")), optionList("b"))

        assertEquals(listOf("a", "b"), result.options.map { it.value })
        assertFalse(result.finished)
    }

    @Test
    fun `a list that adds nothing new ends the data`() {
        val result = mergeSelectOptionsBatch<Int>(null, listOf(option("a")), optionList("a"))

        assertTrue(result.finished)
        assertNull(result.issue)
    }

    @Test
    fun `a page without a cursor ends the data after appending`() {
        val result = mergeSelectOptionsBatch(0, emptyList(), pageOf("a", nextCursor = null))

        assertEquals(listOf("a"), result.options.map { it.value })
        assertTrue(result.finished)
        assertNull(result.cursor)
    }

    @Test
    fun `a page with a cursor continues from it`() {
        val result = mergeSelectOptionsBatch(0, emptyList(), pageOf("a", nextCursor = 30))

        assertFalse(result.finished)
        assertEquals(30, result.cursor)
    }

    @Test
    fun `a page of duplicates with an advancing cursor continues`() {
        val result = mergeSelectOptionsBatch(0, listOf(option("a")), pageOf("a", nextCursor = 30))

        assertFalse(result.finished)
        assertEquals(30, result.cursor)
    }

    @Test
    fun `a page of duplicates with the same cursor ends the data and reports it`() {
        val result = mergeSelectOptionsBatch(30, listOf(option("a")), pageOf("a", nextCursor = 30))

        assertTrue(result.finished)
        assertEquals(SelectOptionsBatchIssue.CURSOR_STUCK, result.issue)
    }

    @Test
    fun `a repeated option is replaced in place by its newer copy`() {
        val existing = listOf(option("a", "Old"), option("b"))
        val result =
            mergeSelectOptionsBatch<Int>(null, existing, SelectOptionsBatch.OptionList(listOf(option("a", "New"))))

        assertEquals(listOf("New", "b"), result.options.map { it.label })
    }

    @Test
    fun `a missing batch counts as an empty list`() {
        val result = mergeSelectOptionsBatch<Int>(null, listOf(option("a")), null)

        assertEquals(listOf("a"), result.options.map { it.value })
        assertTrue(result.finished)
    }
}

class LoadUntilOptionsGrowTest {
    @Test
    fun `stops after a batch that grew the list`() =
        runTest {
            var loads = 0

            loadUntilOptionsGrow(loadBatch = { ++loads }, applyBatch = { false }, isCurrent = { true })

            assertEquals(1, loads)
        }

    @Test
    fun `keeps loading while batches add nothing but stops after a few in a row`() =
        runTest {
            var loads = 0

            loadUntilOptionsGrow(loadBatch = { ++loads }, applyBatch = { true }, isCurrent = { true })

            assertEquals(SelectConstants.MAX_EMPTY_CONTINUATIONS + 1, loads)
        }

    @Test
    fun `stops without applying once the list was reset`() =
        runTest {
            var applied = 0

            loadUntilOptionsGrow(loadBatch = { 1 }, applyBatch = {
                applied++
                true
            }, isCurrent = { false })

            assertEquals(0, applied)
        }

    @Test
    fun `passes a load failure on`() =
        runTest {
            val thrown =
                try {
                    loadUntilOptionsGrow<Int>(
                        loadBatch = { throw IllegalStateException("boom") },
                        applyBatch = { false },
                        isCurrent = { true },
                    )
                    null
                } catch (error: IllegalStateException) {
                    error
                }

            assertEquals("boom", thrown?.message)
        }
}

class SelectRetryTest {
    @Test
    fun `retries a server error and a no response failure`() {
        assertTrue(isRetryableSelectError(failure(ThunderIDErrorCode.SERVER_ERROR)))
        assertTrue(isRetryableSelectError(failure(ThunderIDErrorCode.NETWORK_ERROR)))
        assertTrue(isRetryableSelectError(failure(ThunderIDErrorCode.REQUEST_TIMEOUT)))
    }

    @Test
    fun `does not retry a client error or an unknown failure`() {
        assertFalse(isRetryableSelectError(failure(ThunderIDErrorCode.FORBIDDEN)))
        assertFalse(isRetryableSelectError(failure(ThunderIDErrorCode.NOT_FOUND)))
        assertFalse(isRetryableSelectError(failure(ThunderIDErrorCode.INVALID_INPUT)))
        assertFalse(isRetryableSelectError(failure(ThunderIDErrorCode.AUTHENTICATION_FAILED)))
        assertFalse(isRetryableSelectError(RuntimeException("boom")))
    }

    @Test
    fun `a retryable failure is tried once more and can recover`() =
        runTest {
            var calls = 0

            val result =
                withSelectRetry {
                    calls++
                    if (calls == 1) throw failure(ThunderIDErrorCode.SERVER_ERROR)
                    "ok"
                }

            assertEquals("ok", result)
            assertEquals(2, calls)
        }

    @Test
    fun `the pause before the retry is the configured delay`() =
        runTest {
            var calls = 0
            val started = testScheduler.currentTime

            withSelectRetry {
                calls++
                if (calls == 1) throw failure(ThunderIDErrorCode.NETWORK_ERROR)
                "ok"
            }

            assertEquals(SelectConstants.RETRY_DELAY_MILLIS, testScheduler.currentTime - started)
        }

    @Test
    fun `the second failure is the one that is thrown`() =
        runTest {
            var calls = 0

            val thrown =
                try {
                    withSelectRetry { throw failure(ThunderIDErrorCode.SERVER_ERROR, "attempt ${++calls}") }
                    null
                } catch (error: IAMException) {
                    error
                }

            assertTrue(thrown?.message?.contains("attempt 2") == true)
        }

    @Test
    fun `a client error is not retried`() =
        runTest {
            var calls = 0

            val thrown =
                try {
                    withSelectRetry {
                        calls++
                        throw failure(ThunderIDErrorCode.FORBIDDEN)
                    }
                    null
                } catch (error: IAMException) {
                    error
                }

            assertEquals(ThunderIDErrorCode.FORBIDDEN, thrown?.code)
            assertEquals(1, calls)
        }

    @Test
    fun `cancelling during the pause skips the retry`() =
        runTest {
            var calls = 0
            val job =
                launch {
                    withSelectRetry {
                        calls++
                        throw failure(ThunderIDErrorCode.SERVER_ERROR)
                    }
                }

            runCurrent()
            job.cancel()
            advanceUntilIdle()

            assertEquals(1, calls)
            assertTrue(job.isCancelled)
        }

    @Test
    fun `a cancellation is never retried`() =
        runTest {
            var calls = 0

            val thrown =
                try {
                    withSelectRetry {
                        calls++
                        throw CancellationException("stop")
                    }
                    null
                } catch (error: CancellationException) {
                    error
                }

            assertEquals("stop", thrown?.message)
            assertEquals(1, calls)
        }

    @Test
    fun `a cancellation is not retried even when the retry rule accepts every failure`() =
        runTest {
            var calls = 0

            val thrown =
                try {
                    withSelectRetry(shouldRetry = { true }) {
                        calls++
                        throw CancellationException("stop")
                    }
                    null
                } catch (error: CancellationException) {
                    error
                }

            assertEquals("stop", thrown?.message)
            assertEquals(1, calls)
        }
}

class SelectFailureLoggerTest {
    private class Recorder {
        val entries = mutableListOf<Pair<SelectLoadFailure, Boolean>>()
        val logger = SelectFailureLogger { failure, isRepeat -> entries.add(failure to isRepeat) }
    }

    private fun failureOf(code: String) = SelectLoadFailure("users", code, attempts = 2, limit = 30, offset = 0, durationMillis = 5)

    @Test
    fun `the first failure of a code is logged and repeats are marked`() {
        val recorder = Recorder()

        recorder.logger.log(failureOf("SERVER_ERROR"))
        recorder.logger.log(failureOf("SERVER_ERROR"))
        recorder.logger.log(failureOf("FORBIDDEN"))

        assertEquals(listOf(false, true, false), recorder.entries.map { it.second })
    }

    @Test
    fun `a load that succeeds logs nothing`() =
        runTest {
            val recorder = Recorder()

            val result = loadSelectPageWithRetryAndLog("users", 30, 0, recorder.logger) { "page" }

            assertEquals("page", result)
            assertEquals(0, recorder.entries.size)
        }

    @Test
    fun `a load that recovers on the retry logs nothing`() =
        runTest {
            val recorder = Recorder()
            var calls = 0

            val result =
                loadSelectPageWithRetryAndLog("users", 30, 0, recorder.logger) {
                    calls++
                    if (calls == 1) throw failure(ThunderIDErrorCode.SERVER_ERROR, "Server error: 503")
                    "page"
                }

            assertEquals("page", result)
            assertEquals(2, calls)
            assertEquals(0, recorder.entries.size)
        }

    @Test
    fun `the final failure is logged once with the call context and rethrown unchanged`() =
        runTest {
            val recorder = Recorder()

            val thrown =
                try {
                    loadSelectPageWithRetryAndLog<String>("users", 30, 60, recorder.logger) {
                        throw failure(ThunderIDErrorCode.SERVER_ERROR, "Server error: 503")
                    }
                    null
                } catch (error: IAMException) {
                    error
                }

            assertTrue(thrown?.message?.contains("Server error: 503") == true)
            assertEquals(1, recorder.entries.size)
            val logged = recorder.entries[0].first
            assertEquals("users", logged.resource)
            assertEquals("SERVER_ERROR", logged.code)
            assertEquals(2, logged.attempts)
            assertEquals(30, logged.limit)
            assertEquals(60, logged.offset)
            assertTrue(logged.durationMillis >= 0)
        }

    @Test
    fun `a client error is logged after one attempt`() =
        runTest {
            val recorder = Recorder()

            runCatching {
                loadSelectPageWithRetryAndLog<String>("auth flows", 30, 0, recorder.logger) {
                    throw failure(ThunderIDErrorCode.FORBIDDEN)
                }
            }

            assertEquals(
                "FORBIDDEN",
                recorder.entries
                    .first()
                    .first.code,
            )
            assertEquals(
                1,
                recorder.entries
                    .first()
                    .first.attempts,
            )
        }

    @Test
    fun `a failure that is not an IAMException is logged as unknown and not retried`() =
        runTest {
            val recorder = Recorder()
            var calls = 0

            runCatching {
                loadSelectPageWithRetryAndLog<String>("users", 30, 0, recorder.logger) {
                    calls++
                    throw RuntimeException("boom")
                }
            }

            assertEquals(
                "unknown",
                recorder.entries
                    .first()
                    .first.code,
            )
            assertEquals(1, calls)
        }

    @Test
    fun `a cancelled load is not logged as a failure`() =
        runTest {
            val recorder = Recorder()
            val job =
                launch {
                    loadSelectPageWithRetryAndLog("users", 30, 0, recorder.logger) {
                        delay(5_000)
                        "late"
                    }
                }

            runCurrent()
            job.cancel()
            advanceUntilIdle()

            assertEquals(0, recorder.entries.size)
        }
}

class ResourceOptionsLoaderTest {
    private fun loader(
        pageSize: Int = 30,
        filter: String? = null,
        logger: SelectFailureLogger = SelectFailureLogger { _, _ -> },
        fetchPage: suspend (SelectPageRequest) -> SelectPage<String>,
    ) = ResourceOptionsLoader(
        resource = "items",
        pageSize = pageSize,
        filter = filter,
        logger = logger,
        fetchPage = FetchSelectPage { fetchPage(it) },
        convert = { items -> items.map { SelectOption(it, it) } },
    )

    @Test
    fun `asks for the cursor offset with the page size and filter`() =
        runTest {
            val requests = mutableListOf<SelectPageRequest>()
            val loader =
                loader(pageSize = 25, filter = "department eq sales") {
                    requests.add(it)
                    SelectPage(options = emptyList(), nextOffset = null)
                }

            loader.load(null)
            loader.load(50)

            assertEquals(listOf(0, 50), requests.map { it.offset })
            assertEquals(listOf(25, 25), requests.map { it.limit })
            assertEquals(listOf("department eq sales", "department eq sales"), requests.map { it.filter })
        }

    @Test
    fun `clamps the page size to what the backend serves`() =
        runTest {
            val requests = mutableListOf<SelectPageRequest>()
            val record: suspend (SelectPageRequest) -> SelectPage<String> = {
                requests.add(it)
                SelectPage(options = emptyList(), nextOffset = null)
            }

            loader(pageSize = 500, fetchPage = record).load(null)
            loader(pageSize = 0, fetchPage = record).load(null)

            assertEquals(listOf(100, 1), requests.map { it.limit })
        }

    @Test
    fun `converts raw items when the page has no ready made options`() =
        runTest {
            val loader = loader { SelectPage(items = listOf("a", "b"), nextOffset = 2) }

            val batch = loader.load(null) as SelectOptionsBatch.Page

            assertEquals(listOf("a", "b"), batch.options.map { it.value })
            assertEquals(2, batch.nextCursor)
        }

    @Test
    fun `prefers ready made options over items`() =
        runTest {
            val ready = listOf(SelectOption("Ready", "ready"))
            val loader = loader { SelectPage(items = listOf("raw"), options = ready, nextOffset = null) }

            val batch = loader.load(null) as SelectOptionsBatch.Page

            assertEquals(listOf("ready"), batch.options.map { it.value })
        }

    @Test
    fun `ends the list when the next offset does not advance`() =
        runTest {
            val sameOffset = loader { SelectPage(options = emptyList(), nextOffset = it.offset) }
            val backwards = loader { SelectPage(options = emptyList(), nextOffset = 0) }

            assertNull((sameOffset.load(30) as SelectOptionsBatch.Page).nextCursor)
            assertNull((backwards.load(30) as SelectOptionsBatch.Page).nextCursor)
        }

    @Test
    fun `retries a server error once without logging it`() =
        runTest {
            val logged = mutableListOf<SelectLoadFailure>()
            var calls = 0
            val loader =
                loader(logger = SelectFailureLogger { failure, _ -> logged.add(failure) }) {
                    calls++
                    if (calls == 1) throw failure(ThunderIDErrorCode.SERVER_ERROR, "Server error: 503")
                    SelectPage(options = listOf(SelectOption("A", "a")), nextOffset = null)
                }

            val batch = loader.load(null) as SelectOptionsBatch.Page

            assertEquals(listOf("a"), batch.options.map { it.value })
            assertEquals(0, logged.size)
        }

    @Test
    fun `a load that fails for good is logged once and throws the original error`() =
        runTest {
            val logged = mutableListOf<SelectLoadFailure>()
            val loader =
                loader(logger = SelectFailureLogger { failure, _ -> logged.add(failure) }) {
                    throw failure(ThunderIDErrorCode.FORBIDDEN)
                }

            val thrown =
                try {
                    loader.load(null)
                    null
                } catch (error: IAMException) {
                    error
                }

            assertEquals(ThunderIDErrorCode.FORBIDDEN, thrown?.code)
            assertEquals(1, logged.size)
            assertEquals("items", logged[0].resource)
        }
}
