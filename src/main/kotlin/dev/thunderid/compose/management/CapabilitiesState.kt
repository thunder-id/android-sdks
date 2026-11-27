// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.management

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.thunderid.android.User
import dev.thunderid.android.api.management.Capabilities
import dev.thunderid.android.api.management.RecordAccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The signed-in user's management capabilities, shared by every component under one
 * [dev.thunderid.compose.ThunderIDProvider] as `ThunderIDState.capabilities`.
 *
 * Nothing is read until [load] is called, which [dev.thunderid.compose.components.management.CapabilityGuard] and
 * the management components do for you. At most one read runs at a time and every consumer shares its answer.
 * The answer is kept for the signed-in user, their organization unit and their granted permissions, and read
 * again when any of them changes after first use. Signing out discards it.
 *
 * Capabilities decide what to offer, not what is allowed: they are not an authorization decision. The server
 * authorizes every management request, and the SDK never skips or blocks a request because of them.
 */
@Stable
class CapabilitiesState internal constructor(
    private val scope: CoroutineScope,
    private val read: suspend () -> Capabilities,
    private val grantedPermissions: suspend () -> List<String>,
) {
    /** What the server reported, or `null` until it has been read, while it loads, and after a failed read. */
    var capabilities by mutableStateOf<Capabilities?>(null)
        private set

    var isLoading by mutableStateOf(false)
        private set

    /**
     * Why the last read failed. A failure means the capabilities are unavailable, not that the user lacks them:
     * [retry] reads them again.
     */
    var error by mutableStateOf<Throwable?>(null)
        private set

    private var user: User? = null
    private var used = false
    private var started = false

    // Bumped by every read and by sign-out, so only the latest read may write the state.
    private var generation = 0

    // What the current answer, or the read in flight, was taken for.
    private var readKey: List<Any?>? = null
    private var job: Job? = null

    /**
     * Whether the user may perform [action] on [resource]. `false` while loading, after a failed read, and for
     * anything the server did not report.
     */
    fun can(
        resource: String,
        action: String = "view",
    ): Boolean = capabilities?.can(resource, action) == true

    /** The access to a record of [resource], from its `isReadOnly` and `isOwnedByOU` flags (`null` when absent). */
    fun recordAccess(
        resource: String,
        isReadOnly: Boolean?,
        isOwnedByOU: Boolean?,
    ): RecordAccess = RecordAccess(resource, isReadOnly, isOwnedByOU, ::can)

    /** Marks the capabilities as used and reads them if they have not been read for the signed-in user. */
    fun load() {
        used = true
        if (user != null && !started) start()
    }

    /** Reads the capabilities again, for example after a failed read. Does nothing while a read runs. */
    fun retry() {
        if (user != null && !isLoading) start()
    }

    /** Tells the state who is signed in, `null` when nobody is. Called by `ThunderIDState`. */
    internal fun setUser(next: User?) {
        user = next
        if (next == null) {
            generation++
            job?.cancel()
            started = false
            readKey = null
            capabilities = null
            error = null
            isLoading = false
            return
        }
        if (!used) return
        if (!started) return start()
        val current = generation
        scope.launch {
            val key = keyFor(next)
            // Something newer started meanwhile, or the answer already belongs to this user and token.
            if (current != generation || user == null || (key == readKey && error == null)) return@launch
            start(key)
        }
    }

    private suspend fun keyFor(user: User?): List<Any?> {
        val permissions =
            try {
                grantedPermissions()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
        return listOf(user?.sub, user?.get("ouId"), permissions.sorted())
    }

    private fun start(key: List<Any?>? = null) {
        val current = ++generation
        val forUser = user
        started = true
        readKey = key
        capabilities = null
        error = null
        isLoading = true
        job?.cancel()
        job =
            scope.launch {
                try {
                    val nextKey = key ?: keyFor(forUser)
                    if (current == generation) readKey = nextKey
                    val result = read()
                    if (current == generation) {
                        capabilities = result
                        isLoading = false
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (current == generation) {
                        error = e
                        isLoading = false
                    }
                }
            }
    }
}
