package dev.thunderid.compose.components.guards

import androidx.compose.runtime.Composable
import dev.thunderid.compose.LocalThunderID

/** Renders [content] only when the user is authenticated (spec §8.4 Guards). */
@Composable
fun SignedIn(
    fallback: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val state = LocalThunderID.current
    if (state.isSignedIn) content() else fallback()
}
