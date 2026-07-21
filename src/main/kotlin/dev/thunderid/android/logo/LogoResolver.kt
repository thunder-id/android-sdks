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
 * Resolves an application logo spec string — `emoji:<glyph>`,
 * `avatar:shape=...,variant=...,content=...,colors=...,bg=...`, or a bare URL — into a
 * renderable [ResolvedLogo].
 *
 * An unrecognized `anonymous_animal` `content` name, or a spec in none of the recognized
 * schemes, falls back to [ResolvedLogo.Url] so callers can always render *something* without
 * special-casing.
 */
object LogoResolver {
    /**
     * @param spec The stored logo spec string.
     * @param fallbackSeedText Seed text used to derive an `avatar:` spec's `content` (initials or
     *   anonymous animal key) when the spec itself doesn't carry a `content` param (e.g. an app
     *   name to keep the avatar in sync as it changes).
     * @return The resolved logo, ready to render.
     *
     * ```kotlin
     * LogoResolver.resolve("emoji:🛡️") // ResolvedLogo.Emoji("🛡️")
     * LogoResolver.resolve("avatar:shape=circle,variant=anonymous_animal,content=jackalope")
     *     // ResolvedLogo.Icon(R.drawable.logo_icon_anonymous_jackalope)
     * LogoResolver.resolve("https://example.com/logo.png") // ResolvedLogo.Url("https://example.com/logo.png")
     * ```
     */
    fun resolve(
        spec: String,
        fallbackSeedText: String = "",
    ): ResolvedLogo {
        LogoSpecParser.emojiGlyphOrNull(spec)?.let { glyph ->
            return ResolvedLogo.Emoji(glyph)
        }

        LogoSpecParser.avatarParamsOrNull(spec)?.let { params ->
            val content = params.content.ifEmpty { AvatarContentDeriver.derive(fallbackSeedText, params.variant) }

            if (params.variant == AvatarVariant.ANONYMOUS_ANIMAL) {
                val resId = LogoIconRegistry.anonymousAnimalIconOrNull(content)
                return if (resId != null) ResolvedLogo.Icon(resId) else ResolvedLogo.Url(spec)
            }

            if (params.variant == AvatarVariant.ANONYMOUS_ENTITY) {
                val resId = LogoIconRegistry.anonymousEntityIconOrNull(content)
                return if (resId != null) ResolvedLogo.Icon(resId) else ResolvedLogo.Url(spec)
            }

            val letterCount = if (params.variant == AvatarVariant.ONE_LETTER) 1 else 2
            return ResolvedLogo.Avatar(
                AvatarGenerator.generate(content, params.colors, params.shape, letterCount, params.bg),
            )
        }

        return ResolvedLogo.Url(spec)
    }
}
