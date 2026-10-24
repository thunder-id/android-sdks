// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.backend

import dev.thunderid.android.StorageAdapter

/**
 * Keeps the backend session handle in the platform's secure store, through the same [StorageAdapter]
 * the token store uses, so a caller-supplied adapter applies here too.
 *
 * The handle stands for a session whose tokens stay on the backend, so it is written and read like a
 * token and never logged.
 */
internal class BackendSessionStore(
    private val storage: StorageAdapter,
    vendor: String,
    instanceId: Int?,
) {
    private val key = "$vendor.${instanceId?.let { "instance_$it-" } ?: ""}backend_session_handle"

    fun save(handle: String) = storage.store(key, handle)

    fun handle(): String? = storage.retrieve(key)

    fun clear() = storage.delete(key)
}
