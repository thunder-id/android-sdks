// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.actions.adapters

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Generic outlined trigger button for `eventType: TRIGGER` actions with no dedicated brand
 * adapter, using the label supplied by the flow schema and no icon.
 */
@Composable
fun OutlinedTriggerButton(
    label: String,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    disabled: Boolean = false,
) {
    TriggerButtonStyle(
        label = label,
        isLoading = isLoading,
        onClick = onClick,
        modifier = modifier,
        disabled = disabled,
        icon = null,
    )
}
