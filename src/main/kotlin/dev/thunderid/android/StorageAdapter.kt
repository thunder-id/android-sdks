// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Interface for custom token/session storage backends (spec §11.1).
 */
interface StorageAdapter {
    fun store(
        key: String,
        value: String,
    )

    fun retrieve(key: String): String?

    fun delete(key: String)

    fun clear()
}

/**
 * Default storage using Android EncryptedSharedPreferences backed by the Android Keystore (spec §11.1).
 */
class EncryptedStorageAdapter(
    context: Context,
    prefsName: String = "dev.thunderid.sdk.prefs",
) : StorageAdapter {
    private val prefs =
        run {
            val masterKey =
                MasterKey
                    .Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
            EncryptedSharedPreferences.create(
                context,
                prefsName,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }

    override fun store(
        key: String,
        value: String,
    ) {
        prefs.edit().putString(key, value).apply()
    }

    override fun retrieve(key: String): String? = prefs.getString(key, null)

    override fun delete(key: String) {
        prefs.edit().remove(key).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }
}

/**
 * In-memory storage adapter for testing.
 */
class InMemoryStorageAdapter : StorageAdapter {
    private val store = mutableMapOf<String, String>()

    override fun store(
        key: String,
        value: String,
    ) {
        store[key] = value
    }

    override fun retrieve(key: String): String? = store[key]

    override fun delete(key: String) {
        store.remove(key)
    }

    override fun clear() {
        store.clear()
    }
}
