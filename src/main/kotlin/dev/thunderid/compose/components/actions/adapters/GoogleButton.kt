// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.actions.adapters

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.thunderid.android.R

/**
 * "Continue with Google" federated sign-in trigger, styled to match the outlined action
 * buttons rendered below a SignIn form's "Or" divider.
 */
@Composable
fun GoogleButton(
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
        icon = {
            Image(
                painter = painterResource(id = R.drawable.ic_provider_google),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        },
    )
}
