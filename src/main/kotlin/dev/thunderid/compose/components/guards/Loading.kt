package dev.thunderid.compose.components.guards

import androidx.compose.runtime.Composable
import dev.thunderid.compose.LocalThunderID

/** Renders [indicator] while the SDK is initializing or mid-operation (spec §8.4 Guards). */
@Composable
fun Loading(
    indicator: @Composable () -> Unit = {},
) {
    val state = LocalThunderID.current
    if (state.isLoading) indicator()
}
