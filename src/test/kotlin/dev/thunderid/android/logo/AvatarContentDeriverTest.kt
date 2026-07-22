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
