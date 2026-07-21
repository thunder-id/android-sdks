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
