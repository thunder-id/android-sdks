package dev.thunderid.compose.components.actions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.thunderid.compose.LocalThunderID

/** Button that initiates the sign-up flow (spec §8.4 Actions). */
@Composable
fun SignUpButton(modifier: Modifier = Modifier, onTap: (() -> Unit)? = null) {
    val state = LocalThunderID.current
    val label = state.i18n.resolve("signUp.button")
    BaseSignUpButton(label = label, modifier = modifier) { onTap?.invoke() }
}

/** Unstyled base variant (spec §8.3). */
@Composable
fun BaseSignUpButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
            .semantics { contentDescription = label }
            .clickable(onClick = onClick),
    ) {
        BasicText(label)
    }
}
