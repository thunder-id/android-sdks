// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.actions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.thunderid.compose.LocalThunderID

/** Tappable button that starts the redirect-based sign-in flow (spec §8.4 Actions). */
@Composable
fun SignInButton(
    modifier: Modifier = Modifier,
    testTag: String? = null,
    onTap: (() -> Unit)? = null,
) {
    val state = LocalThunderID.current
    val label = state.i18n.resolve("signIn.button")
    BaseSignInButton(label = label, isLoading = state.isLoading, modifier = modifier, testTag = testTag) {
        onTap?.invoke()
    }
}

/** Unstyled base variant (spec §8.3). */
@Composable
fun BaseSignInButton(
    label: String,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
                .semantics { contentDescription = label }
                .then(if (!isLoading) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        BasicText(label)
    }
}
