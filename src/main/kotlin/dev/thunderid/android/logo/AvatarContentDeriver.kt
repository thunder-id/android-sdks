// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.logo

import kotlin.random.Random

/**
 * Derives a ready-to-render `avatar:` spec `content` value from a raw seed string (e.g. an app
 * or user name) when a caller doesn't already have a stored spec with `content` populated.
 *
 * [LogoSpecParser]/[LogoResolver] never derive `content` themselves — a spec's `content` is
 * always treated as the final, literal value to render. This helper exists for the separate case
 * of producing that value in the first place, from a raw seed, for [AvatarVariant.ONE_LETTER],
 * [AvatarVariant.TWO_LETTER], [AvatarVariant.ANONYMOUS_ANIMAL], or [AvatarVariant.ANONYMOUS_ENTITY].
 */
internal object AvatarContentDeriver {
    /**
     * The 19 curated `anonymous_animal` keys, sorted, so a given seed always hashes to the same
     * index regardless of map iteration order.
     */
    private val ANONYMOUS_ANIMAL_NAMES: List<String> = LogoIconRegistry.ANONYMOUS_ANIMAL_ICONS.keys.sorted()

    /**
     * The 36 curated `anonymous_entity` keys, sorted, so a given seed always hashes to the same
     * index regardless of map iteration order.
     */
    private val ANONYMOUS_ENTITY_NAMES: List<String> = LogoIconRegistry.ANONYMOUS_ENTITY_ICONS.keys.sorted()

    /**
     * Derives the `content` value for [variant] from [seed].
     */
    fun derive(
        seed: String,
        variant: AvatarVariant,
    ): String =
        when (variant) {
            AvatarVariant.ONE_LETTER -> AvatarMath.initials(seed, letterCount = 1)
            AvatarVariant.TWO_LETTER -> AvatarMath.initials(seed, letterCount = 2)
            AvatarVariant.ANONYMOUS_ANIMAL -> pickAnonymousName(ANONYMOUS_ANIMAL_NAMES, seed)
            AvatarVariant.ANONYMOUS_ENTITY -> pickAnonymousName(ANONYMOUS_ENTITY_NAMES, seed)
        }

    /**
     * Hash-picks one of [names] using [AvatarMath.hashStr], so the same seed always resolves to
     * the same name. An empty seed has no meaningful hash, so it falls back to a random pick
     * instead.
     */
    private fun pickAnonymousName(
        names: List<String>,
        seed: String,
    ): String {
        if (seed.isEmpty()) {
            return names[Random.nextInt(names.size)]
        }
        val hash = AvatarMath.hashStr(seed)
        return names[(hash % names.size).toInt()]
    }
}
