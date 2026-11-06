// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.token

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import dev.thunderid.android.FlowSessionData
import dev.thunderid.android.StorageAdapter
import dev.thunderid.android.TokenResponse

private object StoreKey {
    const val ACCESS_TOKEN = "thunder.access_token"
    const val REFRESH_TOKEN = "thunder.refresh_token"
    const val ID_TOKEN = "thunder.id_token"
    const val TOKEN_EXPIRY = "thunder.token_expiry"
    const val SCOPE = "thunder.scope"
    const val DEVICE_SECRET = "thunder.device_secret"
    const val FLOW_SESSIONS = "thunder.flow_sessions"
}

/** An SSO session handle kept from an app-native flow step. [expiresAt] is in epoch milliseconds. */
internal data class StoredFlowSession(
    @SerializedName("flowIdentifier") val flowIdentifier: String = "",
    @SerializedName("sessionHandle") val sessionHandle: String = "",
    @SerializedName("expiresAt") val expiresAt: Long? = null,
)

/**
 * Persists and retrieves the token set using the configured StorageAdapter. The device secret and the
 * flow sessions sit beside it, in the same storage.
 */
internal class TokenStore(
    private val storage: StorageAdapter,
) {
    fun save(response: TokenResponse) {
        storage.store(StoreKey.ACCESS_TOKEN, response.accessToken)
        response.refreshToken?.let { storage.store(StoreKey.REFRESH_TOKEN, it) }
        response.idToken?.let { storage.store(StoreKey.ID_TOKEN, it) }
        response.expiresIn?.let {
            val expiry = System.currentTimeMillis() / 1000L + it
            storage.store(StoreKey.TOKEN_EXPIRY, expiry.toString())
        }
        response.scope?.let { storage.store(StoreKey.SCOPE, it) }
        response.deviceSecret?.takeIf { it.isNotEmpty() }?.let { storage.store(StoreKey.DEVICE_SECRET, it) }
    }

    /**
     * Replaces the whole token set with [response], so nothing of the previous session, such as a refresh
     * token bound to another organization unit, outlives it. The device secret, the flow sessions and other
     * keys in the storage stay.
     */
    fun replace(response: TokenResponse) {
        listOf(StoreKey.ACCESS_TOKEN, StoreKey.REFRESH_TOKEN, StoreKey.ID_TOKEN, StoreKey.TOKEN_EXPIRY, StoreKey.SCOPE)
            .forEach { storage.delete(it) }
        save(response)
    }

    fun accessToken(): String? = storage.retrieve(StoreKey.ACCESS_TOKEN)

    fun refreshToken(): String? = storage.retrieve(StoreKey.REFRESH_TOKEN)

    fun idToken(): String? = storage.retrieve(StoreKey.ID_TOKEN)

    fun tokenExpiry(): Long? = storage.retrieve(StoreKey.TOKEN_EXPIRY)?.toLongOrNull()

    fun isNearExpiry(thresholdSeconds: Long = 60): Boolean {
        val expiry = tokenExpiry() ?: return true
        return System.currentTimeMillis() / 1000L + thresholdSeconds >= expiry
    }

    fun deviceSecret(): String? = storage.retrieve(StoreKey.DEVICE_SECRET)?.takeIf { it.isNotEmpty() }

    fun removeDeviceSecret() = storage.delete(StoreKey.DEVICE_SECRET)

    /**
     * Merges the sessions a flow step started into the kept ones by flow identifier, then drops the ones it
     * [cleared]. Does nothing when the step names neither.
     */
    fun updateFlowSessions(
        started: List<FlowSessionData>?,
        cleared: List<String>?,
        now: Long = System.currentTimeMillis(),
    ) {
        if (started.isNullOrEmpty() && cleared.isNullOrEmpty()) return
        val held = LinkedHashMap<String, StoredFlowSession>()
        storedFlowSessions().forEach { held[it.flowIdentifier] = it }
        started.orEmpty().forEach { session ->
            val id = session.flowIdentifier?.takeIf { it.isNotEmpty() } ?: return@forEach
            val handle = session.sessionHandle?.takeIf { it.isNotEmpty() } ?: return@forEach
            held[id] = StoredFlowSession(id, handle, session.expiresIn?.let { now + it * MILLIS_PER_SECOND })
        }
        cleared.orEmpty().forEach { held.remove(it) }
        if (held.isEmpty()) {
            storage.delete(StoreKey.FLOW_SESSIONS)
        } else {
            storage.store(StoreKey.FLOW_SESSIONS, Gson().toJson(held.values.toTypedArray()))
        }
    }

    /** The kept SSO session handles that have not expired, as a flow request presents them. */
    fun flowSessions(now: Long = System.currentTimeMillis()): List<FlowSessionData> =
        storedFlowSessions()
            .filter { (it.expiresAt ?: Long.MAX_VALUE) > now }
            .map { FlowSessionData(flowIdentifier = it.flowIdentifier, sessionHandle = it.sessionHandle) }

    fun clear() = storage.clear()

    private fun storedFlowSessions(): List<StoredFlowSession> {
        val json = storage.retrieve(StoreKey.FLOW_SESSIONS) ?: return emptyList()
        // Unreadable data is dropped rather than failing the flow that reads it.
        return runCatching {
            Gson()
                .fromJson(json, Array<StoredFlowSession>::class.java)
                .filter { it.flowIdentifier.isNotEmpty() && it.sessionHandle.isNotEmpty() }
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
    }
}
