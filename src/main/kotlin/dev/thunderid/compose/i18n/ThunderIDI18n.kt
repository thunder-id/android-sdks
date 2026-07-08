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

package dev.thunderid.compose.i18n

import android.content.Context
import android.content.SharedPreferences

/** Resolves localized strings for ThunderIDCompose components (spec §8.1 i18n). */
class ThunderIDI18n(
    private val bundles: Map<String, Map<String, String>> = emptyMap(),
    language: String? = null,
    private val fallbackLanguage: String = "en-US",
    private val storageKey: String = "thunder_locale",
    context: Context? = null,
) {
    private val prefs: SharedPreferences? =
        context?.getSharedPreferences("thunder_i18n", Context.MODE_PRIVATE)

    var activeLocale: String =
        language
            ?: prefs?.getString(storageKey, null)
            ?: fallbackLanguage
        private set

    /** Returns the localized string for [key], falling back through the resolution chain. */
    fun resolve(key: String): String =
        bundles[activeLocale]?.get(key)
            ?: bundles[fallbackLanguage]?.get(key)
            ?: DefaultStrings.all[key]
            ?: key

    /** Sets the active locale and persists it to SharedPreferences if a context was provided. */
    fun setLocale(locale: String) {
        if (locale == activeLocale) return
        activeLocale = locale
        prefs?.edit()?.putString(storageKey, locale)?.apply()
    }
}
