/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

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
                MasterKey.Builder(context)
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
