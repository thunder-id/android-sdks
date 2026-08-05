// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

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
