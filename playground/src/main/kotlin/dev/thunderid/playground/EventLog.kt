// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.playground

import androidx.compose.runtime.mutableStateListOf
import dev.thunderid.android.StorageAdapter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class EventKind { OK, INFO, ERROR }

data class PlaygroundEvent(
    val time: String,
    val name: String,
    val detail: String,
    val kind: EventKind,
    val source: String,
)

/**
 * The playground's event log. The Android SDK has no event emitter, so the app records what it
 * can observe from outside: the calls it makes, the auth state the provider publishes, and the
 * reads and writes the SDK makes through the [StorageAdapter] it is given.
 */
object EventLog {
    private const val MAX_EVENTS = 300
    private val format = SimpleDateFormat("HH:mm:ss", Locale.US)

    val events = mutableStateListOf<PlaygroundEvent>()

    fun ok(
        name: String,
        detail: String = "",
        source: String = "sdk",
    ) = add(name, detail, EventKind.OK, source)

    fun info(
        name: String,
        detail: String = "",
        source: String = "sdk",
    ) = add(name, detail, EventKind.INFO, source)

    fun error(
        name: String,
        detail: String = "",
        source: String = "sdk",
    ) = add(name, detail, EventKind.ERROR, source)

    fun clear() = events.clear()

    private fun add(
        name: String,
        detail: String,
        kind: EventKind,
        source: String,
    ) {
        val event = PlaygroundEvent(format.format(Date()), name, detail, kind, source)
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            events.add(0, event)
            if (events.size > MAX_EVENTS) events.removeRange(MAX_EVENTS, events.size)
        }
    }
}

/**
 * Wraps the storage adapter handed to the SDK so the playground can log token writes and show the
 * stored tokens in the Session tab. Values are never logged, only key names.
 */
class RecordingStorageAdapter(
    private val delegate: StorageAdapter,
) : StorageAdapter {
    override fun store(
        key: String,
        value: String,
    ) {
        delegate.store(key, value)
        EventLog.info("storage:write", key, source = "storage")
    }

    override fun retrieve(key: String): String? = delegate.retrieve(key)

    override fun delete(key: String) {
        delegate.delete(key)
        EventLog.info("storage:delete", key, source = "storage")
    }

    override fun clear() {
        delegate.clear()
        EventLog.info("storage:clear", "token set removed", source = "storage")
    }

    /** Reads a stored value whose key ends with [suffix] (for example `refresh_token`). */
    fun find(suffix: String): String? = TOKEN_KEYS.firstOrNull { it.endsWith(suffix) }?.let(delegate::retrieve)

    /**
     * Marks the stored access token as expired, so the next `getAccessToken()` call takes the SDK's
     * refresh path. The SDK has no public "refresh now" operation.
     */
    fun expireAccessToken(): Boolean {
        val key = TOKEN_KEYS.firstOrNull { it.endsWith("token_expiry") } ?: return false
        delegate.store(key, "0")
        return true
    }

    private companion object {
        // Key names the SDK's token store writes through the adapter.
        val TOKEN_KEYS =
            listOf(
                "thunder.access_token",
                "thunder.refresh_token",
                "thunder.id_token",
                "thunder.token_expiry",
                "thunder.scope",
                "thunder.device_secret",
                "thunder.flow_sessions",
            )
    }
}
