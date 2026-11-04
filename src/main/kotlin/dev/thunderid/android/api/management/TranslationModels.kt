// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.management

/** The language tags, such as `en-US`, that have translations. */
data class LanguageListResponse(
    val languages: List<String>,
)

/**
 * The translations of a language, keyed by namespace and then by key. [totalResults] is the number of keys
 * returned.
 */
data class LanguageTranslations(
    val language: String,
    val translations: Map<String, Map<String, String>>,
    val totalResults: Int? = null,
)

/** One translation. */
data class Translation(
    val language: String,
    val namespace: String,
    val key: String,
    val value: String,
)
