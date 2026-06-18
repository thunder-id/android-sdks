package dev.thunderid.compose.components.actions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.thunderid.compose.LocalThunderID
import kotlinx.coroutines.launch

/** Button that calls signOut and refreshes auth state (spec §8.4 Actions). */
@Composable
fun SignOutButton(modifier: Modifier = Modifier, onSignOutComplete: (() -> Unit)? = null) {
    val state = LocalThunderID.current
    val scope = rememberCoroutineScope()
    val label = state.i18n.resolve("signOut.button")
    BaseSignOutButton(label = label, isLoading = state.isLoading, modifier = modifier) {
        scope.launch {
            runCatching { state.client.signOut() }
            state.refresh()
            onSignOutComplete?.invoke()
        }
    }
}

/** Unstyled base variant (spec §8.3). */
@Composable
fun BaseSignOutButton(
    label: String,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
            .semantics { contentDescription = label }
            .then(if (!isLoading) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        BasicText(label)
    }
}
