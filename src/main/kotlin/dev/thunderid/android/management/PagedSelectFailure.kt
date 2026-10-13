// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.management

import dev.thunderid.android.IAMException

/**
 * Where a failed page load should get its display text from.
 *
 * [message] is present only when the loader threw an [IAMException], and takes precedence when it
 * is. When it is absent, the caller falls back to a generic, translatable message
 * (`i18n.resolve("userSelect.loadError")`) — this type only records that a load failed, not what
 * to say about it, keeping this module free of i18n concerns the same way `mapCredentialError` is.
 */
data class PagedSelectFailure(
    val message: String?,
)

/**
 * Maps a `FetchPagedOptions` loader failure onto what a picker should show.
 *
 * The loader is consumer-supplied — it is not guaranteed to throw an [IAMException] the way
 * `client.users.list` does — so only an [IAMException]'s message is trusted for direct display;
 * anything else (a bare exception, an upstream failure with an unreviewed message) carries no
 * message, and the caller shows the generic fallback instead. [IAMException.message] carries the
 * `[CODE]` prefix `Exception` derives it with, so that prefix is stripped before display.
 */
fun mapPagedSelectError(error: Throwable): PagedSelectFailure {
    val iamError = error as? IAMException ?: return PagedSelectFailure(message = null)
    val message = iamError.message?.removePrefix("[${iamError.code.value}] ")
    return PagedSelectFailure(message = message)
}
