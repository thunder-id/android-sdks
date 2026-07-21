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
 * Parsed `avatar:` spec parameters, e.g. from
 * `"avatar:shape=circle,variant=two_letter,content=BM,colors=2"`.
 */
internal data class AvatarParams(
    val shape: AvatarShape = AvatarShape.DEFAULT,
    val variant: AvatarVariant = AvatarVariant.DEFAULT,
    val content: String = "",
    val colors: Int = 0,
    val bg: String? = null,
)

/**
 * Recognizes and decomposes the fixed set of logo spec schemes (`emoji:`, `avatar:`). Anything
 * not matching one of these is treated as a plain URL by [LogoResolver].
 */
internal object LogoSpecParser {
    private const val EMOJI_SCHEME = "emoji:"
    private const val AVATAR_SCHEME = "avatar:"

    fun emojiGlyphOrNull(spec: String): String? = if (spec.startsWith(EMOJI_SCHEME)) spec.substring(EMOJI_SCHEME.length) else null

    fun avatarParamsOrNull(spec: String): AvatarParams? {
        if (!spec.startsWith(AVATAR_SCHEME)) {
            return null
        }
        val raw = spec.substring(AVATAR_SCHEME.length)
        val params = mutableMapOf<String, String>()
        raw.split(",").forEach { pair ->
            val key = pair.substringBefore('=').trim()
            val value = if (pair.contains('=')) pair.substringAfter('=').trim() else ""
            if (key.isNotEmpty()) {
                params[key] = value
            }
        }
        return AvatarParams(
            shape = AvatarShape.fromWireValue(params["shape"]),
            variant = AvatarVariant.fromWireValue(params["variant"]),
            content = params["content"] ?: "",
            colors = params["colors"]?.toIntOrNull() ?: 0,
            bg = params["bg"]?.ifEmpty { null },
        )
    }
}
