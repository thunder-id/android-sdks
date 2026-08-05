// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.logo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarContentDeriverTest {
    @Test
    fun `derives one letter content from the seed`() {
        assertEquals("A", AvatarContentDeriver.derive("Acme", AvatarVariant.ONE_LETTER))
        assertEquals("A", AvatarContentDeriver.derive("---", AvatarVariant.ONE_LETTER))
    }

    @Test
    fun `derives two letter content from the seed`() {
        assertEquals("AC", AvatarContentDeriver.derive("Acme", AvatarVariant.TWO_LETTER))
        assertEquals("A", AvatarContentDeriver.derive("---", AvatarVariant.TWO_LETTER))
    }

    @Test
    fun `derives a deterministic anonymous_animal key from the seed`() {
        // hashStr("Fox") == 70831 (see AvatarMathTest); 70831 mod 19 == 18, the last entry of
        // the sorted 19-name list, "skunk".
        assertEquals("skunk", AvatarContentDeriver.derive("Fox", AvatarVariant.ANONYMOUS_ANIMAL))
        assertEquals(
            AvatarContentDeriver.derive("Fox", AvatarVariant.ANONYMOUS_ANIMAL),
            AvatarContentDeriver.derive("Fox", AvatarVariant.ANONYMOUS_ANIMAL),
        )
    }

    @Test
    fun `falls back to a random but valid anonymous_animal key for an empty seed`() {
        val validNames = LogoIconRegistry.ANONYMOUS_ANIMAL_ICONS.keys
        repeat(20) {
            assertTrue(validNames.contains(AvatarContentDeriver.derive("", AvatarVariant.ANONYMOUS_ANIMAL)))
        }
    }

    @Test
    fun `derives a deterministic anonymous_entity key from the seed`() {
        // hashStr("Fox") == 70831 (see AvatarMathTest); 70831 mod 36 == 19, "obelisk" in the sorted
        // 36-name list.
        assertEquals("obelisk", AvatarContentDeriver.derive("Fox", AvatarVariant.ANONYMOUS_ENTITY))
        assertEquals(
            AvatarContentDeriver.derive("Fox", AvatarVariant.ANONYMOUS_ENTITY),
            AvatarContentDeriver.derive("Fox", AvatarVariant.ANONYMOUS_ENTITY),
        )
    }

    @Test
    fun `falls back to a random but valid anonymous_entity key for an empty seed`() {
        val validNames = LogoIconRegistry.ANONYMOUS_ENTITY_ICONS.keys
        repeat(20) {
            assertTrue(validNames.contains(AvatarContentDeriver.derive("", AvatarVariant.ANONYMOUS_ENTITY)))
        }
    }
}
