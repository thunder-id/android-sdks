// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import dev.thunderid.android.KeyValuePair

/**
 * Displays label and value pairs as two aligned columns, with an optional label above them. Rendered
 * for a `KEY_VALUE_LIST` flow component, e.g. the account-linking prompt's matched attributes.
 */
@Composable
fun KeyValueList(
    label: String,
    pairs: List<KeyValuePair>,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (label.isNotBlank()) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val labelStyle = MaterialTheme.typography.bodyMedium
        val textMeasurer = rememberTextMeasurer()
        val density = LocalDensity.current
        // The label column is as wide as its widest label, so every value starts at the same edge and
        // gets the rest of the row. It is capped at half the row so one long label cannot squeeze the
        // values; a label past the cap wraps instead.
        val widestLabel =
            remember(pairs, labelStyle, density) {
                with(density) { (pairs.maxOfOrNull { textMeasurer.measure(it.label, labelStyle).size.width } ?: 0).toDp() }
            }
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                    .padding(16.dp),
        ) {
            val labelWidth = minOf(widestLabel, maxWidth / 2)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                pairs.forEach { pair ->
                    // Each row reads as one element.
                    Row(
                        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        Text(
                            text = pair.label,
                            modifier = Modifier.width(labelWidth),
                            style = labelStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = pair.value,
                            modifier = Modifier.weight(1f),
                            style = labelStyle.copy(fontWeight = FontWeight.Medium),
                        )
                    }
                }
            }
        }
    }
}
