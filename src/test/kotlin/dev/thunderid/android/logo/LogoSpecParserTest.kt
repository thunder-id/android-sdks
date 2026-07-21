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
import org.junit.Assert.assertNull
import org.junit.Test

class LogoSpecParserTest {
    @Test
    fun `emojiGlyphOrNull extracts the glyph`() {
        assertEquals("🛡️", LogoSpecParser.emojiGlyphOrNull("emoji:🛡️"))
        assertNull(LogoSpecParser.emojiGlyphOrNull("avatar:content=Acme"))
    }

    @Test
    fun `avatarParamsOrNull parses all fields`() {
        val params =
            LogoSpecParser.avatarParamsOrNull(
                "avatar:shape=circle,variant=two_letter,content=Acme,colors=2,bg=#FF5733",
            )
        assertEquals(AvatarShape.CIRCLE, params?.shape)
        assertEquals(AvatarVariant.TWO_LETTER, params?.variant)
        assertEquals("Acme", params?.content)
        assertEquals(2, params?.colors)
        assertEquals("#FF5733", params?.bg)
    }

    @Test
    fun `avatarParamsOrNull parses the anonymous_animal variant`() {
        val params = LogoSpecParser.avatarParamsOrNull("avatar:variant=anonymous_animal,content=jackalope")
        assertEquals(AvatarVariant.ANONYMOUS_ANIMAL, params?.variant)
        assertEquals("jackalope", params?.content)
    }

    @Test
    fun `avatarParamsOrNull parses the anonymous_entity variant`() {
        val params = LogoSpecParser.avatarParamsOrNull("avatar:variant=anonymous_entity,content=hexagon")
        assertEquals(AvatarVariant.ANONYMOUS_ENTITY, params?.variant)
        assertEquals("hexagon", params?.content)
    }

    @Test
    fun `avatarParamsOrNull falls back to defaults for missing or invalid fields`() {
        val params = LogoSpecParser.avatarParamsOrNull("avatar:content=Acme")
        assertEquals(AvatarShape.DEFAULT, params?.shape)
        assertEquals(AvatarVariant.DEFAULT, params?.variant)
        assertEquals("Acme", params?.content)
        assertEquals(0, params?.colors)
        assertNull(params?.bg)
    }

    @Test
    fun `avatarParamsOrNull returns null for a non-avatar spec`() {
        assertNull(LogoSpecParser.avatarParamsOrNull("emoji:🐯"))
    }
}
