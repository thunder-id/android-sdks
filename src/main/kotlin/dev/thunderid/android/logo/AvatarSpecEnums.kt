// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.logo

/**
 * Background shape for a generated `avatar:` logo, matching the wire values used by the
 * `avatar:shape=<SHAPE>,...` spec.
 */
enum class AvatarShape(
    internal val wireValue: String,
) {
    ROUNDED("rounded"),
    CIRCLE("circle"),
    ;

    companion object {
        val DEFAULT: AvatarShape = ROUNDED

        /**
         * Parses the `shape=<...>` wire value from an `avatar:` spec, falling back to
         * [DEFAULT] for anything unrecognized.
         */
        fun fromWireValue(value: String?): AvatarShape = entries.firstOrNull { it.wireValue == value } ?: DEFAULT
    }
}

/**
 * What's drawn on top of an `avatar:` logo's background, matching the wire values used by the
 * `avatar:variant=<VARIANT>,...` spec.
 */
enum class AvatarVariant(
    internal val wireValue: String,
) {
    ONE_LETTER("one_letter"),
    TWO_LETTER("two_letter"),
    ANONYMOUS_ANIMAL("anonymous_animal"),
    ANONYMOUS_ENTITY("anonymous_entity"),
    ;

    companion object {
        val DEFAULT: AvatarVariant = TWO_LETTER

        /**
         * Parses the `variant=<...>` wire value from an `avatar:` spec, falling back to
         * [DEFAULT] for anything unrecognized.
         */
        fun fromWireValue(value: String?): AvatarVariant = entries.firstOrNull { it.wireValue == value } ?: DEFAULT
    }
}
