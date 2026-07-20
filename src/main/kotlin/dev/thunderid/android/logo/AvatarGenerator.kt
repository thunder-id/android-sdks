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
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renders the deterministic gradient avatar described by an `avatar:` logo spec's
 * `one_letter`/`two_letter` variants natively (no SVG dependency), matching [AvatarMath]'s
 * hash/palette/rotation algorithm. The `anonymous_animal` variant is rendered separately via
 * [LogoIconRegistry].
 */
internal object AvatarGenerator {
    /**
     * Reference box the shape geometry below (corner radius, initials position/size) was
     * designed against; [generate] scales it to [sizePx].
     */
    private const val BOX_SIZE = 60f
    private const val CORNER_RADIUS = 14f
    private const val TEXT_Y = 38f
    private const val TEXT_SIZE = 20f
    private const val DEFAULT_SIZE_PX = 240

    /**
     * Generates a gradient (or flat, when [bg] is set) avatar with initials overlaid, as a
     * [Bitmap].
     *
     * @param content The final, ready-to-render initials text (already resolved by the caller —
     *   not a seed to derive from). Uppercased and truncated to [letterCount] characters here.
     * @param colors Gradient/rotation variant index (any integer; wraps around the palette).
     *   Hashed together with [content] to pick the palette entry/angle, ignored when [bg] is set.
     * @param shape Avatar background shape.
     * @param letterCount Number of characters of [content] to draw (1 for `one_letter`, 2 for
     *   `two_letter`).
     * @param bg Optional explicit flat background color (e.g. `"#FF5733"`) overriding the derived
     *   gradient.
     * @param sizePx Output bitmap width/height in pixels; the box is square.
     */
    fun generate(
        content: String,
        colors: Int,
        shape: AvatarShape,
        letterCount: Int,
        bg: String? = null,
        sizePx: Int = DEFAULT_SIZE_PX,
    ): Bitmap {
        val seed = AvatarMath.seedFor(content)

        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val scale = sizePx / BOX_SIZE

        val shapePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                if (bg != null) {
                    color = Color.parseColor(bg)
                } else {
                    val hash = AvatarMath.hashStr(seed)
                    val (startHex, endHex) = AvatarMath.PALETTES[AvatarMath.paletteIndex(hash, colors)]
                    val angle = AvatarMath.angleDegrees(hash, colors)
                    shader = gradientShader(sizePx.toFloat(), angle, Color.parseColor(startHex), Color.parseColor(endHex))
                }
            }

        when (shape) {
            AvatarShape.CIRCLE -> {
                canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, shapePaint)
            }

            AvatarShape.ROUNDED -> {
                val radius = CORNER_RADIUS * scale
                canvas.drawRoundRect(RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat()), radius, radius, shapePaint)
            }
        }

        val textPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = TEXT_SIZE * scale
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
        canvas.drawText(seed.uppercase().take(letterCount), sizePx / 2f, TEXT_Y * scale, textPaint)

        return bitmap
    }

    /**
     * Builds the gradient shader for a [sizePx] square, replicating the SVG semantics of a
     * `0,0 -> 1,1` (objectBoundingBox) linear gradient rotated by [angleDegrees] around the
     * box's center.
     */
    private fun gradientShader(
        sizePx: Float,
        angleDegrees: Int,
        startColor: Int,
        endColor: Int,
    ): LinearGradient {
        val theta = Math.toRadians(angleDegrees.toDouble())
        val cos = cos(theta)
        val sin = sin(theta)

        fun rotate(
            x: Double,
            y: Double,
        ): Pair<Float, Float> {
            val dx = x - 0.5
            val dy = y - 0.5
            val rx = 0.5 + dx * cos - dy * sin
            val ry = 0.5 + dx * sin + dy * cos
            return (rx * sizePx).toFloat() to (ry * sizePx).toFloat()
        }

        val (startX, startY) = rotate(0.0, 0.0)
        val (endX, endY) = rotate(1.0, 1.0)
        return LinearGradient(startX, startY, endX, endY, startColor, endColor, Shader.TileMode.CLAMP)
    }
}
