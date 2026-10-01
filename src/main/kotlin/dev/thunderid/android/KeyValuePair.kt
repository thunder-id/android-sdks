// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android

import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException

/** One row of a `KEY_VALUE_LIST` component, read from the `additionalData` key named in its `source`. */
data class KeyValuePair(
    val label: String,
    val value: String,
) {
    companion object {
        /**
         * Reads the pairs a `KEY_VALUE_LIST` renders from the raw value found under its `source` key.
         * The server publishes them as a JSON-encoded array, since additional data carries strings, so
         * both an array and its encoding are accepted.
         *
         * Entries that are not objects, or that carry no value, are dropped, since an empty row tells
         * the user nothing. Anything that is not a list of pairs yields none.
         */
        fun list(raw: Any?): List<KeyValuePair> {
            val entries: List<Map<*, *>> =
                when (raw) {
                    is String -> fromJson(raw)
                    is List<*> -> raw.filterIsInstance<Map<*, *>>()
                    else -> emptyList()
                }
            return entries.mapNotNull { entry ->
                val value = entry["value"] as? String
                if (value.isNullOrEmpty()) null else KeyValuePair(entry["label"] as? String ?: "", value)
            }
        }

        private fun fromJson(raw: String): List<Map<*, *>> {
            val array =
                try {
                    JsonParser.parseString(raw).takeIf { it.isJsonArray }?.asJsonArray
                } catch (_: JsonSyntaxException) {
                    null
                } ?: return emptyList()
            return array.filter { it.isJsonObject }.map { element ->
                element.asJsonObject.entrySet().associate { (key, field) ->
                    key to field.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
                }
            }
        }
    }
}
