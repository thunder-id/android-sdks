// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.management

import dev.thunderid.android.IAMException
import dev.thunderid.android.ThunderIDErrorCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

private const val LOG_TAG = "SelectLoader"

private val RETRYABLE_CODES =
    setOf(ThunderIDErrorCode.SERVER_ERROR, ThunderIDErrorCode.NETWORK_ERROR, ThunderIDErrorCode.REQUEST_TIMEOUT)

/**
 * Tells whether a failed read is worth trying once more: a server error, or no response at all (a
 * network failure or a timeout). Every other failure, including a client error, is final.
 */
fun isRetryableSelectError(error: Throwable): Boolean = error is IAMException && error.code in RETRYABLE_CODES

/**
 * Runs a read operation and, when it fails with a retryable error, runs it once more after a short
 * pause. Cancelling the coroutine cancels the pause and skips the retry. Only use it for operations
 * that are safe to repeat.
 */
suspend fun <R> withSelectRetry(
    delayMillis: Long = SelectConstants.RETRY_DELAY_MILLIS,
    shouldRetry: (Throwable) -> Boolean = ::isRetryableSelectError,
    operation: suspend () -> R,
): R =
    try {
        operation()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        if (!shouldRetry(failure)) throw failure
        delay(delayMillis)
        operation()
    }

/**
 * What is known about a failed page load. It carries no text from the failure itself, which is
 * whatever the server or a proxy replied and can hold data the SDK does not control.
 */
data class SelectLoadFailure(
    val resource: String,
    /** The SDK error code, or `unknown` for any other failure. */
    val code: String,
    val attempts: Int,
    val limit: Int,
    val offset: Int,
    val durationMillis: Long,
)

/**
 * Logs the final failure of a page load once. For one picker, the first failure of each error code
 * is logged as an error and repeats at debug level, so repeated attempts against a failing list do
 * not flood the log.
 */
class SelectFailureLogger(
    private val sink: (failure: SelectLoadFailure, isRepeat: Boolean) -> Unit = ::writeToSystemLog,
) {
    private val loggedCodes: MutableSet<String> = java.util.Collections.synchronizedSet(mutableSetOf())

    fun log(failure: SelectLoadFailure) {
        sink(failure, !loggedCodes.add(failure.code))
    }

    companion object {
        /** Writes one line naming the call and its context, never the text of the failure. */
        fun writeToSystemLog(
            failure: SelectLoadFailure,
            isRepeat: Boolean,
        ) {
            val context =
                "code=${failure.code} attempts=${failure.attempts} offset=${failure.offset} " +
                    "limit=${failure.limit} durationMs=${failure.durationMillis}"
            if (isRepeat) {
                android.util.Log.d(LOG_TAG, "Failed to load ${failure.resource} again. $context")
            } else {
                android.util.Log.e(LOG_TAG, "Failed to load ${failure.resource}. $context")
            }
        }
    }
}

/**
 * Runs one page request of a resource list. A server error or a failure with no response is retried
 * once, a client error is not. The final failure is logged once, with the call context, and rethrown
 * unchanged. Cancellation is not a failure and is not logged.
 */
suspend fun <R> loadSelectPageWithRetryAndLog(
    resource: String,
    limit: Int,
    offset: Int,
    logger: SelectFailureLogger,
    retryDelayMillis: Long = SelectConstants.RETRY_DELAY_MILLIS,
    operation: suspend () -> R,
): R {
    val startedAt = System.nanoTime()
    var attempts = 0
    try {
        return withSelectRetry(retryDelayMillis) {
            attempts += 1
            operation()
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        logger.log(
            SelectLoadFailure(
                resource = resource,
                code = (failure as? IAMException)?.code?.value ?: "unknown",
                attempts = attempts,
                limit = limit,
                offset = offset,
                durationMillis = (System.nanoTime() - startedAt) / 1_000_000,
            ),
        )
        throw failure
    }
}

/**
 * Builds the loader a select picker gives to its options helper. The loader asks the page function
 * for one page at the cursor offset, retries a server or network failure once, converts the items
 * to options and returns them with the offset of the next page.
 */
class ResourceOptionsLoader<T>(
    /** What is being loaded, such as `users`. Named in the failure log. */
    private val resource: String,
    pageSize: Int = SelectConstants.DEFAULT_PAGE_SIZE,
    private val filter: String? = null,
    private val logger: SelectFailureLogger = SelectFailureLogger(),
    private val retryDelayMillis: Long = SelectConstants.RETRY_DELAY_MILLIS,
    private val fetchPage: FetchSelectPage<T>,
    /** Converts the raw items of a page that came without ready-made options. */
    private val convert: (List<T>) -> List<SelectOption> = { emptyList() },
) {
    val pageSize: Int = resolvePageSize(pageSize)

    suspend fun load(cursor: Int?): SelectOptionsBatch<Int> {
        val offset = cursor ?: 0
        val request = SelectPageRequest(limit = pageSize, offset = offset, filter = filter)
        val page =
            loadSelectPageWithRetryAndLog(resource, pageSize, offset, logger, retryDelayMillis) {
                fetchPage.fetch(request)
            }
        return SelectOptionsBatch.Page(
            options = page.options ?: convert(page.items ?: emptyList()),
            nextCursor = page.nextOffset.takeIf { SelectPaging.isAdvancingPageOffset(offset, it) },
        )
    }
}
