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

    var activeLocale: String = language
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
