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

package dev.thunderid.compose.components.actions.adapters

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp

/**
 * "Continue with GitHub" federated sign-in trigger, styled to match the outlined action
 * buttons rendered below a SignIn form's "Or" divider.
 */
@Composable
fun GitHubButton(
    label: String,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TriggerButtonStyle(
        label = label,
        isLoading = isLoading,
        onClick = onClick,
        modifier = modifier,
        icon = { GitHubGlyph() },
    )
}

/**
 * Simplified GitHub "octocat" silhouette, drawn with plain [Canvas] circles rather than ported
 * SVG path data — a rounded head with two ears, visually recognizable without pixel-perfect
 * brand fidelity.
 */
@Composable
private fun GitHubGlyph() {
    val glyphColor = LocalContentColor.current
    Canvas(modifier = Modifier.size(18.dp)) {
        val headRadius = size.minDimension * 0.42f
        val center = Offset(size.width / 2f, size.height / 2f)
        val earRadius = size.minDimension * 0.14f

        // Ears.
        drawCircle(
            color = glyphColor,
            radius = earRadius,
            center = Offset(center.x - headRadius * 0.62f, center.y - headRadius * 0.62f),
        )
        drawCircle(
            color = glyphColor,
            radius = earRadius,
            center = Offset(center.x + headRadius * 0.62f, center.y - headRadius * 0.62f),
        )
        // Head/body.
        drawCircle(color = glyphColor, radius = headRadius, center = center)
    }
}
