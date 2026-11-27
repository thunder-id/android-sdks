// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.telemetry

/**
 * Event names: `<type>.<action>`, after the prefix when one is set, the type singular and a multi-word
 * action hyphenated, a failure ending in `-failed` and a page ending in `-view`. Also matches the `exclude` patterns.
 *
 * @param prefix The product prefix, when configured.
 */
internal class TelemetryNames(
    private val prefix: String?,
) {
    /** The full name of an event: the prefix, when set, and the name. */
    fun full(name: String): String = if (prefix.isNullOrEmpty()) name else "$prefix.$name"

    companion object {
        private const val REGEX_SPECIALS = ".+?^\${}()|[]\\"

        /**
         * Whether [value] matches [pattern]: `*` matches the rest of one path segment, `**` the rest of the
         * value, and anything else must be equal.
         */
        fun matches(
            value: String,
            pattern: String,
        ): Boolean {
            val expression = StringBuilder()
            var i = 0
            while (i < pattern.length) {
                val char = pattern[i]
                when {
                    char == '*' && pattern.getOrNull(i + 1) == '*' -> {
                        expression.append(".*")
                        i += 1
                    }

                    char == '*' -> {
                        expression.append("[^/]*")
                    }

                    char in REGEX_SPECIALS -> {
                        expression.append('\\').append(char)
                    }

                    else -> {
                        expression.append(char)
                    }
                }
                i += 1
            }
            return Regex("^$expression$").matches(value)
        }

        /** Whether [value] matches any of [patterns]. */
        fun matchesAny(
            value: String,
            patterns: List<String>,
        ): Boolean = patterns.any { matches(value, it) }

        /**
         * The singular of a collection's path segment: `applications` is `application`, `policies` is
         * `policy`, and a word that is not a collection stays as it is.
         */
        fun singular(segment: String): String =
            when {
                // A word ending in `-ss` (`access`) or `-us` (`status`) is not a plural.
                segment.endsWith("ss") || segment.endsWith("us") -> segment

                segment.endsWith("ies") -> segment.dropLast(3) + "y"

                segment.endsWith("sses") || segment.endsWith("xes") -> segment.dropLast(2)

                segment.endsWith("s") -> segment.dropLast(1)

                else -> segment
            }

        /** Whether a path segment names a collection: a plural word. */
        fun isCollection(segment: String): Boolean = singular(segment) != segment

        /** Whether a route segment is a parameter, such as `:id` or `{id}`. */
        fun isParameter(segment: String): Boolean =
            segment.startsWith(":") || (segment.startsWith("{") && segment.endsWith("}")) || segment == "*"

        /**
         * The name of a page from its route pattern, before the prefix:
         *
         * - `/applications` is `application.list-view`;
         * - `/applications/:id` is `application.detail-view`;
         * - `/applications/create` and `/applications/:id/status` are `application.create-view` and
         *   `application.status-view`;
         * - `/organizations/:org/home` is `organization.home-view`, and `/` is `app.home-view`.
         */
        fun pageName(pattern: String): String {
            val segments = pattern.split("/").filter { it.isNotEmpty() }
            if (segments.isEmpty()) return "app.home-view"
            val last = segments.last()

            fun owner(before: Int): String {
                for (i in before downTo 0) {
                    if (!isParameter(segments[i])) return singular(segments[i])
                }
                return "app"
            }
            return when {
                isParameter(last) -> "${owner(segments.size - 2)}.detail-view"
                isCollection(last) -> "${singular(last)}.list-view"
                else -> "${owner(segments.size - 2)}.$last-view"
            }
        }
    }
}
