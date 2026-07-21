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
import androidx.annotation.DrawableRes

/**
 * Result of resolving an application logo spec string via [LogoResolver.resolve].
 *
 * A logo spec is a compact string stored in a theme/branding config, e.g. `"emoji:🛡️"`,
 * `"avatar:shape=circle,variant=two_letter,content=BM,colors=2"`,
 * `"avatar:variant=anonymous_animal,content=jackalope"`, or a bare image URL. Each variant is
 * resolved into whichever representation is cheapest to render on Android: an emoji glyph, a
 * bundled drawable resource, a natively-rendered gradient avatar [Bitmap], or a plain URL for
 * the caller to load.
 */
sealed class ResolvedLogo {
    /**
     * An emoji glyph to render directly (from an `emoji:<glyph>` spec).
     */
    data class Emoji(
        val glyph: String,
    ) : ResolvedLogo()

    /**
     * A curated icon bundled with the SDK (from a recognized
     * `avatar:...,variant=anonymous_animal,content=<name>` spec), identified by its drawable
     * resource id.
     */
    data class Icon(
        @DrawableRes val resId: Int,
    ) : ResolvedLogo()

    /**
     * A deterministically-generated gradient avatar (from an `avatar:...` spec), rendered
     * natively to a [Bitmap].
     */
    data class Avatar(
        val bitmap: Bitmap,
    ) : ResolvedLogo()

    /**
     * A plain image URL to load directly — either the spec was already a bare URL, or it was
     * an `avatar:...,variant=anonymous_animal,...` spec whose `content` name isn't recognized.
     */
    data class Url(
        val url: String,
    ) : ResolvedLogo()
}
