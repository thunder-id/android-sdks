// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.management

/** Builds the filter expressions the management list endpoints accept. */
object ManagementFilter {
    private val ATTRIBUTE = Regex("""^\w+(\.\w+)*$""")

    /**
     * `attribute eq "value"`, for example to search users by username. The server matches the whole value.
     *
     * Surrounding whitespace is ignored and double quotes, which the expression cannot carry, are removed.
     * Returns `null` when the value is blank or the attribute name is not valid.
     */
    fun equality(
        attribute: String,
        value: String,
    ): String? {
        val cleaned = value.replace("\"", "").trim()
        if (cleaned.isEmpty() || !ATTRIBUTE.matches(attribute)) return null
        return "$attribute eq \"$cleaned\""
    }
}
