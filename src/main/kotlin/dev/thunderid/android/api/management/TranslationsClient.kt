// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.management

import dev.thunderid.android.ThunderIDFetcher

/**
 * Translation management operations, reached through `ThunderIDClient.translations`, against the `i18n`
 * collection (`{i18n}/languages/...`). Each call accepts an optional [ThunderIDFetcher] that takes
 * precedence over `ThunderIDConfig.http.fetcher`.
 */
class TranslationsClient internal constructor(
    private val transport: ManagementTransport,
) {
    /** Returns the language tags that have translations. */
    suspend fun getLanguages(fetcher: ThunderIDFetcher? = null): LanguageListResponse =
        transport.request("GET", listOf(LANGUAGES), fetcher = fetcher)

    /**
     * Returns the translations of [language], custom values merged over the defaults, optionally only those
     * in [namespace].
     */
    suspend fun resolveTranslations(
        language: String,
        namespace: String? = null,
        fetcher: ThunderIDFetcher? = null,
    ): LanguageTranslations =
        transport.request(
            "GET",
            listOf(LANGUAGES, language, TRANSLATIONS, RESOLVE),
            mapOf("namespace" to namespace),
            fetcher = fetcher,
        )

    /** Returns one translation, its custom value merged over the default. */
    suspend fun resolveTranslation(
        language: String,
        namespace: String,
        key: String,
        fetcher: ThunderIDFetcher? = null,
    ): Translation = transport.request("GET", keyPath(language, namespace, key) + RESOLVE, fetcher = fetcher)

    /**
     * Replaces every custom translation of [language] with [translations], keyed by namespace and then by
     * key, and returns the result.
     */
    suspend fun setTranslations(
        language: String,
        translations: Map<String, Map<String, String>>,
        fetcher: ThunderIDFetcher? = null,
    ): LanguageTranslations =
        transport.request(
            "POST",
            listOf(LANGUAGES, language, TRANSLATIONS),
            body = mapOf("translations" to translations),
            fetcher = fetcher,
        )

    /** Removes every custom translation of [language], leaving the defaults. */
    suspend fun deleteTranslations(
        language: String,
        fetcher: ThunderIDFetcher? = null,
    ) {
        transport.request<Unit>("DELETE", listOf(LANGUAGES, language, TRANSLATIONS), fetcher = fetcher)
    }

    /** Creates or replaces the custom translation of one key and returns it. */
    suspend fun setTranslation(
        language: String,
        namespace: String,
        key: String,
        value: String,
        fetcher: ThunderIDFetcher? = null,
    ): Translation {
        val body = mapOf("value" to value)
        return transport.request("POST", keyPath(language, namespace, key), body = body, fetcher = fetcher)
    }

    /** Removes the custom translation of one key, leaving its default. */
    suspend fun deleteTranslation(
        language: String,
        namespace: String,
        key: String,
        fetcher: ThunderIDFetcher? = null,
    ) {
        transport.request<Unit>("DELETE", keyPath(language, namespace, key), fetcher = fetcher)
    }

    private fun keyPath(
        language: String,
        namespace: String,
        key: String,
    ): List<String> = listOf(LANGUAGES, language, TRANSLATIONS, "ns", namespace, "keys", key)

    private companion object {
        const val LANGUAGES = "languages"
        const val TRANSLATIONS = "translations"
        const val RESOLVE = "resolve"
    }
}
