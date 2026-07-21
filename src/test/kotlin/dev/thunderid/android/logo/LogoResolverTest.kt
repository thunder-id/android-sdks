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

import android.graphics.Bitmap
import dev.thunderid.android.R
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogoResolverTest {
    @After
    fun tearDown() {
        unmockkObject(AvatarGenerator)
        unmockkObject(AvatarContentDeriver)
    }

    @Test
    fun `resolves an emoji spec`() {
        val resolved = LogoResolver.resolve("emoji:🐯")
        assertTrue(resolved is ResolvedLogo.Emoji)
        assertEquals("🐯", (resolved as ResolvedLogo.Emoji).glyph)
    }

    @Test
    fun `resolves a known anonymous_animal content to its bundled icon`() {
        val resolved = LogoResolver.resolve("avatar:variant=anonymous_animal,content=jackalope")
        assertTrue(resolved is ResolvedLogo.Icon)
        assertEquals(R.drawable.logo_icon_anonymous_jackalope, (resolved as ResolvedLogo.Icon).resId)
    }

    @Test
    fun `falls back to a URL for an unrecognized anonymous_animal content`() {
        val spec = "avatar:variant=anonymous_animal,content=dragon"
        val resolved = LogoResolver.resolve(spec)
        assertTrue(resolved is ResolvedLogo.Url)
        assertEquals(spec, (resolved as ResolvedLogo.Url).url)
    }

    @Test
    fun `resolves a known anonymous_entity content to its bundled icon`() {
        val resolved = LogoResolver.resolve("avatar:variant=anonymous_entity,content=hexagon")
        assertTrue(resolved is ResolvedLogo.Icon)
        assertEquals(R.drawable.logo_icon_entity_hexagon, (resolved as ResolvedLogo.Icon).resId)
    }

    @Test
    fun `falls back to a URL for an unrecognized anonymous_entity content`() {
        val spec = "avatar:variant=anonymous_entity,content=dragon"
        val resolved = LogoResolver.resolve(spec)
        assertTrue(resolved is ResolvedLogo.Url)
        assertEquals(spec, (resolved as ResolvedLogo.Url).url)
    }

    @Test
    fun `treats a bare URL as a plain image URL`() {
        val resolved = LogoResolver.resolve("https://example.com/logo.png")
        assertTrue(resolved is ResolvedLogo.Url)
        assertEquals("https://example.com/logo.png", (resolved as ResolvedLogo.Url).url)
    }

    @Test
    fun `resolves an avatar spec by delegating to AvatarGenerator with parsed params`() {
        val bitmap = mockk<Bitmap>()
        mockkObject(AvatarGenerator)
        every { AvatarGenerator.generate("A", 2, AvatarShape.CIRCLE, 1, null, any()) } returns bitmap

        val resolved = LogoResolver.resolve("avatar:shape=circle,variant=one_letter,content=A,colors=2")

        assertTrue(resolved is ResolvedLogo.Avatar)
        assertEquals(bitmap, (resolved as ResolvedLogo.Avatar).bitmap)
    }

    @Test
    fun `resolves an avatar spec passing through the explicit bg param`() {
        val bitmap = mockk<Bitmap>()
        mockkObject(AvatarGenerator)
        every { AvatarGenerator.generate("BM", 0, AvatarShape.ROUNDED, 2, "#FF5733", any()) } returns bitmap

        val resolved = LogoResolver.resolve("avatar:content=BM,bg=#FF5733")

        assertTrue(resolved is ResolvedLogo.Avatar)
        assertEquals(bitmap, (resolved as ResolvedLogo.Avatar).bitmap)
    }

    @Test
    fun `resolves an avatar spec deriving content from the fallback seed when content is empty`() {
        val bitmap = mockk<Bitmap>()
        mockkObject(AvatarContentDeriver)
        mockkObject(AvatarGenerator)
        every { AvatarContentDeriver.derive("MyApp", AvatarVariant.TWO_LETTER) } returns "MY"
        every { AvatarGenerator.generate("MY", 0, AvatarShape.ROUNDED, 2, null, any()) } returns bitmap

        val resolved = LogoResolver.resolve("avatar:shape=rounded", fallbackSeedText = "MyApp")

        assertTrue(resolved is ResolvedLogo.Avatar)
        assertEquals(bitmap, (resolved as ResolvedLogo.Avatar).bitmap)
    }

    @Test
    fun `resolves an anonymous_animal avatar spec deriving content from the fallback seed when content is empty`() {
        mockkObject(AvatarContentDeriver)
        every { AvatarContentDeriver.derive("session-123", AvatarVariant.ANONYMOUS_ANIMAL) } returns "otter"

        val resolved = LogoResolver.resolve("avatar:variant=anonymous_animal", fallbackSeedText = "session-123")

        assertTrue(resolved is ResolvedLogo.Icon)
        assertEquals(R.drawable.logo_icon_anonymous_otter, (resolved as ResolvedLogo.Icon).resId)
    }

    @Test
    fun `resolves an anonymous_entity avatar spec deriving content from the fallback seed when content is empty`() {
        mockkObject(AvatarContentDeriver)
        every { AvatarContentDeriver.derive("app-abc123", AvatarVariant.ANONYMOUS_ENTITY) } returns "hexagon"

        val resolved = LogoResolver.resolve("avatar:variant=anonymous_entity", fallbackSeedText = "app-abc123")

        assertTrue(resolved is ResolvedLogo.Icon)
        assertEquals(R.drawable.logo_icon_entity_hexagon, (resolved as ResolvedLogo.Icon).resId)
    }
}
