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
 * Pure (Android-framework-free) math backing [AvatarGenerator], kept separate so it can be
 * unit tested without a device/Robolectric. Mirrors the console's `generateAvatarDataUri`
 * algorithm exactly — same hash, same palette, same rotation math — so a given
 * `(content, colors, shape)` triple always maps to the same avatar on every platform.
 */
internal object AvatarMath {
    /**
     * Curated on-brand gradient pairs — `colors` rotates through this set.
     */
    val PALETTES: List<Pair<String, String>> =
        listOf(
            "#FF7300" to "#EF4223",
            "#3688FF" to "#1d5eb4",
            "#5567D5" to "#8B6FE8",
            "#06b6d4" to "#0891b2",
            "#10b981" to "#059669",
            "#ec4899" to "#be185d",
            "#f59e0b" to "#ea580c",
            "#8b5cf6" to "#6d28d9",
            "#5CD1FF" to "#3688FF",
            "#ef4444" to "#b91c1c",
        )

    private const val DEFAULT_SEED = "App"

    /**
     * `h = 0; for each char: h = (h*31 + charCode) mod 2^32 (unsigned)`.
     */
    fun hashStr(str: String): Long {
        var h = 0L
        for (c in str) {
            h = (h * 31 + c.code) and 0xFFFFFFFFL
        }
        return h
    }

    /**
     * Resolves the seed text to hash/derive initials from, defaulting to `"App"` when [text]
     * is blank.
     */
    fun seedFor(text: String): String = text.ifEmpty { DEFAULT_SEED }

    /**
     * Picks the [PALETTES] index for a given hash/colors pair:
     * `((hash + colors) mod 10 + 10) mod 10`.
     */
    fun paletteIndex(
        hash: Long,
        colors: Int,
    ): Int {
        val size = PALETTES.size
        return (((hash + colors) % size + size) % size).toInt()
    }

    /**
     * Gradient rotation angle in degrees: `(((hash >> 4) + colors*37) mod 360 + 360) mod 360`.
     *
     * The reference implementation applies `>>` (JS's *signed* 32-bit shift) to [hash], which is
     * otherwise treated as unsigned everywhere else. [hash] is reinterpreted as a signed 32-bit
     * value before shifting to match that exactly — it matters for hashes >= 2^31, which would
     * otherwise shift to a different (positive, non-sign-extended) result.
     */
    fun angleDegrees(
        hash: Long,
        colors: Int,
    ): Int {
        val shifted = (hash.toInt() shr 4).toLong()
        val raw = shifted + colors.toLong() * 37L
        return (((raw % 360) + 360) % 360).toInt()
    }

    /**
     * First [letterCount] alphanumeric characters of [seed], uppercased. Falls back to `"A"`
     * when the seed has no alphanumeric characters.
     */
    fun initials(
        seed: String,
        letterCount: Int = 2,
    ): String {
        val alnum = seed.filter { it.isLetterOrDigit() && it.code < 128 }
        val chars = if (alnum.isEmpty()) "A" else alnum
        return chars.take(letterCount).uppercase()
    }
}
