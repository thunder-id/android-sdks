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
import org.junit.Test

/**
 * Verifies [AvatarMath] against values independently computed from the JS
 * `generateAvatarDataUri` reference implementation, to keep the two platforms in lockstep.
 */
class AvatarMathTest {
    @Test
    fun `hashStr matches the JS reference for known seeds`() {
        assertEquals(66049L, AvatarMath.hashStr("App"))
        assertEquals(2035034L, AvatarMath.hashStr("Acme"))
        assertEquals(70831L, AvatarMath.hashStr("Fox"))
    }

    @Test
    fun `seedFor defaults blank text to App`() {
        assertEquals("App", AvatarMath.seedFor(""))
        assertEquals("Acme", AvatarMath.seedFor("Acme"))
    }

    @Test
    fun `paletteIndex matches the JS reference`() {
        val hash = AvatarMath.hashStr("App")
        assertEquals(9, AvatarMath.paletteIndex(hash, 0))
        assertEquals(1, AvatarMath.paletteIndex(hash, 2))
        assertEquals(6, AvatarMath.paletteIndex(hash, -3))
        assertEquals(6, AvatarMath.paletteIndex(hash, 37))
    }

    @Test
    fun `angleDegrees matches the JS reference for small hashes`() {
        val hash = AvatarMath.hashStr("App")
        assertEquals(168, AvatarMath.angleDegrees(hash, 0))
        assertEquals(242, AvatarMath.angleDegrees(hash, 2))
        assertEquals(57, AvatarMath.angleDegrees(hash, -3))
        assertEquals(97, AvatarMath.angleDegrees(hash, 37))
    }

    @Test
    fun `angleDegrees matches the JS signed-shift behavior for hashes above 2^31`() {
        // hashStr("SomeReallyLongAppNameThatProducesABigHash12345") == 3969909691, which is >=
        // 2^31 and therefore exercises JS's `>>` sign-extension quirk on the raw hash.
        val hash = AvatarMath.hashStr("SomeReallyLongAppNameThatProducesABigHash12345")
        assertEquals(3969909691L, hash)
        assertEquals(324, AvatarMath.angleDegrees(hash, 5))
    }

    @Test
    fun `initials takes the first two alphanumeric characters uppercased`() {
        assertEquals("AC", AvatarMath.initials("Acme"))
        assertEquals("A1", AvatarMath.initials("a1-Corp"))
        assertEquals("A", AvatarMath.initials("---"))
    }
}
