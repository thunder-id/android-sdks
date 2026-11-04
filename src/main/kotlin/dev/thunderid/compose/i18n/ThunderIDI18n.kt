// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.i18n

import android.content.Context
import android.content.SharedPreferences

/**
 * Resolves localized strings for ThunderIDCompose components (spec §8.1 i18n).
 *
 * A key resolves from the app's [bundles] for the active locale, then the SDK's own strings for it, then the same
 * two for the fallback language, then the English defaults. The SDK ships strings for [BUILT_IN_LOCALES]; a locale
 * named by language only, such as `fr`, uses the first built-in locale of that language (`pt` is `pt-BR`).
 */
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
            ?: builtInStrings(activeLocale)?.get(key)
            ?: bundles[fallbackLanguage]?.get(key)
            ?: builtInStrings(fallbackLanguage)?.get(key)
            ?: DefaultStrings.all[key]
            ?: key

    /** Sets the active locale and persists it to SharedPreferences if a context was provided. */
    fun setLocale(locale: String) {
        if (locale == activeLocale) return
        activeLocale = locale
        prefs?.edit()?.putString(storageKey, locale)?.apply()
    }

    companion object {
        /** The locales the SDK ships strings for, the same ones the JavaScript SDK ships. */
        val BUILT_IN_LOCALES: List<String> =
            listOf("en-US", "fr-FR", "hi-IN", "ja-JP", "pt-BR", "pt-PT", "si-LK", "ta-IN", "te-IN")

        /**
         * The SDK's own strings for [locale], matched exactly (ignoring case and `_` for `-`), or else by its
         * language alone, taking the first built-in locale of it (`pt` is `pt-BR`). `null` when the SDK ships none.
         */
        internal fun builtInStrings(locale: String): Map<String, String>? {
            val tag = locale.replace('_', '-')
            val exact = BUILT_IN_LOCALES.firstOrNull { it.equals(tag, ignoreCase = true) }
            val language = tag.substringBefore('-')
            val match =
                exact
                    ?: BUILT_IN_LOCALES
                        .firstOrNull { it.substringBefore('-').equals(language, ignoreCase = true) }
            return when (match) {
                "en-US" -> DefaultStrings.all
                "fr-FR" -> FrFrStrings.all
                "hi-IN" -> HiInStrings.all
                "ja-JP" -> JaJpStrings.all
                "pt-BR" -> PtBrStrings.all
                "pt-PT" -> PtPtStrings.all
                "si-LK" -> SiLkStrings.all
                "ta-IN" -> TaInStrings.all
                "te-IN" -> TeInStrings.all
                else -> null
            }
        }
    }
}
